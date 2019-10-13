/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneUtils;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionAttributes;
import java.util.List;
import javax.swing.Timer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This provides the WidgetConstraintPanel when working with Constraint objects in MotionEditor
 */
public class MotionConstraintPanel extends WidgetConstraintPanel {
  NlComponent myComponent;

  public MotionConstraintPanel(@NotNull List<NlComponent> components) {
    super(components);
    myComponent = components.get(0);
    MotionAttributes attr = MotionSceneUtils.getAttributes(myComponent);
    mTitle.setText("Constraints from " + attr.getLayoutSource());
  }

  @Override
  protected WidgetConstraintModel getWidgetModel(Runnable modelUpdate) {
    return new MotionWidgetConstraintModel(modelUpdate);
  }

  //////////////////////////////////////////////////////////////////////////////////////
  class MotionWidgetConstraintModel extends WidgetConstraintModel {

    MTag.TagWriter mTagWriter;
    private Timer myTimer = new Timer(DELAY_BEFORE_COMMIT, (c) -> {
      commit();
    });

    public MotionWidgetConstraintModel(@NotNull Runnable modelUpdateCallback) {
      super(modelUpdateCallback);
    }

    @Override
    public void removeAttributes(final NlComponent component, @NotNull String namespace, @NotNull String attribute) {
      setAttribute(component, namespace, attribute, null);
      commit();
    }

    @Override
    protected String getValue(NlComponent component, String namespace, @NotNull String attribute) {
      if (component == null) {
        return null;
      }
      MotionAttributes attr = MotionSceneUtils.getAttributes(component);
      MotionAttributes.DefinedAttribute v = attr.getAttrMap().get(attribute);
      if (v == null) {
        return null;
      }
      return v.getValue();
    }

    @Override
    protected void setAttribute(NlComponent component, String namespace, @NotNull String attribute, @Nullable String value) {
      if (component == null) {
        return;
      }
      mTagWriter = MotionSceneUtils.getTagWriter(component);
      mTagWriter.setAttribute(namespace, attribute, value);
      myTimer.setRepeats(false);
      myTimer.restart();
    }

    @Override
    public void commit() {
      if (mTagWriter == null) {
        return;
      }
      mTagWriter.commit("Mod");
      mTagWriter = null;
    }
  }
}
