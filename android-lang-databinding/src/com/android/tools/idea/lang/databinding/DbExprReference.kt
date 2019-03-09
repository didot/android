/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.lang.databinding

import com.android.tools.idea.lang.databinding.model.ModelClassResolvable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.util.IncorrectOperationException

private fun getElementRange(element: PsiElement): TextRange {
  val startOffsetInParent = element.parent.startOffsetInParent
  return if (startOffsetInParent > 0) element.textRange.shiftRight(-startOffsetInParent) else element.textRange
}

/**
 * A base class for references found within a data binding expression.
 * TODO: Right now this class is only inherited by classes inside [DataBindingExprReferenceContributor].
 *  We might as well move this inside along with them, to keep related code together.
 */
internal abstract class DbExprReference(private val psiElement: PsiElement,
                                        private val resolveTo: PsiElement,
                                        private val textRange: TextRange = getElementRange(psiElement)) :
  ModelClassResolvable, PsiReference {


  override fun getElement(): PsiElement {
    return psiElement
  }

  override fun getRangeInElement(): TextRange {
    return textRange
  }

  override fun resolve(): PsiElement? {
    return resolveTo
  }

  override fun getCanonicalText(): String {
    return psiElement.text
  }

  override fun handleElementRename(newElementName: String): PsiElement? {
    return null
  }

  override fun bindToElement(element: PsiElement): PsiElement? {
    return null
  }

  override fun isReferenceTo(element: PsiElement): Boolean {
    return element.manager.areElementsEquivalent(resolve(), psiElement)
  }

  override fun isSoft(): Boolean {
    return false
  }
}
