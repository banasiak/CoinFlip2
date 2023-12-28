package com.banasiak.coinflip.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.banasiak.coinflip.R

class SettingsFragment : PreferenceFragmentCompat() {
  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.root_settings, rootKey)
  }
}