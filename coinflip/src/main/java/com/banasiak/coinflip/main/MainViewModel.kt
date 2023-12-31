package com.banasiak.coinflip.main

import androidx.lifecycle.ViewModel
import com.banasiak.coinflip.common.Coin
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(private val coin: Coin) : ViewModel() {

  init {
    Timber.wtf("Hello MainViewModel")
  }

}