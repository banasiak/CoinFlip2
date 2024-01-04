package com.banasiak.coinflip.common

import androidx.annotation.StringRes
import com.banasiak.coinflip.R
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class Coin @Inject constructor() {
  enum class Value(@StringRes val display: Int) {
    HEADS(R.string.heads),
    TAILS(R.string.tails),
    UNKNOWN(0)
  }

  private val generator = Random.Default
  private var currentValue: Value = Value.UNKNOWN

  fun flip(): Pair<Value, Value> {
    val current = currentValue
    val next = generator.nextValue()
    currentValue = next
    return Pair(current, next)
  }

  private fun Random.Default.nextValue(): Value {
    val next = this.nextBoolean()
    return if (next) Value.HEADS else Value.TAILS
  }
}