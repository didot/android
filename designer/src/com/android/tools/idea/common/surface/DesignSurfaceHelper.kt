/*
 * Copyright (C) 2017 The Android Open Source Project
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
@file:JvmName("DesignSurfaceHelper")
package com.android.tools.idea.common.surface

import com.android.SdkConstants.*
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.tools.idea.npw.assetstudio.IconGenerator
import com.android.tools.idea.npw.assetstudio.MaterialDesignIcons
import com.android.tools.idea.res.ResourceRepositoryManager
import com.google.common.io.CharStreams
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.lang.annotations.Language
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

private val logger: Logger by lazy { Logger.getInstance("DesignSurfaceHelper") }

fun moduleContainsResource(facet: AndroidFacet, type: ResourceType, name: String): Boolean {
  return ResourceRepositoryManager.getModuleResources(facet).hasResources(ResourceNamespace.TODO(), type, name)
}

fun copyVectorAssetToMainModuleSourceSet(project: Project, facet: AndroidFacet, asset: String) {
  val path = MaterialDesignIcons.getPathForBasename(asset)

  try {
    InputStreamReader(IconGenerator::class.java.classLoader.getResourceAsStream(path), StandardCharsets.UTF_8).use {
      reader -> createResourceFile(project, facet, FD_RES_DRAWABLE, asset + DOT_XML, CharStreams.toString(reader))
    }
  }
  catch (exception: IOException) {
    logger.warn(exception)
  }
}

fun copyLayoutToMainModuleSourceSet(project: Project, facet: AndroidFacet, layout: String, @Language("XML") xml: String) {
  val message = "Do you want to copy layout $layout to your main module source set?"

  if (Messages.showYesNoDialog(project, message, "Copy Layout", Messages.getQuestionIcon()) == Messages.NO) {
    return
  }

  createResourceFile(project, facet, FD_RES_LAYOUT, layout + DOT_XML, xml)
}

private fun createResourceFile(project: Project,
                               facet: AndroidFacet,
                               resourceDirectory: String,
                               resourceFileName: String,
                               resourceFileContent: CharSequence) {
  WriteCommandAction.runWriteCommandAction(project) {
    try {
      val directory = getResourceDirectoryChild(project, facet, resourceDirectory) ?: return@runWriteCommandAction

      val document = FileDocumentManager.getInstance().getDocument(directory.createChildData(project, resourceFileName))!!

      document.setText(resourceFileContent)
    }
    catch (exception: IOException) {
      logger.warn(exception)
    }
  }
}

@Throws(IOException::class)
private fun getResourceDirectoryChild(project: Project, facet: AndroidFacet, child: String): VirtualFile? {
  val resourceDirectory = ResourceFolderManager.getInstance(facet).primaryFolder

  if (resourceDirectory == null) {
    logger.warn("resourceDirectory is null")
    return null
  }

  return resourceDirectory.findChild(child) ?: return resourceDirectory.createChildDirectory(project, child)
}
