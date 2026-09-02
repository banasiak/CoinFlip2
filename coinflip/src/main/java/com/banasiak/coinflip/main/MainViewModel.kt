package com.banasiak.coinflip.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.common.CoinType
import com.banasiak.coinflip.common.CustomCoin
import com.banasiak.coinflip.common.Stats
import com.banasiak.coinflip.extensions.restore
import com.banasiak.coinflip.extensions.save
import com.banasiak.coinflip.settings.SettingsManager
import com.banasiak.coinflip.util.AnimationHelper
import com.banasiak.coinflip.util.SoundHelper
import com.banasiak.coinflip.util.VibrationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * How long a run has to be before beating your own record earns the fanfare. Short records are set
 * within the first handful of flips, so celebrating those would fire a five-second sound constantly
 * early on. Ten is also the bar the bit itself uses — "ten heads in a row" is the win condition
 * everyone who built one of these settled on — and it is genuinely rare: the wait roughly doubles
 * per step, so a first run of ten takes about 1,000 flips, an eleven about 2,000.
 */
private const val FANFARE_THRESHOLD = 10L

/** Decided in [MainViewModel.flipCoin]: a new record compares against the stats from before the flip, which are gone once they are committed. */
private enum class Landing { ORDINARY, NEW_RECORD, HUNDREDTH }

@HiltViewModel
class MainViewModel @Inject constructor(
  private val animationHelper: AnimationHelper,
  private val coin: Coin,
  private val settings: SettingsManager,
  private val soundHelper: SoundHelper,
  private val vibrationHelper: VibrationHelper,
  private val savedState: SavedStateHandle
) : ViewModel() {
  private var state = savedState.restore() ?: seededState()
    private set(value) {
      field = value
      // emit the new state when it changes
      Timber.d("emitState(): $value")
      _stateFlow.tryEmit(value)
    }

  private val _stateFlow = MutableStateFlow(state)
  val stateFlow = _stateFlow.asStateFlow()

  private val _effectFlow = MutableSharedFlow<MainEffect>(extraBufferCapacity = 1)
  val effectFlow = _effectFlow.asSharedFlow()

  // postAction() and viewModelScope coroutines all run on the main dispatcher, so a plain field suffices
  private var isFlipping = false

  // the screen re-reports these on every composition, so there is nothing here to restore across
  // process death
  private var coinColors: AnimationHelper.CoinColors? = null

  fun postAction(action: MainAction) {
    Timber.d("postAction(): $action")
    when (action) {
      MainAction.OnPause -> onPause()
      is MainAction.SetCoinColors -> onSetCoinColors(action.headsRim, action.tailsRim, action.fill)
      MainAction.OnResume -> onResume()
      MainAction.ResetStats -> onResetStats()
      MainAction.TapAbout -> _effectFlow.tryEmit(MainEffect.ToAbout)
      MainAction.TapCoin -> flipCoin()
      MainAction.Shake -> flipCoin()
      MainAction.TapDiagnostics -> _effectFlow.tryEmit(MainEffect.ToDiagnostics)
      MainAction.TapSettings -> _effectFlow.tryEmit(MainEffect.ToSettings)
    }
  }

  private fun seededState(): MainState {
    val face = settings.loadStats().streakValue
    return MainState(result = Coin.Result(face, AnimationHelper.Permutation.landingOn(face)))
  }

  private fun onResume() {
    val instructions = if (settings.shakeEnabled) R.string.instructions_tap_shake else R.string.instructions_tap
    val stats = settings.loadStats()

    state =
      state.copy(
        dynamicColors = settings.dynamicColorsEnabled,
        instructionsText = instructions,
        labels = Pair(settings.customHeadsText, settings.customTailsText),
        paused = false,
        resetVisible = settings.showStats && settings.showQuickReset,
        shakeEnabled = settings.shakeEnabled,
        shakeSensitivity = settings.shakeSensitivity,
        stats = stats,
        statsVisible = settings.showStats,
        streakVisible = settings.showStreak
      )

    // mid-flip, state.result already holds what the animation has not revealed yet: restoring the
    // "previous" state here shows the result the coin is about to land on
    if (!isFlipping) restoreDisplay(stats)

    generateAnimations()
  }

  private fun restoreDisplay(stats: Stats) {
    val identity = animationHelper.identity(settings.coinPrefix)
    // a null drawnCoin is a cold start, not a coin change: the face rebuilt from the persisted run
    // is the last result, which belongs to whichever coin is selected now
    val surprise = settings.coinPrefix == CoinType.RANDOM.prefix || (state.drawnCoin != null && state.drawnCoin != identity)
    val result =
      if (surprise) {
        Coin.Result(Coin.Value.UNKNOWN, AnimationHelper.Permutation.UNKNOWN, null)
      } else {
        // the label can have been edited in Settings while the screen was away; the face it names has not
        state.result.copy(customLabel = state.result.value.customLabel(settings))
      }
    // the flip animation starts from the face the coin holds, so it has to be the one on screen
    coin.restoreFace(result.value)

    val animation = animationHelper.animations[result.permutation]
    state =
      state.copy(
        animation = animation,
        coinImageType = if (animation == null) CoinImageType.PLACEHOLDER else CoinImageType.IMAGE,
        drawnCoin = identity,
        result = result,
        resultVisible = settings.textEnabled && result.value != Coin.Value.UNKNOWN,
        headsCount = stats.count(Coin.Value.HEADS),
        tailsCount = stats.count(Coin.Value.TAILS),
        streakCount = if (surprise) 0 else stats.streak
      )
  }

  private fun onPause() {
    state = state.copy(paused = true, shakeEnabled = false)

    settings.persistStats(state.stats)
    savedState.save(state)
  }

  private fun flipCoin() {
    if (isFlipping) {
      Timber.d("flipCoin() already in progress. Ignoring.")
      return
    }
    // set before launching so the guard doesn't depend on the coroutine dispatching immediately
    isFlipping = true
    viewModelScope.launch {
      // the heart and soul of this entire endeavor
      val result = coin.flip()

      val previous = state.stats
      val stats = previous.afterFlip(result.value)
      // the fanfare is for beating your own record, and only once the run is long enough to deserve
      // one (see FANFARE_THRESHOLD). Records are still kept when the display is switched off; the
      // sound is part of that display, so it goes quiet with it.
      val fanfare =
        state.streakVisible &&
          stats.streak >= FANFARE_THRESHOLD &&
          stats.record(result.value) > previous.record(result.value)

      // keeping it &#128175;...
      val landing =
        when {
          // the hundredth flip outranks a new record: it is the rarer of the two and it comes with a dialog
          stats.total % 100 == 0L -> Landing.HUNDREDTH
          fanfare -> Landing.NEW_RECORD
          else -> Landing.ORDINARY
        }

      val animationEnabled = settings.animationEnabled
      val animation = animationHelper.animations[result.permutation]

      state =
        state.copy(
          animation = animation,
          coinImageType = if (animationEnabled) CoinImageType.ANIMATION else CoinImageType.IMAGE,
          result = result,
          resultVisible = false,
          shakeEnabled = false,
          stats = stats,
          streakCount = 0
        )
      _effectFlow.emit(MainEffect.FlipCoin)

      if (animationEnabled) {
        // an obtuse way of pausing while the animation renders, proceeding 80 ms (4 frames, or 1/2 flip) before completion
        animation?.duration(withoutLastFrames = 4)?.let {
          Timber.d("animation delay: $it ms")
          // vibrate while animating
          vibrationHelper.vibrate(VibrationHelper.Vibration.SPIN)
          delay(it)
          vibrationHelper.stop()
        }
      }

      onFlipFinished(landing)
      isFlipping = false
    }
  }

  private suspend fun onFlipFinished(landing: Landing) {
    // note: the displayed counts are updated here (after the flip lands), not in flipCoin()
    state =
      state.copy(
        resultVisible = settings.textEnabled,
        shakeEnabled = settings.shakeEnabled && !state.paused,
        headsCount = state.stats.count(Coin.Value.HEADS),
        tailsCount = state.stats.count(Coin.Value.TAILS),
        streakCount = state.stats.streak
      )

    // ask for free internet points every 100 flips
    if (landing == Landing.HUNDREDTH) {
      _effectFlow.emit(MainEffect.ShowRateDialog)
    }

    val sound =
      when (landing) {
        Landing.HUNDREDTH -> SoundHelper.Sound.ONEUP // Happy Easter, Ryan!
        Landing.NEW_RECORD -> SoundHelper.Sound.STREAK
        Landing.ORDINARY -> SoundHelper.Sound.COIN
      }
    soundHelper.playSound(sound)

    vibrationHelper.vibrate(VibrationHelper.Vibration.THUD)
  }

  private fun onSetCoinColors(headsRim: Int, tailsRim: Int, fill: Int) {
    val colors = AnimationHelper.CoinColors(headsRim, tailsRim, fill)
    if (colors == coinColors) return
    coinColors = colors
    // a theme change has to redraw a custom coin, and the helper is a singleton that outlives the
    // activity recreation a light/dark switch causes. Guarded because RANDOM rerolls rather than
    // taking the cache hit: an unconditional call would draw a second, different coin.
    if (needsThemeColors()) generateAnimations()
  }

  // whether the coin on screen is drawn in the theme's colors, and so has to follow them. Which of
  // them it actually uses is AnimationHelper's to decide -- it is the one that knows the artwork.
  private fun needsThemeColors(): Boolean = CustomCoin.forPrefix(settings.coinPrefix) != null

  private fun generateAnimations() {
    val prefix = settings.coinPrefix
    val colors = if (needsThemeColors()) coinColors ?: return else null

    viewModelScope.launch {
      animationHelper.loadAnimationsForCoin(prefix, colors, settings.customCoinRim)
      showFace()
    }
  }

  private fun showFace() {
    if (isFlipping) return
    val animation = animationHelper.animations[state.result.permutation] ?: return
    state = state.copy(animation = animation, coinImageType = CoinImageType.IMAGE)
  }

  private fun onResetStats() {
    settings.resetStats()
    val stats = settings.loadStats()
    state =
      state.copy(
        stats = stats,
        headsCount = stats.count(Coin.Value.HEADS),
        tailsCount = stats.count(Coin.Value.TAILS),
        streakCount = stats.streak
      )
  }
}