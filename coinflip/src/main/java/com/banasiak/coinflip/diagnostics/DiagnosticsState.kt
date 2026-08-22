package com.banasiak.coinflip.diagnostics

import android.os.Parcelable
import androidx.annotation.StringRes
import kotlinx.parcelize.Parcelize

/**
 * Upper bound on the test size. Without one a mistyped value starts a run that never finishes and
 * silently resumes on every reopen, because the loop persists partial progress across pauses.
 */
const val MAX_ITERATIONS = 10_000_000L

@Parcelize
data class DiagnosticsState(
  val heads: Long = 0,
  val tails: Long = 0,
  val total: Long = 0,
  val headsCount: String = "0",
  val headsRatio: String = "[0%]",
  val tailsCount: String = "0",
  val tailsRatio: String = "[0%]",
  val totalCount: String = "0",
  val totalRatio: String = "[0%]",
  val startTime: Long = 0,
  val elapsedTime: Long = 0,
  val formattedTime: String = "",
  val iterations: Long = 0,
  val labels: Pair<String?, String?> = Pair(null, null),
  val finished: Boolean = false,
  val turboMode: Boolean = false,
  val turboModeShown: Boolean = false,
  val dynamicColors: Boolean = false
) : Parcelable

sealed class DiagnosticsAction {
  data object Back : DiagnosticsAction()
  data class SetIterations(val value: Long) : DiagnosticsAction()
  data object Start : DiagnosticsAction()
  data object Wikipedia : DiagnosticsAction()
}

sealed class DiagnosticsEffect {
  data class LaunchUrl(val url: String) : DiagnosticsEffect()
  data object NavBack : DiagnosticsEffect()
  data class ShowToast(@param:StringRes val text: Int) : DiagnosticsEffect()
}