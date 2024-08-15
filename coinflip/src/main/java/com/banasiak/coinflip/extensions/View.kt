package com.banasiak.coinflip.extensions

import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

fun View.applySystemBarInsets() {
  ViewCompat.setOnApplyWindowInsetsListener(this) { v: View, windowInsets: WindowInsetsCompat ->
    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
    v.updateLayoutParams<MarginLayoutParams> {
      leftMargin = insets.left
      rightMargin = insets.right
      topMargin = insets.top
      bottomMargin = insets.bottom
    }
    WindowInsetsCompat.CONSUMED
  }
}