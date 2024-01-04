package com.banasiak.coinflip.main

import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.banasiak.coinflip.R
import com.banasiak.coinflip.databinding.FragmentMainBinding
import com.banasiak.coinflip.settings.SettingsManager
import com.banasiak.coinflip.util.navigate
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import com.squareup.seismic.ShakeDetector
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainFragment : Fragment() {

  @Inject lateinit var sensorManager: SensorManager
  @Inject lateinit var settingsManager: SettingsManager

  private lateinit var binding: FragmentMainBinding
  private lateinit var viewModel: MainViewModel
  private lateinit var shakeDetector: ShakeDetector

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    binding = FragmentMainBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewModel = ViewModelProvider(this)[MainViewModel::class.java]
    shakeDetector = ShakeDetector { Timber.i("Shake detected!") }

    binding.settingsButton.setOnClickListener { navigate(R.id.toSettings) }
    binding.diagnosticsButton.setOnClickListener { navigate(R.id.toDiagnostics) }
    binding.aboutButton.setOnClickListener { navigate(R.id.toAbout) }
  }

  private suspend fun showRateAppDialog() {
    val reviewManager = ReviewManagerFactory.create(requireContext())
    val reviewInfo = reviewManager.requestReview()
    reviewManager.launchReview(requireActivity(), reviewInfo)
  }

  override fun onResume() {
    super.onResume()
    if (settingsManager.shake) {
      shakeDetector.start(sensorManager, SensorManager.SENSOR_DELAY_GAME)
      shakeDetector.setSensitivity(settingsManager.force)
    }
  }

  override fun onPause() {
    super.onPause()
    shakeDetector.stop()
  }

}