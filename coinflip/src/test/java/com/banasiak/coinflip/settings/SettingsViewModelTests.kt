package com.banasiak.coinflip.settings

import app.cash.turbine.test
import com.banasiak.coinflip.MainDispatcherRule
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.settings.SettingsManager.Settings
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

  private fun viewModel(): SettingsViewModel {
    return SettingsViewModel(settings)
  }

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
      every { settings.diagnosticsIterations } returns 5000L
      every { settings.dynamicColorsEnabled } returns true
      every { settings.secureRandom } returns true
      every { settings.forceValue } returns "high"

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
          quickReset = true,
          customHeads = "CROWN",
          customTails = "SHIP",
          diagnostics = "5000",
          dynamic = true,
          secureRandom = true,
          force = "high"
        )
    }

  @Test
  fun set_animate_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetAnimate(false))

      verify { settings.update(Settings.ANIMATE, false) }
      vm.stateFlow.value.animate shouldBeEqualTo false
    }

  @Test
  fun set_coin_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetCoin("jfk"))

      verify { settings.update(Settings.COIN, "jfk") }
      vm.stateFlow.value.coin shouldBeEqualTo "jfk"
    }

  @Test
  fun set_shake_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetShake(false))

      verify { settings.update(Settings.SHAKE, false) }
      vm.stateFlow.value.shake shouldBeEqualTo false
    }

  @Test
  fun set_sound_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetSound(false))

      verify { settings.update(Settings.SOUND, false) }
      vm.stateFlow.value.sound shouldBeEqualTo false
    }

  @Test
  fun set_text_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetText(false))

      verify { settings.update(Settings.TEXT, false) }
      vm.stateFlow.value.text shouldBeEqualTo false
    }

  @Test
  fun set_vibrate_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetVibrate(false))

      verify { settings.update(Settings.VIBRATE, false) }
      vm.stateFlow.value.vibrate shouldBeEqualTo false
    }

  @Test
  fun set_stats_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetStats(false))

      verify { settings.update(Settings.STATS, false) }
      vm.stateFlow.value.stats shouldBeEqualTo false
    }

  @Test
  fun set_quick_reset_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetQuickReset(true))

      verify { settings.update(Settings.QUICK_RESET, true) }
      vm.stateFlow.value.quickReset shouldBeEqualTo true
    }

  @Test
  fun set_secure_random_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetSecureRandom(true))

      verify { settings.update(Settings.SECURE_RANDOM, true) }
      vm.stateFlow.value.secureRandom shouldBeEqualTo true
    }

  @Test
  fun set_custom_heads_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetCustomHeads("CROWN"))

      verify { settings.update(Settings.CUSTOM_HEADS_TEXT, "CROWN") }
      vm.stateFlow.value.customHeads shouldBeEqualTo "CROWN"
    }

  @Test
  fun set_custom_tails_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetCustomTails("SHIP"))

      verify { settings.update(Settings.CUSTOM_TAILS_TEXT, "SHIP") }
      vm.stateFlow.value.customTails shouldBeEqualTo "SHIP"
    }

  @Test
  fun set_force_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetForce("high"))

      verify { settings.update(Settings.FORCE, "high") }
      vm.stateFlow.value.force shouldBeEqualTo "high"
    }

  @Test
  fun valid_diagnostics_value_is_persisted() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetDiagnostics("500"))

      verify { settings.update(Settings.DIAGNOSTICS, "500") }
      vm.stateFlow.value.diagnostics shouldBeEqualTo "500"
    }

  @Test
  fun invalid_diagnostics_value_shows_snackbar_and_is_not_persisted() =
    runTest {
      val vm = viewModel()

      vm.effectFlow.test {
        vm.postAction(SettingsAction.SetDiagnostics("0"))
        awaitItem() shouldBeEqualTo SettingsEffect.ShowSnackbar(R.string.invalid_iterations)
      }

      verify(exactly = 0) { settings.update(Settings.DIAGNOSTICS, any()) }
    }

  @Test
  fun non_numeric_diagnostics_value_shows_snackbar() =
    runTest {
      val vm = viewModel()

      vm.effectFlow.test {
        vm.postAction(SettingsAction.SetDiagnostics("abc"))
        awaitItem() shouldBeEqualTo SettingsEffect.ShowSnackbar(R.string.invalid_iterations)
      }
    }

  @Test
  fun negative_diagnostics_value_shows_snackbar_and_is_not_persisted() =
    runTest {
      val vm = viewModel()

      vm.effectFlow.test {
        vm.postAction(SettingsAction.SetDiagnostics("-100"))
        awaitItem() shouldBeEqualTo SettingsEffect.ShowSnackbar(R.string.invalid_iterations)
      }

      verify(exactly = 0) { settings.update(Settings.DIAGNOSTICS, any()) }
    }

  @Test
  fun toggling_dynamic_colors_requests_restart_on_back() =
    runTest {
      val vm = viewModel()

      vm.effectFlow.test {
        vm.postAction(SettingsAction.SetDynamic(true))
        awaitItem() shouldBeEqualTo SettingsEffect.EnableRestartOnBack
      }

      verify { settings.update(Settings.DYNAMIC, true) }
      vm.stateFlow.value.dynamic shouldBeEqualTo true
    }

  @Test
  fun reset_stats_emits_undo_snackbar_and_can_be_undone() =
    runTest {
      val previous = mapOf(Coin.Value.HEADS to 7L, Coin.Value.TAILS to 2L)
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

      vm.postAction(SettingsAction.UndoResetStats)
      verify { settings.persistStats(previous) }
    }
}