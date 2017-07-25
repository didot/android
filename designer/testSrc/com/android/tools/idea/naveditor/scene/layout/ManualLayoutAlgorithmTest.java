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
package com.android.tools.idea.naveditor.scene.layout;

import com.android.tools.idea.naveditor.NavigationTestCase;
import com.android.tools.idea.uibuilder.SyncNlModel;
import com.android.tools.idea.uibuilder.scene.Scene;
import org.jetbrains.android.dom.navigation.NavigationSchema;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link ManualLayoutAlgorithm}
 */
public class ManualLayoutAlgorithmTest extends NavigationTestCase {

  public void testSimple() throws Exception {
    SyncNlModel model = model("nav.xml",
                              rootComponent().unboundedChildren(
                                fragmentComponent("fragment1"),
                                fragmentComponent("fragment2"))).build();
    ManualLayoutAlgorithm.LayoutPositions positions = new ManualLayoutAlgorithm.LayoutPositions();
    positions.myPositions.put("fragment1", new ManualLayoutAlgorithm.Point(123, 456));
    positions.myPositions.put("fragment2", new ManualLayoutAlgorithm.Point(456, 789));
    Scene scene = model.getSurface().getScene();
    NavSceneLayoutAlgorithm fallback = mock(NavSceneLayoutAlgorithm.class);
    ManualLayoutAlgorithm algorithm = new ManualLayoutAlgorithm(fallback, NavigationSchema.getOrCreateSchema(myAndroidFacet), positions);
    scene.getRoot().flatten().forEach(algorithm::layout);
    verifyZeroInteractions(fallback);

    assertEquals(123, scene.getSceneComponent("fragment1").getDrawX());
    assertEquals(456, scene.getSceneComponent("fragment1").getDrawY());
    assertEquals(456, scene.getSceneComponent("fragment2").getDrawX());
    assertEquals(789, scene.getSceneComponent("fragment2").getDrawY());
  }

  public void testFallback() throws Exception {
    SyncNlModel model = model("nav.xml",
                              rootComponent().unboundedChildren(
                                fragmentComponent("fragment1"),
                                fragmentComponent("fragment2"),
                                fragmentComponent("fragment3"))).build();
    ManualLayoutAlgorithm.LayoutPositions positions = new ManualLayoutAlgorithm.LayoutPositions();
    positions.myPositions.put("fragment1", new ManualLayoutAlgorithm.Point(60, 60));
    positions.myPositions.put("fragment3", new ManualLayoutAlgorithm.Point(200, 200));
    Scene scene = model.getSurface().getScene();
    NavSceneLayoutAlgorithm fallback = mock(NavSceneLayoutAlgorithm.class);
    ManualLayoutAlgorithm algorithm = new ManualLayoutAlgorithm(fallback, NavigationSchema.getOrCreateSchema(myAndroidFacet), positions);
    scene.getRoot().flatten().forEach(algorithm::layout);
    verify(fallback).layout(scene.getSceneComponent("fragment2"));
    verifyNoMoreInteractions(fallback);

    assertEquals(60, scene.getSceneComponent("fragment1").getDrawX());
    assertEquals(60, scene.getSceneComponent("fragment1").getDrawY());
    assertEquals(200, scene.getSceneComponent("fragment3").getDrawX());
    assertEquals(200, scene.getSceneComponent("fragment3").getDrawY());
  }
}
