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

import com.android.SdkConstants;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.base.Objects;
import com.intellij.psi.xml.XmlTag;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.ComponentDriver;
import org.fest.swing.fixture.JMenuItemFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;

/**
 * Represents a view in the layout editor
 */
public class NlComponentFixture {
  private final Robot myRobot;
  private final NlComponent myComponent;
  private final DesignSurface mySurface;

  public NlComponentFixture(@NotNull Robot robot,
                            @NotNull NlComponent component,
                            @NotNull DesignSurface surface) {
    myRobot = robot;
    myComponent = component;
    mySurface = surface;
  }

  /** Returns the center point in panel coordinates */
  @NotNull
  private Point getMidPoint() {
    ScreenView screenView = mySurface.getCurrentScreenView();
    int midX = Coordinates.getSwingX(screenView, myComponent.x + myComponent.w / 2);
    int midY = Coordinates.getSwingY(screenView, myComponent.y + myComponent.h / 2);
    return new Point(midX, midY);
  }

  public String getTextAttribute() {
    return myComponent.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT);
  }

  public Object getViewObject() {
    return myComponent.viewInfo.getViewObject();
  }

  public String getText() {
    try {
      return (String)getViewObject().getClass().getMethod("getText").invoke(getViewObject());
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /** Click in the middle of the view (typically selects it) */
  public void click() {
    new ComponentDriver(myRobot).click(mySurface, getMidPoint());
  }

  /** Right clicks s in the middle of the view */
  public void rightClick() {
    // Can't use ComponentDriver -- need to both set button and where
    myRobot.click(mySurface, getMidPoint(), MouseButton.RIGHT_BUTTON, 1);
  }

  public void invokeContextMenuAction(@NotNull String actionLabel) {
    rightClick();
    new JMenuItemFixture(myRobot, GuiTests.waitUntilShowing(myRobot, Matchers.byText(JMenuItem.class, actionLabel))).click();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    NlComponentFixture that = (NlComponentFixture)o;
    if (!myComponent.equals(that.myComponent)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return myComponent.hashCode();
  }

  @Override
  public String toString() {
    return describe(myComponent);
  }

  @NotNull
  private static String describe(@NotNull NlComponent root) {
    return Objects.toStringHelper(root).omitNullValues()
      .add("tag", describe(root.getTag()))
      .add("id", root.getId())
      .add("bounds", "[" + root.x + "," + root.y + ":" + root.w + "x" + root.h)
      .toString();
  }

  @NotNull
  private static String describe(@Nullable XmlTag tag) {
    if (tag == null) {
      return "";
    } else {
      return '<' + tag.getName() + '>';
    }
  }
}
