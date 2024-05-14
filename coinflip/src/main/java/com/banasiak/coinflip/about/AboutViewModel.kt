package com.banasiak.coinflip.about

import androidx.lifecycle.ViewModel
import com.banasiak.coinflip.common.BuildInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
  buildInfo: BuildInfo
) : ViewModel() {
  private val donateUrl = "https://eff.org/donate"
  private val rateUrl = "market://details?id=${buildInfo.packageName}"

  private var state = AboutState(buildInfo.versionName, buildInfo.versionCode)
  private val _stateFlow = MutableStateFlow(state)
  val stateFlow = _stateFlow.asStateFlow()

  private val _effectFlow = MutableSharedFlow<AboutEffect>(extraBufferCapacity = 1)
  val effectFlow = _effectFlow.asSharedFlow()

  fun postAction(action: AboutAction) {
    when (action) {
      is AboutAction.Donate -> _effectFlow.tryEmit(AboutEffect.LaunchUrl(donateUrl))
      is AboutAction.RateApp -> _effectFlow.tryEmit(AboutEffect.LaunchUrl(rateUrl))
    }
  }
}