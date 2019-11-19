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
package com.android.tools.idea.layoutinspector.ui

import com.android.testutils.TestUtils.getWorkspaceRoot
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.android.tools.idea.layoutinspector.transport.DefaultInspectorClient
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.awt.Color
import java.awt.Dimension
import java.awt.Image
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.io.File
import javax.imageio.ImageIO

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData"

class DeviceViewContentPanelTest {

  @Before
  fun setUp() {
    InspectorClient.clientFactory = { mock(InspectorClient::class.java) }
  }

  @After
  fun tearDown() {
    InspectorClient.clientFactory = { DefaultInspectorClient(it) }
  }

  @Test
  fun testSize() {
    val model = model {
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW1, 0, 0, 50, 50) {
          view(VIEW3, 30, 30, 10, 10)
        }
        view(VIEW2, 60, 160, 10, 20)
      }
    }
    val settings = DeviceViewSettings(scalePercent = 30)
    val panel = DeviceViewContentPanel(model, settings)
    assertEquals(Dimension(188, 197), panel.preferredSize)

    settings.scalePercent = 100
    assertEquals(Dimension(510, 542), panel.preferredSize)

    model.root = model {
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW1, 0, 0, 50, 50)
      }
    }.root
    assertEquals(Dimension(366, 410), panel.preferredSize)
  }

  @Test
  fun testPaint() {
    val model = model {
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW1, 25, 30, 50, 50)
      }
    }

    @Suppress("UndesirableClassUsage")
    val generatedImage = BufferedImage(200, 300, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val settings = DeviceViewSettings(scalePercent = 100)
    val panel = DeviceViewContentPanel(model, settings)
    panel.setSize(200, 300)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint.png"), generatedImage, 0.05)

    settings.scalePercent = 50
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_scaled.png"), generatedImage, 0.05)

    settings.scalePercent = 100
    panel.model.rotate(0.3, 0.2)
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_rotated.png"), generatedImage, 0.05)

    model.selection = model.root
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_selected.png"), generatedImage, 0.05)

    model.hoveredNode = model.root!!.children[0]
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_hovered.png"), generatedImage, 0.05)
  }

  @Test
  fun testOverlay() {
    val model = model {
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW1, 25, 30, 50, 50)
      }
    }

    @Suppress("UndesirableClassUsage")
    val generatedImage = BufferedImage(200, 300, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val settings = DeviceViewSettings(scalePercent = 100)
    val panel = DeviceViewContentPanel(model, settings)
    panel.setSize(200, 300)

    panel.model.overlay = ImageIO.read(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_hovered.png"))

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_overlay-60.png"), generatedImage, 0.05)

    panel.model.overlayAlpha = 0.2f
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_overlay-20.png"), generatedImage, 0.05)

    panel.model.overlayAlpha = 0.9f
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_overlay-90.png"), generatedImage, 0.05)
  }

  @Test
  fun testClipping() {
    val model = model {
      view(ROOT, 0, 0, 100, 100) {
        view(VIEW1, 25, 50, 50, 100)
      }
    }

    @Suppress("UndesirableClassUsage")
    val childImage = BufferedImage(50, 100, TYPE_INT_ARGB)
    val childImageGraphics = childImage.createGraphics()
    childImageGraphics.color = Color.RED
    childImageGraphics.fillOval(0, 0, 50, 100)

    model.root!!.children[0].imageBottom = childImage

    @Suppress("UndesirableClassUsage")
    val generatedImage = BufferedImage(200, 300, TYPE_INT_ARGB)
    val graphics = generatedImage.createGraphics()

    val settings = DeviceViewSettings(scalePercent = 50)
    val panel = DeviceViewContentPanel(model, settings)
    panel.setSize(200, 300)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testClip.png"), generatedImage, 0.1)
  }

  @Test
  fun testDrag() {
    val model = model {
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW1, 25, 30, 50, 50, imageBottom = mock(Image::class.java))
      }
    }

    val settings = DeviceViewSettings(scalePercent = 100)
    val panel = DeviceViewContentPanel(model, settings)
    panel.setSize(200, 300)
    val fakeUi = FakeUi(panel)

    fakeUi.mouse.drag(10, 10, 10, 10)
    assertEquals(0.01, panel.model.xOff)
    assertEquals(0.01, panel.model.yOff)

    panel.model.resetRotation()
    assertEquals(0.0, panel.model.xOff)
    assertEquals(0.0, panel.model.yOff)
  }
}