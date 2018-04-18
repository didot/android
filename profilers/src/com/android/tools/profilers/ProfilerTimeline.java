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

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.model.updater.Updater;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * A helper object that manages the current view and selection ranges for the Studio Profilers.
 */
public final class ProfilerTimeline extends AspectModel<ProfilerTimeline.Aspect> implements Updatable {

  public enum Aspect {
    STREAMING
  }

  @VisibleForTesting
  public static final long DEFAULT_VIEW_LENGTH_US = TimeUnit.SECONDS.toMicros(30);

  @NotNull private final Updater myUpdater;
  @NotNull private final Range myDataRangeUs;
  @NotNull private final Range myViewRangeUs;
  @NotNull private final Range mySelectionRangeUs;
  @NotNull private final Range myTooltipRangeUs;

  private boolean myStreaming;

  private float myStreamingFactor;
  private double myZoomLeft;

  private boolean myCanStream = true;
  private long myDataStartTimeNs;
  private long myDataLengthNs;
  private boolean myIsReset = false;
  private long myResetTimeNs;
  private boolean myIsPaused = false;
  private long myPausedTime;

  /**
   * When not negative, interpolates {@link #myViewRangeUs}'s max to it while keeping the view range length.
   */
  private double myTargetRangeMaxUs = -1;

  /**
   * Interpolation factor of the animation that happens when jumping to a target value.
   */
  private float myJumpFactor;

  public ProfilerTimeline(@NotNull Updater updater) {
    myDataRangeUs = new Range(0, 0);
    myViewRangeUs = new Range(0, 0);
    mySelectionRangeUs = new Range(); // Empty range
    myTooltipRangeUs = new Range(); // Empty range

    myUpdater = updater;
    myUpdater.register(this);
  }

  /**
   * Change the streaming mode of this timeline. If canStream is currently set to false (e.g. while user is scrolling or zooming), then
   * nothing happens if the caller tries to enable streaming.
   */
  public void setStreaming(boolean isStreaming) {
    if (!myCanStream && isStreaming) {
      isStreaming = false;
    }

    if (myStreaming == isStreaming) {
      return;
    }
    assert myCanStream || !isStreaming;

    myStreaming = isStreaming;
    // Update the ranges as if no time has passed.
    update(0);

    changed(Aspect.STREAMING);
  }

  public boolean isStreaming() {
    return myStreaming && !myIsPaused;
  }

  /**
   * Sets whether the timeline can turn on streaming. If this is set to false and the timeline is currently streaming, streaming mode
   * will be toggled off.
   */
  public void setCanStream(boolean canStream) {
    myCanStream = canStream;
    if (!myCanStream && myStreaming) {
      setStreaming(false);
    }
  }

  public boolean canStream() {
    return myCanStream && !myIsPaused;
  }

  public void toggleStreaming() {
    myZoomLeft = 0.0;
    setStreaming(!isStreaming());
  }

  public boolean isPaused() {
    return myIsPaused;
  }

  public void setIsPaused(boolean paused) {
    myIsPaused = paused;
    if (myIsPaused) {
      myPausedTime = myDataLengthNs;
    }
  }

  @NotNull
  public Range getDataRange() {
    return myDataRangeUs;
  }

  @NotNull
  public Range getViewRange() {
    return myViewRangeUs;
  }

  public Range getSelectionRange() {
    return mySelectionRangeUs;
  }

  public Range getTooltipRange() {
    return myTooltipRangeUs;
  }

  @Override
  public void update(long elapsedNs) {
    if (myIsReset) {
      // If the timeline has been reset, we need to make sure the elapsed time is the duration between the current update and when reset
      // was triggered. Otherwise we would be adding extra time. e.g.
      //
      // |<----------------  elapsedNs -------------->|
      //                         |<------------------>| // we only want this duration.
      // Last update             Reset                This update
      // |-----------------------r--------------------|
      elapsedNs = myUpdater.getTimer().getCurrentTimeNs() - myResetTimeNs;
      myResetTimeNs = 0;
      myIsReset = false;
    }

    myDataLengthNs += elapsedNs;
    long maxTimelineTimeNs = myDataLengthNs;
    if (myIsPaused) {
      maxTimelineTimeNs = myPausedTime;
    }

    long deviceNowNs = myDataStartTimeNs + maxTimelineTimeNs;
    long deviceNowUs = TimeUnit.NANOSECONDS.toMicros(deviceNowNs);
    myDataRangeUs.setMax(deviceNowUs);
    double viewUs = myViewRangeUs.getLength();
    if (myStreaming) {
      myStreamingFactor = Updater.lerp(myStreamingFactor, 1.0f, 0.95f, elapsedNs, Float.MIN_VALUE);
      double min = Updater.lerp(myViewRangeUs.getMin(), deviceNowUs - viewUs, myStreamingFactor);
      double max = Updater.lerp(myViewRangeUs.getMax(), deviceNowUs, myStreamingFactor);
      myViewRangeUs.set(min, max);
    }
    else {
      myStreamingFactor = 0.0f;
    }
    double left = Updater.lerp(myZoomLeft, 0.0, 0.95f, elapsedNs, myViewRangeUs.getLength() * 0.0001f);
    zoom(myZoomLeft - left, myStreaming ? 1.0 : 0.5f);
    myZoomLeft = left;

    handleJumpToTargetMax(elapsedNs);
  }

  /**
   * If {@link #myTargetRangeMaxUs} is not negative, interpolates {@link #myViewRangeUs}'s max to it.
   */
  private void handleJumpToTargetMax(long elapsedNs) {
    if (myTargetRangeMaxUs < 0) {
      return; // No need to jump. Return early.
    }

    // Update the view range
    myJumpFactor = Updater.lerp(myJumpFactor, 1.0f, 0.95f, elapsedNs, Float.MIN_VALUE);
    double targetMin = myTargetRangeMaxUs - myViewRangeUs.getLength();
    double min = Updater.lerp(myViewRangeUs.getMin(), targetMin, myJumpFactor);
    double max = Updater.lerp(myViewRangeUs.getMax(), myTargetRangeMaxUs, myJumpFactor);
    myViewRangeUs.set(min, max);

    // Reset the jump factor and myTargetRangeMaxUs when finish jumping to target.
    if (Double.compare(myTargetRangeMaxUs, max) == 0) {
      myTargetRangeMaxUs = -1;
      myJumpFactor = 0.0f;
    }
  }

  /**
   * Makes sure the given target {@link Range} fits {@link #myViewRangeUs}. That means the timeline should zoom out until the view range is
   * bigger than (or equals to) the target range and then shift until the it totally fits the view range.
   * See {@link #adjustRangeCloseToMiddleView(Range)}.
   */
  private void ensureRangeFitsViewRange(@NotNull Range target) {
    setStreaming(false);
    if (myViewRangeUs.contains(target.getMin()) && myViewRangeUs.contains(target.getMax())) {
      // Target already visible. No need to animate to it.
      return;
    }

    // First, zoom out until the target range fits the view range.
    double delta = target.getLength() - myViewRangeUs.getLength();
    if (delta > 0) {
      myZoomLeft += delta;
      // If we need to zoom out, it means the target range will occupy the full view range, so the target max should be its max.
      myTargetRangeMaxUs = target.getMax();
    }
    // Otherwise, we move the timeline as little as possible to reach the target range. At this point, there are only two possible
    // scenarios: a) The target range is on the right of the view range, so we adjust the view range relatively to the target's max.
    else if (target.getMax() > myViewRangeUs.getMax()) {
      myTargetRangeMaxUs = target.getMax();
    }
    // b) The target range is on the left of the view range, so we adjust the view range relatively to the target's min.
    else {
      assert target.getMin() < myViewRangeUs.getMin();
      myTargetRangeMaxUs = target.getMin() + myViewRangeUs.getLength();
    }
  }

  /**
   * Adjust the view range to ensure given target is within the {@link #myViewRangeUs}, also try to make the given target is in the middle
   * of the view range. Due to the data range, when the target cannot be displayed in the middle, the view range either starts from zero or
   * ends at the data range max.
   */
  public void adjustRangeCloseToMiddleView(@NotNull Range target) {
    ensureRangeFitsViewRange(target);
    double targetMiddle = (target.getMax() + target.getMin()) / 2;
    double targetMax = targetMiddle + myViewRangeUs.getLength() / 2;
    // When the view range is from timestamp zero, i.e the data range's min, get the view range max value. The view range is the larger one
    // of the previous view length, or the target length if need zooming.
    double maxFromZero = myDataRangeUs.getMin() + Math.max(target.getLength(), myViewRangeUs.getLength());
    // We limit the target max to data range's min, as we can't scroll earlier than timestamp zero.
    targetMax = Math.max(targetMax, maxFromZero);
    // We limit the target max to data range's max, as we can't scroll further than the data.
    myTargetRangeMaxUs = Math.min(targetMax, myDataRangeUs.getMax());
  }

  public void zoom(double deltaUs, double percent) {
    if (deltaUs == 0.0) {
      return;
    }
    if (deltaUs < 0 && percent < 1.0 && myViewRangeUs.getMin() >= myDataRangeUs.getMin()) {
      setStreaming(false);
    }
    double minUs = myViewRangeUs.getMin() - deltaUs * percent;
    double maxUs = myViewRangeUs.getMax() + deltaUs * (1 - percent);
    // When the view range is not fully covered, reset minUs to data range could change zoomLeft from zero to a large number.
    boolean isDataRangeFullyCoveredByViewRange = myDataRangeUs.getMin() <= myViewRangeUs.getMin();
    if (isDataRangeFullyCoveredByViewRange && minUs < myDataRangeUs.getMin()) {
      maxUs += myDataRangeUs.getMin() - minUs;
      minUs = myDataRangeUs.getMin();
    }
    // If our new view range is less than our data range then lock our max view so we
    // don't expand it beyond the data range max.
    if (!isDataRangeFullyCoveredByViewRange && minUs < myDataRangeUs.getMin()) {
      maxUs = myDataRangeUs.getMax();
    }
    if (maxUs > myDataRangeUs.getMax()) {
      minUs -= maxUs - myDataRangeUs.getMax();
      maxUs = myDataRangeUs.getMax();
    }
    // minUs could have gone past again.
    if (isDataRangeFullyCoveredByViewRange) {
      minUs = Math.max(minUs, myDataRangeUs.getMin());
    }
    myViewRangeUs.set(minUs, maxUs);
  }

  /**
   * Zooms out by 10% of the current view range length.
   */
  public void zoomOut() {
    zoomOutBy(myViewRangeUs.getLength() * 0.1f);
  }

  /**
   * Zooms out by a given amount in microseconds.
   */
  public void zoomOutBy(double amountUs) {
    myZoomLeft += amountUs;
  }

  public void zoomIn() {
    myZoomLeft -= myViewRangeUs.getLength() * 0.1f;
  }

  public void resetZoom() {
    myZoomLeft = DEFAULT_VIEW_LENGTH_US - myViewRangeUs.getLength();
  }

  public void pan(double deltaUs) {
    if (deltaUs < 0) {
      setStreaming(false);
    }
    if (myViewRangeUs.getMin() + deltaUs < myDataRangeUs.getMin()) {
      deltaUs = myDataRangeUs.getMin() - myViewRangeUs.getMin();
    }
    else if (myViewRangeUs.getMax() + deltaUs > myDataRangeUs.getMax()) {
      deltaUs = myDataRangeUs.getMax() - myViewRangeUs.getMax();
    }
    myViewRangeUs.shift(deltaUs);
  }

  /**
   * This function resets the internal state to the timeline.
   *
   * @param startTimeNs the time which should be the 0 value on the timeline (e.g. the beginning of the data range).
   * @param endTimeNs   the current rightmost value on the timeline (e.g. the current max of the data range).
   */
  public void reset(long startTimeNs, long endTimeNs) {
    myDataStartTimeNs = startTimeNs;
    myDataLengthNs = endTimeNs - startTimeNs;
    myIsPaused = false;
    double startTimeUs = TimeUnit.NANOSECONDS.toMicros(startTimeNs);
    double endTimeUs = TimeUnit.NANOSECONDS.toMicros(endTimeNs);
    myDataRangeUs.set(startTimeUs, endTimeUs);
    myViewRangeUs.set(endTimeUs - DEFAULT_VIEW_LENGTH_US, endTimeUs);
    setStreaming(true);
    myResetTimeNs = myUpdater.getTimer().getCurrentTimeNs();
    myIsReset = true;
  }

  public long getDataStartTimeNs() {
    return myDataStartTimeNs;
  }

  /**
   * @param absoluteTimeNs the device time in nanoseconds.
   * @return time relative to the data start time (e.g. zero on the timeline), in microseconds.
   */
  public long convertToRelativeTimeUs(long absoluteTimeNs) {
    return TimeUnit.NANOSECONDS.toMicros(absoluteTimeNs - myDataStartTimeNs);
  }
}
