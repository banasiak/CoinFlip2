package com.banasiak.coinflip.diagnostics

data class DiagnosticsState(
  val headsCount: Long = 0,
  val headsRatio: String = "",
  val tailsCount: Long = 0,
  val tailsRatio: String = "",
  val totalCount: Long = 0,
  val totalRatio: String = "",
  val elapsedTime: Long = 0
)
