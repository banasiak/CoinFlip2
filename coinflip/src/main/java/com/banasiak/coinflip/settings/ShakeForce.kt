package com.banasiak.coinflip.settings

import androidx.annotation.StringRes
import com.banasiak.coinflip.R
import com.squareup.seismic.ShakeDetector

/**
 * How hard the phone has to be shaken before it counts as a flip. The segmented control draws
 * these in order.
 *
 * [stored] is spelled out rather than derived from the constant's name, which it happens to match:
 * these strings are already on disk from earlier versions, so deriving them would let a rename
 * invalidate every install's setting without a word from the compiler.
 */
enum class ShakeForce(val stored: String, @param:StringRes val label: Int, val sensitivity: Int) {
  LOW("low", R.string.force_low, ShakeDetector.SENSITIVITY_LIGHT),
  MEDIUM("medium", R.string.force_medium, ShakeDetector.SENSITIVITY_MEDIUM),
  HIGH("high", R.string.force_high, ShakeDetector.SENSITIVITY_HARD)
}