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
package com.android.tools.idea.layoutinspector.common

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.layoutinspector.proto.LayoutInspectorProto

class StringTableImpl(strings: List<LayoutInspectorProto.StringEntry>) : StringTable {
  private val table = strings.associateBy({ it.id }, { it.str })

  override operator fun get(id: Int): String = table[id].orEmpty()

  override operator fun get(resource: LayoutInspectorProto.Resource?): ResourceReference? {
    if (resource == null) {
      return null
    }
    val type = table[resource.type] ?: return null
    val namespace = table[resource.namespace] ?: return null
    val name = table[resource.name] ?: return null
    val resNamespace = ResourceNamespace.fromPackageName(namespace)
    val resType = ResourceType.fromFolderName(type) ?: return null
    return ResourceReference(resNamespace, resType, name)
  }
}
