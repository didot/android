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
package com.android.tools.idea.uibuilder.handlers;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.model.NlComponent;
import icons.AndroidIcons;
import org.intellij.lang.annotations.Language;

import javax.swing.*;

/**
 * Handler for the {@code requestFocus} tag.
 */
public class RequestFocusHandler extends ViewHandler {

  @Override
  @NonNull
  public String getTitle(@NonNull String tagName) {
    return "<requestFocus>";
  }

  @Override
  @NonNull
  public String getTitle(@NonNull NlComponent component) {
    return "<requestFocus>";
  }

  @Override
  @NonNull
  public Icon getIcon(@NonNull String tagName) {
    return AndroidIcons.Views.RequestFocus;
  }

  @Override
  @NonNull
  public Icon getIcon(@NonNull NlComponent component) {
    return AndroidIcons.Views.RequestFocus;
  }

  @Override
  @Language("XML")
  @NonNull
  public String getXml(@NonNull String tagName, @NonNull XmlType xmlType) {
    switch (xmlType) {
      case COMPONENT_CREATION:
        return "<requestFocus/>";
      default:
        return NO_PREVIEW;
    }
  }
}
