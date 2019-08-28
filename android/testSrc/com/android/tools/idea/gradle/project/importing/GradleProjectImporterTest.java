/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.importing;

import com.android.annotations.Nullable;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.SdkSync;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.JavaProjectTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.mockito.Mock;
import org.mockito.verification.VerificationMode;

import java.io.File;
import java.io.IOException;

import static com.intellij.pom.java.LanguageLevel.JDK_1_8;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link GradleProjectImporter}.
 */
public class GradleProjectImporterTest extends JavaProjectTestCase {
  @Mock private GradleSyncInvoker mySyncInvoker;
  @Mock private NewProjectSetup myProjectSetup;
  @Mock private ProjectFolder myProjectFolder;
  @Mock private GradleSettings myGradleSettings;
  @Mock private GradleProjectInfo myGradleProjectInfo;

  private String myProjectName;
  private File myProjectFolderPath;

  private GradleProjectImporter myProjectImporter;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myProjectName = "testProject";

    SdkSync sdkSync = mock(SdkSync.class);

    Project project = getProject();
    String projectFolderPathText = project.getBasePath();
    assertNotNull(projectFolderPathText);
    myProjectFolderPath = new File(projectFolderPathText);

    ProjectFolder.Factory projectFolderFactory = mock(ProjectFolder.Factory.class);
    when(projectFolderFactory.create(myProjectFolderPath)).thenReturn(myProjectFolder);

    // Replace GradleSettings service with a mock.
    new IdeComponents(project).replaceProjectService(GradleSettings.class, myGradleSettings);
    assertSame(GradleSettings.getInstance(project), myGradleSettings);

    ServiceContainerUtil.registerServiceInstance(project, GradleProjectInfo.class, myGradleProjectInfo);

    myProjectImporter = new GradleProjectImporter(sdkSync, mySyncInvoker, myProjectSetup, projectFolderFactory);
  }

  // See:
  // https://code.google.com/p/android/issues/detail?id=172347
  // https://code.google.com/p/android/issues/detail?id=227437
  public void testOpenProject() throws Exception {
    Project newProject = getProject();
    VirtualFile rootFolder = newProject.getBaseDir();

    when(myProjectSetup.openProject(myProjectFolderPath.getPath())).thenReturn(newProject);

    myProjectImporter.openProject(rootFolder);

    // Verify project setup before syncing.
    verifyProjectFilesCreation();
    verifyProjectCreation(never());
    verifyProjectPreparation(null, true);
    verifyGradleVmOptionsCleanup(times(1));

    verify(myProjectSetup, times(1)).openProject(myProjectFolderPath.getPath());
    verifyProjectWasMarkedAsImported();
  }

  public void testImportProjectWithDefaultSettings() throws Exception {
    Project newProject = getProject();
    when(myProjectSetup.createProject(myProjectName, myProjectFolderPath.getPath())).thenReturn(newProject);

    GradleSyncListener syncListener = mock(GradleSyncListener.class);
    myProjectImporter.importProject(myProjectName, myProjectFolderPath, syncListener);

    // Verify project setup before syncing.
    verifyProjectFilesCreation();
    verifyProjectCreation(times(1));
    verifyProjectPreparation(null, false);
    verifyGradleVmOptionsCleanup(times(1));

    // Verify sync.
    verifyGradleSyncInvocation(new GradleProjectImporter.Request(), syncListener);
  }

  public void testImportProjectWithNullProject() throws Exception {
    GradleProjectImporter.Request importSettings = new GradleProjectImporter.Request();
    importSettings.project = null;
    importSettings.javaLanguageLevel = JDK_1_8;

    Project newProject = getProject();
    when(myProjectSetup.createProject(myProjectName, myProjectFolderPath.getPath())).thenReturn(newProject);

    GradleSyncListener syncListener = mock(GradleSyncListener.class);
    myProjectImporter.importProject(myProjectName, myProjectFolderPath, importSettings, syncListener);

    // Verify project setup before syncing.
    verifyProjectFilesCreation();
    verifyProjectCreation(times(1));
    verifyProjectPreparation(JDK_1_8, false);
    verifyGradleVmOptionsCleanup(times(1));

    // Verify sync.
    verifyGradleSyncInvocation(importSettings, syncListener);
  }

  public void testImportProjectWithNonNullProject() throws Exception {
    GradleProjectImporter.Request importSettings = new GradleProjectImporter.Request();
    importSettings.project = getProject();
    importSettings.javaLanguageLevel = JDK_1_8;

    GradleSyncListener syncListener = mock(GradleSyncListener.class);
    myProjectImporter.importProject(myProjectName, myProjectFolderPath, importSettings, syncListener);

    // Verify project setup before syncing.
    verifyProjectFilesCreation();
    verifyProjectCreation(never());
    verifyProjectPreparation(JDK_1_8, false);
    verifyGradleVmOptionsCleanup(never());

    // Verify sync.
    verifyGradleSyncInvocation(importSettings, syncListener);
  }

  private void verifyProjectFilesCreation() throws IOException {
    verify(myProjectFolder, times(1)).createTopLevelBuildFile();
    verify(myProjectFolder, times(1)).createIdeaProjectFolder();
  }

  private void verifyProjectCreation(@NotNull VerificationMode verificationMode) {
    verify(myProjectSetup, verificationMode).createProject(myProjectName, myProjectFolderPath.getPath());
  }

  private void verifyProjectPreparation(@Nullable LanguageLevel languageLevel, boolean openProject) {
    verify(myProjectSetup, times(1)).prepareProjectForImport(getProject(), languageLevel, openProject);
  }

  private void verifyGradleVmOptionsCleanup(@NotNull VerificationMode verificationMode) {
    verify(myGradleSettings, verificationMode).setGradleVmOptions("");
  }

  private void verifyGradleSyncInvocation(@NotNull GradleProjectImporter.Request importSettings,
                                          @Nullable GradleSyncListener syncListener) {
    GradleSyncInvoker.Request syncRequest = GradleSyncInvoker.Request.projectLoaded();

    syncRequest.generateSourcesOnSuccess = importSettings.generateSourcesOnSuccess;
    syncRequest.runInBackground = true;

    verify(mySyncInvoker, times(1)).requestProjectSync(getProject(), syncRequest, syncListener);
    verifyProjectWasMarkedAsImported();
  }

  private void verifyProjectWasMarkedAsImported() {
    verify(myGradleProjectInfo, times(1)).setImportedProject(true);
  }
}
