package com.banasiak.coinflip.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {
  private val viewModel: SettingsViewModel by viewModels()
  private lateinit var onBackPressedCallback: OnBackPressedCallback

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return ComposeView(requireContext()).apply {
      setContent {
        SettingsScreen(
          viewModel = viewModel,
          onEnableRestartOnBack = { onBackPressedCallback.isEnabled = true }
        )
      }
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    // when the dynamic color preference changes, the activity must be recreated on the way out to
    // re-apply (or remove) Material You theming -- this mirrors the legacy PreferenceFragment behavior
    onBackPressedCallback = requireActivity().onBackPressedDispatcher.addCallback(this) { restartActivity() }
    onBackPressedCallback.isEnabled = false
  }

  private fun restartActivity() {
    val activity = requireActivity()
    val intent = activity.intent
    activity.finish()
    activity.startActivity(intent)
  }
}