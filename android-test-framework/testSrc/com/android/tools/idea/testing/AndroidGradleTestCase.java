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
package com.android.tools.idea.testing;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_BUILD_GRADLE_KTS;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS;
import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.testutils.TestUtils.getSdk;
import static com.android.testutils.TestUtils.getWorkspaceRoot;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.prepareGradleProject;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueData;
import com.android.tools.idea.gradle.util.GradleBuildOutputUtil;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.testing.AndroidGradleTests.SyncIssuesPresentError;
import com.android.tools.idea.util.AndroidTestPaths;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.TestApplicationManager;
import com.intellij.testFramework.ThreadTracker;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.Consumer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.jetbrains.android.AndroidTempDirTestFixture;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;
import org.jetbrains.annotations.SystemIndependent;

/**
 * Base class for unit tests that operate on Gradle projects
 * <p>
 * TODO: After converting all tests over, check to see if there are any methods we can delete or
 * reduce visibility on.
 * <p>
 * NOTE: If you are writing a new test, consider using JUnit4 with {@link AndroidGradleProjectRule}
 * instead. This allows you to use features introduced in JUnit4 (such as parameterization) while
 * also providing a more compositional approach - instead of your test class inheriting dozens and
 * dozens of methods you might not be familiar with, those methods will be constrained to the rule.
 */
public abstract class AndroidGradleTestCase extends AndroidTestBase implements GradleIntegrationTest {
  private static final Logger LOG = Logger.getInstance(AndroidGradleTestCase.class);

  protected AndroidFacet myAndroidFacet;

  public AndroidGradleTestCase() {
  }

  protected boolean createDefaultProject() {
    return true;
  }

  @NotNull
  protected File getProjectFolderPath() {
    String projectFolderPath = getProject().getBasePath();
    assertNotNull(projectFolderPath);
    return new File(projectFolderPath);
  }

  @NotNull
  protected File getBuildFilePath(@NotNull String moduleName) {
    File buildFilePath = new File(getProjectFolderPath(), join(moduleName, FN_BUILD_GRADLE));
    if (!buildFilePath.isFile()) {
      buildFilePath = new File(getProjectFolderPath(), join(moduleName, FN_BUILD_GRADLE_KTS));
    }
    assertAbout(file()).that(buildFilePath).isFile();
    return buildFilePath;
  }

  @NotNull
  protected File getSettingsFilePath() {
    File settingsFilePath = new File(getProjectFolderPath(), FN_SETTINGS_GRADLE);
    if (!settingsFilePath.isFile()) {
      settingsFilePath = new File(getProjectFolderPath(), FN_SETTINGS_GRADLE_KTS);
    }
    assertAbout(file()).that(settingsFilePath).isFile();
    return settingsFilePath;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    StudioFlags.KOTLIN_DSL_PARSING.override(true);
    TestApplicationManager.getInstance();
    ensureSdkManagerAvailable();
    // Layoutlib rendering thread will be shutdown when the app is closed so do not report it as a leak
    ThreadTracker.longRunningThreadCreated(ApplicationManager.getApplication(), "Layoutlib");

    if (createDefaultProject()) {
      setUpFixture();

      // To ensure that application IDs are loaded from the listing file as needed, we must register the required listeners.
      // This is normally done within an AndroidStartupActivity but these are not run in tests.
      // TODO(b/159600848)
      GradleBuildOutputUtil.emulateStartupActivityForTest(getProject());
    }
  }

  public void setUpFixture() throws Exception {
    AndroidTempDirTestFixture tempDirFixture = new AndroidTempDirTestFixture(getName());
    TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
      IdeaTestFixtureFactory.getFixtureFactory()
        .createFixtureBuilder(getName(), tempDirFixture.getProjectDir().getParentFile().toPath(), true);
    IdeaProjectTestFixture projectFixture = projectBuilder.getFixture();
    setUpFixture(projectFixture);
  }

  public void setUpFixture(IdeaProjectTestFixture projectFixture) throws Exception {
    JavaCodeInsightTestFixture fixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectFixture);
    fixture.setUp();
    fixture.setTestDataPath(AndroidTestPaths.adtSources().resolve(getTestDataDirectoryAdtIdeaRelativePath()).toAbsolutePath().toString());
    ensureSdkManagerAvailable();

    Project project = fixture.getProject();
    FileUtil.ensureExists(new File(toSystemDependentName(project.getBasePath())));
    LocalFileSystem.getInstance().refreshAndFindFileByPath(project.getBasePath());
    AndroidGradleTests.setUpSdks(fixture, getSdk());
    myFixture = fixture;
  }

  public void tearDownFixture() {
    myAndroidFacet = null;
    if (myFixture != null) {
      try {
        myFixture.tearDown();
      }
      catch (Throwable e) {
        LOG.warn("Failed to tear down " + myFixture.getClass().getSimpleName(), e);
      }
      myFixture = null;
    }
  }

  @Override
  protected void tearDown() throws Exception {
    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    StudioFlags.KOTLIN_DSL_PARSING.clearOverride();
    try {
      TestDialogManager.setTestDialog(TestDialog.DEFAULT);
      tearDownFixture();

      Project[] openProjects = projectManager.getOpenProjects();
      if (openProjects.length > 0) {
        ProjectManagerEx.getInstanceEx().forceCloseProject(openProjects[0]);
      }
      myAndroidFacet = null;
    }
    finally {
      try {
        assertEquals(0, projectManager.getOpenProjects().length);
      }
      catch (Throwable e) {
        addSuppressedException(e);
      }
      finally {
        //noinspection ThrowFromFinallyBlock
        super.tearDown();
      }
    }
  }

  @NotNull
  protected String loadProjectAndExpectSyncError(@NotNull String relativePath) throws Exception {
    return loadProjectAndExpectSyncError(relativePath, request -> {
    });
  }

  protected String loadProjectAndExpectSyncError(@NotNull String relativePath,
                                                 @NotNull Consumer<GradleSyncInvoker.Request> requestConfigurator) throws Exception {
    prepareProjectForImport(relativePath);
    return requestSyncAndGetExpectedFailure(requestConfigurator);
  }

  protected void loadSimpleApplication() throws Exception {
    loadProject(SIMPLE_APPLICATION);
  }

  protected final void loadProject(@NotNull String relativePath) throws Exception {
    loadProject(relativePath, null, null, null, null);
  }

  protected final void loadProject(@NotNull String relativePath,
                                   @Nullable String chosenModuleName) throws Exception {
    loadProject(relativePath, chosenModuleName, null, null, null);
  }

  protected final void loadProject(@NotNull String relativePath, @Nullable AndroidGradleTests.SyncIssueFilter issueFilter) throws Exception {
    loadProject(relativePath, null, null, null, issueFilter);
  }

  protected final void loadProject(@NotNull String relativePath,
                                   @Nullable String chosenModuleName,
                                   @Nullable String gradleVersion,
                                   @Nullable String gradlePluginVersion
                                   ) throws Exception {
    loadProject(relativePath, chosenModuleName, gradleVersion, gradlePluginVersion, null);
  }
  protected final void loadProject(@NotNull String relativePath,
                                   @Nullable String chosenModuleName,
                                   @Nullable String gradleVersion,
                                   @Nullable String gradlePluginVersion,
                                   @Nullable AndroidGradleTests.SyncIssueFilter issueFilter) throws Exception {
    prepareProjectForImport(relativePath, gradleVersion, gradlePluginVersion);
    importProject(issueFilter);

    prepareProjectForTest(getProject(), chosenModuleName);
  }

  /**
   * @return a collection of absolute paths to additional local repositories required by the test.
   */
  @Override
  public Collection<File> getAdditionalRepos() {
    return ImmutableList.of();
  }

  protected void prepareProjectForTest(Project project, @Nullable String chosenModuleName) {
    AndroidProjectInfo androidProjectInfo = AndroidProjectInfo.getInstance(project);
    assertTrue(androidProjectInfo.requiresAndroidModel());
    assertFalse(androidProjectInfo.isLegacyIdeaAndroidProject());

    Module[] modules = ModuleManager.getInstance(project).getModules();

    myAndroidFacet = AndroidGradleTests.findAndroidFacetForTests(project, modules, chosenModuleName);
  }

  protected void patchPreparedProject(@NotNull File projectRoot,
                                      @Nullable String gradleVersion,
                                      @Nullable String gradlePluginVersion,
                                      File... localRepos) throws IOException {
    AndroidGradleTests.defaultPatchPreparedProject(projectRoot, gradleVersion, gradlePluginVersion, localRepos);
  }

  @NotNull
  protected File prepareProjectForImport(@NotNull @SystemIndependent String relativePath) throws IOException {
    return prepareProjectForImport(relativePath, null, null);
  }

  @NotNull
  protected File prepareProjectForImport(@NotNull @SystemIndependent String relativePath, @NotNull File targetPath) throws IOException {
    return prepareProjectForImport(relativePath, targetPath, null, null);
  }

  @NotNull
  protected File prepareProjectForImport(@NotNull @SystemIndependent String relativePath, @NotNull File targetPath,
                                         @Nullable String gradleVersion, @Nullable String gradlePluginVersion) throws IOException {
    File projectSourceRoot = resolveTestDataPath(relativePath);

    prepareGradleProject(
      projectSourceRoot,
      targetPath,
      file -> patchPreparedProject(file, gradleVersion, gradlePluginVersion, getAdditionalRepos().toArray(new File[0])));
    return targetPath;
  }

  @NotNull
  protected File prepareProjectForImport(@NotNull @SystemIndependent String relativePath, @Nullable String gradleVersion,
                                         @Nullable String gradlePluginVersion) throws IOException {
    File projectRoot = new File(toSystemDependentName(getProject().getBasePath()));
    return prepareProjectForImport(relativePath, projectRoot, gradleVersion, gradlePluginVersion);
  }

  @NotNull
  @Override
  @SystemIndependent
  public String getTestDataDirectoryAdtIdeaRelativePath() {
    return "android/testData";
  }

  @NotNull
  @Override
  public File resolveTestDataPath(@NotNull @SystemIndependent String relativePath) {
    return new File(myFixture.getTestDataPath(), toSystemDependentName(relativePath));
  }

  protected void generateSources() throws InterruptedException {
    GradleInvocationResult result = invokeGradle(getProject(), GradleBuildInvoker::generateSources);
    assertTrue("Generating sources failed.", result.isBuildSuccessful());
    refreshProjectFiles();
  }

  protected static GradleInvocationResult invokeGradleTasks(@NotNull Project project, @NotNull String... tasks)
    throws InterruptedException {
    assertThat(tasks).named("Gradle tasks").isNotEmpty();
    File projectDir = getBaseDirPath(project);
    // Tests should not need to access the network
    return invokeGradle(project, gradleInvoker ->
      gradleInvoker.executeTasks(projectDir, Arrays.asList(tasks), Collections.singletonList("--offline")));
  }

  @NotNull
  protected static GradleInvocationResult invokeGradle(@NotNull Project project, @NotNull Consumer<GradleBuildInvoker> gradleInvocationTask)
    throws InterruptedException {
    Ref<GradleInvocationResult> resultRef = new Ref<>();
    CountDownLatch latch = new CountDownLatch(1);
    GradleBuildInvoker gradleBuildInvoker = GradleBuildInvoker.getInstance(project);

    GradleBuildInvoker.AfterGradleInvocationTask task = result -> {
      resultRef.set(result);
      latch.countDown();
    };

    gradleBuildInvoker.add(task);

    try {
      gradleInvocationTask.consume(gradleBuildInvoker);
    }
    finally {
      gradleBuildInvoker.remove(task);
    }

    latch.await(5, MINUTES);
    GradleInvocationResult result = resultRef.get();
    assert result != null;
    return result;
  }

  protected static void createGradleWrapper(@NotNull File projectRoot) throws IOException {
    AndroidGradleTests.createGradleWrapper(projectRoot, GRADLE_LATEST_VERSION);
  }

  protected void importProject() throws Exception {
    importProject(null);
  }

  protected void importProject(@Nullable AndroidGradleTests.SyncIssueFilter issueFilter) throws Exception {
    Project project = getProject();
    AndroidGradleTests.importProject(project, GradleSyncInvoker.Request.testRequest(), issueFilter);
  }

  @NotNull
  protected AndroidModuleModel getModel() {
    AndroidModuleModel model = AndroidModuleModel.get(myAndroidFacet);
    assert model != null;
    return model;
  }

  @NotNull
  protected String getTextForFile(@NotNull String relativePath) {
    Project project = getProject();
    VirtualFile file = VfsUtil.findFile(Paths.get(project.getBasePath(), relativePath), false);
    if (file != null) {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile != null) {
        return psiFile.getText();
      }
    }

    return "";
  }

  @NotNull
  protected Module getModule(@NotNull String qualifiedModuleName) {
    return TestModuleUtil.findModule(getProject(), qualifiedModuleName);
  }

  protected boolean hasModule(@NotNull String qualifiedModuleName) {
    return TestModuleUtil.hasModule(getProject(), qualifiedModuleName);
  }

  protected void requestSyncAndWait(@NotNull GradleSyncInvoker.Request request) throws Exception {
    TestGradleSyncListener syncListener = requestSync(request);
    AndroidGradleTests.checkSyncStatus(getProject(), syncListener, null);
  }

  protected void requestSyncAndWait() throws SyncIssuesPresentError, Exception {
    AndroidGradleTests.SyncIssueFilter issueFilter = null;
    requestSyncAndWait(issueFilter);
  }

  protected void requestSyncAndWait(@Nullable AndroidGradleTests.SyncIssueFilter issueFilter) throws SyncIssuesPresentError, Exception {
    TestGradleSyncListener syncListener = requestSync(GradleSyncInvoker.Request.testRequest());
    AndroidGradleTests.checkSyncStatus(getProject(), syncListener, issueFilter);
  }

  @NotNull
  protected String requestSyncAndGetExpectedFailure() throws Exception {
    return requestSyncAndGetExpectedFailure(request -> { });
  }

  @NotNull
  protected List<SyncIssueData> requestSyncAndGetExpectedSyncIssueErrors() throws Exception {
    try {
      requestSyncAndWait(GradleSyncInvoker.Request.testRequest());
    } catch (SyncIssuesPresentError e) {
      return e.getIssues();
    }

    fail("Failure was expected, but no SyncIssue errors were present");
    return null; // Unreachable
  }

  @NotNull
  protected String requestSyncAndGetExpectedFailure(@NotNull Consumer<GradleSyncInvoker.Request> requestConfigurator) throws Exception {
    try {
      GradleSyncInvoker.Request request = GradleSyncInvoker.Request.testRequest();
      requestConfigurator.consume(request);
      requestSyncAndWait(request);
    } catch (AssertionError error) {
      return error.getMessage();
    }

    fail("Failure was expected, but import was successful");
    return null; // Unreachable
  }

  @NotNull
  protected TestGradleSyncListener requestSync(@NotNull GradleSyncInvoker.Request request) throws Exception {
    refreshProjectFiles();
    return AndroidGradleTests.syncProject(getProject(), request);
  }

  @Override
  @SystemDependent
  @NotNull
  public String getBaseTestPath() {
    return myFixture.getTempDirPath();
  }
}
