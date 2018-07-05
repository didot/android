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
package com.android.tools.idea.gradle.structure.model

import com.android.builder.model.AndroidProject.ARTIFACT_MAIN
import com.android.tools.idea.gradle.structure.model.android.*
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.testing.TestProjectPaths.PSD_DEPENDENCY
import com.intellij.openapi.project.Project
import org.hamcrest.CoreMatchers.*
import org.junit.Assert.assertThat

class DependencyManagementTest : DependencyTestCase() {
  private lateinit var resolvedProject: Project
  private lateinit var project: PsProject

  override fun setUp() {
    super.setUp()
    loadProject(PSD_DEPENDENCY)
    reparse()
  }

  private fun reparse() {
    resolvedProject = myFixture.project
    project = PsProjectImpl(resolvedProject)
  }

  fun testParsedDependencies() {
    run {
      val appModule = project.findModuleByName("app") as PsAndroidModule
      assertThat(appModule.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
      val moduleDependency = appModule.dependencies.findModuleDependency(":mainModule")
      assertThat(moduleDependency, notNullValue())
      assertThat(moduleDependency?.joinedConfigurationNames, equalTo("implementation"))
      val libModule = project.findModuleByName("mainModule") as PsAndroidModule
      val lib10 = libModule.dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
      val lib091 = libModule.dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1")
      assertThat(lib10.testDeclaredScopes(), hasItems("implementation", "debugImplementation"))
      assertThat(lib091.testDeclaredScopes(), hasItems("releaseImplementation"))
      assertThat(libModule.dependencies.findLibraryDependency("com.example.libs:lib2:1.0"), nullValue())
      assertThat(libModule.dependencies.findLibraryDependency("com.example.jlib:lib3:1.0"), nullValue())
      assertThat(libModule.dependencies.findLibraryDependency("com.example.jlib:lib4:1.0"), nullValue())
    }
    run {
      val libModule = project.findModuleByName("modulePlus") as PsAndroidModule
      val lib1 = libModule.dependencies.findLibraryDependency("com.example.libs:lib1:0.+")
      assertThat(lib1.testDeclaredScopes(), hasItems("implementation"))

      val module1 = libModule.dependencies.findModuleDependencies(":jModuleK")
      assertThat(module1.testDeclaredScopes(), hasItems("implementation"))
    }
    run {
      val jLibModule = project.findModuleByName("jModuleK") as PsJavaModule

      val lib1 = jLibModule.dependencies.findLibraryDependency("com.example.jlib:lib4:0.6")
      assertThat(lib1.testDeclaredScopes(), hasItems("implementation"))
      val lib2 = jLibModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1")
      assertThat(lib2.testDeclaredScopes(), hasItems("implementation"))

      val module1 = jLibModule.dependencies.findModuleDependencies(":jModuleL")
      assertThat(module1.testDeclaredScopes(), hasItems("implementation"))
    }
  }

  fun testParsedDependencies_variables() {
    run {
      val libModule = project.findModuleByName("mainModule") as PsAndroidModule
      val lib306 = libModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.6")
      assertThat(lib306.testDeclaredScopes(), equalTo(listOf("freeImplementation")))
      val depLib306 = lib306!![0]
      assertThat<ParsedValue<String>>(depLib306.version, equalTo(ParsedValue.Set.Parsed("0.6", DslText.Reference("var06"))))
    }
    run {
      val libModule = project.findModuleByName("jModuleK") as PsJavaModule
      val lib3091 = libModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1")
      assertThat(lib3091.testDeclaredScopes(), equalTo(listOf("implementation")))
      val depLib3091 = lib3091!![0]
      assertThat(depLib3091.spec.compactNotation(), equalTo("com.example.jlib:lib3:0.9.1"))
      // TODO(b/111174250): Assert values of not yet existing properties.
    }
    run {
      val libModule = project.findModuleByName("jModuleL") as PsJavaModule
      val lib310 = libModule.dependencies.findLibraryDependency("com.example.jlib:lib3:1.0")
      assertThat(lib310.testDeclaredScopes(), equalTo(listOf("implementation")))
      val depLib310 = lib310!![0]
      assertThat(depLib310.spec.compactNotation(), equalTo("com.example.jlib:lib3:1.0"))
      // TODO(b/111174250): Assert values of not yet existing properties.
    }
  }

  fun testResolvedDependencies() {
    val libModule = project.findModuleByName("mainModule") as PsAndroidModule
    val jLibModule = project.findModuleByName("jModuleK") as PsJavaModule

    run {
      val artifact = libModule.findVariant("paidDebug")!!.findArtifact(ARTIFACT_MAIN)
      val dependencies = artifact!!.dependencies
      val lib1 = dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
      val lib2 = dependencies.findLibraryDependency("com.example.libs:lib2:1.0")
      val lib3 = dependencies.findLibraryDependency("com.example.jlib:lib3:1.0")
      val lib4 = dependencies.findLibraryDependency("com.example.jlib:lib4:1.0")
      assertThat(lib1.testDeclared(), hasItems(true))
      assertThat(lib2.testDeclared(), hasItems(false))
      assertThat(lib3.testDeclared(), hasItems(false))
      assertThat(lib4.testDeclared(), hasItems(false))
    }

    run {
      val artifact = libModule.findVariant("paidRelease")!!.findArtifact(ARTIFACT_MAIN)
      val dependencies = artifact!!.dependencies
      val lib1 = dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
      val lib2 = dependencies.findLibraryDependency("com.example.libs:lib2:1.0")
      val lib3 = dependencies.findLibraryDependency("com.example.jlib:lib3:1.0")
      val lib4 = dependencies.findLibraryDependency("com.example.jlib:lib4:1.0")
      assertThat(lib1.testDeclared(), hasItems(true))
      assertThat(lib2.testDeclared(), hasItems(false))
      assertThat(lib3.testDeclared(), hasItems(false))
      assertThat(lib4.testDeclared(), hasItems(false))
    }

    run {
      val dependencies = jLibModule.resolvedDependencies
      val lib3 = dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1")
      val lib4old = dependencies.findLibraryDependency("com.example.jlib:lib4:0.6")
      val lib4new = dependencies.findLibraryDependency("com.example.jlib:lib4:0.9.1")
      assertThat(lib3.testDeclared(), hasItems(true))
      assertThat(lib4old, nullValue())
      assertThat(lib4new.testDeclared(), hasItems(true))
    }
  }

  fun testParsedModelMatching() {
    run {
      val libModule = project.findModuleByName("mainModule") as PsAndroidModule
      assertThat(libModule.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())

      val artifact = libModule.findVariant("paidDebug")!!.findArtifact(ARTIFACT_MAIN)
      val dependencies = artifact!!.dependencies
      val lib1 = dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
      assertThat(lib1.testDeclared(), hasItems(true))
      assertThat(lib1.testMatchingScopes(), hasItems("implementation:debugImplementation"))
    }

    run {
      // TODO(b/110774403): Properly support test scopes in Java modules.
      val jLibModule = project.findModuleByName("jModuleM") as PsJavaModule
      assertThat(jLibModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1"), notNullValue())

      val dependencies = jLibModule.resolvedDependencies
      val lib3 = dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1")
      assertThat(lib3.testDeclared(), hasItems(true))
      assertThat(lib3.testMatchingScopes(), hasItems("implementation"))

      val lib4 = dependencies.findLibraryDependency("com.example.jlib:lib4:0.9.1")
      assertThat(lib4.testDeclared(), hasItems(false))
    }
  }

  fun testPromotedParsedModelMatching() {
    run {
      val libModule = project.findModuleByName("mainModule") as PsAndroidModule
      assertThat(libModule.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
      assertThat(libModule.dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1"), notNullValue())

      val artifact = libModule.findVariant("paidRelease")!!.findArtifact(ARTIFACT_MAIN)
      val dependencies = artifact!!.dependencies
      val lib1 = dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
      assertThat(lib1.testDeclared(), hasItems(true))
      // Despite requesting a different version the 'releaseImplementation' configuration should be included in the promoted
      // version of the resolved dependency since it is where it tries to contribute to.
      assertThat(lib1.testMatchingScopes(), hasItems("implementation:releaseImplementation"))
    }

    run {
      // TODO(b/110774403): Properly support test scopes in Java modules.
      val jLibModule = project.findModuleByName("jModuleK") as PsJavaModule
      assertThat(jLibModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1"), notNullValue())

      val dependencies = jLibModule.resolvedDependencies
      val lib3 = dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1")
      assertThat(lib3.testDeclared(), hasItems(true))
      assertThat(lib3.testMatchingScopes(), hasItems("implementation"))

      val lib4 = dependencies.findLibraryDependency("com.example.jlib:lib4:0.9.1")
      assertThat(lib4.testDeclared(), hasItems(true))
      assertThat(lib4.testMatchingScopes(), hasItems("implementation"))
      assertThat(lib4?.first()?.declaredDependencies?.first()?.version, equalTo("0.6".asParsed()))
    }
  }

  fun testPlusParsedModelMatching() {
    val libModule = project.findModuleByName("modulePlus") as PsAndroidModule
    assertThat(libModule.dependencies.findLibraryDependency("com.example.libs:lib1:0.+"), notNullValue())

    val artifact = libModule.findVariant("release")!!.findArtifact(ARTIFACT_MAIN)
    val dependencies = artifact!!.dependencies
    val lib1 = dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1")
    assertThat(lib1.testDeclared(), hasItems(true))
    assertThat(lib1.testMatchingScopes(), hasItems("implementation"))
  }

  fun testParsedDependencyPromotions() {
    val libModule = project.findModuleByName("mainModule") as PsAndroidModule
    run {
      val lib1 = libModule.dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
      val lib2 = libModule.dependencies.findLibraryDependency("com.example.libs:lib2:1.0")
      val lib3 = libModule.dependencies.findLibraryDependency("com.example.jlib:lib3:1.0")
      val lib4 = libModule.dependencies.findLibraryDependency("com.example.jlib:lib4:1.0")
      assertThat(lib1.testDeclared(), hasItems(true))
      assertThat(lib2, nullValue())
      assertThat(lib3, nullValue())
      assertThat(lib4, nullValue())
    }
    run {
      val artifact = libModule.findVariant("paidRelease")!!.findArtifact(ARTIFACT_MAIN)
      val dependencies = artifact!!.dependencies
      val lib1 = dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
      val lib2 = dependencies.findLibraryDependency("com.example.libs:lib2:1.0")
      val lib3 = dependencies.findLibraryDependency("com.example.jlib:lib3:1.0")
      val lib4 = dependencies.findLibraryDependency("com.example.jlib:lib4:1.0")
      assertThat(lib1.testDeclared(), hasItems(true))
      assertThat(lib2.testDeclared(), hasItems(false))
      assertThat(lib3.testDeclared(), hasItems(false))
      assertThat(lib4.testDeclared(), hasItems(false))
      assertThat(lib1.testHasPromotedVersion(), hasItems(true))
      assertThat(lib2.testHasPromotedVersion(), hasItems(false))
      assertThat(lib3.testHasPromotedVersion(), hasItems(false))
      assertThat(lib4.testHasPromotedVersion(), hasItems(false))
    }
    run {
      val artifact = libModule.findVariant("paidDebug")!!.findArtifact(ARTIFACT_MAIN)
      val dependencies = artifact!!.dependencies
      val lib1 = dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
      val lib2 = dependencies.findLibraryDependency("com.example.libs:lib2:1.0")
      val lib3 = dependencies.findLibraryDependency("com.example.jlib:lib3:1.0")
      val lib4 = dependencies.findLibraryDependency("com.example.jlib:lib4:1.0")
      assertThat(lib1, notNullValue())
      assertThat(lib2, notNullValue())
      assertThat(lib3, notNullValue())
      assertThat(lib4, notNullValue())
      assertThat(lib1.testDeclared(), hasItems(true))
      assertThat(lib2.testDeclared(), hasItems(false))
      assertThat(lib3.testDeclared(), hasItems(false))
      assertThat(lib4.testDeclared(), hasItems(false))
      assertThat(lib1.testHasPromotedVersion(), hasItems(false))
      assertThat(lib2.testHasPromotedVersion(), hasItems(false))
      assertThat(lib3.testHasPromotedVersion(), hasItems(false))
      assertThat(lib4.testHasPromotedVersion(), hasItems(false))
    }
  }

  fun testRemoveLibraryDependency() {
    var module = project.findModuleByName("mainModule") as PsAndroidModule
    var jModule = project.findModuleByName("jModuleK") as PsJavaModule
    val lib10 = module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
    val lib3 = jModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1")
    assertThat(lib10, notNullValue())
    assertThat(lib3, notNullValue())
    val numberOfMatchingDependenciesInModule = 2
    val numberOfMatchingDependenciesInJModule = 1
    assertThat(lib10!!.size, equalTo(numberOfMatchingDependenciesInModule))
    assertThat(lib3!!.size, equalTo(numberOfMatchingDependenciesInJModule))
    var notifications = 0
    module.addDependencyChangedListener(testRootDisposable) { if (it is PsModule.DependencyRemovedEvent) notifications++ }
    lib10.forEach {
      module.removeDependency(it)
    }
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
    assertThat(notifications, equalTo(numberOfMatchingDependenciesInModule))

    notifications = 0
    jModule.addDependencyChangedListener(testRootDisposable) { if (it is PsModule.DependencyRemovedEvent) notifications++ }
    lib3.forEach {
      jModule.removeDependency(it)
    }
    assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1"), nullValue())
    assertThat(notifications, equalTo(numberOfMatchingDependenciesInJModule))

    run {
      val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(ARTIFACT_MAIN)?.dependencies
      assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
      assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib2:1.0"), notNullValue())
    }

    run {
      val resolvedDependencies = jModule.resolvedDependencies
      assertThat(resolvedDependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1"), notNullValue())
    }

    project.applyChanges()
    requestSyncAndWait()
    reparse()

    module = project.findModuleByName("mainModule") as PsAndroidModule
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib2:1.0"), nullValue())

    jModule = project.findModuleByName("jModuleK") as PsJavaModule
    assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1"), nullValue())

    run {
      val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(ARTIFACT_MAIN)?.dependencies
      assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
      assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib2:1.0"), nullValue())
    }

    run {
      val resolvedDependencies = jModule.resolvedDependencies
      assertThat(resolvedDependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1"), nullValue())
    }
  }

  fun testAddLibraryDependency() {
    var module = project.findModuleByName("moduleA") as PsAndroidModule
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
    module.addLibraryDependency("com.example.libs:lib1:1.0", listOf("implementation"))
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())

    module.addLibraryDependency("com.example.libs:lib2:1.0", listOf("implementation"))
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib2:1.0"), notNullValue())

    var jModule = project.findModuleByName("jModuleM") as PsJavaModule
    assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib4:1.0"), nullValue())

    jModule.addLibraryDependency("com.example.jlib:lib4:1.0", listOf("implementation"))
    assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib4:1.0"), notNullValue())

    run {
      val resolvedDependencies = module.findVariant("release")?.findArtifact(ARTIFACT_MAIN)?.dependencies
      assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
      assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib2:1.0"), nullValue())
    }

    run {
      val resolvedDependencies = jModule.resolvedDependencies
      assertThat(resolvedDependencies.findLibraryDependency("com.example.jlib:lib4:1.0"), nullValue())
    }

    project.applyChanges()
    requestSyncAndWait()
    reparse()

    module = project.findModuleByName("moduleA") as PsAndroidModule
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib2:1.0"), notNullValue())

    jModule = project.findModuleByName("jModuleM") as PsJavaModule
    assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib4:1.0"), notNullValue())

    run {
      val resolvedDependencies = module.findVariant("release")?.findArtifact(ARTIFACT_MAIN)?.dependencies
      assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
      assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib2:1.0"), notNullValue())
    }

    run {
      val resolvedDependencies = jModule.resolvedDependencies
      assertThat(resolvedDependencies.findLibraryDependency("com.example.jlib:lib4:1.0"), notNullValue())
    }
  }

  fun testEditLibraryDependencyVersion() {
    var module = project.findModuleByName("mainModule") as PsAndroidModule
    var jModule = project.findModuleByName("jModuleM") as PsJavaModule

    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0", "implementation"), notNullValue())
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1", "implementation"), nullValue())

    assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1"), notNullValue())
    assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib3:1.0"), nullValue())

    run {
      val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(ARTIFACT_MAIN)?.dependencies
      val lib1 = resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0")
      assertThat(lib1, notNullValue())
      assertThat(lib1?.first()?.spec?.version, equalTo("1.0"))
    }

    run {
      val resolvedDependencies = jModule.resolvedDependencies
      val lib3 = resolvedDependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1")
      assertThat(lib3, notNullValue())
      assertThat(lib3?.first()?.spec?.version, equalTo("0.9.1"))
    }

    module.setLibraryDependencyVersion(PsArtifactDependencySpec.create("com.example.libs:lib1:1.0")!!, "implementation", "0.9.1")
    jModule.setLibraryDependencyVersion(PsArtifactDependencySpec.create("com.example.jlib:lib3:0.9.1")!!, "implementation", "1.0")

    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0", "implementation"), nullValue())
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1", "implementation"), notNullValue())

    assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1"), nullValue())
    assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib3:1.0"), notNullValue())

    run {
      val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(ARTIFACT_MAIN)?.dependencies
      val lib1 = resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0")
      assertThat(lib1, notNullValue())
      assertThat(lib1?.first()?.spec?.version, equalTo("1.0"))
    }

    run {
      val resolvedDependencies = jModule.resolvedDependencies
      val lib3 = resolvedDependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1")
      assertThat(lib3, notNullValue())
      assertThat(lib3?.first()?.spec?.version, equalTo("0.9.1"))
    }

    project.applyChanges()
    requestSyncAndWait()
    reparse()

    module = project.findModuleByName("mainModule") as PsAndroidModule
    jModule = project.findModuleByName("jModuleM") as PsJavaModule

    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0", "implementation"), nullValue())
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1", "implementation"), notNullValue())

    assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1"), nullValue())
    assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib3:1.0"), notNullValue())

    run {
      val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(ARTIFACT_MAIN)?.dependencies
      val lib1 = resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:0.9.1")
      assertThat(lib1, notNullValue())
      assertThat(lib1?.first()?.spec?.version, equalTo("0.9.1"))
    }

    run {
      val resolvedDependencies = jModule.resolvedDependencies
      val lib3 = resolvedDependencies.findLibraryDependency("com.example.jlib:lib3:1.0")
      assertThat(lib3, notNullValue())
      assertThat(lib3?.first()?.spec?.version, equalTo("1.0"))
    }

  }

  fun testEditLibraryDependencyVersionProperty() {
    var module = project.findModuleByName("mainModule") as PsAndroidModule
    var jModule = project.findModuleByName("jModuleK") as PsJavaModule

    val declaredDependency = module.dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1", "releaseImplementation")
    val jDeclaredDependency = jModule.dependencies.findLibraryDependency("com.example.jlib:lib4:0.6", "implementation")

    assertThat(declaredDependency, notNullValue())
    assertThat(declaredDependency?.size, equalTo(1))
    assertThat(
      module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0", "releaseImplementation"), nullValue())

    assertThat(jDeclaredDependency, notNullValue())
    assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib4:0.9.1"), nullValue())

    run {
      val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(ARTIFACT_MAIN)?.dependencies
      val lib1 = resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0")
      assertThat(lib1.testHasPromotedVersion(), equalTo(listOf(true)))
      assertThat(lib1, notNullValue())
      assertThat(lib1?.first()?.spec?.version, equalTo("1.0"))
    }

    run {
      val resolvedDependencies = jModule.resolvedDependencies
      val lib4 = resolvedDependencies.findLibraryDependency("com.example.jlib:lib4:0.9.1")
      // TODO(b/110778597): Implement library version promotion analysis for Java modules.
      // assertThat(lib4.testHasPromotedVersion(), equalTo(listOf(true)))
      assertThat(lib4, notNullValue())
      assertThat(lib4?.first()?.spec?.version, equalTo("0.9.1"))
    }

    declaredDependency!![0].version = "1.0".asParsed()
    jDeclaredDependency!![0].version = "0.9.1".asParsed()

    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0", "releaseImplementation"), notNullValue())
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1", "releaseImplementation"), nullValue())

    assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib4:0.9.1", "implementation"), notNullValue())
    assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib4:0.6", "implementation"), nullValue())

    run {
      val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(ARTIFACT_MAIN)?.dependencies
      val lib1 = resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0")
      assertThat(lib1.testHasPromotedVersion(), equalTo(listOf(false)))
      assertThat(lib1, notNullValue())
      assertThat(lib1?.first()?.spec?.version, equalTo("1.0"))
    }

    run {
      val resolvedDependencies = jModule.resolvedDependencies
      val lib4 = resolvedDependencies.findLibraryDependency("com.example.jlib:lib4:0.9.1")
      // TODO(b/110778597): Implement library version promotion analysis for Java modules.
      // assertThat(lib4.testHasPromotedVersion(), equalTo(listOf(false)))
      assertThat(lib4, notNullValue())
      assertThat(lib4?.first()?.spec?.version, equalTo("0.9.1"))
    }

    project.applyChanges()
    requestSyncAndWait()
    reparse()

    module = project.findModuleByName("mainModule") as PsAndroidModule
    jModule = project.findModuleByName("jModuleK") as PsJavaModule

    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1", "releaseImplementation"), nullValue())
    assertThat(
      module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0", "releaseImplementation"), notNullValue())

    assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib4:0.9.1", "implementation"), notNullValue())
    assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib4:0.6", "implementation"), nullValue())

    run {
      val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(ARTIFACT_MAIN)?.dependencies
      val lib1 = resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0")
      // TODO(b/110778597): Implement library version promotion analysis for Java modules.
      // assertThat(lib1.testHasPromotedVersion(), equalTo(listOf(false)))
      assertThat(lib1, notNullValue())
      assertThat(lib1?.first()?.spec?.version, equalTo("1.0"))
    }

    run {
      val resolvedDependencies = jModule.resolvedDependencies
      val lib4 = resolvedDependencies.findLibraryDependency("com.example.jlib:lib4:0.9.1")
      assertThat(lib4.testHasPromotedVersion(), equalTo(listOf(false)))
      assertThat(lib4, notNullValue())
      assertThat(lib4?.first()?.spec?.version, equalTo("0.9.1"))
    }
  }

  fun testAddModuleDependency() {
    var module = project.findModuleByName("mainModule") as PsAndroidModule
    assertThat(module.dependencies.findModuleDependency(":moduleA"), nullValue())
    module.addModuleDependency(":moduleA", listOf("implementation"))
    assertThat(module.dependencies.findModuleDependency(":moduleA"), notNullValue())

    module.addModuleDependency(":moduleB", listOf("implementation"))
    assertThat(module.dependencies.findModuleDependency(":moduleA"), notNullValue())
    assertThat(module.dependencies.findModuleDependency(":moduleB"), notNullValue())

    run {
      val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(ARTIFACT_MAIN)?.dependencies
      assertThat(resolvedDependencies?.findModuleDependency(":moduleA"), nullValue())
      assertThat(resolvedDependencies?.findModuleDependency(":moduleB"), nullValue())
    }

    project.applyChanges()
    requestSyncAndWait()
    reparse()

    module = project.findModuleByName("mainModule") as PsAndroidModule
    assertThat(module.dependencies.findModuleDependency(":moduleA"), notNullValue())
    assertThat(module.dependencies.findModuleDependency(":moduleB"), notNullValue())

    run {
      val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(ARTIFACT_MAIN)?.dependencies
      assertThat(resolvedDependencies?.findModuleDependency(":moduleA"), notNullValue())
      assertThat(resolvedDependencies?.findModuleDependency(":moduleB"), notNullValue())
    }
  }

  fun testAddJavaModuleDependency() {
    var module = project.findModuleByName("mainModule") as PsAndroidModule
    assertThat(module.dependencies.findModuleDependency(":jModuleK"), nullValue())
    module.addModuleDependency(":jModuleK", listOf("implementation"))
    assertThat(module.dependencies.findModuleDependency(":jModuleK"), notNullValue())

    run {
      val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(ARTIFACT_MAIN)?.dependencies
      assertThat(resolvedDependencies?.findModuleDependency(":jModuleK"), nullValue())
    }

    project.applyChanges()
    requestSyncAndWait()
    reparse()

    module = project.findModuleByName("mainModule") as PsAndroidModule
    assertThat(module.dependencies.findModuleDependency(":jModuleK"), notNullValue())

    run {
      val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(ARTIFACT_MAIN)?.dependencies
      assertThat(resolvedDependencies?.findModuleDependency(":jModuleK"), notNullValue())
    }
  }

  fun testAddJavaModuleDependencyToJavaModule() {
    var module = project.findModuleByName("jModuleK") as PsJavaModule
    assertThat(module.dependencies.findModuleDependency(":jModuleM"), nullValue())
    module.addModuleDependency(":jModuleM", listOf("implementation"))
    assertThat(module.dependencies.findModuleDependency(":jModuleM"), notNullValue())

    project.applyChanges()
    requestSyncAndWait()
    reparse()

    module = project.findModuleByName("jModuleK") as PsJavaModule
    assertThat(module.dependencies.findModuleDependency(":jModuleM"), notNullValue())
  }

  data class TestReverseDependency(val from: String, val to: String, val resolved: String, val kind: String, val isPromoted: Boolean)

  private fun ReverseDependency.toTest() =
    TestReverseDependency(
      when (this) {
        is ReverseDependency.Declared -> dependency.configurationName
        is ReverseDependency.Transitive -> requestingResolvedDependency.spec.toString()
      },
      spec.toString(), resolvedSpec.toString(), javaClass.simpleName, isPromoted)

  fun testReverseDependencies() {
    val module = project.findModuleByName("mainModule") as PsAndroidModule
    run {
      val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(ARTIFACT_MAIN)?.dependencies
      val lib3 = resolvedDependencies?.findLibraryDependencies("com.example.jlib", "lib3")?.singleOrNull()?.getReverseDependencies()
      val lib2 = resolvedDependencies?.findLibraryDependencies("com.example.libs", "lib2")?.singleOrNull()?.getReverseDependencies()
      val lib1 = resolvedDependencies?.findLibraryDependencies("com.example.libs", "lib1")?.singleOrNull()?.getReverseDependencies()

      assertThat(
        lib3?.map { it.toTest() }?.toSet(),
        equalTo(
          setOf(
            TestReverseDependency(
              from = "com.example.libs:lib2:1.0", to = "com.example.jlib:lib3:1.0", resolved = "com.example.jlib:lib3:1.0",
              kind = "Transitive", isPromoted = false),
            TestReverseDependency(
              from = "freeImplementation", to = "com.example.jlib:lib3:0.6", resolved = "com.example.jlib:lib3:1.0",
              kind = "Declared", isPromoted = true))))

      assertThat(
        lib2?.map { it.toTest() }?.toSet(),
        equalTo(
          setOf(
            TestReverseDependency(
              from = "com.example.libs:lib1:1.0", to = "com.example.libs:lib2:1.0", resolved = "com.example.libs:lib2:1.0",
              kind = "Transitive", isPromoted = false))))

      assertThat(
        lib1?.map { it.toTest() }?.toSet(),
        equalTo(
          setOf(
            TestReverseDependency(
              from = "implementation", to = "com.example.libs:lib1:1.0", resolved = "com.example.libs:lib1:1.0",
              kind = "Declared", isPromoted = false),
            TestReverseDependency(
              from = "releaseImplementation", to = "com.example.libs:lib1:0.9.1", resolved = "com.example.libs:lib1:1.0",
              kind = "Declared", isPromoted = true))))
    }
  }
}

private fun <T> PsDeclaredDependencyCollection<*, T, *>.findLibraryDependency(
  compactNotation: String,
  configuration: String? = null
): List<T>?
  where T : PsDeclaredDependency,
        T : PsLibraryDependency,
        T : PsDependency =
  PsArtifactDependencySpec.create(compactNotation)?.let { spec ->
    findLibraryDependencies(
      spec.group,
      spec.name
    )
      .filter { it.spec.version == spec.version && it.configurationName == (configuration ?: it.configurationName) }
      .let { if (it.isEmpty()) null else it }
  }

private fun <T> PsResolvedDependencyCollection<*, *, T, *>.findLibraryDependency(compactNotation: String): List<T>?
  where T : PsResolvedDependency,
        T : PsLibraryDependency,
        T : PsDependency =
  PsArtifactDependencySpec.create(compactNotation)?.let { spec ->
    findLibraryDependencies(
      spec.group,
      spec.name
    )
      .filter { it.spec.version == spec.version }
      .let { if (it.isEmpty()) null else it }
  }

private fun List<PsResolvedDependency>?.testMatchingScopes(): List<String> =
  orEmpty().map { it.getParsedModels().joinToString(":") { it.configurationName() } }

private fun List<PsDeclaredDependency>?.testDeclaredScopes(): List<String> = orEmpty().map { it.parsedModel.configurationName() }

private fun List<PsModel>?.testDeclared(): List<Boolean> = orEmpty().map { it.isDeclared }
private fun List<PsResolvedLibraryDependency>?.testHasPromotedVersion(): List<Boolean> = orEmpty().map { it.hasPromotedVersion() }
