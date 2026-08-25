package com.banasiak.coinflip.main

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.banasiak.coinflip.MainDispatcherRule
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.common.Stats
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

  private fun viewModel(): MainViewModel = MainViewModel(animationHelper, coin, settings, soundHelper, vibrationHelper, savedStateHandle)

  @BeforeEach
  fun beforeEach() {
    every { savedStateHandle.get<MainState>("state") } returns null
    every { savedStateHandle.set("state", any<MainState>()) } returns Unit
    every { settings.coinPrefix } returns "gw"
    every { settings.customHeadsText } returns null
    every { settings.customTailsText } returns null
    every { settings.loadStats() } returns Stats(counts = mapOf(Coin.Value.HEADS to 0L, Coin.Value.TAILS to 0L))
  }

  @Test
  fun onResume_loads_stats_and_settings_into_state() =
    runTest {
      every { settings.shakeEnabled } returns true
      every { settings.dynamicColorsEnabled } returns true
      every { settings.showStats } returns true
      every { settings.showQuickReset } returns true
      every { settings.shakeSensitivity } returns 11
      every { settings.loadStats() } returns Stats(counts = mapOf(Coin.Value.HEADS to 5L, Coin.Value.TAILS to 3L))

      val vm = viewModel()
      vm.postAction(MainAction.OnResume)

      val state = vm.stateFlow.value
      state.headsCount shouldBeEqualTo 5L
      state.tailsCount shouldBeEqualTo 3L
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
  fun flip_without_animation_updates_stats_and_result_immediately() =
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
      state.headsCount shouldBeEqualTo 1L
      state.tailsCount shouldBeEqualTo 0L
      state.stats.count(Coin.Value.HEADS) shouldBeEqualTo 1L
    }

  @Test
  fun reset_stats_zeroes_the_displayed_counts() =
    runTest {
      every { settings.loadStats() } returns Stats()

      val vm = viewModel()
      vm.postAction(MainAction.ResetStats)

      verify { settings.resetStats() }
      val state = vm.stateFlow.value
      state.headsCount shouldBeEqualTo 0L
      state.tailsCount shouldBeEqualTo 0L
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
      vm.stateFlow.value.tailsCount shouldBeEqualTo 1L
    }

  @Test
  fun on_pause_persists_stats_and_disables_shake() =
    runTest {
      val stats = Stats(counts = mapOf(Coin.Value.HEADS to 2L, Coin.Value.TAILS to 1L))
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
      val saved = MainState(headsCount = 7, tailsCount = 9, statsVisible = false)
      every { savedStateHandle.get<MainState>("state") } returns saved

      val vm = viewModel()

      vm.stateFlow.value shouldBeEqualTo saved
    }

  @Test
  fun animated_flip_defers_counts_and_ignores_taps_until_landed() =
    runTest {
      val animation: DurationAnimationDrawable = mockk(relaxed = true)
      every { animation.duration(withoutLastFrames = 4) } returns 100L
      every { animationHelper.animations } returns mapOf(AnimationHelper.Permutation.HEADS_HEADS to animation)
      every { settings.animationEnabled } returns true
      every { settings.textEnabled } returns true
      every { coin.flip() } returns Coin.Result(Coin.Value.HEADS, AnimationHelper.Permutation.HEADS_HEADS)

      val vm = viewModel()
      vm.postAction(MainAction.TapCoin) // suspends at the animation delay

      // mid-air: the flip is recorded internally but not yet revealed
      val midAir = vm.stateFlow.value
      midAir.stats.count(Coin.Value.HEADS) shouldBeEqualTo 1L
      midAir.headsCount shouldBeEqualTo 0L
      midAir.resultVisible shouldBeEqualTo false

      vm.postAction(MainAction.TapCoin) // ignored while the first flip is mid-air
      advanceUntilIdle()

      // landed: the counts and result text are revealed and the guard is released
      verify(exactly = 1) { coin.flip() }
      val landed = vm.stateFlow.value
      landed.headsCount shouldBeEqualTo 1L
      landed.resultVisible shouldBeEqualTo true

      vm.postAction(MainAction.TapCoin) // once landed, flipping works again
      advanceUntilIdle()

      verify(exactly = 2) { coin.flip() }
    }

  @Test
  fun every_hundredth_flip_requests_the_rate_dialog() =
    runTest {
      every { settings.animationEnabled } returns false
      every { settings.loadStats() } returns Stats(counts = mapOf(Coin.Value.HEADS to 66L, Coin.Value.TAILS to 33L))
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

  /** Flips [count] identical results through a view model that has already resumed with the streak on. */
  private fun streakingViewModel(
    value: Coin.Value = Coin.Value.HEADS,
    count: Int,
    stored: Stats = Stats()
  ): MainViewModel {
    every { settings.animationEnabled } returns false
    every { settings.showStreak } returns true
    every { settings.loadStats() } returns stored
    every { coin.flip() } returns Coin.Result(value, AnimationHelper.Permutation.HEADS_HEADS)

    val vm = viewModel()
    vm.postAction(MainAction.OnResume)
    repeat(count) { vm.postAction(MainAction.TapCoin) }
    return vm
  }

  @Test
  fun a_repeated_result_extends_the_run_and_a_change_restarts_it() =
    runTest {
      val vm = streakingViewModel(count = 3)
      vm.stateFlow.value.streakCount shouldBeEqualTo 3L

      // the other face lands: the run restarts at one rather than dropping to zero
      every { coin.flip() } returns Coin.Result(Coin.Value.TAILS, AnimationHelper.Permutation.HEADS_TAILS)
      vm.postAction(MainAction.TapCoin)

      val state = vm.stateFlow.value
      state.streakCount shouldBeEqualTo 1L
      state.stats.streakValue shouldBeEqualTo Coin.Value.TAILS
      // the run it just ended stands as the record for that face
      state.stats.record(Coin.Value.HEADS) shouldBeEqualTo 3L
    }

  @Test
  fun the_run_is_held_back_until_the_flip_lands() =
    runTest {
      val animation: DurationAnimationDrawable = mockk(relaxed = true)
      every { animation.duration(withoutLastFrames = 4) } returns 100L
      every { animationHelper.animations } returns mapOf(AnimationHelper.Permutation.HEADS_HEADS to animation)
      every { settings.animationEnabled } returns true
      every { settings.showStreak } returns true
      every { coin.flip() } returns Coin.Result(Coin.Value.HEADS, AnimationHelper.Permutation.HEADS_HEADS)

      val vm = viewModel()
      vm.postAction(MainAction.OnResume)
      vm.postAction(MainAction.TapCoin) // suspends at the animation delay

      // mid-air: the run is recorded but not yet drawn, same as the counts
      vm.stateFlow.value.stats.streak shouldBeEqualTo 1L
      vm.stateFlow.value.streakCount shouldBeEqualTo 0L

      advanceUntilIdle()

      vm.stateFlow.value.streakCount shouldBeEqualTo 1L
    }

  @Test
  fun beating_your_record_past_the_threshold_plays_the_fanfare() =
    runTest {
      val vm = streakingViewModel(count = 10)

      vm.stateFlow.value.stats.record(Coin.Value.HEADS) shouldBeEqualTo 10L
      verify(exactly = 1) { soundHelper.playSound(SoundHelper.Sound.STREAK) }
      // the nine flips before it were ordinary
      verify(exactly = 9) { soundHelper.playSound(SoundHelper.Sound.COIN) }
    }

  @Test
  fun a_new_record_below_the_threshold_stays_quiet() =
    runTest {
      // every one of these nine flips sets a record, and none is worth a five-second fanfare
      val vm = streakingViewModel(count = 9)

      vm.stateFlow.value.stats.record(Coin.Value.HEADS) shouldBeEqualTo 9L
      verify(exactly = 0) { soundHelper.playSound(SoundHelper.Sound.STREAK) }
    }

  @Test
  fun matching_your_record_without_beating_it_stays_quiet() =
    runTest {
      val stored = Stats(records = mapOf(Coin.Value.HEADS to 10L))
      streakingViewModel(count = 10, stored = stored)

      verify(exactly = 0) { soundHelper.playSound(SoundHelper.Sound.STREAK) }
    }

  @Test
  fun the_fanfare_is_silent_when_the_streak_display_is_off() =
    runTest {
      every { settings.animationEnabled } returns false
      every { settings.showStreak } returns false
      every { coin.flip() } returns Coin.Result(Coin.Value.HEADS, AnimationHelper.Permutation.HEADS_HEADS)

      val vm = viewModel()
      vm.postAction(MainAction.OnResume)
      // long enough that the fanfare would fire if the display were on
      repeat(10) { vm.postAction(MainAction.TapCoin) }

      verify(exactly = 0) { soundHelper.playSound(SoundHelper.Sound.STREAK) }
      // the record is still kept, it just does not announce itself
      vm.stateFlow.value.stats.record(Coin.Value.HEADS) shouldBeEqualTo 10L
    }

  @Test
  fun a_standing_run_is_on_screen_before_the_first_flip_of_a_session() =
    runTest {
      every { settings.showStreak } returns true
      every { settings.textEnabled } returns true
      every { settings.loadStats() } returns
        Stats(
          counts = mapOf(Coin.Value.HEADS to 9L, Coin.Value.TAILS to 4L),
          records = mapOf(Coin.Value.HEADS to 7L),
          streakValue = Coin.Value.HEADS,
          streak = 7L
        )

      val vm = viewModel()
      vm.postAction(MainAction.OnResume)

      val state = vm.stateFlow.value
      state.streakVisible shouldBeEqualTo true
      state.streakCount shouldBeEqualTo 7L
      // named, not a bare number: the run says which face it is made of
      state.result.value shouldBeEqualTo Coin.Value.HEADS
      state.resultVisible shouldBeEqualTo true
    }

  @Test
  fun a_standing_run_of_one_is_not_worth_showing() =
    runTest {
      every { settings.showStreak } returns true
      every { settings.textEnabled } returns true
      every { settings.loadStats() } returns Stats(streakValue = Coin.Value.HEADS, streak = 1L)

      val vm = viewModel()
      vm.postAction(MainAction.OnResume)

      val state = vm.stateFlow.value
      // the run is still tracked -- it is simply not a run yet
      state.streakCount shouldBeEqualTo 1L
      // so the screen opens blank rather than naming last session's final flip
      state.resultVisible shouldBeEqualTo false
    }

  @Test
  fun a_standing_run_is_not_drawn_when_the_streak_display_is_off() =
    runTest {
      every { settings.showStreak } returns false
      every { settings.textEnabled } returns true
      every { settings.loadStats() } returns Stats(streakValue = Coin.Value.HEADS, streak = 7L)

      val vm = viewModel()
      vm.postAction(MainAction.OnResume)

      val state = vm.stateFlow.value
      state.streakVisible shouldBeEqualTo false
      // the result text keeps its old behaviour of starting blank
      state.resultVisible shouldBeEqualTo false
    }

  @Test
  fun resetting_clears_the_run_along_with_the_counts() =
    runTest {
      val vm = streakingViewModel(count = 3)
      vm.stateFlow.value.streakCount shouldBeEqualTo 3L

      every { settings.loadStats() } returns Stats()
      vm.postAction(MainAction.ResetStats)

      val state = vm.stateFlow.value
      state.streakCount shouldBeEqualTo 0L
      state.stats.record(Coin.Value.HEADS) shouldBeEqualTo 0L
    }
}