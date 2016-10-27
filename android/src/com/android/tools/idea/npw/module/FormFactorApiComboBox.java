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
package com.android.tools.idea.npw.module;

import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.google.common.base.Objects;
import com.intellij.ide.util.PropertiesComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.util.List;

import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MIN_API;

/**
 * A labeled combo box of SDK options for a given FormFactor.
 */
public final class FormFactorApiComboBox extends JComboBox<AndroidVersionsInfo.VersionItem> {
  private FormFactor myFormFactor;

  public void init(@NotNull FormFactor formFactor, @NotNull List<AndroidVersionsInfo.VersionItem> items) {

    myFormFactor = formFactor;

    removeAllItems();
    for (AndroidVersionsInfo.VersionItem item : items) {
      addItem(item);
    }

    loadSavedApi();
    addItemListener(this::saveSelectedApi);
  }

  /**
   * Load the saved value for this ComboBox
   */
  private void loadSavedApi() {
    // Check for a saved value for the min api level
    String savedApiLevel = PropertiesComponent.getInstance().getValue(getPropertiesComponentMinSdkKey(myFormFactor),
                                                                      Integer.toString(myFormFactor.defaultApi));

    // If the savedApiLevel is not available, just pick the first target in the list
    int index = getItemCount() > 0 ? 0 : -1;
    for (int i = 0; i < getItemCount(); i++) {
      AndroidVersionsInfo.VersionItem item = getItemAt(i);
      if (Objects.equal(item.getApiLevelStr(), savedApiLevel)) {
        index = i;
        break;
      }
    }

    setSelectedIndex(index);
  }

  private void saveSelectedApi(ItemEvent e) {
    if (e.getStateChange() == ItemEvent.SELECTED && e.getItem() != null) {
      AndroidVersionsInfo.VersionItem item = (AndroidVersionsInfo.VersionItem)e.getItem();
      PropertiesComponent.getInstance().setValue(getPropertiesComponentMinSdkKey(myFormFactor), item.getApiLevelStr());
    }
  }

  private static String getPropertiesComponentMinSdkKey(@NotNull FormFactor formFactor) {
    return formFactor.id + ATTR_MIN_API;
  }
}
