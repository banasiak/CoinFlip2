package com.banasiak.coinflip.main

import com.banasiak.coinflip.common.AnimationCallback
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.util.AnimationHelper

data class MainState(
  val currentValue: Coin.Value = Coin.Value.UNKNOWN,
  val nextValue: Coin.Value = Coin.Value.UNKNOWN
)

sealed class MainAction {
  data object TapAbout : MainAction()

  data object TapCoin : MainAction()

  data object TapDiagnostics : MainAction()

  data object TapSettings : MainAction()
}

sealed class MainEffect {
  data class FlipCoin(val permutation: AnimationHelper.Permutation, val callback: AnimationCallback) : MainEffect()

  data object NavToAbout : MainEffect()

  data object NavToDiagnostics : MainEffect()

  data object NavToSettings : MainEffect()

  data object ShowRateDialog : MainEffect()
}