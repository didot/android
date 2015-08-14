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
package com.android.tools.idea.tests.gui.framework.fixture.theme;

import com.android.tools.idea.editors.theme.ThemeEditorTable;
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import org.fest.swing.annotation.RunsInCurrentThread;
import org.fest.swing.core.Robot;
import org.fest.swing.data.TableCell;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JTableFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;
import java.awt.Font;

import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.assertTrue;

public class ThemeEditorTableFixture extends JTableFixture {
  private ThemeEditorTableFixture(Robot robot, ThemeEditorTable target) {
    super(robot, target);
  }

  @NotNull
  public static ThemeEditorTableFixture find(@NotNull Robot robot) {
    return new ThemeEditorTableFixture(robot, robot.finder().findByType(ThemeEditorTable.class));
  }

  @Nullable
  public String attributeNameAt(@NotNull final TableCell cell) {
    return execute(new GuiQuery<String>() {
      @Override
      protected String executeInEDT() throws Throwable {
        Component renderer = rendererComponentAt(cell);
        if (!(renderer instanceof ResourceComponent)) {
          return null;
        }

        ResourceComponentFixture resourceComponent = new ResourceComponentFixture(robot(), (ResourceComponent)renderer);
        return resourceComponent.getAttributeName();
      }
    });
  }

  @Nullable
  public Font valueFontAt(@NotNull final TableCell cell) {
    return execute(new GuiQuery<Font>() {
      @Override
      protected Font executeInEDT() throws Throwable {
        Component renderer = rendererComponentAt(cell);
        assertTrue(renderer instanceof ResourceComponent);
        ResourceComponentFixture resourceComponent = new ResourceComponentFixture(robot(), (ResourceComponent)renderer);
        return resourceComponent.getValueFont();
      }
    });
  }

  @Override
  @Nullable
  public String valueAt(@NotNull final TableCell cell) {
    return execute(new GuiQuery<String>() {
      @Override
      protected String executeInEDT() throws Throwable {
        Component renderer = rendererComponentAt(cell);
        if (!(renderer instanceof ResourceComponent)) {
          return ThemeEditorTableFixture.super.valueAt(cell);
        }

        ResourceComponentFixture resourceComponent = new ResourceComponentFixture(robot(), (ResourceComponent)renderer);
        return resourceComponent.getValueString();
      }
    });
  }

  @Nullable
  public String colorValueAt(@NotNull final TableCell cell) {
    return execute(new GuiQuery<String>() {
      @Override
      protected String executeInEDT() throws Throwable {
        Component renderer = rendererComponentAt(cell);
        assertTrue(renderer instanceof ResourceComponent);
        ResourceComponentFixture resourceComponent = new ResourceComponentFixture(robot(), (ResourceComponent)renderer);
        return resourceComponent.getColorValue();
      }
    });
  }

  @RunsInCurrentThread
  @Nullable
  private Component rendererComponentAt(@NotNull final TableCell cell) {
    return target().prepareRenderer(target().getCellRenderer(cell.row, cell.column), cell.row, cell.column);
  }
}
