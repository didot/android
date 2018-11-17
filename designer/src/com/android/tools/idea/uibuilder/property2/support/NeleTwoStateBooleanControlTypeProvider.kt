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
package com.android.tools.idea.uibuilder.property2.support

import com.android.tools.idea.common.property2.api.ControlType
import com.android.tools.idea.common.property2.api.EnumSupportProvider
import com.android.tools.idea.uibuilder.property2.NelePropertyItem

class NeleTwoStateBooleanControlTypeProvider(enumSupportProvider: EnumSupportProvider<NelePropertyItem>)
  : NeleControlTypeProvider(enumSupportProvider) {

  override fun invoke(actual: NelePropertyItem): ControlType {
    val type = super.invoke(actual)
    return if (type != ControlType.THREE_STATE_BOOLEAN) type else ControlType.BOOLEAN
  }
}
