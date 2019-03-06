/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.model;

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.resourceExplorer.ResourceManagerTracking;
import com.android.tools.idea.resourceExplorer.viewmodel.ResourceDataManagerKt;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.InvalidDnDOperationException;
import java.io.IOException;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DnDTransferItem {
  private final boolean myFromPalette;
  private final long myModelId;
  private final ImmutableList<DnDTransferComponent> myComponents;
  private boolean myIsCut;

  /**
   * Create a drag and drop item for a new component from the palette.
   */
  public DnDTransferItem(@NotNull DnDTransferComponent component) {
    this(true, 0, ImmutableList.of(component));
  }

  /**
   * Create a drag and drop item for existing designer components.
   */
  public DnDTransferItem(long modelId, @NotNull ImmutableList<DnDTransferComponent> components) {
    this(false, modelId, components);
  }

  private DnDTransferItem(boolean fromPalette, long modelId, @NotNull ImmutableList<DnDTransferComponent> components) {
    myFromPalette = fromPalette;
    myModelId = modelId;
    myComponents = components;
  }

  @Nullable
  public static DnDTransferItem getTransferItem(@NotNull Transferable transferable, boolean allowPlaceholder) {
    try {
      if (transferable.isDataFlavorSupported(ItemTransferable.DESIGNER_FLAVOR)) {
        return (DnDTransferItem)transferable.getTransferData(ItemTransferable.DESIGNER_FLAVOR);
      }

      if (transferable.isDataFlavorSupported(ResourceDataManagerKt.RESOURCE_URL_FLAVOR)) {
        ResourceUrl url = (ResourceUrl)transferable.getTransferData(ResourceDataManagerKt.RESOURCE_URL_FLAVOR);
        DnDTransferItem item = fromResourceUrl(url);
        if (item != null) {
          return item;
        }
      }

      if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        String xml = (String)transferable.getTransferData(DataFlavor.stringFlavor);
        if (!StringUtil.isEmpty(xml)) {
          return new DnDTransferItem(new DnDTransferComponent("", xml, 200, 100));
        }
      }
    }
    catch (InvalidDnDOperationException ex) {
      if (!allowPlaceholder) {
        return null;
      }
      String defaultXml = "<placeholder xmlns:android=\"http://schemas.android.com/apk/res/android\"/>";
      return new DnDTransferItem(new DnDTransferComponent("", defaultXml, 200, 100));
    }
    catch (IOException | UnsupportedFlavorException ex) {
      Logger.getInstance(DnDTransferItem.class).warn(ex);
    }
    return null;
  }

  private static DnDTransferItem fromResourceUrl(ResourceUrl url) {
    if (url.type == ResourceType.DRAWABLE) {
      @Language("XML")
      String representation = "<ImageView\n" +
                              "    android:layout_width=\"wrap_content\"\n" +
                              "    android:layout_height=\"wrap_content\"\n" +
                              "    android:src=\"" + url.toString() + "\"/>";
      ResourceManagerTracking.INSTANCE.logDragOnViewGroup();
      return new DnDTransferItem(new DnDTransferComponent(SdkConstants.IMAGE_VIEW, representation, 200, 100));
    }
    return null;
  }

  public boolean isFromPalette() {
    return myFromPalette;
  }

  public long getModelId() {
    return myModelId;
  }

  public void setIsCut() {
    myIsCut = true;
  }

  public boolean isCut() {
    return myIsCut;
  }

  public void consumeCut() {
    myIsCut = false;
  }

  public ImmutableList<DnDTransferComponent> getComponents() {
    return myComponents;
  }
}
