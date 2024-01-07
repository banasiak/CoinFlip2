package com.banasiak.coinflip.main

import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.settings.SettingsManager
import com.banasiak.coinflip.ui.AnimationCallback
import com.banasiak.coinflip.util.AnimationHelper
import com.banasiak.coinflip.util.SoundHelper
import com.squareup.seismic.ShakeDetector
import dagger.hilt.android.lifecycle.HiltViewModel
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
  private val sensorManager: SensorManager,
  private val settings: SettingsManager,
  private val soundHelper: SoundHelper,
  private val vibrator: Vibrator,
  private val savedState: SavedStateHandle
) : ViewModel(), LifecycleEventObserver {
  companion object {
    private val VIBRATION_EFFECT = VibrationEffect.createOneShot(200, 255)
  }

  private var state = savedState.restore() ?: MainState()
  private val _stateFlow = MutableStateFlow<MainState>(state)
  val stateFlow: StateFlow<MainState> = _stateFlow

  private val _effectFlow = MutableSharedFlow<MainEffect>(extraBufferCapacity = 1)
  val effectFlow: SharedFlow<MainEffect> = _effectFlow

  private val shakeDetector = ShakeDetector { flipCoin() }

  fun postAction(action: MainAction) {
    Timber.d("postAction(): $action")
    when (action) {
      MainAction.TapCoin -> flipCoin()
    }
  }

  override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
    Timber.d("Lifecycle onStateChanged(): $event")
    when (event) {
      Lifecycle.Event.ON_RESUME -> onResume()
      Lifecycle.Event.ON_PAUSE -> onPause()
      else -> { }
    }
  }

  private fun onResume() {
    generateAnimations()

    val instructions = if (settings.shakeEnabled) R.string.instructions_tap_shake else R.string.instructions_tap

    state =
      state.copy(
        animation = null,
        instructionsText = instructions,
        placeholderVisible = true,
        resultVisible = false,
        stats = settings.loadStats(),
        statsVisible = settings.showStats
      )
    _stateFlow.tryEmit(state)

    _effectFlow.tryEmit(updateStatsEffect(state.stats))

    startShakeListener()
  }

  private fun onPause() {
    stopShakeListener()
    settings.persistStats(state.stats)
    savedState.save(state)
  }

  private fun flipCoin() {
    // pause the shake listener while the animation is in progress -- it will be re-enabled by the callback
    stopShakeListener()

    val result = coin.flip()

    val stats = state.stats.toMutableMap()
    val currentCount = stats.getOrDefault(result.value, 0)
    stats[result.value] = currentCount + 1

    var total = 0L
    stats.forEach { (k, v) -> total += v }

    val animation = animationHelper.animations[result.permutation]
    animation?.onFinished = onFlipFinished(total)

    state =
      state.copy(
        animation = animation,
        placeholderVisible = false,
        result = result,
        resultVisible = false,
        stats = stats
      )
    _stateFlow.tryEmit(state)
    _effectFlow.tryEmit(MainEffect.FlipCoin)
  }

  private fun onFlipFinished(total: Long): AnimationCallback {
    return {
      state = state.copy(resultVisible = true)
      _stateFlow.tryEmit(state)
      _effectFlow.tryEmit(updateStatsEffect(state.stats))

      if (settings.soundEnabled) {
        if (total % 100 == 0L) {
          soundHelper.playSound(SoundHelper.Sound.ONEUP) // Happy Easter, Ryan!
          _effectFlow.tryEmit(MainEffect.ShowRateDialog) // might as well ask for free internet points while we're at it
        } else {
          soundHelper.playSound(SoundHelper.Sound.COIN)
        }
      }

      if (settings.vibrateEnabled) {
        vibrator.vibrate(VIBRATION_EFFECT)
      }

      // re-enable the shake listener
      startShakeListener()
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

  private fun startShakeListener() {
    if (settings.shakeEnabled) {
      Timber.d("shakeDetector.start()")
      shakeDetector.start(sensorManager, SensorManager.SENSOR_DELAY_GAME)
      shakeDetector.setSensitivity(settings.shakeSensitivity)
    }
  }

  private fun stopShakeListener() {
    Timber.d("shakeDetector.stop()")
    shakeDetector.stop()
  }

  private fun SavedStateHandle.save(state: MainState) {
    this.set("state", state)
  }

  private fun SavedStateHandle.restore(): MainState? {
    return this.get<MainState>("state")
  }
}