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
package com.android.tools.idea.wizard;

import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.collect.Lists;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.HashSet;
import icons.AndroidIcons;
import org.jetbrains.android.AndroidTestCase;

import javax.swing.*;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Tests for {@link NewModuleWizard}
 */
public class NewModuleWizardTest extends AndroidTestCase {
  public void testTemplateChanged() throws Exception {
    NewModuleWizard wizard = new NewModuleWizard(myModule.getProject());
    TemplateWizardModuleBuilder moduleBuilder = (TemplateWizardModuleBuilder)wizard.myModuleBuilder;

    moduleBuilder.templateChanged(TemplateWizardModuleBuilder.LIB_TEMPLATE_NAME);
    assertTrue(moduleBuilder.myWizardState.getBoolean(TemplateMetadata.ATTR_IS_LIBRARY_MODULE));
    assertFalse(moduleBuilder.myWizardState.getBoolean(TemplateMetadata.ATTR_IS_LAUNCHER));
    assertFalse(moduleBuilder.myWizardState.getBoolean(TemplateMetadata.ATTR_CREATE_ICONS));

    moduleBuilder.templateChanged(TemplateWizardModuleBuilder.APP_TEMPLATE_NAME);
    assertFalse(moduleBuilder.myWizardState.getBoolean(TemplateMetadata.ATTR_IS_LIBRARY_MODULE));
    assertTrue(moduleBuilder.myWizardState.getBoolean(TemplateMetadata.ATTR_IS_LAUNCHER));
    assertTrue(moduleBuilder.myWizardState.getBoolean(TemplateMetadata.ATTR_CREATE_ICONS));

    Disposer.dispose(wizard.getDisposable());
  }

  public void testBuildChooseModuleStep() throws Exception {
    File otherTemplateDir = new File(TemplateManager.getTemplateRootFolder(), Template.CATEGORY_PROJECTS);
    List<String> templateDirFiles = Arrays.asList(otherTemplateDir.list(new FilenameFilter() {
      @Override
      public boolean accept(File file, String name) {
        return !name.startsWith(".");
      }
    }));

    int expectedCount = templateDirFiles.size() - TemplateWizardModuleBuilder.EXCLUDED_TEMPLATES.size() + 2 /* NewApp and NewLib */;

    ArrayList<ModuleWizardStep> steps = Lists.newArrayList();
    TemplateWizardModuleBuilder myModuleBuilder = new TemplateWizardModuleBuilder(null, null, myModule.getProject(),
                                                                                  AndroidIcons.Wizards.NewModuleSidePanel,
                                                                                  steps, getTestRootDisposable(), false) {
      @Override
      public void update() {
        // Do nothing
      }
    };

    ChooseTemplateStep chooseTemplateStep = myModuleBuilder.buildChooseModuleStep(myModule.getProject());

    // Make sure we've got an actual object
    assertNotNull(chooseTemplateStep);

    DefaultListModel templateMetadatas = (DefaultListModel)chooseTemplateStep.myTemplateList.getModel();
    Set<String> templateNames = new HashSet<String>(templateMetadatas.getSize());
    for (Object o : templateMetadatas.toArray()) {
      templateNames.add(o.toString());
    }

    // Make sure we have the right number of choices
    assertEquals(expectedCount, templateMetadatas.getSize());

    // Ensure we're offering NewApplication and NewLibrary
    assertContainsElements(templateNames, TemplateWizardModuleBuilder.APP_TEMPLATE_NAME, TemplateWizardModuleBuilder.LIB_TEMPLATE_NAME);

    // Ensure we're not offering duplicate elements in the list
    assertEquals(templateNames.size(), templateMetadatas.getSize());
  }

  /**
   * Test what paths the wizard takes depending on the first page selection.
   */
  public void testWizardPaths() {
    NewModuleWizard wizard = new NewModuleWizard(myModule.getProject());

    assertInstanceOf(wizard.getCurrentStepObject(), ChooseTemplateStep.class);
    assertEquals(7, wizard.getNextStep(0));

    // Should proceed to import source location
    assertFollowingTheRightPath(wizard, "Import Existing Project", 1, ImportSourceLocationStep.class);

    // Should proceed to Android module creation first step
    assertFollowingTheRightPath(wizard, TemplateWizardModuleBuilder.APP_TEMPLATE_NAME, 7, ChooseAndroidAndJavaSdkStep.class);
  }

  private static void assertFollowingTheRightPath(NewModuleWizard wizard, String templateName, int stepIndex, Class<?> stepClass) {
    wizard.myModuleBuilder.templateChanged(templateName);
    assertEquals(stepIndex, wizard.getNextStep(0));
    wizard.doNextAction();
    assertInstanceOf(wizard.getCurrentStepObject(), stepClass);
    assertEquals(0, wizard.getPreviousStep(stepIndex));
    wizard.doPreviousAction();
    assertInstanceOf(wizard.getCurrentStepObject(), ChooseTemplateStep.class);
  }
}
