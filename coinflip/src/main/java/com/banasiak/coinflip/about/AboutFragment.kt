package com.banasiak.coinflip.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.banasiak.coinflip.R
import com.banasiak.coinflip.databinding.FragmentAboutBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AboutFragment : BottomSheetDialogFragment() {
  private lateinit var binding: FragmentAboutBinding
  private lateinit var viewModel: AboutViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    binding = FragmentAboutBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewModel = ViewModelProvider(this)[AboutViewModel::class.java]
    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { viewModel.stateFlow.collect { bind(it) } }
        launch { viewModel.effectFlow.collect { onEffect(it) } }
      }
    }

    binding.rateButton.setOnClickListener { viewModel.postAction(AboutAction.RateApp) }
  }

  private fun bind(state: AboutState) {
    binding.version.text = getString(R.string.version, state.versionName, state.versionCode)
  }

  private fun onEffect(effect: AboutEffect) {
    when (effect) {
      is AboutEffect.ShowRateAppDialog -> launchGooglePlayStore()
    }
  }

  private fun launchGooglePlayStore() {
    val packageName = requireContext().packageName
    val uri = Uri.parse("market://details?id=$packageName")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    startActivity(intent)
  }
}