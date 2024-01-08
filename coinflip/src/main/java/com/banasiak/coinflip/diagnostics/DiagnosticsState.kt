package com.banasiak.coinflip.diagnostics

data class DiagnosticsState(
  val heads: Long = 0,
  val tails: Long = 0,
  val total: Long = 0,
  val headsCount: String = "0",
  val headsRatio: String = "0%",
  val tailsCount: String = "0",
  val tailsRatio: String = "0%",
  val totalCount: String = "0",
  val totalRatio: String = "0%",
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