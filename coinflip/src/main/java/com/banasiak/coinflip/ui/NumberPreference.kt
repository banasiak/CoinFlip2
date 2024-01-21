package com.banasiak.coinflip.ui

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import com.banasiak.coinflip.R
import com.banasiak.coinflip.util.formatNumber

// For the record, I resent having to implement this...
class NumberPreference(context: Context, attrs: AttributeSet?) : EditTextPreference(context, attrs) {
  // TODO -> make "units" a legitimate XML attribute instead of hijacking "summary", then it can be a plural instead of a string
  var unitsText: CharSequence? = null

  init {
    setOnBindEditTextListener {
      it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL
      it.setSelection(it.length())
    }

    summaryProvider = null // ignore summaryProvider attribute set by XML
    unitsText = summary // retrieve the summary attribute set by XML
    summaryProvider = SummaryProvider.instance // use my own SummaryProvider instead
  }

  private class SummaryProvider private constructor() : Preference.SummaryProvider<NumberPreference> {
    companion object {
      val instance: SummaryProvider by lazy { SummaryProvider() }
    }

    override fun provideSummary(preference: NumberPreference): CharSequence {
      val text = preference.text?.formatNumber()
      return when {
        text.isNullOrEmpty() -> preference.context.getString(R.string.settings_not_set)
        preference.unitsText != null -> "$text ${preference.unitsText}"
        else -> text
      }
    }
  }
}