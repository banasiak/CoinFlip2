package com.banasiak.coinflip.util

import android.content.res.Resources
import com.banasiak.coinflip.common.BuildInfo
import com.banasiak.coinflip.common.CoinType
import com.banasiak.coinflip.common.CustomCoin
import io.mockk.every
import io.mockk.mockk
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldNotBeEqualTo
import org.amshove.kluent.shouldNotContain
import org.junit.jupiter.api.Test
import java.time.Clock

class AnimationHelperTests {
  private val buildInfo: BuildInfo = mockk { every { packageName } returns PACKAGE }
  private val clock: Clock = mockk(relaxed = true)
  private val resources: Resources = mockk()
  private val customCoins: CustomCoinStore = mockk(relaxed = true)

  private fun helper() = AnimationHelper(buildInfo, clock, resources, customCoins)

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
    every { resources.getIdentifier(capture(requested), "drawable", PACKAGE) } returns 0

    repeat(100) { helper().getIdentifiersForPrefix(RANDOM) }

    // every draw names a coin that ships artwork, and the sentinel never does
    val drawn = requested.map { it.substringBeforeLast('_') }.toSet()
    drawn shouldNotContain RANDOM
    drawn.filterNot { it in flippablePrefixes }.shouldBeEmpty()
  }

  @Test
  fun `random rerolls rather than sticking to one coin`() {
    val requested = mutableListOf<String>()
    every { resources.getIdentifier(capture(requested), "drawable", PACKAGE) } returns 0

    repeat(50) { helper().getIdentifiersForPrefix(RANDOM) }

    // each call draws again, so the heads requests are not all for the same coin
    requested.filter { it.endsWith("_heads") }.distinct().size shouldBeGreaterThan 1
  }

  @Test
  fun `both faces of a random draw come from the same coin`() {
    val requested = mutableListOf<String>()
    every { resources.getIdentifier(capture(requested), "drawable", PACKAGE) } returns 0

    repeat(50) { helper().getIdentifiersForPrefix(RANDOM) }

    requested.chunked(2).forEach { (heads, tails) ->
      heads.removeSuffix("_heads") shouldBeEqualTo tails.removeSuffix("_tails")
    }
  }

  @Test
  fun `a shipped coin's cache key is nothing but its prefix`() {
    every { customCoins.revision } returns 99L

    // the revision and the rim belong to the custom coin; keying a shipped one on them would
    // regenerate eighty coins' worth of bitmaps every time the theme changed
    helper().cacheKey("jfk", rim(1, 2)) shouldBeEqualTo "jfk"
    helper().cacheKey("jfk", rim(3, 4)) shouldBeEqualTo "jfk"
  }

  @Test
  fun `the custom coin's key moves with the revision, so a replaced face is redrawn`() {
    // the prefix stays "custom" across a re-upload, so without this the old artwork would stay up
    every { customCoins.revision } returns 100L
    val before = helper().cacheKey(CustomCoin.PREFIX, rim(1, 2))

    every { customCoins.revision } returns 200L
    val after = helper().cacheKey(CustomCoin.PREFIX, rim(1, 2))

    before shouldNotBeEqualTo after
  }

  @Test
  fun `the custom coin's key moves with the rim colors, so a theme change is redrawn`() {
    // a light/dark switch recreates the activity but not this singleton, so the key is what notices
    every { customCoins.revision } returns 100L

    helper().cacheKey(CustomCoin.PREFIX, rim(1, 2)) shouldNotBeEqualTo
      helper().cacheKey(CustomCoin.PREFIX, rim(1, 3))
    helper().cacheKey(CustomCoin.PREFIX, rim(1, 2)) shouldNotBeEqualTo
      helper().cacheKey(CustomCoin.PREFIX, rim(9, 2))
  }

  @Test
  fun `an unchanged custom coin keeps its key, so nothing is regenerated for free`() {
    every { customCoins.revision } returns 100L

    helper().cacheKey(CustomCoin.PREFIX, rim(1, 2)) shouldBeEqualTo
      helper().cacheKey(CustomCoin.PREFIX, rim(1, 2))
  }

  @Test
  fun `switching the border off keys differently, so the ring is redrawn away`() {
    every { customCoins.revision } returns 100L

    // a null rim means no ring and an untinted edge; it has to force a regeneration like any other
    // change, or the coin keeps the ring the user just turned off
    helper().cacheKey(CustomCoin.PREFIX, null) shouldNotBeEqualTo
      helper().cacheKey(CustomCoin.PREFIX, rim(1, 2))
  }

  @Test
  fun `an unringed custom coin still keys on its revision`() {
    every { customCoins.revision } returns 100L
    val before = helper().cacheKey(CustomCoin.PREFIX, null)

    every { customCoins.revision } returns 200L

    before shouldNotBeEqualTo helper().cacheKey(CustomCoin.PREFIX, null)
  }

  @Test
  fun `a shipped coin keys on its prefix whether or not a rim is passed`() {
    every { customCoins.revision } returns 100L

    helper().cacheKey("jfk", null) shouldBeEqualTo "jfk"
    helper().cacheKey("jfk", rim(1, 2)) shouldBeEqualTo "jfk"
  }

  private fun rim(heads: Int, tails: Int) = AnimationHelper.RimColors(heads, tails)

  companion object {
    private const val PACKAGE = "com.banasiak.coinflip"
    private val RANDOM = CoinType.RANDOM.prefix
    private val flippablePrefixes = CoinType.flippable.map { it.prefix }.toSet()
  }
}