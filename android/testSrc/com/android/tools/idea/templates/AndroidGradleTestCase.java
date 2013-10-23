/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.templates;

import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkManager;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.wizard.NewModuleWizardState;
import com.android.tools.idea.wizard.NewProjectWizard;
import com.android.tools.idea.wizard.NewProjectWizardState;
import com.android.tools.idea.wizard.TemplateWizardState;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.inspections.lint.IntellijLintClient;
import org.jetbrains.android.inspections.lint.IntellijLintIssueRegistry;
import org.jetbrains.android.inspections.lint.IntellijLintRequest;
import org.jetbrains.android.inspections.lint.ProblemData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_API;

/** Base class for unit tests that operate on Gradle projects */
public abstract class AndroidGradleTestCase extends AndroidTestBase {
  private static SdkManager ourPreviousSdkManager;

  public AndroidGradleTestCase() {
  }

  protected boolean createDefaultProject() {
    return true;
  }

  /** Is the bundled (incomplete) SDK install adequate or do we need to find a valid install? */
  @Override
  protected boolean requireRecentSdk() {
    return true;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    if (createDefaultProject()) {
      final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
        IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
      myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
      myFixture.setUp();
    }

    ensureSdkManagerAvailable();
  }

  @Override
  protected void tearDown() throws Exception {
    if (myFixture != null) {
      myFixture.tearDown();
      myFixture = null;
    }

    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    Project[] openProjects = projectManager.getOpenProjects();
    if (openProjects.length > 0) {
      final Project project = openProjects[0];
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          Disposer.dispose(project);
          ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
          if (projectManager instanceof ProjectManagerImpl) {
            Collection<Project> projectsStillOpen = projectManager.closeTestProject(project);
            if (!projectsStillOpen.isEmpty()) {
              Project project = projectsStillOpen.iterator().next();
              projectsStillOpen.clear();
              throw new AssertionError("Test project is not disposed: " + project+";\n created in: " +
                                       PlatformTestCase.getCreationPlace(project));
            }
          }
        }
      });
    }

    super.tearDown();

    // In case other test cases rely on the builtin (incomplete) SDK, restore
    if (ourPreviousSdkManager != null) {
      AndroidSdkUtils.setSdkManager(ourPreviousSdkManager);
    }
  }

  @Override
  protected void ensureSdkManagerAvailable() {
    if (requireRecentSdk() && ourPreviousSdkManager == null) {
      ourPreviousSdkManager = AndroidSdkUtils.tryToChooseAndroidSdk();
    }
    super.ensureSdkManagerAvailable();
  }

  public void createProject(String activityName, boolean syncModel) throws Exception {
    final NewProjectWizardState projectWizardState = new NewProjectWizardState();

    configureProjectState(projectWizardState);
    TemplateWizardState activityWizardState = projectWizardState.getActivityTemplateState();
    configureActivityState(activityWizardState, activityName);

    createProject(projectWizardState, syncModel);
  }

  protected void configureActivityState(TemplateWizardState activityWizardState, String activityName) {
    TemplateManager manager = TemplateManager.getInstance();
    List<File> templates = manager.getTemplates(Template.CATEGORY_ACTIVITIES);
    File blankActivity = null;
    for (File t : templates) {
      if (t.getName().equals(activityName)) {
        blankActivity = t;
        break;
      }
    }
    assertNotNull(blankActivity);
    activityWizardState.setTemplateLocation(blankActivity);
    activityWizardState.convertApisToInt();
    assertNotNull(activityWizardState.getTemplate());
  }

  protected void configureProjectState(NewProjectWizardState projectWizardState) {
    final Project project = myFixture.getProject();
    SdkManager sdkManager = AndroidSdkUtils.tryToChooseAndroidSdk();
    assert sdkManager != null;

    projectWizardState.convertApisToInt();
    projectWizardState.put(TemplateMetadata.ATTR_GRADLE_VERSION, GradleUtil.GRADLE_LATEST_VERSION);
    projectWizardState.put(TemplateMetadata.ATTR_GRADLE_PLUGIN_VERSION, GradleUtil.GRADLE_PLUGIN_LATEST_VERSION);
    projectWizardState.put(TemplateMetadata.ATTR_V4_SUPPORT_LIBRARY_VERSION, TemplateMetadata.V4_SUPPORT_LIBRARY_VERSION);
    projectWizardState.put(NewModuleWizardState.ATTR_PROJECT_LOCATION, project.getBasePath());
    projectWizardState.put(NewProjectWizardState.ATTR_MODULE_NAME, "TestModule");
    projectWizardState.put(TemplateMetadata.ATTR_PACKAGE_NAME, "test.pkg");
    projectWizardState.put(TemplateMetadata.ATTR_CREATE_ICONS, false); // If not, you need to initialize additional state
    final BuildToolInfo buildTool = sdkManager.getLatestBuildTool();
    if (buildTool != null) {
      projectWizardState.put(TemplateMetadata.ATTR_BUILD_TOOLS_VERSION, buildTool.getRevision().toString());
    }
    IAndroidTarget[] targets = sdkManager.getTargets();
    projectWizardState.put(ATTR_BUILD_API, targets[targets.length - 1].getVersion().getApiLevel());
  }

  public void createProject(final NewProjectWizardState projectWizardState, boolean syncModel) throws Exception {
    createProject(myFixture, projectWizardState, syncModel);
  }

  public static void createProject(final IdeaProjectTestFixture myFixture,
                                   final NewProjectWizardState projectWizardState,
                                   boolean syncModel) throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        NewProjectWizard.createProject(projectWizardState, myFixture.getProject());
        if (Template.ourMostRecentException != null) {
          fail(Template.ourMostRecentException.getMessage());
        }
      }
    });

    // Sync model
    if (syncModel) {
      String projectName = projectWizardState.getString(NewProjectWizardState.ATTR_MODULE_NAME);
      File projectRoot = new File(projectWizardState.getString(NewModuleWizardState.ATTR_PROJECT_LOCATION));
      assertEquals(projectRoot, VfsUtilCore.virtualToIoFile(myFixture.getProject().getBaseDir()));
      importProject(myFixture.getProject(), projectName, projectRoot);
    }
  }

  public void assertBuildsCleanly(Project project, boolean allowWarnings) throws Exception {
    File base = VfsUtilCore.virtualToIoFile(project.getBaseDir());
    File gradlew = new File(base, "gradlew" + (SystemInfo.isWindows ? ".bat" : ""));
    assertTrue(gradlew.exists());
    File pwd = base.getAbsoluteFile();
    // TODO: Add in --no-daemon, anything to suppress total time?
    Process process = Runtime.getRuntime().exec(new String[]{gradlew.getPath(), "assembleDebug"}, null, pwd);
    int exitCode = process.waitFor();
    byte[] stdout = ByteStreams.toByteArray(process.getInputStream());
    byte[] stderr = ByteStreams.toByteArray(process.getErrorStream());
    String errors = new String(stderr, Charsets.UTF_8);
    String output = new String(stdout, Charsets.UTF_8);
    assertTrue(output + "\n" + errors, output.contains("BUILD SUCCESSFUL"));
    if (!allowWarnings) {
      assertEquals(output + "\n" + errors, "", errors);
    }
    assertEquals(0, exitCode);
  }

  public void assertLintsCleanly(Project project, Severity maxSeverity, Set<Issue> ignored) throws Exception {
    BuiltinIssueRegistry registry = new IntellijLintIssueRegistry();
    Map<Issue, Map<File, List<ProblemData>>> map = Maps.newHashMap();
    IntellijLintClient client = IntellijLintClient.forBatch(project, map, new AnalysisScope(project), registry.getIssues());
    LintDriver driver = new LintDriver(registry, client);
    List<Module> modules = Arrays.asList(ModuleManager.getInstance(project).getModules());
    LintRequest request = new IntellijLintRequest(client, project, null, modules);
    EnumSet<Scope> scope = EnumSet.allOf(Scope.class);
    scope.remove(Scope.CLASS_FILE);
    scope.remove(Scope.ALL_CLASS_FILES);
    scope.remove(Scope.JAVA_LIBRARIES);
    request.setScope(scope);
    driver.analyze(request);
    if (!map.isEmpty()) {
      for (Map<File, List<ProblemData>> fileListMap : map.values()) {
        for (Map.Entry<File, List<ProblemData>> entry : fileListMap.entrySet()) {
          File file = entry.getKey();
          List<ProblemData> problems = entry.getValue();
          for (ProblemData problem : problems) {
            Issue issue = problem.getIssue();
            if (ignored != null && ignored.contains(issue)) {
              continue;
            }
            if (issue.getDefaultSeverity().compareTo(maxSeverity) < 0) {
              fail("Found lint issue " +
                   issue.getId() +
                   " with severity " +
                   issue.getDefaultSeverity() +
                   " in " +
                   file +
                   " at " +
                   problem.getTextRange() +
                   ": " +
                   problem.getMessage());
            }
          }
        }
      }
    }
  }

  public static void importProject(Project project, String projectName, File projectRoot) throws Exception {
    GradleProjectImporter projectImporter = GradleProjectImporter.getInstance();
    projectImporter.importProject(projectName, projectRoot, new GradleProjectImporter.Callback() {
      @Override
      public void projectImported(@NotNull Project project) {
      }

      @Override
      public void importFailed(@NotNull Project project, @NotNull final String errorMessage) {
        fail(errorMessage);
      }
    }, project);
  }
}
