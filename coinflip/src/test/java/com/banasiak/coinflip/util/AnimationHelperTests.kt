package com.banasiak.coinflip.util

import android.content.res.Resources
import com.banasiak.coinflip.common.BuildInfo
import com.banasiak.coinflip.common.Coin
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
    every { customCoins.revision(any()) } returns 99L

    // the revision and the rim belong to the custom coin; keying a shipped one on them would
    // regenerate eighty coins' worth of bitmaps every time the theme changed
    helper().cacheKey("jfk", rim(1, 2), true) shouldBeEqualTo "jfk"
    helper().cacheKey("jfk", rim(3, 4), true) shouldBeEqualTo "jfk"
  }

  @Test
  fun `the custom coin's key moves with the revision, so a replaced face is redrawn`() {
    // the prefix stays "custom" across a re-upload, so without this the old artwork would stay up
    every { customCoins.revision(any()) } returns 100L
    val before = helper().cacheKey(CustomCoin.PHOTO.prefix, rim(1, 2), true)

    every { customCoins.revision(any()) } returns 200L
    val after = helper().cacheKey(CustomCoin.PHOTO.prefix, rim(1, 2), true)

    before shouldNotBeEqualTo after
  }

  @Test
  fun `the custom coin's key moves with the rim colors, so a theme change is redrawn`() {
    // a light/dark switch recreates the activity but not this singleton, so the key is what notices
    every { customCoins.revision(any()) } returns 100L

    helper().cacheKey(CustomCoin.PHOTO.prefix, rim(1, 2), true) shouldNotBeEqualTo
      helper().cacheKey(CustomCoin.PHOTO.prefix, rim(1, 3), true)
    helper().cacheKey(CustomCoin.PHOTO.prefix, rim(1, 2), true) shouldNotBeEqualTo
      helper().cacheKey(CustomCoin.PHOTO.prefix, rim(9, 2), true)
  }

  @Test
  fun `an unchanged custom coin keeps its key, so nothing is regenerated for free`() {
    every { customCoins.revision(any()) } returns 100L

    helper().cacheKey(CustomCoin.PHOTO.prefix, rim(1, 2), true) shouldBeEqualTo
      helper().cacheKey(CustomCoin.PHOTO.prefix, rim(1, 2), true)
  }

  @Test
  fun `switching the border off keys differently, so the ring is redrawn away`() {
    every { customCoins.revision(any()) } returns 100L

    // a null rim means no ring and an untinted edge; it has to force a regeneration like any other
    // change, or the coin keeps the ring the user just turned off
    helper().cacheKey(CustomCoin.PHOTO.prefix, null, true) shouldNotBeEqualTo
      helper().cacheKey(CustomCoin.PHOTO.prefix, rim(1, 2), true)
  }

  @Test
  fun `an unringed custom coin still keys on its revision`() {
    every { customCoins.revision(any()) } returns 100L
    val before = helper().cacheKey(CustomCoin.PHOTO.prefix, null, true)

    every { customCoins.revision(any()) } returns 200L

    before shouldNotBeEqualTo helper().cacheKey(CustomCoin.PHOTO.prefix, null, true)
  }

  @Test
  fun `a shipped coin keys on its prefix whether or not a rim is passed`() {
    every { customCoins.revision(any()) } returns 100L

    helper().cacheKey("jfk", null, true) shouldBeEqualTo "jfk"
    helper().cacheKey("jfk", rim(1, 2), true) shouldBeEqualTo "jfk"
  }

  @Test
  fun `a shipped coin's identity is nothing but its prefix`() {
    every { customCoins.revision(any()) } returns 99L

    helper().identity("jfk") shouldBeEqualTo "jfk"
  }

  @Test
  fun `the custom coin's identity moves with the revision, so a replaced face is a different coin`() {
    every { customCoins.revision(any()) } returns 100L
    val before = helper().identity(CustomCoin.PHOTO.prefix)

    every { customCoins.revision(any()) } returns 200L

    before shouldNotBeEqualTo helper().identity(CustomCoin.PHOTO.prefix)
  }

  @Test
  fun `a face resolves to the permutation that leaves it showing`() {
    // the coin restored on resume is the last frame of one of these, and a result that was not
    // flipped this session has no permutation of its own
    AnimationHelper.Permutation.landingOn(Coin.Value.HEADS) shouldBeEqualTo AnimationHelper.Permutation.HEADS_HEADS
    AnimationHelper.Permutation.landingOn(Coin.Value.TAILS) shouldBeEqualTo AnimationHelper.Permutation.TAILS_TAILS
    AnimationHelper.Permutation.landingOn(Coin.Value.UNKNOWN) shouldBeEqualTo AnimationHelper.Permutation.UNKNOWN
  }

  private fun rim(heads: Int, tails: Int) = AnimationHelper.CoinColors(heads, tails, FILL)

  companion object {
    private const val FILL = 7
    private const val PACKAGE = "com.banasiak.coinflip"
    private val RANDOM = CoinType.RANDOM.prefix
    private val flippablePrefixes = CoinType.flippable.map { it.prefix }.toSet()
  }
}