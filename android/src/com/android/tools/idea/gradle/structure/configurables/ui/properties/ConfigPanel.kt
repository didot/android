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
package com.android.tools.idea.gradle.structure.configurables.ui.properties

import com.android.tools.idea.gradle.structure.configurables.ui.ComponentProvider
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.meta.PropertiesUiModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

/**
 * A panel for editing configuration entities such as [PsProductFlavor] and [PsBuildType].
 *
 * @param ModelT the model type of an entity being edited
 * @param propertiesModel the ui of model of the properties being edited
 */
open class ConfigPanel<in ModelT>(
    private val propertiesModel: PropertiesUiModel<ModelT>
) : ConfigPanelUi(), ComponentProvider, Disposable {
  private var model: ModelT? = null
  private var editors = mutableListOf<ModelPropertyEditor<Any?>>()

  /**
   * Configures the panel for editing of the [model] and binds the editors.
   */
  fun bind(module: PsModule, model: ModelT) {
    this.model = model
    setNumberOfProperties(propertiesModel.properties.size)
    for (property in propertiesModel.properties) {
      val editor: ModelPropertyEditor<Any?> = property.createEditor(module.parent, module, model)
      addPropertyComponents(property.propertyDescription, editor.component, editor.statusComponent)
      editors.add(editor)
    }
  }

  override fun dispose() {
    editors.forEach { Disposer.dispose(it) }
  }
}

