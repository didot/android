/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose

import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.MavenRepo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path

/**
 * Ensures that Compose Preview works for an XML file.
 */
class ComposePreviewXml {

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun basic() {
    val system = AndroidSystem.standard(tempFolder.root.toPath())

    // Create a new android project, and set a fixed distribution
    val project = AndroidProject("tools/adt/idea/compose-designer/testData/projects/composepreview")
    project.setDistribution("tools/external/gradle/gradle-7.3.3-bin.zip")

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(MavenRepo("tools/adt/idea/compose-designer/compose_preview_deps.manifest"))
    system.runStudio(project) { studio ->
      studio.waitForSync()
      studio.waitForIndex()

      val path: Path = project.targetProject.resolve("app/src/main/res/drawable/ic_launcher_background.xml")
      studio.openFile("ComposePreviewTest", path.toString())
      studio.waitForComponentByClass("DesignSurfaceScrollPane", "JBViewport", "SceneViewPanel")
    }
  }
}
