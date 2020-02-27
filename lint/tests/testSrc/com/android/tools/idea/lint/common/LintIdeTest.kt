/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * U…nless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.lint.common

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.CommentDetector
import com.google.common.base.Verify
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.google.common.truth.Truth.assertThat
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass.IntentionsInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.fixtures.ModuleFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.testFramework.fixtures.impl.JavaModuleFixtureBuilderImpl
import com.intellij.testFramework.fixtures.impl.ModuleFixtureImpl
import com.intellij.util.ThrowableRunnable
import junit.framework.TestCase
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

class LintIdeTest : UsefulTestCase() {
  private lateinit var myFixture: JavaCodeInsightTestFixture
  private lateinit var myModule: Module
  override fun setUp() {
    super.setUp()

    // Compute the workspace root before any IDE code starts messing with user.dir:
    TestUtils.getWorkspaceRoot()
    VfsRootAccess.allowRootAccess(testRootDisposable, FileUtil.toCanonicalPath(androidPluginHome))

    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val projectBuilder = factory.createFixtureBuilder(name)
    val fixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.fixture)
    myFixture = fixture
    fixture.setUp()
    fixture.testDataPath = testDataPath

    // Set up module and content roots
    factory.registerFixtureBuilder(LintModuleFixtureBuilder::class.java, LintModuleFixtureBuilderImpl::class.java)
    val moduleFixtureBuilder = projectBuilder.addModule(LintModuleFixtureBuilder::class.java)
    moduleFixtureBuilder.setModuleRoot(fixture.tempDirPath)
    moduleFixtureBuilder.addContentRoot(fixture.tempDirPath)
    File("${fixture.tempDirPath}/src/").mkdir()
    moduleFixtureBuilder.addSourceRoot("src")
    myModule = moduleFixtureBuilder.fixture!!.module
    AndroidLintInspectionBase.setRegisterDynamicToolsFromTests(false)
    fixture.allowTreeAccessForAllFiles()
  }

  override fun tearDown() {
    try {
      myFixture.tearDown()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  private val project: Project
    get() = myFixture.project

  fun testLintIdeClientReturnsModuleFromEditorResult() {
    val fileContent = """
      package p1.p2;
      public class WhySoSerious {}
    """.trimIndent()
    val vfPointer = myFixture.addFileToProject("src/p1/p2/WhySoSerious.java", fileContent).virtualFile

    val module = ModuleManager.getInstance(myFixture.project).modules[0]
    val lintClient = LintIdeClient(myFixture.project, LintEditorResult(module, vfPointer, fileContent, Sets.newHashSet()))

    assertThat(lintClient.module).isSameAs(module)
  }

  fun testUseValueOf() {
    doTestWithFix(AndroidLintUseValueOfInspection(),
                  "Replace with valueOf()", "/src/test/pkg/UseValueOf.java", "java")
  }

  fun testWrongQuote() {
    doTestWithFix(AndroidLintNotInterpolatedInspection(),
                  "Replace single quotes with double quotes", "build.gradle", "gradle")
  }

  fun testAddSuperCallJava() {
    addCallSuper()
    doTestWithFix(AndroidLintMissingSuperCallInspection(),
                  "Add super call", "/src/p1/p2/SuperTestJava.java", "java")
  }

  fun testAddSuperCall() {
    addCallSuper()
    doTestWithFix(AndroidLintMissingSuperCallInspection(),
                  "Add super call", "/src/p1/p2/SuperTest.kt", "kt")
  }

  fun testAddSuperCallExpression() {
    addCallSuper()
    doTestWithFix(AndroidLintMissingSuperCallInspection(),
                  "Add super call", "/src/p1/p2/SuperTest.kt", "kt")
  }

  fun testJavaCheckResultTest1() {
    addCheckResult()
    doTestWithFix(AndroidLintCheckResultInspection(),
                  "Call replace instead", "/src/p1/p2/JavaCheckResultTest1.java", "java")
  }

  fun testKotlinCheckResultTest1() {
    addCheckResult()
    doTestWithFix(AndroidLintCheckResultInspection(),
                  "Call replace instead", "/src/p1/p2/KotlinCheckResultTest1.kt", "kt")
  }

  fun testPropertyFiles() {
    doTestWithFix(AndroidLintPropertyEscapeInspection(),
                  "Escape", "local.properties", "properties")
  }

  fun testCallSuper() {
    addCallSuper()
    doTestWithFix(AndroidLintMissingSuperCallInspection(),
                  "Add super call", "src/p1/p2/CallSuperTest.java", "java")
  }

  fun testCallSuper2() {
    addCallSuper()
    doTestWithFix(AndroidLintMissingSuperCallInspection(),
                  "Add super call", "src/p1/p2/FooImpl.java", "java")
  }

  fun testStopShip() {
    CommentDetector.STOP_SHIP.setEnabledByDefault(true)
    doTestWithFix(AndroidLintStopShipInspection(), "Remove STOPSHIP", "/src/test/pkg/StopShip.java",
                  "java")
  }

  fun testDisabledTestsEnabledOnTheFly() {
    // If this changes test no longer applies; pick different disabled issue
    assertThat(CommentDetector.STOP_SHIP.isEnabledByDefault()).isFalse()
    myFixture.copyFileToProject("$globalTestDir/Stopship.java", "src/p1/p2/Stopship.java")
    doGlobalInspectionTest(AndroidLintStopShipInspection())
  }

  fun testGradleWindows() {
    doTestWithFix(AndroidLintGradlePathInspection(),
                  "Replace with my/libs/http.jar", "build.gradle", "gradle")
  }

  // Global (batch) inspections

  fun testSuppressingInJava() {
    myFixture.copyFileToProject("$globalTestDir/MyActivity.java", "src/p1/p2/MyActivity.java")
    doGlobalInspectionTest(AndroidLintUseValueOfInspection())
  }

  fun testLintInJavaFile() {
    myFixture.copyFileToProject("$globalTestDir/MyActivity.java", "src/p1/p2/MyActivity.java")
    doGlobalInspectionTest(AndroidLintUseValueOfInspection())
  }

  fun testLintNonAndroid() {
    // Make sure that we include the lint implementation checks themselves outside of Android contexts
    val issues = LintIdeIssueRegistry()
    val issue = issues.getIssue("LintImplDollarEscapes")!!
    val support = object : LintIdeSupport() { }
    assertEquals(support.getPlatforms(), issue.platforms)
  }

  private fun doGlobalInspectionTest(inspection: AndroidLintInspectionBase) {
    myFixture.enableInspections(inspection)
    doGlobalInspectionTest(inspection, globalTestDir, AnalysisScope(myModule))
  }

  private fun doGlobalInspectionTest(
    inspection: GlobalInspectionTool, globalTestDir: String, scope: AnalysisScope) {
    doGlobalInspectionTest(GlobalInspectionToolWrapper(inspection), globalTestDir, scope)
  }

  private fun doGlobalInspectionTest(
    wrapper: GlobalInspectionToolWrapper, globalTestDir: String, scope: AnalysisScope) {
    myFixture.enableInspections(wrapper.tool)
    scope.invalidate()
    val globalContext = createGlobalContextForTool(scope, project,
                                                   listOf<InspectionToolWrapper<*, *>>(wrapper))
    InspectionTestUtil.runTool(wrapper, scope, globalContext)
    InspectionTestUtil.compareToolResults(globalContext, wrapper, false, testDataPath + globalTestDir)
    globalContext.getPresentation(wrapper).problemElements
  }

  private val globalTestDir: String
    get() = BASE_PATH_GLOBAL + getTestName(true)

  private fun doTestWithFix(inspection: AndroidLintInspectionBase,
                            message: String,
                            copyTo: String,
                            extension: String) {
    val action = doTestHighlightingAndGetQuickfix(inspection, message, copyTo, extension)
    doTestWithAction(extension, action!!)
  }

  private fun doTestWithAction(extension: String, action: IntentionAction) {
    TestCase.assertTrue(action.isAvailable(myFixture.project, myFixture.editor, myFixture.file))
    WriteCommandAction.writeCommandAction(myFixture.project).run(
      ThrowableRunnable<Throwable?> {
        action.invoke(myFixture.project, myFixture.editor, myFixture.file)
      })
    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after." + extension)
  }

  @Throws(IOException::class)
  private fun doTestHighlightingAndGetQuickfix(inspection: AndroidLintInspectionBase,
                                               message: String,
                                               copyTo: String,
                                               extension: String): IntentionAction? {
    doTestHighlighting(inspection, copyTo, extension, skipCheck = false)
    return getIntentionAction(message)
  }

  private fun doTestHighlighting(inspection: AndroidLintInspectionBase, copyTo: String, extension: String, skipCheck: Boolean) {
    myFixture.enableInspections(inspection)
    val file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + "." + extension, copyTo)
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.doHighlighting()
    if (!skipCheck) {
      myFixture.checkHighlighting(true, false, false)
    }
  }

  private val availableFixes: List<IntentionAction>
    get() {
      val intentions = IntentionsInfo()
      ShowIntentionsPass.getActionsToShow(myFixture.editor, myFixture.file, intentions, -1)
      val actions: MutableList<IntentionAction> = Lists.newArrayList()
      for (descriptor in intentions.inspectionFixesToShow) {
        actions.add(descriptor.action)
      }
      return actions
    }

  private fun listAvailableFixes(): String {
    val intentions = IntentionsInfo()
    ShowIntentionsPass.getActionsToShow(myFixture.editor, myFixture.file, intentions, -1)
    val sb = StringBuilder()
    for (action in availableFixes) {
      sb.append(action.text).append("\n")
    }
    return sb.toString()
  }

  private fun getIntentionAction(message: String): IntentionAction? {
    for (intention in myFixture.availableIntentions) {
      if (message == intention.text) {
        return if (intention is IntentionActionDelegate) {
          (intention as IntentionActionDelegate).delegate
        }
        else {
          intention
        }
      }
    }
    return null
  }

  private fun addCallSuper() {
    myFixture.addFileToProject("/src/android/support/annotation/CallSuper.java", """
        package android.support.annotation;
        import static java.lang.annotation.ElementType.METHOD;
        import static java.lang.annotation.RetentionPolicy.CLASS;
        import java.lang.annotation.Documented;
        import java.lang.annotation.Retention;
        import java.lang.annotation.Target;
        @Documented
        @Retention(CLASS)
        @Target({METHOD})
        public @interface CallSuper {
        }""".trimIndent())
  }

  private fun addCheckResult() {
    myFixture.addFileToProject("/src/android/support/annotation/Keep.java", """
          package android.support.annotation;
          import static java.lang.annotation.ElementType.METHOD;
          import static java.lang.annotation.RetentionPolicy.CLASS;
          import java.lang.annotation.Documented;
          import java.lang.annotation.Retention;
          import java.lang.annotation.Target;
          @Documented
          @Retention(CLASS)
          @Target({METHOD})
          public @interface CheckResult {
              String suggest() default "";
          }""".trimIndent())
  }

  interface LintModuleFixtureBuilder<T : ModuleFixture?> : JavaModuleFixtureBuilder<T> {
    fun setModuleRoot(moduleRoot: String)
  }

  class LintModuleFixtureBuilderImpl(fixtureBuilder: TestFixtureBuilder<out IdeaProjectTestFixture>)
    : JavaModuleFixtureBuilderImpl<ModuleFixtureImpl>(fixtureBuilder), LintModuleFixtureBuilder<ModuleFixtureImpl> {
    private var myModuleRoot: File? = null
    override fun setModuleRoot(moduleRoot: String) {
      val file = File(moduleRoot)
      myModuleRoot = file
      if (!file.exists()) {
        Verify.verify(file.mkdirs())
      }
    }

    override fun createModule(): Module {
      myModuleRoot!!
      val project = myFixtureBuilder.fixture.project
      Verify.verifyNotNull(project)
      val moduleFilePath = myModuleRoot.toString() + "/app" + ModuleFileType.DOT_DEFAULT_EXTENSION
      return ModuleManager.getInstance(project).newModule(moduleFilePath, ModuleTypeId.JAVA_MODULE)
    }

    override fun instantiateFixture(): ModuleFixtureImpl {
      return ModuleFixtureImpl(this)
    }
  }

  companion object {
    private const val BASE_PATH = "/lint/"
    private const val BASE_PATH_GLOBAL = BASE_PATH + "global/"

    val testDataPath: String
      get() = "$androidPluginHome/../lint/tests/testData"

    // For now lint is co-located with the Android plugin
    private val androidPluginHome: String
      get() {
        val adtPath = Paths.get(PathManager.getHomePath(), "../adt/idea", "android").normalize()
        return if (Files.exists(adtPath))
          adtPath.toString()
        else
          PathManagerEx.findFileUnderCommunityHome("android/android").path
      }
  }
}