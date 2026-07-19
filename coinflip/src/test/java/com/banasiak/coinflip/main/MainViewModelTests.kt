package com.banasiak.coinflip.main

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.banasiak.coinflip.MainDispatcherRule
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.settings.SettingsManager
import com.banasiak.coinflip.ui.DurationAnimationDrawable
import com.banasiak.coinflip.util.AnimationHelper
import com.banasiak.coinflip.util.SoundHelper
import com.banasiak.coinflip.util.VibrationHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class MainViewModelTests {
  private val animationHelper: AnimationHelper = mockk(relaxed = true)
  private val coin: Coin = mockk(relaxed = true)
  private val settings: SettingsManager = mockk(relaxed = true)
  private val soundHelper: SoundHelper = mockk(relaxed = true)
  private val vibrationHelper: VibrationHelper = mockk(relaxed = true)
  private val savedStateHandle: SavedStateHandle = mockk()

  private fun viewModel(): MainViewModel {
    return MainViewModel(animationHelper, coin, settings, soundHelper, vibrationHelper, savedStateHandle)
  }

  @BeforeEach
  fun beforeEach() {
    every { savedStateHandle.get<MainState>("state") } returns null
    every { savedStateHandle.set("state", any<MainState>()) } returns Unit
    every { settings.coinPrefix } returns "gw"
    every { settings.customHeadsText } returns null
    every { settings.customTailsText } returns null
    every { settings.loadStats() } returns mapOf(Coin.Value.HEADS to 0L, Coin.Value.TAILS to 0L)
  }

  @Test
  fun onResume_loads_stats_and_settings_into_state() =
    runTest {
      every { settings.shakeEnabled } returns true
      every { settings.dynamicColorsEnabled } returns true
      every { settings.showStats } returns true
      every { settings.showQuickReset } returns true
      every { settings.shakeSensitivity } returns 11
      every { settings.loadStats() } returns mapOf(Coin.Value.HEADS to 5L, Coin.Value.TAILS to 3L)

      val vm = viewModel()
      vm.postAction(MainAction.OnResume)

      val state = vm.stateFlow.value
      state.headsCount shouldBeEqualTo "5"
      state.tailsCount shouldBeEqualTo "3"
      state.dynamicColors shouldBeEqualTo true
      state.statsVisible shouldBeEqualTo true
      state.resetVisible shouldBeEqualTo true
      state.shakeEnabled shouldBeEqualTo true
      state.instructionsText shouldBeEqualTo R.string.instructions_tap_shake
    }

  @Test
  fun onResume_without_shake_shows_tap_only_instructions() =
    runTest {
      every { settings.shakeEnabled } returns false
      every { settings.showStats } returns false

      val vm = viewModel()
      vm.postAction(MainAction.OnResume)

      vm.stateFlow.value.instructionsText shouldBeEqualTo R.string.instructions_tap
    }

  @Test
  fun flip_increments_stats_and_reveals_counts_after_landing() =
    runTest {
      every { settings.animationEnabled } returns false
      every { settings.textEnabled } returns true
      every { coin.flip() } returns Coin.Result(Coin.Value.HEADS, AnimationHelper.Permutation.HEADS_HEADS)

      val vm = viewModel()

      vm.effectFlow.test {
        vm.postAction(MainAction.TapCoin)
        awaitItem() shouldBeEqualTo MainEffect.FlipCoin
      }

      val state = vm.stateFlow.value
      state.result.value shouldBeEqualTo Coin.Value.HEADS
      state.resultVisible shouldBeEqualTo true
      state.headsCount shouldBeEqualTo "1"
      state.tailsCount shouldBeEqualTo "0"
      state.stats[Coin.Value.HEADS] shouldBeEqualTo 1L
    }

  @Test
  fun reset_stats_zeroes_the_displayed_counts() =
    runTest {
      every { settings.loadStats() } returns emptyMap()

      val vm = viewModel()
      vm.postAction(MainAction.ResetStats)

      val state = vm.stateFlow.value
      state.headsCount shouldBeEqualTo "0"
      state.tailsCount shouldBeEqualTo "0"
    }

  @Test
  fun tap_about_emits_navigation_effect() =
    runTest {
      val vm = viewModel()
      vm.effectFlow.test {
        vm.postAction(MainAction.TapAbout)
        awaitItem() shouldBeEqualTo MainEffect.ToAbout
      }
    }

  @Test
  fun tap_settings_emits_navigation_effect() =
    runTest {
      val vm = viewModel()
      vm.effectFlow.test {
        vm.postAction(MainAction.TapSettings)
        awaitItem() shouldBeEqualTo MainEffect.ToSettings
      }
    }

  @Test
  fun tap_diagnostics_emits_navigation_effect() =
    runTest {
      val vm = viewModel()
      vm.effectFlow.test {
        vm.postAction(MainAction.TapDiagnostics)
        awaitItem() shouldBeEqualTo MainEffect.ToDiagnostics
      }
    }

  @Test
  fun shake_flips_the_coin_and_plays_the_coin_sound() =
    runTest {
      every { settings.animationEnabled } returns false
      every { coin.flip() } returns Coin.Result(Coin.Value.TAILS, AnimationHelper.Permutation.HEADS_TAILS)

      val vm = viewModel()
      vm.postAction(MainAction.Shake)

      verify { coin.flip() }
      verify { soundHelper.playSound(SoundHelper.Sound.COIN) }
      vm.stateFlow.value.tailsCount shouldBeEqualTo "1"
    }

  @Test
  fun on_pause_persists_stats_and_disables_shake() =
    runTest {
      val stats = mapOf(Coin.Value.HEADS to 2L, Coin.Value.TAILS to 1L)
      every { settings.shakeEnabled } returns true
      every { settings.loadStats() } returns stats

      val vm = viewModel()
      vm.postAction(MainAction.OnResume)
      vm.postAction(MainAction.OnPause)

      val state = vm.stateFlow.value
      state.paused shouldBeEqualTo true
      state.shakeEnabled shouldBeEqualTo false
      verify { settings.persistStats(stats) }
      verify { savedStateHandle.set("state", any<MainState>()) }
    }

  @Test
  fun state_is_restored_from_the_saved_state_handle() =
    runTest {
      val saved = MainState(headsCount = "7", tailsCount = "9", statsVisible = false)
      every { savedStateHandle.get<MainState>("state") } returns saved

      val vm = viewModel()

      vm.stateFlow.value shouldBeEqualTo saved
    }

  @Test
  fun taps_during_an_animated_flip_are_ignored() =
    runTest {
      val animation: DurationAnimationDrawable = mockk(relaxed = true)
      every { animation.duration(withoutLastFrames = 4) } returns 100L
      every { animationHelper.animations } returns mapOf(AnimationHelper.Permutation.HEADS_HEADS to animation)
      every { settings.animationEnabled } returns true
      every { coin.flip() } returns Coin.Result(Coin.Value.HEADS, AnimationHelper.Permutation.HEADS_HEADS)

      val vm = viewModel()
      vm.postAction(MainAction.TapCoin) // suspends at the animation delay
      vm.postAction(MainAction.TapCoin) // ignored while the first flip is mid-air
      advanceUntilIdle()

      verify(exactly = 1) { coin.flip() }

      vm.postAction(MainAction.TapCoin) // once landed, flipping works again
      advanceUntilIdle()

      verify(exactly = 2) { coin.flip() }
    }

  @Test
  fun every_hundredth_flip_requests_the_rate_dialog() =
    runTest {
      every { settings.animationEnabled } returns false
      every { settings.loadStats() } returns mapOf(Coin.Value.HEADS to 66L, Coin.Value.TAILS to 33L)
      every { coin.flip() } returns Coin.Result(Coin.Value.HEADS, AnimationHelper.Permutation.HEADS_HEADS)

      val vm = viewModel()
      vm.postAction(MainAction.OnResume)

      vm.effectFlow.test {
        vm.postAction(MainAction.TapCoin)
        awaitItem() shouldBeEqualTo MainEffect.FlipCoin
        awaitItem() shouldBeEqualTo MainEffect.ShowRateDialog
      }

      verify { soundHelper.playSound(SoundHelper.Sound.ONEUP) }
    }
}