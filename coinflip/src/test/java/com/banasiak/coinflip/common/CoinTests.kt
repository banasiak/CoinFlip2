package com.banasiak.coinflip.common

import com.banasiak.coinflip.settings.SettingsManager
import com.banasiak.coinflip.util.AnimationHelper
import io.mockk.every
import io.mockk.mockk
import org.amshove.kluent.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CoinTests {
  private val rng: RNG = mockk()
  private val settings: SettingsManager = mockk()

  @BeforeEach
  fun before() {
    every { settings.customHeadsText } returns "HEADS"
    every { settings.customTailsText } returns "TAILS"
  }

  @Test
  fun `heads - heads`() {
    // given
    every { rng.nextBoolean() } returnsMany listOf(true, true) // heads, heads
    val coin = Coin(rng, settings)
    coin.flip() // to set 'currentValue'

    // when
    val result = coin.flip()

    // then
    result.value shouldBe Coin.Value.HEADS
    result.permutation shouldBe AnimationHelper.Permutation.HEADS_HEADS
    result.customLabel shouldBe "HEADS"
  }

  @Test
  fun `heads - tails`() {
    // given
    every { rng.nextBoolean() } returnsMany listOf(true, false) // heads, tails
    val coin = Coin(rng, settings)
    coin.flip()

    // when
    val result = coin.flip()

    // then
    result.value shouldBe Coin.Value.TAILS
    result.permutation shouldBe AnimationHelper.Permutation.HEADS_TAILS
    result.customLabel shouldBe "TAILS"
  }

  @Test
  fun `tails - heads`() {
    // given
    every { rng.nextBoolean() } returnsMany listOf(false, true) // tails, heads
    val coin = Coin(rng, settings)
    coin.flip() // to set current value to tails

    // when
    val result = coin.flip()

    // then
    result.value shouldBe Coin.Value.HEADS
    result.permutation shouldBe AnimationHelper.Permutation.TAILS_HEADS
    result.customLabel shouldBe "HEADS"
  }

  @Test
  fun `tails - tails`() {
    // given
    every { rng.nextBoolean() } returnsMany listOf(false, false) // tails, tails
    val coin = Coin(rng, settings)
    coin.flip()

    // when
    val result = coin.flip()

    // then
    result.value shouldBe Coin.Value.TAILS
    result.permutation shouldBe AnimationHelper.Permutation.TAILS_TAILS
    result.customLabel shouldBe "TAILS"
  }

  @Test
  fun `a cleared custom label falls back to nothing so the localized default applies`() {
    // given the settings dialog stores a blank field as null rather than as the localized literal
    every { settings.customHeadsText } returns null
    every { rng.nextBoolean() } returns true
    val coin = Coin(rng, settings)

    // when
    val result = coin.flip()

    // then
    result.customLabel shouldBe null
  }

  @Test
  fun `the unknown face never carries a custom label`() {
    Coin.Value.UNKNOWN.customLabel(settings) shouldBe null
  }

  @Test
  fun `a restored face is where the next flip starts from`() {
    // given a coin that has never flipped, so it holds its default face
    every { rng.nextBoolean() } returns false // tails
    val coin = Coin(rng, settings)

    // when the screen reports the face it is showing
    coin.restoreFace(Coin.Value.TAILS)

    // then the flip after it animates from there rather than from the default
    coin.flip().permutation shouldBe AnimationHelper.Permutation.TAILS_TAILS
  }

  @Test
  fun `an unknown face leaves the coin on the one it holds`() {
    // given nothing on screen for the coin to agree with
    every { rng.nextBoolean() } returns true // heads
    val coin = Coin(rng, settings)

    // when
    coin.restoreFace(Coin.Value.UNKNOWN)

    // then the default face is still what the flip comes from
    coin.flip().permutation shouldBe AnimationHelper.Permutation.HEADS_HEADS
  }

  @Test
  fun `the coin reports which source it is flipping with`() {
    every { rng.useSecureRandom } returns true

    Coin(rng, settings).isSecure() shouldBe true
  }
}