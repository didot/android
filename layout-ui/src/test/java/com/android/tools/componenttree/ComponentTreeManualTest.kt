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
package com.android.tools.componenttree

import com.android.SdkConstants.BUTTON
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.componenttree.api.BadgeItem
import com.android.tools.componenttree.api.ComponentTreeBuilder
import com.android.tools.componenttree.api.ComponentTreeModel
import com.android.tools.componenttree.api.ComponentTreeSelectionModel
import com.android.tools.componenttree.util.Item
import com.android.tools.componenttree.util.ItemNodeType
import com.intellij.ide.DataManager
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.mock.MockApplication
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.WindowManagerImpl
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.LookAndFeel
import javax.swing.SwingUtilities
import javax.swing.SwingUtilities.invokeLater
import javax.swing.UIManager

/**
 * Interactive tester of a ComponentTree.
 */
object ComponentTreeManualTest {

  @Suppress("UnusedMainParameter")
  @JvmStatic
  fun main(args: Array<String>) {
    startTestApplication()
    IconLoader.activate()
    invokeLater {
      val test = ComponentTreeTest()
      test.start()
    }
  }

  private fun startTestApplication(): MockApplication {
    val disposable = Disposer.newDisposable()
    val app = TestApplication(disposable)
    ApplicationManager.setApplication(app, disposable)
    app.registerService(DataManager::class.java, DataManagerImpl())
    app.registerService(WindowManager::class.java, WindowManagerImpl())
    return app
  }
}

private class ComponentTreeTest {
  private val frame: JFrame
  private val tree: JComponent
  private val model: ComponentTreeModel
  private val selectionModel: ComponentTreeSelectionModel
  private val popup: JPopupMenu

  init {
    setLAF(IntelliJLaf())
    frame = JFrame("Demo")
    popup = createPopup("tree")

    val badge1 = Badge("badge1")
    val badge2 = Badge("badge2")

    val result = ComponentTreeBuilder()
      .withNodeType(ItemNodeType())
      .withMultipleSelection()
      .withContextMenu(::showPopup)
      .withoutTreeSearch()
      .withBadgeSupport(badge1)
      .withBadgeSupport(badge2)
      .withHorizontalScrollBar()
      .build()
    tree = result.first
    model = result.second
    selectionModel = result.third

    val rightPanel = JPanel()
    val splitter = Splitter(false, 0.8f)
    splitter.firstComponent = tree
    splitter.secondComponent = rightPanel
    rightPanel.background = secondaryPanelBackground
    rightPanel.add(JButton("OK"))
    val panel = JPanel(BorderLayout())
    panel.add(JLabel("Start of panel"), BorderLayout.NORTH)
    panel.add(splitter, BorderLayout.CENTER)
    panel.add(JLabel("End of panel"), BorderLayout.SOUTH)
    frame.contentPane.add(panel)
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.preferredSize = Dimension(800, 400)
    frame.pack()
    model.treeRoot = createRoot()
  }

  fun start() {
    frame.isVisible = true
  }

  private fun getSelectedItem() = selectionModel.currentSelection.singleOrNull() as? Item

  private fun showPopup(component: JComponent, x: Int, y: Int) {
    displayPopup(popup, component, x, y)
  }

  private fun displayPopup(popup: JPopupMenu, component: JComponent, x: Int, y: Int) {
    popup.show(component, x, y)
  }

  private fun createPopup(context: String): JPopupMenu {
    val popup = JPopupMenu()
    popup.add(createGotoDeclarationMenu(context))
    popup.add(createHelpMenu(context))
    return popup
  }

  private fun createGotoDeclarationMenu(context: String): JMenuItem {
    val menuItem = JMenuItem("Goto Declaration")
    menuItem.addActionListener {
      val item = getSelectedItem() ?: "unknown"
      JOptionPane.showMessageDialog(frame, "Goto Declaration activated: $item from $context", "Tree Action",
                                    JOptionPane.INFORMATION_MESSAGE)
    }
    return menuItem
  }

  private fun createHelpMenu(context: String): JMenuItem {
    val menuItem = JMenuItem("Help")
    menuItem.addActionListener {
      val item = getSelectedItem() ?: "unknown"
      JOptionPane.showMessageDialog(frame, "Help activated: $item from $context", "Tree Action", JOptionPane.INFORMATION_MESSAGE)
    }
    return menuItem
  }

  @Suppress("UNUSED_VARIABLE")
  private fun createRoot(): Item {
    val layoutIcon = StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_VERT
    val buttonIcon = StudioIcons.LayoutEditor.Palette.BUTTON
    val textIcon = StudioIcons.LayoutEditor.Palette.TEXT_VIEW
    val layout1 = Item(LINEAR_LAYOUT, "@+id/linear1", null, layoutIcon, null)
    val textView1 = Item(TEXT_VIEW, "@+id/textView1", "Hello World", textIcon, layout1)
    val layout2 = Item(LINEAR_LAYOUT, "@+id/linear2", null, layoutIcon, layout1)
    val button1 = Item(BUTTON, null, "Press here", buttonIcon, layout1)
    val textView2 = Item(TEXT_VIEW, "@+id/textView2", "Hello Again", textIcon, layout2)
    val layout3 = Item(LINEAR_LAYOUT, null, null, layoutIcon, layout2)
    val button2 = Item(BUTTON, "@+id/button1", "OK", buttonIcon, layout2)
    val textView3 = Item(TEXT_VIEW, "@+id/textView3", "Hello London calling we are here", textIcon, layout3)
    val layout4 = Item(LINEAR_LAYOUT, null, null, layoutIcon, layout3)
    val button3 = Item(BUTTON, "@+id/button1", "PressMe", buttonIcon, layout3)
    textView1.badge1 = StudioIcons.Common.ERROR_INLINE
    textView1.badge2 = StudioIcons.Common.CLOSE
    textView2.badge2 = StudioIcons.Common.DELETE
    button1.badge1 = StudioIcons.Common.WARNING_INLINE
    return layout1
  }

  private fun setLAF(laf: LookAndFeel) {
    try {
      UIManager.setLookAndFeel(laf)
      JBColor.setDark(UIUtil.isUnderDarcula())
    }
    catch (ex: Exception) {
      ex.printStackTrace()
    }
  }

  private inner class Badge(val context: String): BadgeItem {
    val popup = createPopup(context)

    override fun getIcon(item: Any): Icon? {
      val itemValue = item as? Item
      return if (context == "badge1") itemValue?.badge1 else itemValue?.badge2
    }

    override fun getTooltipText(item: Any?) = "Tooltip for $item"

    override fun performAction(item: Any) {
      if (getIcon(item) != null) {
        JOptionPane.showMessageDialog(frame, "Badge: $context for $item", "Tree Action", JOptionPane.INFORMATION_MESSAGE)
      }
    }

    override fun showPopup(item: Any, component: JComponent, x: Int, y: Int) {
      displayPopup(popup, component, x, y) // item will always be the selected item as well...
    }

    override fun toString(): String {
      return context
    }
  }
}

private class TestApplication(disposable: Disposable): MockApplication(disposable) {
  override fun invokeLater(runnable: Runnable) {
    SwingUtilities.invokeLater(runnable)
  }

  override fun invokeLater(runnable: Runnable, modalityState: ModalityState) {
    invokeLater(runnable)
  }

  override fun invokeLater(runnable: Runnable, expired: Condition<*>) {
    invokeLater(runnable, ModalityState.any(), expired)
  }

  override fun invokeLater(runnable: Runnable, state: ModalityState, expired: Condition<*>) {
    if (!expired.value(null)) {
      invokeLater(runnable)
    }
  }
}
