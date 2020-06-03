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
package com.android.tools.idea.nav.safeargs.psi.kotlin

import com.android.tools.idea.nav.safeargs.psi.java.getPsiTypeStr
import com.google.common.annotations.VisibleForTesting
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.jvm.JvmPrimitiveType
import org.jetbrains.kotlin.types.ErrorUtils.createUnresolvedType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.makeNullable

/**
 * Return kotlin type with nullability info.
 *
 * It falls back to [fallbackType] if defined. Else [UnresolvedType] is returned.
 */
@VisibleForTesting
fun KotlinBuiltIns.getKotlinType(
  typeStr: String?,
  defaultValue: String?,
  moduleDescriptor: ModuleDescriptor,
  isNonNull: Boolean = true,
  fallbackType: KotlinType? = null
): KotlinType {
  val resolvedTypeStr = getPsiTypeStr(moduleDescriptor.fqNameSafe.toString(), typeStr, defaultValue)

  // array type
  if (resolvedTypeStr.endsWith("[]")) {
    val type = resolvedTypeStr.removeSuffix("[]")
    return try {
      JvmPrimitiveType.get(type).primitiveType.let {
        getPrimitiveArrayKotlinType(it)
      }
    }
    catch (e: AssertionError) {
      this.getArrayType(Variance.INVARIANT, getKotlinTypeFromDescriptor(FqName(type), moduleDescriptor, fallbackType))
    }
  }

  return try {
    JvmPrimitiveType.get(resolvedTypeStr).primitiveType.let {
      getPrimitiveKotlinType(it)
    }
  }
  catch (e: AssertionError) {
    val rawType = getKotlinTypeFromDescriptor(FqName(resolvedTypeStr), moduleDescriptor, fallbackType)

    if (isNonNull) return rawType
    else return rawType.makeNullable()
  }
}

private fun getKotlinTypeFromDescriptor(
  fqName: FqName,
  moduleDescriptor: ModuleDescriptor,
  fallbackType: KotlinType?
): KotlinType {
  return JavaToKotlinClassMap.mapJavaToKotlin(fqName)?.let {
    moduleDescriptor.findClassAcrossModuleDependencies(it)?.defaultType
  } ?: ClassId.topLevel(fqName).let {
    moduleDescriptor.findClassAcrossModuleDependencies(it)?.defaultType
  } ?: fallbackType ?: fqName.getUnresolvedType()
}

private fun FqName.getUnresolvedType(): KotlinType {
  val presentableName = this.toString()
  return createUnresolvedType(presentableName, emptyList())
}