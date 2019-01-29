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
package com.android.tools.idea.tests.gui.framework

import com.android.SdkConstants
import com.android.tools.idea.npw.model.MultiTemplateRenderer
import com.android.tools.idea.npw.model.MultiTemplateRenderer.TemplateRendererListener
import com.android.tools.idea.testing.AndroidGradleTests
import com.google.common.base.Charsets
import com.google.common.io.Files
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectLifecycleListener
import com.intellij.util.messages.MessageBusConnection
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

/**
 * Rule to update a new Project created with the New Project Wizard.
 * This rule waits that NPW generates all files (from the NPW Templates) and before the project is imported:
 * - If "build.gradle" is found, it adds to it a list of local repositories
 * - More modification can be added later
 */
internal class NpwControl : TestWatcher() {
  private val myApplicationMessageBus = ApplicationManager.getApplication().messageBus.connect()
  private lateinit var myProjectMessageBus: MessageBusConnection

  override fun starting(description: Description) {
    myApplicationMessageBus.subscribe(ProjectLifecycleListener.TOPIC, object : ProjectLifecycleListener {
      override fun beforeProjectLoaded(project: Project) {
        myProjectMessageBus = MultiTemplateRenderer.subscribe(project, object : TemplateRendererListener {
          override fun multiRenderingFinished() {
            myProjectMessageBus.disconnect()
            updateProjectFiles(project)
          }
        })
      }
    })
  }

  override fun finished(description: Description) {
    myApplicationMessageBus.disconnect()
  }

  private fun updateProjectFiles(project: Project) {
    val gradleFile = File(project.basePath!!, SdkConstants.FN_BUILD_GRADLE)
    if (gradleFile.exists()) {
      val origContent = Files.toString(gradleFile, Charsets.UTF_8)
      val newContent = AndroidGradleTests.updateLocalRepositories(origContent, AndroidGradleTests.getLocalRepositoriesForGroovy())
      if (newContent != origContent) {
        Files.write(newContent, gradleFile, Charsets.UTF_8)
      }
    }
  }
}