package com.banasiak.coinflip.common

import android.graphics.drawable.AnimationDrawable
import android.os.Handler
import android.os.Looper

typealias AnimationCallback = () -> Unit

class CallbackAnimationDrawable : AnimationDrawable() {
  var onFinished: AnimationCallback? = null

  private val handler = Handler(Looper.getMainLooper())
  private val runnable = Runnable { onFinished?.invoke() }

  override fun start() {
    super.start()
    handler.postDelayed(runnable, totalDuration())
  }

  // call via onPause() to shut this thread down when the app drops to the background
  fun removeCallbacks() {
    handler.removeCallbacks(runnable)
  }

  private fun totalDuration(): Long {
    var totalDuration = 0L
    for (i in 0 until this.numberOfFrames) {
      totalDuration += getDuration(i)
    }
    return totalDuration
  }
}