/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.banasiak.coinflip.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

class SquareFrameLayout(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs) {
  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val widthSize = MeasureSpec.getSize(widthMeasureSpec)
    val heightSize = MeasureSpec.getSize(heightMeasureSpec)
    if (widthSize == 0 && heightSize == 0) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec)
      val minSize = measuredWidth.coerceAtMost(measuredHeight)
      setMeasuredDimension(minSize, minSize)
      return
    }
    val size: Int =
      if (widthSize == 0 || heightSize == 0) {
        widthSize.coerceAtLeast(heightSize)
      } else {
        widthSize.coerceAtMost(heightSize)
      }
    val newMeasureSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
    super.onMeasure(newMeasureSpec, newMeasureSpec)
  }
}