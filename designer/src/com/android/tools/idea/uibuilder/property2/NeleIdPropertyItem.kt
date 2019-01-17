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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.NEW_ID_PREFIX
import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.property2.support.NeleIdRenameProcessor
import com.android.tools.lint.detector.api.stripIdPrefix
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.text.nullize
import org.jetbrains.android.dom.attrs.AttributeDefinition

class NeleIdPropertyItem(model: NelePropertiesModel, definition: AttributeDefinition?, componentName: String, optionalValue: Any?,
                         components: List<NlComponent>) :
  NelePropertyItem(ANDROID_URI, ATTR_ID, NelePropertyType.ID, definition, componentName, "", model, optionalValue,
                   listOf(components.first())) {


  // TODO(b/120919869): The snapshot value in NlComponent may be stale.
  // The snapshot stored in an NlComponent can get stale when something else
  // modifies the XML attributes directly i.e. not through an NlComponent.
  // The RenameProcessor used here is one example.
  // Workaround for now: read the attribute directly from the XmlAttribute.
  @Suppress("DEPRECATION")
  override val rawValue: String?
    get() = components.first().tag.getAttributeValue(ATTR_ID, ANDROID_URI)

  override var value: String?
    get() = stripIdPrefix(super.value)
    set(value) {
      val oldId = stripIdPrefix(super.value)
      val newId = stripIdPrefix(value)
      val newValue = toValue(newId)
      val tag = firstTag
      val attribute = if (tag != null && tag.isValid) tag.getAttribute(ATTR_ID, ANDROID_URI) else null
      val xmlValue = attribute?.valueElement

      if (!renameRefactoring(xmlValue, oldId, newId, newValue)) {
        super.value = newValue
      }
    }

  private fun toValue(id: String): String? {
    return if (id.isNotEmpty() && !id.startsWith("@")) NEW_ID_PREFIX + id else id.nullize()
  }

  override fun getCompletionValues(): List<String> {
    return emptyList()
  }

  override fun validate(text: String?): Pair<EditingErrorCategory, String> {
    return lintValidation() ?: EDITOR_NO_ERROR
  }

  /**
   * Refactor the id value.
   *
   * @return true if the rename refactoring made the requred changes, false if the value must be set
   */
  private fun renameRefactoring(value: XmlAttributeValue?, oldId: String, newId: String, newValue: String?): Boolean {
    if (oldId.isEmpty() || newId.isEmpty() || newValue == null || value == null || !value.isValid) {
      return false
    }

    // Exact replace only
    val project = model.facet.module.project
    val processor = NeleIdRenameProcessor(project, value, newValue)
    processor.run()

    // The RenameProcessor will change the value of the ID here (may happen later if previewing first).
    return true
  }
}
