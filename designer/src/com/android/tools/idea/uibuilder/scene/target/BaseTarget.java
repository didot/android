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
package com.android.tools.idea.uibuilder.scene.target;

import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.ScenePicker;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * Base implementation of a Target
 */
public abstract class BaseTarget implements Target {

  protected SceneComponent myComponent;
  @AndroidDpCoordinate protected float myLeft = 0;
  @AndroidDpCoordinate protected float myTop = 0;
  @AndroidDpCoordinate protected float myRight = 0;
  @AndroidDpCoordinate protected float myBottom = 0;
  protected boolean mIsOver = false;

  /////////////////////////////////////////////////////////////////////////////
  //region Accessors
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean canChangeSelection() {
    return true;
  }

  @Override
  public void setComponent(@NotNull SceneComponent component) {
    myComponent = component;
  }

  @Override
  public SceneComponent getComponent() { return myComponent; }

  @Override
  public void setOver(boolean over) {
    if (over != mIsOver && myComponent != null) {
      myComponent.getScene().repaint();
    }
    mIsOver = over;
  }

  @Override
  public void setExpandSize(boolean expand) {
    //do nothing
  }

  @Override
  @AndroidDpCoordinate
  public float getCenterX() {
    return myLeft + (myRight - myLeft) / 2;
  }

  @Override
  @AndroidDpCoordinate
  public float getCenterY() {
    return myTop + (myBottom - myTop) / 2;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public Cursor getMouseCursor() {
    return Cursor.getDefaultCursor();
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation adds a hittable rectangle with the same bounds as this target.
   * </p>
   */
  @Override
  public void addHit(@NotNull SceneContext transform, @NotNull ScenePicker picker) {
    if (!myComponent.getScene().allowsTarget(this)) {
      return;
    }
    picker.addRect(this, 0, transform.getSwingX(myLeft), transform.getSwingY(myTop),
                   transform.getSwingX(myRight), transform.getSwingY(myBottom));
  }

  @Override
  public String getToolTipText() {
    String str = myComponent.getNlComponent().getId();
    if (str == null) {
      str = myComponent.getComponentClassName();
      str = str.substring(str.lastIndexOf('.')+1);
    }
    return str;
  }

  /**
   * Apply live and commit a list of AttributesTransaction
   *
   * @param nlModel the model we operate on
   * @param attributesList the list of AttributesTransaction
   * @param label label used for the write command (will be appearing when using undo/redo)
   */
  public void applyAndCommit(@NotNull NlModel nlModel,
                             @NotNull List<AttributesTransaction> attributesList,
                             @NotNull String label) {
    for (AttributesTransaction attributes : attributesList) {
      attributes.apply();
    }
    Project project = nlModel.getProject();
    XmlFile file = nlModel.getFile();
    WriteCommandAction action = new WriteCommandAction(project, label, file) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        for (AttributesTransaction attributes : attributesList) {
          attributes.commit();
        }
      }
    };
    action.execute();
    myComponent.getScene().needsLayout(Scene.ANIMATED_LAYOUT);
  }

  @Override
  public void setComponentSelection(boolean selection) {

  }

  //endregion
}
