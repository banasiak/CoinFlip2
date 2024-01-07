package com.banasiak.coinflip.settings

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.banasiak.coinflip.R
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment @Inject constructor() : PreferenceFragmentCompat() {
  @Inject lateinit var settings: SettingsManager

  private lateinit var onBackPressedCallback: OnBackPressedCallback

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    onBackPressedCallback = requireActivity().onBackPressedDispatcher.addCallback(this) { restartActivity() }
    onBackPressedCallback.isEnabled = false
  }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.root_settings, rootKey)

    val resetStats = findPreference<Preference>(SettingsManager.Settings.STATS.key)
    resetStats?.onPreferenceClickListener =
      Preference.OnPreferenceClickListener {
        val stats = settings.loadStats()
        settings.resetStats()
        Snackbar
          .make(requireView(), R.string.stats_reset, Snackbar.LENGTH_LONG)
          .setAction(R.string.undo) { settings.persistStats(stats) }
          .show()
        return@OnPreferenceClickListener true
      }

    val dynamicColor = findPreference<Preference>(SettingsManager.Settings.DYNAMIC.key)
    dynamicColor?.onPreferenceChangeListener =
      Preference.OnPreferenceChangeListener { _, _ ->
        onBackPressedCallback.isEnabled = true
        return@OnPreferenceChangeListener true
      }
  }

  private fun restartActivity() {
    val activity = requireActivity()
    val intent = activity.intent
    activity.finish()
    activity.startActivity(intent)
  }
}