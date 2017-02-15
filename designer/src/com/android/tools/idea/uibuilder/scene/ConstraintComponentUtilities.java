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
package com.android.tools.idea.uibuilder.scene;

import com.android.SdkConstants;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.scene.target.AnchorTarget;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;

import static com.android.tools.idea.uibuilder.scene.draw.DrawGuidelineCycle.BEGIN;
import static com.android.tools.idea.uibuilder.scene.draw.DrawGuidelineCycle.END;
import static com.android.tools.idea.uibuilder.scene.draw.DrawGuidelineCycle.PERCENT;

/**
 * Encapsulate basic querys on a ConstraintLayout component
 * TODO: use this class everywhere for this type of queries and replace/update ConstraintTarget
 */
public class ConstraintComponentUtilities {

  protected static final HashMap<String, String> ourReciprocalAttributes;
  protected static final HashMap<String, String> ourMapMarginAttributes;
  protected static final HashMap<String, AnchorTarget.Type> ourMapSideToOriginAnchors;
  protected static final HashMap<String, AnchorTarget.Type> ourMapSideToTargetAnchors;
  protected static final ArrayList<String> ourLeftAttributes;
  protected static final ArrayList<String> ourTopAttributes;
  protected static final ArrayList<String> ourRightAttributes;
  protected static final ArrayList<String> ourBottomAttributes;
  protected static final ArrayList<String> ourBaselineAttributes;
  protected static final ArrayList<String> ourMarginAttributes;
  protected static final ArrayList<String> ourHorizontalAttributes;
  protected static final ArrayList<String> ourVerticalAttributes;
  protected static final ArrayList<String> ourCreatorAttributes;

  static {
    ourReciprocalAttributes = new HashMap<>();
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF);

    ourMapMarginAttributes = new HashMap<>();
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_MARGIN_TOP);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF, SdkConstants.ATTR_LAYOUT_MARGIN_TOP);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM);

    ourMapSideToOriginAnchors = new HashMap<>();
    ourMapSideToOriginAnchors.put(SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF, AnchorTarget.Type.LEFT);
    ourMapSideToOriginAnchors.put(SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF, AnchorTarget.Type.LEFT);
    ourMapSideToOriginAnchors.put(SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF, AnchorTarget.Type.RIGHT);
    ourMapSideToOriginAnchors.put(SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, AnchorTarget.Type.RIGHT);
    ourMapSideToOriginAnchors.put(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, AnchorTarget.Type.TOP);
    ourMapSideToOriginAnchors.put(SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF, AnchorTarget.Type.TOP);
    ourMapSideToOriginAnchors.put(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF, AnchorTarget.Type.BOTTOM);
    ourMapSideToOriginAnchors.put(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, AnchorTarget.Type.BOTTOM);
    ourMapSideToOriginAnchors.put(SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, AnchorTarget.Type.BASELINE);

    ourMapSideToTargetAnchors = new HashMap<>();
    ourMapSideToTargetAnchors.put(SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF, AnchorTarget.Type.LEFT);
    ourMapSideToTargetAnchors.put(SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF, AnchorTarget.Type.RIGHT);
    ourMapSideToTargetAnchors.put(SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF, AnchorTarget.Type.LEFT);
    ourMapSideToTargetAnchors.put(SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, AnchorTarget.Type.RIGHT);
    ourMapSideToTargetAnchors.put(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, AnchorTarget.Type.TOP);
    ourMapSideToTargetAnchors.put(SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF, AnchorTarget.Type.BOTTOM);
    ourMapSideToTargetAnchors.put(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF, AnchorTarget.Type.TOP);
    ourMapSideToTargetAnchors.put(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, AnchorTarget.Type.BOTTOM);
    ourMapSideToTargetAnchors.put(SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, AnchorTarget.Type.BASELINE);

    ourLeftAttributes = new ArrayList<>();
    ourLeftAttributes.add(SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF);
    ourLeftAttributes.add(SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF);

    ourTopAttributes = new ArrayList<>();
    ourTopAttributes.add(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF);
    ourTopAttributes.add(SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF);

    ourRightAttributes = new ArrayList<>();
    ourRightAttributes.add(SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF);
    ourRightAttributes.add(SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF);

    ourBottomAttributes = new ArrayList<>();
    ourBottomAttributes.add(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF);
    ourBottomAttributes.add(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);

    ourBaselineAttributes = new ArrayList<>();
    ourBaselineAttributes.add(SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF);

    ourMarginAttributes = new ArrayList<>();
    ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN);
    ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
    // ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN_START);
    ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
    // ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN_END);
    ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN_TOP);
    ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM);

    ourHorizontalAttributes = new ArrayList<>();
    ourHorizontalAttributes.add(SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF);
    ourHorizontalAttributes.add(SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF);
    ourHorizontalAttributes.add(SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF);
    ourHorizontalAttributes.add(SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF);

    ourVerticalAttributes = new ArrayList<>();
    ourVerticalAttributes.add(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF);
    ourVerticalAttributes.add(SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF);
    ourVerticalAttributes.add(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF);
    ourVerticalAttributes.add(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);
    ourVerticalAttributes.add(SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF);

    ourCreatorAttributes = new ArrayList<>();
    ourCreatorAttributes.add(SdkConstants.ATTR_LAYOUT_LEFT_CREATOR);
    ourCreatorAttributes.add(SdkConstants.ATTR_LAYOUT_TOP_CREATOR);
    ourCreatorAttributes.add(SdkConstants.ATTR_LAYOUT_RIGHT_CREATOR);
    ourCreatorAttributes.add(SdkConstants.ATTR_LAYOUT_BOTTOM_CREATOR);
    ourCreatorAttributes.add(SdkConstants.ATTR_LAYOUT_BASELINE_CREATOR);
    ourCreatorAttributes.add(SdkConstants.ATTR_LAYOUT_CENTER_CREATOR);
    ourCreatorAttributes.add(SdkConstants.ATTR_LAYOUT_CENTER_X_CREATOR);
    ourCreatorAttributes.add(SdkConstants.ATTR_LAYOUT_CENTER_Y_CREATOR);
  }

  /**
   * Given a NlComponent and an attribute, return the corresponding AnchorTarget
   * @param scene
   * @param targetComponent
   * @param attribute
   * @return
   */
  public static AnchorTarget getOriginAnchor(Scene scene, NlComponent targetComponent, String attribute) {
    AnchorTarget.Type type = ourMapSideToOriginAnchors.get(attribute);
    SceneComponent component = scene.getSceneComponent(targetComponent);
    if (component != null) {
      return component.getAnchorTarget(type);
    }
    return null;
  }

  /**
   * Given a NlComponent and an attribute, return the corresponding AnchorTarget
   * @param scene
   * @param targetComponent
   * @param attribute
   * @return
   */
  public static AnchorTarget getTargetAnchor(Scene scene, NlComponent targetComponent, String attribute) {
    AnchorTarget.Type type = ourMapSideToTargetAnchors.get(attribute);
    SceneComponent component = scene.getSceneComponent(targetComponent);
    if (component != null) {
      return component.getAnchorTarget(type);
    }
    return null;
  }

  private static boolean hasConstraints(NlComponent component, String uri, ArrayList<String> constraints) {
    int count = constraints.size();
    for (int i = 0; i < count; i++) {
      if (component.getLiveAttribute(uri, constraints.get(i)) != null) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasHorizontalConstraints(NlComponent component) {
    return hasConstraints(component, SdkConstants.SHERPA_URI, ourHorizontalAttributes);
  }

  public static boolean hasVerticalConstraints(NlComponent component) {
    return hasConstraints(component, SdkConstants.SHERPA_URI, ourVerticalAttributes);
  }

  /**
   * Return a dp value correctly resolved. This is only intended for generic
   * dimensions (number + unit). Do not use this if the string can contain
   * wrap_content or match_parent. See {@link #getLayoutDimensionDpValue(NlComponent, String)}.
   *
   * @param component the component we are looking at
   * @param value     the attribute value we want to parse
   * @return the value of the attribute in Dp, or zero if impossible to resolve
   */
  public static int getDpValue(@NotNull NlComponent component, String value) {
    if (value != null) {
      Configuration configuration = component.getModel().getConfiguration();
      ResourceResolver resourceResolver = configuration.getResourceResolver();
      if (resourceResolver != null) {
        Integer px = ViewEditor.resolveDimensionPixelSize(resourceResolver, value, configuration);
        return px == null ? 0 : (int)(0.5f + px / (configuration.getDensity().getDpiValue() / 160.0f));
      }
    }
    return 0;
  }

  public static int getGuidelineMode(SceneComponent component) {
    NlComponent nlComponent = component.getNlComponent();
    String begin = nlComponent.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN);
    String end = nlComponent.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END);

    if (begin != null) {
      return BEGIN;
    } else if (end != null) {
      return END;
    } else {
      return PERCENT;
    }
  }

  public static void clearAttributes(NlComponent component) {
    AttributesTransaction transaction = component.startAttributeTransaction();
    clearAllAttributes(component, transaction);
    transaction.apply();

    NlModel nlModel = component.getModel();
    Project project = nlModel.getProject();
    XmlFile file = nlModel.getFile();

    String label = "Cleared all constraints";
    WriteCommandAction action = new WriteCommandAction(project, label, file) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        transaction.commit();
      }
    };
    action.execute();
  }

  public static void setDpAttribute(String uri, String attribute, AttributesTransaction transaction, int value) {
    String position = String.format(SdkConstants.VALUE_N_DP, value);
    transaction.setAttribute(uri, attribute, position);
  }

  public static void clearAttributes(String uri, ArrayList<String> attributes, AttributesTransaction transaction) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      transaction.setAttribute(uri, attribute, null);
    }
  }

  public static void clearConnections(NlComponent component, ArrayList<String> attributes, AttributesTransaction transaction) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      transaction.setAttribute(SdkConstants.SHERPA_URI, attribute, null);
    }
    if (attributes == ourLeftAttributes) {
      transaction.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, null);
      // transaction.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_START, null);
      transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, null);
    } else if (attributes == ourRightAttributes) {
      transaction.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, null);
      // transaction.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_END, null);
      transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, null);
    } else if (attributes == ourTopAttributes) {
      transaction.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_TOP, null);
      transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, null);
    } else if (attributes == ourBottomAttributes) {
      transaction.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM, null);
      transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, null);
    }
    if (!hasHorizontalConstraints(component)) {
      int offsetX = 0;
      NlComponent parent = component.getParent();
      if (parent != null) {
        offsetX = component.x - parent.x;
        // convert px to dp
        float dpiFactor = component.getModel().getConfiguration().getDensity().getDpiValue() / 160f;
        offsetX = (int)(0.5f + offsetX / dpiFactor);
      }
      setDpAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, transaction, offsetX);
    }
    if (!hasVerticalConstraints(component)) {
      int offsetY = 0;
      NlComponent parent = component.getParent();
      if (parent != null) {
        offsetY = component.y - parent.y;
        // convert px to dp
        float dpiFactor = component.getModel().getConfiguration().getDensity().getDpiValue() / 160f;
        offsetY = (int)(0.5f + offsetY / dpiFactor);
      }
      setDpAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, transaction, offsetY);
    }
  }

  private static void clearAllAttributes(NlComponent component, AttributesTransaction transaction) {
    clearAttributes(SdkConstants.SHERPA_URI, ourLeftAttributes, transaction);
    clearAttributes(SdkConstants.SHERPA_URI, ourTopAttributes, transaction);
    clearAttributes(SdkConstants.SHERPA_URI, ourRightAttributes, transaction);
    clearAttributes(SdkConstants.SHERPA_URI, ourBottomAttributes, transaction);
    clearAttributes(SdkConstants.TOOLS_URI, ourCreatorAttributes, transaction);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, null);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, null);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, null);
    clearAttributes(SdkConstants.ANDROID_URI, ourMarginAttributes, transaction);
    int offsetX = Coordinates.pxToDp(component.getModel(), component.x - (component.isRoot() ? 0 : component.getParent().x));
    int offsetY = Coordinates.pxToDp(component.getModel(), component.y - (component.isRoot() ? 0 : component.getParent().y));
    setDpAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, transaction, offsetX);
    setDpAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, transaction, offsetY);
  }

  public static void updateOnDelete(NlComponent component, String targetId) {
    AttributesTransaction transaction = null;
    transaction = updateOnDelete(component, ourLeftAttributes, transaction, targetId);
    transaction = updateOnDelete(component, ourTopAttributes, transaction, targetId);
    transaction = updateOnDelete(component, ourRightAttributes, transaction, targetId);
    transaction = updateOnDelete(component, ourBottomAttributes, transaction, targetId);
    transaction = updateOnDelete(component, ourBaselineAttributes, transaction, targetId);

    if (transaction != null) {
      transaction.apply();
      NlModel nlModel = component.getModel();
      Project project = nlModel.getProject();
      XmlFile file = nlModel.getFile();

      String label = "Remove constraints pointing to a deleted component";
      AttributesTransaction finalTransaction = transaction;
      WriteCommandAction action = new WriteCommandAction(project, label, file) {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          finalTransaction.commit();
        }
      };
      action.execute();
    }
  }

  public static AttributesTransaction updateOnDelete(NlComponent component, ArrayList<String> attributes, AttributesTransaction transaction, String targetId) {
    if (isConnectedTo(component, attributes, targetId)) {
      if (transaction == null) {
        transaction = component.startAttributeTransaction();
      }
      clearConnections(component, attributes, transaction);
    }
    return transaction;
  }

  public static boolean isConnectedTo(NlComponent component, ArrayList<String> attributes, String targetId) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      String target = component.getLiveAttribute(SdkConstants.SHERPA_URI, attribute);
      target = NlComponent.extractId(target);
      if (target != null && target.equalsIgnoreCase(targetId)) {
        return true;
      }
    }
    return false;
  }

  public static void ensureHorizontalPosition(NlComponent component, AttributesTransaction transaction) {
    if (hasHorizontalConstraints(component)) {
      return;
    }
    int dx = component.x - (component.getParent() != null ? component.getParent().x : 0);
    if (dx > 0) {
      float dipValue = component.getModel().getConfiguration().getDensity().getDpiValue() / 160f;
      String position = String.format(SdkConstants.VALUE_N_DP, ((int)(0.5f + dx / dipValue)));
      transaction.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, position);
    }
  }

  public static void ensureVerticalPosition(NlComponent component, AttributesTransaction transaction) {
    if (hasVerticalConstraints(component)) {
      return;
    }
    int dy = component.y - (component.getParent() != null ? component.getParent().y : 0);
    if (dy > 0) {
      float dipValue = component.getModel().getConfiguration().getDensity().getDpiValue() / 160f;
      String position = String.format(SdkConstants.VALUE_N_DP, ((int)(0.5f + dy / dipValue)));
      transaction.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, position);
    }
  }
}
