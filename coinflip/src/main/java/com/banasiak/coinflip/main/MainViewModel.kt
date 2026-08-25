package com.banasiak.coinflip.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.Coin
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
  private var state = savedState.restore() ?: MainState()
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

  fun postAction(action: MainAction) {
    Timber.d("postAction(): $action")
    when (action) {
      MainAction.OnPause -> onPause()
      MainAction.OnResume -> onResume()
      MainAction.ResetStats -> onResetStats()
      MainAction.TapAbout -> _effectFlow.tryEmit(MainEffect.ToAbout)
      MainAction.TapCoin -> flipCoin()
      MainAction.Shake -> flipCoin()
      MainAction.TapDiagnostics -> _effectFlow.tryEmit(MainEffect.ToDiagnostics)
      MainAction.TapSettings -> _effectFlow.tryEmit(MainEffect.ToSettings)
    }
  }

  private fun onResume() {
    generateAnimations()

    val instructions = if (settings.shakeEnabled) R.string.instructions_tap_shake else R.string.instructions_tap
    val stats = settings.loadStats()
    val showStreak = settings.showStreak
    // a run in progress is the thing the user wants to hold up and show somebody, so it is on screen
    // before a single flip is made this session. Seeding the result from it names the run rather than
    // leaving a bare number under a coin that has not been flipped yet. Below MIN_DRAWN_STREAK there
    // is no run to show, so the screen opens blank the way it always did.
    val standing = showStreak && stats.streak >= MIN_DRAWN_STREAK

    state =
      state.copy(
        animation = null,
        coinImageType = CoinImageType.PLACEHOLDER,
        dynamicColors = settings.dynamicColorsEnabled,
        instructionsText = instructions,
        labels = Pair(settings.customHeadsText, settings.customTailsText),
        paused = false,
        resetVisible = settings.showStats && settings.showQuickReset,
        result =
          if (standing) {
            Coin.Result(stats.streakValue, AnimationHelper.Permutation.UNKNOWN, stats.streakValue.customLabel(settings))
          } else {
            state.result
          },
        resultVisible = standing && settings.textEnabled,
        shakeEnabled = settings.shakeEnabled,
        shakeSensitivity = settings.shakeSensitivity,
        stats = stats,
        statsVisible = settings.showStats,
        streakVisible = showStreak,
        headsCount = stats.count(Coin.Value.HEADS),
        tailsCount = stats.count(Coin.Value.TAILS),
        streakCount = stats.streak
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

  private fun generateAnimations() {
    viewModelScope.launch {
      val prefix = settings.coinPrefix
      animationHelper.loadAnimationsForCoin(prefix)
    }
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