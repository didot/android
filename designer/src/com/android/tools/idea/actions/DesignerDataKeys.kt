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
@file:JvmName("DesignerDataKeys")

package com.android.tools.idea.actions

import com.android.tools.idea.common.editor.DesignerEditorPanel
import com.intellij.openapi.actionSystem.DataKey

/**
 * Data key for the actions work in Designer Editor. This includes DesignSurface, ActionToolBar, and all attached ToolWindows.
 */
@JvmField
val DESIGN_EDITOR: DataKey<DesignerEditorPanel> = DataKey.create(DesignerEditorPanel::class.qualifiedName!!)
