package com.banasiak.coinflip.util

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

fun Fragment.navigate(resId: Int) {
  this.findNavController().navigate(resId)
}