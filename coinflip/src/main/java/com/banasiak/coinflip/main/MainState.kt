package com.banasiak.coinflip.main

import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.ui.CallbackAnimationDrawable
import com.banasiak.coinflip.util.AnimationHelper

data class MainState(
  val animation: CallbackAnimationDrawable? = null,
  val placeholderVisible: Boolean = true,
  val result: Coin.Result = Coin.Result(Coin.Value.UNKNOWN, AnimationHelper.Permutation.UNKNOWN),
  val resultVisible: Boolean = false,
  val stats: Map<Coin.Value, Long> = emptyMap(),
  val statsVisible: Boolean = true
)

sealed class MainAction {
  data object TapCoin : MainAction()
}

sealed class MainEffect {
  data object FlipCoin : MainEffect()

  data object ShowRateDialog : MainEffect()

  data class UpdateStats(val headsCount: Long, val tailsCount: Long) : MainEffect()
}