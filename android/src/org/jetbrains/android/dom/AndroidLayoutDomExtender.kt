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
package org.jetbrains.android.dom

import com.android.SdkConstants
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.EvaluatedXmlName
import com.intellij.util.xml.EvaluatedXmlNameImpl
import com.intellij.util.xml.reflect.CustomDomChildrenDescription
import com.intellij.util.xml.reflect.DomExtender
import com.intellij.util.xml.reflect.DomExtensionsRegistrar
import org.jetbrains.android.dom.layout.LayoutViewElement
import org.jetbrains.android.facet.LayoutViewClassUtils

/**
 * [DomExtender] that registers a [CustomDomChildrenDescription] on tags corresponding to layouts, with completion variants for all
 * known view classes.
 *
 * Note that [CustomDomChildrenDescription.TagNameDescriptor.getCompletionVariants] runs on a background thread, as opposed to
 * [DomExtender.registerExtensions], so this way we avoid blocking the UI for completion.
 */
class AndroidLayoutDomExtender : DomExtender<LayoutViewElement>() {

  override fun registerExtensions(domElement: LayoutViewElement, registrar: DomExtensionsRegistrar) {
    if (StudioFlags.LAYOUT_XML_MODE.get() == StudioFlags.LayoutXmlMode.CUSTOM_CHILDREN) {
      registerCustomChildren(domElement, registrar)
    }
  }

  private fun registerCustomChildren(domElement: LayoutViewElement, registrar: DomExtensionsRegistrar) {
    val xmlTag = domElement.xmlElement as? XmlTag ?: return
    val androidFacet = xmlTag.androidFacet ?: return
    val tagClass = LayoutViewClassUtils.findClassByTagName(androidFacet, xmlTag.localName, SdkConstants.CLASS_VIEW) ?: return
    if (InheritanceUtil.isInheritor(tagClass, SdkConstants.CLASS_VIEWGROUP)) {
      registrar.registerCustomChildrenExtension(LayoutViewElement::class.java, ChildViewNameDescriptor)
    }
  }

  private object ChildViewNameDescriptor : CustomDomChildrenDescription.TagNameDescriptor() {
    override fun getCompletionVariants(parent: DomElement): Set<EvaluatedXmlName> {
      if (ApplicationManager.getApplication().isDispatchThread) {
        error("completion variants on the UI thread")
      }

      val result = mutableSetOf<EvaluatedXmlName>()
      SubtagsProcessingUtil.processSubTags(
        parent.androidFacet ?: return emptySet(),
        parent as? AndroidDomElement ?: return emptySet(),
        false
      ) { name, _ -> result.add(EvaluatedXmlNameImpl.createEvaluatedXmlName(name, null, true)) }
      return result
    }
  }
}