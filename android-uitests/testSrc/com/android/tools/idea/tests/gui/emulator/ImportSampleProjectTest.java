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
package com.android.tools.idea.tests.gui.emulator;

import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.BrowseSamplesWizardFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@RunWith(GuiTestRemoteRunner.class)
public class ImportSampleProjectTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);

  private final EmulatorTestRule emulator = new EmulatorTestRule(false);
  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(new DeleteAvdsRule())
    .around(emulator);

  private static final Pattern RUN_OUTPUT = Pattern.compile(".*Connected to process.*", Pattern.DOTALL);

  /**
   * To verify that importing a sample project and deploying on test device.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: ae1223a3-b42d-4c8f-8837-5c6f7e8c583a
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open Android Studio
   *   2. Import Background/Job Scheduler from sample projects
   *   3. Create an emulator
   *   4. Deploy the project on the emulator
   *   Verify:
   *   1. The sample project is built successfully and deployed on the emulator.
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.SANITY)
  @Test
  public void importSampleProject() throws Exception {
    BrowseSamplesWizardFixture samplesWizard = guiTest.welcomeFrame()
                                                      .importCodeSample();
    samplesWizard.selectSample("Ui/Done Bar")
                 .clickNext()
                 .clickFinish();

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    ideFrameFixture
      .waitForGradleProjectSyncToFail(Wait.seconds(20))
      .getEditor()
      .open("Application/build.gradle")
      .select("buildToolsVersion \"(.*)\"")
      .enterText("27.0.3")
      .invokeAction(EditorFixture.EditorAction.SAVE);

    ideFrameFixture.requestProjectSync();

    GuiTests.findAndClickButtonWhenEnabled(
      ideFrameFixture.waitForDialog("Android Gradle Plugin Update Recommended", 120),
      "Update");

    ideFrameFixture.waitForGradleProjectSyncToFinish(Wait.seconds(120));

    String avdName = EmulatorGenerator.ensureDefaultAvdIsCreated(ideFrameFixture.invokeAvdManager());

    String appName = "Application";
    ideFrameFixture
      .runApp(appName)
      .selectDevice(avdName)
      .clickOk();

    // Wait for background tasks to finish before requesting Run Tool Window. Otherwise Run Tool Window won't activate.
    guiTest.waitForBackgroundTasks();

    ideFrameFixture.getRunToolWindow().findContent(appName)
                   .waitForOutput(new PatternTextMatcher(RUN_OUTPUT), EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS);
  }
}
