/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder;

import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.fixtures.ComponentDescriptor;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.fixtures.SurfaceFixture;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintModel;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

import java.io.File;

public abstract class LayoutTestCase extends AndroidTestCase {
  public LayoutTestCase() {
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.setTestDataPath(getTestDataPath());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      ConstraintModel.clearCache();
    }
    finally {
      super.tearDown();
    }
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static String getTestDataPath() {
    return getDesignerPluginHome() + "/testData";
  }

  public static String getDesignerPluginHome() {
    // Now that the Android plugin is kept in a separate place, we need to look in
    // a relative position instead
    String adtPath = PathManager.getHomePath() + "/../adt/idea/designer";
    if (new File(adtPath).exists()) {
      return adtPath;
    }
    return AndroidTestBase.getAndroidPluginHome();
  }

  protected ModelBuilder model(@NotNull String name, @NotNull ComponentDescriptor root) {
    return new ModelBuilder(myFacet, myFixture, name, root);
  }

  protected ComponentDescriptor component(@NotNull String tag) {
    return new ComponentDescriptor(tag);
  }

  protected SurfaceFixture surface() {
    return new SurfaceFixture();
  }

  // Format the XML using AndroidStudio formatting
  protected void format(@NotNull XmlFile xmlFile) {
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      CodeStyleManager.getInstance(getProject()).reformat(xmlFile);
    });
  }


  @NotNull
  protected ViewEditor editor(ScreenView screenView) {
    ViewEditor editor = Mockito.mock(ViewEditor.class);
    NlModel model = screenView.getModel();
    Mockito.when(editor.getModel()).thenReturn(model);
    Mockito.when(editor.dpToPx(Mockito.anyInt())).thenAnswer(i -> Coordinates.dpToPx(screenView, (Integer)i.getArguments()[0]));
    Mockito.when(editor.pxToDp(Mockito.anyInt())).thenAnswer(i -> Coordinates.pxToDp(screenView, (Integer)i.getArguments()[0]));

    return editor;
  }

}
