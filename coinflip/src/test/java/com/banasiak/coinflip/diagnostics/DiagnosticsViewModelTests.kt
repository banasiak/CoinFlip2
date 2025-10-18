package com.banasiak.coinflip.diagnostics

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
import kotlinx.coroutines.launch
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
    return DiagnosticsViewModel(clock, coin, settingsManager, soundHelper, savedStateHandle)
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

      backgroundScope.launch {
        vm.postAction(DiagnosticsAction.Start)
      }

      states.test {
        awaitItem() shouldBeEqualTo initialState
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

      backgroundScope.launch {
        vm.postAction(DiagnosticsAction.Start)
      }

      states.test {
        awaitItem() shouldBeEqualTo initialState
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

      backgroundScope.launch {
        vm.postAction(DiagnosticsAction.Start)
      }

      states.test {
        awaitItem()shouldBeEqualTo initialState
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

      backgroundScope.launch {
        vm.postAction(DiagnosticsAction.Start)
      }

      states.test {
        awaitItem() shouldBeEqualTo initialState
        awaitItem() shouldBeEqualTo expectedState
      }
    }

  @Test
  fun wikipedia() =
    runTest {
      val vm = viewModel()
      val effects = vm.effectFlow

      backgroundScope.launch {
        vm.postAction(DiagnosticsAction.Start)
        vm.postAction(DiagnosticsAction.Wikipedia)
      }

      effects.test {
        awaitItem() shouldBeEqualTo DiagnosticsEffect.LaunchUrl("https://w.wiki/3kSY")
      }
    }

  @Test
  fun turbo_mode() =
    runTest {
      every { savedStateHandle.get<DiagnosticsState>("state") } returns DiagnosticsState(iterations = 1L, turboMode = true)

      val vm = viewModel()
      val states = vm.stateFlow
      val effects = vm.effectFlow

      backgroundScope.launch {
        vm.postAction(DiagnosticsAction.Start)
      }

      states.test {
        awaitItem() shouldBeEqualTo DiagnosticsState(iterations = 1, turboMode = true)
      }

      effects.test {
        awaitItem() shouldBeEqualTo DiagnosticsEffect.ShowToast(R.string.turbo_mode)
      }
    }

  @Test
  fun turbo_mode_notice_already_shown() =
    runTest {
      every {
        savedStateHandle.get<DiagnosticsState>("state")
      } returns DiagnosticsState(iterations = 1L, turboMode = true, turboModeShown = true)

      val vm = viewModel()
      val states = vm.stateFlow
      val effects = vm.effectFlow

      backgroundScope.launch {
        vm.postAction(DiagnosticsAction.Start)
      }

      states.test {
        awaitItem() shouldBeEqualTo DiagnosticsState(iterations = 1L, turboMode = true, turboModeShown = true)
      }

      effects.test {
        expectNoEvents()
      }
    }
}