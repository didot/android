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
package com.android.tools.idea.uibuilder.handlers.menu;

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.rendering.api.ViewType;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

final class TestFactory {
  private TestFactory() {
  }

  @NotNull
  static ViewInfo mockViewInfo(@NotNull ViewType viewType) {
    ViewInfo view = Mockito.mock(ViewInfo.class);
    Mockito.when(view.getViewType()).thenReturn(viewType);

    return view;
  }

  @NotNull
  static NlComponent newNlComponent(@NotNull String tagName) {
    XmlTag tag = Mockito.mock(XmlTag.class);
    Mockito.when(tag.getName()).thenReturn(tagName);

    return new NlComponent(Mockito.mock(NlModel.class), tag);
  }
}
