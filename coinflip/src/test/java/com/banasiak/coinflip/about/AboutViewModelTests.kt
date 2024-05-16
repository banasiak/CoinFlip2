package com.banasiak.coinflip.about

import app.cash.turbine.test
import com.banasiak.coinflip.MainDispatcherRule
import com.banasiak.coinflip.common.BuildInfo
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class AboutViewModelTests {
  private val buildInfo: BuildInfo = mockk()

  private fun viewModel(): AboutViewModel {
    return AboutViewModel(buildInfo)
  }

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
      val effects = vm.effectFlow

      backgroundScope.launch {
        vm.postAction(AboutAction.Donate)
      }

      effects.test {
        awaitItem() shouldBeEqualTo AboutEffect.LaunchUrl("https://eff.org/donate")
        ensureAllEventsConsumed()
      }
    }

  @Test
  fun `rate app button`() =
    runTest {
      val vm = viewModel()
      val effects = vm.effectFlow

      backgroundScope.launch {
        vm.postAction(AboutAction.RateApp)
      }

      effects.test {
        awaitItem() shouldBeEqualTo AboutEffect.LaunchUrl("market://details?id=packageName")
        ensureAllEventsConsumed()
      }
    }
}