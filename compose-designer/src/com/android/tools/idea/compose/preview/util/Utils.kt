/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.util

import com.intellij.openapi.util.Segment
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.apache.commons.lang.time.DurationFormatUtils
import org.jetbrains.uast.UElement
import java.time.Duration

fun UElement?.toSmartPsiPointer(): SmartPsiElementPointer<PsiElement>? {
  val bodyPsiElement = this?.sourcePsi ?: return null
  return SmartPointerManager.createPointer(bodyPsiElement)
}

fun Segment?.containsOffset(offset: Int) = this?.let {
  it.startOffset <= offset && offset <= it.endOffset
} ?: false

/**
 * Converts the given duration to a display string that contains minutes (if the duration is greater than 60s), seconds and
 * milliseconds.
 */
internal fun Duration.toDisplayString(): String {
  val durationMs = toMillis()
  val durationFormat = if (durationMs >= 60_000) "mm 'm' ss 's' SSS 'ms'" else "ss 's' SSS 'ms'"
  return DurationFormatUtils.formatDuration(durationMs, durationFormat, false)
}
