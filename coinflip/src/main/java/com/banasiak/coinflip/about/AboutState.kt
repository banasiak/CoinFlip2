package com.banasiak.coinflip.about

data class AboutState(
  val versionName: String,
  val versionCode: Int
)

sealed class AboutAction {
  data object Back : AboutAction()

  data object Donate : AboutAction()

  data object RateApp : AboutAction()
}

sealed class AboutEffect {
  data class LaunchUrl(val url: String) : AboutEffect()

  data object NavBack : AboutEffect()
}