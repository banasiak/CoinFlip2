package com.banasiak.coinflip.about

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.banasiak.coinflip.R
import com.banasiak.coinflip.databinding.FragmentAboutBinding
import com.banasiak.coinflip.extensions.launchUrl
import com.banasiak.coinflip.util.ColorHelper
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AboutFragment : BottomSheetDialogFragment() {
  @Inject lateinit var colorHelper: ColorHelper

  private lateinit var binding: FragmentAboutBinding
  private val viewModel: AboutViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    binding = FragmentAboutBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = super.onCreateDialog(savedInstanceState)
    (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
    return dialog
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { viewModel.stateFlow.collect { bind(it) } }
        launch { viewModel.effectFlow.collect { onEffect(it) } }
      }
    }

    setupActions()
    binding.rateButton.setOnClickListener { viewModel.postAction(AboutAction.RateApp) }
  }

  private fun setupActions() {
    binding.rateButton.setOnClickListener { viewModel.postAction(AboutAction.RateApp) }
    binding.donateButton.setOnClickListener { viewModel.postAction(AboutAction.Donate) }
  }

  private fun bind(state: AboutState) {
    binding.version.text = getString(R.string.version, state.versionName, state.versionCode)
  }

  private fun onEffect(effect: AboutEffect) {
    when (effect) {
      is AboutEffect.LaunchUrl -> launchUrl(effect.url, colorHelper.getThemedColors())
    }
  }
}