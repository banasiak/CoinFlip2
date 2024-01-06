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
  data object TapAbout : MainAction()

  data object TapCoin : MainAction()

  data object TapDiagnostics : MainAction()

  data object TapSettings : MainAction()
}

sealed class MainEffect {
  data object FlipCoin : MainEffect()

  data object NavToAbout : MainEffect()

  data object NavToDiagnostics : MainEffect()

  data object NavToSettings : MainEffect()

  data object ShowRateDialog : MainEffect()

  data class UpdateStats(val headsCount: Long, val tailsCount: Long) : MainEffect()
}