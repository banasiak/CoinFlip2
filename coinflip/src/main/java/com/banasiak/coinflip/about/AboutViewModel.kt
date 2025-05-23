package com.banasiak.coinflip.about

import androidx.lifecycle.ViewModel
import com.banasiak.coinflip.about.AboutEffect.LaunchUrl
import com.banasiak.coinflip.common.BuildInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
  buildInfo: BuildInfo
) : ViewModel() {
  private val donateUrl = "https://eff.org/donate"
  private val rateUrl = "market://details?id=${buildInfo.packageName}"
  private val websiteUrl = "https://www.banasiak.com"

  private var state = AboutState(buildInfo.versionName, buildInfo.versionCode)
    set(value) {
      field = value
      // emit the new state when it changes
      Timber.d("emitState(): $value")
      _stateFlow.tryEmit(value)
    }

  private val _stateFlow = MutableStateFlow(state)
  val stateFlow = _stateFlow.asStateFlow()

  private val _effectFlow = MutableSharedFlow<AboutEffect>(extraBufferCapacity = 1)
  val effectFlow = _effectFlow.asSharedFlow()

  fun postAction(action: AboutAction) {
    when (action) {
      AboutAction.Donate -> _effectFlow.tryEmit(LaunchUrl(donateUrl))
      AboutAction.RateApp -> _effectFlow.tryEmit(LaunchUrl(rateUrl))
      AboutAction.Website -> _effectFlow.tryEmit(LaunchUrl(websiteUrl))
    }
  }
}