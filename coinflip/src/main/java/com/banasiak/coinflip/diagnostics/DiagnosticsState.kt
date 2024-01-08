package com.banasiak.coinflip.diagnostics

data class DiagnosticsState(
  val heads: Long = 0,
  val tails: Long = 0,
  val total: Long = 0,
  val headsCount: String = "",
  val headsRatio: String = "",
  val tailsCount: String = "",
  val tailsRatio: String = "",
  val totalCount: String = "",
  val totalRatio: String = "",
  val startTime: Long = 0,
  val elapsedTime: Long = 0,
  val formattedTime: String = "",
  val iterations: Long = 0
)

sealed class DiagnosticsAction {
  data object Wikipedia : DiagnosticsAction()
}

sealed class DiagnosticsEffect {
  data class LaunchUrl(val url: String) : DiagnosticsEffect()
}