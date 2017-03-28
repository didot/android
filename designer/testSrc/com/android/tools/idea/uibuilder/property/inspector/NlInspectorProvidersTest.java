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
package com.android.tools.idea.uibuilder.property.inspector;

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.android.tools.idea.uibuilder.property.editors.NlPropertyEditors;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

public class NlInspectorProvidersTest extends PropertyTestCase {
  private NlInspectorProviders myProviders;
  private NlPropertiesManager myPropertiesManager;
  private Disposable myDisposable;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myPropertiesManager = mock(NlPropertiesManager.class);
    when(myPropertiesManager.getProject()).thenReturn(getProject());
    when(myPropertiesManager.getPropertyEditors()).thenReturn(NlPropertyEditors.getInstance(getProject()));
    myDisposable = Disposer.newDisposable();
    myProviders = new NlInspectorProviders(myPropertiesManager, myDisposable);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      Disposer.dispose(myDisposable);
    }
    finally {
      super.tearDown();
    }
  }


  public void testLookAndFeelChange() {
    List<InspectorComponent> emptyInspectors = getInspectorsFor(ImmutableList.of());
    List<InspectorComponent> textInspectors = getInspectorsFor(myTextView);
    List<InspectorComponent> progressInspectors = getInspectorsFor(myProgressBar);
    List<InspectorComponent> buttonInspectors = getInspectorsFor(myButton);

    assertSameInstances(emptyInspectors, getInspectorsFor(ImmutableList.of()));
    assertSameInstances(textInspectors, getInspectorsFor(myTextView));
    assertSameInstances(progressInspectors, getInspectorsFor(myProgressBar));
    assertSameInstances(buttonInspectors, getInspectorsFor(myButton));

    myProviders.lookAndFeelChanged(LafManager.getInstance());

    assertDifferentInstances(emptyInspectors, getInspectorsFor(ImmutableList.of()));
    assertDifferentInstances(textInspectors, getInspectorsFor(myTextView));
    assertDifferentInstances(progressInspectors, getInspectorsFor(myProgressBar));
    assertDifferentInstances(buttonInspectors, getInspectorsFor(myButton));

    verify(myPropertiesManager).updateSelection();
  }

  @NotNull
  private List<InspectorComponent> getInspectorsFor(@NotNull NlComponent component) {
    return getInspectorsFor(ImmutableList.of(component));
  }

  @NotNull
  private List<InspectorComponent> getInspectorsFor(@NotNull List<NlComponent> components) {
    Map<String, NlProperty> properties = getPropertyMap(components);
    return myProviders.createInspectorComponents(components, properties, myPropertiesManager);
  }

  private static void assertSameInstances(@NotNull List<InspectorComponent> expected,
                                          @NotNull List<InspectorComponent> actual) {
    assertThat(actual.size()).isEqualTo(expected.size());
    for (int index = 0; index < expected.size(); index++) {
      assertThat(actual.get(index)).isSameAs(expected.get(index));
    }
  }

  private static void assertDifferentInstances(@NotNull List<InspectorComponent> expected,
                                               @NotNull List<InspectorComponent> actual) {
    assertThat(actual.size()).isEqualTo(expected.size());
    for (int index = 0; index < expected.size(); index++) {
      assertThat(actual.get(index)).isNotSameAs(expected.get(index));
    }
  }
}
