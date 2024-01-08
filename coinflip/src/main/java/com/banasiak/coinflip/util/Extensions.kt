package com.banasiak.coinflip.util

import android.content.Intent
import android.net.Uri
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

fun Fragment.navigate(@IdRes to: Int) {
  this.findNavController().navigate(to)
}

fun Fragment.launchUrl(url: String) {
  val uri = Uri.parse(url)
  val intent = Intent(Intent.ACTION_VIEW, uri)
  startActivity(intent)
}