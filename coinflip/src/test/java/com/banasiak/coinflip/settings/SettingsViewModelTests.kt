package com.banasiak.coinflip.settings

import app.cash.turbine.test
import com.banasiak.coinflip.MainDispatcherRule
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.common.Stats
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class SettingsViewModelTests {
  private val settings: SettingsManager = mockk(relaxed = true)

  private fun viewModel(): SettingsViewModel = SettingsViewModel(settings)

  @Test
  fun initial_state_is_loaded_from_the_settings_manager() =
    runTest {
      every { settings.coinPrefix } returns "jfk"
      every { settings.animationEnabled } returns false
      every { settings.shakeEnabled } returns false
      every { settings.soundEnabled } returns false
      every { settings.textEnabled } returns false
      every { settings.vibrateEnabled } returns false
      every { settings.showStats } returns false
      every { settings.showQuickReset } returns true
      every { settings.customHeadsText } returns "CROWN"
      every { settings.customTailsText } returns "SHIP"
      every { settings.dynamicColorsEnabled } returns true
      every { settings.secureRandom } returns true
      every { settings.force } returns ShakeForce.HIGH
      every { settings.showStreak } returns true
      every { settings.loadStats() } returns
        Stats(
          counts = mapOf(Coin.Value.HEADS to 7L, Coin.Value.TAILS to 5L),
          records = mapOf(Coin.Value.HEADS to 4L, Coin.Value.TAILS to 3L)
        )

      val vm = viewModel()

      vm.stateFlow.value shouldBeEqualTo
        SettingsState(
          coin = "jfk",
          animate = false,
          shake = false,
          sound = false,
          text = false,
          vibrate = false,
          stats = false,
          streak = true,
          quickReset = true,
          customHeads = "CROWN",
          customTails = "SHIP",
          dynamic = true,
          secureRandom = true,
          force = ShakeForce.HIGH,
          flipCount = 12,
          headsRecord = 4,
          tailsRecord = 3
        )
    }

  @Test
  fun set_animate_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetAnimate(false))

      verify { settings.update(Setting.ANIMATE, false) }
      vm.stateFlow.value.animate shouldBeEqualTo false
    }

  @Test
  fun set_coin_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetCoin("jfk"))

      verify { settings.update(Setting.COIN, "jfk") }
      vm.stateFlow.value.coin shouldBeEqualTo "jfk"
    }

  @Test
  fun set_shake_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetShake(false))

      verify { settings.update(Setting.SHAKE, false) }
      vm.stateFlow.value.shake shouldBeEqualTo false
    }

  @Test
  fun set_sound_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetSound(false))

      verify { settings.update(Setting.SOUND, false) }
      vm.stateFlow.value.sound shouldBeEqualTo false
    }

  @Test
  fun set_text_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetText(false))

      verify { settings.update(Setting.TEXT, false) }
      vm.stateFlow.value.text shouldBeEqualTo false
    }

  @Test
  fun set_vibrate_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetVibrate(false))

      verify { settings.update(Setting.VIBRATE, false) }
      vm.stateFlow.value.vibrate shouldBeEqualTo false
    }

  @Test
  fun set_stats_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetStats(false))

      verify { settings.update(Setting.STATS, false) }
      vm.stateFlow.value.stats shouldBeEqualTo false
    }

  @Test
  fun set_quick_reset_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetQuickReset(true))

      verify { settings.update(Setting.QUICK_RESET, true) }
      vm.stateFlow.value.quickReset shouldBeEqualTo true
    }

  @Test
  fun set_secure_random_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetSecureRandom(true))

      verify { settings.update(Setting.SECURE_RANDOM, true) }
      vm.stateFlow.value.secureRandom shouldBeEqualTo true
    }

  @Test
  fun set_custom_heads_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetCustomHeads("CROWN"))

      verify { settings.update(Setting.CUSTOM_HEADS_TEXT, "CROWN") }
      vm.stateFlow.value.customHeads shouldBeEqualTo "CROWN"
    }

  @Test
  fun set_custom_tails_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetCustomTails("SHIP"))

      verify { settings.update(Setting.CUSTOM_TAILS_TEXT, "SHIP") }
      vm.stateFlow.value.customTails shouldBeEqualTo "SHIP"
    }

  @Test
  fun set_force_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetForce(ShakeForce.HIGH))

      verify { settings.update(Setting.FORCE, ShakeForce.HIGH) }
      vm.stateFlow.value.force shouldBeEqualTo ShakeForce.HIGH
    }

  @Test
  fun toggling_dynamic_colors_requests_restart_on_back() =
    runTest {
      val vm = viewModel()

      vm.effectFlow.test {
        vm.postAction(SettingsAction.SetDynamic(true))
        awaitItem() shouldBeEqualTo SettingsEffect.EnableRestartOnBack
      }

      verify { settings.update(Setting.DYNAMIC, true) }
      vm.stateFlow.value.dynamic shouldBeEqualTo true
    }

  @Test
  fun reset_stats_emits_undo_snackbar_and_can_be_undone() =
    runTest {
      val previous =
        Stats(
          counts = mapOf(Coin.Value.HEADS to 7L, Coin.Value.TAILS to 2L),
          records = mapOf(Coin.Value.HEADS to 5L, Coin.Value.TAILS to 2L),
          streakValue = Coin.Value.HEADS,
          streak = 3L
        )
      every { settings.loadStats() } returns previous

      val vm = viewModel()

      vm.effectFlow.test {
        vm.postAction(SettingsAction.ResetStats)
        awaitItem() shouldBeEqualTo
          SettingsEffect.ShowSnackbar(
            message = R.string.stats_reset_message,
            actionLabel = R.string.undo,
            action = SettingsAction.UndoResetStats
          )
      }
      verify { settings.resetStats() }

      // the records go with the counts, and both come back together
      vm.stateFlow.value.flipCount shouldBeEqualTo 0L
      vm.stateFlow.value.headsRecord shouldBeEqualTo 0L
      vm.stateFlow.value.tailsRecord shouldBeEqualTo 0L

      vm.postAction(SettingsAction.UndoResetStats)
      verify { settings.persistStats(previous) }
      vm.stateFlow.value.flipCount shouldBeEqualTo 9L
      vm.stateFlow.value.headsRecord shouldBeEqualTo 5L
      vm.stateFlow.value.tailsRecord shouldBeEqualTo 2L
    }
}