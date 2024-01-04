package com.banasiak.coinflip.about

import androidx.lifecycle.ViewModel
import com.banasiak.coinflip.common.BuildInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
  buildInfo: BuildInfo
) : ViewModel() {
  private var state = AboutState(buildInfo.versionName, buildInfo.versionCode)
  private val _stateFlow = MutableStateFlow<AboutState>(state)
  val stateFlow: StateFlow<AboutState> = _stateFlow

  private val _effectFlow = MutableSharedFlow<AboutEffect>(extraBufferCapacity = 1)
  val effectFlow: SharedFlow<AboutEffect> = _effectFlow

  fun postAction(action: AboutAction) {
    when (action) {
      is AboutAction.RateApp -> _effectFlow.tryEmit(AboutEffect.ShowRateAppDialog)
    }
  }
}