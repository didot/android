/*
 * Copyright (C) 2017 The Android Open Source Project
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

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static com.google.common.truth.Truth.assertThat;

public class SelectionModelTest {
  private Range mySelection;

  @Before
  public void setUp() throws Exception {
    mySelection = new Range();
  }

  @Test
  public void testSetWithNoConstraints() {
    SelectionModel selection = new SelectionModel(mySelection);
    selection.set(10, 20);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(10);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(20);
  }

  @Test
  public void testSetWithPartialConstraint() {
    SelectionModel selection = new SelectionModel(mySelection);
    selection.addConstraint(createConstraint(false, true, 0, 5, 15, 20, 35, 40));

    selection.set(10, 18);

    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(15);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(18);
  }

  @Test
  public void testSetWithPartialConstraintEmpty() {
    SelectionModel selection = new SelectionModel(mySelection);
    selection.addConstraint(createConstraint(false, true, 0, 5, 15, 20, 35, 40));

    selection.set(10, 12);

    assertThat(mySelection.isEmpty()).isTrue();
  }

  @Test
  public void testSelectionPrefersCurrentOne() {
    SelectionModel selection = new SelectionModel(mySelection);
    selection.addConstraint(createConstraint(false, true, 0, 5, 15, 20, 35, 40));
    selection.set(18, 20);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(18);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(20);

    // This overlaps with two constraints, it should choose the one it's using
    selection.set(3, 18);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(15);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(18);

    // In both directions
    selection.set(16, 39);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(16);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(20);
  }

  @Test
  public void testFullSelection() {
    SelectionModel selection = new SelectionModel(mySelection);
    selection.addConstraint(createConstraint(false, false, 0, 5, 15, 20, 35, 40));

    selection.set(10, 18);

    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(15);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(20);
  }

  @Test
  public void testNestedFullConstraints() {
    SelectionModel selection = new SelectionModel(mySelection);
    selection.addConstraint(createConstraint(false, false, 2, 3, 18, 19, 38, 39));
    selection.addConstraint(createConstraint(false, false, 0, 5, 15, 20, 35, 40));

    selection.set(0, 1);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(0);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(5);

    selection.set(2.5, 2.6);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(2);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(3);

    selection.set(4, 5);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(0);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(5);

    selection.set(35, 36);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(35);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(40);

    selection.set(38.5, 38.6);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(38);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(39);

    selection.set(39.5, 39.6);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(35);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(40);
  }

  @Test
  public void testFullWithNestedPartialConstraints() {
    SelectionModel selection = new SelectionModel(mySelection);
    selection.addConstraint(createConstraint(false, true, 2, 3, 18, 19, 38, 39));
    selection.addConstraint(createConstraint(false, false, 0, 5, 15, 20, 35, 40));

    selection.set(0, 1);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(0);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(5);

    selection.set(2.5, 2.6);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(2.5);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(2.6);

    selection.set(4, 5);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(0);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(5);

    selection.set(35, 36);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(35);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(40);

    selection.set(38.5, 38.6);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(38.5);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(38.6);

    selection.set(39.5, 39.6);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(35);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(40);
  }

  @Test
  public void testPartialWithNestedFullConstraints() {
    SelectionModel selection = new SelectionModel(mySelection);
    selection.addConstraint(createConstraint(false, true, 0, 5, 15, 20, 35, 40));
    selection.addConstraint(createConstraint(false, false, 2, 3, 18, 19, 38, 39));

    selection.set(0, 1);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(0);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(1);

    // SelectionModel selects the first constraint that intersects the previous selected range. If we don't clear the selection, the partial
    // constrain would have been used instead.
    selection.clear();
    selection.set(2.5, 2.6);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(2);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(3);

    selection.set(4, 5);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(4);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(5);

    selection.set(35, 36);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(35);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(36);

    selection.clear();
    selection.set(38.5, 38.6);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(38);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(39);

    selection.set(39.5, 39.6);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(39.5);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(39.6);
  }

  @Test
  public void testListenersFiredAsExpected() {
    SelectionModel model = new SelectionModel(mySelection);

    final int SELECTION_CREATED = 0;
    final int SELECTION_CLEARED = 1;
    final int SELECTION_FAILED = 2;
    final boolean[] event = {false, false, false};
    model.addListener(new SelectionListener() {
      @Override
      public void selectionCreated() {
        event[SELECTION_CREATED] = true;
      }

      @Override
      public void selectionCleared() {
        event[SELECTION_CLEARED] = true;
      }

      @Override
      public void selectionCreationFailure() {
        event[SELECTION_FAILED] = true;
      }
    });

    // Basic selection modification
    Arrays.fill(event, false);
    model.set(1, 2);
    assertThat(event[SELECTION_CREATED]).isTrue();
    event[SELECTION_CREATED] = false;
    model.set(1, 3);
    assertThat(event[SELECTION_CREATED]).isFalse();
    assertThat(event[SELECTION_CLEARED]).isFalse();
    assertThat(event[SELECTION_FAILED]).isFalse();
    model.clear();
    assertThat(event[SELECTION_CLEARED]).isTrue();
    assertThat(event[SELECTION_FAILED]).isFalse();

    // Selection creation not fired if not changed
    model.set(1, 2);
    event[SELECTION_CREATED] = false;
    model.set(1, 2);
    assertThat(event[SELECTION_CREATED]).isFalse();
    assertThat(event[SELECTION_FAILED]).isFalse();

    // Selection clear not fired if not changed
    model.clear();
    event[SELECTION_CLEARED] = false;
    model.clear();
    assertThat(event[SELECTION_CLEARED]).isFalse();
    assertThat(event[SELECTION_FAILED]).isFalse();

    // Selection creation only fired after updating is finished
    model.clear();
    Arrays.fill(event, false);
    model.beginUpdate();
    model.set(3, 4);
    model.set(3, 5);
    assertThat(event[SELECTION_CREATED]).isFalse();
    assertThat(event[SELECTION_FAILED]).isFalse();
    model.endUpdate();
    assertThat(event[SELECTION_CREATED]).isTrue();
    assertThat(event[SELECTION_FAILED]).isFalse();

    // Selection clear only fired after updating is finished
    model.set(1, 2);
    event[SELECTION_CLEARED] = false;
    model.beginUpdate();
    model.clear();
    assertThat(event[SELECTION_CLEARED]).isFalse();
    assertThat(event[SELECTION_FAILED]).isFalse();
    model.endUpdate();
    assertThat(event[SELECTION_CLEARED]).isTrue();
    assertThat(event[SELECTION_FAILED]).isFalse();

    // Selection failed is fired when attempting to select constrained ranges
    model.clear();
    Arrays.fill(event, false);
    model.addConstraint(createConstraint(false, true, 0, 1));
    model.addConstraint(createConstraint(false, false, 2, 3));
    model.addConstraint(createConstraint(true, true, 5, Long.MAX_VALUE));
    model.set(0.25, 0.75);
    assertThat(event[SELECTION_FAILED]).isFalse();
    model.set(1.25, 1.75);
    assertThat(event[SELECTION_FAILED]).isTrue();
    event[SELECTION_FAILED] = false;
    model.set(2.25, 2.75);
    assertThat(event[SELECTION_FAILED]).isFalse();
    model.set(7.5, 10);
    assertThat(event[SELECTION_FAILED]).isFalse();
  }

  @Test
  public void testSelectionClearOnRangeChange() {
    SelectionModel model = new SelectionModel(mySelection);
    model.addConstraint(createConstraint(false, false, 2, 3, 18, 19, 38, 39));

    final int CREATED = 0;
    final int CLEARED = 1;
    int[] counts = new int[]{0, 0};
    model.addListener(new SelectionListener() {
      @Override
      public void selectionCreated() {
        counts[CREATED]++;
      }

      @Override
      public void selectionCleared() {
        counts[CLEARED]++;
      }
    });

    model.set(2.5, 2.5);
    assertThat(counts[CLEARED]).isEqualTo(0);
    assertThat(counts[CREATED]).isEqualTo(1);
    Arrays.fill(counts, 0);

    model.set(4, 5);
    assertThat(counts[CLEARED]).isEqualTo(1);
    assertThat(counts[CREATED]).isEqualTo(0);
    Arrays.fill(counts, 0);

    model.set(7, 9);
    assertThat(counts[CLEARED]).isEqualTo(0);
    assertThat(counts[CREATED]).isEqualTo(0);
    Arrays.fill(counts, 0);

    model.set(18, 19);
    assertThat(counts[CLEARED]).isEqualTo(0);
    assertThat(counts[CREATED]).isEqualTo(1);
    Arrays.fill(counts, 0);

    model.set(38.5, 38.7);
    assertThat(counts[CLEARED]).isEqualTo(0);
    assertThat(counts[CREATED]).isEqualTo(1);
    Arrays.fill(counts, 0);

    model.set(38.3, 38.4);
    assertThat(counts[CLEARED]).isEqualTo(0);
    assertThat(counts[CREATED]).isEqualTo(0);
    Arrays.fill(counts, 0);
  }

  @Test
  public void testCanSelectUnfinishedDurationData() {
    SelectionModel selection = new SelectionModel(mySelection);
    selection.addConstraint(createConstraint(false, true, 0, Long.MAX_VALUE));
    selection.set(10, 12);
    assertThat(mySelection.isEmpty()).isTrue();
  }

  @Test
  public void testCannotSelectUnfinishedDurationData() {
    SelectionModel selection = new SelectionModel(mySelection);
    selection.addConstraint(createConstraint(true, true, 0, Long.MAX_VALUE));
    selection.set(10, 12);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(10);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(12);
  }

  private DurationDataModel<DefaultConfigurableDurationData> createConstraint(boolean selectableWhenUnspecifiedDuration, boolean selectPartialRange, long... values) {
    DefaultDataSeries<DefaultConfigurableDurationData> series = new DefaultDataSeries<>();
    RangedSeries<DefaultConfigurableDurationData> ranged = new RangedSeries<>(new Range(0, 100), series);
    DurationDataModel<DefaultConfigurableDurationData> constraint = new DurationDataModel<>(ranged);
    for (int i = 0; i < values.length / 2; i++) {
      long duration = values[i * 2 + 1] == Long.MAX_VALUE ? Long.MAX_VALUE : values[i * 2 + 1] - values[i * 2];
      series.add(values[i * 2], new DefaultConfigurableDurationData(duration, selectableWhenUnspecifiedDuration, selectPartialRange));
    }

    return constraint;
  }
}