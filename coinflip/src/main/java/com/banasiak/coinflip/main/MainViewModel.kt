package com.banasiak.coinflip.main

import androidx.lifecycle.ViewModel
import com.banasiak.coinflip.common.Coin
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(private val coin: Coin) : ViewModel() {
  private var state = MainState()
  private val _stateFlow = MutableStateFlow<MainState>(state)
  val stateFlow: StateFlow<MainState> = _stateFlow

  private val _effectFlow = MutableSharedFlow<MainEffect>(extraBufferCapacity = 1)
  val effectFlow: SharedFlow<MainEffect> = _effectFlow

  fun postAction(action: MainAction) {
    when (action) {
      MainAction.Shake -> onShake()
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
}