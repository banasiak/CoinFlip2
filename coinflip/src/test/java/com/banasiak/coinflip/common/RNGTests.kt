package com.banasiak.coinflip.common

import com.banasiak.coinflip.settings.SettingsManager
import io.mockk.MockKSettings.relaxed
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.random.Random

class RNGTests {
  private val random: Random = mockk(relaxed = true)
  private val secureRandom: SecureRandom = mockk(relaxed = true)
  private val settings: SettingsManager = mockk(relaxed = true)

  @Test
  fun `use random`() {
    // given
    every { settings.secureRandom } returns false
    val rng = RNG(random, secureRandom, settings)

    // when
    rng.nextBoolean()

    // then
    verify(exactly = 1) { random.nextBoolean() }
    verify(exactly = 0) { secureRandom.nextBoolean() }
  }

  @Test
  fun `use secure random`() {
    // given
    every { settings.secureRandom } returns true
    val rng = RNG(random, secureRandom, settings)

    // when
    rng.nextBoolean()

    // then
    verify(exactly = 0) { random.nextBoolean() }
    verify(exactly = 1) { secureRandom.nextBoolean() }
  }
}