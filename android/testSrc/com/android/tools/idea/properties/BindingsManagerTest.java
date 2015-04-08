/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.properties;

import com.android.tools.idea.properties.basicTypes.IntValueProperty;
import com.android.tools.idea.properties.basicTypes.StringValueProperty;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static com.android.tools.idea.properties.BindingsManager.INVOKE_IMMEDIATELY_STRATEGY;
import static org.fest.assertions.Assertions.assertThat;

public final class BindingsManagerTest {

  @Test
  public void oneWayBindingAffectedByTarget() throws Exception {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    IntValueProperty property1 = new IntValueProperty(10);
    IntValueProperty property2 = new IntValueProperty(20);

    bindings.bind(property1, property2);
    assertThat(property1.get()).isEqualTo(20);

    property1.set(30);
    assertThat(property1.get()).isEqualTo(30);
    assertThat(property2.get()).isEqualTo(20);

    property2.set(40);
    assertThat(property1.get()).isEqualTo(40);
  }

  @Test
  public void twoWayBindingsAffectEachOther() throws Exception {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    IntValueProperty property1 = new IntValueProperty(10);
    IntValueProperty property2 = new IntValueProperty(20);

    bindings.bindTwoWay(property1, property2);
    assertThat(property1.get()).isEqualTo(20);

    property1.set(30);
    assertThat(property2.get()).isEqualTo(30);

    property2.set(40);
    assertThat(property1.get()).isEqualTo(40);
  }

  @Test
  public void releaseDisconnectsOneWayBindings() throws Exception {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    StringValueProperty property1 = new StringValueProperty("A");
    StringValueProperty property2 = new StringValueProperty("B");

    bindings.bind(property1, property2);
    assertThat(property1.get()).isEqualTo("B");

    bindings.release(property1);

    property2.set("Property2");
    assertThat(property1.get()).isEqualTo("B");
  }

  @Test
  public void releaseTwoWayDisconnectsTwoWayBindings() throws Exception {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    StringValueProperty property1 = new StringValueProperty("First");
    StringValueProperty property2 = new StringValueProperty("Second");

    bindings.bindTwoWay(property1, property2);
    assertThat(property1.get()).isEqualTo("Second");

    bindings.releaseTwoWay(property1, property2);

    property1.set("Property1");
    assertThat(property2.get()).isEqualTo("Second");

    property2.set("Property2");
    assertThat(property1.get()).isEqualTo("Property1");
  }

  @Test
  public void releaseAllDisconnectsOneWayBindings() throws Exception {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    StringValueProperty property1 = new StringValueProperty("A");
    StringValueProperty property2 = new StringValueProperty("B");

    bindings.bind(property1, property2);
    assertThat(property1.get()).isEqualTo("B");

    bindings.releaseAll();

    property2.set("Property2");
    assertThat(property1.get()).isEqualTo("B");
  }

  @Test
  public void releaseAllDisconnectsTwoWayBindings() throws Exception {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    StringValueProperty property1 = new StringValueProperty("First");
    StringValueProperty property2 = new StringValueProperty("Second");

    bindings.bindTwoWay(property1, property2);
    assertThat(property1.get()).isEqualTo("Second");

    bindings.releaseAll();

    property1.set("Property1");
    assertThat(property2.get()).isEqualTo("Second");

    property2.set("Property2");
    assertThat(property1.get()).isEqualTo("Property1");
  }

  @Test
  public void twoWayBindingsCanBeChained() {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    IntValueProperty a = new IntValueProperty();
    IntValueProperty b = new IntValueProperty();
    IntValueProperty c = new IntValueProperty();

    bindings.bindTwoWay(a, b);
    bindings.bindTwoWay(b, c);

    c.set(30);
    assertThat(a.get()).isEqualTo(30);
    assertThat(b.get()).isEqualTo(30);
    assertThat(c.get()).isEqualTo(30);

    b.set(-100);
    assertThat(a.get()).isEqualTo(-100);
    assertThat(b.get()).isEqualTo(-100);
    assertThat(c.get()).isEqualTo(-100);

    a.set(9);
    assertThat(a.get()).isEqualTo(9);
    assertThat(b.get()).isEqualTo(9);
    assertThat(c.get()).isEqualTo(9);
  }

  @Test
  public void testInvokeStrategyOneStepAtATime() throws Exception {
    TestInvokeStrategy testInvokeStrategy = new TestInvokeStrategy();
    BindingsManager bindings = new BindingsManager(testInvokeStrategy);

    IntValueProperty a = new IntValueProperty();
    IntValueProperty b = new IntValueProperty();
    IntValueProperty c = new IntValueProperty();
    IntValueProperty d = new IntValueProperty();

    bindings.bind(a, b);
    bindings.bind(b, c);
    bindings.bind(c, d);

    // Binding properties sets them all to invalid. Update here to validate them.
    testInvokeStrategy.updateOneStep();
    assertThat(a.get()).isEqualTo(0);
    assertThat(b.get()).isEqualTo(0);
    assertThat(c.get()).isEqualTo(0);
    assertThat(d.get()).isEqualTo(0);

    d.set(10);
    assertThat(a.get()).isEqualTo(0);
    assertThat(b.get()).isEqualTo(0);
    assertThat(c.get()).isEqualTo(0);
    assertThat(d.get()).isEqualTo(10);

    testInvokeStrategy.updateOneStep();
    assertThat(a.get()).isEqualTo(0);
    assertThat(b.get()).isEqualTo(0);
    assertThat(c.get()).isEqualTo(10);
    assertThat(d.get()).isEqualTo(10);

    testInvokeStrategy.updateOneStep();
    assertThat(a.get()).isEqualTo(0);
    assertThat(b.get()).isEqualTo(10);
    assertThat(c.get()).isEqualTo(10);
    assertThat(d.get()).isEqualTo(10);

    testInvokeStrategy.updateOneStep();
    assertThat(a.get()).isEqualTo(10);
    assertThat(b.get()).isEqualTo(10);
    assertThat(c.get()).isEqualTo(10);
    assertThat(d.get()).isEqualTo(10);
  }

  private static final class TestInvokeStrategy implements BindingsManager.InvokeStrategy {
    private Runnable myQueuedRunnable;

    @Override
    public void invoke(@NotNull Runnable runnable) {
      myQueuedRunnable = runnable;
    }

    public void updateOneStep() {
      if (myQueuedRunnable != null) {
        Runnable local = myQueuedRunnable;
        myQueuedRunnable = null;
        local.run();
      }
    }
  }
}