/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.android.designer.designSurface.layout.actions;

import com.android.SdkConstants;
import com.intellij.android.designer.AndroidDesignerUtils;
import com.intellij.android.designer.designSurface.graphics.*;
import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.*;
import com.intellij.designer.designSurface.feedbacks.LineMarginBorder;
import com.intellij.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.designer.designSurface.selection.EmptyPoint;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class LayoutMarginOperation implements EditOperation {
  public static final String TYPE = "layout_margin";

  protected final OperationContext myContext;
  protected RadViewComponent myComponent;
  protected RectangleFeedback myFeedback;
  protected TextFeedback myTextFeedback;
  private Rectangle myBounds; // in screen coordinates
  protected Rectangle myMargins; // in model coordinates

  public LayoutMarginOperation(OperationContext context) {
    myContext = context;
  }

  @Override
  public void setComponent(RadComponent component) {
    myComponent = (RadViewComponent)component;
    myBounds = myComponent.getBounds(myContext.getArea().getFeedbackLayer());
    myMargins = myComponent.getMargins();
  }

  @Override
  public void setComponents(List<RadComponent> components) {
  }

  private void createFeedback() {
    if (myFeedback == null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

      myTextFeedback = new TextFeedback();
      myTextFeedback.setBorder(new LineMarginBorder(0, 5, 3, 0));
      layer.add(myTextFeedback);

      myFeedback = new RectangleFeedback(DrawingStyle.MARGIN_BOUNDS);
      layer.add(myFeedback);

      layer.repaint();
    }
  }

  @Override
  public void showFeedback() {
    createFeedback();

    Rectangle bounds = myContext.getTransformedRectangle(myBounds);
    FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
    applyMargins(bounds, layer, myComponent, myMargins);
    myFeedback.setBounds(bounds);

    myTextFeedback.clear();
    fillTextFeedback();
    myTextFeedback.locationTo(myContext.getLocation(), 15);
  }

  protected void fillTextFeedback() {
    EditableArea area = myContext.getArea();
    FeedbackLayer layer = area.getFeedbackLayer();
    Point moveDelta = myComponent.toModel(layer, myContext.getMoveDelta());
    Dimension sizeDelta = myComponent.toModel(layer, myContext.getSizeDelta());
    int direction = myContext.getResizeDirection();

    if (direction == Position.WEST) { // left
      myTextFeedback.append(AndroidDesignerUtils.pxToDpWithUnits(area, myMargins.x - moveDelta.x));
    }
    else if (direction == Position.EAST) { // right
      myTextFeedback.append(AndroidDesignerUtils.pxToDpWithUnits(area, myMargins.width + sizeDelta.width));
    }
    else if (direction == Position.NORTH) { // top
      myTextFeedback.append(AndroidDesignerUtils.pxToDpWithUnits(area, myMargins.y - moveDelta.y));
    }
    else if (direction == Position.SOUTH) { // bottom
      myTextFeedback.append(AndroidDesignerUtils.pxToDpWithUnits(area, myMargins.height + sizeDelta.height));
    }
  }

  @Override
  public void eraseFeedback() {
    if (myFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myTextFeedback);
      layer.remove(myFeedback);
      layer.repaint();
      myTextFeedback = null;
      myFeedback = null;
    }
  }

  @Override
  public boolean canExecute() {
    return true;
  }

  @Override
  public void execute() throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        XmlTag tag = myComponent.getTag();

        XmlAttribute margin = tag.getAttribute("layout_margin", SdkConstants.NS_RESOURCES);
        if (margin != null) {
          String value = margin.getValue();
          margin.delete();

          if (!StringUtil.isEmpty(value)) {
            tag.setAttribute("layout_marginLeft", SdkConstants.NS_RESOURCES, value);
            tag.setAttribute("layout_marginRight", SdkConstants.NS_RESOURCES, value);
            tag.setAttribute("layout_marginTop", SdkConstants.NS_RESOURCES, value);
            tag.setAttribute("layout_marginBottom", SdkConstants.NS_RESOURCES, value);
          }
        }

        FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
        Point moveDelta = myComponent.toModel(layer, myContext.getMoveDelta());
        Dimension sizeDelta = myComponent.toModel(layer, myContext.getSizeDelta());
        int direction = myContext.getResizeDirection();

        if (direction == Position.WEST) { // left
          setValue(tag, "layout_marginLeft", myMargins.x - moveDelta.x);
        }
        else if (direction == Position.EAST) { // right
          setValue(tag, "layout_marginRight", myMargins.width + sizeDelta.width);
        }
        else if (direction == Position.NORTH) { // top
          setValue(tag, "layout_marginTop", myMargins.y - moveDelta.y);
        }
        else if (direction == Position.SOUTH) { // bottom
          setValue(tag, "layout_marginBottom", myMargins.height + sizeDelta.height);
        }
      }
    });
  }

  private void setValue(XmlTag tag, String name, int pxValue) {
    if (pxValue == 0) {
      ModelParser.deleteAttribute(tag, name);
    }
    else {
      String value = AndroidDesignerUtils.pxToDpWithUnits(myContext.getArea(), pxValue);
      tag.setAttribute(name, SdkConstants.NS_RESOURCES, value);
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // ResizePoint
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public static void points(ResizeSelectionDecorator decorator) {
    pointFeedback(decorator);

    decorator.addPoint(new DirectionResizePoint(DrawingStyle.MARGIN_HANDLE, Position.WEST, TYPE, "Change layout:margin.left") { // left
      @Override
      protected Point getLocation(DecorationLayer layer, RadComponent component) {
        Point location = super.getLocation(layer, component);
        int marginX = ((RadViewComponent)component).getMargins().x;
        if (marginX != 0) {
          marginX = component.fromModel(layer, new Dimension(marginX, 0)).width;
        }

        location.x -= marginX;
        return location;
      }
    });

    pointRight(decorator, DrawingStyle.MARGIN_HANDLE, 0.25, TYPE, "Change layout:margin.right");

    decorator.addPoint(new DirectionResizePoint(DrawingStyle.MARGIN_HANDLE, Position.NORTH, TYPE, "Change layout:margin.top") { // top
      @Override
      protected Point getLocation(DecorationLayer layer, RadComponent component) {
        Point location = super.getLocation(layer, component);
        int marginY = ((RadViewComponent)component).getMargins().y;
        if (marginY != 0) {
          marginY = component.fromModel(layer, new Dimension(0, marginY)).height;
        }

        location.y -= marginY;
        return location;
      }
    });

    pointBottom(decorator, DrawingStyle.MARGIN_HANDLE, 0.25, TYPE, "Change layout:margin.bottom");
  }

  protected static void pointFeedback(ResizeSelectionDecorator decorator) {
    decorator.addPoint(new EmptyPoint() {
      @Override
      protected void paint(DecorationLayer layer, Graphics2D g, RadComponent component) {
        Rectangle bounds = component.getBounds(layer);
        Rectangle margins = ((RadViewComponent)component).getMargins();
        if (margins.x != 0 || margins.y != 0 || margins.width != 0 || margins.height != 0) {
          applyMargins(bounds, layer, component, margins);
          DesignerGraphics.drawRect(DrawingStyle.MARGIN_BOUNDS, g, bounds.x, bounds.y, bounds.width, bounds.height);
        }
      }
    });
  }

  protected static void pointRight(ResizeSelectionDecorator decorator,
                                   DrawingStyle style,
                                   double ySeparator,
                                   Object type,
                                   @Nullable String description) {
    decorator.addPoint(new DirectionResizePoint(style, Position.EAST, type, description) {
      @Override
      protected Point getLocation(DecorationLayer layer, RadComponent component) {
        Point location = super.getLocation(layer, component);
        int marginWidth = ((RadViewComponent)component).getMargins().width;
        if (marginWidth != 0) {
          marginWidth = component.fromModel(layer, new Dimension(marginWidth, 0)).width;
        }

        location.x += marginWidth;
        return location;
      }
    }.move(1, ySeparator));
  }

  protected static void pointBottom(ResizeSelectionDecorator decorator,
                                    DrawingStyle style,
                                    double xSeparator,
                                    Object type,
                                    @Nullable String description) {
    decorator.addPoint(new DirectionResizePoint(style, Position.SOUTH, type, description) {
      @Override
      protected Point getLocation(DecorationLayer layer, RadComponent component) {
        Point location = super.getLocation(layer, component);
        int marginHeight = ((RadViewComponent)component).getMargins().height;
        if (marginHeight != 0) {
          marginHeight = component.fromModel(layer, new Dimension(0, marginHeight)).height;
        }
        location.y += marginHeight;
        return location;
      }
    }.move(xSeparator, 1));
  }

  private static void applyMargins(Rectangle bounds, Component target, RadComponent component, Rectangle margins) {
    if (margins.x == 0 && margins.y == 0 && margins.width == 0 && margins.height == 0) {
      return;
    }

    // Margin x and y are not actually x and y coordinates; they are
    // dimensions on the left and top sides. Therefore, we should NOT
    // use Rectangle bounds conversion operations, since they will
    // shift coordinate systems
    Dimension topLeft = component.fromModel(target, new Dimension(margins.x, margins.y));
    Dimension bottomRight = component.fromModel(target, new Dimension(margins.width, margins.height));

    bounds.x -= topLeft.width;
    bounds.width += topLeft.width;

    bounds.y -= topLeft.height;
    bounds.height += topLeft.height;

    bounds.width += bottomRight.width;
    bounds.height += bottomRight.height;
  }
}
