package com.banasiak.coinflip.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.banasiak.coinflip.R
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment @Inject constructor() : PreferenceFragmentCompat() {
  @Inject
  lateinit var settings: SettingsManager

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.root_settings, rootKey)

    val preference = findPreference<Preference>("resetStats")
    preference?.onPreferenceClickListener =
      Preference.OnPreferenceClickListener {
        val stats = settings.loadStats()
        settings.resetStats()
        Snackbar
          .make(requireView(), R.string.stats_reset, Snackbar.LENGTH_LONG)
          .setAction(R.string.undo) { settings.persistStats(stats) }
          .show()
        return@OnPreferenceClickListener true
      }
  }
}