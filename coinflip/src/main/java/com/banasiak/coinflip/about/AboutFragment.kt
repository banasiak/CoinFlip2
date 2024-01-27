package com.banasiak.coinflip.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.banasiak.coinflip.R
import com.banasiak.coinflip.util.ColorHelper
import com.banasiak.coinflip.util.launchUrl
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AboutFragment : BottomSheetDialogFragment() {
  @Inject lateinit var colorHelper: ColorHelper

  private val viewModel: AboutViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    val view = inflater.inflate(R.layout.fragment_about, container, false)
    view.findViewById<ComposeView>(R.id.compose_view).setContent { AboutScreen(viewModel) }
    return view
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { viewModel.effectFlow.collect { onEffect(it) } }
      }
    }
  }

  private fun onEffect(effect: AboutEffect) {
    when (effect) {
      is AboutEffect.LaunchUrl -> launchUrl(effect.url, colorHelper.getThemedColors())
    }
  }
}