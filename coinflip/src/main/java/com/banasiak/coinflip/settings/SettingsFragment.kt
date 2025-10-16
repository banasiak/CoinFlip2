package com.banasiak.coinflip.settings

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.banasiak.coinflip.R
import com.banasiak.coinflip.extensions.applySystemBarInsets
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment @Inject constructor() : PreferenceFragmentCompat() {
  @Inject lateinit var settings: SettingsManager

  private lateinit var onBackPressedCallback: OnBackPressedCallback

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    view.applySystemBarInsets()

    onBackPressedCallback = requireActivity().onBackPressedDispatcher.addCallback(this) { restartActivity() }
    onBackPressedCallback.isEnabled = false
  }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.root_settings, rootKey)

    findPreference<Preference>(SettingsManager.Settings.RESET.key)?.apply {
      onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          val stats = settings.loadStats()
          settings.resetStats()
          Snackbar
            .make(requireView(), R.string.stats_reset_message, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { settings.persistStats(stats) }
            .show()
          return@OnPreferenceClickListener true
        }
    }

    findPreference<Preference>(SettingsManager.Settings.DYNAMIC.key)?.apply {
      onPreferenceChangeListener =
        Preference.OnPreferenceChangeListener { _, _ ->
          onBackPressedCallback.isEnabled = true
          return@OnPreferenceChangeListener true
        }
    }


    findPreference<EditTextPreference>(SettingsManager.Settings.CUSTOM_HEADS_TEXT.key)?.apply {
      onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
        if ((value as? String).isNullOrEmpty()) {
          this.text = getText(R.string.heads).toString()
          return@OnPreferenceChangeListener false
        }
        return@OnPreferenceChangeListener true
      }
    }

    findPreference<EditTextPreference>(SettingsManager.Settings.CUSTOM_TAILS_TEXT.key)?.apply {
      onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
        if ((value as? String).isNullOrEmpty()) {
          this.text = getText(R.string.tails).toString()
          return@OnPreferenceChangeListener false
        }
        return@OnPreferenceChangeListener true
      }
    }

    // doing this here, because I can't reliably get the view for the snackbar from within NumberPreference
    findPreference<Preference>(SettingsManager.Settings.DIAGNOSTICS.key)?.apply {
      onPreferenceChangeListener =
        Preference.OnPreferenceChangeListener { _, value ->
          // don't allow the user to set a value that can't be safely converted to a Long in the future
          val number = (value as? String)?.toLongOrNull()
          if (number == null || number == 0L) {
            Timber.w("Not persisting invalid NumberPreference: $value")
            Snackbar.make(requireView(), R.string.invalid_iterations, Snackbar.LENGTH_LONG).show()
            return@OnPreferenceChangeListener false
          }
          return@OnPreferenceChangeListener true
        }
    }

  }

  private fun restartActivity() {
    val activity = requireActivity()
    val intent = activity.intent
    activity.finish()
    activity.startActivity(intent)
  }
}