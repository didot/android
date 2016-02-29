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
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.google.common.collect.ImmutableList;
import org.intellij.lang.annotations.Language;

import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Handler for the widgets for which no preview should be rendered.
 * Examples: Space, SurfaceView, etc
 */
public class NoPreviewHandler extends ViewHandler {

  // A list of simple components that have no preview.
  private static final List<String> HAVE_NO_PREVIEW = ImmutableList.of(SPACE, SURFACE_VIEW, TEXTURE_VIEW);

  @Override
  @Language("XML")
  @NonNull
  public String getXml(@NonNull String tagName, @NonNull XmlType xmlType) {
    switch (xmlType) {
      case PREVIEW_ON_PALETTE:
      case DRAG_PREVIEW:
        return NO_PREVIEW;
      default:
        return super.getXml(tagName, xmlType);
    }
  }

  public static boolean hasNoPreview(@NonNull String tagName) {
    return HAVE_NO_PREVIEW.contains(tagName);
  }
}
