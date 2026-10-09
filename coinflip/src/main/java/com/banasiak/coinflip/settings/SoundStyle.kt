package com.banasiak.coinflip.settings

import androidx.annotation.StringRes
import com.banasiak.coinflip.R

/**
 * Which set of sound effects plays. The segmented control draws these in order.
 *
 * [stored] is spelled out rather than derived from the constant's name, for the reason
 * [ShakeForce.stored] is: it is what lands on disk, and a rename must not change it.
 */
enum class SoundStyle(val stored: String, @param:StringRes val label: Int) {
  CLASSIC("classic", R.string.sound_style_classic),
  MODERN("modern", R.string.sound_style_modern)
}