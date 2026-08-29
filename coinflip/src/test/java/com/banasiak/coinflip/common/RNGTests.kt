package com.banasiak.coinflip.common

import com.banasiak.coinflip.FakeSharedPreferences
import com.banasiak.coinflip.settings.Setting
import com.banasiak.coinflip.settings.SettingsManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
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

  @Test
  fun `toggling the setting swaps the source without a restart`() {
    // wired against a real manager and store, because the swap only works if the listener the
    // manager registers is the one the store actually calls back
    val (store, manager) = realSettings()
    val rng = RNG(random, secureRandom, manager)
    rng.useSecureRandom.shouldBeFalse()

    manager.update(Setting.SECURE_RANDOM, true)

    rng.useSecureRandom.shouldBeTrue()
    rng.nextBoolean()
    verify(exactly = 1) { secureRandom.nextBoolean() }
    verify(exactly = 0) { random.nextBoolean() }
    store.values[Setting.SECURE_RANDOM.key] shouldBeEqualTo true
  }

  @Test
  fun `toggling back returns to the standard source`() {
    val (_, manager) = realSettings(Setting.SECURE_RANDOM.key to true)
    val rng = RNG(random, secureRandom, manager)
    rng.useSecureRandom.shouldBeTrue()

    manager.update(Setting.SECURE_RANDOM, false)

    rng.useSecureRandom.shouldBeFalse()
    rng.nextBoolean()
    verify(exactly = 1) { random.nextBoolean() }
  }

  @Test
  fun `an unrelated preference change does not disturb the source`() {
    // the callback fires for every key, so it has to filter rather than re-read on each write
    val (_, manager) = realSettings(Setting.SECURE_RANDOM.key to true)
    val rng = RNG(random, secureRandom, manager)

    manager.update(Setting.COIN, "jfk")

    rng.useSecureRandom.shouldBeTrue()
  }

  private fun realSettings(vararg stored: Pair<String, Any>): Pair<FakeSharedPreferences, SettingsManager> =
    FakeSharedPreferences(stored.toMap()).let { it to SettingsManager(it) }
}