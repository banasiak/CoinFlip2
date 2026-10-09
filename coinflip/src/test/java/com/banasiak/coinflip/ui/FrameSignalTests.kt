package com.banasiak.coinflip.ui

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.junit.jupiter.api.Test

class FrameSignalTests {
  private val frames = FrameSignal()

  private fun show(range: IntRange) = range.forEach(frames::shown)

  @Test
  fun the_wait_ends_when_the_run_reaches_the_frame() =
    runTest {
      var drawn: Boolean? = null
      launch { drawn = frames.await(45, TIMEOUT) }
      runCurrent()

      show(0..44)
      runCurrent()
      drawn.shouldBeNull()

      frames.shown(45)
      runCurrent()
      drawn shouldBeEqualTo true
    }

  @Test
  fun frames_from_before_the_run_restarts_belong_to_the_last_flip() =
    runTest {
      var drawn: Boolean? = null
      launch { drawn = frames.await(45, TIMEOUT) }
      runCurrent()

      // the view re-selects the frame the last flip stopped on, which is past the one awaited
      frames.shown(48)
      runCurrent()
      drawn.shouldBeNull()

      show(0..45)
      runCurrent()
      drawn shouldBeEqualTo true
    }

  @Test
  fun a_frame_past_the_one_awaited_ends_the_wait() =
    runTest {
      var drawn: Boolean? = null
      launch { drawn = frames.await(45, TIMEOUT) }
      runCurrent()

      frames.shown(0)
      frames.shown(47)
      runCurrent()
      drawn shouldBeEqualTo true
    }

  @Test
  fun the_wait_gives_up_once_the_timeout_passes() =
    runTest {
      var drawn: Boolean? = null
      launch { drawn = frames.await(45, TIMEOUT) }
      runCurrent()

      // the view stopped drawing partway through the run
      show(0..20)
      advanceTimeBy(TIMEOUT - 1)
      drawn.shouldBeNull()

      advanceTimeBy(2)
      drawn shouldBeEqualTo false
      // and stops listening, so a later run cannot resume a wait that already ended
      show(0..45)
      runCurrent()
      drawn shouldBeEqualTo false
    }

  @Test
  fun cancelling_an_old_wait_leaves_the_new_one_listening() =
    runTest {
      val old = launch { frames.await(45, TIMEOUT) }
      runCurrent()
      var drawn: Boolean? = null
      launch { drawn = frames.await(45, TIMEOUT) }
      runCurrent()

      old.cancel()
      runCurrent()
      show(0..45)
      runCurrent()

      drawn shouldBeEqualTo true
    }

  private companion object {
    const val TIMEOUT = 1_000L
  }
}