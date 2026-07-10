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
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

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

  @OptIn(ExperimentalAtomicApi::class)
  private val isFlipping = AtomicBoolean(false)

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

    state =
      state.copy(
        animation = null,
        coinImageType = CoinImageType.PLACEHOLDER,
        dynamicColors = settings.dynamicColorsEnabled,
        instructionsText = instructions,
        labels = Pair(settings.customHeadsText, settings.customTailsText),
        paused = false,
        resetVisible = settings.showStats && settings.showQuickReset,
        resultVisible = false,
        shakeEnabled = settings.shakeEnabled,
        shakeSensitivity = settings.shakeSensitivity,
        stats = stats,
        statsVisible = settings.showStats,
        headsCount = stats.count(Coin.Value.HEADS),
        tailsCount = stats.count(Coin.Value.TAILS)
      )
  }

  private fun onPause() {
    state = state.copy(paused = true, shakeEnabled = false)

    settings.persistStats(state.stats)
    savedState.save(state)
  }

  @OptIn(ExperimentalAtomicApi::class)
  private fun flipCoin() {
    if (isFlipping.load()) {
      Timber.d("flipCoin() already in progress. Ignoring.")
      return
    }

    viewModelScope.launch {
      isFlipping.store(true)

      // the heart and soul of this entire endeavor
      val result = coin.flip()

      val stats = state.stats.toMutableMap()
      val currentCount = stats.getOrDefault(result.value, 0)
      stats[result.value] = currentCount + 1

      val animationEnabled = settings.animationEnabled
      val animation = animationHelper.animations[result.permutation]

      state =
        state.copy(
          animation = animation,
          coinImageType = if (animationEnabled) CoinImageType.ANIMATION else CoinImageType.IMAGE,
          result = result,
          resultVisible = false,
          shakeEnabled = false,
          stats = stats
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

      onFlipFinished()
      isFlipping.store(false)
    }
  }

  private suspend fun onFlipFinished() {
    // note: the displayed counts are updated here (after the flip lands), not in flipCoin()
    state =
      state.copy(
        resultVisible = settings.textEnabled,
        shakeEnabled = settings.shakeEnabled && !state.paused,
        headsCount = state.stats.count(Coin.Value.HEADS),
        tailsCount = state.stats.count(Coin.Value.TAILS)
      )

    // keeping it &#128175;...
    val isOneHundred = state.stats.values.sum() % 100 == 0L

    // ask for free internet points every 100 flips
    if (isOneHundred) {
      _effectFlow.emit(MainEffect.ShowRateDialog)
    }

    val sound = if (isOneHundred) SoundHelper.Sound.ONEUP else SoundHelper.Sound.COIN // Happy Easter, Ryan!
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
        tailsCount = stats.count(Coin.Value.TAILS)
      )
  }

  private fun Map<Coin.Value, Long>.count(value: Coin.Value): String {
    return (this[value] ?: 0).toString()
  }
}