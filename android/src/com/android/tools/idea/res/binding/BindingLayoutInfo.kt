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
package com.android.tools.idea.res.binding

import com.android.SdkConstants
import com.android.ide.common.resources.DataBindingResourceType
import com.android.tools.idea.databinding.DataBindingUtil
import com.android.tools.idea.res.binding.BindingLayoutInfo.LayoutType.DATA_BINDING_LAYOUT
import com.android.tools.idea.res.binding.BindingLayoutInfo.LayoutType.VIEW_BINDING_LAYOUT
import com.intellij.openapi.util.ModificationTracker

/**
 * Info for a single, target layout XML file useful for generating a Binding or BindingImpl class
 * (assuming it is a data binding or view binding layout).
 *
 * See also: [BindingLayoutGroup], which owns one (or more) related [BindingLayoutInfo] instances.
 *
 * @param modulePackage The package for the current module, useful when generating this binding's
 *    path. While in practice this shouldn't be null, a user or test configuration may have left
 *    this unset. See also: `MergedManifestSnapshot.getPackage`.
 *    TODO(b/138720985): See if it's possible to make modulePackage non-null
 * @param layoutFolderName The name of the folder that the layout for this binding is located in;
 *    the folder defines the layout's configuration (e.g. "layout-land")
 * @param layoutFileName The name of the layout.xml file
 * @param customBindingName A name which, if present, modifies the logic for choosing a name for
 *    a generated binding class.
 */
class BindingLayoutInfo(private val modulePackage: String?,
                        layoutFolderName: String,
                        layoutFileName: String,
                        customBindingName: String?) : ModificationTracker {
  /**
   * The package + name for the binding class we want to generate for this layout.
   *
   * The package for a binding class is usually a subpackage of [modulePackage], but it can be
   * fully customized based on the value passed in for `customBindingName`
   *
   * See also: [getImplSuffix], if you want to generate the path to a binding impl class instead.
   */
  private class BindingClassPath(val packageName: String, val className: String)

  /**
   * The different android layouts that we create [BindingLayoutInfo] for, depending on whether data binding or view binding is
   * switched on.
   *
   * [VIEW_BINDING_LAYOUT] bindings are generated for legacy views - those that are not data binding views.
   *
   * [DATA_BINDING_LAYOUT] bindings are generated for views using data binding. They start with `<layout>` and `<data>`
   * tags.
   *
   * When both are enabled, data binding layouts will be of type [DATA_BINDING_LAYOUT], the rest will be [VIEW_BINDING_LAYOUT].
   *
   * Note: This enum is used by DataBindingXmlIndex and is serialized and de-serialized. Please only append.
   */
  enum class LayoutType {
    VIEW_BINDING_LAYOUT,
    DATA_BINDING_LAYOUT
  }

  /**
   * Relevant, non-PSI information related to or extracted from the XML layout associated with this
   * binding.
   *
   * Note: This information can also be requested through the [psi] field, but doing so may cause a
   * more expensive parsing pass to happen to lazily generate a PSI tree. Prefer using the raw xml
   * data directly whenever possible.
   */
  var xml = BindingLayoutXml(layoutFolderName, layoutFileName, customBindingName)
    private set

  /**
   * TODO(b/136500593): This should become nullable, or at least changed to be fetched lazily, since
   *  we don't want to request a PSI representation of this layout until we are sure we need it.
   *  For now, as we make changes to these classes incrementally and need to support old behavior,
   *  we always set this immediately after creating an instance of this class.
   */
  lateinit var psi: BindingLayoutPsi

  /**
   * Note: This backing field is lazily loaded but potentially reset by [updateClassData].
   */
  private var _bindingClassPath: BindingClassPath? = null
  private val bindingClassPath: BindingClassPath
    get() {
      if (_bindingClassPath == null) {
        if (xml.customBindingName.isNullOrEmpty()) {
          _bindingClassPath = BindingClassPath("$modulePackage.databinding",
                                               DataBindingUtil.convertToJavaClassName(xml.fileName) + "Binding")
        }
        else {
          val customBindingName = xml.customBindingName!!
          val firstDotIndex = customBindingName.indexOf('.')

          if (firstDotIndex < 0) {
            _bindingClassPath = BindingClassPath("$modulePackage.databinding", customBindingName)
          }
          else {
            val lastDotIndex = customBindingName.lastIndexOf('.')
            val packageName = if (firstDotIndex == 0) {
              // A custom name like ".ExampleBinding" generates a binding class in the module package
              modulePackage + customBindingName.substring(0, lastDotIndex)
            }
            else {
              customBindingName.substring(0, lastDotIndex)
            }
            val className = customBindingName.substring(lastDotIndex + 1)
            _bindingClassPath = BindingClassPath(packageName, className)
          }
        }
      }

      return _bindingClassPath!!
    }

  val packageName
    get() = bindingClassPath.packageName
  val className
    get() = bindingClassPath.className
  val qualifiedName
    get() = "${packageName}.${className}"

  internal var modificationCount: Long = 0

  /**
   * Returns the unique "Impl" suffix for this specific layout configuration.
   *
   * In multi-layout configurations, a general "Binding" class will be generated as well as a
   * unique "Impl" version for each configuration. This method returns what that exact "Impl"
   * suffix should be, which can safely be appended to [qualifiedName] or [className].
   */
  fun getImplSuffix(): String {
    val folderName = xml.folderName
    return when {
      folderName.isEmpty() -> "Impl"
      folderName.startsWith("layout-") ->
        DataBindingUtil.convertToJavaClassName(folderName.substringAfter("layout-")) + "Impl"
      folderName.startsWith("layout") -> "Impl"
      else -> DataBindingUtil.convertToJavaClassName(folderName) + "Impl"
    }
  }

  /**
   * Given an alias or (unqualified) type name, returns the (qualified) type it resolves to, if such
   * a rule is registered with this layout (e.g.
   * `"Calc"` for `<import alias='Calc' type='org.example.math.calc.Calculator'>` or
   * `"Map"` for `<import type='java.util.Map'>`)
   */
  fun resolveImport(aliasOrType: String): String? = xml.imports.find { import -> import.aliasOrType == aliasOrType }?.type

  /**
   * Updates settings for generating the final Binding name for this layout.
   */
  fun updateClassData(customBindingName: String?, modificationCount: Long) {
    if (xml.customBindingName == customBindingName) {
      return
    }

    xml = xml.copy(customBindingName = customBindingName)
    _bindingClassPath = null // Causes this to regenerate lazily next time

    this.modificationCount = modificationCount
  }

  /**
   * Updates this layout info with a whole new set of `<data>` values.
   *
   * Both the data model ([xml]) as well as the PSI information ([psi]) are updated.
   */
  fun replaceDataItems(newDataItems: List<PsiDataBindingResourceItem>, modificationCount: Long) {
    val variables = mutableListOf<BindingLayoutXml.Variable>()
    val imports = mutableListOf<BindingLayoutXml.Import>()
    newDataItems.forEach { dataItem ->
      when {
        dataItem.type == DataBindingResourceType.VARIABLE -> variables.add(
          BindingLayoutXml.Variable(dataItem.name, dataItem.typeDeclaration))
        dataItem.type == DataBindingResourceType.IMPORT -> imports.add(
          BindingLayoutXml.Import(dataItem.typeDeclaration!!, dataItem.getExtra(SdkConstants.ATTR_ALIAS)))
      }
    }
    xml = xml.copy(variables = variables, imports = imports)

    if (psi.replaceDataItems(newDataItems)) {
      this.modificationCount = modificationCount
    }
  }

  override fun getModificationCount(): Long = modificationCount
}
