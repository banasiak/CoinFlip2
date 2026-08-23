package com.banasiak.coinflip.extensions

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.Locale

class LongTests {
  private val original: Locale = Locale.getDefault()

  @AfterEach
  fun restoreLocale() {
    Locale.setDefault(original)
  }

  @Test
  fun `numbers are grouped`() {
    Locale.setDefault(Locale.US)

    0L.formatNumber() shouldBeEqualTo "0"
    999L.formatNumber() shouldBeEqualTo "999"
    1_000L.formatNumber() shouldBeEqualTo "1,000"
    10_000_000L.formatNumber() shouldBeEqualTo "10,000,000"
    Long.MAX_VALUE.formatNumber() shouldBeEqualTo "9,223,372,036,854,775,807"
  }

  @Test
  fun `negative numbers keep their sign`() {
    Locale.setDefault(Locale.US)

    (-1_234L).formatNumber() shouldBeEqualTo "-1,234"
  }

  @Test
  fun `grouping follows the current locale`() {
    // the diagnostics screen renders these, so a German user sees dots where a US user sees commas
    Locale.setDefault(Locale.GERMANY)

    1_234_567L.formatNumber() shouldBeEqualTo "1.234.567"
  }

  @Test
  fun `milliseconds render as seconds to three places`() {
    Locale.setDefault(Locale.US)

    0L.formatMilliseconds() shouldBeEqualTo "0.000"
    1L.formatMilliseconds() shouldBeEqualTo "0.001"
    999L.formatMilliseconds() shouldBeEqualTo "0.999"
    1_500L.formatMilliseconds() shouldBeEqualTo "1.500"
    90_061_000L.formatMilliseconds() shouldBeEqualTo "90061.000"
  }

  @Test
  fun `the decimal separator follows the current locale`() {
    Locale.setDefault(Locale.GERMANY)

    1_500L.formatMilliseconds() shouldBeEqualTo "1,500"
  }
}