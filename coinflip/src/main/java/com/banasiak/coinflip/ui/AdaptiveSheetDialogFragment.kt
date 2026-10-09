package com.banasiak.coinflip.ui

import android.app.Dialog
import android.os.Bundle
import com.banasiak.coinflip.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.sidesheet.SideSheetDialog

/**
 * A modal sheet that docks to the end edge when the window is wider than it is tall, the same rule
 * the main screen lays itself out by, and otherwise rises from the bottom, fully expanded.
 *
 * The form is chosen once, when the dialog is created, and kept for the dialog's life. `AppActivity`
 * handles orientation and size changes itself, so rotating the device leaves an open sheet in the
 * form it opened in. Anything that does recreate the activity picks the form again for the window
 * at that moment.
 */
abstract class AdaptiveSheetDialogFragment : BottomSheetDialogFragment() {
  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val config = resources.configuration
    return if (config.screenWidthDp > config.screenHeightDp) {
      SideSheetDialog(requireContext(), R.style.ThemeOverlay_CoinFlip_SideSheetDialog)
    } else {
      (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
      }
    }
  }
}