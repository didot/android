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
package com.android.tools.idea.templates.recipe

import com.android.tools.idea.wizard.template.SourceSetType
import com.android.tools.idea.wizard.template.findResource
import com.intellij.openapi.vfs.VfsUtil
import com.android.tools.idea.wizard.template.RecipeExecutor as RecipeExecutor2
import java.io.File

/**
 * [RecipeExecutor] that collects references as a result of executing instructions in a [Recipe].
 */
internal class FindReferencesRecipeExecutor2(private val context: RenderingContext2) : RecipeExecutor2 {
  override fun hasDependency(mavenCoordinate: String, configuration: String?): Boolean {
    return false
  }

  override fun save(source: String, to: File, trimVertical: Boolean, squishEmptyLines: Boolean) {
    addTargetFile(to)
  }

  override fun mergeXml(source: String, to: File) {
    addTargetFile(to)
  }

  override fun open(file: File) {
    context.filesToOpen.add(resolveTargetFile(file))
  }

  override fun copy(from: File, to: File) {
    val sourceUrl = findResource(context.templateData.javaClass, from)
    val sourceFile = VfsUtil.findFileByURL(sourceUrl) ?: error("$from ($sourceUrl)")
    if (sourceFile.isDirectory) {
      return
    }
    addTargetFile(to)
  }

  override fun createDirectory(at: File) {}

  override fun applyPlugin(plugin: String) {
    context.plugins.add(plugin)
  }

  override fun addClasspathDependency(mavenCoordinate: String) {
    context.classpathEntries.add(mavenCoordinate)
  }

  override fun addDependency(mavenCoordinate: String, configuration: String) {
    context.dependencies.put(configuration, mavenCoordinate)
  }

  override fun addModuleDependency(configuration: String, moduleName: String, toModule: File) {}

  fun addTargetFile(file: File) {
    context.targetFiles.add(resolveTargetFile(file))
  }

  private fun resolveTargetFile(file: File): File = if (file.isAbsolute) file else File(context.outputRoot, file.path)

  override fun addSourceSet(type: SourceSetType, name: String, dir: File) {
  }

  override fun setExtVar(name: String, value: Any) {
  }

  override fun addIncludeToSettings(moduleName: String) {}

  override fun setBuildFeature(name: String, value: Boolean) {}

  override fun requireJavaVersion(version: String, kotlinSupport: Boolean) {}
  override fun addDynamicFeature(name: String, toModule: File) {}
}
