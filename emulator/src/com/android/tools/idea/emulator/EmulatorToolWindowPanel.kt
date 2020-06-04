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
package com.android.tools.idea.emulator

import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.editor.ActionToolbarUtil.makeToolbarNavigable
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollBar
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.StudioIcons
import org.intellij.lang.annotations.JdkConstants.AdjustableOrientation
import java.awt.Adjustable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Insets
import java.awt.LayoutManager
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.plaf.ScrollBarUI

/**
 * Represents contents of the Emulator tool window for a single Emulator instance.
 */
class EmulatorToolWindowPanel(private val emulator: EmulatorController) : BorderLayoutPanel(), DataProvider {
  private val mainToolbar: ActionToolbar
  private val secondaryToolbar: ActionToolbar
  private var emulatorView: EmulatorView? = null
  private val scrollPane: JScrollPane
  private val layeredPane: JLayeredPane
  private val zoomControlsLayerPane: JPanel
  private var loadingPanel: JBLoadingPanel? = null
  private var contentDisposable: Disposable? = null
  private var floatingToolbar: JComponent? = null

  val id
    get() = emulator.emulatorId

  val title
    get() = emulator.emulatorId.avdName

  val icon
    get() = ICON

  val component: JComponent
    get() = this

  var zoomToolbarIsVisible = false
    set(value) {
      field = value
      floatingToolbar?.let { it.isVisible = value }
    }

  init {
    background = primaryPanelBackground

    mainToolbar = createToolbar(EMULATOR_MAIN_TOOLBAR_ID, isToolbarHorizontal)

    secondaryToolbar = createToolbar(EMULATOR_SECONDARY_TOOLBAR_ID, isToolbarHorizontal).apply {
      setReservePlaceAutoPopupIcon(false)
      layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    }

    zoomControlsLayerPane = JPanel().apply {
      layout = BorderLayout()
      border = JBUI.Borders.empty(UIUtil.getScrollBarWidth())
      isOpaque = false
      isFocusable = true
    }

    scrollPane = MyScrollPane().apply {
      border = null
      verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
      viewport.background = background
    }

    layeredPane = JLayeredPane().apply {
      layout = LayeredPaneLayoutManager()
      isFocusable = true
      setLayer(zoomControlsLayerPane, JLayeredPane.PALETTE_LAYER)
      setLayer(scrollPane, JLayeredPane.DEFAULT_LAYER)

      add(zoomControlsLayerPane, BorderLayout.CENTER)
      add(scrollPane, BorderLayout.CENTER)
    }
  }

  fun getPreferredFocusableComponent(): JComponent {
    return emulatorView ?: this
  }

  private fun addToolbars() {
    if (isToolbarHorizontal) {
      mainToolbar.setOrientation(SwingConstants.HORIZONTAL)
      secondaryToolbar.setOrientation(SwingConstants.HORIZONTAL)
      layeredPane.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.TOP)
      val toolbarPanel = BorderLayoutPanel()
      toolbarPanel.addToLeft(mainToolbar.component)
      toolbarPanel.addToRight(secondaryToolbar.component)
      addToTop(toolbarPanel)
    }
    else {
      mainToolbar.setOrientation(SwingConstants.VERTICAL)
      secondaryToolbar.setOrientation(SwingConstants.VERTICAL)
      layeredPane.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.LEFT)
      val toolbarPanel = BorderLayoutPanel()
      (toolbarPanel.layout as BorderLayout).vgap = 1
      toolbarPanel.addToTop(secondaryToolbar.component)
      secondaryToolbar.component.border = IdeBorderFactory.createBorder(JBColor.PanelBackground, SideBorder.BOTTOM)
      mainToolbar.component.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.TOP)
      toolbarPanel.addToCenter(mainToolbar.component)
      addToLeft(toolbarPanel)
    }
  }

  fun setCropFrame(value: Boolean) {
    emulatorView?.cropFrame = value
  }

  fun createContent(cropSkin: Boolean) {
    try {
      val disposable = Disposer.newDisposable()
      contentDisposable = disposable

      val toolbar = EmulatorZoomToolbar.createToolbar(this, disposable)
      toolbar.isVisible = zoomToolbarIsVisible
      floatingToolbar = toolbar
      zoomControlsLayerPane.add(toolbar, BorderLayout.EAST)

      val emulatorView = EmulatorView(emulator, disposable, cropSkin)
      emulatorView.background = background
      this.emulatorView = emulatorView
      scrollPane.setViewportView(emulatorView)
      mainToolbar.setTargetComponent(emulatorView)
      secondaryToolbar.setTargetComponent(emulatorView)

      addToolbars()

      val loadingPanel = EmulatorLoadingPanel(disposable)
      this.loadingPanel = loadingPanel
      loadingPanel.add(layeredPane, BorderLayout.CENTER)
      addToCenter(loadingPanel)

      loadingPanel.setLoadingText("Connecting to the Emulator")
      loadingPanel.startLoading() // stopLoading is called by EmulatorView after the gRPC connection is established.

      loadingPanel.repaint()
    }
    catch (e: Exception) {
      val label = "Unable to load emulator view: $e"
      add(JLabel(label), BorderLayout.CENTER)
    }
  }

  fun destroyContent() {
    contentDisposable?.let { Disposer.dispose(it) }
    contentDisposable = null

    zoomControlsLayerPane.removeAll()
    floatingToolbar = null

    emulatorView = null
    mainToolbar.setTargetComponent(null)
    secondaryToolbar.setTargetComponent(null)
    scrollPane.setViewportView(null)

    loadingPanel = null
    removeAll()
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      EMULATOR_CONTROLLER_KEY.name -> emulator
      EMULATOR_VIEW_KEY.name, ZOOMABLE_KEY.name -> emulatorView
      else -> null
    }
  }

  private fun createToolbar(toolbarId: String, @Suppress("SameParameterValue") horizontal: Boolean): ActionToolbar {
    val actions = listOf(CustomActionsSchema.getInstance().getCorrectedAction(toolbarId)!!)
    val toolbar = ActionManager.getInstance().createActionToolbar(toolbarId, DefaultActionGroup(actions), horizontal)
    makeToolbarNavigable(toolbar)
    return toolbar
  }

  private class LayeredPaneLayoutManager : LayoutManager {

    override fun layoutContainer(target: Container) {
      val insets: Insets = target.insets
      val top = insets.top
      val bottom = target.height - insets.bottom
      val left = insets.left
      val right = target.width - insets.right

      for (child in target.components) {
        child.setBounds(left, top, right - left, bottom - top)
      }
    }

    // Request all available space.
    override fun preferredLayoutSize(parent: Container): Dimension =
      if (parent.isPreferredSizeSet) parent.preferredSize else Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
    override fun minimumLayoutSize(parent: Container): Dimension = Dimension(0, 0)
    override fun addLayoutComponent(name: String?, comp: Component?) {}
    override fun removeLayoutComponent(comp: Component?) {}
  }

  private class MyScrollPane : JBScrollPane(0) {

    override fun createVerticalScrollBar(): JScrollBar {
      return MyScrollBar(Adjustable.VERTICAL)
    }

    override fun createHorizontalScrollBar(): JScrollBar {
      return MyScrollBar(Adjustable.HORIZONTAL)
    }

    init {
      setupCorners()
    }
  }

  private class MyScrollBar(
    @AdjustableOrientation orientation: Int
  ) : JBScrollBar(orientation), IdeGlassPane.TopComponent {

    private var persistentUI: ScrollBarUI? = null

    override fun canBePreprocessed(event: MouseEvent): Boolean {
      return JBScrollPane.canBePreprocessed(event, this)
    }

    override fun setUI(ui: ScrollBarUI) {
      if (persistentUI == null) {
        persistentUI = ui
      }
      super.setUI(persistentUI)
      isOpaque = false
    }

    override fun getUnitIncrement(direction: Int): Int {
      return 5
    }

    override fun getBlockIncrement(direction: Int): Int {
      return 1
    }

    init {
      isOpaque = false
    }
  }
}

private val ICON = ExecutionUtil.getLiveIndicator(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE)
private const val EMULATOR_SECONDARY_TOOLBAR_ID = "EmulatorSecondaryToolbar"
private const val isToolbarHorizontal = true
