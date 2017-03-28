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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.android.annotations.Nullable;
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.property.NlPropertiesPanel;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.components.JBLabel;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.WaitTimedOutError;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;
import static com.google.common.truth.Truth.assertThat;

/**
 * Fixture wrapping the component inspector
 */
public class NlPropertyInspectorFixture extends ComponentFixture<NlPropertyInspectorFixture, Component> {
  private final NlPropertiesPanel myPanel;

  public NlPropertyInspectorFixture(@NotNull Robot robot, @NotNull NlPropertiesPanel panel) {
    super(NlPropertyInspectorFixture.class, robot, panel);
    myPanel = panel;
  }

  public static NlPropertiesPanel create(@NotNull Robot robot) {
    return waitUntilFound(robot, null, Matchers.byType(NlPropertiesPanel.class));
  }

  @Nullable
  public NlPropertyFixture findProperty(@NotNull String name) {
    Component component = findPropertyComponent(name, null);
    return component != null ? new NlPropertyFixture(robot(), (JPanel)component) : null;
  }

  @NotNull
  public NlPropertyInspectorFixture adjustIdeFrameHeightFor(int visiblePropertyCount, @NotNull String propertyName) {
    Component component = findPropertyComponent(propertyName, null);
    assertThat(component).isNotNull();
    int height = component.getHeight();
    Container previousParent = null;
    Container parent = component.getParent();
    int adjustment = 0;
    while (parent != null) {
      if (adjustment == 0 && parent instanceof JScrollPane) {
        adjustment = visiblePropertyCount * height - parent.getHeight();
      }
      previousParent = parent;
      parent = parent.getParent();
    }
    assertThat(previousParent).isNotNull();
    Dimension size = previousParent.getSize();
    size.height += adjustment;
    previousParent.setSize(size);
    return this;
  }

  @NotNull
  public NlPropertyInspectorFixture focusAndWaitForFocusGainInProperty(@NotNull String name, @Nullable Icon icon) {
    Component component = findPropertyComponent(name, icon);
    assertThat(component).isNotNull();
    driver().focusAndWaitForFocusGain(component);
    return this;
  }

  @NotNull
  public NlPropertyInspectorFixture tab() {
    return pressKeyInUnknownProperty(KeyEvent.VK_TAB, 0);
  }

  @NotNull
  public NlPropertyInspectorFixture tabBack() {
    return pressKeyInUnknownProperty(KeyEvent.VK_TAB, InputEvent.SHIFT_MASK);
  }

  @NotNull
  public NlPropertyInspectorFixture pressKeyInUnknownProperty(int keyCode, int... modifiers) {
    Component component = FocusManager.getCurrentManager().getFocusOwner();
    assertThat(component).isNotNull();
    assertThat(SwingUtilities.isDescendingFrom(component, myPanel)).isTrue();
    driver().pressAndReleaseKey(component, keyCode, modifiers);
    IdeFocusManager.findInstance().doWhenFocusSettlesDown(() -> {});
    return this;
  }

  @NotNull
  public NlPropertyInspectorFixture assertPropertyShowing(@NotNull String name, @Nullable Icon icon) {
    assertThat(isPropertyShowing(name, icon)).named("Property is Visible to user: " + name).isTrue();
    return this;
  }

  @NotNull
  public NlPropertyInspectorFixture assertPropertyNotShowing(@NotNull String name, @Nullable Icon icon) {
    assertThat(isPropertyShowing(name, icon)).named("Property is Visible to user: " + name).isFalse();
    return this;
  }

  @NotNull
  public NlPropertyInspectorFixture assertFocusInProperty(@NotNull String name, @Nullable Icon icon) {
    Component propertyComponent = findPropertyComponent(name, icon);
    Component focusComponent = FocusManager.getCurrentManager().getFocusOwner();
    assertThat(propertyComponent != null).named("property: " + name + " found").isTrue();
    assertThat(SwingUtilities.isDescendingFrom(focusComponent, propertyComponent)).named("property: " + name + " has focus").isTrue();
    return this;
  }

  public boolean isPropertyShowing(@NotNull String name, @Nullable Icon icon) {
    Component component = findPropertyComponent(name, icon);
    if (component == null) {
      return false;
    }
    Rectangle rect = component.getBounds();
    Container parent = component.getParent();
    while (parent != null) {
      Rectangle bounds = parent.getBounds();
      if (rect.y > bounds.height || rect.y + rect.height < 0 ||
          rect.x > bounds.width || rect.x + rect.width < 0) {
        return false;
      }
      rect.x += bounds.x;
      rect.y += bounds.y;
      parent = parent.getParent();
    }
    return true;
  }

  @Nullable
  private Component findPropertyComponent(@NotNull String name, @Nullable Icon icon) {
    try {
      JBLabel label = waitUntilFound(robot(), myPanel, new GenericTypeMatcher<JBLabel>(JBLabel.class) {
        @Override
        protected boolean isMatching(@NotNull JBLabel label) {
          return name.equals(label.getText()) && label.getIcon() == icon;
        }
      });

      Container parent = label.getParent();
      Component[] components = parent.getComponents();
      for (int i = 0; i < components.length; i++) {
        if (label == components[i]) {
          return components[i + 1];
        }
      }
      return null;
    }
    catch (WaitTimedOutError ex) {
      return null;
    }
  }
}