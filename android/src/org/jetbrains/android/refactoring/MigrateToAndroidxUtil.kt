/*
 * Copyright (C) 2018 The Android Open Source Project
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
@file:JvmName("MigrateToAndroidxUtil")

package org.jetbrains.android.refactoring

import com.android.SdkConstants
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import java.io.File

const val USE_ANDROIDX_PROPERTY = "android.useAndroidX"
const val ENABLE_JETIFIER_PROPERTY = "android.enableJetifier"

/**
 * Returns a [PropertiesFile] instance for the `gradle.properties` file in the given project or null if it does not exist.
 */
private fun Project.getProjectProperties(createIfNotExists: Boolean = false): PropertiesFile? {
  val projectBaseDirectory = VfsUtil.findFileByIoFile(File(FileUtil.toCanonicalPath(basePath)), true)
  val gradlePropertiesFile = if (createIfNotExists)
    projectBaseDirectory?.findOrCreateChildData(this, SdkConstants.FN_GRADLE_PROPERTIES)
  else
    projectBaseDirectory?.findChild(SdkConstants.FN_GRADLE_PROPERTIES)
  val psiPropertiesFile = PsiManager.getInstance(this).findFile(gradlePropertiesFile ?: return null)

  return if (psiPropertiesFile is PropertiesFile) psiPropertiesFile else null
}

fun Project.setAndroidxProperties(value: String = "true") {
  // Add gradle properties to enable the androidx handling
  getProjectProperties(true)?.let {
    it.findPropertyByKey(USE_ANDROIDX_PROPERTY)?.setValue(value) ?: it.addProperty(USE_ANDROIDX_PROPERTY, value)
    it.findPropertyByKey(ENABLE_JETIFIER_PROPERTY)?.setValue(value) ?: it.addProperty(ENABLE_JETIFIER_PROPERTY, value)
  }
}

/**
 * Checks that the "useAndroidx" is set explicitly. This method does not say anything about its value.
 */
fun Project.hasAndroidxProperty(): Boolean = ReadAction.compute<Boolean, RuntimeException> {
  getProjectProperties()?.findPropertyByKey(USE_ANDROIDX_PROPERTY) != null
}

/**
 * Checks that the "useAndroidx" property is set to true
 */
fun Project.isAndroidx(): Boolean = ReadAction.compute<Boolean, RuntimeException> {
  getProjectProperties()?.findPropertyByKey(USE_ANDROIDX_PROPERTY)?.value?.toBoolean() ?: false
}