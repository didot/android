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
package com.android.tools.adtui.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SelectionModel extends AspectModel<SelectionModel.Aspect> {

  public enum Aspect {
    SELECTION,
  }

  /**
   * The range being selected.
   */
  @NotNull
  private final Range mySelectionRange;

  /**
   * The previous selection range, which we need to check against in order to fire selection events
   * indirectly (when someone modifies our underlying range externally instead of through this
   * class's API).
   */
  @NotNull
  private final Range myPreviousSelectionRange;

  @NotNull
  private final List<SelectionListener> myListeners = new ArrayList<>();

  @NotNull
  private final List<DurationDataModel<? extends ConfigurableDurationData>> myConstraints;

  private boolean mySelectionEnabled;

  /**
   * If updating, postpone firing selection events until {@link #endUpdate()}.
   */
  private boolean myIsUpdating;
  private boolean myPostponeSelectionEvent;

  @Nullable
  private Consumer<SelectionListener> myEventToFire;

  public SelectionModel(@NotNull Range selection) {
    mySelectionRange = selection;
    myPreviousSelectionRange = new Range(mySelectionRange);
    mySelectionEnabled = true;

    mySelectionRange.addDependency(this).onChange(Range.Aspect.RANGE, this::selectionChanged);
    myConstraints = new ArrayList<>();
  }

  /**
   * Duration data can be added to restrict the time regions that can be selected by the user. The constraint can either be:
   * 1) Full - if the user tries to select a sub-range within a duration data sample., the entire duration will be selected
   * 2) Partial - the user is allowed to select a sub-range within a duration data sample.
   */
  public void addConstraint(@Nullable DurationDataModel<? extends ConfigurableDurationData> constraints) {
    myConstraints.add(constraints);
  }

  /**
   * Add a listener which is fired whenever the selection is created, modified, or changed.
   *
   * Unlike the {@link Aspect#SELECTION} aspect, this event will not be fired between calls to
   * {@link #beginUpdate()} and {@link #endUpdate()}
   */
  public void addListener(final SelectionListener listener) {
    myListeners.add(listener);
  }

  public void clearListeners() {
    myListeners.clear();
  }

  private void fireListeners() {
    if (myIsUpdating) {
      myPostponeSelectionEvent = true;
      return;
    }

    if (myEventToFire != null) {
      myListeners.forEach(myEventToFire);
      myEventToFire = null;
    }
  }

  private void notifyEvent(@NotNull Consumer<SelectionListener> event) {
    myEventToFire = event;
    fireListeners();
  }

  private void selectionChanged() {
    changed(Aspect.SELECTION);

    if (mySelectionRange.isEmpty()) {
      notifyEvent(SelectionListener::selectionCleared);
    }
    else if (myPreviousSelectionRange.isEmpty() && !mySelectionRange.isEmpty()) {
      notifyEvent(SelectionListener::selectionCreated);
    }
    myPreviousSelectionRange.set(mySelectionRange);
  }

  /**
   * Indicate that no events should be fired until the update is complete.
   * Calls to this method should not be nested.
   */
  public void beginUpdate() {
    myIsUpdating = true;
  }

  /**
   * Mark the end of a previously called {@link #beginUpdate()}. When an update is finished,
   * the most recent event will be triggered if it would have been fired during the update,
   * while any previous events would be swallowed. This is useful, for example, to distinguish
   * the user simply clearing the selection vs. creating a new one (which would be
   * beginUpdate -> clear -> set -> endUpdate)
   */
  public void endUpdate() {
    if (myIsUpdating) {
      myIsUpdating = false;
      if (myPostponeSelectionEvent) {
        myPostponeSelectionEvent = false;
        fireListeners();
      }
    }
  }

  public void clear() {
    if (!mySelectionEnabled) {
      return;
    }

    mySelectionRange.clear();
  }

  public void set(double min, double max) {
    if (!mySelectionEnabled) {
      return;
    }

    if (myConstraints.isEmpty()) {
      mySelectionRange.set(min, max);
      return;
    }

    Range proposedRange = new Range(min, max);
    Range resultRange = null;
    ConfigurableDurationData resultDurationData = null;
    boolean found = false;

    for (DurationDataModel<? extends ConfigurableDurationData> constraint : myConstraints) {
      DataSeries<? extends ConfigurableDurationData> series = constraint.getSeries().getDataSeries();
      List<? extends SeriesData<? extends ConfigurableDurationData>> constraints = series.getDataForXRange(new Range(min, max));
      for (SeriesData<? extends ConfigurableDurationData> data : constraints) {
        long duration = data.value.getDurationUs();
        if (duration == Long.MAX_VALUE && !data.value.getSelectableWhenMaxDuration()) {
          continue;
        }

        long dataMax = duration == Long.MAX_VALUE ? duration : data.x + duration;
        Range r = new Range(data.x, dataMax);
        // Check if this constraint intersects the proposedRange.
        if (!r.getIntersection(proposedRange).isEmpty()) {
          resultRange = r;
          resultDurationData = data.value;
          // If this constraint already intersects the current range, use it.
          if (!r.getIntersection(mySelectionRange).isEmpty()) {
            found = true;
            break;
          }
        }
      }
      if (found) {
        break;
      }
    }

    if (resultRange == null) {
      mySelectionRange.clear();
      notifyEvent(SelectionListener::selectionCreationFailure);
    }
    else {
      Range finalRange = resultDurationData.canSelectPartialRange() ? resultRange.getIntersection(proposedRange) : resultRange;
      if (!mySelectionRange.isSameAs(finalRange)) {
        // Clear the previous range, which will allow logic in `selectionChanged` to realize this
        // is a new selection, not a modification of an existing selection
        myPreviousSelectionRange.clear();
        mySelectionRange.set(finalRange);
      }
    }
  }

  @NotNull
  public Range getSelectionRange() {
    return mySelectionRange;
  }

  /**
   * If set, selection cannot be set. Note that if a previous selection exist, it will NOT be cleared.
   */
  public void setSelectionEnabled(boolean enabled) {
    mySelectionEnabled = enabled;
  }
}
