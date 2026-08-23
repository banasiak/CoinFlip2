package com.banasiak.coinflip.extensions

import timber.log.Timber

fun Long.formatNumber(): String =
  try {
    "%,d".format(this)
  } catch (e: Exception) {
    Timber.w("Unable to format Long as number: $this")
    this.toString()
  }

fun Long.formatMilliseconds(): String =
  try {
    "%.3f".format(this.toDouble() / 1000)
  } catch (e: Exception) {
    Timber.w("Unable to format Long as seconds: $this")
    this.toString()
  }