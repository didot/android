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
package com.android.tools.idea.gradle.dsl.model.android.packagingOptions

import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import org.jetbrains.annotations.SystemDependent
import org.junit.Test
import java.io.File

class ResourcesModelTest : GradleFileModelTestCase() {

  @Test
  fun testParse() {
    writeToBuildFile(TestFile.PARSE)
    val buildModel = gradleBuildModel
    val resourcesModel = buildModel.android().packagingOptions().resources()
    checkForValidPsiElement(resourcesModel, ResourcesModelImpl::class.java)
  }

  enum class TestFile(val path: @SystemDependent String) : TestFileName {
    PARSE("parse")
    ;

    override fun toFile(basePath: String, extension: String): File = super.toFile("$basePath/resourcesModel/$path", extension)
  }
}