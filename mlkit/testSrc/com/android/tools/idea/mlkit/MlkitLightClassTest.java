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

import com.android.testutils.TestUtils;
import com.android.tools.idea.editors.manifest.ManifestUtils;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.mlkit.lightpsi.LightModelClass;
import com.android.tools.idea.project.DefaultModuleSystem;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProviderBuilder;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.SourceProviders;
import com.android.tools.idea.testing.AndroidTestUtils;
import com.google.common.collect.ImmutableList;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileTypes.BinaryFileDecompiler;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.fileTypes.FileTypeExtensionPoint;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class MlkitLightClassTest extends AndroidTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    StudioFlags.ML_MODEL_BINDING.override(true);
    ((DefaultModuleSystem)ProjectSystemUtil.getModuleSystem(myModule)).setMlModelBindingEnabled(true);

    // Pull in tflite model, which has image(i.e. name: image1) as input tensor and labels as output tensor
    myFixture.setTestDataPath(TestUtils.getWorkspaceFile("prebuilts/tools/common/mlkit/testData/models").getPath());

    // Mock TensorImage, TensorBuffer and TensorLabel
    myFixture.addFileToProject("src/org/tensorflow/lite/support/image/TensorImage.java",
                               "package org.tensorflow.lite.support.image; public class TensorImage {}");
    myFixture.addFileToProject("src/org/tensorflow/lite/support/tensorbuffer/TensorBuffer.java",
                               "package org.tensorflow.lite.support.tensorbuffer; public class TensorBuffer {}");
    myFixture.addFileToProject("src/org/tensorflow/lite/support/label/TensorLabel.java",
                               "package org.tensorflow.lite.support.label; public class TensorLabel {}");

    AndroidFacet androidFacet = AndroidFacet.getInstance(myModule);
    VirtualFile manifestFile = ManifestUtils.getMainManifest(androidFacet).getVirtualFile();
    NamedIdeaSourceProvider ideSourceProvider = NamedIdeaSourceProviderBuilder.create("name", manifestFile.getUrl())
      .withMlModelsDirectoryUrls(ImmutableList.of(manifestFile.getParent().getUrl() + "/ml")).build();
    SourceProviders.replaceForTest(androidFacet, myModule, ideSourceProvider);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      StudioFlags.ML_MODEL_BINDING.clearOverride();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testHighlighting_java() {
    VirtualFile mobilenetModelFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "/ml/mobilenet_model.tflite");
    VirtualFile ssdModelFile = myFixture.copyFileToProject("ssd_mobilenet_odt_metadata.tflite", "/ml/ssd_model.tflite");
    VirtualFile styleTransferModelFile =
      myFixture.copyFileToProject("style_transfer_quant_metadata.tflite", "/ml/style_transfer_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, ssdModelFile.getParent());

    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import java.lang.String;\n" +
      "import java.lang.Float;\n" +
      "import java.util.Map;\n" +
      "import org.tensorflow.lite.support.image.TensorImage;\n" +
      "import org.tensorflow.lite.support.label.TensorLabel;\n" +
      "import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;\n" +
      "import java.io.IOException;\n" +
      "import p1.p2.ml.MobilenetModel;\n" +
      "import p1.p2.ml.SsdModel;\n" +
      "import p1.p2.ml.StyleTransferModel;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        try {\n" +
      "            MobilenetModel mobilenetModel = MobilenetModel.newInstance(this);\n" +
      "            TensorImage image = null;\n" +
      "            MobilenetModel.Outputs mobilenetOutputs = mobilenetModel.process(image);\n" +
      "            TensorLabel tensorLabel = mobilenetOutputs.getProbabilityAsTensorLabel();\n" +
      "\n" +
      "            SsdModel ssdModel = SsdModel.newInstance(this);\n" +
      "            SsdModel.Outputs ssdOutputs = ssdModel.process(image);\n" +
      "            TensorBuffer locations = ssdOutputs.getLocationsAsTensorBuffer();\n" +
      "            TensorBuffer classes = ssdOutputs.getClassesAsTensorBuffer();\n" +
      "            TensorBuffer scores = ssdOutputs.getScoresAsTensorBuffer();\n" +
      "            TensorBuffer numberofdetections = ssdOutputs.getNumberofdetectionsAsTensorBuffer();\n" +
      "\n" +
      "            TensorBuffer stylearray = null;\n" +
      "            StyleTransferModel styleTransferModel = StyleTransferModel.newInstance(this);\n" +
      "            StyleTransferModel.Outputs outputs = styleTransferModel.process(image, stylearray);\n" +
      "            TensorImage styledimage = outputs.getStyledImageAsTensorImage();" +
      "        } catch (IOException e) {};\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testHighlighting_modelWithoutMetadata_java() {
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_no_metadata.tflite", "/ml/my_plain_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, modelVirtualFile.getParent());
    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import p1.p2.ml.MyPlainModel;\n" +
      "import java.lang.String;\n" +
      "import java.lang.Float;\n" +
      "import java.util.Map;\n" +
      "import android.graphics.Bitmap;\n" +
      "import java.nio.ByteBuffer;\n" +
      "import java.io.IOException;\n" +
      "import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        try {\n" +
      "            MyPlainModel myModel = MyPlainModel.newInstance(this);\n" +
      "            TensorBuffer tensorBuffer = null;\n" +
      "            MyPlainModel.Outputs output = myModel.process(tensorBuffer);\n" +
      "            TensorBuffer data0 = output.getOutputFeature0AsTensorBuffer();\n" +
      "        } catch (IOException e) {};\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testHighlighting_invokeConstructorThrowError_java() {
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_no_metadata.tflite", "/ml/my_plain_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, modelVirtualFile.getParent());
    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import p1.p2.ml.MyPlainModel;\n" +
      "import java.lang.String;\n" +
      "import java.lang.Float;\n" +
      "import java.util.Map;\n" +
      "import android.graphics.Bitmap;\n" +
      "import java.nio.ByteBuffer;\n" +
      "import java.io.IOException;\n" +
      "import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        try {\n" +
      "            MyPlainModel myModel = new <error descr=\"'MyPlainModel(android.content.Context)' has private access in 'p1.p2.ml.MyPlainModel'\">MyPlainModel</error>();\n" +
      "        } catch (IOException e) {};\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testHighlighting_invokeConstructorWithContextThrowError_java() {
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_no_metadata.tflite", "/ml/my_plain_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, modelVirtualFile.getParent());
    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import p1.p2.ml.MyPlainModel;\n" +
      "import java.lang.String;\n" +
      "import java.lang.Float;\n" +
      "import java.util.Map;\n" +
      "import android.graphics.Bitmap;\n" +
      "import java.nio.ByteBuffer;\n" +
      "import java.io.IOException;\n" +
      "import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        try {\n" +
      "            MyPlainModel myModel = new <error descr=\"'MyPlainModel(android.content.Context)' has private access in 'p1.p2.ml.MyPlainModel'\">MyPlainModel</error>(this);\n" +
      "        } catch (IOException e) {};\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testHighlighting_kotlin() {
    VirtualFile mobilenetModelFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "/ml/mobilenet_model.tflite");
    VirtualFile ssdModelFile = myFixture.copyFileToProject("ssd_mobilenet_odt_metadata.tflite", "/ml/ssd_model.tflite");
    VirtualFile styleTransferModelFile =
      myFixture.copyFileToProject("style_transfer_quant_metadata.tflite", "/ml/style_transfer_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, ssdModelFile.getParent());

    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.kt",
      // language=kotlin
      "package p1.p2\n" +
      "\n" +
      "import android.app.Activity\n" +
      "import android.os.Bundle\n" +
      "import android.util.Log\n" +
      "import org.tensorflow.lite.support.image.TensorImage;\n" +
      "import org.tensorflow.lite.support.label.TensorLabel;\n" +
      "import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;\n" +
      "import p1.p2.ml.MobilenetModel;\n" +
      "import p1.p2.ml.SsdModel;\n" +
      "import p1.p2.ml.StyleTransferModel;\n" +
      "\n" +
      "class MainActivity : Activity() {\n" +
      "    override fun onCreate(savedInstanceState: Bundle?) {\n" +
      "        super.onCreate(savedInstanceState)\n" +
      "        val tensorImage = TensorImage()\n" +
      "        val tensorBuffer = TensorBuffer()\n" +
      "\n" +
      "        val mobilenetModel = MobilenetModel.newInstance(this)\n" +
      "        val mobilenetOutputs = mobilenetModel.process(tensorImage)\n" +
      "        val probability = mobilenetOutputs.probabilityAsTensorLabel\n" +
      "        Log.d(\"TAG\", \"Result\" + probability)\n" +
      "\n" +
      "        val ssdModel = SsdModel.newInstance(this)\n" +
      "        val ssdOutputs = ssdModel.process(tensorImage)\n" +
      "        val locations = ssdOutputs.locationsAsTensorBuffer\n" +
      "        val classes = ssdOutputs.classesAsTensorBuffer\n" +
      "        val scores = ssdOutputs.scoresAsTensorBuffer\n" +
      "        val numberofdetections = ssdOutputs.numberofdetectionsAsTensorBuffer\n" +
      "        Log.d(\"TAG\", \"Result\" + locations + classes + scores + numberofdetections)\n" +
      "\n" +
      "        val styleTransferModel = StyleTransferModel.newInstance(this)\n" +
      "        val styleTransferOutputs = styleTransferModel.process(tensorImage, tensorBuffer)\n" +
      "        val styledImage = styleTransferOutputs.styledImageAsTensorImage\n" +
      "        Log.d(\"TAG\", \"Result\" + styledImage)\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testHighlighting_modelWithoutMetadata_kotlin() {
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_no_metadata.tflite", "/ml/my_plain_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, modelVirtualFile.getParent());

    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.kt",
      // language=kotlin
      "package p1.p2\n" +
      "\n" +
      "import android.app.Activity\n" +
      "import android.os.Bundle\n" +
      "import p1.p2.ml.MyPlainModel\n" +
      "import android.util.Log\n" +
      "import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;\n" +
      "\n" +
      "class MainActivity : Activity() {\n" +
      "    override fun onCreate(savedInstanceState: Bundle?) {\n" +
      "        super.onCreate(savedInstanceState)\n" +
      "        val inputFeature = TensorBuffer()\n" +
      "        val mymodel = MyPlainModel.newInstance(this)\n" +
      "        val outputs = mymodel.process(inputFeature)\n" +
      "        val outputFeature = outputs.outputFeature0AsTensorBuffer\n" +
      "        Log.d(\"TAG\", outputFeature.toString())\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testLightModelClassNavigation() {
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "/ml/my_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, modelVirtualFile.getParent());

    // Below is the workaround to make MockFileDocumentManagerImpl#getDocument return a non-null value for a model file, so the non-null
    // document assertion in TestEditorManagerImpl#doOpenTextEditor could pass.
    BinaryFileTypeDecompilers.getInstance().getPoint().registerExtension(
      new FileTypeExtensionPoint<>(TfliteModelFileType.INSTANCE.getName(), new BinaryFileDecompiler() {
        @NotNull
        @Override
        public CharSequence decompile(@NotNull VirtualFile file) {
          return "Model summary info.";
        }
      }),
      getProject());

    AndroidTestUtils.loadNewFile(myFixture,
                                 "/src/p1/p2/MainActivity.java",
                                 "package p1.p2;\n" +
                                 "\n" +
                                 "import android.app.Activity;\n" +
                                 "import android.os.Bundle;\n" +
                                 "import p1.p2.ml.MyModel;\n" +
                                 "\n" +
                                 "public class MainActivity extends Activity {\n" +
                                 "    @Override\n" +
                                 "    protected void onCreate(Bundle savedInstanceState) {\n" +
                                 "        super.onCreate(savedInstanceState);\n" +
                                 "        My<caret>Model myModel = new MyModel(this);\n" +
                                 "    }\n" +
                                 "}"
    );

    AndroidTestUtils.goToElementAtCaret(myFixture);
    assertThat(FileEditorManagerEx.getInstanceEx(myFixture.getProject()).getCurrentFile()).isEqualTo(modelVirtualFile);
  }

  public void testCompleteProcessMethod() {
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "/ml/my_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, modelVirtualFile.getParent());

    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import p1.p2.ml.MyModel;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        MyModel myModel = MyModel.newInstance(this);\n" +
      "        myModel.pro<caret>;\n" +
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
      "import p1.p2.ml.MyModel;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        MyModel myModel = MyModel.newInstance(this);\n" +
      "        myModel.process();\n" +
      "    }\n" +
      "}");
  }

  public void testCompleteNewInstanceMethod() {
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "/ml/my_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, modelVirtualFile.getParent());

    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import p1.p2.ml.MyModel;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        MyModel.newI<caret>;\n" +
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
      "import p1.p2.ml.MyModel;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        MyModel.newInstance();\n" +
      "    }\n" +
      "}");
  }

  public void testCompleteInnerClass() {
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "/ml/my_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, modelVirtualFile.getParent());

    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
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
    assertThat(elements[0].toString()).isEqualTo("MyModel.newInstance");
    assertThat(elements[1].toString()).isEqualTo("MyModel.Outputs");

    myFixture.getLookup().setCurrentItem(elements[1]);
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    myFixture.checkResult("package p1.p2;\n" +
                          "\n" +
                          "import android.app.Activity;\n" +
                          "import android.os.Bundle;\n" +
                          "\n" +
                          "import p1.p2.ml.MyModel;\n" +
                          "\n" +
                          "public class MainActivity extends Activity {\n" +
                          "    @Override\n" +
                          "    protected void onCreate(Bundle savedInstanceState) {\n" +
                          "        super.onCreate(savedInstanceState);\n" +
                          "        MyModel.Outputs;\n" +
                          "    }\n" +
                          "}");
  }

  public void testCompleteInnerInputClassWithoutOuterClass() {
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "/ml/my_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, modelVirtualFile.getParent());

    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        Outpu<caret>;\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    LookupElement[] elements = myFixture.complete(CompletionType.BASIC);

    // Find position of "Outputs"
    int lookupPosition = -1;
    for (int i = 0; i < elements.length; i++) {
      if (elements[i].toString().equals("Outputs")) {
        lookupPosition = i;
        break;
      }
    }
    assertThat(lookupPosition).isGreaterThan(-1);

    myFixture.getLookup().setCurrentItem(elements[lookupPosition]);
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    myFixture.checkResult("package p1.p2;\n" +
                          "\n" +
                          "import android.app.Activity;\n" +
                          "import android.os.Bundle;\n" +
                          "\n" +
                          "import p1.p2.ml.MyModel;\n" +
                          "\n" +
                          "public class MainActivity extends Activity {\n" +
                          "    @Override\n" +
                          "    protected void onCreate(Bundle savedInstanceState) {\n" +
                          "        super.onCreate(savedInstanceState);\n" +
                          "        MyModel.Outputs;\n" +
                          "    }\n" +
                          "}");
  }


  public void testCompleteModelClass() {
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "/ml/my_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, modelVirtualFile.getParent());

    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
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
                          "\n" +
                          "import p1.p2.ml.MyModel;\n" +
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
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "/ml/my_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, modelVirtualFile.getParent());
    myFixture.copyFileToProject("mobilenet_quant_no_metadata.tflite", "/ml/my_plain_model.tflite");

    MlkitModuleService mlkitService = MlkitModuleService.getInstance(myModule);
    List<LightModelClass> lightClasses = mlkitService.getLightModelClassList();
    List<String> classNameList = lightClasses.stream().map(psiClass -> psiClass.getName()).collect(Collectors.toList());
    assertThat(classNameList).containsExactly("MyModel", "MyPlainModel");
    assertThat(ModuleUtilCore.findModuleForPsiElement(lightClasses.get(0))).isEqualTo(myModule);
  }

  public void testBrokenFiles() {
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "/ml/my_model.tflite");
    VfsTestUtil.createFile(ProjectUtil.guessModuleDir(myModule), "ml/broken.tflite", new byte[]{1, 2, 3});
    PsiTestUtil.addSourceContentToRoots(myModule, modelVirtualFile.getParent());

    assertThat(myFixture.getJavaFacade().findClass("p1.p2.ml.MyModel", GlobalSearchScope.projectScope(getProject())))
      .named("Class for valid model")
      .isNotNull();
    assertThat(myFixture.getJavaFacade().findClass("p1.p2.ml.Broken", GlobalSearchScope.projectScope(getProject())))
      .named("Class for invalid model")
      .isNull();
  }
}
