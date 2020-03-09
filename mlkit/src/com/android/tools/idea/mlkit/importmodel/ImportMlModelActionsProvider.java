/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.mlkit.importmodel;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.templates.AdditionalTemplateActionsProvider;
import com.android.tools.idea.wizard.template.Category;
import com.intellij.openapi.actionSystem.AnAction;
import java.util.ArrayList;
import java.util.List;

public class ImportMlModelActionsProvider implements AdditionalTemplateActionsProvider {
  @Override
  public List<AnAction> getAdditionalActions(Category category) {
    List<AnAction> additionalActions = new ArrayList<>();
    if (StudioFlags.MLKIT_TFLITE_MODEL_FILE_TYPE.get() && category == Category.Other) {
      additionalActions.add(new AndroidImportMlModelAction());
    }
    return additionalActions;
  }
}
