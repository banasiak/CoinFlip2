package com.banasiak.coinflip.settings

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import com.banasiak.coinflip.R

// For the record, I resent having to implement this...
class NumberPreference : EditTextPreference {
  var summaryAttribute: CharSequence? = null

  constructor(context: Context) : super(context)

  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
    super(context, attrs, defStyleAttr, defStyleRes)

  init {
    setOnBindEditTextListener {
      it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL
      it.setSelection(it.length())
    }
    summaryProvider = null // ignore summaryProvider attribute set by XML
    summaryAttribute = summary // retrieve the summary attribute set by XML
    summaryProvider = NumberSummaryProvider.instance // use my own NumberSummaryProvider instead
  }
}

class NumberSummaryProvider private constructor() : Preference.SummaryProvider<NumberPreference> {
  companion object {
    val instance: NumberSummaryProvider by lazy { NumberSummaryProvider() }
  }

  override fun provideSummary(preference: NumberPreference): CharSequence? {
    return when {
      preference.text.isNullOrEmpty() -> preference.context.getString(R.string.settings_not_set)
      preference.summaryAttribute != null -> "${preference.text} ${preference.summaryAttribute}"
      else -> preference.text
    }
  }
}