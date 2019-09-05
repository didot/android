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
package com.android.tools.idea.lang.databinding.model

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType

/**
 * PSI wrapper around psi methods that additionally expose information particularly useful in data binding expressions.
 *
 * Note: This class is adapted from [android.databinding.tool.reflection.ModelMethod] from db-compiler.
 */
class PsiModelMethod(val containingClass: PsiModelClass, val psiMethod: PsiMethod) {

  val parameterTypes by lazy(LazyThreadSafetyMode.NONE) {
    psiMethod.parameterList.parameters.map { PsiModelClass(it.type, containingClass.mode) }.toTypedArray()
  }

  val name: String
    get() = psiMethod.name

  val returnType: PsiModelClass?
    get() = psiMethod.returnType?.let { PsiModelClass(containingClass.substitutor.substitute(it), containingClass.mode) }

  val isVoid = PsiType.VOID == psiMethod.returnType

  val isPublic = psiMethod.hasModifierProperty(PsiModifier.PUBLIC)

  val isProtected = psiMethod.hasModifierProperty(PsiModifier.PROTECTED)

  val isStatic = psiMethod.hasModifierProperty(PsiModifier.STATIC)

  val isAbstract = psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)

  /**
   * Returns true if the final parameter is a varargs parameter.
   */
  val isVarArgs = psiMethod.isVarArgs

  /**
   * @param args The arguments to the method
   * @return Whether the arguments would be accepted as parameters to this method.
   */
  // b/129771951 revisit the case when unwrapObservableFields is true
  fun acceptsArguments(args: List<PsiModelClass>): Boolean {
    if ((!isVarArgs && args.size != parameterTypes.size) || (isVarArgs && args.size < parameterTypes.size - 1)) {
      return false // The wrong number of parameters
    }
    var parametersMatch = true
    var i = 0
    while (i < args.size && parametersMatch) {
      // If we are indexing past the end of the parameter list with a varargs function,
      // it means we are referencing a parameter that is really a part of the varargs
      // parameter (which is the last one)
      var parameterType = parameterTypes[i.coerceAtMost(parameterTypes.lastIndex)]
      val arg = args[i]
      if (parameterType.isIncomplete) {
        parameterType = parameterType.erasure()
      }
      // TODO: b/130429958 check if the parameterType is an implicit conversion from the arg
      if (!parameterType.isAssignableFrom(arg)) {
        parametersMatch = false
      }
      i++
    }
    return parametersMatch
  }
}
