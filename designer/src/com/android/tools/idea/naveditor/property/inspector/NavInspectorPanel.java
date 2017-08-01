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
package com.android.tools.idea.naveditor.property.inspector;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.common.property.inspector.InspectorPanel;
import com.android.tools.idea.naveditor.property.NavPropertiesManager;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class NavInspectorPanel extends InspectorPanel<NavPropertiesManager> {
  public NavInspectorPanel(@NotNull Disposable parentDisposable) {
    super(parentDisposable, null);
  }

  @Override
  protected void collectExtraProperties(@NotNull List<NlComponent> components,
                                        @NotNull NavPropertiesManager propertiesManager,
                                        Map<String, NlProperty> propertiesByName) {

  }
}
