/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.TestProjectSystem;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;
import static com.google.common.truth.Truth.assertThat;

public class ImageViewHandlerTest extends LayoutTestCase {
  private NlModel myModel;
  private TestProjectSystem myTestProjectSystem;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.addFileToProject("AndroidManifest.xml", MANIFEST_SOURCE);
    myModel = createModel();

    myTestProjectSystem = new TestProjectSystem();
    PlatformTestUtil.registerExtension(Extensions.getArea(getProject()), ProjectSystemUtil.getEP_NAME(),
                                       myTestProjectSystem, getTestRootDisposable());

    MergedManifest manifest = MergedManifest.get(myModel.getModule());
    String pkg = StringUtil.notNullize(manifest.getPackage());
    assertThat(pkg).isEqualTo("com.example");
  }

  @Override
  public boolean providesCustomManifest() {
    return true;
  }

  public void testSrcCompatNotUsedIfNoAppCompatModuleDependency() {
    ImageViewHandler handler = new ImageViewHandler();
    assertThat(handler.shouldUseSrcCompat(myModel)).isFalse();
  }

  public void testSrcCompatUsedIfNoActivityClassName() {
    myTestProjectSystem.addStubDependency(myModel.getFile().getVirtualFile(), GoogleMavenArtifactId.APPCOMPAT_V7,
                                        new GradleVersion(1, 1));
    ImageViewHandler handler = new ImageViewHandler();
    assertThat(handler.shouldUseSrcCompat(myModel)).isTrue();
  }

  public void testSrcCompatNotUsedIfActivityIsDerivedFromSystemActivity() {
    myModel.getConfiguration().setActivity("com.example.MyActivity");
    myTestProjectSystem.addStubDependency(myModel.getFile().getVirtualFile(), GoogleMavenArtifactId.APPCOMPAT_V7,
                                        new GradleVersion(1, 1));
    addAppCompatActivity();
    addMyActivityAsSystemActivity();

    ImageViewHandler handler = new ImageViewHandler();
    assertThat(handler.shouldUseSrcCompat(myModel)).isFalse();
  }

  public void testSrcCompatUsedIfActivityIsDerivedFromAppCompatActivity() {
    Configuration configuration = myModel.getConfiguration();
    configuration.setActivity("com.example.MyActivity");
    myTestProjectSystem.addStubDependency(myModel.getFile().getVirtualFile(), GoogleMavenArtifactId.APPCOMPAT_V7,
                                          new GradleVersion(1, 1));
    addAppCompatActivity();
    addMyActivityAsAppCompatActivity();

    ImageViewHandler handler = new ImageViewHandler();
    assertThat(handler.shouldUseSrcCompat(myModel)).isTrue();
  }

  public void testSrcCompatUsedIfActivityIsDerivedFromAppCompatActivityUsingReletiveActivityName() {
    Configuration configuration = myModel.getConfiguration();
    configuration.setActivity(".MyActivity");
    myTestProjectSystem.addStubDependency(myModel.getFile().getVirtualFile(), GoogleMavenArtifactId.APPCOMPAT_V7,
                                          new GradleVersion(1, 1));
    addAppCompatActivity();
    addMyActivityAsAppCompatActivity();

    ImageViewHandler handler = new ImageViewHandler();
    assertThat(handler.shouldUseSrcCompat(myModel)).isTrue();
  }

  @NotNull
  private NlModel createModel() {
    ModelBuilder builder = model("linear.xml",
                                 component(LINEAR_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     component(TEXT_VIEW)
                                       .withBounds(100, 100, 100, 100)
                                       .id("@id/myText1")
                                       .width("100dp")
                                       .height("100dp")
                                   ));
    return builder.build();
  }

  private void addMyActivityAsAppCompatActivity() {
    @Language("JAVA")
    String javaFile = "package com.example;\n" +
                      "\n" +
                      "import android.support.v7.app.AppCompatActivity;\n" +
                      "import android.os.Bundle;\n" +
                      "\n" +
                      "public class MyActivity extends AppCompatActivity {\n" +
                      "    @Override\n" +
                      "    protected void onCreate(Bundle savedInstanceState) {\n" +
                      "        super.onCreate(savedInstanceState);\n" +
                      "        setContentView(R.layout.activity_main);\n" +
                      "    }\n" +
                      "\n" +
                      "}\n";
    myFixture.addFileToProject("src/com/example/MyActivity.java", javaFile);
  }

  private void addAppCompatActivity() {
    @Language("JAVA")
    String javaFile = "package android.support.v7.app;\n" +
                      "\n" +
                      "import android.app.Activity;\n" +
                      "\n" +
                      "public class AppCompatActivity extends Activity {\n" +
                      "}\n";
    myFixture.addFileToProject("src/android/support/v7/app/AppCompatActivity.java", javaFile);
  }

  private void addMyActivityAsSystemActivity() {
    @Language("JAVA")
    String javaFile = "package com.example;\n" +
                      "\n" +
                      "import android.app.Activity;\n" +
                      "import android.os.Bundle;\n" +
                      "\n" +
                      "public class MyActivity extends Activity {\n" +
                      "\n" +
                      "    @Override\n" +
                      "    protected void onCreate(Bundle savedInstanceState) {\n" +
                      "        super.onCreate(savedInstanceState);\n" +
                      "        setContentView(R.layout.merge);\n" +
                      "    }\n" +
                      "}\n";
    myFixture.addFileToProject("src/com/example/MyActivity.java", javaFile);
  }

  @Language("XML")
  private static final String MANIFEST_SOURCE =
    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
    "<manifest  \n" +
    "    package='com.example'>\n" +
    "</manifest>\n";
}
