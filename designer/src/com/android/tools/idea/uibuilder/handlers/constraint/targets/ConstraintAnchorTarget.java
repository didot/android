/*
 * Copyright (C) 2017 - 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint.targets;

import static icons.StudioIcons.LayoutEditor.Toolbar.BASELINE_ALIGNED_CONSTRAINT;
import static icons.StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_BOTTOM_TO_BOTTOM;
import static icons.StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_BOTTOM_TO_TOP;
import static icons.StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_END_TO_END;
import static icons.StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_END_TO_START;
import static icons.StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_START_TO_END;
import static icons.StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_START_TO_START;
import static icons.StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_TOP_TO_BOTTOM;
import static icons.StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_TOP_TO_TOP;


import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.NlAttributesHolder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneComponentHelperKt;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.AnchorTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.uibuilder.handlers.constraint.ComponentModification;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawAnchor;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.decorator.DecoratorUtilities;
import com.android.tools.idea.uibuilder.scout.Scout;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.PopupMenuListenerAdapter;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.PopupMenuEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implements a target anchor for the ConstraintLayout.
 */
public class ConstraintAnchorTarget extends AnchorTarget {

  private final Type myType;

  private boolean myRenderingTemporaryConnection = false;
  @AndroidDpCoordinate private int myConnectedX = -1;
  @AndroidDpCoordinate private int myConnectedY = -1;

  private final HashMap<String, String> mPreviousAttributes = new HashMap<>();

  /////////////////////////////////////////////////////////////////////////////
  //region Constructor
  /////////////////////////////////////////////////////////////////////////////

  public ConstraintAnchorTarget(@NotNull Type type, boolean isEdge) {
    super(type, isEdge);
    myType = type;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Accessors
  /////////////////////////////////////////////////////////////////////////////

  public boolean isHorizontalAnchor() {
    return myType == Type.LEFT || myType == Type.RIGHT;
  }

  public boolean isVerticalAnchor() {
    return myType == Type.TOP || myType == Type.BOTTOM;
  }

  public boolean isBaselineAnchor() {
    return myType == Type.BASELINE;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Display
  /////////////////////////////////////////////////////////////////////////////

  private boolean isConnected(ConstraintAnchorTarget target) {
    if (target == null) {
      return false;
    }
    if (!isConnected()) {
      return false;
    }

    String attribute = null;
    //noinspection EnumSwitchStatementWhichMissesCases
    switch (myType) {
      case LEFT: {
        attribute = ConstraintComponentUtilities.getConnectionId(myComponent.getAuthoritativeNlComponent(),
                                                                 SdkConstants.SHERPA_URI,
                                                                 ConstraintComponentUtilities.ourLeftAttributes);
        break;
      }
      case RIGHT: {
        attribute = ConstraintComponentUtilities.getConnectionId(myComponent.getAuthoritativeNlComponent(),
                                                                 SdkConstants.SHERPA_URI,
                                                                 ConstraintComponentUtilities.ourRightAttributes);
        break;
      }
      case TOP: {
        attribute = ConstraintComponentUtilities.getConnectionId(myComponent.getAuthoritativeNlComponent(),
                                                                 SdkConstants.SHERPA_URI,
                                                                 ConstraintComponentUtilities.ourTopAttributes);
        break;
      }
      case BOTTOM: {
        attribute = ConstraintComponentUtilities.getConnectionId(myComponent.getAuthoritativeNlComponent(),
                                                                 SdkConstants.SHERPA_URI,
                                                                 ConstraintComponentUtilities.ourBottomAttributes);
        break;
      }
      case BASELINE: {
        attribute = myComponent.getAuthoritativeNlComponent().getLiveAttribute(SdkConstants.SHERPA_URI,
                                                                               SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF);
        if (attribute != null) {
          attribute = NlComponent.extractId(attribute);
        }
        break;
      }
    }
    if (attribute == null) {
      return false;
    }
    return attribute.equalsIgnoreCase(target.getComponent().getId());
  }

  @Override
  protected boolean isConnected() {
    return ConstraintComponentUtilities.isAnchorConnected(myType, myComponent.getAuthoritativeNlComponent(), useRtlAttributes(), isRtl());
  }

  @SuppressWarnings("UseJBColor")
  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (myIsEdge) {
      return;
    }

    super.render(list, sceneContext);

    if (!myRenderingTemporaryConnection) {
      if (myLastX != -1 && myLastY != -1) {
        if ((myConnectedX == -1 && myConnectedY == -1)
            || !(myLastX == myConnectedX && myLastY == myConnectedY)) {
          float x = getCenterX();
          float y = getCenterY();
          list.addConnection(sceneContext, x, y, myLastX, myLastY, myType.ordinal());
        }
      }
    }
  }

  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
  @Override
  public boolean isEnabled() {
    if (!super.isEnabled()) {
      return false;
    }
    if (myComponent.getScene().getSelection().size() > 1) {
      return false;
    }
    if (myComponent.isSelected()) {
      boolean hasBaselineConnection = isBaselineConnected();
      if (getType() == AnchorTarget.Type.BASELINE) {
        // only show baseline anchor as needed
        return myComponent.canShowBaseline() || hasBaselineConnection;
      }
      else {
        // if the baseline is showing, hide the rest of the anchors
        return (!hasBaselineConnection && !myComponent.canShowBaseline()) || (hasBaselineConnection && isHorizontalAnchor());
      }
    }

    Scene.FilterType filerType = myComponent.getScene().getFilterType();
    boolean matchesFilter;
    switch (filerType) {
      case BASELINE_ANCHOR:
        matchesFilter = isBaselineAnchor();
        break;
      case VERTICAL_ANCHOR:
        matchesFilter = isVerticalAnchor();
        break;
      case HORIZONTAL_ANCHOR:
        matchesFilter = isHorizontalAnchor();
        break;
      case ANCHOR:
        matchesFilter = true;
        break;
      default:
        matchesFilter = false;
        break;
    }
    Integer state = DecoratorUtilities.getTryingToConnectState(myComponent.getNlComponent());
    boolean tryingToConnect = state != null && (state & myType.getMask()) != 0 && isTargeted();
    return  matchesFilter || tryingToConnect;
  }

  @Override
  public boolean isConnectible(@NotNull AnchorTarget dest) {
    if (!(dest instanceof ConstraintAnchorTarget)) {
      return false;
    }
    ConstraintAnchorTarget constraintAnchorDest = (ConstraintAnchorTarget) dest;
    if (isVerticalAnchor() && !constraintAnchorDest.isVerticalAnchor()) {
      return false;
    }
    if (isHorizontalAnchor() && !constraintAnchorDest.isHorizontalAnchor()) {
      return false;
    }
    if (constraintAnchorDest.isEdge()) {
      return myComponent.getParent() == constraintAnchorDest.myComponent;
    }
    else {
      return myComponent.getParent() == constraintAnchorDest.myComponent.getParent();
    }
  }

  private boolean isBaselineConnected() {
    return myComponent.getAuthoritativeNlComponent()
                      .getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF) != null;
  }

  @NotNull
  @Override
  protected DrawAnchor.Mode getDrawMode() {
    Integer state = DecoratorUtilities.getTryingToConnectState(myComponent.getNlComponent());
    boolean can_connect = state != null && (state & myType.getMask()) != 0;
    boolean is_connected = isConnected();
    int drawState =
      ((can_connect) ? 1 : 0) | (mIsOver ? 2 : 0) | (is_connected ? 4 : 0) | (isTargeted() ? 8 : 0) | (myComponent.isSelected() ? 16 : 0);

    DrawAnchor.Mode[] modeTable = {
      DrawAnchor.Mode.DO_NOT_DRAW, //
      DrawAnchor.Mode.CAN_CONNECT, // can_connect
      DrawAnchor.Mode.OVER,        // mIsOver
      DrawAnchor.Mode.CAN_CONNECT, // can_connect & mIsOver
      DrawAnchor.Mode.NORMAL,      // is_connected
      DrawAnchor.Mode.CAN_CONNECT, // is_connected & can_connect
      DrawAnchor.Mode.OVER,        // is_connected & mIsOver
      DrawAnchor.Mode.CAN_CONNECT, // is_connected & can_connect & mIsOver
      DrawAnchor.Mode.NORMAL,      // myThisIsTheTarget
      DrawAnchor.Mode.NORMAL,      // myThisIsTheTarget & can_connect
      DrawAnchor.Mode.DO_NOT_DRAW, // myThisIsTheTarget & mIsOver
      DrawAnchor.Mode.CAN_CONNECT, // myThisIsTheTarget & can_connect & mIsOver
      DrawAnchor.Mode.NORMAL,      // myThisIsTheTarget & is_connected &
      DrawAnchor.Mode.NORMAL,      // myThisIsTheTarget & is_connected & can_connect
      DrawAnchor.Mode.CANNOT_CONNECT, // myThisIsTheTarget & is_cnnected & mIsOver
      DrawAnchor.Mode.CAN_CONNECT, // myThisIsTheTarget & is_connected & can_connect & mIsOver
      DrawAnchor.Mode.NORMAL,      // isSelected
      DrawAnchor.Mode.NORMAL,      // isSelected & can_connect
      DrawAnchor.Mode.OVER,        // isSelected & mIsOver
      DrawAnchor.Mode.CAN_CONNECT, // isSelected & can_connect & mIsOver
      DrawAnchor.Mode.NORMAL,      // isSelected & is_connected
      DrawAnchor.Mode.NORMAL,      // isSelected & is_connected & can_connect
      DrawAnchor.Mode.OVER,        // isSelected & is_connected & mIsOver
      DrawAnchor.Mode.CAN_CONNECT, // isSelected & is_connected & can_connect & mIsOver
      DrawAnchor.Mode.NORMAL,      // isSelected & myThisIsTheTarget
      DrawAnchor.Mode.NORMAL,      // isSelected & myThisIsTheTarget & can_connect
      DrawAnchor.Mode.CANNOT_CONNECT,   // isSelected & myThisIsTheTarget & mIsOver
      DrawAnchor.Mode.CAN_CONNECT, // isSelected & myThisIsTheTarget & can_connect & mIsOver
      DrawAnchor.Mode.NORMAL,      // isSelected & myThisIsTheTarget & is_connected &
      DrawAnchor.Mode.NORMAL,      // isSelected & myThisIsTheTarget & is_connected & can_connect
      DrawAnchor.Mode.OVER,        // isSelected & myThisIsTheTarget & is_connected & mIsOver
      DrawAnchor.Mode.CAN_CONNECT, // isSelected & myThisIsTheTarget & is_connected & can_connect & mIsOver
    };
    return modeTable[drawState];
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Utilities
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Clear the attributes related to this Anchor type
   */
  private void clearMe(@NotNull NlAttributesHolder transaction) {
    ConstraintComponentUtilities.clearAnchor(myType, transaction, useRtlAttributes(), isRtl());
  }

  /**
   * Store the existing attributes in mPreviousAttributes
   */
  private void rememberPreviousAttribute(@NotNull String uri, @NotNull ArrayList<String> attributes) {
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    for (String attribute : attributes) {
      mPreviousAttributes.put(attribute, component.getLiveAttribute(uri, attribute));
    }
  }

  private boolean useRtlAttributes() {
    return myComponent.useRtlAttributes();
  }

  private boolean isRtl() {
    return myComponent.getScene().isInRTL();
  }

  /**
   * Return the correct attribute string given our type and the target type
   */
  private String getAttribute(@NotNull Target target) {
    if (!(target instanceof ConstraintAnchorTarget)) {
      return null;
    }
    ConstraintAnchorTarget anchorTarget = (ConstraintAnchorTarget)target;
    return ConstraintComponentUtilities.getAttribute(myType, anchorTarget.myType, useRtlAttributes(), isRtl());
  }

  /**
   * Revert to the original (on mouse down) state.
   */
  private void revertToPreviousState() {
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    ComponentModification modification = new ComponentModification(component, "Revert");
    modification.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, null);
    for (String key : mPreviousAttributes.keySet()) {
      if (key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X) ||
          key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y)) {
        modification.setAttribute(SdkConstants.TOOLS_URI, key, mPreviousAttributes.get(key));
      }
      else if (key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_MARGIN_TOP)
               || key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM)
               || key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_MARGIN_LEFT)
               || key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT)
               || key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_MARGIN_START)
               || key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_MARGIN_END)) {
        modification.setAttribute(SdkConstants.ANDROID_URI, key, mPreviousAttributes.get(key));
      }
      else {
        modification.setAttribute(SdkConstants.SHERPA_URI, key, mPreviousAttributes.get(key));
      }
    }
    modification.apply();
    myComponent.getScene().needsLayout(Scene.ANIMATED_LAYOUT);
  }

  /**
   * Connect the anchor to the given target. Applied immediately in memory.
   */
  private ComponentModification connectMe(NlComponent component, String attribute, NlComponent targetComponent) {
    ComponentModification modification = new ComponentModification(component, "Connect Constraint");
    String targetId;
    NlComponent parent = component.getParent();
    assert parent != null;
    if (NlComponentHelperKt.isOrHasSuperclass(parent, SdkConstants.CLASS_CONSTRAINT_LAYOUT_CONSTRAINTS)) {
      parent = parent.getParent();
    }
    if (targetComponent == parent) {
      targetId = SdkConstants.ATTR_PARENT;
    }
    else {
      targetId = SdkConstants.NEW_ID_PREFIX + NlComponentHelperKt.ensureLiveId(targetComponent);
    }
    modification.setAttribute(SdkConstants.SHERPA_URI, attribute, targetId);
    if (myType == Type.BASELINE) {
      ConstraintComponentUtilities.clearAttributes(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourTopAttributes, modification);
      ConstraintComponentUtilities.clearAttributes(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourBottomAttributes, modification);
      modification.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, null);
      modification.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_TOP, null);
      modification.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM, null);
    }
    else if (ConstraintComponentUtilities.ourReciprocalAttributes.get(attribute) != null) {
      modification.setAttribute(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourReciprocalAttributes.get(attribute), null);
    }

    // If the other side is already connected to the same component, the user is trying to
    // center this component to targetComponent, so we remove the margin.
    String otherSideAttr = ConstraintComponentUtilities.ourOtherSideAttributes.get(attribute);
    String otherSideAttrValue = otherSideAttr != null ? component.getAttribute(SdkConstants.SHERPA_URI, otherSideAttr) : null;
    if (isOppositeSideConnectedToSameTarget(targetId, otherSideAttrValue)) {
      removeOppositeSideMargin(modification, otherSideAttr);
    }
    else if (ConstraintComponentUtilities.ourMapMarginAttributes.get(attribute) != null) {
      Scene scene = myComponent.getScene();
      int marginValue = getDistance(attribute, targetComponent, scene);
      if (!scene.isControlDown()) {
        if (marginValue < 0) {
          marginValue = 0;
        }
        else {
          marginValue = Scout.getMargin();
        }
      }
      else {
        marginValue = Math.max(marginValue, 0);
      }
      String margin;
      if (Scout.getMarginResource() == null) {
        margin = String.format(SdkConstants.VALUE_N_DP, marginValue);
      } else {
        margin = Scout.getMarginResource();
      }
      String attr = ConstraintComponentUtilities.ourMapMarginAttributes.get(attribute);
      modification.setAttribute(SdkConstants.ANDROID_URI, attr, margin);
      if (SdkConstants.ATTR_LAYOUT_MARGIN_END.equals(attr)) {
        modification.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, margin);
      }
      else if (SdkConstants.ATTR_LAYOUT_MARGIN_START.equals(attr)) {
        modification.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, margin);
      }
      scene.needsRebuildList();
      myConnectedX = myLastX;
      myConnectedY = myLastY;
    }
    ConstraintComponentUtilities.cleanup(modification, myComponent.getNlComponent());
    modification.apply();
    myComponent.getScene().needsLayout(Scene.ANIMATED_LAYOUT);
    myRenderingTemporaryConnection = true;
    return modification;
  }

  private void removeOppositeSideMargin(@NotNull NlAttributesHolder attributes, @NotNull String otherSideAttr) {
    String otherSideMargin = ConstraintComponentUtilities.ourMapMarginAttributes.get(otherSideAttr);
    if (otherSideMargin == null) {
      return;
    }
    String alternateAttr;

    boolean rtl = isRtl();
    switch (otherSideMargin) {
      case SdkConstants.ATTR_LAYOUT_MARGIN_LEFT:
        alternateAttr = rtl ? SdkConstants.ATTR_LAYOUT_MARGIN_END : SdkConstants.ATTR_LAYOUT_MARGIN_START;
        break;
      case SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT:
        alternateAttr = rtl ? SdkConstants.ATTR_LAYOUT_MARGIN_START : SdkConstants.ATTR_LAYOUT_MARGIN_END;
        break;
      case SdkConstants.ATTR_LAYOUT_MARGIN_START:
        alternateAttr = rtl ? SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT : SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
        break;
      case SdkConstants.ATTR_LAYOUT_MARGIN_END:
        alternateAttr = rtl ? SdkConstants.ATTR_LAYOUT_MARGIN_LEFT : SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
        break;
      default:
        alternateAttr = null;
    }

    attributes.setAttribute(SdkConstants.ANDROID_URI, otherSideMargin, null);
    if (alternateAttr != null) {
      attributes.setAttribute(SdkConstants.ANDROID_URI, alternateAttr, null);
    }
  }

  private static boolean isOppositeSideConnectedToSameTarget(@NotNull String targetId, @Nullable String otherSideAttrValue) {
    String strippedId = NlComponent.stripId(otherSideAttrValue);
    return strippedId != null && strippedId.equals(NlComponent.stripId(targetId));
  }

  private int getDistance(String attribute, NlComponent targetComponent, Scene scene) {
    int marginValue;
    ConstraintAnchorTarget
      targetAnchor = ConstraintComponentUtilities.getTargetAnchor(scene, targetComponent, attribute, useRtlAttributes(), isRtl());
    if (targetAnchor == null) {
      return 0;
    }
    switch (myType) {
      case LEFT: {
        switch (targetAnchor.getType()) {
          case LEFT:
          case RIGHT: {
            marginValue = (int)(getCenterX() - targetAnchor.getCenterX());
          }
          break;
          default:
            marginValue = 0;
        }
      }
      break;
      case RIGHT: {
        switch (targetAnchor.getType()) {
          case LEFT:
          case RIGHT: {
            marginValue = (int)(targetAnchor.getCenterX() - getCenterX());
          }
          break;
          default:
            marginValue = 0;
        }
      }
      break;
      case TOP: {
        switch (targetAnchor.getType()) {
          case TOP:
          case BOTTOM: {
            marginValue = (int)(getCenterY() - targetAnchor.getCenterY());
          }
          break;
          default:
            marginValue = 0;
        }
      }
      break;
      case BOTTOM: {
        switch (targetAnchor.getType()) {
          case TOP:
          case BOTTOM: {
            marginValue = (int)(targetAnchor.getCenterY() - getCenterY());
          }
          break;
          default:
            marginValue = 0;
        }
      }
      break;
      default:
        marginValue = 0;
    }
    return marginValue;
  }

  /**
   * Disconnect the anchor
   *
   * @param component
   */
  private void disconnectMe(NlComponent component) {
    ComponentModification modification = new ComponentModification(component, "Constraint Disconnected");
    clearMe(modification);
    ConstraintComponentUtilities.cleanup(modification, myComponent.getNlComponent());
    modification.commit();
    myComponent.getScene().needsLayout(Scene.ANIMATED_LAYOUT);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public int getPreferenceLevel() {
    return Target.ANCHOR_LEVEL;
  }

  @Override
  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    super.mouseDown(x, y);

    Scene scene = myComponent.getScene();
    if (isHorizontalAnchor()) {
      scene.setFilterType(Scene.FilterType.HORIZONTAL_ANCHOR);
    }
    else {
      scene.setFilterType(Scene.FilterType.VERTICAL_ANCHOR);
    }
    if (getType() == AnchorTarget.Type.BASELINE) {
      scene.setFilterType(Scene.FilterType.BASELINE_ANCHOR);
    }

    myConnectedX = -1;
    myConnectedY = -1;
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    mPreviousAttributes.clear();
    mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X,
                            component.getLiveAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X));
    mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y,
                            component.getLiveAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y));
    mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF,
                            component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF));
    //noinspection EnumSwitchStatementWhichMissesCases
    switch (myType) {
      case LEFT:
      case RIGHT: {
        rememberPreviousAttribute(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourLeftAttributes);
        rememberPreviousAttribute(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourRightAttributes);
        rememberPreviousAttribute(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourStartAttributes);
        rememberPreviousAttribute(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourEndAttributes);
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_START,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_START));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_END,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_END));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS,
                                component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS));
      }
      break;
      case TOP: {
        rememberPreviousAttribute(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourTopAttributes);
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_TOP));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS,
                                component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS));
      }
      break;
      case BOTTOM: {
        rememberPreviousAttribute(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourBottomAttributes);
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS,
                                component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS));
      }
      break;
      case BASELINE: {
        rememberPreviousAttribute(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourTopAttributes);
        rememberPreviousAttribute(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourBottomAttributes);
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_TOP));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS,
                                component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS));
      }
      break;
    }
  }

  /**
   * On mouse drag, we can connect (in memory) to existing targets, or revert to the
   * original state that we captured on mouseDown.
   */
  @Override
  public void mouseDrag(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @NotNull List<Target> closestTargets) {
    super.mouseDrag(x, y, closestTargets);

    ConstraintAnchorTarget targetAnchor = null;
    for (Target target : closestTargets) {
      if (target instanceof ConstraintAnchorTarget && target != this) {
        targetAnchor = (ConstraintAnchorTarget)target;
        break;
      }
    }
    if (!myIsDragging) {
      myIsDragging = true;
      DecoratorUtilities.setTryingToConnectState(myComponent.getNlComponent(), myType, true);
    }

    if (targetAnchor != null) {
      NlComponent component = myComponent.getAuthoritativeNlComponent();
      String attribute = getAttribute(targetAnchor);
      if (attribute != null) {
        if (targetAnchor.myComponent != myComponent && !targetAnchor.isConnected(this)) {
          if (myComponent.getParent() != targetAnchor.myComponent) {
            Integer state = DecoratorUtilities.getTryingToConnectState(targetAnchor.myComponent.getNlComponent());
            if (state == null) {
              return;
            }
            int mask = state & targetAnchor.myType.getMask();
            if (mask == 0) {
              return;
            }
          }

          NlComponent targetComponent = targetAnchor.myComponent.getAuthoritativeNlComponent();
          connectMe(component, attribute, targetComponent);
          return;
        }
      }
    }
    if (myRenderingTemporaryConnection) {
      revertToPreviousState();
      myRenderingTemporaryConnection = false;
      return;
    }

    myComponent.getScene().needsRebuildList();
  }

  /**
   * On mouseRelease, we can either disconnect the current anchor (if the mouse release is on ourselves)
   * or connect the anchor to a given target. Modifications are applied first in memory then committed
   * to the XML model.
   */
  @Override
  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @NotNull List<Target> closestTargets) {
    super.mouseRelease(x, y, closestTargets);

    try {
      ConstraintAnchorTarget closestTarget = null;
      for (Target target : closestTargets) {
        if (target instanceof ConstraintAnchorTarget && target != this) {
          closestTarget = (ConstraintAnchorTarget)target;
          break;
        }
      }
      if (closestTarget == null && closestTargets.contains(this)) {
        closestTarget = this;
      }
      if (closestTarget != null && !closestTarget.isConnected(this)) {
        NlComponent component = myComponent.getAuthoritativeNlComponent();
        if (closestTarget == this) {
          disconnectMe(component);
        }
        else {
          String attribute = getAttribute(closestTarget);
          if (attribute != null) {
            if (closestTarget.myComponent == myComponent) {
              return;
            }
            if (myComponent.getParent() != closestTarget.myComponent) {
              Integer state = DecoratorUtilities.getTryingToConnectState(closestTarget.myComponent.getNlComponent());
              if (state == null) {
                return;
              }
              int mask = state & closestTarget.myType.getMask();
              if (mask == 0) {
                return;
              }
            }
            NlComponent targetComponent = closestTarget.myComponent.getAuthoritativeNlComponent();
            ComponentModification modification = connectMe(component, attribute, targetComponent);
            modification.commit();
            myComponent.getScene().needsLayout(Scene.ANIMATED_LAYOUT);
          }
        }
      }
      else {
        Collection<SceneComponent> components = myComponent.getScene().getSceneComponents();
        Rectangle rectangle = new Rectangle();
        ArrayList<NlComponent> list = new ArrayList<>();
        list.add(myComponent.getAuthoritativeNlComponent());
        list.add(myComponent.getAuthoritativeNlComponent());
        ArrayList<SceneComponent> allItems = new ArrayList<>();
        for (SceneComponent component : components) {
          rectangle.width = component.getDrawWidth();
          rectangle.height = component.getDrawHeight();
          rectangle.x = component.getDrawX();
          rectangle.y = component.getDrawY();
          if (rectangle.contains(x, y) && SceneComponentHelperKt.isSibling(myComponent, component)) {
            allItems.add(component);
          }
        }
        if (!allItems.isEmpty()) {
          JBPopupMenu menu = new JBPopupMenu("Connect to:");
          for (SceneComponent component : allItems) {
            list.set(1, component.getAuthoritativeNlComponent());
            switch (myType) {
              case LEFT:
                addConnectMenu(list, allItems, component, menu, Scout.Connect.ConnectStartToStart, "start ", " start", CONSTRAIN_START_TO_START);
                addConnectMenu(list, allItems, component, menu, Scout.Connect.ConnectStartToEnd, "end ", " start", CONSTRAIN_START_TO_END);
                break;
              case RIGHT:
                addConnectMenu(list, allItems, component, menu, Scout.Connect.ConnectEndToStart, "End ", " start", CONSTRAIN_END_TO_START);
                addConnectMenu(list, allItems, component, menu, Scout.Connect.ConnectEndToEnd, "End ", " end", CONSTRAIN_END_TO_END);
                break;
              case TOP:
                addConnectMenu(list, allItems, component, menu, Scout.Connect.ConnectTopToTop, "Top ", " top", CONSTRAIN_TOP_TO_TOP);
                addConnectMenu(list, allItems, component, menu, Scout.Connect.ConnectTopToBottom, "Top ", " bottom", CONSTRAIN_TOP_TO_BOTTOM);
                break;
              case BOTTOM:
                addConnectMenu(list, allItems, component, menu, Scout.Connect.ConnectBottomToTop, "Bottom ", " top", CONSTRAIN_BOTTOM_TO_TOP);
                addConnectMenu(list, allItems, component, menu, Scout.Connect.ConnectBottomToBottom, "Bottom ", " bottom",
                               CONSTRAIN_BOTTOM_TO_BOTTOM);
                break;
              case BASELINE:
                addConnectMenu(list, allItems, component, menu, Scout.Connect.ConnectBaseLineToBaseLine, "Baseline ", " baseline",
                               BASELINE_ALIGNED_CONSTRAINT);
                break;
            }
          }

          double scale = myComponent.getScene().getDesignSurface().getScale();
          scale *= myComponent.getScene().getDesignSurface().getSceneScalingFactor();
          float dx = myComponent.getScene().getDesignSurface().getContentOriginX();
          float dy = myComponent.getScene().getDesignSurface().getContentOriginY();

          // Finish previous dragging setup.
          myIsDragging = false;
          DecoratorUtilities.setTryingToConnectState(myComponent.getAuthoritativeNlComponent(), myType, false);

          List<NlComponent> allItemsNlComponents =
            allItems.stream().map(item -> item.getAuthoritativeNlComponent()).collect(Collectors.toCollection(ArrayList::new));
          menu.addPopupMenuListener(new PopupMenuListenerAdapter() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
              super.popupMenuWillBecomeVisible(e);
              DecoratorUtilities.setTryingToConnectState(myComponent.getAuthoritativeNlComponent(), allItemsNlComponents, myType, true);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
              super.popupMenuWillBecomeInvisible(e);
              DecoratorUtilities.setTryingToConnectState(myComponent.getAuthoritativeNlComponent(), allItemsNlComponents, myType, false);
              myComponent.getScene().setFilterType(Scene.FilterType.NONE);
            }
          });
          menu.show(myComponent.getScene().getDesignSurface().getPreferredFocusedComponent(), (int)(x * scale + dx), (int)(y * scale + dy));
        }
      }
    }
    finally {
      if (myIsDragging) {
        myIsDragging = false;
        DecoratorUtilities.setTryingToConnectState(myComponent.getNlComponent(), myType, false);
        myComponent.getScene().needsRebuildList();
      }
    }
  }

  @Override
  public void mouseCancel() {
    super.mouseCancel();
    DecoratorUtilities.setTryingToConnectState(myComponent.getNlComponent(), myType, false);
    revertToPreviousState();
  }

  /**
   * adds a connection to the connection menu list
   *
   * @param list
   * @param component
   * @param menu
   * @param type
   * @param from
   * @param to
   * @param icon
   */
  private void addConnectMenu(ArrayList<NlComponent> list,
                              List<SceneComponent> allItems,
                              SceneComponent component,
                              JBPopupMenu menu,
                              Scout.Connect type,
                              String from,
                              String to,
                              Icon icon) {
    if (Scout.connectCheck(list, type, false)) {
      menu.add(new ConnectMenu(allItems, myComponent, from, component, to, icon, type));
    }
  }

  static class ConnectMenu extends JBMenuItem implements ActionListener, ChangeListener {
    SceneComponent mySrc;
    SceneComponent myDest;
    Scout.Connect myType;
    @Nullable AnchorTarget myDestTarget;

    List<SceneComponent> mAllItems;

    public ConnectMenu(List<SceneComponent> allItems,
                       SceneComponent src,
                       String from,
                       SceneComponent dest, String text, Icon icon, Scout.Connect type) {
      super(from + " to " + dest.getId() + text, icon);
      mAllItems = allItems;
      mySrc = src;
      myDest = dest;
      myType = type;
      for (Target target : myDest.getTargets()) {
        if (target instanceof AnchorTarget && ((AnchorTarget)target).getType() == myType.getDstAnchorType()) {
          myDestTarget = (AnchorTarget)target;
        }
      }
      addActionListener(this);
      addChangeListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      List<NlComponent> list = Arrays.asList(mySrc.getAuthoritativeNlComponent(), myDest.getAuthoritativeNlComponent());
      Scout.connect(list, myType, false, true);
      NlDesignSurface designSurface = (NlDesignSurface)mySrc.getScene().getDesignSurface();
      designSurface.forceLayersPaint(true);
      designSurface.repaint();
    }


    @Override
    public void stateChanged(ChangeEvent e) {
      for (SceneComponent item : mAllItems) {
        item.setDrawState(SceneComponent.DrawState.NORMAL);
      }
      if (myDestTarget != null) {
        myDestTarget.setMouseHovered(isSelected() || isArmed());
      }
      myDest.setDrawState(isSelected() || isArmed() ? SceneComponent.DrawState.HOVER : SceneComponent.DrawState.NORMAL);
      myDest.getScene().needsRebuildList();
      myDest.getScene().repaint();
    }
  }
  //endregion
  /////////////////////////////////////////////////////////////////////////////
}
