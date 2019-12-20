/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property2.support

import com.android.SdkConstants.ATTR_LAYOUT
import com.android.SdkConstants.ATTR_MODULE_NAME
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TOOLS_URI
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceFolderType
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.api.PropertyItem
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.resourceManagers.LocalResourceManager
import org.jetbrains.android.util.AndroidUtils

data class ClassEnumValue(override val value: String,
                          override val display: String,
                          val module: String?,
                          val isInProject: Boolean) : EnumValue {
  override fun toString() = value

  override fun withHeader(header: String) = this
  override fun withSeparator() = this
  override fun withIndentation() = this

  override fun select(property: PropertyItem): Boolean {
    if (property !is NelePropertyItem) {
      return false
    }

    val component = property.components.firstOrNull() ?: return false
    val layout = findLayoutForClass(component, value)

    TransactionGuard.submitTransaction(property.model, Runnable {
      NlWriteCommandActionUtil.run(property.components,
                                   "Set $component.tagName.${property.name} to $value") {
        property.value = value
        property.components.forEach { it.setAttribute(TOOLS_URI, ATTR_LAYOUT, layout) }
        property.components.forEach { it.setAttribute(AUTO_URI, ATTR_MODULE_NAME, module) }
      }
    })

    return true
  }

  private fun findLayoutForClass(component: NlComponent, className: String): String? {
    val module = component.model.module
    val resourceManager = LocalResourceManager.getInstance(module) ?: return null

    for (resourceFile in resourceManager.findResourceFiles(ResourceNamespace.TODO(), ResourceFolderType.LAYOUT)
      .filterIsInstance<XmlFile>()) {
      val contextClass = AndroidUtils.getContextClass(module, resourceFile) ?: continue
      if (contextClass.qualifiedName == className) {
        return "@layout/" + FileUtil.getNameWithoutExtension(resourceFile.name)
      }
    }

    return null
  }
}
