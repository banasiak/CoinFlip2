package com.banasiak.coinflip.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Test

class TextInputDialogTests {
  @Test
  fun `an all digit value is handed back untouched`() {
    // nothing was dropped, so the caret has to stay where the user left it
    val input = TextFieldValue("1234", TextRange(1))

    (digitsOnly(input) === input).shouldBeTrue()
  }

  @Test
  fun `letters are stripped and the caret lands at the end`() {
    digitsOnly(TextFieldValue("1a2b3", TextRange(5))) shouldBeEqualTo TextFieldValue("123", TextRange(3))
  }

  @Test
  fun `a pasted formatted number loses its separators`() {
    digitsOnly(TextFieldValue("1,000,000")) shouldBeEqualTo TextFieldValue("1000000", TextRange(7))
  }

  @Test
  fun `signs and whitespace are removed`() {
    digitsOnly(TextFieldValue("-1 000")) shouldBeEqualTo TextFieldValue("1000", TextRange(4))
    digitsOnly(TextFieldValue("1.5e3")) shouldBeEqualTo TextFieldValue("153", TextRange(3))
  }

  @Test
  fun `a value with no digits at all empties the field`() {
    digitsOnly(TextFieldValue("abc", TextRange(3))) shouldBeEqualTo TextFieldValue("", TextRange(0))
  }

  @Test
  fun `an already empty value is left alone`() {
    val input = TextFieldValue("")

    (digitsOnly(input) === input).shouldBeTrue()
  }
}