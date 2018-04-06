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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.api.android.SourceSetModel;
import com.android.tools.idea.gradle.dsl.api.android.sourceSets.SourceDirectoryModel;
import com.android.tools.idea.gradle.dsl.api.android.sourceSets.SourceFileModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.android.sourceSets.SourceDirectoryModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.sourceSets.SourceFileModelImpl;
import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValueImpl;
import com.android.tools.idea.gradle.dsl.parser.android.SourceSetDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceFileDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SourceSetModelImpl extends GradleDslBlockModel implements SourceSetModel {
  @NonNls private static final String AIDL = "aidl";
  @NonNls private static final String ASSETS = "assets";
  @NonNls private static final String JAVA = "java";
  @NonNls private static final String JNI = "jni";
  @NonNls private static final String JNI_LIBS = "jniLibs";
  @NonNls private static final String MANIFEST = "manifest";
  @NonNls private static final String RENDERSCRIPT = "renderscript";
  @NonNls private static final String RES = "res";
  @NonNls private static final String RESOURCES = "resources";
  @NonNls private static final String ROOT = "root";
  @NonNls private static final String SET_ROOT = "setRoot";

  public SourceSetModelImpl(@NotNull SourceSetDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public String name() {
    return myDslElement.getName();
  }

  @Override
  @NotNull
  public GradleNullableValue<String> root() {
    GradleDslSimpleExpression rootElement = myDslElement.getPropertyElement(ImmutableList.of(ROOT, SET_ROOT), GradleDslSimpleExpression.class);

    if (rootElement == null) {
      return new GradleNullableValueImpl<>(myDslElement, null);
    }

    String value = null;
    if (rootElement instanceof GradleDslMethodCall) {
      List<GradleDslElement> arguments = ((GradleDslMethodCall)rootElement).getArguments();
      if (!arguments.isEmpty()) {
        GradleDslElement pathArgument = arguments.get(0);
        if (pathArgument instanceof GradleDslSimpleExpression) {
          value = ((GradleDslSimpleExpression)pathArgument).getValue(String.class);
        }
      }
    }
    else {
      value = rootElement.getValue(String.class);
    }

    return new GradleNullableValueImpl<>(rootElement, value);
  }

  @Override
  @NotNull
  public SourceSetModel setRoot(@NotNull String root) {
    GradleDslSimpleExpression rootElement = myDslElement.getPropertyElement(ImmutableList.of(ROOT, SET_ROOT), GradleDslSimpleExpression.class);
    if (rootElement == null) {
      myDslElement.setNewLiteral(ROOT, root);
      return this;
    }

    if (rootElement instanceof GradleDslMethodCall) {
      List<GradleDslElement> arguments = ((GradleDslMethodCall)rootElement).getArguments();
      if (!arguments.isEmpty()) {
        GradleDslElement pathArgument = arguments.get(0);
        if (pathArgument instanceof GradleDslSimpleExpression) {
          ((GradleDslSimpleExpression)pathArgument).setValue(root);
          return this;
        }
      }
    }

    rootElement.setValue(root);
    return this;
  }

  @Override
  @NotNull
  public SourceSetModel removeRoot() {
    myDslElement.removeProperty(ROOT);
    myDslElement.removeProperty(SET_ROOT);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel aidl() {
    SourceDirectoryDslElement aidl = myDslElement.getPropertyElement(AIDL, SourceDirectoryDslElement.class);
    if (aidl == null) {
      GradleNameElement name = GradleNameElement.create(AIDL);
      aidl = new SourceDirectoryDslElement(myDslElement, name);
      myDslElement.setNewElement(aidl);
    }
    return new SourceDirectoryModelImpl(aidl);
  }

  @Override
  @NotNull
  public SourceSetModel removeAidl() {
    myDslElement.removeProperty(AIDL);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel assets() {
    SourceDirectoryDslElement assets = myDslElement.getPropertyElement(ASSETS, SourceDirectoryDslElement.class);
    if (assets == null) {
      GradleNameElement name = GradleNameElement.create(ASSETS);
      assets = new SourceDirectoryDslElement(myDslElement, name);
      myDslElement.setNewElement(assets);
    }
    return new SourceDirectoryModelImpl(assets);
  }

  @Override
  @NotNull
  public SourceSetModel removeAssets() {
    myDslElement.removeProperty(ASSETS);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel java() {
    SourceDirectoryDslElement java = myDslElement.getPropertyElement(JAVA, SourceDirectoryDslElement.class);
    if (java == null) {
      GradleNameElement name = GradleNameElement.create(JAVA);
      java = new SourceDirectoryDslElement(myDslElement, name);
      myDslElement.setNewElement(java);
    }
    return new SourceDirectoryModelImpl(java);
  }

  @Override
  @NotNull
  public SourceSetModel removeJava() {
    myDslElement.removeProperty(JAVA);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel jni() {
    SourceDirectoryDslElement jni = myDslElement.getPropertyElement(JNI, SourceDirectoryDslElement.class);
    if (jni == null) {
      GradleNameElement name = GradleNameElement.create(JNI);
      jni = new SourceDirectoryDslElement(myDslElement, name);
      myDslElement.setNewElement(jni);
    }
    return new SourceDirectoryModelImpl(jni);
  }

  @Override
  @NotNull
  public SourceSetModel removeJni() {
    myDslElement.removeProperty(JNI);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel jniLibs() {
    SourceDirectoryDslElement jniLibs = myDslElement.getPropertyElement(JNI_LIBS, SourceDirectoryDslElement.class);
    if (jniLibs == null) {
      GradleNameElement name = GradleNameElement.create(JNI_LIBS);
      jniLibs = new SourceDirectoryDslElement(myDslElement, name);
      myDslElement.setNewElement(jniLibs);
    }
    return new SourceDirectoryModelImpl(jniLibs);
  }

  @Override
  @NotNull
  public SourceSetModel removeJniLibs() {
    myDslElement.removeProperty(JNI_LIBS);
    return this;
  }

  @Override
  @NotNull
  public SourceFileModel manifest() {
    SourceFileDslElement manifest = myDslElement.getPropertyElement(MANIFEST, SourceFileDslElement.class);
    if (manifest == null) {
      GradleNameElement name = GradleNameElement.create(MANIFEST);
      manifest = new SourceFileDslElement(myDslElement, name);
      myDslElement.setNewElement(manifest);
    }
    return new SourceFileModelImpl(manifest);
  }

  @Override
  @NotNull
  public SourceSetModel removeManifest() {
    myDslElement.removeProperty(MANIFEST);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel renderscript() {
    SourceDirectoryDslElement renderscript = myDslElement.getPropertyElement(RENDERSCRIPT, SourceDirectoryDslElement.class);
    if (renderscript == null) {
      GradleNameElement name = GradleNameElement.create(RENDERSCRIPT);
      renderscript = new SourceDirectoryDslElement(myDslElement, name);
      myDslElement.setNewElement(renderscript);
    }
    return new SourceDirectoryModelImpl(renderscript);
  }

  @Override
  @NotNull
  public SourceSetModel removeRenderscript() {
    myDslElement.removeProperty(RENDERSCRIPT);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel res() {
    SourceDirectoryDslElement res = myDslElement.getPropertyElement(RES, SourceDirectoryDslElement.class);
    if (res == null) {
      GradleNameElement name = GradleNameElement.create(RES);
      res = new SourceDirectoryDslElement(myDslElement, name);
      myDslElement.setNewElement(res);
    }
    return new SourceDirectoryModelImpl(res);
  }

  @Override
  @NotNull
  public SourceSetModel removeRes() {
    myDslElement.removeProperty(RES);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel resources() {
    SourceDirectoryDslElement resources = myDslElement.getPropertyElement(RESOURCES, SourceDirectoryDslElement.class);
    if (resources == null) {
      GradleNameElement name = GradleNameElement.create(RESOURCES);
      resources = new SourceDirectoryDslElement(myDslElement, name);
      myDslElement.setNewElement(resources);
    }
    return new SourceDirectoryModelImpl(resources);
  }

  @Override
  @NotNull
  public SourceSetModel removeResources() {
    myDslElement.removeProperty(RESOURCES);
    return this;
  }
}
