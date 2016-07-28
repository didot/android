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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.Wait;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertNotNull;

class MenuFixture {
  @NotNull private final Robot myRobot;
  @NotNull private final IdeFrameImpl myContainer;

  MenuFixture(@NotNull Robot robot, @NotNull IdeFrameImpl container) {
    myRobot = robot;
    myContainer = container;
  }

  /**
   * Invokes an action by menu path
   *
   * @param path the series of menu names, e.g. {@link invokeActionByMenuPath("Build", "Make Project ")}
   */
  void invokeMenuPath(@NotNull String... path) {
    JMenuItem menuItem = findActionMenuItem(path);
    assertWithMessage("Menu path \"" + Joiner.on(" -> ").join(path) + "\" is not enabled").that(menuItem.isEnabled()).isTrue();
    myRobot.click(menuItem);
  }

  @NotNull
  private JMenuItem findActionMenuItem(@NotNull String... path) {
    myRobot.waitForIdle(); // UI events can trigger modifications of the menu contents
    assertThat(path).isNotEmpty();
    int segmentCount = path.length;

    // We keep the list of previously found pop-up menus, so we don't look for menu items in the same pop-up more than once.
    List<JPopupMenu> previouslyFoundPopups = Lists.newArrayList();

    Container root = myContainer;
    for (int i = 0; i < segmentCount; i++) {
      final String segment = path[i];
      assertNotNull(root);
      JMenuItem found = myRobot.finder().find(root, Matchers.byText(JMenuItem.class, segment));
      if (root instanceof JPopupMenu) {
        previouslyFoundPopups.add((JPopupMenu)root);
      }
      if (i < segmentCount - 1) {
        myRobot.click(found);
        List<JPopupMenu> showingPopupMenus = findShowingPopupMenus(i + 1);
        showingPopupMenus.removeAll(previouslyFoundPopups);
        assertThat(showingPopupMenus).hasSize(1);
        root = showingPopupMenus.get(0);
        continue;
      }
      return found;
    }
    throw new AssertionError("Menu item with path " + Arrays.toString(path) + " should have been found already");
  }

  @NotNull
  private List<JPopupMenu> findShowingPopupMenus(final int expectedCount) {
    final Ref<List<JPopupMenu>> ref = new Ref<>();
    Wait.seconds(30).expecting(expectedCount + " JPopupMenus to show up")
      .until(() -> {
        List<JPopupMenu> popupMenus = Lists.newArrayList(myRobot.finder().findAll(new GenericTypeMatcher<JPopupMenu>(JPopupMenu.class) {
          @Override
          protected boolean isMatching(@NotNull JPopupMenu popupMenu) {
            return popupMenu.isShowing();
          }
        }));
        boolean allFound = popupMenus.size() == expectedCount;
        if (allFound) {
          ref.set(popupMenus);
        }
        return allFound;
      });
    List<JPopupMenu> popupMenus = ref.get();
    assertThat(popupMenus).hasSize(expectedCount);
    return popupMenus;
  }

  /**
   * Returns whether a menu path is enabled
   *
   * @param path the series of menu names, e.g. {@link isMenuPathEnabled("Build", "Make Project ")}
   */
  public boolean isMenuPathEnabled(String... path) {
    boolean isEnabled = findActionMenuItem(path).isEnabled();
    myRobot.pressAndReleaseKey(KeyEvent.VK_ESCAPE); // Close the menu before continuing.

    return isEnabled;
  }
}
