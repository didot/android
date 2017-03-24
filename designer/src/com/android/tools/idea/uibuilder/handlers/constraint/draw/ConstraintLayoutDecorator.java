/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint.draw;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintUtilities;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.*;
import com.android.tools.idea.uibuilder.scene.decorator.SceneDecorator;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawConnection;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * This defines the decorator
 * TODO: move to the ConstraintLayout handler
 */
public class ConstraintLayoutDecorator extends SceneDecorator {
  final static String[] LEFT_DIR = {
    SdkConstants.ATTR_LAYOUT_START_TO_START_OF, SdkConstants.ATTR_LAYOUT_START_TO_END_OF,
    SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
  };
  final static String[] RIGHT_DIR = {
    SdkConstants.ATTR_LAYOUT_END_TO_END_OF, SdkConstants.ATTR_LAYOUT_END_TO_START_OF,
    SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
  };
  final static String[] TOP_DIR = {
    SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF
  };
  final static String[] BOTTOM_DIR = {
    SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF
  };
  final static String[] LEFT_DIR_RTL = {
    SdkConstants.ATTR_LAYOUT_END_TO_END_OF, SdkConstants.ATTR_LAYOUT_END_TO_START_OF,
    SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
  };
  final static String[] RIGHT_DIR_RTL = {
    SdkConstants.ATTR_LAYOUT_START_TO_START_OF, SdkConstants.ATTR_LAYOUT_START_TO_END_OF,
    SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
  };

  final static String[][] ourConnections = {LEFT_DIR, RIGHT_DIR, TOP_DIR, BOTTOM_DIR};
  final static String[][] ourConnections_rtl = {LEFT_DIR_RTL, RIGHT_DIR_RTL, TOP_DIR, BOTTOM_DIR};

  final static String BASELINE = "BASELINE";
  final static String[] BASELINE_DIR = new String[]{SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF};
  final static String BASELINE_TYPE = "BASELINE_TYPE";

  final static String[][] MARGIN_ATTR_LTR = {
    {SdkConstants.ATTR_LAYOUT_MARGIN_START, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT},
      {SdkConstants.ATTR_LAYOUT_MARGIN_END, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT},
        {SdkConstants.ATTR_LAYOUT_MARGIN_TOP},
          { SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM},
  };
  final static String[][] MARGIN_ATTR_RTL = {
    {SdkConstants.ATTR_LAYOUT_MARGIN_END , SdkConstants.ATTR_LAYOUT_MARGIN_LEFT},
    {SdkConstants.ATTR_LAYOUT_MARGIN_START, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT},
    {SdkConstants.ATTR_LAYOUT_MARGIN_TOP},
    { SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM},
  };
  final static String[] BIAS_ATTR = {
    SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS,
    SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS,
    SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS,
    SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS
  };
  final static boolean[] FLIP_BIAS = {
    true, false, false, true,
  };

  enum ConnectionType {
    SAME, BACKWARD
  }

  final static ConnectionType[] DIR_TABLE = {ConnectionType.SAME, ConnectionType.BACKWARD, ConnectionType.SAME, ConnectionType.BACKWARD};
  final static String[] ourDirections = {"LEFT", "RIGHT", "TOP", "BOTTOM"};
  final static String[] ourChainDirections = {"CHAIN_LEFT", "CHAIN_RIGHT", "CHAIN_TOP", "CHAIN_BOTTOM"};
  final static String[] ourDirectionsType = {"LEFT_TYPE", "RIGHT_TYPE", "TOP_TYPE", "BOTTOM_TYPE"};
  final static int[] ourOppositeDirection = {1, 0, 3, 2};

  private void convert(@NotNull SceneContext sceneContext, Rectangle rect) {
    rect.x = sceneContext.getSwingX(rect.x);
    rect.y = sceneContext.getSwingY(rect.y);
    rect.width = sceneContext.getSwingDimension(rect.width);
    rect.height = sceneContext.getSwingDimension(rect.height);
  }

  private void gatherProperties(@NotNull SceneComponent component,
                                @NotNull SceneComponent child) {
    boolean rtl = component.getScene().isInRTL();
    String[][] connections = ((rtl) ? ourConnections_rtl : ourConnections);
    for (int i = 0; i < ourDirections.length; i++) {
      getConnection(component, child, connections[i], ourDirections[i], ourDirectionsType[i]);
    }
    getConnection(component, child, BASELINE_DIR, BASELINE, BASELINE_TYPE);
  }

  /**
   * This caches connections on each child SceneComponent by accessing NLcomponent attributes
   *
   * @param component
   * @param child
   * @param atributes
   * @param dir
   * @param dirType
   */
  private void getConnection(SceneComponent component, SceneComponent child, String[] atributes, String dir, String dirType) {
    String id = null;
    ConnectionType type = ConnectionType.SAME;
    for (int i = 0; i < atributes.length; i++) {
      id = child.getNlComponent().getLiveAttribute(SdkConstants.SHERPA_URI, atributes[i]);
      type = DIR_TABLE[i];
      if (id != null) {
        break;
      }
    }
    if (id == null) {
      child.myCache.put(dir, id);
      child.myCache.put(dirType, ConnectionType.SAME);
      return;
    }
    if (id.equalsIgnoreCase(SdkConstants.ATTR_PARENT)) {
      child.myCache.put(dir, component);
      child.myCache.put(dirType, type);
      return;
    }
    String cleanId = NlComponent.extractId(id);
    if (cleanId == null) {
      child.myCache.put(dir, id);
      child.myCache.put(dirType, ConnectionType.SAME);
      return;
    }
    if (cleanId.equals(component.getId())) {
      child.myCache.put(dir, component);
      child.myCache.put(dirType, type);
      return;
    }
    for (SceneComponent con : component.getChildren()) {
      if (cleanId.equals(con.getId())) {
        child.myCache.put(dir, con);
        child.myCache.put(dirType, type);
        return;
      }
    }
    child.myCache.put(dirType, ConnectionType.SAME);
  }

  @Override
  protected void addBackground(@NotNull DisplayList list,
                               @NotNull SceneContext sceneContext,
                               @NotNull SceneComponent component) {
    // no background
  }

  /**
   * This is responsible for setting the clip and building the list for this component's children
   *
   * @param list
   * @param time
   * @param sceneContext
   * @param component
   */
  @Override
  protected void buildListChildren(@NotNull DisplayList list,
                                   long time,
                                   @NotNull SceneContext sceneContext,
                                   @NotNull SceneComponent component) {
    ArrayList<SceneComponent> children = component.getChildren();
    if (children.size() > 0) {
      // Cache connections between children
      for (SceneComponent child : component.getChildren()) {
        gatherProperties(component, child);
      }
      Rectangle rect = new Rectangle();
      component.fillRect(rect);
      DisplayList.UNClip unClip = list.addClip(sceneContext, rect);
      Scene scene = component.getScene();
      boolean showAllConstraints = scene.isShowAllConstraints();
      List<NlComponent> selection = scene.getSelection();
      for (SceneComponent child : children) {
        child.buildDisplayList(time, list, sceneContext);
        if (showAllConstraints || selection.contains(child.getNlComponent())) {
          buildListConnections(list, time, sceneContext, component, child); // draw child connections
        }
      }
      list.add(unClip);
    }
  }

  /**
   * This is used to build the display list of Constraints hanging off of of each child.
   * This assume all children have been pre-processed to cache the connections to other SceneComponents
   *
   * @param list
   * @param time
   * @param screenView
   * @param component
   * @param child
   */
  public void buildListConnections(@NotNull DisplayList list,
                                   long time,
                                   @NotNull SceneContext sceneContext,
                                   @NotNull SceneComponent component,
                                   @NotNull SceneComponent child) {
    Rectangle dest_rect = new Rectangle();
    Rectangle source_rect = new Rectangle();
    child.fillDrawRect(time, source_rect);
    convert(sceneContext, source_rect);
    int x = source_rect.x;
    int y = source_rect.y;
    int w = source_rect.width;
    int h = source_rect.height;

    List<NlComponent> selection = component.getScene().getSelection();
    boolean selected = selection.contains(child.getNlComponent());
    int mode = (selected) ? DrawConnection.MODE_SELECTED : DrawConnection.MODE_NORMAL;

    // Extract Scene Components constraints from cache (Table speeds up next step)
    ConnectionType[] connectionTypes = new ConnectionType[ourDirections.length];
    SceneComponent[] connectionTo = new SceneComponent[ourDirections.length];
    for (int i = 0; i < ourDirections.length; i++) {
      connectionTypes[i] = (ConnectionType)child.myCache.get(ourDirectionsType[i]);
      connectionTo[i] = (SceneComponent)child.myCache.get(ourDirections[i]);
    }

    for (int i = 0; i < ourDirections.length; i++) {
      mode = (selected) ?  DrawConnection.MODE_SELECTED : DrawConnection.MODE_NORMAL;
      ConnectionType type = connectionTypes[i];
      SceneComponent sc = connectionTo[i];
      int destType = DrawConnection.DEST_NORMAL;
      if (sc != null) {
        sc.fillDrawRect(time, dest_rect);  // get the destination rectangle
        convert(sceneContext, dest_rect);   // scale to screen space
        int connect = (type == ConnectionType.SAME) ? i : ourOppositeDirection[i];
        if (child.getParent().equals(sc)) { // flag a child connection
          destType = DrawConnection.DEST_PARENT;
        }
        else if (SdkConstants.CONSTRAINT_LAYOUT_GUIDELINE.equalsIgnoreCase(sc.getComponentClassName())
          || SdkConstants.CONSTRAINT_LAYOUT_BARRIER.equalsIgnoreCase(sc.getComponentClassName())) {
          destType = DrawConnection.DEST_GUIDELINE;
        }
        int connectType = DrawConnection.TYPE_NORMAL;

        if (connectionTo[ourOppositeDirection[i]] != null) { // opposite side is connected
          connectType = DrawConnection.TYPE_SPRING;
          if (connectionTo[ourOppositeDirection[i]] == sc && destType != DrawConnection.DEST_PARENT) { // center
            if (connectionTypes[ourOppositeDirection[i]] != type) {
              connectType = DrawConnection.TYPE_CENTER;
            }
            else {
              connectType = DrawConnection.TYPE_CENTER_WIDGET;
            }
          }
        }

        SceneComponent toComponentsTo = (SceneComponent)sc.myCache.get(ourDirections[connect]);
        // Chain detection
        if (type == ConnectionType.BACKWARD // this connection must be backward
            && toComponentsTo == child  // it must connect to some one who connects to me
            && sc.myCache.get(ourDirectionsType[connect]) == ConnectionType.BACKWARD) { // and that connection must be backward as well
          connectType = DrawConnection.TYPE_CHAIN;
          if (sc.myCache.containsKey(ourChainDirections[ourOppositeDirection[i]])) {
            continue; // no need to add element to display list chains only have to go one way
          }

          if (selection.contains(sc.getNlComponent())) {
            mode =  DrawConnection.MODE_SELECTED;
          }

          child.myCache.put(ourChainDirections[i], "drawn");
        }
        int margin = 0;
        int marginDistance = 0;
        boolean isMarginReference = false;
        float bias = 0.5f;
        boolean rtl = component.getScene().isInRTL();
        String []margin_attr = (rtl)?MARGIN_ATTR_RTL[i]:MARGIN_ATTR_LTR[i];
        String marginString = child.getNlComponent().getLiveAttribute(SdkConstants.NS_RESOURCES, margin_attr[0]);
        if (marginString == null && margin_attr.length>1) {
          marginString = child.getNlComponent().getLiveAttribute(SdkConstants.NS_RESOURCES, margin_attr[1]);
        }
        if (marginString == null) {
          if (i == 0) { // left check if it is start
            marginString = child.getNlComponent().getLiveAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_START);
          }
          else if (i == 1) { // right check if it is end
            marginString = child.getNlComponent().getLiveAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_END);
          }
        }
        if (marginString != null) {
          if (marginString.startsWith("@")) {
            isMarginReference = true;
          }
          margin = ConstraintUtilities.getDpValue(child.getNlComponent(), marginString);
          marginDistance = sceneContext.getSwingDimension(margin);
        }
        String biasString = child.getNlComponent().getLiveAttribute(SdkConstants.SHERPA_URI, BIAS_ATTR[i]);
        if (biasString != null) {
          try {
            bias = Float.parseFloat(biasString);
            if (FLIP_BIAS[i]) {
              bias = 1 - bias;
            }
          }
          catch (NumberFormatException e) {
          }
        }
        boolean shift = toComponentsTo != null;
        if (destType == DrawConnection.DEST_GUIDELINE) { // connections to guidelines are always Opposite
          connect = ourOppositeDirection[i];
        }
        DrawConnection
          .buildDisplayList(list, connectType, source_rect, i, dest_rect, connect, destType, shift, margin, marginDistance, isMarginReference, bias, mode);
      }
    }

    SceneComponent baseLineConnection = (SceneComponent)child.myCache.get("BASELINE");
    if (baseLineConnection != null) {
      baseLineConnection.fillDrawRect(time, dest_rect);  // get the destination rectangle
      convert(sceneContext, dest_rect);   // scale to screen space
      int dest_offset = sceneContext.getSwingDimension(baseLineConnection.getBaseline());
      int source_offset = sceneContext.getSwingDimension(child.getBaseline());
      source_rect.y += source_offset;
      source_rect.height = 0;
      dest_rect.y += dest_offset;
      dest_rect.height = 0;
      DrawConnection
        .buildDisplayList(list, DrawConnection.TYPE_BASELINE, source_rect, 5, dest_rect, 5, DrawConnection.DEST_NORMAL, false, 0, 0, false, 0f, mode);
    }
  }
}
