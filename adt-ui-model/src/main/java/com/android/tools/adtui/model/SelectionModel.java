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
   * The previous selection range, useful for determining which event to fire (created or cleared).
   */
  @NotNull
  private final Range myPreviousSelectionRange;

  /**
   * The reference range.
   */
  @NotNull
  private final Range myRange;

  @NotNull
  private final List<SelectionListener> myListeners = new ArrayList<>();

  @NotNull
  private final List<DurationDataModel<? extends DurationData>> myConstraints;

  private boolean mySelectFullConstraint;

  private boolean mySelectionEnabled;

  /**
   * If updating, don't fire selection events.
   */
  private boolean myIsUpdating;
  private boolean myPostponeSelectionEvent;

  public SelectionModel(@NotNull Range selection, @NotNull Range range) {
    myRange = range;
    mySelectionRange = selection;
    myPreviousSelectionRange = new Range(mySelectionRange);
    mySelectionEnabled = true;

    myRange.addDependency(this).onChange(Range.Aspect.RANGE, this::rangesChanged);
    mySelectionRange.addDependency(this).onChange(Range.Aspect.RANGE, this::rangesChanged);
    myConstraints = new ArrayList<>();
  }

  public void addConstraint(@Nullable DurationDataModel<? extends DurationData> constraints) {
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

    Consumer<SelectionListener> event = null;
    if (myPreviousSelectionRange.isEmpty() && !mySelectionRange.isEmpty()) {
      event = SelectionListener::selectionCreated;
    }
    else if (!myPreviousSelectionRange.isEmpty() && mySelectionRange.isEmpty()) {
      event = SelectionListener::selectionCleared;
    }

    myPreviousSelectionRange.set(mySelectionRange);
    if (event != null) {
      myListeners.forEach(event);
    }
  }

  private void rangesChanged() {
    changed(Aspect.SELECTION);
    fireListeners();
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
   * any events will be triggered if they would have been fired during the update.
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


    Range candidate = new Range(min, max);
    Range result = null;
    boolean found = false;

    for (DurationDataModel<? extends DurationData> constraint : myConstraints) {
      DataSeries<? extends DurationData> series = constraint.getSeries().getDataSeries();
      List<? extends SeriesData<? extends DurationData>> constraints = series.getDataForXRange(new Range(min, max));
      for (SeriesData<? extends DurationData> data : constraints) {
        Range r = new Range(data.x, data.x + data.value.getDuration());
        // Check if this constraint intersects the candidate range.
        if (!r.getIntersection(candidate).isEmpty()) {
          result = r;
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
    if (result == null) {
      mySelectionRange.clear();
    }
    else if (!mySelectionRange.equals(result)) {
      // In this case, we're completely replacing the existing selection with a brand new selection.
      // If we didn't clear the previous range, it would look like we were just modifying the old selection.
      myPreviousSelectionRange.clear();
      if (mySelectFullConstraint) {
        mySelectionRange.set(result);
      }
      else {
        mySelectionRange.set(result.getIntersection(candidate));
      }
    }
  }

  @NotNull
  public Range getSelectionRange() {
    return mySelectionRange;
  }

  @NotNull
  public Range getRange() {
    return myRange;
  }

  /**
   * If set, it will force the selection to cover the full constraint ranges.
   */
  public void setSelectFullConstraint(boolean selectFullConstraint) {
    mySelectFullConstraint = selectFullConstraint;
  }

  /**
   * If set, selection cannot be set. Note that if a previous selection exist, it will NOT be cleared.
   */
  public void setSelectionEnabled(boolean enabled) {
    mySelectionEnabled = enabled;
  }
}
