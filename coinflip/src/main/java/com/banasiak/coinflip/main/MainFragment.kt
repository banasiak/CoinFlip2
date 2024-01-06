package com.banasiak.coinflip.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.banasiak.coinflip.databinding.FragmentMainBinding
import com.banasiak.coinflip.ui.CallbackAnimationDrawable
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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
    viewLifecycleOwner.lifecycle.addObserver(viewModel)
    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { viewModel.stateFlow.collect { bind(it) } }
        launch { viewModel.effectFlow.collect { onEffect(it) } }
      }
    }

    binding.navigationBar.setupWithNavController(findNavController())
    binding.coinImage.setOnClickListener { viewModel.postAction(MainAction.TapCoin) }
  }

  private fun bind(state: MainState) {
    binding.coinImage.background = state.animation
    binding.coinPlaceholder.isVisible = state.placeholderVisible
    binding.resultText.isInvisible = !state.resultVisible // invisible, not gone
    binding.resultText.text = getString(state.result.value.string)
    binding.headsCount.isVisible = state.statsVisible
    binding.tailsCount.isVisible = state.statsVisible
    // note: don't update the count values based on the stats contained in the state object, they will be updated via an effect
  }

  private fun onEffect(effect: MainEffect) {
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

  private fun updateStats(headsCount: Long, tailsCount: Long) {
    binding.headsCount.text = headsCount.toString()
    binding.tailsCount.text = tailsCount.toString()
  }

  private fun showRateAppDialog() {
    lifecycleScope.launch {
      val reviewManager = ReviewManagerFactory.create(requireContext())
      val reviewInfo = reviewManager.requestReview()
      reviewManager.launchReview(requireActivity(), reviewInfo)
    }
  }
}