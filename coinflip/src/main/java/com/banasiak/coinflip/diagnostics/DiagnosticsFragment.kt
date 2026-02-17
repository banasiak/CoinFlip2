package com.banasiak.coinflip.diagnostics

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.banasiak.coinflip.extensions.launchUrl
import com.banasiak.coinflip.extensions.navigateBack
import com.banasiak.coinflip.util.ColorHelper
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class DiagnosticsFragment : BottomSheetDialogFragment() {
  @Inject
  lateinit var colorHelper: ColorHelper

  private val viewModel: DiagnosticsViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return ComposeView(requireContext()).apply {
      setContent { DiagnosticsScreen(viewModel) }
    }
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = super.onCreateDialog(savedInstanceState)
    (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
    return dialog
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewLifecycleOwner.lifecycle.addObserver(viewModel)
    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { viewModel.effectFlow.collect { onEffect(it) } }
      }
    }
  }

  private fun onEffect(effect: DiagnosticsEffect) {
    Timber.d("onEffect(): $effect")
    when (effect) {
      is DiagnosticsEffect.LaunchUrl -> launchUrl(effect.url, colorHelper.getThemedColors())
      is DiagnosticsEffect.NavBack -> navigateBack()
      is DiagnosticsEffect.ShowToast -> Toast.makeText(requireContext(), effect.text, Toast.LENGTH_LONG).show()
    }
  }
}
