package com.banasiak.coinflip.common

import android.os.Parcelable
import androidx.annotation.StringRes
import com.banasiak.coinflip.R
import com.banasiak.coinflip.util.AnimationHelper
import kotlinx.parcelize.Parcelize
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Coin @Inject constructor(
  private val random: RNG
) {
  enum class Value(@StringRes val string: Int) {
    HEADS(R.string.heads),
    TAILS(R.string.tails),
    UNKNOWN(R.string.empty)
  }

  private var currentValue: Value = Value.HEADS

  fun flip(): Result {
    val current = currentValue
    val next = random.nextValue()
    currentValue = next
    return Result(next, permutation(current, next))
  }

  fun isSecure(): Boolean {
    return random.useSecureRandom
  }

  private fun permutation(current: Value, next: Value): AnimationHelper.Permutation {
    return when {
      current == Value.HEADS && next == Value.HEADS -> AnimationHelper.Permutation.HEADS_HEADS
      current == Value.HEADS && next == Value.TAILS -> AnimationHelper.Permutation.HEADS_TAILS
      current == Value.TAILS && next == Value.HEADS -> AnimationHelper.Permutation.TAILS_HEADS
      current == Value.TAILS && next == Value.TAILS -> AnimationHelper.Permutation.TAILS_TAILS
      else -> AnimationHelper.Permutation.UNKNOWN
    }
  }

  private fun RNG.nextValue(): Value {
    val next = this.nextBoolean()
    return if (next) Value.HEADS else Value.TAILS
  }

  @Parcelize
  data class Result(val value: Value, val permutation: AnimationHelper.Permutation) : Parcelable
}