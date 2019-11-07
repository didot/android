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

import com.android.tools.idea.npw.model.NewModuleModel;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ModuleGalleryEntry {
  /**
   * @return icon to be used in the gallery.
   */
  @Nullable
  Icon getIcon();

  /**
   * @return module template name.
   */
  @NotNull
  String getName();

  /**
   * @return description of the template or {@code null} if none.
   */
  @Nullable
  String getDescription();

  /**
   * @return a new instance of a wizard step that will allow the user to edit the details of this module entry
   */
  @NotNull
  SkippableWizardStep createStep(@NotNull NewModuleModel model);
}
