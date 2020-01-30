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
package com.android.tools.idea.mlkit;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.mlkit.lightpsi.LightModelClass;
import com.google.common.collect.Iterables;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import java.io.File;
import java.util.List;
import org.jetbrains.android.AndroidTestCase;

public class MlkitLightClassTest extends AndroidTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    StudioFlags.MLKIT_TFLITE_MODEL_FILE_TYPE.override(true);
    StudioFlags.MLKIT_LIGHT_CLASSES.override(true);

    myFixture.setTestDataPath(new File(getModulePath("mlkit"), "testData").getPath());
    VirtualFile tfliteFile = myFixture.copyFileToProject("my_model.tflite", "/assets/my_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, tfliteFile);

    PsiFile imageFile = myFixture.addFileToProject(
      "src/com/google/firebase/ml/vision/common/FirebaseVisionImage.java",
      "package com.google.firebase.ml.vision.common;\n" +
      "public class FirebaseVisionImage {}\n");
    myFixture.allowTreeAccessForFile(imageFile.getVirtualFile());
  }

  @Override
  public void tearDown() throws Exception {
    try {
      StudioFlags.MLKIT_TFLITE_MODEL_FILE_TYPE.clearOverride();
      StudioFlags.MLKIT_LIGHT_CLASSES.clearOverride();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testHighlighting_java() {
    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import java.nio.ByteBuffer;" +
      "import p1.p2.mlkit.auto.MyModel;\n" +
      "import com.google.firebase.ml.vision.common.FirebaseVisionImage;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        MyModel myModel = new MyModel(this);\n" +
      "        FirebaseVisionImage image = null;\n" +
      "        MyModel.Output output = myModel.run(image);\n" +
      "        ByteBuffer byteBuffer = output.getProbability();\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testHighlighting_kotlin() {
    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.kt",
      // language=kotlin
      "package p1.p2\n" +
      "\n" +
      "import android.app.Activity\n" +
      "import android.os.Bundle\n" +
      "import p1.p2.mlkit.auto.MyModel\n" +
      "import android.util.Log\n" +
      "import com.google.firebase.ml.vision.common.FirebaseVisionImage\n" +
      "\n" +
      "class MainActivity : Activity() {\n" +
      "    override fun onCreate(savedInstanceState: Bundle?) {\n" +
      "        super.onCreate(savedInstanceState)\n" +
      "        val image: FirebaseVisionImage? = null\n" +
      "        val mymodel = MyModel(this)\n" +
      "        val output = mymodel.run(image)\n" +
      "        val probability = output.probability\n" +
      "        Log.d(\"TAG\", probability.toString())\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testCompleteRunMethod() {
    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import p1.p2.mlkit.auto.MyModel;\n" +
      "import com.google.firebase.ml.vision.common.FirebaseVisionImage;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        MyModel myModel = new MyModel(this);\n" +
      "        myModel.ru<caret>;\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResult(
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import p1.p2.mlkit.auto.MyModel;\n" +
      "import com.google.firebase.ml.vision.common.FirebaseVisionImage;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        MyModel myModel = new MyModel(this);\n" +
      "        myModel.run();\n" +
      "    }\n" +
      "}");
  }

  public void testCompleteInnerClass() {
    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import com.google.firebase.ml.vision.common.FirebaseVisionImage;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        MyModel.<caret>;\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    LookupElement[] elements = myFixture.complete(CompletionType.BASIC);
    assertThat(elements).hasLength(2);
    assertThat(elements[0].toString()).isEqualTo("MyModel.Label");
    assertThat(elements[1].toString()).isEqualTo("MyModel.Output");

    myFixture.getLookup().setCurrentItem(elements[0]);
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    myFixture.checkResult("package p1.p2;\n" +
                          "\n" +
                          "import android.app.Activity;\n" +
                          "import android.os.Bundle;\n" +
                          "import com.google.firebase.ml.vision.common.FirebaseVisionImage;\n" +
                          "\n" +
                          "import p1.p2.mlkit.auto.MyModel;\n" +
                          "\n" +
                          "public class MainActivity extends Activity {\n" +
                          "    @Override\n" +
                          "    protected void onCreate(Bundle savedInstanceState) {\n" +
                          "        super.onCreate(savedInstanceState);\n" +
                          "        MyModel.Label;\n" +
                          "    }\n" +
                          "}");
  }

  public void testCompleteModelClass() {
    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import com.google.firebase.ml.vision.common.FirebaseVisionImage;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        MyMod<caret>;\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    LookupElement[] elements = myFixture.complete(CompletionType.BASIC);
    assertThat(elements).hasLength(1);
    assertThat(elements[0].getLookupString()).isEqualTo("MyModel");

    myFixture.getLookup().setCurrentItem(elements[0]);
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    myFixture.checkResult("package p1.p2;\n" +
                          "\n" +
                          "import android.app.Activity;\n" +
                          "import android.os.Bundle;\n" +
                          "import com.google.firebase.ml.vision.common.FirebaseVisionImage;\n" +
                          "\n" +
                          "import p1.p2.mlkit.auto.MyModel;\n" +
                          "\n" +
                          "public class MainActivity extends Activity {\n" +
                          "    @Override\n" +
                          "    protected void onCreate(Bundle savedInstanceState) {\n" +
                          "        super.onCreate(savedInstanceState);\n" +
                          "        MyModel;\n" +
                          "    }\n" +
                          "}");
  }

  public void testModuleService() {
    MlkitModuleService mlkitService = MlkitModuleService.getInstance(myModule);
    List<PsiClass> lightClasses = mlkitService.getLightModelClassList();
    assertThat(lightClasses).hasSize(1);
    PsiClass lightClass = Iterables.getOnlyElement(lightClasses);
    assertThat(lightClass.getName()).isEqualTo("MyModel");
    assertThat(ModuleUtilCore.findModuleForPsiElement(lightClass)).isEqualTo(myModule);
  }
}
