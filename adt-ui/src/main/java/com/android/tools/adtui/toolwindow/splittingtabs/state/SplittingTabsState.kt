/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.toolwindow.splittingtabs.state

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.Text
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.annotations.XCollection.Style.v2

@Tag("tool-windows")
internal data class SplittingTabsState(
  @XCollection(propertyElementName = "tool-windows", style = v2) var toolWindows: List<ToolWindowState> = mutableListOf()
)

@Tag("tool-window")
internal data class ToolWindowState(
  @Attribute("id") var toolWindowId: String = "",
  @XCollection(propertyElementName = "tabs", style = v2) var tabStates: List<TabState> = mutableListOf(),
  @Attribute("selected-tab") var selectedTabIndex: Int = -1
)

@Tag("tab")
internal data class TabState(
  @Attribute("name") var tabName: String = "",
  @Text var clientState: String? = null,
)
