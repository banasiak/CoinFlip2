package com.banasiak.coinflip.util

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

fun Fragment.navigate(@IdRes to: Int) {
  this.findNavController().navigate(to)
}