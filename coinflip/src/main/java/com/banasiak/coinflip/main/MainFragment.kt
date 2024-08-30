package com.banasiak.coinflip.main

import android.hardware.SensorManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.banasiak.coinflip.R
import com.banasiak.coinflip.databinding.FragmentMainBinding
import com.banasiak.coinflip.extensions.navigate
import com.banasiak.coinflip.ui.DurationAnimationDrawable
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
  companion object {
    private val VIBRATION_EFFECT = VibrationEffect.createOneShot(200, 255)
  }

  @Inject lateinit var sensorManager: SensorManager
  @Inject lateinit var vibrator: Vibrator

  private lateinit var binding: FragmentMainBinding
  private lateinit var shakeDetector: ShakeDetector
  private val viewModel: MainViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    binding = FragmentMainBinding.inflate(inflater, container, false)
    shakeDetector = ShakeDetector { viewModel.postAction(MainAction.Shake) }
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // apply top margin equal to the height of the status bar to support Android 15 edge-to-edge mode
    ViewCompat.setOnApplyWindowInsetsListener(view) { v: View, windowInsets: WindowInsetsCompat ->
      val systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
      v.updateLayoutParams<MarginLayoutParams> { topMargin = systemBarInsets.top }
      return@setOnApplyWindowInsetsListener windowInsets
    }

    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { viewModel.stateFlow.collect { bind(it) } }
        launch { viewModel.effectFlow.collect { onEffect(it) } }
      }
    }

    // setupWithNavController() doesn't respect the animations set in the nav graph, so set up the click listener manually
    binding.navigationBar.setOnItemSelectedListener { item ->
      when (item.itemId) {
        R.id.aboutMenuItem -> {
          viewModel.postAction(MainAction.TapAbout)
          false
        }
        R.id.diagnosticsMenuItem -> {
          viewModel.postAction(MainAction.TapDiagnostics)
          false
        }
        R.id.settingsMenuItem -> {
          viewModel.postAction(MainAction.TapSettings)
          false
        }
        else -> {
          false
        }
      }
    }

    binding.resetButton.setOnClickListener { viewModel.postAction(MainAction.ResetStats) }

    // tap anywhere (besides the nav bar) to flip the coin
    binding.root.setOnClickListener { viewModel.postAction(MainAction.TapCoin) }
  }

  override fun onPause() {
    super.onPause()
    viewModel.postAction(MainAction.OnPause)
  }

  override fun onResume() {
    super.onResume()
    viewModel.postAction(MainAction.OnResume)
  }

  private fun bind(state: MainState) {
    Timber.d("bind(): $state")

    when (state.coinImageType) {
      CoinImageType.ANIMATION -> {
        binding.coinImage.apply {
          setImageDrawable(null)
          background = state.animation
          isVisible = true
        }
        binding.coinPlaceholder.isVisible = false
      }
      CoinImageType.IMAGE -> {
        binding.coinImage.apply {
          setImageDrawable(state.animation?.getLastFrame())
          background = null
          isVisible = true
        }
        binding.coinPlaceholder.isVisible = false
      }
      CoinImageType.PLACEHOLDER -> {
        binding.coinImage.apply {
          setImageDrawable(null)
          background = null
          isVisible = false
        }
        binding.coinPlaceholder.isVisible = true
      }
    }

    binding.instructionsText.text = getString(state.instructionsText)
    binding.resetButton.isVisible = state.resetVisible
    binding.resultText.isInvisible = !state.resultVisible // invisible, not gone
    binding.resultText.text = getString(state.result.value.string)
    binding.resultText.setTextAppearance(state.result.value.style)
    binding.statsContainer.isVisible = state.statsVisible
    // note: don't update the count values based on the stats contained in the state object, they will be updated via an effect

    if (state.shakeEnabled) startShakeDetector(state.shakeSensitivity) else stopShakeDetector()
  }

  private fun onEffect(effect: MainEffect) {
    Timber.d("onEffect(): $effect")
    when (effect) {
      MainEffect.FlipCoin -> renderAnimation()
      MainEffect.ToAbout -> navigate(R.id.toAbout)
      MainEffect.ToDiagnostics -> navigate(R.id.toDiagnostics)
      MainEffect.ToSettings -> navigate(R.id.toSettings)
      MainEffect.ShowRateDialog -> showRateAppDialog()
      is MainEffect.UpdateStats -> updateStats(effect.headsCount, effect.tailsCount)
      MainEffect.Vibrate -> vibrator.vibrate(VIBRATION_EFFECT)
    }
  }

  private fun renderAnimation() {
    val animation = binding.coinImage.background as? DurationAnimationDrawable
    animation?.apply {
      stop()
      start()
    }
  }

  private fun updateStats(headsCount: String, tailsCount: String) {
    binding.headsCount.text = headsCount
    binding.tailsCount.text = tailsCount
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