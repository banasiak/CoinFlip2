package com.banasiak.coinflip.common

import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import com.banasiak.coinflip.R
import com.banasiak.coinflip.settings.SettingsManager
import com.banasiak.coinflip.util.AnimationHelper
import kotlinx.parcelize.Parcelize
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Coin @Inject constructor(
  private val random: RNG,
  private val settings: SettingsManager
) {
  enum class Value(@param:StringRes val string: Int, @param:StyleRes val style: Int) {
    HEADS(R.string.heads, R.style.AppTheme_TextAppearance_Result_Heads),
    TAILS(R.string.tails, R.style.AppTheme_TextAppearance_Result_Tails),
    UNKNOWN(R.string.empty, R.style.AppTheme_TextAppearance_Result);

    fun customLabel(settingsManager: SettingsManager): String? {
      return when (this) {
        HEADS -> settingsManager.customHeadsText
        TAILS -> settingsManager.customTailsText
        else -> null
      }
    }
  }

  private var currentValue: Value = Value.HEADS

  fun flip(): Result {
    val current = currentValue
    val next = random.nextValue()
    currentValue = next
    return Result(next, permutation(current, next), next.customLabel(settings))
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
  data class Result(val value: Value, val permutation: AnimationHelper.Permutation, val customLabel: String? = null) : Parcelable
}