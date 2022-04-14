/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.build.attribution.data

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.intellij.lang.properties.IProperty
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.android.refactoring.getProjectProperties
import org.jetbrains.android.refactoring.isAndroidx
import org.jetbrains.android.refactoring.isEnableJetifier
import org.jetbrains.kotlin.idea.util.application.runReadAction

data class StudioProvidedInfo(
  val agpVersion: GradleVersion?,
  val configurationCachingGradlePropertyState: String?,
  val buildInvocationType: BuildInvocationType,
  val enableJetifierPropertyState: Boolean,
  val useAndroidXPropertyState: Boolean,
  val buildRequestHolder: BuildRequestHolder
) {

  val isInConfigurationCacheTestFlow: Boolean get() = buildInvocationType == BuildInvocationType.CONFIGURATION_CACHE_TRIAL

  companion object {
    private const val CONFIGURATION_CACHE_PROPERTY_NAME = "org.gradle.unsafe.configuration-cache"

    fun fromProject(project: Project, buildRequest: BuildRequestHolder, buildInvocationType: BuildInvocationType) = StudioProvidedInfo(
      agpVersion = AndroidPluginInfo.find(project)?.pluginVersion,
      configurationCachingGradlePropertyState = runReadAction {
        project.getProjectProperties(createIfNotExists = false)?.findPropertyByKey(CONFIGURATION_CACHE_PROPERTY_NAME)?.value
      },
      buildInvocationType = buildInvocationType,
      enableJetifierPropertyState = project.isEnableJetifier(),
      useAndroidXPropertyState = project.isAndroidx(),
      buildRequestHolder = buildRequest
    )

    fun turnOnConfigurationCacheInProperties(project: Project) {
      project.getProjectProperties(createIfNotExists = true)?.apply {
        val property = WriteCommandAction.writeCommandAction(project, this.containingFile).compute<IProperty, Throwable> {
          findPropertyByKey(CONFIGURATION_CACHE_PROPERTY_NAME)?.apply { setValue("true") }
          ?: addProperty(CONFIGURATION_CACHE_PROPERTY_NAME, "true")
        }
        val propertyOffset = property?.psiElement?.textOffset ?: -1
        OpenFileDescriptor(project, virtualFile, propertyOffset).navigate(true)
      }
    }
  }
}