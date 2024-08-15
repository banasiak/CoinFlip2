package com.banasiak.coinflip.extensions

import android.net.Uri
import androidx.annotation.IdRes
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.banasiak.coinflip.util.ColorHelper
import timber.log.Timber

fun Fragment.navigate(@IdRes to: Int) {
  try {
    this.findNavController().navigate(to)
  } catch (e: IllegalArgumentException) {
    // you may think this isn't your fault, Google, but it certainly isn't mine...
    // https://issuetracker.google.com/issues/118975714
    Timber.w(e, "Caught navigation exception")
  }
}

fun Fragment.launchUrl(url: String, colors: ColorHelper.ThemedColors) {
  val intent =
    CustomTabsIntent.Builder()
      .setDefaultColorSchemeParams(
        CustomTabColorSchemeParams.Builder()
          .setNavigationBarColor(colors.primary)
          .setToolbarColor(colors.primary)
          .build()
      )
      .setColorSchemeParams(
        CustomTabsIntent.COLOR_SCHEME_DARK,
        CustomTabColorSchemeParams.Builder()
          .setNavigationBarColor(colors.primaryDark)
          .setToolbarColor(colors.primaryDark)
          .build()
      )
      .setUrlBarHidingEnabled(true)
      .build()

  try {
    intent.launchUrl(requireContext(), Uri.parse(url))
  } catch (e: Exception) {
    Timber.e(e, "Unable to launch intent")
  }
}