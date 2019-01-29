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
package com.android.tools.idea.naveditor.property

import com.android.annotations.VisibleForTesting
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.property.inspector.SimpleProperty
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap

abstract class ListProperty(name: String, components: List<NlComponent>) : SimpleProperty(name, components) {
  val properties: ListMultimap<String, NlProperty> = ArrayListMultimap.create()

  abstract fun refreshList()

  override fun getChildProperty(name: String): NlProperty = throw NotImplementedError()

  @VisibleForTesting
  fun getChildProperties(name: String): List<NlProperty> = properties[name]
}