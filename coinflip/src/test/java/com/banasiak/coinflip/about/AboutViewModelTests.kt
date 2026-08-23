package com.banasiak.coinflip.about

import app.cash.turbine.test
import com.banasiak.coinflip.MainDispatcherRule
import com.banasiak.coinflip.common.BuildInfo
import com.banasiak.coinflip.settings.SettingsManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class AboutViewModelTests {
  private val buildInfo: BuildInfo = mockk()
  private val settings: SettingsManager = mockk(relaxed = true)

  private fun viewModel(): AboutViewModel = AboutViewModel(buildInfo, settings)

  @BeforeEach
  fun beforeEach() {
    every { buildInfo.packageName } returns "packageName"
    every { buildInfo.versionName } returns "versionName"
    every { buildInfo.versionCode } returns 99
  }

  @Test
  fun `initial state`() =
    runTest {
      val initialState = AboutState(versionName = "versionName", versionCode = 99)

      val vm = viewModel()
      val states = vm.stateFlow
      val effects = vm.effectFlow

      states.test {
        awaitItem() shouldBeEqualTo initialState
        ensureAllEventsConsumed()
      }

      effects.test {
        expectNoEvents()
      }
    }

  @Test
  fun `donate button`() =
    runTest {
      val vm = viewModel()

      vm.effectFlow.test {
        vm.postAction(AboutAction.Donate)
        awaitItem() shouldBeEqualTo AboutEffect.LaunchUrl("https://eff.org/donate")
        ensureAllEventsConsumed()
      }
    }

  @Test
  fun `rate app button`() =
    runTest {
      val vm = viewModel()

      vm.effectFlow.test {
        vm.postAction(AboutAction.RateApp)
        awaitItem() shouldBeEqualTo AboutEffect.LaunchUrl("market://details?id=packageName")
        ensureAllEventsConsumed()
      }
    }

  @Test
  fun `website button`() =
    runTest {
      val vm = viewModel()

      vm.effectFlow.test {
        vm.postAction(AboutAction.Website)
        awaitItem() shouldBeEqualTo AboutEffect.LaunchUrl("https://www.banasiak.com")
        ensureAllEventsConsumed()
      }
    }

  @Test
  fun `back button`() =
    runTest {
      val vm = viewModel()

      vm.effectFlow.test {
        vm.postAction(AboutAction.Back)
        awaitItem() shouldBeEqualTo AboutEffect.NavBack
        ensureAllEventsConsumed()
      }
    }

  @Test
  fun `dynamic colors preference is reflected in initial state`() =
    runTest {
      every { settings.dynamicColorsEnabled } returns true

      val vm = viewModel()

      vm.stateFlow.value shouldBeEqualTo AboutState("versionName", 99, dynamicColors = true)
    }
}