/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.model

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.annotations.VisibleForTesting
import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.res.ResourceHelper
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.dom.navigation.NavigationSchema
import java.io.File

/*
 * Extensions to NlComponent used by the navigation editor
 */

fun NlComponent.getUiName(resourceResolver: ResourceResolver?): String {
  val name = resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LABEL) ?:
      resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME) ?:
      NlComponent.stripId(resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID)) ?:
      tagName
  return when {
    resourceResolver != null -> ResourceHelper.resolveStringValue(resourceResolver, name)
    else -> name
  }
}

val NlComponent.visibleDestinations: List<NlComponent>
  get() {
    val schema = NavigationSchema.getOrCreateSchema(model.facet)
    val result = arrayListOf<NlComponent>()
    var p: NlComponent? = this
    while (p != null) {
      p.getChildren().filterTo(result, { c -> schema.getDestinationType(c.tagName) != null })
      p = p.parent
    }
    // The above won't add the root itself
    result.addAll(model.components)
    return result
  }

fun NlComponent.findVisibleDestination(id: String): NlComponent? {
  val schema = NavigationSchema.getOrCreateSchema(model.facet)
  var p = parent
  while (p != null) {
    p.getChildren().firstOrNull { c -> schema.getDestinationType(c.tagName) != null && c.id == id }?.let { return it }
    p = p.parent
  }
  // The above won't pick up the root
  return model.components.firstOrNull { c -> c.id == id }
}

val NlComponent.resolvedId
    get() = NlComponent.stripId(resolveAttribute(ANDROID_URI, ATTR_ID))

@VisibleForTesting
class NavComponentMixin(component: NlComponent)
  : NlComponent.XmlModelComponentMixin(component) {

  val includeAttrs: Table<String, String, String>? by lazy(fun(): Table<String, String, String>? {
    val resources = component.model.configuration.resourceResolver ?: return null
    val value = resources.findResValue(component.getAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_GRAPH), false) ?: return null
    val vFile = VfsUtil.findFileByIoFile(File(value.value), true) ?: return null
    val xmlFile = PsiManager.getInstance(component.model.project).findFile(vFile) as? XmlFile ?: return null
    val result: Table<String, String, String> = HashBasedTable.create()
    xmlFile.rootTag?.attributes?.forEach { result.put(it.namespace, it.localName, it.value) }
    return result
  })

  override fun getAttribute(namespace: String, attribute: String): String? {
    if (component.tagName == NavigationSchema.TAG_INCLUDE) {
      if (attribute == NavigationSchema.ATTR_GRAPH) {
        // To avoid recursion
        return null
      }
      return includeAttrs?.get(namespace, attribute)
    }

    return null
  }

  override fun getTooltipText(): String? {
    // TODO
    return null
  }
}

object NavComponentHelper {

  /**
   * Enhance the given [NlComponent] with nav-specific properties and methods.
   *
   * Note: For mocked components, you probably want LayoutTestUtilities.registerNlComponent.
   */
  fun registerComponent(component: NlComponent) {
    component.setMixin(NavComponentMixin(component))
  }
}
