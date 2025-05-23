package com.banasiak.coinflip.about

data class AboutState(
  val versionName: String,
  val versionCode: Int
)

sealed class AboutAction {
  data object Donate : AboutAction()
  data object RateApp : AboutAction()
  data object Website : AboutAction()
}

sealed class AboutEffect {
  data class LaunchUrl(val url: String) : AboutEffect()
}