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

package org.jetbrains.kotlin.android;

import static org.jetbrains.android.AndroidTestCase.addAndroidFacet;
import static org.jetbrains.android.AndroidTestCase.initializeModuleFixtureBuilderWithSrcAndGen;

import com.android.SdkConstants;
import com.android.testutils.TestUtils;
import com.android.tools.idea.rendering.RenderSecurityManager;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.startup.AndroidCodeStyleSettingsModifier;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.ThreadTracker;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.ui.UIUtil;
import java.io.File;
import java.io.IOException;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.AndroidTestCase.AndroidModuleFixtureBuilder;
import org.jetbrains.android.AndroidTestCase.AndroidModuleFixtureBuilderImpl;
import org.jetbrains.android.ComponentStack;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.formatter.AndroidXmlCodeStyleSettings;
import org.jetbrains.annotations.Nullable;

/**
 * Adapted from {@link org.jetbrains.android.AndroidTestCase}.
 * AndroidTestCase could not be reused because it assumes that all
 * tests reside in tools/adt/idea/android, among other things.
 */
public abstract class KotlinAndroidTestCase extends UsefulTestCase {
  protected JavaCodeInsightTestFixture myFixture;
  protected Module myModule;

  protected AndroidFacet myFacet;
  protected CodeStyleSettings mySettings;

  private boolean myUseCustomSettings;
  private ComponentStack myApplicationComponentStack;
  private ComponentStack myProjectComponentStack;

  protected String getTestDataPath() {
    return TestUtils.getWorkspaceFile("tools/adt/idea/android-kotlin").getPath();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    IdeaTestFixtureFactory.getFixtureFactory().registerFixtureBuilder(
      AndroidModuleFixtureBuilder.class, AndroidModuleFixtureBuilderImpl.class);
    TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    AndroidModuleFixtureBuilder moduleFixtureBuilder = projectBuilder.addModule(AndroidModuleFixtureBuilder.class);
    initializeModuleFixtureBuilderWithSrcAndGen(moduleFixtureBuilder, myFixture.getTempDirPath());

    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
    myModule = moduleFixtureBuilder.getFixture().getModule();

    // Must be done before addAndroidFacet, and must always be done, even if a test provides
    // its own custom manifest file. However, in that case, we will delete it shortly below.
    createManifest();

    myFacet = addAndroidFacet(myModule);

    AndroidTestCase.removeFacetOn(myFixture.getProjectDisposable(), myFacet);

    LanguageLevel languageLevel = getLanguageLevel();
    if (languageLevel != null) {
      LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(myModule.getProject());
      if (extension != null) {
        extension.setLanguageLevel(languageLevel);
      }
    }

    if (RenderSecurityManager.RESTRICT_READS) {
      // Unit test class loader includes disk directories which security manager does not allow access to
      RenderSecurityManager.sEnabled = false;
    }

    mySettings = CodeStyleSettingsManager.getSettings(getProject()).clone();
    AndroidCodeStyleSettingsModifier.modify(mySettings);
    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(mySettings);
    myUseCustomSettings = getAndroidCodeStyleSettings().USE_CUSTOM_SETTINGS;
    getAndroidCodeStyleSettings().USE_CUSTOM_SETTINGS = true;

    // Layoutlib rendering thread will be shutdown when the app is closed so do not report it as a leak
    ThreadTracker.longRunningThreadCreated(ApplicationManager.getApplication(), "Layoutlib");
    IdeSdks.removeJdksOn(myFixture.getProjectDisposable());

    myApplicationComponentStack = new ComponentStack(ApplicationManager.getApplication());
    myProjectComponentStack = new ComponentStack(getProject());

    IdeSdks.removeJdksOn(myFixture.getProjectDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      // Finish dispatching any remaining events before shutting down everything
      UIUtil.dispatchAllInvocationEvents();

      myApplicationComponentStack.restoreComponents();
      myApplicationComponentStack = null;
      myProjectComponentStack.restoreComponents();
      myProjectComponentStack = null;
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
      myModule = null;
      myFacet = null;
      mySettings = null;

      getAndroidCodeStyleSettings().USE_CUSTOM_SETTINGS = myUseCustomSettings;
      if (RenderSecurityManager.RESTRICT_READS) {
        RenderSecurityManager.sEnabled = true;
      }
    }
    finally {
      try {
        myFixture.tearDown();
      }
      finally {
        myFixture = null;
        super.tearDown();
      }
    }
  }

  protected Project getProject() {
    return myFixture.getProject();
  }

  protected void copyResourceDirectoryForTest(String path) {
    File testFile = new File(path);
    if (testFile.isFile()) {
      myFixture.copyDirectoryToProject(testFile.getParent() + "/res", "res");
    } else if (testFile.isDirectory()) {
      myFixture.copyDirectoryToProject(testFile.getPath() + "/res", "res");
    }
  }

  /**
   * Defines the project level to set for the test project, or null to get the default language
   * level associated with the test project.
   */
  @Nullable
  protected LanguageLevel getLanguageLevel() {
    // Higher language levels trigger JavaPlatformModuleSystem checks which fail for our light PSI classes. For now set the language level
    // to what real AS actually uses.
    // TODO(b/110679859): figure out how to stop JavaPlatformModuleSystem from thinking the light classes are not accessible.
    return LanguageLevel.JDK_1_8;
  }

  protected static AndroidXmlCodeStyleSettings getAndroidCodeStyleSettings() {
    return AndroidXmlCodeStyleSettings.getInstance(CodeStyleSchemes.getInstance().getDefaultScheme().getCodeStyleSettings());
  }

  protected final void createManifest() {
    myFixture.copyFileToProject(
      "idea-android/testData/android/" + SdkConstants.FN_ANDROID_MANIFEST_XML,
      SdkConstants.FN_ANDROID_MANIFEST_XML);
  }

  protected final void deleteManifest() {
    deleteManifest(myModule);
  }

  protected final void deleteManifest(final Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assertNotNull(facet);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        String manifestRelativePath = facet.getProperties().MANIFEST_FILE_RELATIVE_PATH;
        VirtualFile manifest = AndroidRootUtil.getFileByRelativeModulePath(module, manifestRelativePath, true);
        if (manifest != null) {
          try {
            manifest.delete(this);
          }
          catch (IOException e) {
            fail("Could not delete default manifest");
          }
        }
      }
    });
  }
}
