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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.util.TypeReference
import java.lang.IllegalStateException

/**
 * Model for handling Gradle properties in the Project Structure Dialog
 */
class PsVariable(private val property: GradlePropertyModel, val module: PsModule) {
  val valueType = property.valueType

  fun <T> getUnresolvedValue(type: TypeReference<T>): T? {
    return property.getRawValue(type)
  }

  fun <T> getResolvedValue(type: TypeReference<T>): T? {
    return property.getValue(type)
  }

  fun setValue(aValue: Any) {
    if (property.valueType == GradlePropertyModel.ValueType.BOOLEAN) {
      property.setValue((aValue as String).toBoolean())
    } else {
      property.setValue(aValue)
    }
    module.isModified = true
  }

  fun delete() {
    property.delete()
    module.isModified = true
  }

  fun setName(newName: String) {
    property.rename(newName)
    module.isModified = true
  }

  fun getName() = property.name

  fun addListValue(value: String): PsVariable {
    if (valueType != GradlePropertyModel.ValueType.LIST) {
      throw IllegalStateException("addListValue can only be called for list variables")
    }

    val listValue = property.addListValue()
    listValue.setValue(value)
    module.isModified = true
    return PsVariable(listValue, module)
  }
}