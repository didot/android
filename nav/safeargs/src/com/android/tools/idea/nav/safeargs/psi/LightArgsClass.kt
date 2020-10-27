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
package com.android.tools.idea.nav.safeargs.psi

import com.android.ide.common.resources.ResourceItem
import com.android.tools.idea.nav.safeargs.index.NavFragmentData
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.facet.AndroidFacet

/**
 * Light class for Args classes generated from navigation xml files.
 *
 * An "Arg" represents an argument which can get passed from one destination to another.
 *
 * For example, if you had the following "nav.xml":
 *
 * ```
 * <argument
 *    android:name="message"
 *    app:argType="string" />
 * ```
 *
 * This would generate a class like the following:
 *
 * ```
 *  class EditorFragmentArgs {
 *    static EditorFragmentArgs fromBundle(Bundle bundle);
 *    String getMessage();
 *  }
 * ```
 */
class LightArgsClass(facet: AndroidFacet,
                     private val modulePackage: String,
                     navigationResource: ResourceItem,
                     val fragment: NavFragmentData)
  : SafeArgsLightBaseClass(facet, modulePackage, "Args", navigationResource, fragment.toDestination()) {

  init {
    setModuleInfo(facet.module, false)
  }

  val builderClass = LightArgsBuilderClass(facet, modulePackage, this)
  private val _fields by lazy { computeFields() }
  private val _methods by lazy { computeMethods() }
  private val backingXmlTag by lazy { backingResourceFile?.getXmlTagById(fragment.id) }

  override fun getInnerClasses(): Array<PsiClass> = arrayOf(builderClass)
  override fun findInnerClassByName(name: String, checkBases: Boolean): PsiClass? {
    return builderClass.takeIf { it.name == name }
  }

  override fun getMethods() = _methods
  override fun getAllMethods() = methods
  override fun findMethodsByName(name: String, checkBases: Boolean): Array<PsiMethod> {
    return allMethods.filter { method -> method.name == name }.toTypedArray()
  }

  override fun getNavigationElement(): PsiElement {
    return backingXmlTag ?: return super.getNavigationElement()
  }

  private fun computeMethods(): Array<PsiMethod> {
    val thisType = PsiTypesUtil.getClassType(this)
    val bundleType = parsePsiType(modulePackage, "android.os.Bundle", null, this)
    val fromBundle = createMethod(name = "fromBundle",
                                  modifiers = MODIFIERS_STATIC_PUBLIC_METHOD,
                                  returnType = annotateNullability(thisType))
      .addParameter("bundle", bundleType)

    val toBundle = createMethod(
      name = "toBundle",
      returnType = annotateNullability(bundleType)
    )

    val getters: Array<PsiMethod> = fragment.arguments.map { arg ->
      val psiType = parsePsiType(modulePackage, arg.type, arg.defaultValue, this)
      createMethod(name = "get${arg.name.capitalize()}",
                   navigationElement = getFieldNavigationElementByName(arg.name),
                   returnType = annotateNullability(psiType, arg.nullable))
    }.toTypedArray()

    return getters + fromBundle + toBundle
  }

  private fun computeFields(): Array<PsiField> {
    return fragment.arguments
      .asSequence()
      .map { createField(it, modulePackage, backingXmlTag as XmlTag) }
      .toList()
      .toTypedArray()
  }

  fun getFieldNavigationElementByName(name: String): PsiElement? {
    return _fields.firstOrNull { it.name == name }?.navigationElement
  }
}
