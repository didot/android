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
package com.android.tools.idea.naveditor.model;

import com.android.tools.idea.naveditor.NavigationTestCase;
import com.android.tools.idea.uibuilder.SyncNlModel;
import com.android.tools.idea.uibuilder.fixtures.ComponentDescriptor;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.util.NlTreeDumper;
import org.jetbrains.android.dom.navigation.NavigationSchema;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link NlModel} as used in the navigation editor
 */
public class NavNlModelTest extends NavigationTestCase {

  public void testAddChild() throws Exception {
    NlTreeDumper treeDumper = new NlTreeDumper();
    ModelBuilder modelBuilder = model("nav.xml",
                                component(NavigationSchema.TAG_NAVIGATION).unboundedChildren(
                                  component(NavigationSchema.TAG_FRAGMENT).id("@id/fragment1"),
                                  component(NavigationSchema.TAG_FRAGMENT).id("@id/fragment2")));
    SyncNlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<navigation>, instance=0}\n" +
                 "    NlComponent{tag=<fragment>, instance=1}\n" +
                 "    NlComponent{tag=<fragment>, instance=2}",
                 treeDumper.toTree(model.getComponents()));

    // Add child
    ComponentDescriptor parent = modelBuilder.findByPath(NavigationSchema.TAG_NAVIGATION);
    assertThat(parent).isNotNull();
    parent.addChild(component(NavigationSchema.TAG_ACTION).id("@id/action"), null);
    modelBuilder.updateModel(model);

    assertEquals("NlComponent{tag=<navigation>, instance=0}\n" +
                 "    NlComponent{tag=<fragment>, instance=1}\n" +
                 "    NlComponent{tag=<fragment>, instance=2}\n" +
                 "    NlComponent{tag=<action>, instance=3}",
                 treeDumper.toTree(model.getComponents()));
  }

}
