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
package com.android.tools.idea.databinding.psiclass

import com.android.ide.common.resources.DataBindingResourceType
import com.android.tools.idea.databinding.ModuleDataBinding
import com.android.tools.idea.databinding.getViewBindingClassName
import com.android.tools.idea.databinding.index.BindingXmlIndex
import com.android.tools.idea.databinding.index.IndexedLayoutInfo
import com.android.tools.idea.databinding.index.ViewIdInfo
import com.android.tools.idea.res.binding.BindingLayoutGroup
import com.android.tools.idea.res.binding.BindingLayoutInfo
import com.android.tools.idea.res.binding.BindingLayoutInfo.LayoutType.DATA_BINDING_LAYOUT
import com.android.tools.idea.res.binding.PsiDataBindingResourceItem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import com.intellij.util.indexing.FileBasedIndex

/**
 * All values needed to generate a specific [LightBindingClass]
 */
interface LightBindingClassConfig {
  val targetLayout: BindingLayoutInfo

  /**
   * The fully-qualified name of the class which this class inherits from.
   */
  val superName: String

  /**
   * The exact class name that should be generated for this light binding class, potentially
   * including an "Impl" suffix.
   */
  val className: String

  /**
   * The exact qualified name that should be generated for this light binding class, potentially
   * including an "Impl" suffix.
   */
  val qualifiedName: String

  /**
   * A list of PSI references to the `<variable>` tags in the source layout.xml file.
   */
  val psiVariables: Collection<PsiDataBindingResourceItem>

  /**
   * Returns a list of all views with IDs that fields should be created for,
   * e.g. `<Button android:id="@+id/example_button">` -> "exampleButton"
   *
   * Note: These fields should only get generated for the base "Binding" class.
   */
  val viewIds: List<ViewIdInfo>

  /**
   * Returns `true` if the generated light binding class should have a full method API, e.g.
   * getters for its `<variable>` fields and other static methods.
   */
  fun shouldGenerateGettersAndStaticMethods(): Boolean

  /**
   * Returns `true` if we generate setters for a base class that should delegate to
   * child implementation classes.
   */
  fun settersShouldBeAbstract(): Boolean
}

/**
 * Used to generate a "Binding" class.
 *
 * A "Binding" class should always be created. "BindingImpl"s should only be created if there
 * are multiple layout configurations.
 */
class BindingClassConfig(private val group: BindingLayoutGroup) : LightBindingClassConfig {
  override val targetLayout: BindingLayoutInfo
    get() = group.mainLayout

  override val superName: String
    get() {
      // Main layout generates a binding class that inherits from the ViewDataBinding classes
      return if (targetLayout.psi.layoutType === DATA_BINDING_LAYOUT) {
        ModuleDataBinding.getInstance(targetLayout.psi.facet).dataBindingMode.viewDataBinding
      }
      else {
        targetLayout.psi.project.getViewBindingClassName()
      }
    }

  override val className = group.mainLayout.className
  override val qualifiedName = group.mainLayout.qualifiedName

  override val psiVariables: Collection<PsiDataBindingResourceItem>
    get() = group.aggregatedPsiItems[DataBindingResourceType.VARIABLE].values

  override val viewIds: List<ViewIdInfo>
    get() {
      fun searchIndexForViewIds(xmlFile: XmlFile): List<IndexedLayoutInfo> {
        return FileBasedIndex.getInstance()
          .getValues(BindingXmlIndex.NAME, BindingXmlIndex.getKeyForFile(xmlFile.virtualFile), GlobalSearchScope.fileScope(xmlFile))
      }
      return group.layouts
        .flatMap { info -> searchIndexForViewIds(info.psi.xmlPsiFile) }
        .flatMap { indexedInfo -> indexedInfo.viewIds }
    }

  override fun shouldGenerateGettersAndStaticMethods() = true

  // Make setters abstract if we are also going to generate "BindingImpl" classes which implement
  // them.
  override fun settersShouldBeAbstract() = group.layouts.size > 1
}

/**
 * Used to generate a "BindingImpl" class.
 *
 * This config should only be used when there are alternate layouts defined in addition to the
 * main one; otherwise, just use [BindingClassConfig].
 */
class BindingImplClassConfig(private val group: BindingLayoutGroup, private val layoutIndex: Int) : LightBindingClassConfig {
  override val targetLayout: BindingLayoutInfo
    get() = group.layouts[layoutIndex]

  /**
   * The super class for an impl is the main binding class.
   *
   * Its name is always this layout's name without the impl suffix.
   */
  override val superName: String
    get() = targetLayout.qualifiedName

  override val className = targetLayout.className + targetLayout.getImplSuffix()
  override val qualifiedName = targetLayout.qualifiedName + targetLayout.getImplSuffix()

  override val psiVariables: Collection<PsiDataBindingResourceItem>
    get() = group.aggregatedPsiItems[DataBindingResourceType.VARIABLE].values

  override val viewIds: List<ViewIdInfo>
    get() = listOf() // Only provided by base "Binding" class.

  override fun shouldGenerateGettersAndStaticMethods() = false
  override fun settersShouldBeAbstract() = false
}
