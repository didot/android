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
package com.android.tools.idea.actions;

import com.android.SdkConstants;
import com.google.common.base.Joiner;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * Tests for {@link AndroidImportProjectAction}.
 */
public class AndroidImportProjectActionTest extends IdeaTestCase {
  private VirtualFile myProjectRootDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProjectRootDir = myProject.getBaseDir();
  }

  public void testFindImportTargetWithDirectoryAndWithoutGradleOrEclipseFiles() {
    assertSame(myProjectRootDir, AndroidImportProjectAction.findImportTarget(myProjectRootDir));
  }

  public void testFindImportTargetWithDirectoryAndGradleBuildFile() throws IOException {
    VirtualFile file = createChildFile(SdkConstants.FN_BUILD_GRADLE);
    assertSame(file, AndroidImportProjectAction.findImportTarget(myProjectRootDir));
  }

  public void testFindImportTargetWithDirectoryAndGradleSettingsFile() throws IOException {
    VirtualFile file = createChildFile(SdkConstants.FN_SETTINGS_GRADLE);
    assertSame(file, AndroidImportProjectAction.findImportTarget(myProjectRootDir));
  }

  public void testFindImportTargetWithDirectoryAndEclipseFiles() throws IOException {
    createChildFile(AndroidImportProjectAction.ECLIPSE_CLASSPATH_FILE_NAME);
    VirtualFile file = createChildFile(AndroidImportProjectAction.ECLIPSE_PROJECT_FILE_NAME);
    assertSame(file, AndroidImportProjectAction.findImportTarget(myProjectRootDir));
  }

  public void testFindImportTargetWithEclipseProjectFile() throws IOException {
    VirtualFile file = createChildFile(AndroidImportProjectAction.ECLIPSE_PROJECT_FILE_NAME);
    assertSame(file, AndroidImportProjectAction.findImportTarget(file));
  }

  public void testFindImportTargetWithEclipseProjectFileAndAndroidNature() throws IOException {
    String[] contents = {
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
      "<projectDescription>",
      "  <natures>",
      "   <nature>com.android.ide.eclipse.adt.AndroidNature</nature>",
      "  </natures>",
      "</projectDescription>"
    };
    VirtualFile file = createChildFile(AndroidImportProjectAction.ECLIPSE_PROJECT_FILE_NAME, contents);
    assertNull(AndroidImportProjectAction.findImportTarget(file));
  }

  @NotNull
  private VirtualFile createChildFile(@NotNull String name, @NotNull String...contents) throws IOException {
    File file = new File(myProjectRootDir.getPath(), name);
    assertTrue(FileUtilRt.createIfNotExists(file));
    if (contents.length > 0) {
      String text = Joiner.on(SystemProperties.getLineSeparator()).join(contents);
      FileUtil.writeToFile(file, text);
    }
    VirtualFile vFile = myProjectRootDir.getFileSystem().findFileByPath(file.getPath());
    assertNotNull(vFile);
    return vFile;
  }
}
