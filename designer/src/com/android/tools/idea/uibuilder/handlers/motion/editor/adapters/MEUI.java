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
package com.android.tools.idea.uibuilder.handlers.motion.editor.adapters;

import com.android.tools.adtui.stdui.menu.CommonPopupMenuUI;
import com.intellij.openapi.actionSystem.ActionButtonComponent;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.FocusListener;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.plaf.basic.BasicButtonUI;

/**
 * The access to platform independent UI features allow us to run using the JB components as well as the stand alone.
 */
public class MEUI {

  static float userScaleFactor = 1;
  public static final Color ourMySelectedTextColor = new JBColor(0xEAEAEA, 0xff333333);

  public static int scale(int i) {
    return JBUI.scale(i);
  }

  public static Dimension size(int width, int height) {
    return JBUI.size(width, height);
  }

  public static Insets insets(int top, int left, int bottom, int right) {
    return JBUI.insets(top, left, bottom, right);
  }

  public static MEComboBox<String> makeComboBox(String[] a) {
    return new MEComboBox<String>(a);
  }

  public static void invokeLater(Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(runnable);
  }

  public static final Color getBorderColor() {
    return JBColor.border();
  }

  private static Color makeColor(String name, int rgb, int darkRGB) {
    return JBColor.namedColor(name, new JBColor(rgb, darkRGB));
  }

  static boolean dark = false;

  public static final int ourLeftColumnWidth = JBUI.scale(150);
  public static final int ourHeaderHeight = JBUI.scale(30);
  public static final int ourGraphHeight = scale(60);


  public static final Color ourErrorColor = makeColor("UIDesigner.motion.ErrorColor", 0x8f831b, 0xffa31b);
  public static final Color ourBannerColor = makeColor("UIDesigner.motion.NotificationBackground", 0xfff8d1, 0x1d3857);
  public static final Color myTimeCursorColor = makeColor("UIDesigner.motion.TimeCursorColor", 0xff3d81e1, 0xff3d81e1);
  public static final Color myTimeCursorStartColor = makeColor("UIDesigner.motion.TimeCursorStartColor", 0xff3da1f1, 0xff3dd1f1);
  public static final Color myTimeCursorEndColor = makeColor("UIDesigner.motion.TimeCursorEndColor", 0xff3da1f1, 0xff3dd1f1);
  public static final Color myGridColor = makeColor("UIDesigner.motion.gridColor", 0x888888, 0x9e9e9e);
  public static final Color myUnSelectedLineColor = new Color(0xe0759a);
  public static final Color ourMySelectedKeyColor = makeColor("UIDesigner.motion.SelectedKeyColor", 0xff3da1f1, 0xff3dd1f1);
  public static final Color ourMySelectedLineColor = new Color(0x3879d9);
  public static final Color ourPrimaryPanelBackground = makeColor("UIDesigner.motion.PrimaryPanelBackground", 0xf5f5f5, 0x2D2F31);
  public static final Color ourSecondaryPanelBackground = makeColor("UIDesigner.motion.ourSelectedLineColor", 0xfcfcfc, 0x313435);
  public static final Color ourAvgBackground = makeColor("UIDesigner.motion.ourAvgBackground", 0xf8f8f8, 0x2f3133);
  public static final Color ourBorder = makeColor("UIDesigner.motion.ourBorder", 0xc9c9c9, 0x242627);
  public static final Color ourBorderLight = makeColor("BorderLight", 0xe8e6e6, 0x3c3f41);
  public static final Color ourAddConstraintColor = makeColor("UIDesigner.motion.AddConstraintColor", 0xff838383, 0xff666666);
  public static final Color ourTextColor = makeColor("UIDesigner.motion.TextColor", 0x2C2C2C, 0x9E9E9E);
  public static final Color ourAddConstraintPlus = makeColor("UIDesigner.motion.AddConstraintPlus", 0xffc9c9c9, 0xff333333);

  public static BufferedImage createImage(int w, int h, int type) {
    return UIUtil.createImage(w,h,type);
  }

  public static class Overview {
    public static final Color ourCS = makeColor("UIDesigner.motion.ConstraintSet", 0xFFFFFF, 0x515658);
    public static final Color ourCSText = makeColor("UIDesigner.motion.ConstraintSetText", 0x000000, 0xC7C7C7);
    public static final Color ourCS_Hover = makeColor("UIDesigner.motion.HoverColor", 0XEAF2FE, 0X6E869B);


    public static final Color ourCS_HoverBorder = makeColor("UIDesigner.motion.HoverColor", 0x989898, 0xA1A1A1);

    public static final Color ourCS_SelectedFocusBorder = makeColor("UIDesigner.motion.ourCS_SelectedFocusBorder", 0x1886F7, 0x9ccdff);
    public static final Color ourCS_SelectedBorder = makeColor("UIDesigner.motion.ourCS_SelectedBorder", 0x7a7a7a, 0xdbdbdb);
    public static final Color ourCS_Border = makeColor("UIDesigner.motion.ourCS_Border", 0xBEBEBE, 0x6D6D6E);
    public static final Color ourCS_Background = makeColor("UIDesigner.motion.ourCS_Background", 0xFFFFFF, 0x515658);
    public static final Color ourCS_TextColor= makeColor("UIDesigner.motion.ourCS_TextColor", 0x686868, 0xc7c7c7);
    public static final Color ourML_BarColor = makeColor("UIDesigner.motion.ourML_BarColor", 0xd8d8d8, 0x808385);

    //public static final Color ourCS_Select = makeColor("UIDesigner.motion.SelectedSetColor", 0xE1E2E1, 0X7792AC);
    //public static final Color ourLayoutHeaderColor = makeColor("UIDesigner.motion.LayoutHeaderColor", 0xD8D8D8, 0x808385);
    //public static final Color ourLayoutColor = makeColor("UIDesigner.motion.LayoutColor", 0xFFFFFF, 0x515658);
    //public static final Color ourHoverColor = makeColor("UIDesigner.motion.HoverColor", 0xD0D1D0, 0xD0D1D0);
    //public static final Color ourSelectedLineColor = makeColor("UIDesigner.motion.SelectedLineColor", 0x1886F7, 0x9CCDFF);
    //public static final Color ourHoverLineColor = makeColor("UIDesigner.motion.LineColor", 0xBEBEBE, 0x6D6D6E);
    public static final Color ourPositionColor = makeColor("UIDesigner.motion.PositionMarkColor", 0XF0A732, 0XF0A732);
  }

  public static class Graph {
    public static final Color ourG_Background = makeColor("UIDesigner.motion.motionGraphBackground", 0xd8d8d8, 0x4f5658);
    public static final Color ourG_line = makeColor("UIDesigner.motion.graphLine", 0xE66F9A, 0x97b1c0);
  }
  public static final Color ourSelectedSetColor = new JBColor(0xE1E2E1, 0xF0F1F0);
  public static final Color ourConstraintSet = new JBColor(0xF0F1F0, 0xF0F1F0);

  public static final int DIR_LEFT = 0;
  public static final int DIR_RIGHT = 1;
  public static final int DIR_TOP = 2;
  public static final int DIR_BOTTOM = 3;

  public static JButton createToolBarButton(Icon icon, String tooltip) {
    return createToolBarButton(icon, null, tooltip);
  }

  public static JButton createToolBarButton(Icon icon, Icon disable_icon, String tooltip) {
    return new MEActionButton(icon, disable_icon, tooltip);
  }

  public static JPopupMenu createPopupMenu() {
    JPopupMenu ret = new JPopupMenu();
    ret.setUI(new CommonPopupMenuUI());
    return ret;
  }


  public interface Popup {
    void dismiss();
    void hide();
    void show();
  }

  public static Popup createPopup(JComponent component, JComponent local) {
    Balloon balloon = JBPopupFactory.getInstance().createBalloonBuilder(component)
      .setFillColor(ourSecondaryPanelBackground)
      .setBorderColor(JBColor.border())
      .setBorderInsets(JBUI.insets(1))
      .setAnimationCycle(Registry.intValue("ide.tooltip.animationCycle"))
      .setShowCallout(true)
      .setPositionChangeYShift(2)
      .setHideOnKeyOutside(false)
      .setHideOnAction(false)
      .setBlockClicksThroughBalloon(true)
      .setRequestFocus(true)
      .setDialogMode(false)
      .createBalloon();
    balloon.showInCenterOf(local);
    return new Popup() {
      @Override
      public void dismiss() {
        balloon.dispose();
      }

      @Override
      public void hide() {
        balloon.hide();
      }

      @Override
      public void show() {
         balloon.showInCenterOf(local);
      }
    };

  }
}