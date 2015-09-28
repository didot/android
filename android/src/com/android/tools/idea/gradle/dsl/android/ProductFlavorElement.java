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
package com.android.tools.idea.gradle.dsl.android;

import com.android.tools.idea.gradle.dsl.*;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.List;
import java.util.Map;

public class ProductFlavorElement extends GradleDslPropertiesElement {
  private static final String APPLICATION_ID = "applicationId";
  private static final String CONSUMER_PROGUARD_FILES = "consumerProguardFiles";
  private static final String DIMENSION = "dimension";
  private static final String MANIFEST_PLACEHOLDERS = "manifestPlaceholders";
  private static final String MAX_SDK_VERSION = "maxSdkVersion";
  private static final String MIN_SDK_VERSION = "minSdkVersion";
  private static final String MULTI_DEX_ENABLED = "multiDexEnabled";
  private static final String PROGUARD_FILES = "proguardFiles";
  private static final String RES_CONFIGS = "resConfigs";
  private static final String RES_VALUES = "resValues";
  private static final String TARGET_SDK_VERSION = "targetSdkVersion";
  private static final String TEST_APPLICATION_ID = "testApplicationId";
  private static final String TEST_FUNCTIONAL_TEST = "testFunctionalTest";
  private static final String TEST_HANDLE_PROFILING = "testHandleProfiling";
  private static final String TEST_INSTRUMENTATION_RUNNER = "testInstrumentationRunner";
  private static final String TEST_INSTRUMENTATION_RUNNER_ARGUMENTS = "testInstrumentationRunnerArguments";
  private static final String USE_JACK = "useJack";
  private static final String VERSION_CODE = "versionCode";
  private static final String VERSION_NAME = "versionName";

  @NotNull
  private final String myName;

  public ProductFlavorElement(@Nullable GradleDslElement parent, @NotNull String name) {
    super(parent);
    myName = name;
  }

  @NotNull
  public String name() {
    return myName;
  }

  @Nullable
  public String applicationId() {
    return getProperty(APPLICATION_ID, String.class);
  }

  @Nullable
  public List<String> consumerProguardFiles() {
    return getListProperty(CONSUMER_PROGUARD_FILES, String.class);
  }

  @Nullable
  public String dimension() {
    return getProperty(DIMENSION, String.class);
  }

  @Nullable
  public Map<String, Object> manifestPlaceholders() {
    return getMapProperty(MANIFEST_PLACEHOLDERS, Object.class);
  }

  @Nullable
  public Integer maxSdkVersion() {
    return getProperty(MAX_SDK_VERSION, Integer.class);
  }

  @Nullable
  public String minSdkVersion() {
    Integer intValue = getProperty(MIN_SDK_VERSION, Integer.class);
    return intValue != null ? intValue.toString() : getProperty(MIN_SDK_VERSION, String.class);
  }

  @Nullable
  public Boolean multiDexEnabled() {
    return getProperty(MULTI_DEX_ENABLED, Boolean.class);
  }

  @Nullable
  public List<String> proguardFiles() {
    return getListProperty(PROGUARD_FILES, String.class);
  }

  @Nullable
  public List<String> resConfigs() {
    return getListProperty(RES_CONFIGS, String.class);
  }

  @Nullable
  public List<ResValue> resValues() {
    List<String> resValues = getListProperty(RES_VALUES, String.class);
    if (resValues != null && resValues.size() % 3 == 0) {
      List<ResValue> result = Lists.newArrayList();
      for (int i = 0; i < resValues.size(); i += 3) {
        result.add(new ResValue(resValues.get(i), resValues.get(i + 1), resValues.get(i + 2)));
      }
      return result;
    }
    return null;
  }

  @Nullable
  public String targetSdkVersion() {
    Integer intValue = getProperty(TARGET_SDK_VERSION, Integer.class);
    return intValue != null ? intValue.toString() : getProperty(TARGET_SDK_VERSION, String.class);
  }

  @Nullable
  public String testApplicationId() {
    return getProperty(TEST_APPLICATION_ID, String.class);
  }

  @Nullable
  public Boolean testFunctionalTest() {
    return getProperty(TEST_FUNCTIONAL_TEST, Boolean.class);
  }

  @Nullable
  public Boolean testHandleProfiling() {
    return getProperty(TEST_HANDLE_PROFILING, Boolean.class);
  }

  @Nullable
  public String testInstrumentationRunner() {
    return getProperty(TEST_INSTRUMENTATION_RUNNER, String.class);
  }

  @Nullable
  public Map<String, String> testInstrumentationRunnerArguments() {
    return getMapProperty(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, String.class);
  }

  @Nullable
  public Boolean useJack() {
    return getProperty(USE_JACK, Boolean.class);
  }

  @Nullable
  public String versionCode() {
    Integer intValue = getProperty(VERSION_CODE, Integer.class);
    return intValue != null ? intValue.toString() : getProperty(VERSION_CODE, String.class);
  }

  @Nullable
  public String versionName() {
    return getProperty(VERSION_NAME, String.class);
  }

  @Override
  public void addProperty(@NotNull String property, @NotNull GradleDslElement element) {
    if (property.equals(PROGUARD_FILES) || property.equals("proguardFile")) {
      addToListElement(PROGUARD_FILES, element);
      return;
    }

    if (property.equals(RES_CONFIGS) || property.equals("resConfig")) {
      addToListElement(RES_CONFIGS, element);
      return;
    }

    if (property.equals("resValue")) {
      if (!(element instanceof ListElement)) {
        return;
      }
      ListElement listElement = (ListElement)element;
      if (listElement.getElements().size() != 3 || listElement.getValues(String.class).size() != 3) {
        return;
      }
      addToListElement(RES_VALUES, element);
    }

    if (property.equals(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS)) {
      if (!(element instanceof MapElement)) {
        return;
      }
      MapElement testInstrumentationRunnerArgumentsElement = getProperty(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, MapElement.class);
      if (testInstrumentationRunnerArgumentsElement == null) {
        setProperty(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, element);
      } else {
        MapElement elementsToAdd = (MapElement)element;
        for (String key : elementsToAdd.getProperties()) {
          GradleDslElement elementToAdd = elementsToAdd.getPropertyElement(key);
          if (elementToAdd != null) {
            testInstrumentationRunnerArgumentsElement.setProperty(key, elementToAdd);
          }
        }
      }
      return;
    }

    if (property.equals("testInstrumentationRunnerArgument")) {
      if (!(element instanceof ListElement)) {
        return;
      }
      ListElement listElement = (ListElement)element;
      List<LiteralElement> elements = listElement.getElements();
      if (elements.size() != 2) {
        return;
      }

      String key = elements.get(0).getValue(String.class);
      if (key == null) {
        return;
      }
      LiteralElement value = elements.get(1);

      MapElement testInstrumentationRunnerArgumentsElement = getProperty(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, MapElement.class);
      if (testInstrumentationRunnerArgumentsElement == null) {
        testInstrumentationRunnerArgumentsElement = new MapElement(this, TEST_INSTRUMENTATION_RUNNER_ARGUMENTS);
      }
      testInstrumentationRunnerArgumentsElement.setProperty(key, value);
      return;
    }

    super.addProperty(property, element);
  }

  private void addToListElement(@NotNull String property, @NotNull GradleDslElement element) {
    GrLiteral[] literalsToAdd =  null;
    if (element instanceof LiteralElement) {
      literalsToAdd = new GrLiteral[]{((LiteralElement)element).getLiteral()};
    } else if (element instanceof ListElement) {
      List<LiteralElement> literalElements = ((ListElement)element).getElements();
      literalsToAdd = new GrLiteral[literalElements.size()];
      for (int i = 0; i < literalElements.size(); i++) {
        literalsToAdd[i] = literalElements.get(i).getLiteral();
      }
    }
    if (literalsToAdd == null) {
      return;
    }

    ListElement listElement = getProperty(property, ListElement.class);
    if (listElement != null) {
      listElement.add(property, literalsToAdd);
      return;
    }

    listElement = new ListElement(this, property, literalsToAdd);
    super.addProperty(property, listElement);
  }

  /**
   * Represents a {@code resValue} statement defined in the product flavor block of the Gradle file.
   */
  static final class ResValue {
    @NotNull private final String myType;
    @NotNull private final String myName;
    @NotNull private final String myValue;

    public ResValue(@NotNull String type, @NotNull String name, @NotNull String value) {
      myType = type;
      myName = name;
      myValue = value;
    }

    @NotNull
    public String type() {
      return myType;
    }

    @NotNull
    public String name() {
      return myName;
    }

    @NotNull
    public String value() {
      return myValue;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myType, myName, myValue);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ResValue)) {
        return false;
      }

      ResValue other = (ResValue)o;
      return myType.equals(other.myType) && myName.equals(other.myName) && myValue.equals(other.myValue);
    }

    @Override
    public String toString() {
      return String.format("Type: %1$s, Name: %2$s, Value: %3$s", myType, myName, myValue);
    }
  }
}
