package com.banasiak.coinflip.util

import android.content.res.Resources
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.BuildInfo
import io.mockk.every
import io.mockk.mockk
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotContain
import org.junit.jupiter.api.Test
import java.time.Clock

class AnimationHelperTests {
  private val buildInfo: BuildInfo = mockk { every { packageName } returns PACKAGE }
  private val clock: Clock = mockk(relaxed = true)
  private val resources: Resources = mockk()

  private fun helper() = AnimationHelper(buildInfo, clock, resources)

  @Test
  fun `a coin prefix resolves the two face drawables`() {
    every { resources.getIdentifier("jfk_heads", "drawable", PACKAGE) } returns 100
    every { resources.getIdentifier("jfk_tails", "drawable", PACKAGE) } returns 200

    helper().getIdentifiersForPrefix("jfk") shouldBeEqualTo Pair(100, 200)
  }

  @Test
  fun `an unknown prefix reports the zero identifier rather than throwing`() {
    every { resources.getIdentifier(any(), "drawable", PACKAGE) } returns 0

    helper().getIdentifiersForPrefix("doubloon") shouldBeEqualTo Pair(0, 0)
  }

  @Test
  fun `random resolves to one of the real coins and never to itself`() {
    val requested = mutableListOf<String>()
    every { resources.getStringArray(R.array.coins_values) } returns arrayOf("gw", "jfk", RANDOM)
    every { resources.getIdentifier(capture(requested), "drawable", PACKAGE) } returns 0

    repeat(100) { helper().getIdentifiersForPrefix(RANDOM) }

    // both real coins turn up, and the sentinel never does
    requested.toSet() shouldBeEqualTo setOf("gw_heads", "gw_tails", "jfk_heads", "jfk_tails")
    requested shouldNotContain "${RANDOM}_heads"
  }

  @Test
  fun `random rerolls rather than sticking to one coin`() {
    val requested = mutableListOf<String>()
    every { resources.getStringArray(R.array.coins_values) } returns arrayOf("gw", "jfk", RANDOM)
    every { resources.getIdentifier(capture(requested), "drawable", PACKAGE) } returns 0

    repeat(50) { helper().getIdentifiersForPrefix(RANDOM) }

    // each call draws again, so the heads requests are not all for the same coin
    requested.filter { it.endsWith("_heads") }.distinct().size shouldBeEqualTo 2
  }

  @Test
  fun `both faces of a random draw come from the same coin`() {
    val requested = mutableListOf<String>()
    every { resources.getStringArray(R.array.coins_values) } returns arrayOf("gw", "jfk", RANDOM)
    every { resources.getIdentifier(capture(requested), "drawable", PACKAGE) } returns 0

    repeat(50) { helper().getIdentifiersForPrefix(RANDOM) }

    requested.chunked(2).forEach { (heads, tails) ->
      heads.removeSuffix("_heads") shouldBeEqualTo tails.removeSuffix("_tails")
    }
  }

  companion object {
    private const val PACKAGE = "com.banasiak.coinflip"
    private const val RANDOM = "random"
  }
}