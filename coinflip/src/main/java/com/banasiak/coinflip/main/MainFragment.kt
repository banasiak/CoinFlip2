package com.banasiak.coinflip.main

import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.CallbackAnimationDrawable
import com.banasiak.coinflip.databinding.FragmentMainBinding
import com.banasiak.coinflip.settings.SettingsManager
import com.banasiak.coinflip.util.navigate
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainFragment : Fragment() {
  @Inject lateinit var sensorManager: SensorManager
  @Inject lateinit var settingsManager: SettingsManager

  private lateinit var binding: FragmentMainBinding
  private lateinit var viewModel: MainViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    binding = FragmentMainBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewModel = ViewModelProvider(this)[MainViewModel::class.java]
    viewLifecycleOwner.lifecycle.addObserver(viewModel)
    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { viewModel.stateFlow.collect { bind(it) } }
        launch { viewModel.effectFlow.collect { onEffect(it) } }
      }
    }

    setupActions()
  }

  private fun setupActions() {
    binding.aboutButton.setOnClickListener { viewModel.postAction(MainAction.TapAbout) }
    binding.coinImage.setOnClickListener { viewModel.postAction(MainAction.TapCoin) }
    binding.diagnosticsButton.setOnClickListener { viewModel.postAction(MainAction.TapDiagnostics) }
    binding.settingsButton.setOnClickListener { viewModel.postAction(MainAction.TapSettings) }
  }

  private fun bind(state: MainState) {
    val drawable = state.image?.let { ResourcesCompat.getDrawable(resources, it, null) }
    binding.coinImage.setImageDrawable(drawable)
    binding.coinImage.background = state.animation
    binding.resultText.isVisible = state.resultVisible
    binding.resultText.text = getString(state.result.value.string)
  }

  private fun onEffect(effect: MainEffect) {
    when (effect) {
      is MainEffect.FlipCoin -> renderAnimation()
      is MainEffect.NavToAbout -> navigate(R.id.toAbout)
      is MainEffect.NavToDiagnostics -> navigate(R.id.toDiagnostics)
      is MainEffect.NavToSettings -> navigate(R.id.toSettings)
      is MainEffect.ShowRateDialog -> showRateAppDialog()
    }
  }

  private fun renderAnimation() {
    val animation = binding.coinImage.background as? CallbackAnimationDrawable
    animation?.stop()
    animation?.start()
  }

  private fun showRateAppDialog() {
    lifecycleScope.launch {
      val reviewManager = ReviewManagerFactory.create(requireContext())
      val reviewInfo = reviewManager.requestReview()
      reviewManager.launchReview(requireActivity(), reviewInfo)
    }
  }
}