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
package com.android.tools.idea.layoutinspector.resource

import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.resources.ResourceResolver.MAX_RESOURCE_INDIRECTION
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.layoutinspector.common.StringTable
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.res.RESOURCE_ICON_SIZE
import com.android.tools.idea.res.parseColor
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.ClassUtil
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import org.jetbrains.android.facet.AndroidFacet
import javax.swing.Icon

/**
 * Utility for looking up resources in a project.
 *
 * This class contains facilities for finding property values and to navigate to
 * the property definition or property assignment.
 */
class ResourceLookup(private val project: Project) {

  private var resolver: ResourceLookupResolver? = null

  /**
   * Update the configuration after a possible configuration change detected on the device.
   */
  fun updateConfiguration(resources: LayoutInspectorProto.ResourceConfiguration, stringTable: StringTable) {
    val loader = ConfigurationLoader(resources, stringTable)
    val facet = ReadAction.compute<AndroidFacet?, RuntimeException> { findFacetFromPackage(project, loader.packageName) }
    if (facet == null) {
      resolver = null
    }
    else {
      val theme = mapReference(facet, loader.theme)?.resourceUrl?.toString() ?: ""
      val mgr = ConfigurationManager.getOrCreateInstance(facet)
      val cache = mgr.resolverCache
      val resourceResolver = ReadAction.compute<ResourceResolver, RuntimeException> {
        cache.getResourceResolver(mgr.target, theme, loader.folderConfiguration)
      }
      resolver = ResourceLookupResolver(project, facet, loader.folderConfiguration, resourceResolver)
    }
  }

  /**
   * Find the file locations for a [property].
   *
   * The list of file locations will start from [InspectorPropertyItem.source]. If that is a reference
   * the definition of that reference will be next etc.
   * The [max] guards against recursive indirection in the resources.
   * Each file location is specified by:
   *  - a string containing the file name and a line number
   *  - a [Navigatable] that can be used to goto the source location
   */
  fun findFileLocations(property: InspectorPropertyItem, max: Int = MAX_RESOURCE_INDIRECTION): List<SourceLocation> =
    resolver?.findFileLocations(property, property.source, max) ?: emptyList()

  /**
   * Find the attribute value from resource reference.
   */
  fun findAttributeValue(property: InspectorPropertyItem, location: ResourceReference): String? =
    resolver?.findAttributeValue(property, location)

  /**
   * Find the icon from this drawable property.
   */
  fun resolveAsIcon(property: InspectorPropertyItem): Icon? {
    resolver?.resolveAsIcon(property)?.let { return it }
    val value = property.value
    val color = value?.let { parseColor(value) } ?: return null
    return JBUI.scale(ColorIcon(RESOURCE_ICON_SIZE, color, false))
  }

  /**
   * Convert a class name to a source location.
   */
  fun resolveClassNameAsSourceLocation(className: String): SourceLocation {
    val psiClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project))
    val navigatable = psiClass?.let { findNavigatable(psiClass) }
    val source = ClassUtil.extractClassName(className)
    return SourceLocation(source, navigatable)
  }
}
