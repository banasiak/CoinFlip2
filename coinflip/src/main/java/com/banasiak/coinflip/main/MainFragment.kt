package com.banasiak.coinflip.main

import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.banasiak.coinflip.extensions.navigate
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import com.squareup.seismic.ShakeDetector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainFragment : Fragment() {
  @Inject lateinit var sensorManager: SensorManager

  private lateinit var shakeDetector: ShakeDetector
  private val viewModel: MainViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    shakeDetector = ShakeDetector { viewModel.postAction(MainAction.Shake) }
    return ComposeView(requireContext()).apply {
      setContent {
        MainScreen(
          viewModel = viewModel,
          onNavigate = { id -> navigate(id) },
          onShowRate = { showRateAppDialog() }
        )
      }
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // the coin animation, navigation, and rate dialog are all handled within MainScreen; the
    // fragment only owns the device sensor (shake detector), driven by the latest state
    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.stateFlow.collect { state ->
          if (state.shakeEnabled) startShakeDetector(state.shakeSensitivity) else stopShakeDetector()
        }
      }
    }
  }

  override fun onPause() {
    super.onPause()
    viewModel.postAction(MainAction.OnPause)
  }

  override fun onResume() {
    super.onResume()
    viewModel.postAction(MainAction.OnResume)
  }

  private fun showRateAppDialog() {
    lifecycleScope.launch {
      try {
        val reviewManager = ReviewManagerFactory.create(requireContext())
        val reviewInfo = reviewManager.requestReview()
        reviewManager.launchReview(requireActivity(), reviewInfo)
      } catch (e: Exception) {
        Timber.e(e, "All you can do is the best you can with what you have. And this device doesn't have the Google Play Store.")
      }
    }
  }

  private fun startShakeDetector(sensitivity: Int) {
    shakeDetector.start(sensorManager, SensorManager.SENSOR_DELAY_GAME)
    shakeDetector.setSensitivity(sensitivity)
  }

  private fun stopShakeDetector() {
    shakeDetector.stop()
  }
}