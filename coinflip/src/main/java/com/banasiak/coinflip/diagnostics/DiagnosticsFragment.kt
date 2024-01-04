package com.banasiak.coinflip.diagnostics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.banasiak.coinflip.databinding.FragmentDiagnosticsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DiagnosticsFragment : BottomSheetDialogFragment() {
  private lateinit var binding: FragmentDiagnosticsBinding
  private lateinit var viewModel: DiagnosticsViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    binding = FragmentDiagnosticsBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewModel = ViewModelProvider(this)[DiagnosticsViewModel::class.java]
    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.stateFlow.collect { bind(it) }
    }
  }

  private fun bind(state: DiagnosticsState) {
    binding.headsCount.text = state.headsCount.toString()
    binding.headsRatio.text = state.headsRatio
    binding.tailsCount.text = state.tailsCount.toString()
    binding.tailsRatio.text = state.tailsRatio
    binding.totalCount.text = state.totalCount.toString()
    binding.totalRatio.text = state.totalRatio
    binding.elapsedTime.text = state.elapsedTime.toString()
  }
}