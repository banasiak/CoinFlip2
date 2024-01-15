package com.banasiak.coinflip.diagnostics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.banasiak.coinflip.R
import com.banasiak.coinflip.databinding.FragmentDiagnosticsBinding
import com.banasiak.coinflip.util.ColorHelper
import com.banasiak.coinflip.util.launchUrl
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class DiagnosticsFragment : BottomSheetDialogFragment() {
  @Inject lateinit var colorHelper: ColorHelper

  private lateinit var binding: FragmentDiagnosticsBinding
  private val viewModel: DiagnosticsViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    binding = FragmentDiagnosticsBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewLifecycleOwner.lifecycle.addObserver(viewModel)
    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { viewModel.stateFlow.collect { bind(it) } }
        launch { viewModel.effectFlow.collect { onEffect(it) } }
      }
    }

    binding.wikipedia.setOnClickListener { viewModel.postAction(DiagnosticsAction.Wikipedia) }
  }

  private fun bind(state: DiagnosticsState) {
    binding.headsCount.text = state.headsCount
    binding.headsRatio.text = state.headsRatio
    binding.tailsCount.text = state.tailsCount
    binding.tailsRatio.text = state.tailsRatio
    binding.totalCount.text = state.totalCount
    binding.totalRatio.text = state.totalRatio
    binding.elapsedTime.text = getString(R.string.seconds, state.formattedTime)
  }

  private fun onEffect(effect: DiagnosticsEffect) {
    Timber.d("onEffect(): $effect")
    when (effect) {
      is DiagnosticsEffect.LaunchUrl -> launchUrl(effect.url, colorHelper.getThemedColors())
      is DiagnosticsEffect.ShowToast -> Toast.makeText(requireContext(), effect.text, Toast.LENGTH_LONG).show()
    }
  }
}