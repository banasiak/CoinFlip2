package com.banasiak.coinflip.diagnostics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.banasiak.coinflip.databinding.FragmentDiagnosticsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

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

  }

}