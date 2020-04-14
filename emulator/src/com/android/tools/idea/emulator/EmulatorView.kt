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

import com.android.annotations.concurrency.Slow
import com.android.annotations.concurrency.UiThread
import com.android.emulator.control.ImageFormat
import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.Rotation.SkinRotation
import com.android.ide.common.util.Cancelable
import com.android.tools.adtui.Zoomable
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.idea.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.emulator.EmulatorController.ConnectionStateListener
import com.android.tools.idea.flags.StudioFlags.EMBEDDED_EMULATOR_TRACE_SCREENSHOTS
import com.android.tools.idea.protobuf.ByteString
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.geom.AffineTransform
import java.awt.image.ColorModel
import java.awt.image.MemoryImageSource
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.android.emulator.control.Image as ImageMessage
import com.android.emulator.control.MouseEvent as MouseEventMessage

/**
 * A view of the Emulator display optionally encased in the device frame.
 *
 * @param emulator the handle of the Emulator
 * @param cropFrame if true, the device frame is cropped to maximize the size of the display image
 */
class EmulatorView(
  private val emulator: EmulatorController,
  parentDisposable: Disposable,
  cropFrame: Boolean
) : JPanel(BorderLayout()), ComponentListener, ConnectionStateListener, Zoomable, Disposable {

  private var connectionStateLabel: JLabel
  private var screenshotFeed: Cancelable? = null
  private var displayImage: Image? = null
  private var displayWidth = 0
  private var displayHeight = 0
  private var skinLayout: ScaledSkinLayout? = null
  private var displayRotationInternal = SkinRotation.PORTRAIT
  private val displayTransform = AffineTransform()
  @Volatile
  private var screenshotReceiver: ScreenshotReceiver? = null

  init {
    Disposer.register(parentDisposable, this)

    connectionStateLabel = JLabel(getConnectionStateText(ConnectionState.NOT_INITIALIZED))
    connectionStateLabel.border = JBUI.Borders.emptyLeft(20)
    connectionStateLabel.font = connectionStateLabel.font.deriveFont(connectionStateLabel.font.size * 1.2F)

    isFocusable = true // Must be focusable to receive keyboard events.

    emulator.addConnectionStateListener(this)
    addComponentListener(this)

    // Forward mouse & keyboard events.
    addMouseMotionListener(object : MouseMotionAdapter() {
      override fun mouseDragged(event: MouseEvent) {
        sendMouseEvent(event.x, event.y, 1)
      }
    })

    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(event: MouseEvent) {
        sendMouseEvent(event.x, event.y, 1)
      }

      override fun mouseReleased(event: MouseEvent) {
        sendMouseEvent(event.x, event.y, 0)
      }

      override fun mouseClicked(event: MouseEvent) {
        requestFocusInWindow()
      }
    })

    addKeyListener(object : KeyAdapter() {
      override fun keyTyped(event: KeyEvent) {
        val keyboardEvent =
          when (val c = event.keyChar) {
            '\b' -> createHardwareKeyEvent("Backspace")
            else -> KeyboardEvent.newBuilder().setText(c.toString()).build()
          }
        emulator.sendKey(keyboardEvent)
      }
    })

    updateConnectionState(emulator.connectionState)
  }

  var displayRotation: SkinRotation
    get() = displayRotationInternal
    set(value) {
      if (value != displayRotationInternal && !cropFrame) {
        requestScreenshotFeed(value)
      }
    }

  var cropFrame: Boolean = cropFrame
    set(value) {
      if (field != value) {
        field = value
        requestScreenshotFeed()
      }
    }

  private inline val skinDefinition
    get() = emulator.skinDefinition

  private val emulatorConfig
    get() = emulator.emulatorConfig

  private val connected
    get() = emulator.connectionState == ConnectionState.CONNECTED

  override val screenScalingFactor
    get() = 1f

  override val scale: Double
    get() {
      if (!connected) {
        return 1.0
      }
      val rotatedDisplaySize = computeRotatedDisplaySize(emulatorConfig, displayRotationInternal)
      return min(displayWidth.toDouble() / rotatedDisplaySize.width, displayHeight.toDouble() / rotatedDisplaySize.height)
    }

  override fun zoom(type: ZoomType): Boolean {
    val scaledSize = computeZoomedSize(type)
    if (scaledSize == preferredSize) {
      return false
    }
    preferredSize = scaledSize
    revalidate()
    return true
  }

  override fun canZoomIn(): Boolean {
    return connected && computeZoomedSize(ZoomType.IN) != explicitlySetPreferredSize
  }

  override fun canZoomOut(): Boolean {
    return connected && computeZoomedSize(ZoomType.OUT) != explicitlySetPreferredSize
  }

  override fun canZoomToActual(): Boolean {
    return connected && computeZoomedSize(ZoomType.ACTUAL) != explicitlySetPreferredSize
  }

  override fun canZoomToFit(): Boolean {
    return connected && isPreferredSizeSet
  }

  private val explicitlySetPreferredSize: Dimension?
    get() = if (isPreferredSizeSet) preferredSize else null

  /**
   * Computes the preferred size after the given zoom operation. The preferred size is null for
   * zoom to fit.
   */
  private fun computeZoomedSize(zoomType: ZoomType): Dimension? {
    val newScale: Double
    when (zoomType) {
      ZoomType.IN -> {
        newScale = min(ZoomType.zoomIn((scale * 100).roundToInt()) / 100.0, MAX_SCALE)
      }
      ZoomType.OUT -> {
        newScale = max(ZoomType.zoomOut((scale * 100).roundToInt()) / 100.0, computeScaleToFitInParent())
      }
      ZoomType.ACTUAL -> {
        newScale = 1.0
      }
      ZoomType.FIT -> {
        return null
      }
      else -> throw IllegalArgumentException("Unsupported zoom type $zoomType")
    }
    val scaledSize = computeScaledSize(newScale, displayRotationInternal)
    if (scaledSize.width <= parent.width && scaledSize.height <= parent.height) {
      return null
    }
    return scaledSize
  }

  private fun computeScaleToFitInParent() = computeScaleToFit(parent.size, displayRotationInternal)

  private fun computeScaleToFit(availableSize: Dimension, rotation: SkinRotation): Double {
    return computeScaleToFit(computeActualSize(rotation), availableSize)
  }

  private fun computeScaleToFit(actualSize: Dimension, availableSize: Dimension): Double {
    return min(availableSize.width.toDouble() / actualSize.width, availableSize.height.toDouble() / actualSize.height)
  }

  private fun computeScaledSize(scale: Double, rotation: SkinRotation): Dimension {
    val size = computeActualSize(rotation)
    return Dimension((size.width * scale).roundToInt(), (size.height * scale).roundToInt())
  }

  private fun computeActualSize(rotation: SkinRotation): Dimension {
    val skin = skinDefinition
    return if (cropFrame || skin == null) {
      computeRotatedDisplaySize(emulatorConfig, rotation)
    }
    else {
      skin.getRotatedSkinSize(rotation)
    }
  }

  private fun computeRotatedDisplaySize(config: EmulatorConfiguration, rotation: SkinRotation): Dimension {
    return if (rotation.is90Degrees) {
      Dimension(config.displayHeight, config.displayWidth)
    }
    else {
      Dimension(config.displayWidth, config.displayHeight)
    }
  }

  private fun sendMouseEvent(x: Int, y: Int, button: Int) {
    val skin = skinLayout ?: return // Null skinLayout means that Emulator screen is not displayed.
    val skinSize = skin.skinSize
    val baseX: Int
    val baseY: Int
    if (cropFrame) {
      baseX = (width - displayWidth) / 2
      baseY = (height - displayHeight) / 2
    }
    else {
      baseX = (width - skinSize.width) / 2 + skin.displayRect.x
      baseY = (height - skinSize.height) / 2 + skin.displayRect.y
    }
    val normalizedX = (x - baseX).toDouble() / displayWidth - 0.5  // X relative to display center in [-0.5, 0.5) range.
    val normalizedY = (y - baseY).toDouble() / displayHeight - 0.5 // Y relative to display center in [-0.5, 0.5) range.
    val deviceDisplayWidth = emulatorConfig.displayWidth
    val deviceDisplayHeight = emulatorConfig.displayHeight
    val displayX: Int
    val displayY: Int
    when (displayRotationInternal) {
      SkinRotation.PORTRAIT -> {
        displayX = ((0.5 + normalizedX) * deviceDisplayWidth).roundToInt()
        displayY = ((0.5 + normalizedY) * deviceDisplayHeight).roundToInt()
      }
      SkinRotation.LANDSCAPE -> {
        displayX = ((0.5 - normalizedY) * deviceDisplayWidth).roundToInt()
        displayY = ((0.5 + normalizedX) * deviceDisplayHeight).roundToInt()
      }
      SkinRotation.REVERSE_PORTRAIT -> {
        displayX = ((0.5 - normalizedX) * deviceDisplayWidth).roundToInt()
        displayY = ((0.5 - normalizedY) * deviceDisplayHeight).roundToInt()
      }
      SkinRotation.REVERSE_LANDSCAPE -> {
        displayX = ((0.5 + normalizedY) * deviceDisplayWidth).roundToInt()
        displayY = ((0.5 - normalizedX) * deviceDisplayHeight).roundToInt()
      }
      else -> {
        return
      }
    }
    val mouseEvent = MouseEventMessage.newBuilder()
      .setX(displayX.coerceIn(0, deviceDisplayWidth))
      .setY(displayY.coerceIn(0, deviceDisplayHeight))
      .setButtons(button)
      .build()
    emulator.sendMouse(mouseEvent)
  }

  override fun connectionStateChanged(emulator: EmulatorController, connectionState: ConnectionState) {
    invokeLater {
      updateConnectionState(connectionState)
    }
  }

  private fun updateConnectionState(connectionState: ConnectionState) {
    if (connectionState == ConnectionState.CONNECTED) {
      remove(connectionStateLabel)
      if (isVisible) {
        requestScreenshotFeed()
      }
    }
    else {
      displayImage = null
      connectionStateLabel.text = getConnectionStateText(connectionState)
      add(connectionStateLabel)
    }
    revalidate()
    repaint()
  }

  private fun getConnectionStateText(connectionState: ConnectionState): String {
    return when (connectionState) {
      ConnectionState.CONNECTED -> "Connected"
      ConnectionState.DISCONNECTED -> "Disconnected from the Emulator"
      else -> "Connecting to the Emulator"
    }
  }

  override fun dispose() {
    removeComponentListener(this)
    emulator.removeConnectionStateListener(this)
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    val displayImage = displayImage ?: return
    val skin = skinLayout ?: return
    val skinSize = skin.skinSize
    val baseX: Int
    val baseY: Int
    if (cropFrame) {
      baseX = (width - displayWidth) / 2 - skin.displayRect.x
      baseY = (height - displayHeight) / 2 - skin.displayRect.y
    }
    else {
      baseX = (width - skinSize.width) / 2
      baseY = (height - skinSize.height) / 2
    }

    g as Graphics2D
    // Draw background.
    val background = skin.background
    if (background != null) {
      displayTransform.setToTranslation((baseX + background.x).toDouble(), (baseY + background.y).toDouble())
      g.drawImage(background.image, displayTransform, null)
    }
    // Draw display.
    displayTransform.setToTranslation((baseX + skin.displayRect.x).toDouble(), (baseY + skin.displayRect.y).toDouble())
    g.drawImage(displayImage, displayTransform, null)
    // Draw mask.
    val mask = skin.mask
    if (mask != null) {
      displayTransform.setToTranslation((baseX + mask.x).toDouble(), (baseY + mask.y).toDouble())
      g.drawImage(mask.image, displayTransform, null)
    }
  }

  private fun requestScreenshotFeed() {
    requestScreenshotFeed(displayRotationInternal)
  }

  private fun requestScreenshotFeed(rotation: SkinRotation) {
    screenshotFeed?.cancel()
    screenshotReceiver = null
    if (width != 0 && height != 0 && connected) {
      val rotatedDisplaySize = computeRotatedDisplaySize(emulatorConfig, rotation)
      val scale = computeScaleToFit(size, rotation)
      val scaledDisplaySize = rotatedDisplaySize.scaled(scale)

      // Limit the size of the received screenshots to avoid wasting gRPC resources.
      val screenshotSize = rotatedDisplaySize.scaled(scale.coerceAtMost(1.0))

      val imageFormat = ImageFormat.newBuilder()
        .setFormat(ImageFormat.ImgFormat.RGBA8888) // TODO: Change to RGB888 after b/150494232 is fixed.
        .setWidth(screenshotSize.width)
        .setHeight(screenshotSize.height)
        .build()
      val receiver = ScreenshotReceiver(DisplayShape(scaledDisplaySize.width, scaledDisplaySize.height, rotation))
      screenshotReceiver = receiver
      screenshotFeed = emulator.streamScreenshot(imageFormat, receiver)
    }
  }

  override fun componentResized(event: ComponentEvent) {
    requestScreenshotFeed()
  }

  override fun componentShown(event: ComponentEvent) {
    requestScreenshotFeed()
  }

  override fun componentHidden(event: ComponentEvent) {
    screenshotFeed?.cancel()
  }

  override fun componentMoved(event: ComponentEvent) {
  }

  private inner class ScreenshotReceiver(val displayShape: DisplayShape) : DummyStreamObserver<ImageMessage>() {
    private var cachedImageSource: MemoryImageSource? = null
    private var screenshotShape: DisplayShape? = null
    private val screenshotForSkinUpdate = AtomicReference<Screenshot>()
    private val screenshotForDisplay = AtomicReference<Screenshot>()

    override fun onNext(response: ImageMessage) {
      if (EMBEDDED_EMULATOR_TRACE_SCREENSHOTS.get()) {
        LOG.info("Screenshot ${response.seq} ${response.format.width}x${response.format.height}")
      }
      if (screenshotReceiver != this) {
        return // This screenshot feed has already been cancelled.
      }

      if (response.format.width == 0 || response.format.height == 0) {
        return // Ignore empty screenshot
      }

      val screenshot = Screenshot(response)

      // It is possible that the snapshot feed was requested assuming an out of date device rotation.
      // If the received rotation is different from the assumed one, ignore this screenshot and request
      // a fresh feed for the accurate rotation.
      if (screenshot.rotation != displayShape.rotation) {
        invokeLater {
          requestScreenshotFeed(screenshot.rotation)
        }
        return
      }

      if (screenshot.shape == screenshotShape) {
        updateDisplayImageAsync(screenshot)
      }
      else {
        updateSkinAndDisplayImageAsync(screenshot)
      }
    }

    private fun updateSkinAndDisplayImageAsync(screenshot: Screenshot) {
      screenshotForSkinUpdate.set(screenshot)

      ApplicationManager.getApplication().executeOnPooledThread {
        // If the screenshot feed has not been cancelled, update the skin and the display image.
        if (screenshotReceiver == this) {
          updateSkinAndDisplayImage()
        }
      }
    }

    @Slow
    private fun updateSkinAndDisplayImage() {
      val screenshot = screenshotForSkinUpdate.getAndSet(null) ?: return
      screenshot.skinLayout = emulator.skinDefinition?.createScaledLayout(displayShape.width, displayShape.height, displayShape.rotation)
      updateDisplayImageAsync(screenshot)
    }

    private fun updateDisplayImageAsync(screenshot: Screenshot) {
      screenshotForDisplay.set(screenshot)

      invokeLater {
        // If the screenshot feed has not been cancelled, update the display image.
        if (screenshotReceiver == this) {
          updateDisplayImage()
        }
      }
    }

    @UiThread
    private fun updateDisplayImage() {
      val screenshot = screenshotForDisplay.getAndSet(null) ?: return
      val w = screenshot.width
      val h = screenshot.height

      val layout = screenshot.skinLayout
      if (layout != null) {
        skinLayout = layout
      }
      if (skinLayout == null) {
        // Create a skin layout without a device frame.
        skinLayout = ScaledSkinLayout(Dimension(w, h))
      }

      displayRotationInternal = screenshot.rotation
      displayWidth = displayShape.width
      displayHeight = displayShape.height
      var imageSource = cachedImageSource
      if (imageSource == null || screenshotShape?.width != w || screenshotShape?.height != h) {
        imageSource = MemoryImageSource(w, h, screenshot.pixels, 0, w)
        imageSource.setAnimated(true)
        val image = createImage(imageSource)
        displayImage = if (w == displayWidth && h == displayHeight)
            image else image.getScaledInstance(displayWidth, displayHeight, Image.SCALE_SMOOTH)
        screenshotShape = screenshot.shape
        cachedImageSource = imageSource
      }
      else {
        imageSource.newPixels(screenshot.pixels, ColorModel.getRGBdefault(), 0, w)
      }
      repaint()
    }
  }

  private class Screenshot(emulatorImage: ImageMessage) {
    val shape: DisplayShape
    val pixels: IntArray
    var skinLayout: ScaledSkinLayout? = null
    val width: Int
      get() = shape.width
    val height: Int
      get() = shape.height
    val rotation: SkinRotation
      get() = shape.rotation

    init {
      val format = emulatorImage.format
      shape = DisplayShape(format.width, format.height, format.rotation.rotation)
      pixels = getPixels(emulatorImage.image, width, height)
    }
  }

  private data class DisplayShape(val width: Int, val height: Int, val rotation: SkinRotation)

  companion object {
    private const val MAX_SCALE = 2.0 // Zoom above 200% is not allowed.

    @JvmStatic
    private val LOG = Logger.getInstance(EmulatorView::class.java)

    @JvmStatic
    private fun getPixels(imageBytes: ByteString, width: Int, height: Int): IntArray {
      val pixels = IntArray(width * height)
      val byteIterator = imageBytes.iterator()
      for (i in pixels.indices) {
        val red = byteIterator.nextByte().toInt() and 0xFF
        val green = byteIterator.nextByte().toInt() and 0xFF
        val blue = byteIterator.nextByte().toInt() and 0xFF
        byteIterator.nextByte() // Alpha is ignored since the screenshots are always opaque.
        pixels[i] = 0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
      }
      return pixels
    }
  }
}