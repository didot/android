/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.explorer

import com.android.tools.idea.ui.resourcemanager.model.DesignAssetSet
import com.android.tools.idea.ui.resourcemanager.model.createTransferable
import java.awt.Cursor
import java.awt.GraphicsEnvironment
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import javax.swing.DropMode
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.TransferHandler

/**
 * Create a new [ResourceDragHandler]
 */
fun resourceDragHandler() = if (GraphicsEnvironment.isHeadless()) {
  HeadlessDragHandler()
}
else {
  ResourceDragHandlerImpl()
}


interface ResourceDragHandler {
  fun registerSource(assetList: JList<DesignAssetSet>)
}

/**
 * DragHandler in headless mode
 */
class HeadlessDragHandler internal constructor() : ResourceDragHandler {
  override fun registerSource(assetList: JList<DesignAssetSet>) {
    // Do Nothing
  }
}

/**
 * Drag handler for the resources list in the resource explorer.
 */
private class ResourceDragHandlerImpl internal constructor() : ResourceDragHandler {

  override fun registerSource(assetList: JList<DesignAssetSet>) {
    assetList.dragEnabled = true
    assetList.dropMode = DropMode.ON
    assetList.transferHandler = object : TransferHandler() {

      // Import is handled by the ResourceImportDragTarget
      override fun canImport(support: TransferSupport?) = false

      override fun importData(comp: JComponent?, t: Transferable?) = false

      override fun getSourceActions(c: JComponent?) = TransferHandler.LINK

      override fun getDragImage() = createDragPreview(assetList, assetList.selectedValue, assetList.selectedIndex)

      override fun createTransferable(c: JComponent?): Transferable {
        c?.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        return createTransferable(assetList.selectedValue.getHighestDensityAsset())
      }

      override fun exportDone(source: JComponent?, data: Transferable?, action: Int) {
        source?.cursor = Cursor.getDefaultCursor()
      }
    }
  }
}

private fun createDragPreview(jList: JList<DesignAssetSet>,
                              assetSet: DesignAssetSet?,
                              index: Int): BufferedImage {
  val component = jList.cellRenderer.getListCellRendererComponent(jList, assetSet, index, false, false)
  // The component having no parent to lay it out an set its size, we need to manually to it, otherwise
  // validate() won't be executed.
  component.setSize(component.preferredSize.width, component.preferredSize.height)
  component.validate()
  val image = BufferedImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
  with(image.createGraphics()) {
    color = jList.background
    fillRect(0, 0, component.width, component.height)
    component.paint(this)
    dispose()
  }
  return image
}

