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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.rendering.parsers.AttributeSnapshot;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ConnectionDraw;
import com.android.tools.idea.uibuilder.handlers.constraint.model.ConstraintWidget;
import com.android.tools.idea.uibuilder.scout.Scout;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Uses a SceneDraw to render an iconic form of the widget
 */
public class SingleWidgetView extends JPanel {
  public static final int RATIO_UNLOCK = 0;
  private static final int RATIO_LOCK = 1;
  public static final int RATIO_LOCK_HEIGHT = 2;
  public static final int RATIO_LOCK_WIDTH = 3;
  public final static String TOP_MARGIN_WIDGET = "topMarginWidget";
  public final static String LEFT_MARGIN_WIDGET = "leftMarginWidget";
  public final static String BOTTOM_MARGIN_WIDGET = "bottomMarginWidget";
  public final static String RIGHT_MARGIN_WIDGET = "rightMarginWidget";

  private WidgetConstraintPanel mWidgetConstraintPanel;
  public final static int MATCH_CONSTRAINT = 1;
  public final static int WRAP_CONTENT = 2;
  public final static int FIXED = 0;
  private static final int UNCONNECTED = -1;
  private final ColorSet mColorSet;
  private int mCacheBottom;
  private int mCacheTop;
  private int mCacheLeft;
  private int mCacheRight;
  private boolean mCacheBaseline;
  private int mCacheWidth;
  private int mCacheHeight;
  private String mRatioString;
  private float mDimensionRatio;
  private int mDimensionRatioSide;
  private int mRatioHeight;
  private int mRatioWidth;
  private int mRatioLock;

  private int mWidth;
  private int mHeight;
  private int mBoxSize;

  private WidgetRender mWidgetRender = new WidgetRender();
  private ArrayList<Graphic> mGraphicList = new ArrayList<>();
  private MarginWidget mTopMargin;
  private MarginWidget mLeftMargin;
  private MarginWidget mRightMargin;
  private MarginWidget mBottomMargin;
  private HConstraintDisplay mHbar1;
  private HConstraintDisplay mHbar2;
  private VConstraintDisplay mVbar1;
  private VConstraintDisplay mVbar2;

  private ConnectButton mTopConnect;
  private ConnectButton mLeftConnect;
  private ConnectButton mRightConnect;
  private ConnectButton mBottomConnect;
  private final static int mConnectRadius = 7;
  private final static int mConnectSize = mConnectRadius * 2 + 1; // widget size

  private KillButton mTopKill;
  private KillButton mLeftKill;
  private KillButton mRightKill;
  private KillButton mBottomKill;
  private KillButton mBaselineKill;
  private AspectButton mAspectButton;
  private JLabel mAspectLabel;
  private JTextField mAspectText;

  private String[] statusString = {"Fixed", "Match Constraints", "Wrap Content"};
  public static final int DROP_DOWN_WIDTH = JBUI.scale(55);
  public static final int DROPDOWN_HEIGHT = JBUI.scale(25);
  public static final int DROPDOWN_OFFSET = JBUI.scale(12);

  public SingleWidgetView(WidgetConstraintPanel constraintPanel, ColorSet colorSet) {
    super(null);
    mColorSet = colorSet;

    mTopMargin = new MarginWidget(TOP_MARGIN_WIDGET);
    mLeftMargin = new MarginWidget(LEFT_MARGIN_WIDGET);
    mRightMargin = new MarginWidget(BOTTOM_MARGIN_WIDGET);
    mBottomMargin = new MarginWidget(RIGHT_MARGIN_WIDGET);
    mTopMargin.setToolTipText("Top Margin");
    mLeftMargin.setToolTipText("Left Margin");
    mRightMargin.setToolTipText("Right Margin");
    mBottomMargin.setToolTipText("Bottom Margin");

    mHbar1 = new HConstraintDisplay(mColorSet, true);
    mHbar2 = new HConstraintDisplay(mColorSet, false);
    mVbar1 = new VConstraintDisplay(mColorSet, true);
    mVbar2 = new VConstraintDisplay(mColorSet, false);

    mTopKill = new KillButton(mColorSet);
    mLeftKill = new KillButton(mColorSet);
    mRightKill = new KillButton(mColorSet);
    mBottomKill = new KillButton(mColorSet);
    mBaselineKill = new KillButton(mColorSet);
    mTopConnect = new ConnectButton(mColorSet);
    mLeftConnect = new ConnectButton(mColorSet);
    mRightConnect = new ConnectButton(mColorSet);
    mBottomConnect = new ConnectButton(mColorSet);

    mAspectButton = new AspectButton(mColorSet);
    mAspectText = new JTextField();
    mAspectLabel = new JLabel("ratio");

    mTopKill.setToolTipText("Delete Top Constraint");
    mLeftKill.setToolTipText("Delete Left Constraint");

    mRightKill.setName("deleteRightConstraintButton");
    mRightKill.setToolTipText("Delete Right Constraint");

    mBottomKill.setToolTipText("Delete Bottom Constraint");
    mBaselineKill.setToolTipText("Delete Baseline Constraint");
    mTopConnect.setToolTipText("Create a connection above");
    mLeftConnect.setToolTipText("Create a connection to the left");
    mRightConnect.setToolTipText("Create a connection to the right");
    mBottomConnect.setToolTipText("Create a connection below");
    mAspectButton.setToolTipText("Toggle Aspect Ratio Constraint");

    mHbar1.setSister(mHbar2);
    mHbar2.setSister(mHbar1);
    mVbar1.setSister(mVbar2);
    mVbar2.setSister(mVbar1);

    mWidgetConstraintPanel = constraintPanel;
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        resize();
      }
    });

    add(mTopMargin);
    add(mLeftMargin);
    add(mRightMargin);
    add(mBottomMargin);
    mTopMargin.addActionListener(e -> mWidgetConstraintPanel.setTopMargin(mTopMargin.getMargin()));
    mLeftMargin.addActionListener(e -> mWidgetConstraintPanel.setLeftMargin(mLeftMargin.getMargin()));
    mRightMargin.addActionListener(e -> mWidgetConstraintPanel.setRightMargin(mRightMargin.getMargin()));
    mBottomMargin.addActionListener(e -> mWidgetConstraintPanel.setBottomMargin(mBottomMargin.getMargin()));
    add(mTopKill);
    add(mLeftKill);
    add(mRightKill);
    add(mBottomKill);
    add(mTopConnect);
    add(mLeftConnect);
    add(mRightConnect);
    add(mBottomConnect);
    add(mBaselineKill);
    add(mAspectButton);
    add(mAspectText);
    add(mAspectLabel);

    add(mHbar1);
    add(mHbar2);
    add(mVbar1);
    add(mVbar2);

    mTopKill.addActionListener(e -> topKill());
    mLeftKill.addActionListener(e -> leftKill());
    mRightKill.addActionListener(e -> rightKill());
    mBottomKill.addActionListener(e -> bottomKill());
    mBaselineKill.addActionListener(e -> baselineKill());
    mTopConnect.addActionListener(e -> connectConstraint(Scout.Arrange.ConnectTop));
    mLeftConnect.addActionListener(e -> connectConstraint(Scout.Arrange.ConnectStart));
    mRightConnect.addActionListener(e -> connectConstraint(Scout.Arrange.ConnectEnd));
    mBottomConnect.addActionListener(e -> connectConstraint(Scout.Arrange.ConnectBottom));
    mAspectButton.addActionListener(e -> toggleAspect());
    mAspectText.addActionListener(e -> setAspectString());
    mAspectText.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        setAspectString();
      }
    });

    mHbar1.addPropertyChangeListener(TriStateControl.STATE, e -> setHorizontalState(mHbar1));
    mHbar2.addPropertyChangeListener(TriStateControl.STATE, e -> setHorizontalState(mHbar2));
    mVbar1.addPropertyChangeListener(TriStateControl.STATE, e -> setVerticalState(mVbar1));
    mVbar2.addPropertyChangeListener(TriStateControl.STATE, e -> setVerticalState(mVbar2));

    mGraphicList.add(mWidgetRender);
  }

  private void setAspectString() {
    String sideRatioString = "";
    if (mRatioString != null && mRatioString.contains(",")) {
      sideRatioString = mRatioString.substring(0, mRatioString.indexOf(',') + 1);
    }
    mRatioString = sideRatioString + mAspectText.getText();
    mWidgetConstraintPanel.setAspect(mRatioString);
    update();
  }

  private static String getRatioPart(String str) {
    if (str == null) {
      return "1:1";
    }
    int index = str.indexOf(',');
    if (index == -1) {
      return str;
    }
    return str.substring(index + 1);
  }

  private void toggleAspect() {
    int[] order = new int[4];
    int count = 0;
    order[count++] = RATIO_UNLOCK;

    if (mCacheHeight == MATCH_CONSTRAINT && mCacheWidth == MATCH_CONSTRAINT) {
      order[count++] = RATIO_LOCK;
    }
    if (mCacheHeight == MATCH_CONSTRAINT) {
      order[count++] = RATIO_LOCK_HEIGHT;
    }
    if (mCacheWidth == MATCH_CONSTRAINT) {
      order[count++] = RATIO_LOCK_WIDTH;
    }

    int lock = RATIO_UNLOCK;
    for (int i = 0; i < count; i++) {
      if (mRatioLock == order[i]) {
        lock = order[(i + 1) % count];
        break;
      }
    }
    mRatioLock = lock;

    switch (mRatioLock) {
      case RATIO_LOCK_WIDTH:
        mRatioString = "w," + getRatioPart(mRatioString);
        break;
      case RATIO_LOCK:
        mRatioString = getRatioPart(mRatioString);
        break;
      case RATIO_LOCK_HEIGHT:
        mRatioString = "h," + getRatioPart(mRatioString);
        break;
      case RATIO_UNLOCK:
        mRatioString = null;
        break;
    }
    mWidgetConstraintPanel.setAspect(mRatioString);
    update();
  }

  private void setHorizontalState(HConstraintDisplay state) {
    if (state == mHbar1) {
      mHbar2.setState(state.getState());
    }
    else {
      mHbar1.setState(state.getState());
    }
    updateTriangle();
    mHbar1.setToolTipText(statusString[state.getState()]);
    mHbar2.setToolTipText(statusString[state.getState()]);
    mWidgetConstraintPanel.setHorizontalConstraint(state.getState());
  }

  private void setVerticalState(VConstraintDisplay state) {
    if (state == mVbar1) {
      mVbar2.setState(state.getState());
    }
    else {
      mVbar1.setState(state.getState());
    }
    updateTriangle();
    mVbar1.setToolTipText(statusString[state.getState()]);
    mVbar2.setToolTipText(statusString[state.getState()]);
    mWidgetConstraintPanel.setVerticalConstraint(state.getState());
  }

  private void updateTriangle() {
    boolean show = mVbar1.getState() == MATCH_CONSTRAINT || mHbar1.getState() == MATCH_CONSTRAINT;
    mWidgetRender.mAspectLock.setShowTriangle(show);
  }

  private void topKill() {
    mWidgetConstraintPanel.killTopConstraint();
    mCacheTop = UNCONNECTED;
    update();
  }

  private void leftKill() {
    mWidgetConstraintPanel.killLeftConstraint();
    mCacheLeft = UNCONNECTED;
    update();
  }

  private void rightKill() {
    mWidgetConstraintPanel.killRightConstraint();
    mCacheRight = UNCONNECTED;
    update();
  }

  private void bottomKill() {
    mWidgetConstraintPanel.killBottomConstraint();
    mCacheBottom = UNCONNECTED;
    update();
  }

  private void baselineKill() {
    mWidgetConstraintPanel.killBaselineConstraint();
    mCacheBaseline = false;
    update();
  }

  private void connectConstraint(Scout.Arrange bottom) {
    NlComponent component = mWidgetConstraintPanel.mComponent;
    if (component != null) {
      Scout.arrangeWidgets(bottom, Collections.singletonList(component), false);
      ComponentModification modification = new ComponentModification(component, "Connect Constraint");
      // Temporary fix -- we should not use AttributeTransaction instead.
      component.startAttributeTransaction().applyToModification(modification);
      modification.commit();
    }
  }

  static int baselinePos(int height) {
    return (9 * height) / 10;
  }

  private void resize() {

    mWidth = getWidth();
    mHeight = getHeight();
    mBoxSize = Math.min(mWidth, mHeight) / 2 - DROPDOWN_OFFSET - 8;

    int boxLeft = (mWidth - mBoxSize) / 2;
    int boxTop = (mHeight - mBoxSize) / 2;
    int boxRight = boxLeft + mBoxSize;

    mWidgetRender.build(boxLeft, boxTop, mBoxSize);

    mTopMargin.setBounds(mWidth / 2 - DROP_DOWN_WIDTH / 2, boxTop - DROPDOWN_OFFSET - DROPDOWN_HEIGHT, DROP_DOWN_WIDTH, DROPDOWN_HEIGHT);
    mLeftMargin.setBounds(boxLeft - DROPDOWN_OFFSET - DROP_DOWN_WIDTH, (mHeight - DROPDOWN_HEIGHT) / 2, DROP_DOWN_WIDTH, DROPDOWN_HEIGHT);
    mRightMargin.setBounds(boxRight + DROPDOWN_OFFSET, (mHeight - DROPDOWN_HEIGHT) / 2, DROP_DOWN_WIDTH, DROPDOWN_HEIGHT);
    mBottomMargin.setBounds(mWidth / 2 - DROP_DOWN_WIDTH / 2, boxTop + mBoxSize + DROPDOWN_OFFSET, DROP_DOWN_WIDTH, DROPDOWN_HEIGHT);
    int rad = KillButton.sCircleRadius;
    int size = rad * 2;
    int centerX = boxLeft + mBoxSize / 2;
    int centerY = boxTop + mBoxSize / 2;
    mTopKill.setBounds(centerX - rad, boxTop - rad, size, size);
    mLeftKill.setBounds(boxLeft - rad, centerY - rad, size, size);
    mRightKill.setBounds(boxRight - rad, centerY - rad, size, size);
    mBottomKill.setBounds(centerX - rad, boxTop + mBoxSize - rad, size, size);
    mBaselineKill.setBounds(centerX - rad, boxTop + baselinePos(mBoxSize) - rad, size, size);

    rad = mConnectRadius;
    size = mConnectSize; // widget size
    mTopConnect.setBounds(centerX - rad, boxTop - size - DROPDOWN_OFFSET, size, size);
    mLeftConnect.setBounds(boxLeft - size - DROPDOWN_OFFSET, centerY - rad, size, size);
    mRightConnect.setBounds(boxRight + size, centerY - rad, size, size);
    mBottomConnect.setBounds(centerX - rad, boxTop + mBoxSize + DROPDOWN_OFFSET, size, size);
    mAspectButton.setBounds(boxLeft, boxTop, mBoxSize / 6, mBoxSize / 6);

    int tmpx, tmpy;
    tmpx = boxRight + 4;
    mAspectText
      .setBounds(boxRight + DROPDOWN_OFFSET, tmpy = boxTop + mBoxSize + DROPDOWN_OFFSET, Math.min(70, mWidth - tmpx),
                 mAspectText.getPreferredSize().height);
    Dimension labelSize = mAspectLabel.getPreferredSize();
    mAspectLabel.setBounds(boxRight + DROPDOWN_OFFSET, tmpy - labelSize.height, labelSize.width, labelSize.height);

    int barMargin = rad + 2;
    int middleSpace = 8;
    int barSize = 9;
    int barLong = mBoxSize / 2 - barMargin - middleSpace;

    centerY = boxTop + (mBoxSize - barSize) / 2;
    centerX = boxLeft + (mBoxSize - barSize) / 2;
    mHbar1.setBounds(boxLeft + barMargin, centerY, barLong, barSize);
    mHbar2.setBounds(boxRight - barLong - barMargin, centerY, barLong, barSize);
    mVbar1.setBounds(centerX, boxTop + barMargin, barSize, barLong);
    if (mCacheBaseline) {
      int top = boxTop + mBoxSize / 2 + barSize;
      int height = boxTop + baselinePos(mBoxSize) - top - 2;
      mVbar2.setBounds(centerX, top, barSize + 1, height);
    }
    else {
      mVbar2.setBounds(centerX, boxTop + mBoxSize - barMargin - barLong, barSize, barLong);
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (mWidth != getWidth() || mHeight != getHeight()) {
      resize();
    }
    Graphics2D g2d = (Graphics2D)g;

    boolean redraw = false;
    for (Graphic graphic : mGraphicList) {
      redraw |= graphic.paint(g2d, mColorSet);
    }
    if (redraw) {
      repaint();
    }
  }

  /**
   * Buttons that can kill the constraint
   */
  static class AspectButton extends JComponent {
    boolean mMouseIn;
    boolean mShow = true;
    ColorSet mColorSet;
    Color mColor;
    int[] mXPoints = new int[3];
    int[] mYPoints = new int[3];

    public static int sCircleRadius = 5;
    private ActionListener mListener;

    @Override
    public void paint(Graphics g) {
      if (mMouseIn && mShow) {
        icon.paintIcon(this, g, 0, 0);
      }
    }

    public void setShown(boolean show) {
      mShow = show;
    }

    public AspectButton(ColorSet colorSet) {
      mColorSet = colorSet;
      mColor = new Color(mColorSet.getInspectorFillColor().getRGB() & 0x88FFFFFF, true);
      setPreferredSize(size);
      setSize(size);
      setOpaque(false);

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent evt) {
          mMouseIn = true;
          repaint();
        }

        @Override
        public void mouseClicked(MouseEvent e) {
          mListener.actionPerformed(null);
        }

        @Override
        public void mouseExited(MouseEvent evt) {
          mMouseIn = false;
          repaint();
        }
      });
    }

    static Dimension size = new Dimension(sCircleRadius * 2, sCircleRadius * 2);
    Icon icon = new Icon() {

      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        g.setColor(JBColor.BLUE);
        mXPoints[0] = 1;
        mYPoints[0] = 1;
        mXPoints[1] = getIconWidth();
        mYPoints[1] = 1;
        mXPoints[2] = 1;
        mYPoints[2] = getIconHeight();
        if (mMouseIn) {
          g.setColor(mColor);
          g.fillPolygon(mXPoints, mYPoints, 3);
        }
      }

      @Override
      public int getIconWidth() {
        return getWidth();
      }

      @Override
      public int getIconHeight() {
        return getHeight();
      }
    };

    public void addActionListener(ActionListener listener) {
      mListener = listener;
    }
  }

  /**
   * Connect button
   */
  static class ConnectButton extends JComponent {
    boolean mMouseIn;
    boolean mShow = true;
    ColorSet mColorSet;
    public static int sCircleRadius = 5;

    public void addActionListener(ActionListener listener) {
      mListener = listener;
    }

    static Dimension size = new Dimension(sCircleRadius * 2, sCircleRadius * 2);

    private ActionListener mListener;

    public void setShown(boolean show) {
      mShow = show;
    }

    public ConnectButton(ColorSet colorSet) {
      mColorSet = colorSet;
      setPreferredSize(size);
      setSize(size);
      setOpaque(false);

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent evt) {
          mMouseIn = true;
          repaint();
        }

        @Override
        public void mouseClicked(MouseEvent e) {
          mListener.actionPerformed(null);
        }

        @Override
        public void mouseExited(MouseEvent evt) {
          mMouseIn = false;
          repaint();
        }
      });
    }

    @Override
    public void paint(Graphics g) {
      StudioIcons.LayoutEditor.Properties.ADD_CONNECTION.paintIcon(this, g, 0, 0);
    }
  }

  /**
   * Buttons that can kill the constraint
   */
  public static class KillButton extends JComponent {
    boolean mMouseIn;
    boolean mShow = true;
    ColorSet mColorSet;

    public static int sCircleRadius = 5;
    private ActionListener mListener;

    @Override
    public void paint(Graphics g) {
      if (mMouseIn && mShow) {
        icon.paintIcon(this, g, 0, 0);
      }
    }

    public void setShown(boolean show) {
      mShow = show;
    }

    public KillButton(ColorSet colorSet) {
      mColorSet = colorSet;
      setPreferredSize(size);
      setSize(size);
      setOpaque(false);

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent evt) {
          mMouseIn = true;
          repaint();
        }

        @Override
        public void mouseClicked(MouseEvent e) {
          mListener.actionPerformed(null);
        }

        @Override
        public void mouseExited(MouseEvent evt) {
          mMouseIn = false;
          repaint();
        }
      });
    }

    static Dimension size = new Dimension(sCircleRadius * 2, sCircleRadius * 2);
    Icon icon = new Icon() {

      private final BasicStroke myStroke = new BasicStroke(1);

      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        g.setColor(JBColor.BLUE);
        if (mMouseIn) {
          drawCircle((Graphics2D)g, x + sCircleRadius, y + sCircleRadius, sCircleRadius);
        }
      }

      /**
       * Draw a circle representing the connection
       *
       * @param g     graphics context
       * @param x      x coordinate of the circle
       * @param y      y coordinate of the circle
       * @param radius radius of the circle
       */
      private void drawCircle(Graphics2D g, int x, int y, int radius) {
        g.setColor(mColorSet.getInspectorConstraintColor());
        g.drawRoundRect(x - radius, y - radius,
                        radius * 2, radius * 2, radius * 2, radius * 2);
        g.fillRoundRect(x - radius, y - radius,
                        radius * 2, radius * 2, radius * 2, radius * 2);


        g.setColor(mColorSet.getInspectorBackgroundColor());
        g.setStroke(myStroke);

        g.drawLine(x - 4, y - 4, x + 4, y + 4);
        g.drawLine(x - 4, y + 4, x + 4, y - 4);
      }

      @Override
      public int getIconWidth() {
        return sCircleRadius * 2 + 2;
      }

      @Override
      public int getIconHeight() {
        return sCircleRadius * 2;
      }
    };

    public void addActionListener(ActionListener listener) {
      mListener = listener;
    }
  }

  private void update() {
    configureUi(mCacheBottom, mCacheTop, mCacheLeft, mCacheRight, mCacheBaseline, mCacheWidth, mCacheHeight, mRatioString);
  }

  /**
   * @param bottom      sets the margin -1 = no margin
   * @param top         sets the margin -1 = no margin
   * @param left        sets the margin -1 = no margin
   * @param right       sets the margin -1 = no margin
   * @param baseline    sets the name of baseline connection null = no baseline
   * @param width       the horizontal constraint state 0,1,2 = FIXED, SPRING, WRAP respectively
   * @param height      the vertical constraint state 0,1,2 = FIXED, SPRING, WRAP respectively
   * @param ratioString The side that will be constrained
   */
  public void configureUi(int bottom, int top, int left, int right, boolean baseline, int width, int height, String ratioString) {
    mRatioString = ratioString;
    parseDimensionRatio(ratioString);
    String aspectText = "";
    if (ratioString != null) {
      if (ratioString.contains(",")) {
        aspectText = ratioString.substring(ratioString.indexOf(',') + 1);
        if (Character.toLowerCase(ratioString.charAt(0)) == 'w') {
          mRatioLock = RATIO_LOCK_WIDTH;
        }
        else {
          mRatioLock = RATIO_LOCK_HEIGHT;
        }
      }
      else {
        aspectText = ratioString;
        mRatioLock = RATIO_LOCK;
      }
    }
    else {
      mRatioLock = RATIO_UNLOCK;
    }
    if (mRatioHeight == -1 && mDimensionRatio > 0) { // it is of the form "[WH],float" see if you can get a nice ratio
      int[] split = splitRatio(mDimensionRatio);
      if (split != null) {
        mRatioWidth = split[1];
        mRatioHeight = split[0];
      }
    }
    mAspectText.setText(aspectText);
    configureUi(bottom, top, left, right, baseline, width, height);
  }

  private void configureUi(int bottom, int top, int left, int right, boolean baseline, int width, int height) {
    mCacheBottom = bottom;
    mCacheTop = top;
    mCacheLeft = left;
    mCacheRight = right;
    mCacheBaseline = baseline;
    mCacheWidth = width;
    mCacheHeight = height;
    mTopMargin.setVisible(top != UNCONNECTED);
    mLeftMargin.setVisible(left != UNCONNECTED);
    mRightMargin.setVisible(right != UNCONNECTED);
    mBottomMargin.setVisible(bottom != UNCONNECTED);
    mTopMargin.setMargin(top);
    mLeftMargin.setMargin(left);
    mRightMargin.setMargin(right);
    mBottomMargin.setMargin(bottom);
    mWidgetRender.setConstraints(left, top, right, bottom);
    mWidgetRender.mBaseline = baseline;
    mTopKill.setVisible(top != UNCONNECTED);
    mLeftKill.setVisible(left != UNCONNECTED);
    mRightKill.setVisible(right != UNCONNECTED);
    mBottomKill.setVisible(bottom != UNCONNECTED);
    mTopConnect.setVisible(top == UNCONNECTED);
    mLeftConnect.setVisible(left == UNCONNECTED);
    mRightConnect.setVisible(right == UNCONNECTED);
    mBottomConnect.setVisible(bottom == UNCONNECTED);
    mBaselineKill.setVisible(baseline);
    mAspectButton.setVisible(true);
    mAspectText.setVisible(mRatioString != null);
    mAspectLabel.setVisible(mRatioString != null);

    mHbar1.setState(width);
    mHbar2.setState(width);
    mVbar1.setState(height);
    mVbar2.setState(height);

    mHbar1.setVisible(mDimensionRatioSide != ConstraintWidget.HORIZONTAL);
    mHbar2.setVisible(mDimensionRatioSide != ConstraintWidget.HORIZONTAL);
    mVbar1.setVisible(mDimensionRatioSide != ConstraintWidget.VERTICAL);
    mVbar2.setVisible(mDimensionRatioSide != ConstraintWidget.VERTICAL);


    mVbar1.setToolTipText(statusString[height]);
    mVbar2.setToolTipText(statusString[height]);
    mHbar1.setToolTipText(statusString[width]);
    mHbar2.setToolTipText(statusString[width]);
    resize();
    repaint();
  }

  /**
   * Set the ratio of the widget from a given string of format [H|V],[float|x:y] or [float|x:y]
   *
   * @param ratio
   */
  private void parseDimensionRatio(String ratio) {
    mRatioHeight = -1;
    mRatioWidth = -1;
    if (ratio == null || ratio.isEmpty()) {
      mDimensionRatio = 0;
      mDimensionRatioSide = ConstraintWidget.UNKNOWN;

      return;
    }
    int dimensionRatioSide = ConstraintWidget.UNKNOWN;
    float dimensionRatio = 0;
    int len = ratio.length();
    int commaIndex = ratio.indexOf(',');
    if (commaIndex > 0 && commaIndex < len - 1) {
      String dimension = ratio.substring(0, commaIndex);
      if (dimension.equalsIgnoreCase("W")) {
        dimensionRatioSide = ConstraintWidget.HORIZONTAL;
      }
      else if (dimension.equalsIgnoreCase("H")) {
        dimensionRatioSide = ConstraintWidget.VERTICAL;
      }
      commaIndex++;
    }
    else {
      commaIndex = 0;
    }
    int colonIndex = ratio.indexOf(':');

    if (colonIndex >= 0 && colonIndex < len - 1) {
      String nominator = ratio.substring(commaIndex, colonIndex);
      String denominator = ratio.substring(colonIndex + 1);
      if (!nominator.isEmpty() && !denominator.isEmpty()) {
        try {
          float nominatorValue = Float.parseFloat(nominator);
          float denominatorValue = Float.parseFloat(denominator);
          dimensionRatio = Math.abs(nominatorValue / denominatorValue);
          mRatioHeight = (int)nominatorValue;
          mRatioWidth = (int)denominatorValue;
        }
        catch (NumberFormatException e) {
          // Ignore
        }
      }
    }
    else {
      String r = ratio.substring(commaIndex);
      if (!r.isEmpty()) {
        try {
          dimensionRatio = Float.parseFloat(r);
        }
        catch (NumberFormatException e) {
          // Ignore
        }
      }
    }

    if (dimensionRatio > 0) {
      mDimensionRatio = dimensionRatio;
      mDimensionRatioSide = dimensionRatioSide;
    }
  }

  private static int[][] ratios = {{1, 1}, {4, 3}, {3, 2}, {5, 3}, {16, 9}, {2, 1}, {21, 9}, {5, 2}, {3, 1}, {4, 1}};

  static {
    Arrays.sort(ratios, (a, b) -> Float.compare(a[0] / (float)a[1], b[0] / (float)b[1]));
  }

  // use to split the ratios
  private static int[] splitRatio(float ratio) {
    if (ratio >= 1) {
      for (int[] r : ratios) {
        if (r[0] / (float)r[1] >= ratio) {
          return r;
        }
      }
    }
    else {
      for (int[] r : ratios) {
        if (r[1] / (float)r[0] <= ratio) {
          return r;
        }
      }
    }
    return null;
  }

  /**
   * Interface to widgets drawn on the screen
   */
  interface Graphic {
    boolean paint(Graphics2D g, ColorSet colorSet);
  }

  static class Box implements Graphic {
    int mX, mY, mWidth, mHeight;
    int mEdges;
    public final static int TOP = 1;
    public final static int BOTTOM = 2;
    public final static int LEFT = 4;
    public final static int RIGHT = 8;
    public final static int ALL = TOP | BOTTOM | LEFT | RIGHT;
    public boolean mDisplay;

    Box(int x, int y, int w, int h, int edges, boolean display) {
      mX = x;
      mY = y;
      mHeight = h;
      mWidth = w;
      mEdges = edges;
      mDisplay = display;
    }

    @Override
    public boolean paint(Graphics2D g, ColorSet colorSet) {
      if (mEdges == 0 || !mDisplay) {
        return false;
      }
      g.setColor(colorSet.getInspectorFillColor());
      g.fillRect(mX, mY, mWidth + 1, mHeight + 1);
      g.setColor(colorSet.getInspectorStrokeColor());
      if (mEdges == ALL) {
        g.drawRect(mX, mY, mWidth, mHeight);
      }
      else {
        if ((mEdges & TOP) != 0) {
          g.setColor(colorSet.getInspectorConstraintColor());
          g.drawLine(mX, mY, mX + mWidth, mY);
        }
        if ((mEdges & BOTTOM) != 0) {
          g.setColor(colorSet.getInspectorConstraintColor());
          g.drawLine(mX, mY + mHeight, mX + mWidth, mY + mHeight);
        }
        if ((mEdges & LEFT) != 0) {
          g.setColor(colorSet.getInspectorConstraintColor());
          g.drawLine(mX, mY, mX, mY + mWidth);
        }
        if ((mEdges & RIGHT) != 0) {
          g.setColor(colorSet.getInspectorConstraintColor());
          g.drawLine(mX + mWidth, mY, mX + mWidth, mY + mHeight);
        }
      }
      return false;
    }
  }

  static class BaseLineBox extends Box {
    String mTitle = null;
    boolean mBaseline;
    boolean mDisplay;

    BaseLineBox(String title, int x, int y, int w, int h, boolean baseline, boolean display) {
      super(x, y, w, h, display ? ALL : 0, true);
      mTitle = title;
      mBaseline = baseline;
      mDisplay = display;
    }

    @Override
    public boolean paint(Graphics2D g, ColorSet colorSet) {

      if (mDisplay) {
        Stroke defaultStroke = g.getStroke();
        g.setColor(colorSet.getInspectorFillColor());
        g.fillRect(mX, mY, mWidth + 1, mHeight + 1);
        g.setColor(colorSet.getInspectorStrokeColor());

        if (mBaseline) {
          g.drawLine(mX, mY, mX, mY + mWidth);
          g.drawLine(mX + mWidth, mY, mX + mWidth, mY + mHeight);
          //g.setStroke(DASHED_STROKE);
          //g.drawLine(mX,mY,mX+mWidth,mY);
          //g.drawLine(mX,mY+mHeight,mX+mWidth,mY+mHeight);

          int y = mY + baselinePos(mHeight);

          g.setStroke(defaultStroke);
          g.drawLine(mX, y, mX + mWidth, y);
        }
        else {
          g.drawRect(mX, mY, mWidth, mHeight);
        }

        if (mTitle != null) {
          int decent = g.getFontMetrics().getDescent();
          g.drawString(mTitle, mX + 2, mY + mHeight - decent);
        }
      }
      return false;
    }
  }

  static class AspectLock implements Graphic {
    int mX, mY, mWidth, mHeight;
    int mLock;
    private int mRatioHeight;
    private int mRatioWidth;
    int[] mXPoints = new int[3];
    int[] mYPoints = new int[3];
    BasicStroke mStroke = new BasicStroke(2f);
    private boolean mShowTriangle;

    AspectLock(int x, int y, int w, int h, int lock, int ratioWidth, int ratioHeight) {
      mX = x;
      mY = y;
      mHeight = h;
      mWidth = w;
      mLock = lock;
      mXPoints[0] = mX;
      mYPoints[0] = mY;
      mXPoints[1] = mX + mWidth / 6;
      mYPoints[1] = mY;
      mXPoints[2] = mX;
      mYPoints[2] = mY + mHeight / 6;
      mRatioHeight = ratioHeight;
      mRatioWidth = ratioWidth;
    }

    public void setShowTriangle(boolean show) {
      mShowTriangle = show;
    }

    @Override
    public boolean paint(Graphics2D g, ColorSet colorSet) {
      if (mShowTriangle) {
        g.setColor(colorSet.getInspectorHighlightsStrokeColor());
        g.drawPolygon(mXPoints, mYPoints, 3);
      }
      if (mLock == RATIO_UNLOCK) {
        return false;
      }
      g.setColor(colorSet.getInspectorStrokeColor());
      g.fillPolygon(mXPoints, mYPoints, 3);
      FontMetrics fm = g.getFontMetrics();
      if (mRatioHeight != -1) {
        String str = Integer.toString(mRatioHeight);
        Rectangle2D bounds = fm.getStringBounds(str, g);
        g.drawString(str, mX + mWidth / 12 - (int)(bounds.getWidth() / 2), mY - fm.getDescent());
      }
      if (mRatioWidth != -1) {
        String str = Integer.toString(mRatioWidth);
        Rectangle2D bounds = fm.getStringBounds(str, g);
        g.drawString(str, mX - (int)bounds.getWidth() - 2, mY + fm.getAscent());
      }
      Stroke prevStroke = g.getStroke();
      g.setStroke(mStroke);
      if (mLock == RATIO_LOCK_WIDTH) {
        g.drawLine(mX, mY + 1, mX, mY + mHeight - 1);
        g.drawLine(mX + mWidth, mY + 1, mX + mWidth, mY + mHeight - 1);
        g.drawLine(mX + 1, mY + mHeight / 2, mX + mWidth - 1, mY + mHeight / 2);
      }
      else if (mLock == RATIO_LOCK_HEIGHT) {
        g.drawLine(mX + 1, mY, mX + mWidth - 1, mY);
        g.drawLine(mX + 1, mY + mHeight, mX + mWidth - 1, mY + mHeight);
        g.drawLine(mX + mWidth / 2, mY + 1, mX + mWidth / 2, mY + mHeight - 1);
      }
      g.setStroke(prevStroke);
      return false;
    }
  }


  static class Line implements Graphic {
    int mX1, mY1, mX2, mY2;
    boolean mDisplay;
    final static float[] dash1 = {1.0f, 3.0f};
    final static private Stroke sDashStroke = new BasicStroke(1.0f,
                                                              BasicStroke.CAP_BUTT,
                                                              BasicStroke.JOIN_MITER,
                                                              2.0f, dash1, 0.0f);

    Line(int x1, int y1, int x2, int y2, boolean display) {
      mX1 = x1;
      mY1 = y1;
      mX2 = x2;
      mY2 = y2;
      mDisplay = display;
    }

    @Override
    public boolean paint(Graphics2D g, ColorSet colorSet) {
      if (mDisplay) {
        drawCircle(g, mX1, mY1, mDisplay);
        g.drawLine(mX1, mY1, mX2, mY2);
      }
      else {
        Stroke stroke = g.getStroke();
        g.setStroke(sDashStroke);
        g.drawLine(mX1, mY1, mX2, mY2);
        g.setStroke(stroke);
      }
      return false;
    }
  }

  private static void drawCircle(Graphics2D g, int x, int y, boolean fill) {
    if (fill) {
      g.fillRoundRect(x - 5, y - 5, 10, 10, 10, 10);
    }
    else {
      g.drawRoundRect(x - 5, y - 5, 10, 10, 10, 10);
    }
  }

  static class LineArrow implements Graphic {
    int mX1, mY1, mX2, mY2;
    boolean mDisplay;
    int[] mXArrow = new int[3];
    int[] mYArrow = new int[3];

    LineArrow(int x1, int y1, int x2, int y2, boolean display) {
      mX1 = x1;
      mY1 = y1;
      mX2 = x2;
      mY2 = y2;
      mDisplay = display;
      mXArrow[0] = x2;
      mYArrow[0] = y2;
      mXArrow[1] = x2 - ConnectionDraw.CONNECTION_ARROW_SIZE;
      mYArrow[1] = y2 - ConnectionDraw.ARROW_SIDE;
      mXArrow[2] = x2 + ConnectionDraw.CONNECTION_ARROW_SIZE;
      mYArrow[2] = y2 - ConnectionDraw.ARROW_SIDE;
    }

    @Override
    public boolean paint(Graphics2D g, ColorSet colorSet) {
      if (mDisplay) {
        g.drawLine(mX1, mY1, mX2, mY2 - 2);
        g.fillPolygon(mXArrow, mYArrow, 3);
        drawCircle(g, mX1, mY1, mDisplay);
      }
      return false;
    }
  }

  /**
   * This renders the basic graphic of a Scene
   */
  class WidgetRender implements Graphic {
    int mMarginLeft;
    int mMarginTop;
    int mMarginRight;
    int mMarginBottom;
    boolean mBaseline;
    Box mWidgetCenter;
    Line mTopArrow;
    Line mLeftArrow;
    Line mRightArrow;
    Line mBottomArrow;
    LineArrow mBaselineArrow;
    AspectLock mAspectLock;

    void setConstraints(int left, int top, int right, int bottom) {
      mMarginTop = top;
      mMarginLeft = left;
      mMarginRight = right;
      mMarginBottom = bottom;
    }

    /**
     * build the widgets used to render the scene
     */
    public void build(int boxLeft, int boxTop, int boxSize) {
      mWidgetCenter = new BaseLineBox(null, boxLeft, boxTop, boxSize, boxSize, mBaseline, true);
      mAspectLock = new AspectLock(boxLeft, boxTop, boxSize, boxSize, mRatioLock, mRatioWidth, mRatioHeight);
      int baseArrowX = boxLeft + boxSize / 2;
      mBaselineArrow =
        new LineArrow(baseArrowX, boxTop + baselinePos(boxSize), baseArrowX, boxTop + boxSize / 2, mBaseline);

      int centerY = boxTop + boxSize / 2;
      int centerX = boxLeft + boxSize / 2;
      mTopArrow = new Line(centerX, boxTop, centerX, boxTop - DROPDOWN_OFFSET, (mMarginTop >= 0));
      mLeftArrow = new Line(boxLeft, centerY, boxLeft - DROPDOWN_OFFSET, centerY, (mMarginLeft >= 0));
      mRightArrow = new Line(boxLeft + boxSize, centerY, boxLeft + boxSize + DROPDOWN_OFFSET, centerY, (mMarginRight >= 0));
      mBottomArrow = new Line(centerX, boxTop + boxSize, centerX, boxTop + boxSize + DROPDOWN_OFFSET, (mMarginBottom >= 0));

      updateTriangle();
    }

    @Override
    public boolean paint(Graphics2D g, ColorSet colorSet) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setColor(mColorSet.getInspectorBackgroundColor());
      g.fillRect(0, 0, getWidth(), getHeight());
      g.setColor(mColorSet.getInspectorStrokeColor());
      mWidgetCenter.paint(g, colorSet);
      mAspectLock.paint(g, colorSet);

      mTopArrow.paint(g, colorSet);
      mLeftArrow.paint(g, colorSet);
      mRightArrow.paint(g, colorSet);
      mBottomArrow.paint(g, colorSet);
      mBaselineArrow.paint(g, colorSet);

      return false;
    }
  }

  /*-----------------------------------------------------------------------*/
  // TriStateControl
  /*-----------------------------------------------------------------------*/

  static class TriStateControl extends JComponent {
    boolean mMouseIn;
    int mState;
    Color mBackground;
    Color mLineColor;
    Color mMouseOverColor;
    TriStateControl mSisterControl;
    public final static String STATE = "state";

    TriStateControl(ColorSet colorSet) {
      mBackground = colorSet.getInspectorFillColor();
      mLineColor = colorSet.getInspectorStrokeColor();
      mMouseOverColor = colorSet.getInspectorConstraintColor();

      setPreferredSize(new Dimension(200, 30));

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          int oldValue = mState;
          mState = (mState + 1) % 3;
          firePropertyChange(STATE, oldValue, mState);
          repaint();
        }

        @Override
        public void mouseExited(MouseEvent e) {
          mMouseIn = false;
          if (mSisterControl != null) {
            mSisterControl.mMouseIn = mMouseIn;
            mSisterControl.repaint();
          }
          repaint();
        }

        @Override
        public void mouseEntered(MouseEvent e) {
          mMouseIn = true;
          if (mSisterControl != null) {
            mSisterControl.mMouseIn = mMouseIn;
            mSisterControl.repaint();
          }
          repaint();
        }
      });
      addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          resize();
        }
      });
    }

    public void setSister(TriStateControl sister) {
      mSisterControl = sister;
    }

    public int getState() {
      return mState;
    }

    public void setState(int state) {
      mState = state;
      repaint();
    }

    void resize() {
    }

    @Override
    protected void paintComponent(Graphics g) {
      int width = getWidth() - 1;
      int height = getHeight() - 1;
      g.setColor(mBackground);
      // g.fillRect(0, 0, getWidth(), getHeight());
      ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(mMouseIn ? mMouseOverColor : mLineColor);
      drawState(g, width, height);
    }

    void drawState(Graphics g, int width, int height) {

    }
  }

  /*-----------------------------------------------------------------------*/
  // HConstraintDisplay
  /*-----------------------------------------------------------------------*/

  static class HConstraintDisplay extends TriStateControl {
    boolean mDirection;

    HConstraintDisplay(ColorSet colorSet, boolean direction) {
      super(colorSet);
      mDirection = direction;
      setPreferredSize(new Dimension(200, 30));
    }

    @Override
    void resize() {

    }

    @Override
    void drawState(Graphics g, int width, int height) {
      int start = 0;
      int pos = height / 2;
      switch (mState) {
        case FIXED:
          drawFixedHorizontalConstraint(g, start, pos, width);
          break;
        case MATCH_CONSTRAINT:
          drawSpringHorizontalConstraint(g, start, pos, width);
          break;
        case WRAP_CONTENT:
          drawWrapHorizontalConstraint(g, start, pos, width, mDirection);
          break;
      }
    }

    /**
     * Utility function to draw an horizontal spring
     *
     * @param g graphics context
     * @param l left end
     * @param y y origin
     * @param r right end
     */

    private static void drawSpringHorizontalConstraint(Graphics g, int l, int y, int r) {
      int m = 7;
      int d = 3;
      int w = (r - l);
      int ni = (w / (2 * d)) - 1;
      int margin = (w - (ni * 2 * d)) / 2;

      g.drawLine(l, y - m, l, y + m);
      g.drawLine(l, y, l + margin, y);
      for (int i = l + margin; i <= r - margin - 2 * d; i += 2 * d) {
        g.drawLine(i, y, i + d, y - d);
        g.drawLine(i + d, y - d, i + d, y + d);
        g.drawLine(i + d, y + d, i + 2 * d, y);
      }
      g.drawLine(r - margin, y, r, y);
      g.drawLine(r, y - m, r, y + m);
    }

    /**
     * Utility function to draw the wrap horizontal constraint (drawing chevrons)
     *
     * @param g                    graphics context
     * @param l                    left end
     * @param y                    y origin
     * @param r                    right end
     * @param directionLeftToRight indicates the direction of the chevrons
     */
    private static void drawWrapHorizontalConstraint(Graphics g, int l, int y, int r,
                                                     boolean directionLeftToRight) {
      int d = 3;
      int spacing = d + 3;
      Graphics2D g2 = (Graphics2D)g;

      if (directionLeftToRight) {
        for (int x = l; x <= r - d; x += spacing) {
          g2.drawLine(x, y - d, x + d, y);
          g2.drawLine(x + d, y, x, y + d);
        }
      }
      else {
        for (int x = r; x >= l + d; x -= spacing) {
          g2.drawLine(x, y - d, x - d, y);
          g2.drawLine(x - d, y, x, y + d);
        }
      }
    }

    /**
     * Utility function to draw a fixed horizontal constraint
     *
     * @param g graphics context
     * @param l left end
     * @param y y origin
     * @param r right end
     */
    private static void drawFixedHorizontalConstraint(Graphics g, int l, int y, int r) {
      int m = 2;
      g.drawLine(l, y - m, l, y + m);
      g.drawLine(l, y, r, y);
      g.drawLine(r, y - m, r, y + m);
    }
  }

  /*-----------------------------------------------------------------------*/
  // VConstraintDisplay
  /*-----------------------------------------------------------------------*/

  static class VConstraintDisplay extends TriStateControl {
    boolean mDirection;

    VConstraintDisplay(ColorSet colorSet, boolean direction) {
      super(colorSet);
      mDirection = direction;
      setPreferredSize(new Dimension(30, 200));
    }

    @Override
    void drawState(Graphics g, int width, int height) {
      int start = 0;
      int pos = width / 2;
      switch (mState) {
        case FIXED:
          drawFixedVerticalConstraint(g, start, pos, height);
          break;
        case MATCH_CONSTRAINT:
          drawSpringVerticalConstraint(g, start, pos, height);
          break;
        case WRAP_CONTENT:
          drawWrapVerticalConstraint(g, start, pos, height, mDirection);
          break;
      }
    }

    /**
     * Utility function to draw a vertical spring
     *
     * @param g graphics context
     * @param t top end
     * @param x x origin
     * @param b bottom end
     */
    private static void drawSpringVerticalConstraint(Graphics g, int t, int x, int b) {
      int m = 4;
      int d = 3;
      int h = (b - t);
      int ni = (h / (2 * d)) - 1;
      int margin = (h - (ni * 2 * d)) / 2;

      g.drawLine(x - m, t, x + m, t);
      g.drawLine(x, t, x, t + margin);
      for (int i = t + margin; i <= b - margin - 2 * d; i += 2 * d) {
        g.drawLine(x, i, x + d, i + d);
        g.drawLine(x + d, i + d, x - d, i + d);
        g.drawLine(x - d, i + d, x, i + 2 * d);
      }
      g.drawLine(x, b - margin, x, b);
      g.drawLine(x - m, b, x + m, b);
    }

    /**
     * Utility function to draw a vertical constraint
     *
     * @param g graphics context
     * @param t top end
     * @param x x origin
     * @param b bottom end
     */
    private static void drawFixedVerticalConstraint(Graphics g, int t, int x, int b) {
      int m = 2;
      g.drawLine(x - m, t, x + m, t);
      g.drawLine(x, t, x, b);
      g.drawLine(x - m, b, x + m, b);
    }

    /**
     * Utility function to draw the wrap vertical constraint (drawing chevrons)
     *
     * @param g           graphics context
     * @param t           top end
     * @param x           x origin
     * @param b           bottom end
     * @param topToBottom indicates the direction of the chevrons
     */
    private static void drawWrapVerticalConstraint(Graphics g, int t, int x, int b,
                                                   boolean topToBottom) {
      int d = 3;
      int spacing = d + 3;

      if (topToBottom) {
        for (int y = t; y <= b - d; y += spacing) {
          g.drawLine(x - d, y, x, y + d);
          g.drawLine(x + d, y, x, y + d);
        }
      }
      else {
        for (int y = b; y >= t + d; y -= spacing) {
          g.drawLine(x - d, y, x, y - d);
          g.drawLine(x + d, y, x, y - d);
        }
      }
    }
  }
}
