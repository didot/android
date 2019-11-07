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

import com.android.SdkConstants.FONT_PREFIX
import com.android.ide.common.resources.ResourceResolver
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.idea.fonts.MoreFontsDialog
import com.android.tools.idea.fonts.ProjectFonts
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.android.dom.AndroidDomUtil
import org.jetbrains.android.facet.AndroidFacet

/**
 * Standard font [EnumSupport].
 *
 * Generates data for a fontFamily popup with 2 sections: user defined (Project) and framework (Android) defined fonts.
 * The bottom item is an action for opening the downloadable font dialog.
 */
class FontEnumSupport(private val facet: AndroidFacet, private val resolver: ResourceResolver?) : EnumSupport {
  private val projectFonts = ProjectFonts(facet)

  override val values: List<EnumValue>
    get() {
      // TODO: Need to investigate support for fonts defined in libraries.
      val fonts = mutableListOf<EnumValue>()

      projectFonts.fonts.mapTo(fonts) { EnumValue.indented(value = FONT_PREFIX + it.name, display = it.name) }
      if (fonts.isNotEmpty()) {
        fonts[0] = fonts[0].withHeader(PROJECT_HEADER)
      }

      val index = fonts.size
      AndroidDomUtil.AVAILABLE_FAMILIES.mapTo(fonts) { EnumValue.indented(it) }
      if (fonts.size > index) {
        fonts[index] = fonts[index].withHeader(ANDROID_HEADER)
      }

      if (resolver != null) {
        val action = SelectFontAction(facet)
        fonts.add(EnumValue.action(action).withSeparator())
      }

      return fonts
    }

  private class SelectFontAction(private val facet: AndroidFacet) : AnAction("More Fonts...") {

    override fun actionPerformed(event: AnActionEvent) {
      val property = event.dataContext.getData("property") as NelePropertyItem
      // TODO: May need namespace resolver when fonts from libraries are supported
      val dialog = MoreFontsDialog(facet, property.resolvedValue)
      dialog.show()
      val font = if (dialog.isOK) dialog.resultingFont else null
      if (font != null) {
        property.value = font
      }
    }
  }
}
