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
package com.android.tools.idea.properties.basicTypes;

import com.android.tools.idea.properties.InvalidationListener;
import com.android.tools.idea.properties.ObservableValue;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

public final class StringValuePropertyTest {
  @Test
  public void testInitialization() {
    {
      StringValueProperty stringValue = new StringValueProperty("Initial value");
      assertThat(stringValue.get()).isEqualTo("Initial value");
    }

    {
      StringValueProperty stringValue = new StringValueProperty();
      assertThat(stringValue.get()).isEmpty();
    }
  }

  @Test
  public void testSetValue() {
    StringValueProperty stringValue = new StringValueProperty("Hello");
    stringValue.set("Goodbye");
    assertThat(stringValue.get()).isEqualTo("Goodbye");
  }

  @Test
  public void testInvalidationListenerFiredOnValueChange() {
    StringValueProperty stringValue = new StringValueProperty();
    CountListener listener = new CountListener();
    stringValue.addListener(listener);

    assertThat(listener.myCount).isEqualTo(0);
    stringValue.set("Text");
    assertThat(listener.myCount).isEqualTo(1);
    stringValue.set("Text");
    assertThat(listener.myCount).isEqualTo(1);
  }

  private static class CountListener extends InvalidationListener<String> {
    public int myCount;

    @Override
    protected void onInvalidated(@NotNull ObservableValue<String> sender) {
      myCount++;
    }
  }
}