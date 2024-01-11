package com.banasiak.coinflip.main

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.settings.SettingsManager
import com.banasiak.coinflip.util.AnimationHelper
import com.banasiak.coinflip.util.SoundHelper
import com.banasiak.coinflip.util.restore
import com.banasiak.coinflip.util.save
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
  private val animationHelper: AnimationHelper,
  private val coin: Coin,
  private val settings: SettingsManager,
  private val soundHelper: SoundHelper,
  private val vibrator: Vibrator,
  private val savedState: SavedStateHandle
) : ViewModel() {
  companion object {
    private val VIBRATION_EFFECT = VibrationEffect.createOneShot(200, 255)
  }

  private var state = savedState.restore() ?: MainState()
  private val _stateFlow = MutableStateFlow<MainState>(state)
  val stateFlow: StateFlow<MainState> = _stateFlow

  private val _effectFlow = MutableSharedFlow<MainEffect>(extraBufferCapacity = 1)
  val effectFlow: SharedFlow<MainEffect> = _effectFlow

  fun postAction(action: MainAction) {
    Timber.d("postAction(): $action")
    when (action) {
      MainAction.OnPause -> onPause()
      MainAction.OnResume -> onResume()
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

    state =
      state.copy(
        animation = null,
        instructionsText = instructions,
        paused = false,
        placeholderVisible = true,
        resultVisible = false,
        shakeEnabled = settings.shakeEnabled,
        shakeSensitivity = settings.shakeSensitivity,
        stats = settings.loadStats(),
        statsVisible = settings.showStats
      )
    _stateFlow.tryEmit(state)

    _effectFlow.tryEmit(updateStatsEffect(state.stats))
  }

  private fun onPause() {
    state = state.copy(paused = true, shakeEnabled = false)
    _stateFlow.tryEmit(state)

    settings.persistStats(state.stats)
    savedState.save(state)
  }

  private fun flipCoin() {
    viewModelScope.launch {
      // the heart and soul of this entire endeavor
      val result = coin.flip()

      val stats = state.stats.toMutableMap()
      val currentCount = stats.getOrDefault(result.value, 0)
      stats[result.value] = currentCount + 1

      val animation = animationHelper.animations[result.permutation]

      state =
        state.copy(
          animation = animation,
          placeholderVisible = false,
          result = result,
          resultVisible = false,
          shakeEnabled = false,
          stats = stats
        )
      _stateFlow.emit(state)
      _effectFlow.emit(MainEffect.FlipCoin)

      // an obtuse way of pausing while the animation renders, proceeding 80 ms (4 frames, or 1/2 flip) before completion
      animation?.duration(withoutLastFrames = 4)?.let {
        Timber.d("animation delay: $it ms")
        delay(it)
      }

      onFlipFinished()
    }
  }

  private suspend fun onFlipFinished() {
    state =
      state.copy(
        resultVisible = true,
        shakeEnabled = settings.shakeEnabled && !state.paused
      )
    _stateFlow.emit(state)
    _effectFlow.emit(updateStatsEffect(state.stats))

    // keeping it &#128175;...
    val isOneHundred = state.stats.values.sum() % 100 == 0L

    // ask for free internet points every 100 flips
    if (isOneHundred) {
      _effectFlow.emit(MainEffect.ShowRateDialog)
    }

    if (settings.soundEnabled) {
      val sound = if (isOneHundred) SoundHelper.Sound.ONEUP else SoundHelper.Sound.COIN // Happy Easter, Ryan!
      soundHelper.playSound(sound)
    }

    if (settings.vibrateEnabled) {
      vibrator.vibrate(VIBRATION_EFFECT)
    }
  }

  private fun generateAnimations() {
    viewModelScope.launch {
      val prefix = settings.coinPrefix
      animationHelper.loadAnimationsForCoin(prefix)
    }
  }

  private fun updateStatsEffect(stats: Map<Coin.Value, Long>): MainEffect.UpdateStats {
    return MainEffect.UpdateStats(
      headsCount = (stats[Coin.Value.HEADS] ?: 0).toString(),
      tailsCount = (stats[Coin.Value.TAILS] ?: 0).toString()
    )
  }
}