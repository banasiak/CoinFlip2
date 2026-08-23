package com.banasiak.coinflip.common

import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Test

/**
 * The API levels are spelled out as literals rather than as `Build.VERSION_CODES` so that a
 * mistyped constant fails here instead of quietly gating a feature on the wrong release.
 */
class BuildInfoTests {
  @Test
  fun `snow cone is api 31, which gates dynamic colors`() {
    buildInfo(apiLevel = 30).isSnowCone().shouldBeFalse()
    buildInfo(apiLevel = 31).isSnowCone().shouldBeTrue()
    buildInfo(apiLevel = 36).isSnowCone().shouldBeTrue()
  }

  @Test
  fun `upside down cake is api 34`() {
    buildInfo(apiLevel = 33).isUpsideDownCake().shouldBeFalse()
    buildInfo(apiLevel = 34).isUpsideDownCake().shouldBeTrue()
  }

  @Test
  fun `baklava is api 36`() {
    buildInfo(apiLevel = 35).isBaklava().shouldBeFalse()
    buildInfo(apiLevel = 36).isBaklava().shouldBeTrue()
  }

  private fun buildInfo(apiLevel: Int) =
    BuildInfo(apiLevel = apiLevel, packageName = "com.banasiak.coinflip", versionName = "2026/07", versionCode = 74)
}