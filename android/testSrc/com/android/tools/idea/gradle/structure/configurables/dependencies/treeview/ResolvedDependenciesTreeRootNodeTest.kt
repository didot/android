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
package com.android.tools.idea.gradle.structure.configurables.dependencies.treeview

import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.testStructure
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.android.DependencyTestCase
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.testResolve
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.openapi.project.Project
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.Assert.assertThat
import org.junit.Assume.assumeThat


class ResolvedDependenciesTreeRootNodeTest : DependencyTestCase() {
  private lateinit var resolvedProject: Project
  private lateinit var project: PsProject

  override fun setUp() {
    super.setUp()
    loadProject(TestProjectPaths.PSD_DEPENDENCY)
    reparse()
  }

  private fun reparse() {
    resolvedProject = myFixture.project
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
  }

  fun testTreeStructure() {
    val appModule = project.findModuleByGradlePath(":app") as PsAndroidModule
    val node = ResolvedDependenciesTreeRootNode(appModule, PsUISettings())

    // Note: indentation matters!
    val expectedProjectStructure = """
    app
        freeDebug
            mainModule
                lib1:1.0 (com.example.libs)
                    lib2:1.0 (com.example.libs)
                        lib3:1.0 (com.example.jlib)
                            lib4:1.0 (com.example.jlib)
                lib3:0.6→1.0 (com.example.jlib)
                    lib4:1.0 (com.example.jlib)
        freeDebugAndroidTest
            freeDebug
                mainModule
                    lib1:1.0 (com.example.libs)
                        lib2:1.0 (com.example.libs)
                            lib3:1.0 (com.example.jlib)
                                lib4:1.0 (com.example.jlib)
                    lib3:0.6→1.0 (com.example.jlib)
                        lib4:1.0 (com.example.jlib)
        freeDebugUnitTest
            freeDebug
                mainModule
                    lib1:1.0 (com.example.libs)
                        lib2:1.0 (com.example.libs)
                            lib3:1.0 (com.example.jlib)
                                lib4:1.0 (com.example.jlib)
                    lib3:0.6→1.0 (com.example.jlib)
                        lib4:1.0 (com.example.jlib)
        freeRelease
            mainModule
                lib1:1.0,0.9.1→1.0 (com.example.libs)
                    lib2:1.0 (com.example.libs)
                        lib3:1.0 (com.example.jlib)
                            lib4:1.0 (com.example.jlib)
                lib3:0.6→1.0 (com.example.jlib)
                    lib4:1.0 (com.example.jlib)
        freeReleaseUnitTest
            freeRelease
                mainModule
                    lib1:1.0,0.9.1→1.0 (com.example.libs)
                        lib2:1.0 (com.example.libs)
                            lib3:1.0 (com.example.jlib)
                                lib4:1.0 (com.example.jlib)
                    lib3:0.6→1.0 (com.example.jlib)
                        lib4:1.0 (com.example.jlib)
        paidDebug
            mainModule
                lib1:1.0 (com.example.libs)
                    lib2:1.0 (com.example.libs)
                        lib3:1.0 (com.example.jlib)
                            lib4:1.0 (com.example.jlib)
        paidDebugAndroidTest
            paidDebug
                mainModule
                    lib1:1.0 (com.example.libs)
                        lib2:1.0 (com.example.libs)
                            lib3:1.0 (com.example.jlib)
                                lib4:1.0 (com.example.jlib)
        paidDebugUnitTest
            paidDebug
                mainModule
                    lib1:1.0 (com.example.libs)
                        lib2:1.0 (com.example.libs)
                            lib3:1.0 (com.example.jlib)
                                lib4:1.0 (com.example.jlib)
        paidRelease
            mainModule
                lib1:1.0,0.9.1→1.0 (com.example.libs)
                    lib2:1.0 (com.example.libs)
                        lib3:1.0 (com.example.jlib)
                            lib4:1.0 (com.example.jlib)
        paidReleaseUnitTest
            paidRelease
                mainModule
                    lib1:1.0,0.9.1→1.0 (com.example.libs)
                        lib2:1.0 (com.example.libs)
                            lib3:1.0 (com.example.jlib)
                                lib4:1.0 (com.example.jlib)""".trimIndent()
    val treeStructure = node.testStructure { !it.name.startsWith("appcompat-v7") }
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    assertThat(treeStructure.toString(), equalTo(expectedProjectStructure))
  }

  fun testTreeStructure_javaModule() {
    val module = project.findModuleByGradlePath(":jModuleZ")!!
    val node = ResolvedDependenciesTreeRootNode(module, PsUISettings())

    // Note: indentation matters!
    val expectedProjectStructure = """
    jModuleZ
        testTreeStructure_javaModulejModuleZ
            jModuleK
                jModuleL
                    lib3:1.0 (com.example.jlib)
                        lib4:1.0 (com.example.jlib)
                lib3:0.9.1 (com.example.jlib)
                    lib4:0.9.1 (com.example.jlib)
                lib4:0.9.1 (com.example.jlib)
            jModuleL
                lib3:1.0 (com.example.jlib)
                    lib4:1.0 (com.example.jlib)
            nestedZ
                lib4:0.6 (com.example.jlib)
            lib4:0.6 (com.example.jlib)""".trimIndent()
    val treeStructure = node.testStructure { !it.name.startsWith("appcompat-v7") }
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    assertThat(treeStructure.toString(), equalTo(expectedProjectStructure))
  }

  fun testLibraryMatchingStructure() {
    val appModule = project.findModuleByGradlePath(":mainModule") as PsAndroidModule
    @Suppress("LocalVariableName")
    val lib1_09 = appModule.dependencies.findLibraryDependencies("com.example.libs", "lib1").firstOrNull { it.spec.version == "0.9.1" }
    assumeThat(lib1_09, notNullValue()); lib1_09!!
    val node = ResolvedDependenciesTreeRootNode(appModule, PsUISettings())

    // Note: indentation matters!
    val expectedProjectStructure = """
    mainModule
        freeDebug
        freeDebugAndroidTest
            freeDebug
        freeDebugUnitTest
            freeDebug
        freeRelease
            lib1:1.0,0.9.1→1.0 (com.example.libs)
        freeReleaseUnitTest
            freeRelease
                lib1:1.0,0.9.1→1.0 (com.example.libs)
        paidDebug
        paidDebugAndroidTest
            paidDebug
        paidDebugUnitTest
            paidDebug
        paidRelease
            lib1:1.0,0.9.1→1.0 (com.example.libs)
        paidReleaseUnitTest
            paidRelease
                lib1:1.0,0.9.1→1.0 (com.example.libs)""".trimIndent()

    val treeStructure = node.testStructure { it !is LibraryDependencyNode || it.matches(lib1_09) }
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    assertThat(treeStructure.toString(), equalTo(expectedProjectStructure))
  }
}
