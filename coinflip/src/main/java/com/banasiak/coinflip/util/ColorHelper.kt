package com.banasiak.coinflip.util

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.ColorInt
import com.banasiak.coinflip.settings.SettingsManager
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class ColorHelper @Inject constructor(
  @param:ActivityContext private val context: Context,
  private val settings: SettingsManager
) {
  @SuppressLint("ResourceType")
  fun getThemedColors(): ThemedColors {
    val context = if (settings.dynamicColorsEnabled) DynamicColors.wrapContextIfAvailable(context) else context
    val colors = intArrayOf(com.google.android.material.R.attr.colorPrimary, com.google.android.material.R.attr.colorPrimaryDark)
    val attributes = context.obtainStyledAttributes(colors)
    val colorPrimary = attributes.getColor(0, 0)
    val colorPrimaryDark = attributes.getColor(1, 0)
    attributes.recycle()
    return ThemedColors(colorPrimary, colorPrimaryDark)
  }

  data class ThemedColors(@param:ColorInt val primary: Int, @param:ColorInt val primaryDark: Int)
}