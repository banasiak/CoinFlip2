package com.banasiak.coinflip.ui

import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class DurationAnimationDrawable : AnimationDrawable() {
  private val frames = FrameSignal()

  override fun selectDrawable(index: Int): Boolean {
    val changed = super.selectDrawable(index)
    frames.shown(index)
    return changed
  }

  /**
   * Suspends until the next run of this animation has played all but its final [withoutFinal]
   * frames, at the moment the first of those is selected to be drawn, and returns true. A run already
   * in progress does not count: the wait begins at its next restart from the first frame. Returns
   * false instead once the animation's whole declared duration has passed, as it does when its view
   * stops drawing mid-run.
   */
  suspend fun awaitFrames(withoutFinal: Int): Boolean =
    frames.await(numberOfFrames - withoutFinal, timeoutMillis = (0 until numberOfFrames).sumOf { getDuration(it).toLong() })

  fun getLastFrame(): Drawable = getFrame(numberOfFrames - 1)
}

// the frames an animation selects, for a coroutine waiting on one of them. Main thread only: frames
// are selected there, and the flip that waits runs there
internal class FrameSignal {
  private var waiter: ((Int) -> Unit)? = null

  fun shown(index: Int) {
    waiter?.invoke(index)
  }

  /** True once a run restarted after the call selects [index] or later; false if [timeoutMillis] pass first. */
  suspend fun await(index: Int, timeoutMillis: Long): Boolean =
    withTimeoutOrNull(timeoutMillis) {
      suspendCancellableCoroutine { continuation ->
        // until the run restarts, a selected frame is the last flip's: attaching the drawable
        // re-selects the frame it stopped on, which is past any frame awaited
        var restarted = false
        val waiting: (Int) -> Unit = { shown ->
          if (shown == 0) restarted = true
          if (restarted && shown >= index) {
            waiter = null
            continuation.resume(Unit)
          }
        }
        waiter = waiting
        continuation.invokeOnCancellation { if (waiter === waiting) waiter = null }
      }
    } != null
}