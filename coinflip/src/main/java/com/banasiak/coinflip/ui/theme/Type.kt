package com.banasiak.coinflip.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

object Type {
  // a section header that is smaller than the rows beneath it reads as a caption rather than a
  // heading, so this sits one step above the titleMedium used for preference titles
  val settingsHeader: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)

  val diagnosticsLabel: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp)

  val diagnosticsValue: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.titleSmall.copy(fontSize = 18.sp)
}