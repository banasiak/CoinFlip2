package com.banasiak.coinflip.common

import androidx.annotation.StringRes
import com.banasiak.coinflip.R

/**
 * The one coin whose artwork the user supplies rather than the APK.
 *
 * Deliberately *not* a [CoinType]. Every catalog entry names drawables that ship with the build,
 * `CoinType.flippable` is the pool `RANDOM` draws from, and `CoinResourcesTests` asserts both of
 * those about each entry -- a photo off the user's phone belongs to none of it.
 *
 * [PREFIX] is the identity in the same sense `CoinType.prefix` is: the string SharedPreferences
 * holds under `Setting.COIN`. It is named here once so nothing repeats the literal.
 */
object CustomCoin {
  const val PREFIX = "custom"

  // where the two faces live, under filesDir. Also the path the backup rules include.
  const val DIRECTORY = "coins"

  /**
   * Which side a stored image is. PNG rather than WebP because `WEBP_LOSSLESS` is API 30 against a
   * minSdk of 26, and the lossy `WEBP` constant that does span the range is deprecated; the size
   * difference on two files is not worth either branch.
   */
  enum class Face(val fileName: String, @param:StringRes val label: Int) {
    HEADS("custom_heads.png", R.string.heads),
    TAILS("custom_tails.png", R.string.tails)
  }
}