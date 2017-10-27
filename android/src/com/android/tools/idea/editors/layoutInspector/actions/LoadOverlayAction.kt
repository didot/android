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
package com.android.tools.idea.editors.layoutInspector.actions

import com.android.tools.idea.editors.layoutInspector.ui.ViewNodeActiveDisplay
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileTypeDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import icons.AndroidIcons
import java.awt.Image
import java.io.IOException
import javax.imageio.ImageIO
import javax.swing.JComponent

/**
 * Lets the user choose an image to overlay on top of the captured view to compare the app's visual against design mocks.
 */
class LoadOverlayAction(private val myPreview: ViewNodeActiveDisplay) :
    AnAction(ACTION_ID, "Overlay Image", AndroidIcons.Views.FrameLayout), CustomComponentAction {
  companion object {
    @JvmField
    val ACTION_ID = "Load Overlay"
  }

  override fun createCustomComponent(presentation: Presentation?): JComponent {
    return ActionButtonWithText(this, presentation, "Toolbar", ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
  }

  override fun update(e: AnActionEvent?) {
    super.update(e)
    if (e == null) return
    if (myPreview.hasOverlay()) {
      e.presentation.text = myPreview.overlayFileName
    }
    else {
      e.presentation.text = ACTION_ID
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    // choose image
    val descriptor = FileTypeDescriptor("Choose Overlay", "svg", "png", "jpg")
    val fileChooserDialog = FileChooserFactory.getInstance().createFileChooser(descriptor, null, null)
    val toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(e.project?.basePath ?: "/")
    val files = fileChooserDialog.choose(null, toSelect!!)
    if (files.isEmpty()) {
      return
    }
    assert(files.size == 1)

    myPreview.setOverLay(getImage(files[0]), files[0].name)
  }

  private fun getImage(file: VirtualFile): Image? {
    return try {
      ImageIO.read(file.inputStream)
    }
    catch (e: IOException) {
      return null
    }

  }
}
