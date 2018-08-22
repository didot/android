/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property2

import com.android.SdkConstants
import com.android.resources.ResourceType
import com.android.tools.idea.res.parseColor
import com.intellij.psi.util.PsiLiteralUtil
import org.jetbrains.android.dom.converters.DimensionConverter
import java.util.EnumSet

/**
 * Types of a [NelePropertyItem].
 */
enum class NelePropertyType {
  UNKNOWN,
  BOOLEAN,
  COLOR,
  COLOR_OR_DRAWABLE,
  DIMENSION,
  ENUM,
  FLAGS,
  FLOAT,
  FONT,
  FONT_SIZE,
  FRACTION,
  FRAGMENT,
  ID,
  INTEGER,
  LAYOUT,
  LIST,
  READONLY_STRING,
  STRING,
  STYLE,
  THREE_STATE_BOOLEAN,
  TEXT_APPEARANCE;

  val resourceTypes: EnumSet<ResourceType>
    get() = when (this) {
      BOOLEAN -> EnumSet.of(ResourceType.BOOL)
      COLOR -> EnumSet.of(ResourceType.COLOR)
      COLOR_OR_DRAWABLE -> EnumSet.of(ResourceType.COLOR, ResourceType.DRAWABLE, ResourceType.MIPMAP)
      DIMENSION -> EnumSet.of(ResourceType.DIMEN)
      FLOAT -> EnumSet.of(ResourceType.DIMEN)
      FONT -> EnumSet.of(ResourceType.FONT)
      FRACTION -> EnumSet.of(ResourceType.FRACTION)
      FONT_SIZE -> EnumSet.of(ResourceType.DIMEN)
      ID -> EnumSet.of(ResourceType.ID)
      INTEGER -> EnumSet.of(ResourceType.INTEGER)
      LAYOUT -> EnumSet.of(ResourceType.LAYOUT)
      LIST -> EnumSet.noneOf(ResourceType.ID.javaClass)
      READONLY_STRING -> EnumSet.noneOf(ResourceType.ID.javaClass)
      STRING -> EnumSet.of(ResourceType.STRING)
      STYLE -> EnumSet.of(ResourceType.STYLE)
      TEXT_APPEARANCE -> EnumSet.of(ResourceType.STYLE)
      THREE_STATE_BOOLEAN -> EnumSet.of(ResourceType.BOOL)
      else -> EnumSet.noneOf(ResourceType.BOOL.javaClass)
    }

  /**
   * Check the specified [literal] value and return the error if any or null.
   *
   * This method does not check resource values, theme values, and enum values.
   * Those checks should be made BEFORE calling this method.
   */
  fun validateLiteral(literal: String): String? {
    if (literal.isEmpty()) {
      return null
    }
    return when (this) {
      THREE_STATE_BOOLEAN,
      BOOLEAN -> error(literal != SdkConstants.VALUE_TRUE && literal != SdkConstants.VALUE_FALSE) { "Invalid bool value: '$literal'"}
      COLOR_OR_DRAWABLE,
      COLOR -> error(parseColor(literal) == null) { "Invalid color value: '$literal'" }
      ENUM -> "Invalid value: '$literal'"
      FONT_SIZE,
      DIMENSION -> error(DimensionConverter.INSTANCE.fromString(literal, null) == null) { getDimensionError(literal) }
      FLOAT -> error(PsiLiteralUtil.parseDouble(literal) == null) { "Invalid float: '$literal'" }
      FRACTION -> error(parseFraction(literal) == null) { "Invalid fraction: '$literal'"}
      INTEGER -> error(PsiLiteralUtil.parseInteger(literal) == null) { "Invalid integer: '$literal'"}
      ID -> "Invalid id: '$literal'"
      else -> null
    }
  }

  private fun error(condition: Boolean, message: () -> String): String? {
    return if (condition) message() else null
  }

  private fun getDimensionError(literal: String): String {
    val unit = DimensionConverter.getUnitFromValue(literal)
    return if (unit == null) "Cannot resolve: '$literal'" else "Unknown units '$unit'"
  }

  private fun parseFraction(literal: String): Double? {
    val isPercent = literal.endsWith('?')
    val text = if (isPercent) literal.substring(0, literal.length - 1) else literal
    val value = PsiLiteralUtil.parseDouble(text) ?: return null
    return if (isPercent) value / 100.0 else value
  }
}
