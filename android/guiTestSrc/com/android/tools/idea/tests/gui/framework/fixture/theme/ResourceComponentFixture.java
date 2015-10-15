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

import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.swing.ui.ClickableLabel;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JPanelFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static org.junit.Assert.assertNotNull;


public class ResourceComponentFixture extends JPanelFixture {

  public ResourceComponentFixture(@NotNull Robot robot, @NotNull ResourceComponent target) {
    super(robot, target);
  }

  @NotNull
  private JButtonFixture getLabel() {
    return new JButtonFixture(robot(), robot().finder().findByName(ResourceComponent.NAME_LABEL, ClickableLabel.class));
  }

  @NotNull
  private SwatchComponentFixture getValueComponent() {
    return SwatchComponentFixture.find(robot());
  }

  @NotNull
  public String getAttributeName() {
    String labelValue = getLabel().text();
    assertNotNull(labelValue);
    return labelValue;
  }

  @NotNull
  public Font getValueFont() {
    Font font = getValueComponent().target().getFont();
    assertNotNull(font);
    return font;
  }

  @Nullable
  public String getValueString() {
    return getValueComponent().getText();
  }
}
