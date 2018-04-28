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
package com.android.tools.idea.uibuilder.handlers.motion;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.handlers.motion.attributeEditor.AttributeTagPanel;
import com.android.tools.idea.uibuilder.handlers.motion.attributeEditor.OnSwipePanel;
import com.android.tools.idea.uibuilder.handlers.motion.attributeEditor.TransitionPanel;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import com.android.tools.idea.uibuilder.surface.AccessoryPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Provide the Panel that is displayed during editing a KeyFrame attribute
 */
public class MotionLayoutAttributePanel implements AccessoryPanelInterface {
  static Color ourSecondaryPanelBackground = new JBColor(0xfcfcfc, 0x313435);
  static Color ourMainBackground = ourSecondaryPanelBackground;

  private final ViewGroupHandler.AccessoryPanelVisibility myVisibilityCallback;
  private final NlComponent myMotionLayout;
  private JPanel myPanel;
  private NlComponent mySelection;
  private MotionLayoutTimelinePanel myTimelinePanel;
  public MotionSceneModel.KeyFrame myCurrentKeyframe;
  public NlModel myNlModel;
  JPanel myAttribGroups;
  TransitionPanel myTransitionPanel = new TransitionPanel();
  AttributeTagPanel myAttributeTagPanel = new AttributeTagPanel(this);
  private OnSwipePanel myOnSwipeTagPanel = new OnSwipePanel();

  public MotionLayoutAttributePanel(@NotNull NlComponent parent, @NotNull ViewGroupHandler.AccessoryPanelVisibility visibility) {
    myMotionLayout = parent;
    myVisibilityCallback = visibility;
  }

  @Override
  public @NotNull
  JPanel getPanel() {
    if (myPanel == null) {
      myPanel = createPanel(AccessoryPanel.Type.EAST_PANEL);
    }
    return myPanel;
  }

  @Override
  public @NotNull
  JPanel createPanel(@NotNull AccessoryPanel.Type type) {
    JPanel panel = new JPanel(new BorderLayout()) {
      {
        setPreferredSize(new Dimension(250, 250));
      }
    };

    myAttribGroups = new JPanel();
    myAttribGroups.setLayout(new GridBagLayout());
    myAttribGroups.setBackground(ourMainBackground);

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = JBUI.insetsTop( 10);
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1;
    gbc.gridwidth = 2;
    gbc.fill = GridBagConstraints.BOTH;
    myAttribGroups.add(myTransitionPanel, gbc);

    gbc.gridy++;
    myAttribGroups.add(myOnSwipeTagPanel, gbc);

    gbc.gridy++;
    myAttribGroups.add(myAttributeTagPanel, gbc);

    gbc.gridy++;
    gbc.weighty = 1;
    myAttribGroups.add(Box.createVerticalGlue(), gbc);

    JScrollPane scrollPane = new JBScrollPane(myAttribGroups);
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.setBackground(ourMainBackground);
    return panel;
  }

  @Override
  public void updateAccessoryPanelWithSelection(@NotNull AccessoryPanel.Type type, @NotNull List<NlComponent> selection) {
    if (selection.isEmpty()) {
      mySelection = null;
      myTimelinePanel = null;
      return;
    }

    mySelection = selection.get(0);
    Object property = mySelection.getClientProperty(MotionLayoutTimelinePanel.TIMELINE);
    if (property == null && mySelection.getParent() != null) {
      // need to grab the timeline from the MotionLayout component...
      // TODO: walk the tree up until we find the MotionLayout?
      property = mySelection.getParent().getClientProperty(MotionLayoutTimelinePanel.TIMELINE);
    }
    if (property != null) {
      myTimelinePanel = (MotionLayoutTimelinePanel)property;
      myTimelinePanel.setMotionLayoutAttributePanel(this);
    }
    updatePanel();
  }

  public void updateSelection() {
    updatePanel();
  }

  @Override
  public void deactivate() {
  }

  @Override
  public void updateAfterModelDerivedDataChanged() {
    updatePanel();
  }

  private void updatePanel() {
    if (myTimelinePanel != null && mySelection != null) {
      myNlModel = mySelection.getModel();
      MotionSceneModel.KeyFrame keyframe = myTimelinePanel.getSelectedKeyframe();
      if (keyframe != null) {
        MotionSceneModel.TransitionTag transitionTag = keyframe.getModel().getTransitionTag(0);
        myTransitionPanel.setTransitionTag(transitionTag);
        MotionSceneModel.OnSwipeTag onSwipeTag = keyframe.getModel().getOnSwipeTag();
        myOnSwipeTagPanel.setOnSwipeTag(onSwipeTag);
      }
      myCurrentKeyframe = keyframe;

      // fill out the key frame attributes
      myAttributeTagPanel.setKeyFrame(keyframe);
    }
  }

}

