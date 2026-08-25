package com.banasiak.coinflip.common

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Everything the app records about the flips made so far: how many landed on each face, the run of
 * identical results currently in progress, and the longest run each face has ever managed.
 *
 * The three travel as one value because every operation touches all of them — a reset clears all
 * three, an undo restores all three, and a flip advances all three. Split across separate accessors,
 * the reset/undo path is where one of them quietly gets forgotten.
 */
@Parcelize
data class Stats(
  val counts: Map<Coin.Value, Long> = emptyMap(),
  val records: Map<Coin.Value, Long> = emptyMap(),
  /** The face the run in progress is made of, or [Coin.Value.UNKNOWN] before the first flip. */
  val streakValue: Coin.Value = Coin.Value.UNKNOWN,
  val streak: Long = 0
) : Parcelable {
  val total: Long get() = counts.values.sum()

  fun count(value: Coin.Value): Long = counts[value] ?: 0

  /** The longest run of [value] on record. */
  fun record(value: Coin.Value): Long = records[value] ?: 0

  /**
   * Folds a landed flip in: bumps that face's count, extends the run or starts a new one at 1, and
   * raises the face's record if the run has just beaten it.
   */
  fun afterFlip(value: Coin.Value): Stats {
    val run = if (value == streakValue) streak + 1 else 1
    return copy(
      counts = counts + (value to count(value) + 1),
      records = if (run > record(value)) records + (value to run) else records,
      streakValue = value,
      streak = run
    )
  }
}