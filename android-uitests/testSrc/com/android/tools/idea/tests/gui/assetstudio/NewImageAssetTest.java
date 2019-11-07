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
package com.android.tools.idea.tests.gui.assetstudio;

import static com.google.common.truth.Truth.assertThat;

import com.android.testutils.filesystemdiff.Action;
import com.android.testutils.filesystemdiff.CreateDirectoryAction;
import com.android.testutils.filesystemdiff.CreateFileAction;
import com.android.testutils.filesystemdiff.FileSystemEntry;
import com.android.testutils.filesystemdiff.Script;
import com.android.testutils.filesystemdiff.TreeBuilder;
import com.android.testutils.filesystemdiff.TreeDifferenceEngine;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.AssetStudioWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.NewImageAssetStepFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class NewImageAssetTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testAdaptiveIconsPreviewPanelContents() throws Exception {
    AssetStudioWizardFixture wizard = guiTest.importSimpleApplication()
      .getProjectView()
      .selectAndroidPane()
      .clickPath("app")
      .openFromMenu(AssetStudioWizardFixture::find, "File", "New", "Image Asset");

    NewImageAssetStepFixture<AssetStudioWizardFixture> step = wizard.getImageAssetStep();
    step.selectIconType("Launcher Icons (Adaptive and Legacy)");
    assertThat(step.getPreviewPanelCount()).isEqualTo(1);
    assertThat(step.getPreviewPanelIconNames(0)).containsExactly(
      "Circle", "Squircle", "Rounded Square", "Square", "Full Bleed Layers", "Legacy Icon", "Round Icon", "Google Play Store")
      .inOrder();
    wizard.clickCancel();
  }

  @Test
  public void testNotificationImageCount() throws Exception {
    NewImageAssetStepFixture<AssetStudioWizardFixture> step = guiTest.importSimpleApplication()
      .getProjectView()
      .selectAndroidPane()
      .clickPath("app")
      .openFromMenu(AssetStudioWizardFixture::find, "File", "New", "Image Asset")
      .getImageAssetStep();

    Path projectDir = guiTest.getProjectPath().toPath();
    FileSystemEntry original = TreeBuilder.buildFromFileSystem(projectDir);

    step.selectIconType("Notification Icons");
    assertThat(step.getPreviewPanelCount()).isEqualTo(1);
    assertThat(step.getPreviewPanelIconNames(0)).containsExactly("anydpi", "xxhdpi", "xhdpi", "hdpi", "mdpi").inOrder();
    step.wizard()
      .clickNext()
      .clickFinish();

    FileSystemEntry changed = TreeBuilder.buildFromFileSystem(projectDir);

    Path filterPath = projectDir.resolve("app/src");
    List<String> newFiles =
        getNewFiles(projectDir, TreeDifferenceEngine.computeEditScript(original, changed), path -> path.startsWith(filterPath));
    assertThat(newFiles).containsExactly("app/src/main/res/drawable-mdpi/ic_stat_name.png",
                                         "app/src/main/res/drawable-hdpi/ic_stat_name.png",
                                         "app/src/main/res/drawable-xhdpi/ic_stat_name.png",
                                         "app/src/main/res/drawable-xxhdpi/ic_stat_name.png",
                                         "app/src/main/res/drawable-anydpi-v24/ic_stat_name.xml");
  }

  @NotNull
  public static List<String> getNewFiles(@NotNull Path root, @NotNull Script script, @NotNull Predicate<Path> filter) {
    List<String> newFiles = new ArrayList<>();
    List<Action> actions = script.getActions();
    for (Action action : actions) {
      if (action instanceof CreateFileAction) {
        addRelativePathConditionally(action.getSourceEntry().getPath(), root, filter, newFiles);
      }
      if (action instanceof CreateDirectoryAction) {
        try (Stream<Path> stream = Files.walk(action.getSourceEntry().getPath())) {
          stream.filter(Files::isRegularFile)
              .forEach(path -> addRelativePathConditionally(path, root, filter, newFiles));
        }
        catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      }
    }
    return newFiles;
  }

  private static void addRelativePathConditionally(
      @NotNull Path pathToAdd, @NotNull Path root, @NotNull Predicate<Path> condition, @NotNull Collection<String> result) {
    if (condition.test(pathToAdd)) {
      result.add(toString(root, pathToAdd));
    }
  }

  @NotNull
  private static String toString(@NotNull Path root, @NotNull Path path) {
    return root.relativize(path).toString().replace('\\', '/');
  }
}
