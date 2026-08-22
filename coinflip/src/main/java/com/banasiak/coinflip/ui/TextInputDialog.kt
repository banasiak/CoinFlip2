package com.banasiak.coinflip.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.banasiak.coinflip.ui.theme.Dimen

/**
 * The shell shared by every dialog that exists only to collect one or two short values: title,
 * stacked fields, and an OK/Cancel pair. Opens focused on the field the caller hands
 * [FocusRequester] to.
 *
 * @param confirmEnabled gates OK so an invalid value cannot be confirmed in the first place.
 */
@Composable
fun TextInputDialog(
  title: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  confirmEnabled: Boolean = true,
  fields: @Composable ColumnScope.(FocusRequester) -> Unit
) {
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(Dimen.medium)) {
        fields(focusRequester)
      }
    },
    confirmButton = {
      TextButton(
        enabled = confirmEnabled,
        onClick = {
          onConfirm()
          onDismiss()
        }
      ) {
        Text(stringResource(android.R.string.ok))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
    }
  )
}

/**
 * Field state for [TextInputDialog], saved across configuration change and process death.
 *
 * @param selectAll opens with the value selected, so typing replaces it -- what you want on the
 *   focused field, since these dialogs replace a value rather than append to it.
 */
@Composable
fun rememberEditableValue(initial: String, selectAll: Boolean = false): MutableState<TextFieldValue> =
  rememberSaveable(stateSaver = TextFieldValue.Saver) {
    val selection = if (selectAll) TextRange(0, initial.length) else TextRange(initial.length)
    mutableStateOf(TextFieldValue(initial, selection))
  }

/** Keeps [input] to digits, moving the caret to the end when a character is rejected. */
fun digitsOnly(input: TextFieldValue): TextFieldValue {
  val digits = input.text.filter { it.isDigit() }
  // dropping a character would leave the caret past the end of what's left
  return if (digits == input.text) input else TextFieldValue(digits, TextRange(digits.length))
}