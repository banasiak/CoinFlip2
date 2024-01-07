package com.banasiak.coinflip.main

import android.os.Parcelable
import androidx.annotation.StringRes
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.ui.CallbackAnimationDrawable
import com.banasiak.coinflip.util.AnimationHelper
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class MainState(
  @IgnoredOnParcel val animation: CallbackAnimationDrawable? = null,
  @StringRes val instructionsText: Int = R.string.instructions_tap_shake,
  val placeholderVisible: Boolean = true,
  val result: Coin.Result = Coin.Result(Coin.Value.UNKNOWN, AnimationHelper.Permutation.UNKNOWN),
  val resultVisible: Boolean = false,
  val stats: Map<Coin.Value, Long> = emptyMap(),
  val statsVisible: Boolean = true
) : Parcelable

sealed class MainAction {
  data object TapCoin : MainAction()
}

sealed class MainEffect {
  data object FlipCoin : MainEffect()

  data object ShowRateDialog : MainEffect()

  data class UpdateStats(val headsCount: Long, val tailsCount: Long) : MainEffect()
}