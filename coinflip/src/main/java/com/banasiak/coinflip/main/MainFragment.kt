package com.banasiak.coinflip.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.banasiak.coinflip.R
import com.banasiak.coinflip.databinding.FragmentMainBinding
import com.banasiak.coinflip.ui.CallbackAnimationDrawable
import com.banasiak.coinflip.util.navigate
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MainFragment : Fragment() {
  private lateinit var binding: FragmentMainBinding
  private val viewModel: MainViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    binding = FragmentMainBinding.inflate(inflater, container, false)
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

    // setupWithNavController() doesn't respect the animations set in the nav graph, so set up the click listener manually
    binding.navigationBar.setOnItemSelectedListener { item ->
      when (item.itemId) {
        R.id.diagnosticsMenuItem -> {
          navigate(R.id.toDiagnostics)
          false
        }
        R.id.settingsMenuItem -> {
          navigate(R.id.toSettings)
          false
        }
        R.id.aboutMenuItem -> {
          navigate(R.id.toAbout)
          false
        }
        else -> {
          false
        }
      }
    }

    // tap anywhere (besides the nav bar) to flip the coin
    binding.root.setOnClickListener { viewModel.postAction(MainAction.TapCoin) }
  }

  private fun bind(state: MainState) {
    Timber.d("bind(): $state")
    binding.coinImage.background = state.animation
    binding.coinPlaceholder.isVisible = state.placeholderVisible
    binding.instructionsText.text = getString(state.instructionsText)
    binding.resultText.isInvisible = !state.resultVisible // invisible, not gone
    binding.resultText.text = getString(state.result.value.string)
    binding.statsContainer.isVisible = state.statsVisible
    // note: don't update the count values based on the stats contained in the state object, they will be updated via an effect
  }

  private fun onEffect(effect: MainEffect) {
    Timber.d("onEffect(): $effect")
    when (effect) {
      MainEffect.FlipCoin -> renderAnimation()
      MainEffect.ShowRateDialog -> showRateAppDialog()
      is MainEffect.UpdateStats -> updateStats(effect.headsCount, effect.tailsCount)
    }
  }

  private fun renderAnimation() {
    val animation = binding.coinImage.background as? CallbackAnimationDrawable
    animation?.stop()
    animation?.start()
  }

  private fun updateStats(headsCount: String, tailsCount: String) {
    binding.headsCount.text = headsCount
    binding.tailsCount.text = tailsCount
  }

  private fun showRateAppDialog() {
    lifecycleScope.launch {
      val reviewManager = ReviewManagerFactory.create(requireContext())
      val reviewInfo = reviewManager.requestReview()
      reviewManager.launchReview(requireActivity(), reviewInfo)
    }
  }
}