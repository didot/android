/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.npw;

import com.android.tools.adtui.ASGallery;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

public class NewProjectWizardFixture extends AbstractWizardFixture<NewProjectWizardFixture> {
  @NotNull
  public static NewProjectWizardFixture find(@NotNull Robot robot) {
    JDialog dialog = GuiTests.waitUntilShowing(robot, Matchers.byTitle(JDialog.class, "Create New Project"));
    return new NewProjectWizardFixture(robot, dialog);
  }

  private NewProjectWizardFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(NewProjectWizardFixture.class, robot, target);
  }

  @NotNull
  public ConfigureAndroidProjectStepFixture<NewProjectWizardFixture> getConfigureAndroidProjectStep() {
    JRootPane rootPane = findStepWithTitle("Create Android Project");
    return new ConfigureAndroidProjectStepFixture<>(this, rootPane);
  }

  @NotNull
  public ConfigureFormFactorStepFixture<NewProjectWizardFixture> getConfigureFormFactorStep() {
    JRootPane rootPane = findStepWithTitle("Target Android Devices", 30);
    return new ConfigureFormFactorStepFixture<>(this, rootPane);
  }

  public ConfigureInstantModuleStepFixture<NewProjectWizardFixture> getConfigureInstantModuleStep() {
    JRootPane rootPane = findStepWithTitle("Configure the Instant App Feature module");
    return new ConfigureInstantModuleStepFixture<>(this, rootPane);
  }

  @NotNull
  public ConfigureCppStepFixture<NewProjectWizardFixture> getConfigureCppStepFixture() {
    JRootPane rootPane = findStepWithTitle("Customize C++ Support", 30);
    return new ConfigureCppStepFixture<>(this, rootPane);
  }

  public NewProjectWizardFixture chooseActivity(@NotNull String activity) {
    JListFixture listFixture = new JListFixture(robot(), robot().finder().findByType(target(), ASGallery.class));
    listFixture.replaceCellReader((jList, index) -> String.valueOf(jList.getModel().getElementAt(index)));
    listFixture.clickItem(activity);
    return this;
  }

  @NotNull
  public ChooseOptionsForNewFileStepFixture<NewProjectWizardFixture> getChooseOptionsForNewFileStep() {
    JRootPane rootPane = findStepWithTitle("Configure Activity");
    return new ChooseOptionsForNewFileStepFixture<>(this, rootPane);
  }

  @NotNull
  public NewProjectWizardFixture clickFinish() {
    List<Project> previouslyOpenProjects = newArrayList(ProjectManager.getInstance().getOpenProjects());
    super.clickFinish(Wait.seconds(10));

    List<Project> newOpenProjects = newArrayList();
    Wait.seconds(5).expecting("Project to be open")
      .until(() -> {
        newOpenProjects.addAll(newArrayList(ProjectManager.getInstance().getOpenProjects()));
        newOpenProjects.removeAll(previouslyOpenProjects);
        return !newOpenProjects.isEmpty();
      });

    GuiTests.waitForProjectIndexingToFinish(newOpenProjects.get(0));

    return myself();
  }
}
