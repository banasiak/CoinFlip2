package com.banasiak.coinflip.util

import android.net.Uri
import android.os.Parcelable
import androidx.annotation.IdRes
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.Fragment
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.fragment.findNavController
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

  intent.launchUrl(requireContext(), Uri.parse(url))
}

fun String.formatNumber(): String {
  return try {
    this.toLong().formatNumber()
  } catch (e: Exception) {
    Timber.w("Unable to format String as number: $this")
    this
  }
}

fun Long.formatNumber(): String {
  return try {
    "%,d".format(this)
  } catch (e: Exception) {
    Timber.w("Unable to format Long as number: $this")
    this.toString()
  }
}

fun Long.formatMilliseconds(): String {
  return try {
    "%.3f".format(this.toDouble() / 1000)
  } catch (e: Exception) {
    Timber.w("Unable to format Long as seconds: $this")
    this.toString()
  }
}

fun <T> SavedStateHandle.save(state: T) {
  Timber.d("Persisting state to SavedStateHandle: $state")
  if (state !is Parcelable) {
    throw IllegalArgumentException("Unable to save state because it is not Parcelable")
  }
  this.set("state", state)
}

fun <T> SavedStateHandle.restore(): T? {
  val state = this.get<T>("state")
  Timber.d("Loaded state from SavedStateHandle: $state")
  return state
}