package com.banasiak.coinflip.main

import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.AnimationCallback
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.settings.SettingsManager
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
  private val vibrator: Vibrator
) : ViewModel(), LifecycleEventObserver {
  companion object {
    private val VIBRATION_EFFECT = VibrationEffect.createOneShot(200, 255)
  }

  private var state = MainState()
  private val _stateFlow = MutableStateFlow<MainState>(state)
  val stateFlow: StateFlow<MainState> = _stateFlow

  private val _effectFlow = MutableSharedFlow<MainEffect>(extraBufferCapacity = 1)
  val effectFlow: SharedFlow<MainEffect> = _effectFlow

  private val shakeDetector = ShakeDetector { flipCoin() }

  fun postAction(action: MainAction) {
    when (action) {
      MainAction.TapAbout -> _effectFlow.tryEmit(MainEffect.NavToAbout)
      MainAction.TapCoin -> flipCoin()
      MainAction.TapDiagnostics -> _effectFlow.tryEmit(MainEffect.NavToDiagnostics)
      MainAction.TapSettings -> _effectFlow.tryEmit(MainEffect.NavToSettings)
    }
  }

  override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
    Timber.d("Lifecycle state change: $event")
    when (event) {
      Lifecycle.Event.ON_RESUME -> onResume()
      Lifecycle.Event.ON_PAUSE -> onPause()
      else -> { }
    }
  }

  private fun onResume() {
    generateAnimations()
    state =
      state.copy(
        animation = null,
        image = R.drawable.unknown,
        resultVisible = false,
        stats = settings.loadStats(),
        statsVisible = settings.stats
      )
    _stateFlow.tryEmit(state)
    _effectFlow.tryEmit(updateStats())

    startShakeListener()
  }

  private fun onPause() {
    stopShakeListener()
    settings.persistStats(state.stats)
  }

  private fun flipCoin() {
    // pause the shake listener while the animation is in progress -- it will be re-enabled by the callback
    stopShakeListener()

    val result = coin.flip()
    val animation = animationHelper.animations[result.permutation]
    animation?.onFinished = onFlipFinished()

    val stats = state.stats.toMutableMap()
    val currentCount = stats.getOrDefault(result.value, 0)
    stats[result.value] = currentCount + 1

    state =
      state.copy(
        animation = animation,
        image = null,
        result = result,
        resultVisible = false,
        stats = stats
      )
    _stateFlow.tryEmit(state)
    _effectFlow.tryEmit(MainEffect.FlipCoin)
  }

  private fun onFlipFinished(): AnimationCallback {
    return {
      state = state.copy(resultVisible = true)
      _stateFlow.tryEmit(state)
      _effectFlow.tryEmit(updateStats())

      if (settings.sound) {
        soundHelper.playSound(SoundHelper.Sound.COIN)
      }

      if (settings.vibrate) {
        vibrator.vibrate(VIBRATION_EFFECT)
      }

      // re-enable the shake listener
      startShakeListener()
    }
  }

  private fun generateAnimations() {
    viewModelScope.launch {
      // TODO -> load drawables for selected coin
      animationHelper.generateAnimations(R.drawable.gw_heads, R.drawable.gw_tails)
    }
  }

  private fun updateStats(): MainEffect.UpdateStats {
    return MainEffect.UpdateStats(
      headsCount = state.stats[Coin.Value.HEADS]!!,
      tailsCount = state.stats[Coin.Value.TAILS]!!
    )
  }

  private fun startShakeListener() {
    if (settings.shake) {
      Timber.d("shakeDetector.start()")
      shakeDetector.start(sensorManager, SensorManager.SENSOR_DELAY_GAME)
      shakeDetector.setSensitivity(settings.force)
    }
  }

  private fun stopShakeListener() {
    Timber.d("shakeDetector.stop()")
    shakeDetector.stop()
  }
}