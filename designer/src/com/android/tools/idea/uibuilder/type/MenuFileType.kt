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
package com.android.tools.idea.uibuilder.type

import com.android.tools.idea.common.editor.SetZoomActionGroups
import com.android.tools.idea.common.surface.DesignSurface
import com.google.wireless.android.sdk.stats.LayoutEditorState
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.dom.menu.MenuDomFileDescription

object MenuFileType : LayoutEditorFileType("menu") {
  override fun getLayoutEditorStateType() = LayoutEditorState.Type.MENU

  override fun isResourceTypeOf(file: PsiFile) = file is XmlFile && MenuDomFileDescription.isMenuFile(file)

  override fun getToolbarActionGroups(surface: DesignSurface) = SetZoomActionGroups(surface)
}
