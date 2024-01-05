package com.banasiak.coinflip.main

import android.hardware.SensorManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.settings.SettingsManager
import com.squareup.seismic.ShakeDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
  private val coin: Coin,
  private val sensorManager: SensorManager,
  private val settingsManager: SettingsManager
) : ViewModel(), LifecycleEventObserver {
  private var state = MainState()
  private val _stateFlow = MutableStateFlow<MainState>(state)
  val stateFlow: StateFlow<MainState> = _stateFlow

  private val _effectFlow = MutableSharedFlow<MainEffect>(extraBufferCapacity = 1)
  val effectFlow: SharedFlow<MainEffect> = _effectFlow

  private val shakeDetector = ShakeDetector { onShake() }

  fun postAction(action: MainAction) {
    when (action) {
      MainAction.TapAbout -> _effectFlow.tryEmit(MainEffect.NavToAbout)
      MainAction.TapCoin -> onTap()
      MainAction.TapDiagnostics -> _effectFlow.tryEmit(MainEffect.NavToDiagnostics)
      MainAction.TapSettings -> _effectFlow.tryEmit(MainEffect.NavToSettings)
    }
  }

  private fun onShake() {
    Timber.wtf("SHAKE!")
  }

  private fun onTap() {
    Timber.wtf("TAP!")
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
    if (settingsManager.shake) {
      Timber.d("shakeDetector.start()")
      shakeDetector.start(sensorManager, SensorManager.SENSOR_DELAY_GAME)
      shakeDetector.setSensitivity(settingsManager.force)
    }
  }

  private fun onPause() {
    Timber.d("shakeDetector.stop")
    shakeDetector.stop()
  }
}