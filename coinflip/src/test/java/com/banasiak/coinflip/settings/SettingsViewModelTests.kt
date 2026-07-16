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