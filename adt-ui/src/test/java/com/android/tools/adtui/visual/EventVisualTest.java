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

package com.android.tools.adtui.visual;

import com.android.tools.adtui.*;
import com.android.tools.adtui.common.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.EventAction;
import com.android.tools.adtui.model.RangedSeries;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class EventVisualTest extends VisualTest {

  private static final int IMAGE_WIDTH = 16;
  private static final int IMAGE_HEIGHT = 16;
  private static final String[] ACTIVITY_NAMES = {
    "SigninActivity",
    "GamemodeActivity",
    "MainMenuActivity",
    "OptionsActivity",
    "MultiplayerActivity"
  };

  class MockActivity {

    String myName;
    long myStartTimeUs;

    public MockActivity() {
      myName = EventVisualTest.ACTIVITY_NAMES[(int)(Math.random() * ACTIVITY_NAMES.length)];
      myStartTimeUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
      addSelf();
    }

    private void addSelf() {
      long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
      EventAction<StackedEventComponent.Action, String> event =
        new EventAction<StackedEventComponent.Action, String>(myStartTimeUs, 0,
                                                              StackedEventComponent.Action.ACTIVITY_STARTED, myName);
      mActivityData.add(nowUs, event);
    }

    public void tearDown() {
      long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
      EventAction<StackedEventComponent.Action, String> event =
        new EventAction<StackedEventComponent.Action, String>(myStartTimeUs, nowUs,
                                                              StackedEventComponent.Action.ACTIVITY_COMPLETED, myName);
      mActivityData.add(nowUs, event);
    }
  }

  private static Icon buildStaticImage(Color color) {
    BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT,
                                            BufferedImage.TYPE_4BYTE_ABGR);
    for (int y = 0; y < IMAGE_HEIGHT; y++) {
      for (int x = 0; x < IMAGE_WIDTH; x++) {
        image.setRGB(x, y, color.getRGB());
      }
    }
    ImageIcon icon = new ImageIcon(image);
    return icon;
  }

  private static final Icon[] MOCK_ICONS = {
    buildStaticImage(Color.red),
    buildStaticImage(Color.green),
    buildStaticImage(Color.blue),
  };

  /**
   * Enum that defines what Icon to draw for an event action.
   */
  public enum ActionType {
    TOUCH,
    HOLD,
    DOUBLE_TAP;
  }


  private ArrayList<MockActivity> myOpenActivites;

  private SimpleEventComponent mSimpleEventComponent;

  private StackedEventComponent myStackedEventComponent;

  private AxisComponent mTimeAxis;

  private DefaultDataSeries<EventAction<SimpleEventComponent.Action, ActionType>>
    mData;

  private DefaultDataSeries<EventAction<StackedEventComponent.Action, String>>
    mActivityData;

  private AnimatedTimeRange mAnimatedRange;
  private AnimatedTimeRange mTimelineRange;
  private static final int AXIS_SIZE = 100;
  private static final int ACTIVITY_GRAPH_SIZE = 31;

  @Override
  protected List<Animatable> createComponentsList() {
    long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
    Range xRange = new Range(nowUs, nowUs + TimeUnit.SECONDS.toMicros(60));
    Range xTimelineRange = new Range(0, 0);

    mData = new DefaultDataSeries<>();
    mActivityData = new DefaultDataSeries<>();
    mSimpleEventComponent = new SimpleEventComponent(new RangedSeries<>(xRange, mData), MOCK_ICONS);
    myStackedEventComponent = new StackedEventComponent(new RangedSeries(xRange, mActivityData), ACTIVITY_GRAPH_SIZE);
    mAnimatedRange = new AnimatedTimeRange(xRange, 0);
    mTimelineRange = new AnimatedTimeRange(xTimelineRange, nowUs);
    myOpenActivites = new ArrayList<>();
    // add horizontal time axis
    mTimeAxis = new AxisComponent(xTimelineRange, xTimelineRange, "TIME",
                                  AxisComponent.AxisOrientation.BOTTOM,
                                  AXIS_SIZE, AXIS_SIZE, false, TimeAxisFormatter.DEFAULT);
    List<Animatable> componentsList = new ArrayList<>();
    // Add the scene components to the list
    componentsList.add(xRange);
    componentsList.add(xTimelineRange);
    componentsList.add(mAnimatedRange);
    componentsList.add(mTimelineRange);
    componentsList.add(mTimeAxis);
    componentsList.add(mSimpleEventComponent);
    componentsList.add(myStackedEventComponent);
    return componentsList;

  }

  @Override
  public String getName() {
    return "EventChart";
  }

  private void performTapAction() {
    long now = System.currentTimeMillis();
    EventAction<SimpleEventComponent.Action, ActionType> event =
      new EventAction<SimpleEventComponent.Action, ActionType>(now,
                                                               0, SimpleEventComponent.Action.DOWN, ActionType.HOLD);
    mData.add(now, event);
    event = new EventAction<SimpleEventComponent.Action, ActionType>(now,
                                                                     now, SimpleEventComponent.Action.UP, ActionType.TOUCH);
    mData.add(now, event);
  }

  private void addActivityCreatedEvent() {
    performTapAction();
    myOpenActivites.add(new MockActivity());
  }

  private void addActivityFinishedEvent() {
    //Find existing open activity.
    if (myOpenActivites.size() > 0) {
      MockActivity activity = myOpenActivites.remove(myOpenActivites.size() - 1);
      activity.tearDown();
      performTapAction();
    }
  }

  @Override
  protected void populateUi(@NotNull JPanel panel) {
    panel.setLayout(new BorderLayout());
    JLayeredPane timelinePane = createMockTimeline();
    panel.add(timelinePane, BorderLayout.CENTER);

    JPanel controls = new JPanel();
    LayoutManager manager = new BoxLayout(controls, BoxLayout.Y_AXIS);
    controls.setLayout(manager);
    panel.add(controls, BorderLayout.WEST);
    controls.add(VisualTest.createButton("Add Activity", new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        addActivityCreatedEvent();
      }
    }));
    controls.add(VisualTest.createButton("Close Top Activity", new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        addActivityFinishedEvent();
      }
    }));
    controls.add(VisualTest.createButton("Close Random Activity", new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int size = myOpenActivites.size();
        if(size != 0) {
          MockActivity m = myOpenActivites.remove((int)(Math.random() * size));
          m.tearDown();
          performTapAction();
        }
      }
    }));
    JButton tapButton = VisualTest.createButton("Tap Me");
    tapButton.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
        EventAction<SimpleEventComponent.Action, ActionType> event =
          new EventAction<>(nowUs, 0, SimpleEventComponent.Action.DOWN, ActionType.HOLD);
        mData.add(nowUs, event);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
        EventAction<SimpleEventComponent.Action, ActionType> event =
          new EventAction<>(nowUs, nowUs, SimpleEventComponent.Action.UP, ActionType.TOUCH);
        mData.add(nowUs, event);
      }
    });
    controls.add(tapButton);
    controls.add(VisualTest.createCheckbox("Shift xRange Min", new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent itemEvent) {
        mAnimatedRange.setShift(itemEvent.getStateChange() == ItemEvent.SELECTED);
        mTimelineRange.setShift(itemEvent.getStateChange() == ItemEvent.SELECTED);
      }
    }));

    controls.add(
      new Box.Filler(new Dimension(0, 0), new Dimension(300, Integer.MAX_VALUE),
                     new Dimension(300, Integer.MAX_VALUE)));
  }

  private JLayeredPane createMockTimeline() {
    JLayeredPane timelinePane = new JLayeredPane();
    timelinePane.add(mTimeAxis);
    timelinePane.add(mSimpleEventComponent);
    timelinePane.add(myStackedEventComponent);
    timelinePane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        JLayeredPane host = (JLayeredPane)e.getComponent();
        if (host != null) {
          Dimension dim = host.getSize();
          int numChart = 0;
          for (Component c : host.getComponents()) {
            if (c instanceof AxisComponent) {
              AxisComponent axis = (AxisComponent)c;
              switch (axis.getOrientation()) {
                case LEFT:
                  axis.setBounds(0, 0, AXIS_SIZE, dim.height);
                  break;
                case BOTTOM:
                  axis.setBounds(0, dim.height - AXIS_SIZE, dim.width, AXIS_SIZE);
                  break;
                case RIGHT:
                  axis.setBounds(dim.width - AXIS_SIZE, 0, AXIS_SIZE, dim.height);
                  break;
                case TOP:
                  axis.setBounds(0, 0, dim.width, AXIS_SIZE);
                  break;
              }
            }
            else {
              c.setBounds(AXIS_SIZE, 40 * numChart, dim.width - AXIS_SIZE * 2,
                          AXIS_SIZE);
              numChart++;
            }
          }
        }
      }
    });

    return timelinePane;
  }
}
