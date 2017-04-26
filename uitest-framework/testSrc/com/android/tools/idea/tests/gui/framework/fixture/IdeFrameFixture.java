/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater;
import com.android.tools.idea.gradle.project.build.GradleBuildContext;
import com.android.tools.idea.gradle.project.build.GradleBuildState;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.project.build.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.project.AndroidProjectBuildNotifications;
import com.android.tools.idea.testing.Modules;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdManagerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleBuildModelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleProjectEventListener;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.collect.Lists;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.ThreeState;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate;
import static com.android.tools.idea.gradle.util.BuildMode.COMPILE_JAVA;
import static com.android.tools.idea.gradle.util.BuildMode.SOURCE_GEN;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.fail;
import static org.fest.util.Strings.quote;
import static org.jetbrains.android.AndroidPlugin.EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY;
import static org.jetbrains.plugins.gradle.settings.DistributionType.LOCAL;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IdeFrameFixture extends ComponentFixture<IdeFrameFixture, IdeFrameImpl> {
  @NotNull private final File myProjectPath;
  @NotNull private final GradleProjectEventListener myGradleProjectEventListener;
  @NotNull private final Modules myModules;

  private EditorFixture myEditor;
  private boolean myIsClosed;

  @NotNull
  public static IdeFrameFixture find(@NotNull final Robot robot, @NotNull final File projectPath, @Nullable final String projectName) {
    final GenericTypeMatcher<IdeFrameImpl> matcher = new GenericTypeMatcher<IdeFrameImpl>(IdeFrameImpl.class) {
      @Override
      protected boolean isMatching(@NotNull IdeFrameImpl frame) {
        Project project = frame.getProject();
        if (project != null && toSystemIndependentName(projectPath.getPath()).equals(project.getBasePath())) {
          return projectName == null || projectName.equals(project.getName());
        }
        return false;
      }
    };

    return new IdeFrameFixture(robot, GuiTests.waitUntilShowing(robot, matcher), projectPath);
  }

  public IdeFrameFixture(@NotNull Robot robot, @NotNull IdeFrameImpl target, @NotNull File projectPath) {
    super(IdeFrameFixture.class, robot, target);
    myProjectPath = projectPath;
    Project project = getProject();
    myModules = new Modules(project);

    Disposable disposable = new NoOpDisposable();
    Disposer.register(project, disposable);

    myGradleProjectEventListener = new GradleProjectEventListener();

    GradleSyncState.subscribe(project, myGradleProjectEventListener);
    GradleBuildState.subscribe(project, myGradleProjectEventListener);
  }

  @NotNull
  public File getProjectPath() {
    return myProjectPath;
  }

  @NotNull
  public List<String> getModuleNames() {
    List<String> names = Lists.newArrayList();
    for (Module module : getModuleManager().getModules()) {
      names.add(module.getName());
    }
    return names;
  }

  @NotNull
  public AndroidModuleModel getAndroidProjectForModule(@NotNull String name) {
    Module module = getModule(name);
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null && facet.requiresAndroidModel()) {
      // TODO: Resolve direct AndroidGradleModel dep (b/22596984)
      AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
      if (androidModel != null) {
        return androidModel;
      }
    }
    throw new AssertionError("Unable to find AndroidGradleModel for module " + quote(name));
  }

  @NotNull
  public Module getModule(@NotNull String name) {
    return myModules.getModule(name);
  }

  public boolean hasModule(@NotNull String name) {
    return myModules.hasModule(name);
  }

  @NotNull
  private ModuleManager getModuleManager() {
    return ModuleManager.getInstance(getProject());
  }

  @NotNull
  public EditorFixture getEditor() {
    if (myEditor == null) {
      myEditor = new EditorFixture(robot(), this);
    }

    return myEditor;
  }

  @NotNull
  public AndroidModuleModel getAndroidModel(@NotNull String moduleName) {
    Module module = getModule(moduleName);
    assertNotNull("Could not find module " + moduleName, module);
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assertNotNull("Module " + moduleName + " is not an Android module", facet);
    assertTrue("Module " + moduleName + " is not a Gradle project", facet.requiresAndroidModel());
    // TODO: Resolve direct AndroidGradleModel dep (b/22596984)
    AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
    assertNotNull("Module " + moduleName + " does not have a Gradle project (not synced yet or sync failed?)", androidModel);

    return androidModel;
  }

  @NotNull
  public GradleInvocationResult invokeProjectMake() {
    myGradleProjectEventListener.reset();

    AtomicReference<GradleInvocationResult> resultRef = new AtomicReference<>();
    AndroidProjectBuildNotifications.subscribe(
      getProject(), context -> {
        if (context instanceof GradleBuildContext) {
          resultRef.set(((GradleBuildContext)context).getBuildResult());
        }
      });
    selectProjectMakeAction();

    waitForBuildToFinish(COMPILE_JAVA);

    return resultRef.get();
  }

  @NotNull
  public IdeFrameFixture invokeProjectMakeAndSimulateFailure(@NotNull String failure) {
    Runnable failTask = () -> {
      throw new ExternalSystemException(failure);
    };
    ApplicationManager.getApplication().putUserData(EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY, failTask);
    selectProjectMakeAction();
    return this;
  }

  /**
   * Finds the Run button in the IDE interface.
   *
   * @return ActionButtonFixture for the run button.
   */
  @NotNull
  public ActionButtonFixture findDebugApplicationButton() {
    return findActionButtonByActionId("Debug");
  }

  @NotNull
  public ActionButtonFixture findRunApplicationButton() {
    return findActionButtonByActionId("Run");
  }

  @NotNull
  public ActionButtonFixture findApplyChangesButton() {
    return findActionButtonByText("Apply Changes");
  }

  public DeployTargetPickerDialogFixture debugApp(@NotNull String appName) throws ClassNotFoundException {
    selectApp(appName);
    findActionButtonByActionId("Debug").click();
    return DeployTargetPickerDialogFixture.find(robot());
  }

  public DeployTargetPickerDialogFixture runApp(@NotNull String appName) throws ClassNotFoundException {
    selectApp(appName);
    findActionButtonByActionId("Run").click();
    return DeployTargetPickerDialogFixture.find(robot());
  }

  /** Similar to runApp except doesn't wait for a DeployTargetPickerDialog after. */
  @NotNull
  public IdeFrameFixture runNonAndroidApp(@NotNull String appName) {
    selectApp(appName);
    findActionButtonByActionId("Run").click();
    return this;
  }

  @NotNull
  public IdeFrameFixture stopApp() {
    return invokeMenuPath("Run", "Stop \'app\'");
  }

  @NotNull
  public RunToolWindowFixture getRunToolWindow() {
    return new RunToolWindowFixture(this);
  }

  @NotNull
  public DebugToolWindowFixture getDebugToolWindow() {
    return new DebugToolWindowFixture(this);
  }

  protected void selectProjectMakeAction() {
    invokeMenuPath("Build", "Make Project");
  }

  /** Selects the item at {@code menuPath} and returns the result of {@code fixtureFunction} applied to this {@link IdeFrameFixture}. */
  public <T> T openFromMenu(Function<IdeFrameFixture, T> fixtureFunction, @NotNull String... menuPath) {
    getMenuFixture().invokeMenuPath(menuPath);
    return fixtureFunction.apply(this);
  }

  /**
   * Invokes an action by menu path
   *
   * @param path the series of menu names, e.g. {@link invokeActionByMenuPath("Build", "Make Project")}
   */
  public IdeFrameFixture invokeMenuPath(@NotNull String... path) {
    getMenuFixture().invokeMenuPath(path);
    return this;
  }

  /**
   * Wait till an path is enabled then invokes the action. Used for menu options that might be disabled or not available at first
   *
   * @param path the series of menu names, e.g. {@link invokeActionByMenuPath("Build", "Make Project")}
   */
  public IdeFrameFixture waitAndInvokeMenuPath(@NotNull String... path) {
    Wait.seconds(10).expecting("Wait until the path " + Arrays.toString(path) + " is ready.")
      .until(() -> getMenuFixture().isMenuPathEnabled(path));
    getMenuFixture().invokeMenuPath(path);
    return this;
  }

  /**
   * Returns whether a menu path is enabled
   *
   * @param path the series of menu names, e.g. {@link isMenuPathEnabled("Build", "Make Project ")}
   */
  public boolean isMenuPathEnabled(@NotNull String... path) {
    return getMenuFixture().isMenuPathEnabled(path);
  }

  @NotNull
  private MenuFixture getMenuFixture() {
    return new MenuFixture(robot(), target());
  }

  @NotNull
  public IdeFrameFixture waitForBuildToFinish(@NotNull BuildMode buildMode) {
    Project project = getProject();
    if (buildMode == SOURCE_GEN && !GradleProjectBuilder.getInstance(project).isSourceGenerationEnabled()) {
      return this;
    }

    // http://b.android.com/226797 - Most builds finish in 10 seconds, but GradleBuildTest.compileWithJack was failing.
    Wait.seconds(20).expecting("Build (" + buildMode + ") for project " + quote(project.getName()) + " to finish'")
      .until(() -> {
        if (buildMode == SOURCE_GEN) {
          PostProjectBuildTasksExecutor tasksExecutor = PostProjectBuildTasksExecutor.getInstance(project);
          if (tasksExecutor.getLastBuildTimestamp() != null) {
            // This will happen when creating a new project. Source generation happens before the IDE frame is found and build listeners
            // are created. It is fairly safe to assume that source generation happened if we have a timestamp for a "last performed build".
            return true;
          }
        }
        return myGradleProjectEventListener.isBuildFinished(buildMode);
      });

    GuiTests.waitForBackgroundTasks(robot());
    robot().waitForIdle();

    return this;
  }

  @NotNull
  public FileFixture findExistingFileByRelativePath(@NotNull String relativePath) {
    VirtualFile file = findFileByRelativePath(relativePath, true);
    return new FileFixture(getProject(), file);
  }

  @Nullable
  @Contract("_, true -> !null")
  public VirtualFile findFileByRelativePath(@NotNull String relativePath, boolean requireExists) {
    //noinspection Contract
    assertFalse("Should use '/' in test relative paths, not File.separator", relativePath.contains("\\"));
    Project project = getProject();
    VirtualFile file = project.getBaseDir().findFileByRelativePath(relativePath);
    if (requireExists) {
      //noinspection Contract
      assertNotNull("Unable to find file with relative path " + quote(relativePath), file);
    }
    return file;
  }

  @NotNull
  public IdeFrameFixture requestProjectSyncAndExpectFailure() {
    requestProjectSync();
    return waitForGradleProjectSyncToFail();
  }

  @NotNull
  public IdeFrameFixture requestProjectSyncAndSimulateError(@NotNull String error) {
    registerSyncErrorToSimulate(error);
    // When simulating the error, we don't have to wait for sync to happen. Sync never happens because the error is thrown before it (sync)
    // is started.
    return requestProjectSync();
  }

  @NotNull
  public IdeFrameFixture requestProjectSyncAndSimulateError(@NotNull Throwable error) {
    registerSyncErrorToSimulate(error);
    // When simulating the error, we don't have to wait for sync to happen. Sync never happens because the error is thrown before it (sync)
    // is started.
    return requestProjectSync();
  }

  @NotNull
  public IdeFrameFixture requestProjectSync() {
    myGradleProjectEventListener.reset();

    waitForGradleSyncAction();
    invokeMenuPath("Tools", "Android", "Sync Project with Gradle Files");

    return this;
  }

  private void waitForGradleSyncAction() {
    GuiTests.waitForBackgroundTasks(robot());
  }

  @NotNull
  public IdeFrameFixture waitForGradleProjectSyncToFail() {
    try {
      waitForGradleProjectSyncToFinish(Wait.seconds(10), true);
      fail("Expecting project sync to fail");
    }
    catch (RuntimeException expected) {
      // expected failure.
    }
    GuiTests.waitForBackgroundTasks(robot());
    return this;
  }

  @NotNull
  public IdeFrameFixture waitForGradleProjectSyncToStart() {
    Project project = getProject();
    GradleSyncState syncState = GradleSyncState.getInstance(project);
    if (!syncState.isSyncInProgress()) {
      Wait.seconds(10).expecting("Syncing project " + quote(project.getName()) + " to finish")
        .until(myGradleProjectEventListener::isSyncStarted);
    }
    return this;
  }

  @NotNull
  public IdeFrameFixture waitForGradleProjectSyncToFinish() {
    return waitForGradleProjectSyncToFinish(Wait.seconds(10));
  }

  @NotNull
  public IdeFrameFixture waitForGradleProjectSyncToFinish(@NotNull Wait waitForSync) {
    waitForGradleProjectSyncToFinish(waitForSync, false);
    return this;
  }

  @NotNull
  public IdeFrameFixture waitForGradleImportProjectSync() {
    Wait.seconds(30).expecting("Project Import Sync to finish")
      .until(() -> !GradleSyncState.getInstance(getProject()).isSyncInProgress());
    return this;
  }

  private void waitForGradleProjectSyncToFinish(@NotNull Wait waitForSync, boolean expectSyncFailure) {
    Project project = getProject();

    // ensure GradleInvoker (in-process build) is always enabled.
    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
    buildConfiguration.USE_EXPERIMENTAL_FASTER_BUILD = true;

    waitForSync.expecting("Syncing project " + quote(project.getName()) + " to finish")
      .until(() -> {
        GradleSyncState syncState = GradleSyncState.getInstance(project);
        boolean syncFinished =
          (myGradleProjectEventListener.isSyncFinished() || syncState.isSyncNeeded() != ThreeState.YES) && !syncState.isSyncInProgress();
        if (expectSyncFailure) {
          syncFinished = syncFinished && myGradleProjectEventListener.hasSyncError();
        }
        return syncFinished;
      });

    waitForGradleSyncAction();

    if (myGradleProjectEventListener.hasSyncError()) {
      RuntimeException syncError = myGradleProjectEventListener.getSyncError();
      myGradleProjectEventListener.reset();
      throw syncError;
    }

    if (!myGradleProjectEventListener.isSyncSkipped()) {
      waitForBuildToFinish(SOURCE_GEN);
    }

    GuiTests.waitForBackgroundTasks(robot());
  }

  @NotNull
  private ActionButtonFixture findActionButtonByActionId(String actionId) {
    return ActionButtonFixture.findByActionId(actionId, robot(), target());
  }

  @NotNull
  private ActionButtonFixture findActionButtonByText(@NotNull String text) {
    return ActionButtonFixture.findByText(text, robot(), target());
  }

  @NotNull
  public AndroidToolWindowFixture getAndroidToolWindow() {
    return new AndroidToolWindowFixture(getProject(), robot());
  }

  @NotNull
  public CapturesToolWindowFixture getCapturesToolWindow() {
    return new CapturesToolWindowFixture(getProject(), robot());
  }

  @NotNull
  public BuildVariantsToolWindowFixture getBuildVariantsWindow() {
    return new BuildVariantsToolWindowFixture(this);
  }

  @NotNull
  public MessagesToolWindowFixture getMessagesToolWindow() {
    return new MessagesToolWindowFixture(getProject(), robot());
  }

  @NotNull
  public GradleToolWindowFixture getGradleToolWindow() {
    return new GradleToolWindowFixture(getProject(), robot());
  }

  @NotNull
  public IdeSettingsDialogFixture openIdeSettings() {
    // Using invokeLater because we are going to show a *modal* dialog via API (instead of clicking a button, for example.) If we use
    // GuiActionRunner the test will hang until the modal dialog is closed.
    ApplicationManager.getApplication().invokeLater(
      () -> {
        Project project = getProject();
        ShowSettingsUtil.getInstance().showSettingsDialog(project, ShowSettingsUtilImpl.getConfigurableGroups(project, true));
      });
    return IdeSettingsDialogFixture.find(robot());
  }

  @NotNull
  public IdeFrameFixture useLocalGradleDistribution(@NotNull File gradleHomePath) {
    return useLocalGradleDistribution(gradleHomePath.getPath());
  }

  @NotNull
  public IdeFrameFixture useLocalGradleDistribution(@NotNull String gradleHome) {
    GradleProjectSettings settings = getGradleSettings();
    settings.setDistributionType(LOCAL);
    settings.setGradleHome(gradleHome);
    return this;
  }

  @NotNull
  public GradleProjectSettings getGradleSettings() {
    return GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(getProject());
  }

  @NotNull
  public AvdManagerDialogFixture invokeAvdManager() {
    ActionButtonFixture button = findActionButtonByActionId("Android.RunAndroidAvdManager");
    button.requireVisible();
    button.requireEnabled();
    button.click();
    return AvdManagerDialogFixture.find(robot(), this);
  }

  @NotNull
  public IdeSettingsDialogFixture invokeSdkManager() {
    robot().click(robot().finder().find(Matchers.byTooltip(JComponent.class, "SDK Manager").andIsShowing()));
    return IdeSettingsDialogFixture.find(robot());
  }

  @NotNull
  public ProjectViewFixture getProjectView() {
    return new ProjectViewFixture(getProject(), robot());
  }

  @NotNull
  public Project getProject() {
    return target().getProject();
  }

  public WelcomeFrameFixture closeProject() {
    myIsClosed = true;
    return openFromMenu(WelcomeFrameFixture::find, "File", "Close Project");
  }

  public boolean isClosed() {
    return myIsClosed;
  }

  @NotNull
  public LibraryPropertiesDialogFixture showPropertiesForLibrary(@NotNull String libraryName) {
    return getProjectView().showPropertiesForLibrary(libraryName);
  }

  @NotNull
  public MessagesFixture findMessageDialog(@NotNull String title) {
    return MessagesFixture.findByTitle(robot(), title);
  }

  @NotNull
  public DialogFixture findDialog(@NotNull String title) {
    return new DialogFixture(robot(), robot().finder().find(Matchers.byTitle(JDialog.class, title)));
  }

  @NotNull
  public IdeFrameFixture setGradleJvmArgs(@NotNull String jvmArgs) {
    Project project = getProject();

    GradleSettings settings = GradleSettings.getInstance(project);
    settings.setGradleVmOptions(jvmArgs);

    Wait.seconds(1).expecting("Gradle settings to be set").until(() -> jvmArgs.equals(settings.getGradleVmOptions()));

    return this;
  }

  @NotNull
  public IdeFrameFixture updateGradleWrapperVersion(@NotNull String version) throws IOException {
    GradleWrapper.find(getProject()).updateDistributionUrlAndDisplayFailure(version);
    return this;
  }

  @NotNull
  public IdeFrameFixture updateAndroidGradlePluginVersion(@NotNull String version) throws IOException {
    ApplicationManager.getApplication().invokeAndWait(
      () -> {
        AndroidPluginVersionUpdater versionUpdater = AndroidPluginVersionUpdater.getInstance(getProject());
        AndroidPluginVersionUpdater.UpdateResult result = versionUpdater.updatePluginVersion(GradleVersion.parse(version), null);
        assertTrue("Android Gradle plugin version was not updated", result.isPluginVersionUpdated());
      });
    return this;
  }

  @NotNull
  public GradleBuildModelFixture parseBuildFileForModule(@NotNull String moduleName) {
    Module module = getModule(moduleName);
    VirtualFile buildFile = getGradleBuildFile(module);
    Ref<GradleBuildModel> buildModelRef = new Ref<>();
    new ReadAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        buildModelRef.set(GradleBuildModel.parseBuildFile(buildFile, getProject()));
      }
    }.execute();
    return new GradleBuildModelFixture(buildModelRef.get());
  }

  private static class NoOpDisposable implements Disposable {
    @Override
    public void dispose() {
    }
  }

  public void selectApp(@NotNull String appName) {
    ActionButtonFixture runButton = findRunApplicationButton();
    Container actionToolbarContainer = GuiQuery.getNonNull(() -> runButton.target().getParent());
    ComboBoxActionFixture comboBoxActionFixture = ComboBoxActionFixture.findComboBox(robot(), actionToolbarContainer);
    comboBoxActionFixture.selectItem(appName);
    robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
    Wait.seconds(1).expecting("ComboBox to be selected").until(() -> appName.equals(comboBoxActionFixture.getSelectedItemText()));
  }

  /**
   * Gets the focus back to Android Studio if it was lost
   */
  public void requestFocusIfLost() {
    KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    Wait.seconds(5).expecting("a component to have the focus").until(() -> {
      // Keep requesting focus until it is obtained by a component which is showing. Since there is no guarantee that the request focus will
      // be granted keep asking until it is. This problem has appeared at least when not using a window manager when running tests. The focus
      // can sometimes temporarily be held by a component that is not showing, when closing a dialog for example. This is a transition state
      // and we want to make sure to keep going until the focus is held by a stable component.
      Component focusOwner = keyboardFocusManager.getFocusOwner();
      if (focusOwner == null || !focusOwner.isShowing()) {
        if (SystemInfo.isMac) {
          robot().click(target(), new Point(1, 1)); // Simulate title bar click
        }
        GuiTask.execute(() -> target().requestFocus());
        return false;
      }
      return true;
    });
  }
}
