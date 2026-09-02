package com.banasiak.coinflip.common

import androidx.annotation.StringRes
import com.banasiak.coinflip.R

/**
 * A coin whose artwork the user supplies rather than the APK: one built from photographs, one built
 * from emoji. The user can have one of each, and a coin is entirely one or the other.
 *
 * Deliberately *not* [CoinType] entries. Every catalog entry names drawables that ship with the
 * build, `CoinType.flippable` is the pool `RANDOM` draws from, and `CoinResourcesTests` asserts both
 * of those about each entry -- a photo off the user's phone belongs to none of it.
 *
 * [prefix] is the identity in the same sense `CoinType.prefix` is: the string SharedPreferences
 * holds under `Setting.COIN`, and the one thing `AnimationHelper` needs to know how a face is drawn.
 * Because the kind is the coin rather than the face, nothing has to record which *faces* were drawn
 * from emoji.
 */
enum class CustomCoin(val prefix: String, @param:StringRes val title: Int) {
  PHOTO("custom", R.string.settings_item_photo_coin_title),
  EMOJI("emoji", R.string.settings_item_emoji_coin_title);

  /**
   * Which side a stored image is. PNG rather than WebP because `WEBP_LOSSLESS` is API 30 against a
   * minSdk of 26, and the lossy `WEBP` constant that does span the range is deprecated; the size
   * difference on two files is not worth either branch.
   */
  enum class Face(val stem: String, @param:StringRes val label: Int) {
    HEADS("heads", R.string.heads),
    TAILS("tails", R.string.tails)
  }

  // spelled out rather than derived from the constant name it happens to match: "custom_heads.png"
  // is already on disk from before the emoji coin existed, so a rename here would orphan it
  fun fileName(face: Face): String = "${prefix}_${face.stem}.png"

  companion object {
    // where the faces live, under filesDir. Also the path the backup rules include.
    const val DIRECTORY = "coins"

    // null for every shipped coin, which is what tells the two kinds apart everywhere a stored
    // prefix arrives without knowing what it names
    fun forPrefix(prefix: String): CustomCoin? = entries.firstOrNull { it.prefix == prefix }
  }
}