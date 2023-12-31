package com.banasiak.coinflip.main

import com.banasiak.coinflip.common.Coin

data class MainState(
  val currentValue: Coin.Value,
  val nextValue: Coin.Value,

  )