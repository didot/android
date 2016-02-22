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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.*;

/**
 * Tests for {@link ArtifactDependencySpec}.
 */
public class ArtifactDependencySpecTest {
  private ArtifactDependencySpec myDependency;

  @Before
  public void setUp() {
    myDependency = new ArtifactDependencySpec("name", "group", "version");
  }

  @Test
  public void testCreate1() {
    myDependency = ArtifactDependencySpec.create("group:name:version:classifier@extension");
    assertNotNull(myDependency);
    assertEquals("group", myDependency.group);
    assertEquals("name", myDependency.name);
    assertEquals("version", myDependency.version);
    assertEquals("classifier", myDependency.classifier);
    assertEquals("extension", myDependency.extension);
  }

  @Test
  public void testCreate2() {
    myDependency = ArtifactDependencySpec.create("group:name:version@extension");
    assertNotNull(myDependency);
    assertEquals("group", myDependency.group);
    assertEquals("name", myDependency.name);
    assertEquals("version", myDependency.version);
    assertNull(myDependency.classifier);
    assertEquals("extension", myDependency.extension);
  }

  @Test
  public void testCreate3() {
    myDependency = ArtifactDependencySpec.create("group:name:version@extension");
    assertNotNull(myDependency);
    assertEquals("group", myDependency.group);
    assertEquals("name", myDependency.name);
    assertEquals("version", myDependency.version);
    assertNull(myDependency.classifier);
    assertEquals("extension", myDependency.extension);
  }

  @Test
  public void testCreate4() {
    myDependency = ArtifactDependencySpec.create("com.google.javascript:closure-compiler:v20151216");
    assertNotNull(myDependency);
    assertEquals("com.google.javascript", myDependency.group);
    assertEquals("closure-compiler", myDependency.name);
    assertEquals("v20151216", myDependency.version);
  }

  @Test
  public void testGetCompactNotationWithoutClassifierAndExtension() {
    assertEquals("group:name:version", myDependency.compactNotation());
  }

  @Test
  public void testGetCompactNotationWithoutExtension() {
    myDependency.classifier = "classifier";
    assertEquals("group:name:version:classifier", myDependency.compactNotation());
  }

  @Test
  public void testGetCompactNotationWithoutClassifier() {
    myDependency.extension = "ext";
    assertEquals("group:name:version@ext", myDependency.compactNotation());
  }

  @Test
  public void testGetCompactNotation() {
    myDependency.classifier = "classifier";
    myDependency.extension = "ext";
    assertEquals("group:name:version:classifier@ext", myDependency.compactNotation());
  }
}