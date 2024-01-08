package com.banasiak.coinflip.ui

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.View
import androidx.annotation.StringRes
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.banasiak.coinflip.R
import com.banasiak.coinflip.util.formatNumber
import com.google.android.material.snackbar.Snackbar
import timber.log.Timber
import java.lang.ref.WeakReference

// For the record, I resent having to implement this...
class NumberPreference(context: Context, attrs: AttributeSet?) : EditTextPreference(context, attrs) {
  private var parentView: WeakReference<View?>? = null

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

  override fun onBindViewHolder(holder: PreferenceViewHolder) {
    super.onBindViewHolder(holder)
    parentView = WeakReference(holder.itemView as? View)
  }

  override fun setText(text: String?) {
    // don't allow the user to set a value that can't be safely converted to a Long in the future
    val number = text?.toLongOrNull()
    if (number == null || number == 0L) {
      Timber.w("Not persisting invalid NumberPreference: $text")
      showSnackbar(R.string.invalid_iterations)
      return
    }

    super.setText(text)
  }

  private fun showSnackbar(@StringRes string: Int) {
    parentView?.get()?.let { Snackbar.make(it, string, Snackbar.LENGTH_LONG).show() }
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