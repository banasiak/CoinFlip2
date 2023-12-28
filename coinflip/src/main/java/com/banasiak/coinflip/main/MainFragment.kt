package com.banasiak.coinflip.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.banasiak.coinflip.R
import com.banasiak.coinflip.databinding.FragmentMainBinding

class MainFragment : Fragment() {

  private lateinit var binding: FragmentMainBinding
  private lateinit var viewModel: MainViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    binding = FragmentMainBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewModel = ViewModelProvider(this)[MainViewModel::class.java]

    binding.settingsButton.setOnClickListener { findNavController().navigate(R.id.toSettings) }
    binding.diagnosticsButton.setOnClickListener { findNavController().navigate(R.id.toDiagnostics) }
  }

}