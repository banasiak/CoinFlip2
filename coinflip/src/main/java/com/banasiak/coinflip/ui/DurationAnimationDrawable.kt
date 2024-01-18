package com.banasiak.coinflip.ui

import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import timber.log.Timber

class DurationAnimationDrawable : AnimationDrawable() {
  private var totalDuration = 0L

  override fun addFrame(frame: Drawable, duration: Int) {
    super.addFrame(frame, duration)
    totalDuration += duration
  }

  fun duration(withoutLastFrames: Int = 0): Long {
    if (withoutLastFrames == 0) return totalDuration

    var skippedDuration = 0L
    for (i in numberOfFrames downTo numberOfFrames - withoutLastFrames) {
      skippedDuration += getDuration(i)
    }
    Timber.d("duration of last $withoutLastFrames frames = $skippedDuration ms")
    return totalDuration - skippedDuration
  }

  fun getLastFrame(): Drawable {
    return getFrame(numberOfFrames - 1)
  }
}