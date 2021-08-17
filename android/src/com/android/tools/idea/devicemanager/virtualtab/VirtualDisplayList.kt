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
package com.android.tools.idea.devicemanager.virtualtab

import com.android.resources.Density
import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.adtui.common.ColoredIconGenerator.generateWhiteIcon
import com.android.tools.idea.avdmanager.AccelerationErrorCode
import com.android.tools.idea.avdmanager.AccelerationErrorNotificationPanel
import com.android.tools.idea.avdmanager.ApiLevelComparator
import com.android.tools.idea.avdmanager.AvdActionPanel
import com.android.tools.idea.avdmanager.AvdActionPanel.AvdRefreshProvider
import com.android.tools.idea.avdmanager.AvdDisplayList
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.avdmanager.AvdUiAction.AvdInfoProvider
import com.android.tools.idea.avdmanager.DeleteAvdAction
import com.android.tools.idea.avdmanager.DeviceManagerConnection
import com.android.tools.idea.avdmanager.EditAvdAction
import com.android.tools.idea.avdmanager.RunAvdAction
import com.android.tools.idea.devicemanager.virtualtab.columns.AvdActionsColumnInfo
import com.android.tools.idea.devicemanager.virtualtab.columns.AvdDeviceColumnInfo
import com.android.tools.idea.devicemanager.virtualtab.columns.SizeOnDiskColumn
import com.android.tools.idea.devicemanager.virtualtab.columns.VirtualDeviceTableCellRenderer
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Sets
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.containers.toArray
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import icons.StudioIcons
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.AbstractAction
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.table.TableCellRenderer

/**
 * A UI component which lists the existing AVDs
 */
class VirtualDisplayList @TestOnly constructor(private val project: Project?,
                         private val virtualDeviceModel: VirtualDeviceModel,
                         modelListenerLatch: CountDownLatch?,
                         private val deviceTableCellRenderer: TableCellRenderer)
  : JPanel(), ListSelectionListener, AvdRefreshProvider, AvdInfoProvider {

  constructor(project: Project?) : this(project,
                                        VirtualDeviceModel(),
                                        null,
                                        VirtualDeviceTableCellRenderer())

  private val notificationPanel = JPanel().apply {
    layout = BoxLayout(this, 1)
  }
  private val tableModel = ListTableModel<AvdInfo>().apply {
    isSortable = true
  }

  val table: VirtualTableView
  private val listeners: MutableSet<AvdSelectionListener> = Sets.newHashSet()
  private val logger: Logger get() = logger<VirtualDisplayList>()

  private var latestSearchString: String = ""

  init {
    layout = BorderLayout()
    table = VirtualTableView(tableModel, this)
    virtualDeviceModel.addListener(ModelListener(modelListenerLatch))
    add(notificationPanel, BorderLayout.NORTH)
    add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER)

    table.apply {
      selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
      selectionModel.addListSelectionListener(this)
      addMouseListener(editingListener)
      addMouseMotionListener(editingListener)
      addMouseListener(LaunchListener())
      getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).apply {
        put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter")
        put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "enter")
        put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteAvd")
        put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "deleteAvd")
      }
    }
    table.actionMap.apply {
      // put("selectPreviousColumnCell", CycleAction(true))
      // put("selectNextColumnCell", CycleAction(false))
      put("deleteAvd", DeleteAvdAction(this@VirtualDisplayList))
      put("enter", object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
          doAction()
        }
      })
    }
    refreshAvds()

    tableModel.columnInfos = newColumns().toArray(ColumnInfo.EMPTY_ARRAY)
  }

  /**
   * Components which wish to receive a notification when the user has selected an AVD from this
   * table must implement this interface and register themselves through [.addSelectionListener]
   */
  interface AvdSelectionListener {
    fun onAvdSelected(avdInfo: AvdInfo?)
  }

  fun addSelectionListener(listener: AvdSelectionListener) {
    listeners.add(listener)
  }

  fun removeSelectionListener(listener: AvdSelectionListener) {
    listeners.remove(listener)
  }

  /**
   * This class implements the table selection interface and passes the selection events on to its listeners.
   */
  override fun valueChanged(e: ListSelectionEvent) {
    // Required so the editor component is updated to know it's selected.
    table.editCellAt(table.selectedRow, table.selectedColumn)
    for (listener in listeners) {
      listener.onAvdSelected(table.selectedObject)
    }
  }

  override fun getAvdInfo(): AvdInfo? = table.selectedObject

  private val avdProviderComponent: JComponent = this
  override fun getAvdProviderComponent(): JComponent {
    return avdProviderComponent
  }

  fun updateSearchResults(searchString: String?) {
    if (searchString != null) {
      latestSearchString = searchString
    }
    tableModel.items = tableModel.items.filter {
      it.displayName.contains(latestSearchString, ignoreCase = true)
    }
  }

  /**
   * Reload AVD definitions from disk and repopulate the table
   */
  override fun refreshAvds() {
    virtualDeviceModel.refreshAvds()
  }

  /**
   * Reload AVD definitions from disk, repopulate the table, and select the indicated AVD
   */
  override fun refreshAvdsAndSelect(avdToSelect: AvdInfo?) {
    refreshAvds()

    avdToSelect ?: return
    val avdInList = table.items.firstOrNull { it.name == avdToSelect.name } ?: return
    table.selection = listOf(avdInList)
  }

  override fun getProject(): Project? {
    return project
  }

  override fun getComponent(): JComponent = this

  @VisibleForTesting
  fun getTableItems(): List<AvdInfo> {
    return tableModel.items
  }

  private val editingListener: MouseAdapter = object : MouseAdapter() {
    override fun mouseMoved(e: MouseEvent) {
      possiblySwitchEditors(e)
    }

    override fun mouseEntered(e: MouseEvent) {
      possiblySwitchEditors(e)
    }

    override fun mouseExited(e: MouseEvent) {
      possiblySwitchEditors(e)
    }

    override fun mouseClicked(e: MouseEvent) {
      possiblySwitchEditors(e)
    }

    override fun mousePressed(e: MouseEvent) {
      possiblyShowPopup(e)
    }

    override fun mouseReleased(e: MouseEvent) {
      possiblyShowPopup(e)
    }
  }

  private fun possiblySwitchEditors(e: MouseEvent) {
    val p = e.point
    val row = table.rowAtPoint(p)
    val col = table.columnAtPoint(p)
    if (row != table.editingRow || col != table.editingColumn) {
      if ((row != -1) && (col != -1) && table.isCellEditable(row, col)) {
        table.editCellAt(row, col)
      }
    }
  }

  private fun possiblyShowPopup(e: MouseEvent) {
    if (!e.isPopupTrigger) {
      return
    }
    val p = e.point
    val row = table.rowAtPoint(p)
    val col = table.columnAtPoint(p)
    if (row != -1 && col != -1) {
      val lastColumn = table.columnCount - 1
      val maybeActionPanel = table.getCellRenderer(row, lastColumn).getTableCellRendererComponent(
        table, table.getValueAt(row, lastColumn), false, true, row, lastColumn
      )
      if (maybeActionPanel is AvdActionPanel) {
        maybeActionPanel.showPopup(table, e)
      }
    }
  }


  @VisibleForTesting
  class HighlightableIconPair(val baseIcon: Icon?) {
    var highlightedIcon: Icon? = if (baseIcon != null) generateWhiteIcon(baseIcon) else null
  }

  enum class DeviceType {
    VIRTUAL,
    REAL,

    // TODO(qumeric): should probably be a sealed class and provide data to facilitate download
    PRECONFIGURED
  }


  // needs an initialized table
  fun newColumns(): Collection<ColumnInfo<AvdInfo, *>> {
    return listOf(
      AvdDeviceColumnInfo("Device", deviceTableCellRenderer),
      object : AvdDisplayList.AvdColumnInfo("API") {
        override fun valueOf(avdInfo: AvdInfo): String = avdInfo.androidVersion.apiString

        /**
         * We override the comparator here to sort the API levels numerically (when possible;
         * with preview platforms codenames are compared alphabetically)
         */
        override fun getComparator(): Comparator<AvdInfo> = with(ApiLevelComparator()) {
          Comparator { o1, o2 -> compare(valueOf(o1), valueOf(o2)) }
        }
      },
      SizeOnDiskColumn(table),
      AvdActionsColumnInfo("Actions", project != null, this)
    )
  }

  private fun refreshErrorCheck() {
    val refreshUI = AtomicBoolean(notificationPanel.componentCount > 0)
    notificationPanel.removeAll()
    val error = AvdManagerConnection.getDefaultAvdManagerConnection().checkAccelerationAsync()
    Futures.addCallback(
      error,
      object : FutureCallback<AccelerationErrorCode> {
        override fun onSuccess(result: AccelerationErrorCode?) {
          requireNotNull(result)
          if (result != AccelerationErrorCode.ALREADY_INSTALLED) {
            refreshUI.set(true)
            notificationPanel.add(
              AccelerationErrorNotificationPanel(result, project) { refreshErrorCheck() })
          }
          if (refreshUI.get()) {
            notificationPanel.revalidate()
            notificationPanel.repaint()
          }
        }

        override fun onFailure(t: Throwable) {
          logger.warn("Check for emulation acceleration failed", t)
        }
      }, EdtExecutorService.getInstance())
  }

  private inner class LaunchListener : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent) {
      if (e.clickCount == 2) {
        doAction()
      }
    }
  }

  private fun doAction() {
    val info = avdInfo ?: return

    if (info.status == AvdInfo.AvdStatus.OK) {
      RunAvdAction(this).actionPerformed(null)
    }
    else {
      EditAvdAction(this).actionPerformed(null)
    }
  }

  /**
   * Renders a cell with borders.
   */
  class NoBorderCellRenderer(var defaultRenderer: TableCellRenderer) : TableCellRenderer {
    override fun getTableCellRendererComponent(
      table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component = (defaultRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JComponent).apply {
      border = JBUI.Borders.empty(10)
    }
  }

  inner class ModelListener(private val latch: CountDownLatch?) : VirtualDeviceModel.VirtualDeviceModelListener {

    override fun avdListChanged(avds: MutableList<AvdInfo>) {
      tableModel.items = avds
      updateSearchResults(null)
      table.setWidths()

      refreshErrorCheck()
      latch?.countDown()
    }
  }

  companion object {
    private const val MOBILE_TAG_STRING = "mobile-device"
    private val deviceClassIcons = hashMapOf<String, HighlightableIconPair>()
    val deviceManager: DeviceManagerConnection get() = DeviceManagerConnection.getDefaultDeviceManagerConnection()

    /**
     * @return the device screen size of this AVD
     */
    @VisibleForTesting
    fun getScreenSize(info: AvdInfo): Dimension? {
      val device = deviceManager.getDevice(info.deviceName, info.deviceManufacturer) ?: return null
      return device.getScreenSize(device.defaultState.orientation)
    }

    /**
     * @return the resolution of a given AVD as a string of the format widthxheight - density
     * (e.g. 1200x1920 - xhdpi) or "Unknown Resolution" if the AVD does not define a resolution.
     */
    @VisibleForTesting
    fun getResolution(info: AvdInfo): String {
      val device = deviceManager.getDevice(info.deviceName, info.deviceManufacturer)
      val res: Dimension? = device?.getScreenSize(device.defaultState.orientation)
      val density: Density? = device?.defaultHardware?.screen?.pixelDensity
      val densityString = density?.resourceValue ?: "Unknown Density"

      return if (res != null) "${res.width} \u00D7 ${res.height}: $densityString" else "Unknown Resolution"
    }

    /**
     * Get the icons representing the device class of the given AVD (e.g. phone/tablet, Wear, TV)
     */
    fun getDeviceClassIconPair(info: AvdInfo): HighlightableIconPair {
      val id = info.tag.id
      var thisClassPair: HighlightableIconPair?
      if (id.contains("android-")) {
        // TODO(qumeric): replace icons
        val path = "/studio/icons/avd/${id.replaceFirst("android", "device")}-large.svg"
        thisClassPair = deviceClassIcons[path]
        if (thisClassPair == null) {
          thisClassPair = HighlightableIconPair(IconLoader.getIcon(path, VirtualDisplayList::class.java))
          deviceClassIcons[path] = thisClassPair
        }
      }
      else {
        // Phone/tablet
        thisClassPair = deviceClassIcons[MOBILE_TAG_STRING]
        if (thisClassPair == null) {
          thisClassPair = HighlightableIconPair(
            StudioIcons.Avd.DEVICE_MOBILE_LARGE)
          deviceClassIcons[MOBILE_TAG_STRING] = thisClassPair
        }
      }
      return thisClassPair
    }
  }
}
