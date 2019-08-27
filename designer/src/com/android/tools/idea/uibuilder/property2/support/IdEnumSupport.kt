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
package com.android.tools.idea.uibuilder.property2.support

import com.android.SdkConstants.*
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.idea.gradle.structure.model.PsVariablesScope.NONE.name
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.lint.detector.api.stripIdPrefix
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.xml.XmlTag
import com.intellij.util.containers.notNullize
import com.intellij.util.text.nullize

private const val UNEXPECTED_ATTR = "Unexpected attribute"

class IdEnumSupport(val property: NelePropertyItem) : EnumSupport {

  override val values: List<EnumValue>
    get() =
      when (property.name) {
        // RelativeLayout attributes expecting an id of a sibling:
        ATTR_LAYOUT_ABOVE,
        ATTR_LAYOUT_ALIGN_BASELINE,
        ATTR_LAYOUT_ALIGN_BOTTOM,
        ATTR_LAYOUT_ALIGN_END,
        ATTR_LAYOUT_ALIGN_LEFT,
        ATTR_LAYOUT_ALIGN_RIGHT,
        ATTR_LAYOUT_ALIGN_START,
        ATTR_LAYOUT_ALIGN_TOP,
        ATTR_LAYOUT_BELOW,
        ATTR_LAYOUT_TO_END_OF,
        ATTR_LAYOUT_TO_LEFT_OF,
        ATTR_LAYOUT_TO_RIGHT_OF,
        ATTR_LAYOUT_TO_START_OF -> findSiblingIds()

        // ConstraintLayout attributes expecting an id of a sibling:
        ATTR_LAYOUT_BASELINE_TO_BASELINE_OF,
        ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
        ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF,
        ATTR_LAYOUT_END_TO_START_OF,
        ATTR_LAYOUT_END_TO_END_OF,
        ATTR_LAYOUT_LEFT_TO_LEFT_OF,
        ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
        ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
        ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
        ATTR_LAYOUT_START_TO_END_OF,
        ATTR_LAYOUT_START_TO_START_OF,
        ATTR_LAYOUT_TOP_TO_TOP_OF,
        ATTR_LAYOUT_TOP_TO_BOTTOM_OF -> findSiblingIds()

        ATTR_CHECKED_BUTTON,
        ATTR_CHECKED_CHIP -> findChildIds()

        ATTR_ACCESSIBILITY_TRAVERSAL_BEFORE,
        ATTR_ACCESSIBILITY_TRAVERSAL_AFTER -> findLayoutIds()

        ATTR_LABEL_FOR -> findSiblingIds()

        ATTR_CONSTRAINT_SET_START,
        ATTR_CONSTRAINT_SET_END,
        ATTR_DERIVE_CONSTRAINTS_FROM -> findConstraintSetIds()

        else -> error("$UNEXPECTED_ATTR: $name")
      }

  private fun findCommonParent(): NlComponent? {
    val parents = mutableSetOf<NlComponent>()
    property.components.mapNotNullTo(parents) { it.parent }
    return if (parents.size == 1) parents.first() else null
  }

  private fun findSiblingIds(): List<EnumValue> {
    return findChildIdsOf(findCommonParent(), property.components)
  }

  private fun findChildIds(): List<EnumValue> {
    return findChildIdsOf(property.components.singleOrNull(), emptyList())
  }

  private fun findChildIdsOf(parent: NlComponent?, omit: List<NlComponent>): List<EnumValue> {
    val values = mutableListOf<EnumValue>()
    parent?.children?.filter { !omit.contains(it) }?.mapNotNull { it.id }?.mapTo(values) { EnumValue.item(ID_PREFIX + it)}
    property.definition?.values?.mapTo(values) { EnumValue.item(it) }
    return values
  }

  private fun findLayoutIds(): List<EnumValue> {
    val values = mutableListOf<EnumValue>()
    property.components.firstOrNull()?.model?.flattenComponents()
      ?.filter { !property.components.contains(it) }
      ?.map { it.id }.notNullize()
      .forEach { values.add(EnumValue.item(ID_PREFIX + it)) }
    return values
  }

  private fun findConstraintSetIds(): List<EnumValue> {
    @Suppress("UNCHECKED_CAST")
    val tagPointer = property.optionalValue1 as? SmartPsiElementPointer<XmlTag>
    val tag = tagPointer?.element
    return tag?.parentTag?.subTags
             ?.filter { it.localName == MotionSceneAttrs.Tags.CONSTRAINTSET && it != tag }
             ?.mapNotNull { stripIdPrefix(it.getAttributeValue(ATTR_ID, ANDROID_URI)).nullize() }
             ?.map { EnumValue.item(ID_PREFIX + it) } ?: emptyList()
  }
}
