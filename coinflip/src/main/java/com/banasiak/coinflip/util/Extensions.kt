package com.banasiak.coinflip.util

import android.content.Intent
import android.net.Uri
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import timber.log.Timber

fun Fragment.navigate(@IdRes to: Int) {
  this.findNavController().navigate(to)
}

fun Fragment.launchUrl(url: String) {
  val uri = Uri.parse(url)
  val intent = Intent(Intent.ACTION_VIEW, uri)
  startActivity(intent)
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