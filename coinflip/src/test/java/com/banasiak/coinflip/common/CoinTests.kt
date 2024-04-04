package com.banasiak.coinflip.common

import com.banasiak.coinflip.util.AnimationHelper
import io.mockk.every
import io.mockk.mockk
import org.amshove.kluent.shouldBe
import org.junit.jupiter.api.Test

class CoinTests {
  private val rng: RNG = mockk()

  @Test
  fun `heads - heads`() {
    // given
    every { rng.nextBoolean() } returnsMany listOf(true, true) // heads, heads
    val coin = Coin(rng)
    coin.flip() // to set 'currentValue'

    // when
    val result = coin.flip()

    // then
    result.value shouldBe Coin.Value.HEADS
    result.permutation shouldBe AnimationHelper.Permutation.HEADS_HEADS
  }

  @Test
  fun `heads - tails`() {
    // given
    every { rng.nextBoolean() } returnsMany listOf(true, false) // tails, heads
    val coin = Coin(rng)
    coin.flip()

    // when
    val result = coin.flip()

    // then
    result.value shouldBe Coin.Value.TAILS
    result.permutation shouldBe AnimationHelper.Permutation.HEADS_TAILS
  }

  @Test
  fun `tails - heads`() {
    // given
    every { rng.nextBoolean() } returnsMany listOf(false, true) // tails, heads
    val coin = Coin(rng)
    coin.flip() // to set current value to tails

    // when
    val result = coin.flip()

    // then
    result.value shouldBe Coin.Value.HEADS
    result.permutation shouldBe AnimationHelper.Permutation.TAILS_HEADS
  }

  @Test
  fun `tails - tails`() {
    // given
    every { rng.nextBoolean() } returnsMany listOf(false, false) // tails, tails
    val coin = Coin(rng)
    coin.flip()

    // when
    every { rng.nextBoolean() } returns false // heads
    val result = coin.flip()

    // then
    result.value shouldBe Coin.Value.TAILS
    result.permutation shouldBe AnimationHelper.Permutation.TAILS_TAILS
  }
}