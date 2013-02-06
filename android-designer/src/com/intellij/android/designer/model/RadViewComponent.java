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
package com.intellij.android.designer.model;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ViewInfo;
import com.intellij.designer.designSurface.ScalableComponent;
import com.intellij.designer.model.*;
import com.intellij.designer.palette.PaletteItem;
import com.intellij.designer.propertyTable.PropertyTable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.hash.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Alexander Lobas
 */
public class RadViewComponent extends RadVisualComponent {
  private final List<RadComponent> myChildren = new ArrayList<RadComponent>();
  protected ViewInfo myViewInfo;
  private Rectangle myMargins;
  private XmlTag myTag;
  private List<Property> myProperties;
  private PaletteItem myPaletteItem;

  public XmlTag getTag() {
    if (myTag != null && (myTag.getParent() == null || !myTag.isValid())) {
      return EmptyXmlTag.INSTANCE;
    }
    return myTag;
  }

  public void setTag(XmlTag tag) {
    myTag = tag;
  }

  public void updateTag(XmlTag tag) {
    setTag(tag);

    int size = myChildren.size();
    XmlTag[] tags = tag.getSubTags();

    for (int i = 0; i < size; i++) {
      RadViewComponent child = (RadViewComponent)myChildren.get(i);
      child.updateTag(tags[i]);
    }
  }

  public String getCreationXml() {
    throw new UnsupportedOperationException();
  }

  public ViewInfo getViewInfo() {
    return myViewInfo;
  }

  public void setViewInfo(ViewInfo viewInfo) {
    myViewInfo = viewInfo;
    myMargins = null;
  }

  public int getViewInfoCount() {
    return 1;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public String ensureId() {
    String id = getId();
    if (id == null) {
      id = IdManager.get(this).createId(this);
    }
    return id;
  }

  public String getId() {
    String idValue = getTag().getAttributeValue("id", SdkConstants.NS_RESOURCES);
    return StringUtil.isEmpty(idValue) ? null : idValue;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public int getBaseline() {
    try {
      Object viewObject = myViewInfo.getViewObject();
      return (Integer)viewObject.getClass().getMethod("getBaseline").invoke(viewObject);
    }
    catch (Throwable e) {
    }

    return -1;
  }

  public Rectangle getMargins() {
    if (myMargins == null) {
      myMargins = new Rectangle();

      try {
        Object layoutParams = myViewInfo.getLayoutParamsObject();
        Class<?> layoutClass = layoutParams.getClass();

        myMargins.x = fixDefault(layoutClass.getField("leftMargin").getInt(layoutParams));
        myMargins.y = fixDefault(layoutClass.getField("topMargin").getInt(layoutParams));
        myMargins.width = fixDefault(layoutClass.getField("rightMargin").getInt(layoutParams));
        myMargins.height = fixDefault(layoutClass.getField("bottomMargin").getInt(layoutParams));
      }
      catch (Throwable e) {
      }
    }
    return myMargins;
  }

  private static int fixDefault(int value) {
    return value == Integer.MIN_VALUE ? 0 : value;
  }

  private static final int WRAP_CONTENT = 0 << 30;

  public void calculateWrapSize(Dimension wrapSize, Rectangle bounds) {
    if (wrapSize.width == -1 || wrapSize.height == -1) {
      try {
        Object viewObject = myViewInfo.getViewObject();
        Class<?> viewClass = viewObject.getClass();

        viewClass.getMethod("forceLayout").invoke(viewObject);
        viewClass.getMethod("measure", int.class, int.class).invoke(viewObject, WRAP_CONTENT, WRAP_CONTENT);

        if (wrapSize.width == -1) {
          wrapSize.width = (Integer)viewClass.getMethod("getMeasuredWidth").invoke(viewObject);
        }
        if (wrapSize.height == -1) {
          wrapSize.height = (Integer)viewClass.getMethod("getMeasuredHeight").invoke(viewObject);
        }
      }
      catch (Throwable e) {
        if (wrapSize.width == -1) {
          wrapSize.width = bounds.width;
        }
        if (wrapSize.height == -1) {
          wrapSize.height = bounds.height;
        }
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @NotNull
  @Override
  public List<RadComponent> getChildren() {
    return myChildren;
  }

  @Override
  public boolean isBackground() {
    // In Android layouts there are two levels of parents; at the top level
    // there is the "Device Screen", which is not deletable.
    // Below that is the root layout, which we want to consider as the background,
    // such that a marquee selection within the layout drag will select its children.
    // This will also make select all operate on the children of the layout.
    RadComponent parent = getParent();
    return parent == null || parent.getParent() == null;
  }

  @Override
  public void delete() throws Exception {
    IdManager.get(this).removeComponent(this, true);

    removeFromParent();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myTag.delete();
      }
    });
  }

  @Override
  public List<Property> getProperties() {
    return myProperties;
  }

  public void setProperties(List<Property> properties) {
    myProperties = properties;
  }

  @Override
  public List<Property> getInplaceProperties() throws Exception {
    List<Property> properties = super.getInplaceProperties();
    Property idProperty = PropertyTable.findProperty(myProperties, "id");
    if (idProperty != null) {
      properties.add(idProperty);
    }
    return properties;
  }

  @Override
  public void copyTo(Element parent) throws Exception {
    // skip root
    if (getParent() != null) {
      Element component = new Element("component");
      component.setAttribute("tag", myTag.getName());

      XmlAttribute[] attributes = myTag.getAttributes();
      if (attributes.length > 0) {
        Element properties = new Element("properties");
        component.addContent(properties);

        Map<String, Element> namespaces = new HashMap<String, Element>();

        for (XmlAttribute attribute : attributes) {
          String namespace = attribute.getNamespacePrefix();
          if (namespace.length() == 0) {
            properties.setAttribute(attribute.getName(), attribute.getValue());
          }
          else {
            Element element = namespaces.get(namespace);
            if (element == null) {
              element = new Element(namespace);
              namespaces.put(namespace, element);
            }

            element.setAttribute(attribute.getLocalName(), attribute.getValue());
          }
        }

        for (Element element : namespaces.values()) {
          properties.addContent(element);
        }
      }

      parent.addContent(component);
      parent = component;
    }

    for (RadComponent child : myChildren) {
      child.copyTo(parent);
    }
  }

  @Override
  public RadComponent morphingTo(MetaModel target) throws Exception {
    return new ComponentMorphingTool(this, this, target, null).result();
  }

  /** Sets the palette item this component was initially created from */
  public void setInitialPaletteItem(PaletteItem paletteItem) {
    myPaletteItem = paletteItem;
  }

  /**
   * The palette item this component was initially created from, if known.
   * This will be null for widgets created through other means (pasting, typing code, etc)
   * or after an IDE restart.
   */
  @Nullable
  public PaletteItem getInitialPaletteItem() {
    return myPaletteItem;
  }

  /**
   * Like {@link #toModel(java.awt.Component, java.awt.Rectangle)}, but
   * also translates from pixels in the source, to device independent pixels
   * in the model.
   * <p>
   * A lot of client code needs to compute model dp by doing arithmetic on
   * bounds in the tool (which are scaled). By first computing to model
   * pixels (with {@link #toModel(java.awt.Component, java.awt.Rectangle)},
   * and <b>then</b> computing the dp from there, you introduce two levels
   * of rounding.
   * <p>
   * This method performs both computations in a single go which reduces
   * the amount of rounding error.
   */
  public Rectangle toModelDp(double dpi, Component source, Rectangle rectangle) {
    Component myNativeComponent = getNativeComponent();
    Rectangle bounds = myNativeComponent == source
                       ? rectangle : SwingUtilities.convertRectangle(source, rectangle, myNativeComponent);

    // To convert px to dpi, dp = px * 160 / dpi
    double x = 160 * bounds.x;
    double y = 160 * bounds.y;
    double w = 160 * bounds.width;
    double h = 160 * bounds.height;

    if (myNativeComponent != source && myNativeComponent instanceof ScalableComponent) {
      ScalableComponent scalableComponent = (ScalableComponent)myNativeComponent;
      double zoom = scalableComponent.getScale();
      if (zoom != 1) {
        x /= zoom;
        y /= zoom;
        w /= zoom;
        h /= zoom;
      }
    }

    return new Rectangle(
      (int)(x / dpi),
      (int)(y / dpi),
      (int)(w / dpi),
      (int)(h / dpi));
  }

  /**
   * Like {@link #toModel(java.awt.Component, java.awt.Point)}, but
   * also translates from pixels in the source, to device independent pixels
   * in the model.
   * <p>
   * A lot of client code needs to compute model dp by doing arithmetic on
   * bounds in the tool (which are scaled). By first computing to model
   * pixels (with {@link #toModel(java.awt.Component, java.awt.Point)},
   * and <b>then</b> computing the dp from there, you introduce two levels
   * of rounding.
   * <p>
   * This method performs both computations in a single go which reduces
   * the amount of rounding error.
   */
  public Point toModelDp(double dpi, @NotNull Component source, @NotNull Point point) {
    Component myNativeComponent = getNativeComponent();
    Point bounds = myNativeComponent == source
                       ? point : SwingUtilities.convertPoint(source, point, myNativeComponent);

    // To convert px to dpi, dp = px * 160 / dpi
    double x = 160 * bounds.x;
    double y = 160 * bounds.y;

    if (myNativeComponent != source && myNativeComponent instanceof ScalableComponent) {
      ScalableComponent scalableComponent = (ScalableComponent)myNativeComponent;
      double zoom = scalableComponent.getScale();
      if (zoom != 1) {
        x /= zoom;
        y /= zoom;
      }
    }

    return new Point(
      (int)(x / dpi),
      (int)(y / dpi));
  }

  /**
   * Like {@link #toModel(java.awt.Component, java.awt.Dimension)}, but
   * also translates from pixels in the source, to device independent pixels
   * in the model.
   * <p>
   * A lot of client code needs to compute model dp by doing arithmetic on
   * bounds in the tool (which are scaled). By first computing to model
   * pixels (with {@link #toModel(java.awt.Component, java.awt.Dimension)},
   * and <b>then</b> computing the dp from there, you introduce two levels
   * of rounding.
   * <p>
   * This method performs both computations in a single go which reduces
   * the amount of rounding error.
   */
  public Dimension toModelDp(double dpi, @NotNull Component source, @NotNull Dimension size) {
    Component myNativeComponent = getNativeComponent();
    size = new Dimension(size);

    // To convert px to dpi, dp = px * 160 / dpi
    double w = 160 * size.width;
    double h = 160 * size.height;

    if (myNativeComponent != source && myNativeComponent instanceof ScalableComponent) {
      ScalableComponent scalableComponent = (ScalableComponent)myNativeComponent;
      double zoom = scalableComponent.getScale();
      if (zoom != 1) {
        w /= zoom;
        h /= zoom;
      }
    }

    return new Dimension(
      (int)(w / dpi),
      (int)(h / dpi));
  }
}