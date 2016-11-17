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
package com.android.tools.profilers;

import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.model.Range;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.TimeUnit;

/**
 * A custom toolbar that synchronizes with the data+view ranges from the {@link ProfilerTimeline}.
 *
 * This control sets the timeline into streaming mode if users drags the thumb all the way to the right.
 * Also, any updates back to the timeline object are handled inside {@link #animate(float)} so that
 * view range is synchronized with the {@link Choreographer}. This also means that this control should be
 * registered before/after all {@link Animatable} to ensure the view range does not change in the middle of
 * the animation loop.
 */
public final class ProfilerScrollbar extends JBScrollBar implements Animatable {

  /**
   * Work in ms to keep things compatible with scrollbar's integer api.
   * This should cover a long enough time period for us in terms of profiling.
   */
  private static final long MS_TO_US = TimeUnit.MILLISECONDS.toMicros(1);

  /**
   * Percentage threshold to switch {@link #myTimeline} to streaming mode.
   * e.g. if the scrollbar is more than 95% to the right, {@link #myTimeline} will be set to streaming
   */
  private static final float STREAMING_POSITION_THRESHOLD = 0.95f;

  @NotNull final ProfilerTimeline myTimeline;

  private boolean myScrolling;

  public ProfilerScrollbar(@NotNull ProfilerTimeline timeline) {
    super(HORIZONTAL);

    myTimeline = timeline;

    setUI(new ButtonlessScrollBarUI() {
      /**
       * The default ButtonlessScrollBarUI contains logic to overlay the scrollbar
       * and fade in/out on top of a JScrollPane. Because we are using the scrollbar
       * without a scroll pane, it can fade in and out unexpectedly. This subclass
       * simply disables the overlay feature.
       */
      @Override
      protected boolean isMacOverlayScrollbar() {
        return false;
      }
    });

    // We cannot simply use an AdjustmentListener as the values can be changed in multiple scenarios.
    // e.g. streaming vs scrolling vs viewing back in time
    // Here we simply keep track of the state and let the animate loop handles everything.
    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        myTimeline.setStreaming(false);
        myScrolling = true;
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        myScrolling = false;

        BoundedRangeModel model = getModel();
        if ((model.getValue() + model.getExtent()) / (float)model.getMaximum() >= STREAMING_POSITION_THRESHOLD) {
          myTimeline.setStreaming(true);
        }
      }
    });
  }

  @Override
  public void animate(float frameLength) {
    Range dataRangeUs = myTimeline.getDataRange();
    Range viewRangeUs = myTimeline.getViewRange();
    int dataExtentMs = (int)((dataRangeUs.getLength() - myTimeline.getViewBuffer()) / MS_TO_US);
    int viewExtentMs = Math.min(dataExtentMs, (int)(viewRangeUs.getLength() / MS_TO_US));
    int viewRelativeMinMs = Math.max(0, (int)((viewRangeUs.getMin() - dataRangeUs.getMin()) / MS_TO_US));

    if (myScrolling) {
      int valueMs = getValue();
      setValues(valueMs, viewExtentMs, 0, dataExtentMs);
      double deltaUs = (valueMs - viewRelativeMinMs) * MS_TO_US;
      viewRangeUs.shift(deltaUs);
    }
    else {
      setValues(viewRelativeMinMs, viewExtentMs, 0, dataExtentMs);
    }
  }
}
