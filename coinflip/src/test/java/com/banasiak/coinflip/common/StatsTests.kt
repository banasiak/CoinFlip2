package com.banasiak.coinflip.common

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class StatsTests {
  private fun Stats.flip(vararg values: Coin.Value): Stats = values.fold(this) { stats, value -> stats.afterFlip(value) }

  @Test
  fun `an empty record reads as zero rather than throwing`() {
    val stats = Stats()

    stats.count(Coin.Value.HEADS) shouldBeEqualTo 0L
    stats.record(Coin.Value.TAILS) shouldBeEqualTo 0L
    stats.total shouldBeEqualTo 0L
  }

  @Test
  fun `a flip bumps its own count and leaves the other alone`() {
    val stats = Stats().flip(Coin.Value.HEADS, Coin.Value.HEADS)

    stats.count(Coin.Value.HEADS) shouldBeEqualTo 2L
    stats.count(Coin.Value.TAILS) shouldBeEqualTo 0L
    stats.total shouldBeEqualTo 2L
  }

  @Test
  fun `the first flip of any face starts a run of one`() {
    // UNKNOWN is not a face, so it never matches and never extends a run
    val stats = Stats().flip(Coin.Value.TAILS)

    stats.streakValue shouldBeEqualTo Coin.Value.TAILS
    stats.streak shouldBeEqualTo 1L
  }

  @Test
  fun `a repeat extends the run`() {
    val stats = Stats().flip(Coin.Value.HEADS, Coin.Value.HEADS, Coin.Value.HEADS)

    stats.streak shouldBeEqualTo 3L
  }

  @Test
  fun `the other face restarts the run at one`() {
    val stats = Stats().flip(Coin.Value.HEADS, Coin.Value.HEADS, Coin.Value.TAILS)

    stats.streakValue shouldBeEqualTo Coin.Value.TAILS
    stats.streak shouldBeEqualTo 1L
  }

  @Test
  fun `each face keeps its own record`() {
    val stats =
      Stats().flip(
        Coin.Value.HEADS,
        Coin.Value.HEADS,
        Coin.Value.HEADS,
        Coin.Value.TAILS,
        Coin.Value.HEADS
      )

    stats.record(Coin.Value.HEADS) shouldBeEqualTo 3L
    stats.record(Coin.Value.TAILS) shouldBeEqualTo 1L
  }

  @Test
  fun `a record stands until a longer run beats it`() {
    val threeThenTwo =
      Stats().flip(
        Coin.Value.HEADS,
        Coin.Value.HEADS,
        Coin.Value.HEADS,
        Coin.Value.TAILS,
        Coin.Value.HEADS,
        Coin.Value.HEADS
      )

    // the later run of two does not displace the earlier run of three
    threeThenTwo.record(Coin.Value.HEADS) shouldBeEqualTo 3L
    threeThenTwo.streak shouldBeEqualTo 2L

    threeThenTwo.flip(Coin.Value.HEADS, Coin.Value.HEADS).record(Coin.Value.HEADS) shouldBeEqualTo 4L
  }

  @Test
  fun `a run in progress is already on the record`() {
    // the record is not something you claim when the run ends -- it counts while it is still alive
    val stats = Stats().flip(Coin.Value.HEADS, Coin.Value.HEADS)

    stats.streak shouldBeEqualTo 2L
    stats.record(Coin.Value.HEADS) shouldBeEqualTo 2L
  }
}