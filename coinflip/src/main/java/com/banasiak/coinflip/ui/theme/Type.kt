package com.banasiak.coinflip.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

object Type {
  val diagnosticsLabel: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp)

  val diagnosticsValue: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.titleSmall.copy(fontSize = 18.sp)
}
