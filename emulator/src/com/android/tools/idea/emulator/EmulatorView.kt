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

import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.Slow
import com.android.annotations.concurrency.UiThread
import com.android.emulator.ImageConverter
import com.android.emulator.control.DisplayModeValue
import com.android.emulator.control.ImageFormat
import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.Notification.EventType.DISPLAY_CONFIGURATIONS_CHANGED_UI
import com.android.emulator.control.Notification.EventType.VIRTUAL_SCENE_CAMERA_ACTIVE
import com.android.emulator.control.Notification.EventType.VIRTUAL_SCENE_CAMERA_INACTIVE
import com.android.emulator.control.Rotation.SkinRotation
import com.android.emulator.control.RotationRadian
import com.android.emulator.control.Touch
import com.android.emulator.control.Touch.EventExpiration.NEVER_EXPIRE
import com.android.emulator.control.TouchEvent
import com.android.ide.common.util.Cancelable
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.common.AdtUiCursorType
import com.android.tools.adtui.common.AdtUiCursorsProvider
import com.android.tools.analytics.toProto
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.emulator.EmulatorConfiguration.DisplayMode
import com.android.tools.idea.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.emulator.EmulatorController.ConnectionStateListener
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.flags.StudioFlags.EMBEDDED_EMULATOR_TRACE_NOTIFICATIONS
import com.android.tools.idea.flags.StudioFlags.EMBEDDED_EMULATOR_TRACE_SCREENSHOTS
import com.google.protobuf.TextFormat.shortDebugString
import com.intellij.ide.DataManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import com.intellij.util.Alarm
import com.intellij.util.SofterReference
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlStringUtil
import org.HdrHistogram.Histogram
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.color.ColorSpace
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.InputEvent.CTRL_DOWN_MASK
import java.awt.event.InputEvent.SHIFT_DOWN_MASK
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.CHAR_UNDEFINED
import java.awt.event.KeyEvent.VK_BACK_SPACE
import java.awt.event.KeyEvent.VK_CONTROL
import java.awt.event.KeyEvent.VK_DELETE
import java.awt.event.KeyEvent.VK_DOWN
import java.awt.event.KeyEvent.VK_END
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_ESCAPE
import java.awt.event.KeyEvent.VK_HOME
import java.awt.event.KeyEvent.VK_KP_DOWN
import java.awt.event.KeyEvent.VK_KP_LEFT
import java.awt.event.KeyEvent.VK_KP_RIGHT
import java.awt.event.KeyEvent.VK_KP_UP
import java.awt.event.KeyEvent.VK_LEFT
import java.awt.event.KeyEvent.VK_PAGE_DOWN
import java.awt.event.KeyEvent.VK_PAGE_UP
import java.awt.event.KeyEvent.VK_RIGHT
import java.awt.event.KeyEvent.VK_SHIFT
import java.awt.event.KeyEvent.VK_TAB
import java.awt.event.KeyEvent.VK_UP
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1
import java.awt.event.MouseEvent.BUTTON2
import java.awt.event.MouseEvent.BUTTON3
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.DataBuffer
import java.awt.image.DataBufferInt
import java.awt.image.DirectColorModel
import java.awt.image.Raster
import java.awt.image.SinglePixelPackedSampleModel
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.nextDown
import kotlin.math.roundToInt
import com.android.emulator.control.Image as ImageMessage
import com.android.emulator.control.MouseEvent as MouseEventMessage
import com.android.emulator.control.Notification as EmulatorNotification

/**
 * A view of the Emulator display optionally encased in the device frame.
 *
 * @param disposableParent the disposable parent controlling the lifetime of this view
 * @param emulator the handle of the Emulator
 * @param displayId the ID of the device display
 * @param displaySize the size of the device display; a null value defaults to `emulator.emulatorConfig.displaySize`
 * @param deviceFrameVisible controls visibility of the device frame
 */
class EmulatorView(
  disposableParent: Disposable,
  val emulator: EmulatorController,
  displayId: Int,
  private val displaySize: Dimension?,
  deviceFrameVisible: Boolean
) : AbstractDisplayView(displayId), ConnectionStateListener, Disposable {

  private var lastScreenshot: Screenshot? = null
  private var displayRectangle: Rectangle? = null
  private val displayTransform = AffineTransform()
  private val screenshotShape: DisplayShape
    get() = lastScreenshot?.displayShape ?: DisplayShape(0, 0, initialOrientation)
  private val initialOrientation: Int
    get() = if (displayId == PRIMARY_DISPLAY_ID) emulatorConfig.initialOrientation.number else SkinRotation.PORTRAIT.number
  private val deviceDisplaySize: Dimension
    get() = displaySize ?: emulatorConfig.displaySize
  private val currentDisplaySize: Dimension
    get() = screenshotShape.activeDisplayRegion?.size ?: deviceDisplaySize
  private val deviceDisplayRegion: Rectangle
    get() = screenshotShape.activeDisplayRegion ?: Rectangle(deviceDisplaySize)
  internal val displayMode: DisplayMode?
    get() = screenshotShape.displayMode ?: emulatorConfig.displayModes.firstOrNull()

  /** Count of received display frames. */
  @get:VisibleForTesting
  var frameNumber = 0
    private set
  /** Time of the last frame update in milliseconds since epoch. */
  @get:VisibleForTesting
  var frameTimestampMillis = 0L
    private set

  private var screenshotFeed: Cancelable? = null
  @Volatile
  private var screenshotReceiver: ScreenshotReceiver? = null

  private var notificationFeed: Cancelable? = null
  @Volatile
  private var notificationReceiver: NotificationReceiver? = null

  private val displayConfigurationListeners: MutableList<DisplayConfigurationListener> = ContainerUtil.createLockFreeCopyOnWriteList()

  var displayOrientationQuadrants: Int
    get() = screenshotShape.orientation
    set(value) {
      if (value != screenshotShape.orientation && deviceFrameVisible) {
        requestScreenshotFeed(currentDisplaySize, value)
      }
    }

  var deviceFrameVisible: Boolean = deviceFrameVisible
    set(value) {
      if (field != value) {
        field = value
        requestScreenshotFeed()
      }
    }

  private val connected
    get() = emulator.connectionState == ConnectionState.CONNECTED
  private val emulatorConfig
    get() = emulator.emulatorConfig

  /**
   * The size of the device including frame in device pixels.
   */
  val displaySizeWithFrame: Dimension
    get() = computeActualSize(screenshotShape.orientation)

  private var multiTouchMode = false
    set(value) {
      if (value != field) {
        field = value
        repaint()
        if (!value) {
          // Terminate all ongoing touches.
          lastMultiTouchEvent?.let {
            val touchEvent = it.toBuilder()
            for (touch in touchEvent.touchesBuilderList) {
              touch.pressure = 0
            }
            emulator.sendTouch(touchEvent.build())
            lastMultiTouchEvent = null
          }
        }
      }
    }

  /** Last multi-touch event with pressure. */
  private var lastMultiTouchEvent: TouchEvent? = null
  /** A bit set indicating the current pressed buttons. */
  private var buttons = 0

  private var virtualSceneCameraActive = false
    set(value) {
      if (value != field) {
        field = value
        if (value) {
          multiTouchMode = false
          if (isFocusOwner) {
            showVirtualSceneCameraPrompt()
          }
        }
        else {
          virtualSceneCameraOperating = false
          hideVirtualSceneCameraPrompt()
        }
      }
    }

  private var virtualSceneCameraOperating = false
    set(value) {
      if (value != field) {
        field = value
        if (value) {
          startOperatingVirtualSceneCamera()
        }
        else {
          stopOperatingVirtualSceneCamera()
        }
      }
    }

  private var virtualSceneCameraVelocityController: VirtualSceneCameraVelocityController? = null
  private val stats = if (StudioFlags.EMBEDDED_EMULATOR_SCREENSHOT_STATISTICS.get()) Stats() else null

  init {
    Disposer.register(disposableParent, this)

    emulator.addConnectionStateListener(this)
    addComponentListener(object : ComponentAdapter() {
      override fun componentShown(event: ComponentEvent) {
        requestScreenshotFeed()
        if (displayId == PRIMARY_DISPLAY_ID) {
          requestNotificationFeed()
        }
      }
    })

    // Forward mouse & keyboard events.
    val mouseListener = MyMouseListener()
    addMouseListener(mouseListener)
    addMouseMotionListener(mouseListener)

    addKeyListener(MyKeyListener())

    if (displayId == PRIMARY_DISPLAY_ID) {
      showLongRunningOperationIndicator("Connecting to the Emulator")

      addFocusListener(object : FocusListener {
        override fun focusGained(event: FocusEvent) {
          if (virtualSceneCameraActive) {
            showVirtualSceneCameraPrompt()
          }
        }

        override fun focusLost(event: FocusEvent) {
          hideVirtualSceneCameraPrompt()
        }
      })
    }

    val connection = ApplicationManager.getApplication().messageBus.connect(this)
    connection.subscribe(LafManagerListener.TOPIC, LafManagerListener { lafManager ->
      if (connected) {
        emulator.setUiTheme(getEmulatorUiTheme(lafManager))
      }
    })

    updateConnectionState(emulator.connectionState)
  }

  override fun dispose() {
    cancelNotificationFeed()
    cancelScreenshotFeed()
    emulator.removeConnectionStateListener(this)
    stats?.let { Disposer.dispose(it) } // The stats object has to be disposed last.
  }

  fun addDisplayConfigurationListener(listener: DisplayConfigurationListener) {
    displayConfigurationListeners.add(listener)
  }

  fun removeDisplayConfigurationListener(listener: DisplayConfigurationListener) {
    displayConfigurationListeners.remove(listener)
  }

  override fun canZoom(): Boolean = connected

  override fun computeActualSize(): Dimension =
    computeActualSize(screenshotShape.orientation)

  private fun computeActualSize(orientationQuadrants: Int): Dimension {
    val skin = emulator.skinDefinition
    return if (skin != null && deviceFrameVisible) {
      skin.getRotatedFrameSize(orientationQuadrants, currentDisplaySize)
    }
    else {
      currentDisplaySize.rotatedByQuadrants(orientationQuadrants)
    }
  }

  override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    val resized = width != this.width || height != this.height
    super.setBounds(x, y, width, height)
    if (resized) {
      requestScreenshotFeed()
    }
  }

  override fun connectionStateChanged(emulator: EmulatorController, connectionState: ConnectionState) {
    invokeLaterInAnyModalityState {
      updateConnectionState(connectionState)
    }
  }

  private fun updateConnectionState(connectionState: ConnectionState) {
    if (connectionState == ConnectionState.CONNECTED) {
      hideDisconnectedStateMessage()
      if (isVisible) {
        if (screenshotFeed == null) {
          requestScreenshotFeed()
        }
        if (displayId == PRIMARY_DISPLAY_ID) {
          if (notificationFeed == null) {
            requestNotificationFeed()
          }
        }
      }
    }
    else if (connectionState == ConnectionState.DISCONNECTED) {
      lastScreenshot = null
      showDisconnectedStateMessage("Disconnected from the Emulator")
    }

    repaint()
  }

  private fun notifyEmulatorIsOutOfDate() {
    if (emulatorOutOfDateNotificationShown) {
      return
    }
    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this)) ?: return
    val title = "Emulator is out of date"
    val message = "Please update the Android Emulator"
    val notification = EMULATOR_NOTIFICATION_GROUP.createNotification(title, XmlStringUtil.wrapInHtml(message), NotificationType.WARNING)
    notification.collapseDirection = Notification.CollapseActionsDirection.KEEP_LEFTMOST
    notification.addAction(object : NotificationAction("Check for updates") {
      override fun actionPerformed(event: AnActionEvent, notification: Notification) {
        notification.expire()
        val action = ActionManager.getInstance().getAction("CheckForUpdate")
        action.actionPerformed(event)
      }
    })
    notification.notify(project)
    emulatorOutOfDateNotificationShown = true
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    val screenshot = lastScreenshot ?: return
    val skin = screenshot.skinLayout
    assert(screenshotShape.width != 0)
    assert(screenshotShape.height != 0)
    val displayRect = computeDisplayRectangle(skin)
    displayRectangle = displayRect

    g as Graphics2D
    val physicalToVirtualScale = 1.0 / screenScale
    g.scale(physicalToVirtualScale, physicalToVirtualScale) // Set the scale to draw in physical pixels.

    // Draw display.
    if (displayRect.width == screenshotShape.width && displayRect.height == screenshotShape.height) {
      g.drawImage(screenshot.image, null, displayRect.x, displayRect.y)
    }
    else {
      displayTransform.setToTranslation(displayRect.x.toDouble(), displayRect.y.toDouble())
      displayTransform.scale(displayRect.width.toDouble() / screenshotShape.width, displayRect.height.toDouble() / screenshotShape.height)
      g.drawImage(screenshot.image, displayTransform, null)
    }

    if (multiTouchMode) {
      drawMultiTouchFeedback(g, displayRect, (buttons and BUTTON1_BIT) != 0)
    }

    if (deviceFrameVisible) {
      // Draw device frame and mask.
      skin.drawFrameAndMask(g, displayRect)
    }
    if (!screenshot.painted) {
      screenshot.painted = true
      val paintTime = System.currentTimeMillis()
      stats?.recordLatencyEndToEnd(paintTime - screenshot.frameOriginationTime)
    }
  }

  private fun computeDisplayRectangle(skin: SkinLayout): Rectangle {
    // The roundScale call below is used to avoid scaling by a fractional factor larger than 1 or
    // by a factor that is only slightly below 1.
    return if (deviceFrameVisible) {
      val frameRectangle = skin.frameRectangle
      val scale = roundScale(min(realWidth.toDouble() / frameRectangle.width, realHeight.toDouble() / frameRectangle.height))
      val fw = frameRectangle.width.scaled(scale)
      val fh = frameRectangle.height.scaled(scale)
      val w = screenshotShape.width.scaled(scale)
      val h = screenshotShape.height.scaled(scale)
      Rectangle((realWidth - fw) / 2 - frameRectangle.x.scaled(scale), (realHeight - fh) / 2 - frameRectangle.y.scaled(scale), w, h)
    }
    else {
      val scale = roundScale(min(realWidth.toDouble() / screenshotShape.width, realHeight.toDouble() / screenshotShape.height))
      val w = screenshotShape.width.scaled(scale)
      val h = screenshotShape.height.scaled(scale)
      Rectangle((realWidth - w) / 2, (realHeight - h) / 2, w, h)
    }
  }

  private fun requestScreenshotFeed() {
    if (connected) {
      requestScreenshotFeed(currentDisplaySize, displayOrientationQuadrants)
    }
  }

  private fun requestScreenshotFeed(displaySize: Dimension, orientationQuadrants: Int) {
    if (width != 0 && height != 0 && connected) {
      val maxSize = realSize.rotatedByQuadrants(-orientationQuadrants)
      val skin = emulator.skinDefinition
      if (skin != null && deviceFrameVisible) {
        // Scale down to leave space for the device frame.
        val layout = skin.layout
        maxSize.width = maxSize.width.scaledDown(layout.displaySize.width, layout.frameRectangle.width)
        maxSize.height = maxSize.height.scaledDown(layout.displaySize.height, layout.frameRectangle.height)
      }

      // TODO: Remove the following three lines when b/238205075 is fixed.
      // Limit by the display resolution.
      maxSize.width = maxSize.width.coerceAtMost(displaySize.width)
      maxSize.height = maxSize.height.coerceAtMost(displaySize.height)

      val maxImageSize = maxSize.rotatedByQuadrants(orientationQuadrants)

      val currentReceiver = screenshotReceiver
      if (currentReceiver != null && currentReceiver.maxImageSize == maxImageSize) {
        return // Keep the current screenshot feed because it is identical.
      }

      cancelScreenshotFeed()
      val imageFormat = ImageFormat.newBuilder()
        .setDisplay(displayId)
        .setFormat(ImageFormat.ImgFormat.RGB888)
        .setWidth(maxImageSize.width)
        .setHeight(maxImageSize.height)
        .build()
      val receiver = ScreenshotReceiver(maxImageSize, orientationQuadrants)
      screenshotReceiver = receiver
      screenshotFeed = emulator.streamScreenshot(imageFormat, receiver)
    }
  }

  private fun cancelScreenshotFeed() {
    screenshotReceiver?.let { Disposer.dispose(it) }
    screenshotReceiver = null
    screenshotFeed?.cancel()
    screenshotFeed = null
  }

  private fun requestNotificationFeed() {
    cancelNotificationFeed()
    if (connected) {
      val receiver = NotificationReceiver()
      notificationReceiver = receiver
      notificationFeed = emulator.streamNotification(receiver)
    }
  }

  private fun cancelNotificationFeed() {
    notificationReceiver = null
    notificationFeed?.cancel()
    notificationFeed = null
  }

  private fun showVirtualSceneCameraPrompt() {
    findNotificationHolderPanel()?.showNotification("Hold Shift to control camera")
  }

  private fun hideVirtualSceneCameraPrompt() {
    findNotificationHolderPanel()?.hideNotification()
  }

  private fun startOperatingVirtualSceneCamera() {
    val keys = EmulatorSettings.getInstance().cameraVelocityControls.keys
    findNotificationHolderPanel()?.showNotification("Move camera with $keys keys, rotate with mouse or arrow keys")
    val glass = IdeGlassPaneUtil.find(this)
    val cursor = AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.MOVE)
    val rootPane = glass.rootPane
    val scale = PI / min(rootPane.width, rootPane.height)
    UIUtil.setCursor(rootPane, cursor)
    glass.setCursor(cursor, this)
    val referencePoint = MouseInfo.getPointerInfo().location
    val mouseListener = object: MouseAdapter() {

      override fun mouseMoved(event: MouseEvent) {
        rotateVirtualSceneCamera(-(event.yOnScreen - referencePoint.y) * scale, (referencePoint.x - event.xOnScreen) * scale)
        referencePoint.setLocation(event.xOnScreen, event.yOnScreen)
        event.consume()
      }

      override fun mouseDragged(e: MouseEvent) {
        mouseMoved(e)
      }

      override fun mouseEntered(event: MouseEvent) {
        glass.setCursor(cursor, this)
      }
    }

    val velocityController = VirtualSceneCameraVelocityController(emulator, EmulatorSettings.getInstance().cameraVelocityControls.keys)
    virtualSceneCameraVelocityController = velocityController
    glass.addMousePreprocessor(mouseListener, velocityController)
    glass.addMouseMotionPreprocessor(mouseListener, velocityController)
  }

  private fun stopOperatingVirtualSceneCamera() {
    virtualSceneCameraVelocityController?.let(Disposer::dispose)
    virtualSceneCameraVelocityController = null
    findNotificationHolderPanel()?.showNotification("Hold Shift to control camera")
    val glass = IdeGlassPaneUtil.find(this)
    glass.setCursor(null, this)
    UIUtil.setCursor(glass.rootPane, null)
  }

  private fun rotateVirtualSceneCamera(rotationX: Double, rotationY: Double) {
    val cameraRotation = RotationRadian.newBuilder()
      .setX(rotationX.toFloat())
      .setY(rotationY.toFloat())
      .build()
    emulator.rotateVirtualSceneCamera(cameraRotation)
  }

  private fun getButtonBit(button: Int): Int {
    return when(button) {
      BUTTON1 -> BUTTON1_BIT
      BUTTON2 -> BUTTON2_BIT
      BUTTON3 -> BUTTON3_BIT
      else -> 0
    }
  }

  internal fun displayModeChanged(displayModeId: DisplayModeValue) {
    val displayMode = emulatorConfig.displayModes.firstOrNull { it.displayModeId == displayModeId } ?: return
    requestScreenshotFeed(displayMode.displaySize, displayOrientationQuadrants)
  }

  private val IdeGlassPane.rootPane
    get() = (this as IdeGlassPaneImpl).rootPane

  private inner class NotificationReceiver : EmptyStreamObserver<EmulatorNotification>() {

    override fun onNext(response: EmulatorNotification) {
      if (EMBEDDED_EMULATOR_TRACE_NOTIFICATIONS.get()) {
        LOG.info("Notification ${response.event}")
      }

      if (notificationReceiver != this) {
        return // This notification feed has already been cancelled.
      }

      invokeLaterInAnyModalityState {
        when (response.event) {
          VIRTUAL_SCENE_CAMERA_ACTIVE -> virtualSceneCameraActive = true
          VIRTUAL_SCENE_CAMERA_INACTIVE -> virtualSceneCameraActive = false
          DISPLAY_CONFIGURATIONS_CHANGED_UI -> notifyDisplayConfigurationListeners()
          else -> {}
        }
      }
    }

    private fun notifyDisplayConfigurationListeners() {
      for (listener in displayConfigurationListeners) {
        listener.displayConfigurationChanged()
      }
    }
  }

  private inner class MyKeyListener  : KeyAdapter() {

    override fun keyTyped(event: KeyEvent) {
      if (virtualSceneCameraOperating) {
        return
      }

      val c = event.keyChar
      if (c == CHAR_UNDEFINED || Character.isISOControl(c)) {
        return
      }

      val keyboardEvent = KeyboardEvent.newBuilder().setText(c.toString()).build()
      emulator.sendKey(keyboardEvent)
    }

    override fun keyPressed(event: KeyEvent) {
      if (event.keyCode == VK_SHIFT && event.modifiersEx == SHIFT_DOWN_MASK && virtualSceneCameraActive) {
        virtualSceneCameraOperating = true
        return
      }

      if (virtualSceneCameraOperating) {
        when (event.keyCode) {
          VK_LEFT, VK_KP_LEFT -> rotateVirtualSceneCamera(0.0, VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
          VK_RIGHT, VK_KP_RIGHT -> rotateVirtualSceneCamera(0.0, -VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
          VK_UP, VK_KP_UP -> rotateVirtualSceneCamera(VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, 0.0)
          VK_DOWN, VK_KP_DOWN -> rotateVirtualSceneCamera(-VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, 0.0)
          VK_HOME -> rotateVirtualSceneCamera(VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
          VK_END -> rotateVirtualSceneCamera(-VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
          VK_PAGE_UP -> rotateVirtualSceneCamera(VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, -VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
          VK_PAGE_DOWN -> rotateVirtualSceneCamera(-VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, -VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
          else -> virtualSceneCameraVelocityController?.keyPressed(event.keyCode)
        }
        return
      }

      if (event.keyCode == VK_CONTROL && event.modifiersEx == CTRL_DOWN_MASK) {
        multiTouchMode = true
        return
      }

      // The Tab character is passed to the emulator, but Shift+Tab is converted to Tab and processed locally.
      if (event.keyCode == VK_TAB && event.modifiersEx == SHIFT_DOWN_MASK) {
        val tabEvent = KeyEvent(event.source as Component, event.id, event.getWhen(), 0, event.keyCode, event.keyChar, event.keyLocation)
        traverseFocusLocally(tabEvent)
        return
      }

      if (event.modifiersEx != 0) {
        return
      }
      val keyName =
        when (event.keyCode) {
          VK_BACK_SPACE -> "Backspace"
          VK_DELETE -> if (SystemInfo.isMac) "Backspace" else "Delete"
          VK_ENTER -> "Enter"
          VK_ESCAPE -> "Escape"
          VK_TAB -> "Tab"
          VK_LEFT, VK_KP_LEFT -> "ArrowLeft"
          VK_RIGHT, VK_KP_RIGHT -> "ArrowRight"
          VK_UP, VK_KP_UP -> "ArrowUp"
          VK_DOWN, VK_KP_DOWN -> "ArrowDown"
          VK_HOME -> "Home"
          VK_END -> "End"
          VK_PAGE_UP -> "PageUp"
          VK_PAGE_DOWN -> "PageDown"
          else -> return
        }
      emulator.sendKey(createHardwareKeyEvent(keyName))
    }

    override fun keyReleased(event: KeyEvent) {
      if (event.keyCode == VK_CONTROL) {
        multiTouchMode = false
      }
      else if (event.keyCode == VK_SHIFT) {
        virtualSceneCameraOperating = false
      }

      virtualSceneCameraVelocityController?.keyReleased(event.keyCode)
    }
  }

  private inner class MyMouseListener : MouseAdapter() {

    private var dragging = false

    override fun mousePressed(event: MouseEvent) {
      requestFocusInWindow()
      if (event.button == BUTTON1) {
        updateMultiTouchMode(event)
      }
      buttons = buttons or getButtonBit(event.button)
      sendMouseEvent(event.x, event.y, buttons)
    }

    override fun mouseReleased(event: MouseEvent) {
      if (event.button == BUTTON1) {
        updateMultiTouchMode(event)
      }
      buttons = buttons and getButtonBit(event.button).inv()
      sendMouseEvent(event.x, event.y, buttons)
    }

    override fun mouseEntered(event: MouseEvent) {
      updateMultiTouchMode(event)
    }

    override fun mouseExited(event: MouseEvent) {
      if (dragging) {
        sendMouseEvent(event.x, event.y, 0) // Terminate the ongoing dragging.
      }
      multiTouchMode = false
    }

    override fun mouseDragged(event: MouseEvent) {
      updateMultiTouchMode(event)
      if (!virtualSceneCameraOperating) {
        sendMouseEvent(event.x, event.y, buttons, drag = true)
      }
    }

    override fun mouseMoved(event: MouseEvent) {
      updateMultiTouchMode(event)
      if (!virtualSceneCameraOperating && !multiTouchMode) {
        sendMouseEvent(event.x, event.y, 0)
      }
    }

    private fun updateMultiTouchMode(event: MouseEvent) {
      val oldMultiTouchMode = multiTouchMode
      multiTouchMode = (event.modifiersEx and CTRL_DOWN_MASK) != 0 && !virtualSceneCameraOperating
      if (multiTouchMode && oldMultiTouchMode) {
        repaint() // If multitouch mode changed above, the repaint method was already called.
      }
    }

    private fun sendMouseEvent(x: Int, y: Int, button: Int, drag: Boolean = false) {
      val screenScale = screenScale
      sendMouseEventInPhysicalPixels(x * screenScale, y * screenScale, button, drag)
    }

    private fun sendMouseEventInPhysicalPixels(physicalX: Double, physicalY: Double, button: Int, drag: Boolean) {
      val displayRect = displayRectangle ?: return // Null displayRectangle means that Emulator screen is not displayed.
      if (!displayRect.contains(physicalX, physicalY)) {
        // Coordinates are outside the display rectangle.
        if (drag) {
          // Terminate dragging.
          sendMouseEventInPhysicalPixels(physicalX.coerceIn(displayRect.x.toDouble(),
                                                            (displayRect.x + displayRect.width).toDouble().nextDown()),
                                         physicalY.coerceIn(displayRect.y.toDouble(),
                                                            (displayRect.y + displayRect.height).toDouble().nextDown()),
                                         0, false)
        }
        return
      }

      dragging = drag

      val normalizedX = (physicalX - displayRect.x) / displayRect.width - 0.5  // X relative to display center in [-0.5, 0.5) range.
      val normalizedY = (physicalY - displayRect.y) / displayRect.height - 0.5 // Y relative to display center in [-0.5, 0.5) range.
      val deviceDisplayRegion = deviceDisplayRegion
      val displayX: Int
      val displayY: Int
      when (screenshotShape.orientation) {
        0 -> {
          displayX = transformNormalizedCoordinate(normalizedX, deviceDisplayRegion.x, deviceDisplayRegion.width)
          displayY = transformNormalizedCoordinate(normalizedY, deviceDisplayRegion.y, deviceDisplayRegion.height)
        }
        1 -> {
          displayX = transformNormalizedCoordinate(-normalizedY, deviceDisplayRegion.x, deviceDisplayRegion.width)
          displayY = transformNormalizedCoordinate(normalizedX, deviceDisplayRegion.y, deviceDisplayRegion.height)
        }
        2 -> {
          displayX = transformNormalizedCoordinate(-normalizedX, deviceDisplayRegion.x, deviceDisplayRegion.width)
          displayY = transformNormalizedCoordinate(-normalizedY, deviceDisplayRegion.y, deviceDisplayRegion.height)
        }
        3 -> {
          displayX = transformNormalizedCoordinate(normalizedY, deviceDisplayRegion.x, deviceDisplayRegion.width)
          displayY = transformNormalizedCoordinate(-normalizedX, deviceDisplayRegion.y, deviceDisplayRegion.height)
        }
        else -> {
          return
        }
      }

      sendMouseOrTouchEvent(displayX, displayY, button, deviceDisplayRegion)
    }

    private fun transformNormalizedCoordinate(normalizedCoordinate: Double, rangeStart: Int, rangeSize: Int): Int {
      return ((0.5 + normalizedCoordinate) * rangeSize).roundToInt().coerceIn(0, rangeSize - 1) + rangeStart
    }

    private fun sendMouseOrTouchEvent(displayX: Int, displayY: Int, button: Int, deviceDisplayRegion: Rectangle) {
      if (multiTouchMode) {
        val pressure = if (button == 0) 0 else PRESSURE_RANGE_MAX
        val touchEvent = TouchEvent.newBuilder()
          .setDisplay(displayId)
          .addTouches(createTouch(displayX, displayY, 0, pressure))
          .addTouches(createTouch(
            deviceDisplayRegion.width - 1 - displayX, deviceDisplayRegion.height - 1 - displayY, 1, pressure))
          .build()
        emulator.sendTouch(touchEvent)
        lastMultiTouchEvent = touchEvent
      }
      else {
        val mouseEvent = MouseEventMessage.newBuilder()
          .setDisplay(displayId)
          .setX(displayX)
          .setY(displayY)
          .setButtons(button)
          .build()
        emulator.sendMouse(mouseEvent)
      }
    }

    private fun createTouch(x: Int, y: Int, identifier: Int, pressure: Int): Touch.Builder {
      return Touch.newBuilder()
        .setX(x)
        .setY(y)
        .setIdentifier(identifier)
        .setPressure(pressure)
        .setExpiration(NEVER_EXPIRE)
    }
  }

  private inner class ScreenshotReceiver(
    val maxImageSize: Dimension,
    val orientationQuadrants: Int
  ) : EmptyStreamObserver<ImageMessage>(), Disposable {
    private val screenshotForProcessing = AtomicReference<Screenshot?>()
    private val screenshotForDisplay = AtomicReference<Screenshot?>()
    private val skinLayoutCache = SkinLayoutCache(emulator)
    private val recycledImage = AtomicReference<SofterReference<BufferedImage>?>()
    private val alarm = Alarm(this)
    private var expectedFrameNumber = -1

    override fun onNext(response: ImageMessage) {
      val arrivalTime = System.currentTimeMillis()
      val imageFormat = response.format
      val imageRotation = imageFormat.rotation.rotation.number
      val frameOriginationTime: Long = response.timestampUs / 1000
      val displayMode: DisplayMode? = emulatorConfig.displayModes.firstOrNull { it.displayModeId == imageFormat.displayMode }

      if (EMBEDDED_EMULATOR_TRACE_SCREENSHOTS.get()) {
        val latency = arrivalTime - frameOriginationTime
        val foldedState = if (imageFormat.hasFoldedDisplay()) " ${imageFormat.foldedDisplay}" else ""
        val mode = imageFormat.displayMode?.let { " $it"} ?: ""
        LOG.info("Screenshot ${response.seq} ${imageFormat.width}x${imageFormat.height}$mode$foldedState $imageRotation" +
                 " $latency ms latency")
      }
      if (screenshotReceiver != this) {
        expectedFrameNumber++
        return // This screenshot feed has already been cancelled.
      }

      if (imageFormat.width == 0 || imageFormat.height == 0) {
        expectedFrameNumber++
        return // Ignore empty screenshot.
      }

      if (response.image.size() != imageFormat.width * imageFormat.height * 3) {
        LOG.error("Inconsistent ImageMessage: ${imageFormat.width}x${imageFormat.width} image contains ${response.image.size()} bytes" +
                  " instead of ${imageFormat.width * imageFormat.height * 3}")
        return
      }

      // It is possible that the snapshot feed was requested assuming an out of date device rotation.
      // If the received rotation is different from the assumed one, ignore this screenshot and request
      // a fresh feed for the accurate rotation.
      if (imageRotation != orientationQuadrants) {
        invokeLaterInAnyModalityState {
          requestScreenshotFeed(currentDisplaySize, imageRotation)
        }
        expectedFrameNumber++
        return
      }
      // It is possible that the snapshot feed was requested assuming an out of date device rotation.
      // If the received rotation is different from the assumed one, ignore this screenshot and request
      // a fresh feed for the accurate rotation.
      if (imageFormat.displayMode != displayMode?.displayModeId) {
        if (displayMode != null) {
          invokeLaterInAnyModalityState {
            requestScreenshotFeed(displayMode.displaySize, imageRotation)
          }
          expectedFrameNumber++
          return
        }
      }

      alarm.cancelAllRequests()
      val recycledImage = recycledImage.getAndSet(null)?.get()
      val image = if (recycledImage?.width == imageFormat.width && recycledImage.height == imageFormat.height) {
        val pixels = (recycledImage.raster.dataBuffer as DataBufferInt).data
        ImageConverter.unpackRgb888(response.image, pixels)
        recycledImage
      }
      else {
        val pixels = IntArray(imageFormat.width * imageFormat.height)
        ImageConverter.unpackRgb888(response.image, pixels)
        val buffer = DataBufferInt(pixels, pixels.size)
        val sampleModel = SinglePixelPackedSampleModel(DataBuffer.TYPE_INT, imageFormat.width, imageFormat.height, SAMPLE_MODEL_BIT_MASKS)
        val raster = Raster.createWritableRaster(sampleModel, buffer, ZERO_POINT)
        @Suppress("UndesirableClassUsage")
        BufferedImage(COLOR_MODEL, raster, false, null)
      }

      val lostFrames = if (expectedFrameNumber > 0) response.seq - expectedFrameNumber else 0
      stats?.recordFrameArrival(arrivalTime - frameOriginationTime, lostFrames, imageFormat.width * imageFormat.height)
      expectedFrameNumber = response.seq + 1

      if (displayMode != null && !checkAspectRatioConsistency(imageFormat, displayMode)) {
        return
      }
      val foldedDisplay = imageFormat.foldedDisplay
      val activeDisplayRegion = when {
        foldedDisplay.width != 0 && foldedDisplay.height != 0 ->
            Rectangle(foldedDisplay.xOffset, foldedDisplay.yOffset, foldedDisplay.width, foldedDisplay.height)
        displayMode != null -> Rectangle(displayMode.displaySize)
        else -> null
      }
      val displayShape = DisplayShape(imageFormat.width, imageFormat.height, imageRotation, activeDisplayRegion, displayMode)
      val screenshot = Screenshot(displayShape, image, frameOriginationTime)
      val skinLayout = skinLayoutCache.getCached(displayShape)
      if (skinLayout == null) {
        computeSkinLayoutOnPooledThread(screenshot)
      }
      else {
        screenshot.skinLayout = skinLayout
        updateDisplayImageOnUiThread(screenshot)
      }
    }

    private fun checkAspectRatioConsistency(imageFormat: ImageFormat, displayMode: DisplayMode): Boolean {
      val imageAspectRatio = if (imageFormat.rotation.rotationValue % 2 == 0) imageFormat.width.toDouble() / imageFormat.height
                             else imageFormat.height.toDouble() / imageFormat.width
      val displayAspectRatio = displayMode.width.toDouble() / displayMode.height
      val tolerance = 1.0 / imageFormat.width + 1.0 / imageFormat.height
      if (abs(imageAspectRatio / displayAspectRatio - 1) > tolerance) {
        val imageDimensions = if (imageFormat.rotation.rotationValue % 2 == 0) "${imageFormat.width}x${imageFormat.height}"
                              else "${imageFormat.height}x${imageFormat.width}"
        LOG.error("Inconsistent ImageMessage: the $imageDimensions display image has different aspect ratio than" +
                  " the ${displayMode.width}x${displayMode.height} display")
        return false
      }
      return true
    }

    private fun computeSkinLayoutOnPooledThread(screenshotWithoutSkin: Screenshot) {
      screenshotForProcessing.set(screenshotWithoutSkin)

      executeOnPooledThread {
        // If the screenshot feed has not been cancelled, update the skin and the display image.
        if (screenshotReceiver == this) {
          val screenshot = screenshotForProcessing.getAndSet(null)
          if (screenshot == null) {
            stats?.recordDroppedFrame()
          }
          else {
            screenshot.skinLayout = skinLayoutCache.get(screenshot.displayShape)
            updateDisplayImageOnUiThread(screenshot)
          }
        }
      }
    }

    private fun updateDisplayImageOnUiThread(screenshot: Screenshot) {
      screenshotForDisplay.set(screenshot)

      invokeLaterInAnyModalityState {
        // If the screenshot feed has not been cancelled, update the display image.
        if (screenshotReceiver == this) {
          updateDisplayImage()
        }
      }
    }

    @UiThread
    private fun updateDisplayImage() {
      hideLongRunningOperationIndicatorInstantly()

      val screenshot = screenshotForDisplay.getAndSet(null)
      if (screenshot == null) {
        stats?.recordDroppedFrame()
        return
      }

      // Creation of a large BufferedImage is expensive. Recycle the old image if it has the proper size.
      lastScreenshot?.image?.let {
        if (it.width == screenshot.displayShape.width && it.height == screenshot.displayShape.height) {
          recycledImage.set(SofterReference(it))
          alarm.cancelAllRequests()
          alarm.addRequest({ recycledImage.set(null) }, CACHED_IMAGE_LIVE_TIME_MILLIS, ModalityState.any())
        }
        else if (!isSameAspectRatio(it.width, it.height, screenshot.displayShape.width, screenshot.displayShape.height, 0.01)) {
          zoom(ZoomType.FIT) // Display dimensions changed - reset zoom level.
        }
      }

      val lastDisplayMode = lastScreenshot?.displayShape?.displayMode
      lastScreenshot = screenshot

      frameNumber++
      frameTimestampMillis = System.currentTimeMillis()
      repaint()

      if (screenshot.displayShape.displayMode != lastDisplayMode) {
        firePropertyChange(DISPLAY_MODE_PROPERTY, lastDisplayMode, screenshot.displayShape.displayMode)
      }
    }

    override fun dispose() {
    }
  }

  private class Screenshot(val displayShape: DisplayShape, val image: BufferedImage, val frameOriginationTime: Long) {
    lateinit var skinLayout: SkinLayout
    var painted = false
  }

  /**
   * Stores the last computed scaled [SkinLayout] together with the corresponding display
   * dimensions and orientation.
   */
  private class SkinLayoutCache(val emulator: EmulatorController) {
    var displayShape: DisplayShape? = null
    var skinLayout: SkinLayout? = null

    fun getCached(displayShape: DisplayShape): SkinLayout? {
      synchronized(this) {
        return if (displayShape == this.displayShape) skinLayout else null
      }
    }

    @Slow
    fun get(displayShape: DisplayShape): SkinLayout {
      synchronized(this) {
        var layout = this.skinLayout
        if (displayShape != this.displayShape || layout == null) {
          layout = emulator.skinDefinition?.createScaledLayout(displayShape.width, displayShape.height, displayShape.orientation) ?:
                   SkinLayout(displayShape.width, displayShape.height)
          this.displayShape = displayShape
          this.skinLayout = layout
        }
        return layout
      }
    }
  }

  private data class DisplayShape(val width: Int,
                                  val height: Int,
                                  val orientation: Int,
                                  val activeDisplayRegion: Rectangle? = null,
                                  val displayMode: DisplayMode? = null)

  private class Stats: Disposable {
    @GuardedBy("this")
    private var data = Data()
    private val alarm = Alarm(this)

    init {
      scheduleNextLogging()
    }

    @Synchronized
    fun recordFrameArrival(latencyOfArrival: Long, numberOfLostFrames: Int, numberOfPixels: Int) {
      data.frameCount += 1 + numberOfLostFrames
      data.pixelCount += (1 + numberOfLostFrames) * numberOfPixels
      data.latencyOfArrival.recordValue(latencyOfArrival)
      if (numberOfLostFrames != 0) {
        data.droppedFrameCount += numberOfLostFrames
        data.droppedFrameCountBeforeArrival += numberOfLostFrames
      }
    }

    @Synchronized
    fun recordDroppedFrame() {
      data.droppedFrameCount++
    }

    @Synchronized
    fun recordLatencyEndToEnd(latency: Long) {
      data.latencyEndToEnd.recordValue(latency)
    }

    @Synchronized
    override fun dispose() {
      data.log()
    }

    @Synchronized
    private fun getAndSetData(newData: Data): Data {
      val oldData = data
      data = newData
      return oldData
    }

    private fun scheduleNextLogging() {
      alarm.addRequest(::logAndReset, STATS_LOG_INTERVAL_MILLIS, ModalityState.any())
    }

    private fun logAndReset() {
      getAndSetData(Data()).log()
      scheduleNextLogging()
    }

    private class Data {
      var frameCount = 0
      var droppedFrameCount = 0
      var droppedFrameCountBeforeArrival = 0
      var pixelCount = 0L
      val latencyEndToEnd = Histogram(1)
      val latencyOfArrival = Histogram(1)
      val collectionStart = System.currentTimeMillis()

      fun log() {
        if (frameCount != 0) {
          val frameRate = String.format("%.2g", frameCount * 1000.0 / (System.currentTimeMillis() - collectionStart))
          val frameSize = (pixelCount.toDouble() / frameCount).roundToInt()
          val neverArrived = if (droppedFrameCountBeforeArrival != 0) " (${droppedFrameCountBeforeArrival} never arrived)" else ""
          val dropped = if (droppedFrameCount != 0) " dropped frames: $droppedFrameCount$neverArrived" else ""
          LOG.info("Frames: $frameCount $dropped average frame rate: $frameRate average frame size: $frameSize pixels\n" +
                   "latency: ${shortDebugString(latencyEndToEnd.toProto())}\n" +
                   "latency of arrival: ${shortDebugString(latencyOfArrival.toProto())}")
        }
      }
    }
  }
}

internal const val DISPLAY_MODE_PROPERTY = "displayMode"

private var emulatorOutOfDateNotificationShown = false

private const val VIRTUAL_SCENE_CAMERA_ROTATION_STEP_DEGREES = 5
private const val VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN = VIRTUAL_SCENE_CAMERA_ROTATION_STEP_DEGREES * PI / 180

private val ZERO_POINT = Point()
private const val ALPHA_MASK = 0xFF shl 24
private val SAMPLE_MODEL_BIT_MASKS = intArrayOf(0xFF0000, 0xFF00, 0xFF, ALPHA_MASK)
private val COLOR_MODEL = DirectColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                                           32, 0xFF0000, 0xFF00, 0xFF, ALPHA_MASK, false, DataBuffer.TYPE_INT)
private const val CACHED_IMAGE_LIVE_TIME_MILLIS = 2000
// In Android MotionEvent, the right button is secondary and the middle button is tertiary, while in AWT the middle button is secondary and
// the right button is tertiary. Here the bits are for the Android (and the emulator gRPC) definition.
private const val BUTTON1_BIT = 1 shl 0 // Left
private const val BUTTON2_BIT = 1 shl 2 // Middle
private const val BUTTON3_BIT = 1 shl 1 // Right


private val STATS_LOG_INTERVAL_MILLIS = StudioFlags.EMBEDDED_EMULATOR_STATISTICS_INTERVAL_SECONDS.get().toLong() * 1000

// Keep the value in sync with goldfish's MTS_PRESSURE_RANGE_MAX.
private const val PRESSURE_RANGE_MAX = 0x400

private val LOG = Logger.getInstance(EmulatorView::class.java)