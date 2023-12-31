package com.banasiak.coinflip.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.banasiak.coinflip.R
import com.banasiak.coinflip.databinding.FragmentMainBinding
import com.banasiak.coinflip.util.navigate
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
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

    binding.settingsButton.setOnClickListener { navigate(R.id.toSettings) }
    binding.diagnosticsButton.setOnClickListener { navigate(R.id.toDiagnostics) }
    binding.aboutButton.setOnClickListener { navigate(R.id.toAbout) }
  }

  private suspend fun showRateAppDialog() {
    val reviewManager = ReviewManagerFactory.create(requireContext())
    val reviewInfo = reviewManager.requestReview()
    reviewManager.launchReview(requireActivity(), reviewInfo)
  }

}