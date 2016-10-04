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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.BuildSignedApkDialogKeystoreStepFixture;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

import static com.google.common.truth.Truth.assertThat;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class BuildSignedApkTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Rule public TemporaryFolder myTemporaryFolder = new TemporaryFolder();

  @Before
  public void skipSourceGenerationOnSync() {
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
  }

  @Test
  public void openAndSignUsingStore() throws IOException {
    File jksFile = new File(myTemporaryFolder.getRoot(), "jks");
    File dstFolder = myTemporaryFolder.newFolder("dst");

    guiTest.importSimpleApplication()
      .openFromMenu(BuildSignedApkDialogKeystoreStepFixture::find, "Build", "Generate Signed APK...")
      .createNew()
      .keyStorePath(jksFile.getAbsolutePath())
      .password("passwd")
      .passwordConfirm("passwd")
      .alias("key")
      .keyPassword("passwd2")
      .keyPasswordConfirm("passwd2")
      .validity("3")
      .firstAndLastName("Android Studio")
      .organizationalUnit("Android")
      .organization("Google")
      .cityOrLocality("Mountain View")
      .stateOrProvince("California")
      .countryCode("US")
      .clickOk()
      .keyStorePassword("passwd")
      .keyAlias("key")
      .keyPassword("passwd2")
      .clickNext()
      .apkDestinationFolder(dstFolder.getAbsolutePath())
      .clickFinish();

    File[] apks = dstFolder.listFiles();
    assertThat(apks).hasLength(1);
    File apk = apks[0];
    try (ZipFile zf = new ZipFile(apk)) {
      assertThat(zf.getEntry("META-INF/CERT.SF")).isNotNull();
    }
  }
}
