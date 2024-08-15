package com.banasiak.coinflip.extensions

import timber.log.Timber

fun String.formatNumber(): String {
  return try {
    this.toLong().formatNumber()
  } catch (e: Exception) {
    Timber.w("Unable to format String as number: $this")
    this
  }
}