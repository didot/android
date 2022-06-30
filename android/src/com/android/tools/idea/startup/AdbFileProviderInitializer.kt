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
package com.android.tools.idea.startup

import com.android.tools.idea.adb.AdbFileProvider
import com.android.utils.reflection.qualifiedName
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import org.jetbrains.android.sdk.AndroidSdkUtils
import java.io.File
import java.util.function.Supplier

/**
 * Ensures [AdbFileProvider] is available for each new project
 *
 * Note: The reason this code need to live in the "android-core" module
 * is that it depends on [AndroidSdkUtils] which has many dependencies
 * that have not been factored out. Ideally, this class should be part
 * of the "android-adb" module.
 */
class AdbFileProviderInitializer : ProjectManagerListener {
  /**
   * Sets up the [AdbFileProvider] for each [Project]
   *
   * Note: this code runs on the EDT thread, so we need to avoid slow operations.
   */
  override fun projectOpened(project: Project) {
    val supplier = Supplier<File?> { getAdbPathAndReportError(project, project) }
    AdbFileProvider(supplier).storeInProject(project)
  }

  companion object {
    @JvmStatic
    fun initializeApplication() {
      val supplier = Supplier<File?> { getAdbPathAndReportError(null, ApplicationManager.getApplication()) }
      AdbFileProvider(supplier).storeInApplication()
    }

    private val LOG_ERROR_KEY: Key<Boolean> = Key.create(::LOG_ERROR_KEY.qualifiedName)

    private fun getAdbPathAndReportError(project: Project?, userData: UserDataHolder): File? {
      val result = AndroidSdkUtils.findAdb(project)

      if (result.adbPath == null) {
        // Log error only once per application or project
        if (userData.getUserData(LOG_ERROR_KEY) != true) {
          userData.putUserData(LOG_ERROR_KEY, true)
          thisLogger().warn("Location of ADB could not be determined for ${project ?: "application"}.\n" +
                            "The following paths were searched:\n" +
                            result.searchedPaths.joinToString("\n"))
        }
      }

      // Return path (it may be null)
      return result.adbPath
    }
  }
}
