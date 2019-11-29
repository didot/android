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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.JBColor;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Insets;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;

/**
 * The access to platform independent UI features allow us to run using the JB components as well as the stand alone.
 */
public class MEUI {

  static float userScaleFactor = 1;
  public static final Color ourMySelectedTextColor = new JBColor(0xEAEAEA, 0xCCCCCC);

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
  public static final Color myTimeCursorColor = makeColor("UIDesigner.motion.TimeCursorColor", 0xFF4A81FF, 0xFFB4D7FF);
  public static final Color myTimeCursorStartColor = makeColor("UIDesigner.motion.TimeCursorStartColor", 0xff3da1f1, 0xff3dd1f1);
  public static final Color myTimeCursorEndColor = makeColor("UIDesigner.motion.TimeCursorEndColor", 0xff3da1f1, 0xff3dd1f1);
  public static final Color myGridColor = makeColor("UIDesigner.motion.timeLineGridColor", 0xDDDDDD, 0x555555);
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
    return UIUtil.createImage(w, h, type);
  }

  /**
   * TODO: support intellij copy paste
   *
   * @param copyListener
   * @param pasteListener
   * @param panel
   */
  public static void addCopyPaste(ActionListener copyListener, ActionListener pasteListener, JComponent panel) {
    // TODO ideally support paste and copy with control or command
    KeyStroke copy = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_MASK, false);
    KeyStroke copy2 = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.META_MASK, false);
    KeyStroke paste = KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_MASK, false);
    KeyStroke paste2 = KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.META_MASK, false);
    panel.registerKeyboardAction(copyListener, "Copy", copy, JComponent.WHEN_FOCUSED);
    panel.registerKeyboardAction(copyListener, "Copy", copy2, JComponent.WHEN_FOCUSED);
    panel.registerKeyboardAction(pasteListener, "Paste", paste, JComponent.WHEN_FOCUSED);
    panel.registerKeyboardAction(pasteListener, "Paste", paste2, JComponent.WHEN_FOCUSED);
  }

  //0c283e
  public static class CSPanel {
    public static final Color our_SelectedFocusBackground =
      makeColor("UIDesigner.motion.CSPanel.SelectedFocusBackground", 0x3973d6, 0x2E65CA);
    public static final Color our_SelectedBackground =
      makeColor("UIDesigner.motion.CSPanel.SelectedBackground", 0xD3D3D3, 0x0C283E);
  }

  public static class Overview {
    public static final Color ourCS = makeColor("UIDesigner.motion.ConstraintSet", 0xFFFFFF, 0x515658);
    public static final Color ourCSText = makeColor("UIDesigner.motion.ConstraintSetText", 0x000000, 0xC7C7C7);
    public static final Color ourCS_Hover = makeColor("UIDesigner.motion.HoverColor", 0XEAF2FE, 0X6E869B);
    public static final Color ourCS_HoverBorder = makeColor("UIDesigner.motion.HoverColor", 0x7A7A7A, 0xA1A1A1);
    public static final Color ourCS_SelectedFocusBorder = makeColor("UIDesigner.motion.ourCS_SelectedFocusBorder", 0x1886F7, 0x9CCDFF);
    public static final Color ourCS_SelectedBorder = makeColor("UIDesigner.motion.ourCS_SelectedBorder", 0x7A7A7A, 0xA1A1A1);
    public static final Color ourCS_SelectedFocusBackground =
      makeColor("UIDesigner.motion.ourCS_SelectedFocusBackground", 0xD1E7FD, 0x7691AB);
    public static final Color ourCS_SelectedBackground = makeColor("UIDesigner.motion.ourCS_SelectedBackground", 0xD3D3D3, 0x797B7C);
    public static final Color ourCS_Border = makeColor("UIDesigner.motion.ourCS_Border", 0xBEBEBE, 0x6D6D6E);
    public static final Color ourCS_Background = makeColor("UIDesigner.motion.ourCS_Background", 0xFFFFFF, 0x515658);
    public static final Color ourCS_TextColor = makeColor("UIDesigner.motion.ourCS_TextColor", 0x686868, 0xc7c7c7);
    public static final Color ourCS_FocusTextColor = makeColor("UIDesigner.motion.cs_FocusText", 0x000000, 0xFFFFFF);
    public static final Color ourML_BarColor = makeColor("UIDesigner.motion.ourML_BarColor", 0xd8d8d8, 0x808385);
    public static final Color ourPositionColor = makeColor("UIDesigner.motion.PositionMarkColor", 0XF0A732, 0XF0A732);
  }

  public static class Graph {
    public static final Color ourG_Background = makeColor("UIDesigner.motion.motionGraphBackground", 0xfcfcfc, 0x313334);
    public static final Color ourG_line = makeColor("UIDesigner.motion.graphLine", 0xE66F9A, 0xA04E6C);
    public static final Color ourCursorTextColor = makeColor("UIDesigner.motion.CursorTextColor", 0xFFFFFF, 0x000000);

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

  public static void copy(MTag tag) {
    CopyPasteManager.getInstance().setContents(new StringSelection(MTag.serializeTag(tag)));
  }

  public static void cut(MTag tag) {
    CopyPasteManager.getInstance().setContents(new StringSelection(MTag.serializeTag(tag)));
    tag.getTagWriter().deleteTag().commit("cut");
  }

  public static Icon generateImageIcon(Image image) {
    return IconUtil.createImageIcon(image);
  }
}