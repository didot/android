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

import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.editor.AnimationToolbar
import com.android.tools.idea.uibuilder.lint.CommonPanelIssueSet
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.util.Key
import java.util.concurrent.CountDownLatch

/**
 * Data key for the actions work in Design Editor. This includes DesignSurface and ActionToolBar, but **exclude** all attached ToolWindows.
 * Attached ToolWindows take the responsibility of handling shortcuts and key events. For example, when focusing Palette, typing means
 * search the widget.
 */
@JvmField
val DESIGN_SURFACE: DataKey<DesignSurface> = DataKey.create(DesignSurface::class.qualifiedName!!)

@JvmField
val ANIMATION_TOOLBAR: DataKey<AnimationToolbar> = DataKey.create(AnimationToolbar::class.qualifiedName!!)

private const val COMMON_PROBLEMS_PANEL_ISSUE = "COMMON_PROBLEMS_PANEL_ISSUE"

/**
 * User data key for render related issues. It is used as a bus between external annotator and design surface.
 */
@JvmField
val ATF_ISSUES: Key<CommonPanelIssueSet> = Key.create("${COMMON_PROBLEMS_PANEL_ISSUE}_ATF")

/**
 * User data key for render related latch. It is used to control scheduling between lint and render.
 */
@JvmField
val ATF_ISSUES_LATCH: Key<CountDownLatch> = Key.create("${COMMON_PROBLEMS_PANEL_ISSUE}_ATF_LATCH")

/**
 * User data key for visual lint issues. It is used as a bus between external annotator and design surface.
 */
@JvmField
val VISUAL_LINT_ISSUES: Key<CommonPanelIssueSet> = Key.create("${COMMON_PROBLEMS_PANEL_ISSUE}_VISUAL_LINT")

/**
 * User data key for visual lint related latch. It is used to control scheduling between lint and render.
 */
@JvmField
val VISUAL_LINT_ISSUES_LATCH: Key<CountDownLatch> = Key.create("${COMMON_PROBLEMS_PANEL_ISSUE}_VISUAL_LINT_LATCH")
