package com.banasiak.coinflip.diagnostics

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.banasiak.coinflip.MainDispatcherRule
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.settings.SettingsManager
import com.banasiak.coinflip.util.AnimationHelper
import com.banasiak.coinflip.util.SoundHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Clock

@ExtendWith(MainDispatcherRule::class)
class DiagnosticsViewModelTests {
  private val clock: Clock = mockk()
  private val coin: Coin = mockk(relaxed = true)
  private val settingsManager: SettingsManager = mockk(relaxed = true)
  private val soundHelper: SoundHelper = mockk(relaxed = true)
  private val savedStateHandle: SavedStateHandle = mockk()

  private fun viewModel(): DiagnosticsViewModel {
    // Dispatchers.Main is the UnconfinedTestDispatcher here, so runTest virtual time drives the flip loop
    return DiagnosticsViewModel(clock, coin, settingsManager, soundHelper, Dispatchers.Main, savedStateHandle)
  }

  @BeforeEach
  fun beforeEach() {
    every { savedStateHandle.get<DiagnosticsState>("state") } returns null
    every { clock.millis() } returnsMany listOf(1000L, 2500L)

    // not *that* relaxed, mockk
    every { settingsManager.customHeadsText } returns null
    every { settingsManager.customTailsText } returns null
  }

  @Test
  fun setting_iterations_persists_the_value_and_reruns_the_test() =
    runTest {
      every { settingsManager.diagnosticsIterations } returns 1L
      every { coin.flip() } returns Coin.Result(Coin.Value.TAILS, AnimationHelper.Permutation.HEADS_TAILS)

      val vm = viewModel()
      vm.postAction(DiagnosticsAction.Start)
      advanceUntilIdle()
      vm.stateFlow.value.total shouldBeEqualTo 1L

      vm.postAction(DiagnosticsAction.SetIterations(2L))
      advanceUntilIdle()

      verify { settingsManager.update(SettingsManager.Settings.DIAGNOSTICS, "2") }
      // asserted on the settled value rather than the emissions: the reset is conflated away by
      // StateFlow. A run that had already finished would early-return, so reaching 2 is what
      // proves the state was cleared and the loop restarted.
      val state = vm.stateFlow.value
      state.iterations shouldBeEqualTo 2L
      state.total shouldBeEqualTo 2L
      state.tailsCount shouldBeEqualTo "2"
    }

  @Test
  fun out_of_range_iteration_counts_are_rejected() =
    runTest {
      every { settingsManager.diagnosticsIterations } returns 1L
      every { coin.flip() } returns Coin.Result(Coin.Value.TAILS, AnimationHelper.Permutation.HEADS_TAILS)

      val vm = viewModel()

      vm.postAction(DiagnosticsAction.SetIterations(0L))
      vm.postAction(DiagnosticsAction.SetIterations(-1L))
      // above this the loop would never finish, and it resumes from saved progress on every reopen
      vm.postAction(DiagnosticsAction.SetIterations(MAX_ITERATIONS + 1))
      advanceUntilIdle()

      verify(exactly = 0) { settingsManager.update(SettingsManager.Settings.DIAGNOSTICS, any()) }
      vm.stateFlow.value.iterations shouldBeEqualTo 1L
    }

  @Test
  fun heads() =
    runTest {
      every { settingsManager.diagnosticsIterations } returns 1L
      every { coin.flip() } returns Coin.Result(Coin.Value.HEADS, AnimationHelper.Permutation.TAILS_HEADS)

      val vm = viewModel()
      val states = vm.stateFlow

      val initialState = DiagnosticsState(iterations = 1)
      val expectedState =
        DiagnosticsState(
          iterations = 1,
          heads = 1,
          tails = 0,
          total = 1,
          changes = 0,
          changesCount = "0",
          changesRatio = "[0.00%]",
          runValue = Coin.Value.HEADS,
          currentRun = 1,
          headsStreak = 1,
          tailsStreak = 0,
          headsCount = "1",
          headsRatio = "[100.00%]",
          tailsCount = "0",
          tailsRatio = "[0.00%]",
          totalCount = "1",
          totalRatio = "[100.00%]",
          startTime = 1000L,
          elapsedTime = 1500L,
          formattedTime = "1.500"
        )

      states.test {
        awaitItem() shouldBeEqualTo initialState
        vm.postAction(DiagnosticsAction.Start)
        awaitItem() shouldBeEqualTo expectedState
      }
    }

  @Test
  fun tails() =
    runTest {
      every { settingsManager.diagnosticsIterations } returns 1L
      every { coin.flip() } returns Coin.Result(Coin.Value.TAILS, AnimationHelper.Permutation.HEADS_TAILS)

      val vm = viewModel()
      val states = vm.stateFlow

      val initialState = DiagnosticsState(iterations = 1)
      val expectedState =
        DiagnosticsState(
          iterations = 1,
          heads = 0,
          tails = 1,
          total = 1,
          changes = 0,
          changesCount = "0",
          changesRatio = "[0.00%]",
          runValue = Coin.Value.TAILS,
          currentRun = 1,
          headsStreak = 0,
          tailsStreak = 1,
          headsCount = "0",
          headsRatio = "[0.00%]",
          tailsCount = "1",
          tailsRatio = "[100.00%]",
          totalCount = "1",
          totalRatio = "[100.00%]",
          startTime = 1000L,
          elapsedTime = 1500L,
          formattedTime = "1.500"
        )

      states.test {
        awaitItem() shouldBeEqualTo initialState
        vm.postAction(DiagnosticsAction.Start)
        awaitItem() shouldBeEqualTo expectedState
      }
    }

  @Test
  fun five_iterations() =
    runTest {
      val flips =
        listOf(
          Coin.Result(Coin.Value.HEADS, AnimationHelper.Permutation.HEADS_HEADS),
          Coin.Result(Coin.Value.TAILS, AnimationHelper.Permutation.HEADS_TAILS),
          Coin.Result(Coin.Value.HEADS, AnimationHelper.Permutation.TAILS_HEADS),
          Coin.Result(Coin.Value.TAILS, AnimationHelper.Permutation.TAILS_TAILS),
          Coin.Result(Coin.Value.TAILS, AnimationHelper.Permutation.TAILS_TAILS)
        )
      every { settingsManager.diagnosticsIterations } returns flips.size.toLong()
      every { coin.flip() } returnsMany flips

      val vm = viewModel()
      val states = vm.stateFlow

      val initialState = DiagnosticsState(iterations = 5)
      val expectedState =
        DiagnosticsState(
          iterations = 5,
          heads = 2,
          tails = 3,
          total = 5,
          // H T H T T -- three changes, and the run left open at the end is two long
          changes = 3,
          changesCount = "3",
          changesRatio = "[60.00%]",
          runValue = Coin.Value.TAILS,
          currentRun = 2,
          headsStreak = 1,
          tailsStreak = 2,
          headsCount = "2",
          headsRatio = "[40.00%]",
          tailsCount = "3",
          tailsRatio = "[60.00%]",
          totalCount = "5",
          totalRatio = "[100.00%]",
          startTime = 1000L,
          elapsedTime = 1500L,
          formattedTime = "1.500"
        )

      states.test {
        awaitItem() shouldBeEqualTo initialState
        vm.postAction(DiagnosticsAction.Start)
        awaitItem() shouldBeEqualTo expectedState
      }
    }

  @Test
  fun custom_labels() =
    runTest {
      every { savedStateHandle.get<DiagnosticsState>("state") } returns DiagnosticsState(finished = true)

      every { settingsManager.customHeadsText } returns "HEADS"
      every { settingsManager.customTailsText } returns "TAILS"
      val vm = viewModel()
      val states = vm.stateFlow

      val initialState = DiagnosticsState(finished = true)
      val expectedState = DiagnosticsState(finished = true, labels = Pair("HEADS", "TAILS"))

      states.test {
        awaitItem() shouldBeEqualTo initialState
        vm.postAction(DiagnosticsAction.Start)
        awaitItem() shouldBeEqualTo expectedState
      }
    }

  @Test
  fun wikipedia() =
    runTest {
      val vm = viewModel()
      val effects = vm.effectFlow

      effects.test {
        vm.postAction(DiagnosticsAction.Start)
        vm.postAction(DiagnosticsAction.Wikipedia)
        awaitItem() shouldBeEqualTo DiagnosticsEffect.LaunchUrl("https://w.wiki/3kSY")
      }
    }

  @Test
  fun turbo_mode() =
    runTest {
      every { savedStateHandle.get<DiagnosticsState>("state") } returns DiagnosticsState(iterations = 1L, turboMode = true)

      val vm = viewModel()
      vm.stateFlow.value shouldBeEqualTo DiagnosticsState(iterations = 1, turboMode = true)

      // subscribe before acting so the one-shot toast can't be missed
      vm.effectFlow.test {
        vm.postAction(DiagnosticsAction.Start)
        awaitItem() shouldBeEqualTo DiagnosticsEffect.ShowToast(R.string.turbo_mode)
        ensureAllEventsConsumed()
      }
      verify { soundHelper.playSound(SoundHelper.Sound.POWERUP) }
    }

  @Test
  fun turbo_mode_notice_already_shown() =
    runTest {
      every {
        savedStateHandle.get<DiagnosticsState>("state")
      } returns DiagnosticsState(iterations = 1L, turboMode = true, turboModeShown = true)

      val vm = viewModel()

      // subscribe before acting and drive the run to completion, so a spurious toast would be caught
      vm.effectFlow.test {
        vm.postAction(DiagnosticsAction.Start)
        advanceUntilIdle()
        expectNoEvents()
      }
      verify(exactly = 0) { soundHelper.playSound(any()) }
    }

  @Test
  fun back() =
    runTest {
      val vm = viewModel()

      vm.effectFlow.test {
        vm.postAction(DiagnosticsAction.Back)
        awaitItem() shouldBeEqualTo DiagnosticsEffect.NavBack
      }
    }

  @Test
  fun on_start_lifecycle_event_runs_diagnostics() =
    runTest {
      every { settingsManager.diagnosticsIterations } returns 1L
      every { coin.flip() } returns Coin.Result(Coin.Value.HEADS, AnimationHelper.Permutation.TAILS_HEADS)

      val vm = viewModel()

      vm.stateFlow.test {
        awaitItem() // initial
        vm.onStateChanged(mockk(), Lifecycle.Event.ON_START)
        awaitItem().total shouldBeEqualTo 1L
      }
      verify(exactly = 1) { coin.flip() }
    }

  @Test
  fun on_pause_lifecycle_event_saves_state() =
    runTest {
      every { savedStateHandle.set("state", any<DiagnosticsState>()) } returns Unit

      val vm = viewModel()
      vm.onStateChanged(mockk(), Lifecycle.Event.ON_PAUSE)

      verify { savedStateHandle.set("state", any<DiagnosticsState>()) }
    }

  @Test
  fun restored_partial_run_resumes_where_it_left_off() =
    runTest {
      every { clock.millis() } returns 2500L
      every {
        savedStateHandle.get<DiagnosticsState>("state")
      } returns DiagnosticsState(iterations = 5, heads = 1, tails = 1, total = 2, startTime = 1000L)
      every { coin.flip() } returnsMany
        listOf(
          Coin.Result(Coin.Value.HEADS, AnimationHelper.Permutation.TAILS_HEADS),
          Coin.Result(Coin.Value.HEADS, AnimationHelper.Permutation.HEADS_HEADS),
          Coin.Result(Coin.Value.TAILS, AnimationHelper.Permutation.HEADS_TAILS)
        )

      val vm = viewModel()

      vm.stateFlow.test {
        awaitItem() // restored state
        vm.postAction(DiagnosticsAction.Start)
        val final = awaitItem()
        final.heads shouldBeEqualTo 3L
        final.tails shouldBeEqualTo 2L
        final.total shouldBeEqualTo 5L
        // the original start time is retained so the elapsed clock includes the "paused" period
        final.startTime shouldBeEqualTo 1000L
        final.elapsedTime shouldBeEqualTo 1500L
      }
      verify(exactly = 3) { coin.flip() }
    }

  @Test
  fun total_equals_iterations() =
    runTest {
      val flips =
        listOf(
          Coin.Result(Coin.Value.HEADS, AnimationHelper.Permutation.HEADS_HEADS),
          Coin.Result(Coin.Value.TAILS, AnimationHelper.Permutation.HEADS_TAILS),
          Coin.Result(Coin.Value.HEADS, AnimationHelper.Permutation.TAILS_HEADS)
        )
      every { settingsManager.diagnosticsIterations } returns flips.size.toLong()
      every { coin.flip() } returnsMany flips

      val vm = viewModel()

      vm.stateFlow.test {
        awaitItem() // initial
        vm.postAction(DiagnosticsAction.Start)
        val finalState = awaitItem()
        finalState.heads + finalState.tails shouldBeEqualTo finalState.iterations
        finalState.total shouldBeEqualTo finalState.iterations
      }
    }

  @Test
  fun duplicate_start_does_not_double_count() =
    runTest {
      every { settingsManager.diagnosticsIterations } returns 1L
      every { coin.flip() } returns Coin.Result(Coin.Value.HEADS, AnimationHelper.Permutation.TAILS_HEADS)

      val vm = viewModel()

      vm.stateFlow.test {
        awaitItem() // initial
        vm.postAction(DiagnosticsAction.Start)
        vm.postAction(DiagnosticsAction.Start)
        val finalState = awaitItem()
        finalState.heads shouldBeEqualTo 1L
        finalState.total shouldBeEqualTo 1L
      }
      verify(exactly = 1) { coin.flip() }
    }
}