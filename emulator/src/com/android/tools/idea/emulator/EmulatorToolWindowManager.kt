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
package com.android.tools.idea.emulator

import com.android.adblib.AdbLibSession
import com.android.adblib.DeviceInfo
import com.android.adblib.DeviceList
import com.android.adblib.DevicePropertyNames.RO_BOOT_QEMU_AVD_NAME
import com.android.adblib.DevicePropertyNames.RO_KERNEL_QEMU_AVD_NAME
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_CPU_ABI
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_MANUFACTURER
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_MODEL
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState
import com.android.adblib.deviceProperties
import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.UiThread
import com.android.ddmlib.IDevice
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.avdmanager.AvdLaunchListener
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.device.DeviceToolWindowPanel
import com.android.tools.idea.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.emulator.EmulatorController.ConnectionStateListener
import com.android.tools.idea.emulator.RunningDevicePanel.UiState
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.DeviceHeadsUpListener
import com.google.common.cache.CacheBuilder
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.actions.ToggleToolbarAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.Alarm
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.awt.EventQueue
import java.text.Collator
import java.time.Duration

/**
 * Manages contents of the Emulator tool window. Listens to changes in [RunningEmulatorCatalog]
 * and maintains [EmulatorToolWindowPanel]s, one per running Emulator.
 */
internal class EmulatorToolWindowManager private constructor(
  private val project: Project
) : RunningEmulatorCatalog.Listener, DeviceMirroringSettingsListener, DumbAware {

  private var contentCreated = false
  private var physicalDeviceWatcher: PhysicalDeviceWatcher? = null
  private val panels = arrayListOf<RunningDevicePanel>()
  private var selectedPanel: RunningDevicePanel? = null
  /** When the tool window is hidden, the ID of the last selected Emulator, otherwise null. */
  private var lastSelectedDeviceId: DeviceId? = null
  /** When the tool window is hidden, the state of the UI for all emulators, otherwise empty. */
  private val savedUiState = hashMapOf<DeviceId, UiState>()
  private val emulators = hashSetOf<EmulatorController>()
  private val properties = PropertiesComponent.getInstance(project)
  // IDs of recently launched AVDs keyed by themselves.
  private val recentLaunches = CacheBuilder.newBuilder().expireAfterWrite(LAUNCH_INFO_EXPIRATION).build<String, String>()
  private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project.earlyDisposable)

  private val contentManagerListener = object : ContentManagerListener {
    @UiThread
    override fun selectionChanged(event: ContentManagerEvent) {
      viewSelectionChanged(getToolWindow())
    }

    @UiThread
    override fun contentRemoved(event: ContentManagerEvent) {
      val panel = event.content.component as? RunningDevicePanel ?: return
      if (panel is EmulatorToolWindowPanel) {
        panel.emulator.shutdown()
      }

      panels.remove(panel)
      savedUiState.remove(panel.id)
      if (panels.isEmpty()) {
        createPlaceholderPanel()
        hideLiveIndicator(getToolWindow())
      }
    }
  }

  private val connectionStateListener = object: ConnectionStateListener {
    @AnyThread
    override fun connectionStateChanged(emulator: EmulatorController, connectionState: ConnectionState) {
      if (connectionState == ConnectionState.DISCONNECTED) {
        EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
          if (contentCreated && emulators.remove(emulator)) {
            removeEmulatorPanel(emulator)
          }
        }
      }
    }
  }

  private var deviceFrameVisible
    get() = properties.getBoolean(DEVICE_FRAME_VISIBLE_PROPERTY, DEVICE_FRAME_VISIBLE_DEFAULT)
    set(value) {
      properties.setValue(DEVICE_FRAME_VISIBLE_PROPERTY, value, DEVICE_FRAME_VISIBLE_DEFAULT)
      for (panel in panels) {
        panel.setDeviceFrameVisible(value)
      }
    }

  private var zoomToolbarIsVisible
    get() = properties.getBoolean(ZOOM_TOOLBAR_VISIBLE_PROPERTY, ZOOM_TOOLBAR_VISIBLE_DEFAULT)
    set(value) {
      properties.setValue(ZOOM_TOOLBAR_VISIBLE_PROPERTY, value, ZOOM_TOOLBAR_VISIBLE_DEFAULT)
      for (panel in panels) {
        panel.zoomToolbarVisible = value
      }
    }

  init {
    Disposer.register(project.earlyDisposable) {
      ToolWindowManager.getInstance(project).getToolWindow(EMULATOR_TOOL_WINDOW_ID)?.let { destroyContent(it) }
    }

    // Lazily initialize content since we can only have one frame.
    val messageBusConnection = project.messageBus.connect(project.earlyDisposable)
    messageBusConnection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      @UiThread
      override fun stateChanged(toolWindowManager: ToolWindowManager) {
        val toolWindow = toolWindowManager.getToolWindow(EMULATOR_TOOL_WINDOW_ID) ?: return

        toolWindowManager.invokeLater {
          if (!project.isDisposed) {
            if (toolWindow.isVisible) {
              createContent(toolWindow)
            }
            else {
              destroyContent(toolWindow)
            }
          }
        }
      }
    })

    messageBusConnection.subscribe(AvdLaunchListener.TOPIC,
                                   AvdLaunchListener { avd, commandLine, project ->
                                     if (project == this.project && isEmbeddedEmulator(commandLine)) {
                                       RunningEmulatorCatalog.getInstance().updateNow()
                                       EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
                                         onEmulatorUsed(avd.name)
                                       }
                                     }
                                   })

    messageBusConnection.subscribe(DeviceHeadsUpListener.TOPIC,
                                   DeviceHeadsUpListener { device, project ->
                                     if (project == this.project && device.isEmulator) {
                                       onDeploymentToEmulator(device)
                                     }
                                   })


    messageBusConnection.subscribe(DeviceMirroringSettingsListener.TOPIC, this)
  }

  @AnyThread
  private fun onDeploymentToEmulator(device: IDevice) {
    val future = RunningEmulatorCatalog.getInstance().updateNow()
    future.addCallback(EdtExecutorService.getInstance(),
                       success = { emulators ->
                         if (emulators != null) {
                           onDeploymentToEmulator(device, emulators)
                         }},
                       failure = {})
  }

  private fun onDeploymentToEmulator(device: IDevice, runningEmulators: Set<EmulatorController>) {
    val serialPort = device.serialPort
    val emulator = runningEmulators.find { it.emulatorId.serialPort == serialPort } ?: return
    // Ignore standalone emulators.
    if (emulator.emulatorId.isEmbedded) {
      onEmulatorUsed(emulator.emulatorId.avdId)
    }
  }

  private fun onEmulatorUsed(avdId: String) {
    val toolWindow = getToolWindow()
    if (!toolWindow.isVisible) {
      toolWindow.show(null)
      if (!toolWindow.isActive) {
        toolWindow.activate(null)
      }
    }

    val panel = findPanelByAvdId(avdId)
    if (panel == null) {
      RunningEmulatorCatalog.getInstance().updateNow()
      recentLaunches.put(avdId, avdId)
      alarm.addRequest(recentLaunches::cleanUp, LAUNCH_INFO_EXPIRATION.toMillis())
    }
    else if (selectedPanel != panel) {
      val contentManager = toolWindow.contentManager
      val content = contentManager.getContent(panel)
      contentManager.setSelectedContent(content)
    }
  }

  private fun createContent(toolWindow: ToolWindow) {
    if (contentCreated) {
      return
    }
    contentCreated = true

    val actionGroup = DefaultActionGroup()
    actionGroup.addAction(ToggleZoomToolbarAction())
    actionGroup.addAction(ToggleDeviceFrameAction())
    (toolWindow as ToolWindowEx).setAdditionalGearActions(actionGroup)

    val emulatorCatalog = RunningEmulatorCatalog.getInstance()
    emulatorCatalog.updateNow()
    emulatorCatalog.addListener(this, EMULATOR_DISCOVERY_INTERVAL_MILLIS)
    // Ignore standalone emulators.
    emulators.addAll(emulatorCatalog.emulators.filter { it.emulatorId.isEmbedded })

    // Create the panel for the last selected Emulator before other panels so that it becomes selected
    // unless a recently launched Emulator takes over.
    val activeEmulator = (lastSelectedDeviceId as? DeviceId.EmulatorDeviceId)?.let { lastSelected ->
      emulators.find { it.emulatorId == lastSelected.emulatorId }
    }
    if (activeEmulator != null && !activeEmulator.isShuttingDown) {
      addEmulatorPanel(activeEmulator)
    }
    for (emulator in emulators) {
      if (emulator != activeEmulator && !emulator.isShuttingDown) {
        addEmulatorPanel(emulator)
      }
    }

    if (DeviceMirroringSettings.getInstance().deviceMirroringEnabled) {
      physicalDeviceWatcher = PhysicalDeviceWatcher()
    }

    // Not maintained when the tool window is visible.
    lastSelectedDeviceId = null

    val contentManager = toolWindow.contentManager
    if (contentManager.contentCount == 0) {
      createPlaceholderPanel()
    }

    contentManager.addContentManagerListener(contentManagerListener)
    viewSelectionChanged(toolWindow)
  }

  private fun destroyContent(toolWindow: ToolWindow) {
    if (!contentCreated) {
      return
    }
    contentCreated = false
    physicalDeviceWatcher?.let { Disposer.dispose(it) }
    physicalDeviceWatcher = null

    lastSelectedDeviceId = selectedPanel?.id

    RunningEmulatorCatalog.getInstance().removeListener(this)
    for (emulator in emulators) {
      emulator.removeConnectionStateListener(connectionStateListener)
    }
    emulators.clear()
    selectedPanel?.let {
      savedUiState[it.id] = it.destroyContent()
    }
    selectedPanel = null
    panels.clear()
    recentLaunches.invalidateAll()
    val contentManager = toolWindow.contentManager
    contentManager.removeContentManagerListener(contentManagerListener)
    contentManager.removeAllContents(true)
  }

  private fun addEmulatorPanel(emulator: EmulatorController) {
    emulator.addConnectionStateListener(connectionStateListener)
    addPanel(EmulatorToolWindowPanel(project, emulator))
  }

  private fun addPhysicalDevicePanel(serialNumber: String, abi: String, title: String) {
    addPanel(DeviceToolWindowPanel(project, serialNumber, abi, title))
  }

  private fun addPanel(panel: RunningDevicePanel) {
    val toolWindow = getToolWindow()
    val contentManager = toolWindow.contentManager
    var placeholderContent: Content? = null
    if (panels.isEmpty()) {
      showLiveIndicator(toolWindow)
      if (!contentManager.isEmpty) {
        // Remember the placeholder panel content to remove it later. Deleting it now would leave
        // the tool window empty and cause the contentRemoved method in ToolWindowContentUi to
        // hide it.
        placeholderContent = contentManager.getContent(0)
      }
    }

    val contentFactory = ContentFactory.SERVICE.getInstance()
    val content = contentFactory.createContent(panel, panel.title, false).apply {
      putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
      isCloseable = panel.isClosable
      tabName = panel.title
      icon = panel.icon
      popupIcon = panel.icon
      setPreferredFocusedComponent(panel::preferredFocusableComponent)
      putUserData(ID_KEY, panel.id)
    }

    panel.zoomToolbarVisible = zoomToolbarIsVisible

    val index = panels.binarySearch(panel, PANEL_COMPARATOR).inv()
    assert(index >= 0)

    if (index >= 0) {
      panels.add(index, panel)
      contentManager.addContent(content, index)

      if (selectedPanel != panel) {
        // Activate the newly added panel if it corresponds to a recently launched or used Emulator.
        val deviceId = panel.id
        if (deviceId is DeviceId.EmulatorDeviceId) {
          val avdId = deviceId.emulatorId.avdId
          if (recentLaunches.getIfPresent(avdId) != null) {
            recentLaunches.invalidate(avdId)
            contentManager.setSelectedContent(content)
          }
        }
      }

      placeholderContent?.let { contentManager.removeContent(it, true) } // Remove the placeholder panel if it was present.
    }
  }

  private fun removeEmulatorPanel(emulator: EmulatorController) {
    emulator.removeConnectionStateListener(connectionStateListener)

    val panel = findPanelByEmulatorId(emulator.emulatorId) ?: return
    removePanel(panel)
  }

  private fun removePhysicalDevicePanel(serialNumber: String) {
    val panel = findPanelBySerialNumber(serialNumber) ?: return
    removePanel(panel)
  }

  private fun removeAllPhysicalDevicePanels() {
    panels.filterIsInstance<DeviceToolWindowPanel>().forEach(::removePanel)
  }

  private fun removePanel(panel: RunningDevicePanel) {
    val toolWindow = getToolWindow()
    val contentManager = toolWindow.contentManager
    val content = contentManager.getContent(panel)
    contentManager.removeContent(content, true)
  }

  private fun createPlaceholderPanel() {
    val panel = PlaceholderPanel(project)
    val contentFactory = ContentFactory.SERVICE.getInstance()
    val content = contentFactory.createContent(panel, panel.title, false).apply {
      tabName = panel.title
      isCloseable = false
    }
    val contentManager = getContentManager()
    contentManager.addContent(content)
    contentManager.setSelectedContent(content)
  }

  private fun viewSelectionChanged(toolWindow: ToolWindow) {
    val contentManager = toolWindow.contentManager
    val content = contentManager.selectedContent
    val id = content?.getUserData(ID_KEY)
    if (id != selectedPanel?.id) {
      selectedPanel?.let { panel ->
        savedUiState[panel.id] = panel.destroyContent()
        selectedPanel = null
      }

      if (id != null) {
        selectedPanel = findPanelByDeviceId(id)
        selectedPanel?.createContent(deviceFrameVisible, savedUiState.remove(id))
        ToggleToolbarAction.setToolbarVisible(toolWindow, PropertiesComponent.getInstance(project), null)
      }
    }
  }

  @AnyThread
  private suspend fun physicalDeviceConnected(deviceSerialNumber: String, adbSession: AdbLibSession) {
    try {
      val properties = adbSession.deviceServices.deviceProperties(DeviceSelector.fromSerialNumber(deviceSerialNumber)).allReadonly()
      var title = (properties[RO_BOOT_QEMU_AVD_NAME] ?: properties[RO_KERNEL_QEMU_AVD_NAME])?.replace('_', ' ')
      if (title == null) {
        title = properties[RO_PRODUCT_MODEL] ?: deviceSerialNumber
        val manufacturer = properties[RO_PRODUCT_MANUFACTURER]
        if (!manufacturer.isNullOrBlank() && manufacturer != "unknown") {
          title = "$manufacturer $title"
        }
      }
      val deviceAbi = properties[RO_PRODUCT_CPU_ABI]
      if (deviceAbi == null) {
        thisLogger().warn("Unable to determine ABI of $title")
        return
      }

      UIUtil.invokeLaterIfNeeded { // This is safe because this code doesn't touch PSI or VFS.
        addPhysicalDevicePanel(deviceSerialNumber, deviceAbi, title)
      }
    }
    catch (e: Exception) {
      thisLogger().warn(e)
    }
  }

  @AnyThread
  private fun physicalDeviceDisconnected(deviceSerialNumber: String) {
    UIUtil.invokeLaterIfNeeded { // This is safe because this code doesn't touch PSI or VFS.
      removePhysicalDevicePanel(deviceSerialNumber)
    }
  }

  private fun findPanelByDeviceId(deviceId: DeviceId): RunningDevicePanel? {
    return panels.firstOrNull { it.id == deviceId }
  }

  private fun findPanelByEmulatorId(emulatorId: EmulatorId): RunningDevicePanel? {
    return panels.firstOrNull { it.id is DeviceId.EmulatorDeviceId && it.id.emulatorId == emulatorId }
  }

  private fun findPanelByAvdId(avdId: String): RunningDevicePanel? {
    return panels.firstOrNull { it.id is DeviceId.EmulatorDeviceId && it.id.emulatorId.avdId == avdId }
  }

  private fun findPanelBySerialNumber(serialNumber: String): RunningDevicePanel? {
    return panels.firstOrNull { it.id.serialNumber == serialNumber }
  }

  private fun getContentManager(): ContentManager {
    return getToolWindow().contentManager
  }

  private fun getToolWindow(): ToolWindow {
    return ToolWindowManager.getInstance(project).getToolWindow(EMULATOR_TOOL_WINDOW_ID) ?:
           throw IllegalStateException("Could not find the $EMULATOR_TOOL_WINDOW_TITLE tool window")
  }

  private fun showLiveIndicator(toolWindow: ToolWindow) {
    toolWindow.setIcon(ExecutionUtil.getLiveIndicator(StudioIcons.Shell.ToolWindows.EMULATOR))
  }

  private fun hideLiveIndicator(toolWindow: ToolWindow) {
    toolWindow.setIcon(StudioIcons.Shell.ToolWindows.EMULATOR)
  }

  @AnyThread
  override fun emulatorAdded(emulator: EmulatorController) {
    if (emulator.emulatorId.isEmbedded) {
      EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
        if (contentCreated && emulators.add(emulator)) {
          addEmulatorPanel(emulator)
        }
      }
    }
    else if (StudioFlags.DEVICE_MIRRORING_STANDALONE_EMULATORS.get()) {
      val deviceWatcher = physicalDeviceWatcher ?: return
      deviceWatcher.startMirroringIfOnline(emulator.emulatorId)
    }
  }

  @AnyThread
  override fun emulatorRemoved(emulator: EmulatorController) {
    if (emulator.emulatorId.isEmbedded) {
      EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
        if (contentCreated && emulators.remove(emulator)) {
          removeEmulatorPanel(emulator)
        }
      }
    }
  }

  override fun settingsChanged(settings: DeviceMirroringSettings) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(EMULATOR_TOOL_WINDOW_ID) ?: return
    toolWindow.stripeTitle = EMULATOR_TOOL_WINDOW_TITLE

    if (settings.deviceMirroringEnabled) {
      if (contentCreated && physicalDeviceWatcher == null) {
        physicalDeviceWatcher = PhysicalDeviceWatcher()
      }
    }
    else {
      physicalDeviceWatcher?.dispose()
      physicalDeviceWatcher = null
      removeAllPhysicalDevicePanels()
    }
  }

  /**
   * Extracts and returns the port number from the serial number of an Emulator device,
   * or zero if the serial number doesn't have an expected format, "emulator-<port_number>".
   */
  private val IDevice.serialPort: Int
    get() {
      require(isEmulator)
      val pos = serialNumber.indexOf('-')
      return StringUtil.parseInt(serialNumber.substring(pos + 1), 0)
    }

  private inner class ToggleDeviceFrameAction : ToggleAction("Show Device Frame"), DumbAware {

    override fun update(event: AnActionEvent) {
      super.update(event)
      val panel = selectedPanel
      event.presentation.isEnabled = panel is EmulatorToolWindowPanel && panel.emulator.emulatorConfig.skinFolder != null
    }

    override fun isSelected(event: AnActionEvent): Boolean {
      return deviceFrameVisible
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      deviceFrameVisible = state
    }
  }

  private inner class ToggleZoomToolbarAction : ToggleAction("Show Zoom Controls"), DumbAware {

    override fun isSelected(event: AnActionEvent): Boolean {
      return zoomToolbarIsVisible
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      zoomToolbarIsVisible = state
    }
  }

  companion object {
    private const val DEVICE_FRAME_VISIBLE_PROPERTY = "com.android.tools.idea.emulator.frame.visible"
    private const val DEVICE_FRAME_VISIBLE_DEFAULT = true
    private const val ZOOM_TOOLBAR_VISIBLE_PROPERTY = "com.android.tools.idea.emulator.zoom.toolbar.visible"
    private const val ZOOM_TOOLBAR_VISIBLE_DEFAULT = true
    private const val EMULATOR_DISCOVERY_INTERVAL_MILLIS = 1000

    @JvmStatic
    private val ID_KEY = Key.create<DeviceId>("device-id")

    @JvmStatic
    private val LAUNCH_INFO_EXPIRATION = Duration.ofSeconds(30)

    @JvmStatic
    private val COLLATOR = Collator.getInstance()

    @JvmStatic
    private val PANEL_COMPARATOR = compareBy<RunningDevicePanel, Any?>(COLLATOR) { it.title }.thenBy { it.id }

    @JvmStatic
    private val registeredProjects: MutableSet<Project> = hashSetOf()

    /**
     * Initializes [EmulatorToolWindowManager] for the given project. Repeated calls for the same project
     * are ignored.
     */
    @JvmStatic
    fun initializeForProject(project: Project) {
      if (registeredProjects.add(project)) {
        Disposer.register(project.earlyDisposable) { registeredProjects.remove(project) }
        EmulatorToolWindowManager(project)
      }
    }

    @JvmStatic
    private fun isEmbeddedEmulator(commandLine: GeneralCommandLine) =
      commandLine.parametersList.parameters.contains("-qt-hide-window")
  }

  private inner class PhysicalDeviceWatcher : Disposable {
    @GuardedBy("this")
    private var devices = listOf<DeviceInfo>()
    @GuardedBy("this")
    private var mirroredDevices = setOf<String>()
    private val coroutineScope = AndroidCoroutineScope(this)

    init {
      coroutineScope.launch {
        val adbSession = AdbLibService.getSession(project)
        adbSession.hostServices.trackDevices().collect { deviceList ->
          onDeviceListChanged(deviceList, adbSession)
        }
      }
    }

    fun startMirroringIfOnline(emulatorId: EmulatorId) {
      val serialNumber = emulatorId.serialNumber
      if (addToMirroredDevices(serialNumber)) {
        coroutineScope.launch {
          physicalDeviceConnected(serialNumber, AdbLibService.getSession(project))
        }
      }
    }

    private fun addToMirroredDevices(serialNumber: String): Boolean {
      synchronized(this) {
        if (devices.find { it.serialNumber == serialNumber } == null) {
          return false // Unable to mirror because the device is not yet discovered by adb.
        }

        val devices = mirroredDevices.plus(serialNumber)
        if (devices.size == mirroredDevices.size) {
          return false // The device is already being mirrored.
        }
        mirroredDevices = devices
      }
      return true
    }

    private suspend fun onDeviceListChanged(deviceList: DeviceList, adbSession: AdbLibSession) {
      val added: Set<String>
      val removed: Set<String>
      synchronized(this) {
        val oldDevices = mirroredDevices
        devices = deviceList.filter { it.deviceState == DeviceState.ONLINE }
        val newDevices = devices.map(DeviceInfo::serialNumber).filter(::isMirrorable).toSortedSet()
        added = newDevices.minus(oldDevices)
        removed = oldDevices.minus(newDevices)
        mirroredDevices = newDevices
      }

      for (device in added) {
        physicalDeviceConnected(device, adbSession)
      }
      for (device in removed) {
        physicalDeviceDisconnected(device)
      }
    }

    private fun isMirrorable(deviceSerialNumber: String): Boolean {
      if (deviceSerialNumber.startsWith("emulator-")) {
        if (StudioFlags.DEVICE_MIRRORING_STANDALONE_EMULATORS.get()) {
          val emulators = RunningEmulatorCatalog.getInstance().emulators
          val emulator = emulators.find { "emulator-${it.emulatorId.serialPort}" == deviceSerialNumber}
          return emulator != null && !emulator.emulatorId.isEmbedded
        }
        return false
      }
      return true
    }

    override fun dispose() {
    }
  }
}
