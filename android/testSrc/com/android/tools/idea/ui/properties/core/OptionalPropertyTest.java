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
package com.android.tools.idea.ui.properties.core;

import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.CountListener;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

public final class OptionalPropertyTest {
  @Test
  public void testInitializationByStaticMethodOf() {
    OptionalProperty<String> optStringValue = OptionalProperty.of("Test");
    assertThat(optStringValue.get().isPresent()).isTrue();
    assertThat(optStringValue.getValue()).isEqualTo("Test");
    assertThat(optStringValue.get().get()).isEqualTo("Test"); // Grab the underlying optional
  }

  @Test
  public void testInitializationByStaticMethodAbsent() {
    OptionalProperty<String> optStringValue = OptionalProperty.absent();
    assertThat(optStringValue.get().isPresent()).isFalse();
  }

  @Test
  public void testInitializationByStaticMethodFromNullableWithValue() throws Exception {
    OptionalProperty<String> optStringValue = OptionalProperty.fromNullable("Test");
    assertThat(optStringValue.get().isPresent()).isTrue();
  }

  @Test
  public void testInitializationByStaticMethodFromNullableWithNull() throws Exception {
    OptionalProperty<String> optStringValue = OptionalProperty.fromNullable(null);
    assertThat(optStringValue.get().isPresent()).isFalse();
  }

  @Test
  public void testSetValue() {
    OptionalProperty<String> optStringValue = OptionalProperty.of("Hello");
    optStringValue.setValue("Goodbye");
    assertThat(optStringValue.getValue()).isEqualTo("Goodbye");
  }

  @Test
  public void testClearValue() {
    OptionalProperty<String> optStringValue = OptionalProperty.of("Dummy text");
    optStringValue.clear();
    assertThat(optStringValue.get().isPresent()).isFalse();
  }

  @Test
  public void testSetNullableValue() throws Exception {
    OptionalProperty<String> optStringValue = OptionalProperty.absent();
    optStringValue.setNullableValue("Hello");
    assertThat(optStringValue.getValue()).isEqualTo("Hello");
    optStringValue.setNullableValue(null);
    assertThat(optStringValue.get().isPresent()).isEqualTo(false);
  }

  @Test
  public void testGetValueOr() {
    OptionalProperty<String> optStringValue = OptionalProperty.absent();
    assertThat(optStringValue.getValueOr("Default")).isEqualTo("Default");
    assertThat(optStringValue.get().isPresent()).isFalse();

    optStringValue.setValue("Not Default");
    assertThat(optStringValue.getValueOr("Default")).isEqualTo("Not Default");
    assertThat(optStringValue.get().isPresent()).isTrue();
  }

  @Test
  public void testGetValueOrNull() {
    OptionalProperty<String> optStringValue = OptionalProperty.absent();
    assertThat(optStringValue.getValueOrNull()).isNull();
  }

  @Test(expected = IllegalStateException.class)
  public void testGetOnAbsentOptionThrowsException() {
    OptionalProperty<String> optStringValue = OptionalProperty.absent();
    optStringValue.getValue();
  }

  @Test
  public void testInvalidationListenerFiredOnValueChange() {
    OptionalProperty<String> optStringValue = OptionalProperty.absent();
    CountListener listener = new CountListener();
    optStringValue.addListener(listener);

    assertThat(listener.getCount()).isEqualTo(0);
    optStringValue.setValue("Text");
    assertThat(listener.getCount()).isEqualTo(1);
    optStringValue.setValue("Text");
    assertThat(listener.getCount()).isEqualTo(1);
    optStringValue.clear();
    assertThat(listener.getCount()).isEqualTo(2);
    optStringValue.clear();
    assertThat(listener.getCount()).isEqualTo(2);
  }

  @Test
  public void testIsPresentBinding() throws Exception {
    OptionalProperty<String> optString = OptionalProperty.of("Hello");
    OptionalProperty<Integer> optInt = OptionalProperty.of(10);

    BindingsManager bindings = new BindingsManager(BindingsManager.INVOKE_IMMEDIATELY_STRATEGY);
    BoolProperty areBothPresent = new BoolValueProperty();
    bindings.bind(areBothPresent, optString.isPresent().and(optInt.isPresent()));

    assertThat(areBothPresent.get()).isTrue();
    optString.clear();
    assertThat(areBothPresent.get()).isFalse();
    optString.setValue("I'm back");
    assertThat(areBothPresent.get()).isTrue();
    optInt.clear();
    assertThat(areBothPresent.get()).isFalse();
    optString.clear();
    assertThat(areBothPresent.get()).isFalse();
  }
}

