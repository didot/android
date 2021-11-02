/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.MultiSelectionModel
import com.android.tools.adtui.model.Range
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineEvent
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Graphics
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.OverlayLayout
import kotlin.math.max
import kotlin.math.min

object FrameTimelineSelectionOverlayPanel {
  @JvmStatic
  fun of(content: JComponent, captureRange: Range, selection: MultiSelectionModel<*>, shouldGrayOutAll: Boolean): JComponent =
    object: JPanel() {
      override fun isOptimizedDrawingEnabled() = false
    }.apply {
      layout = OverlayLayout(this)
      isOpaque = false
      content.isOpaque = false
      add(overlay(captureRange, selection, shouldGrayOutAll))
      add(content)
      val handler = object : MouseListener, MouseMotionListener {
        override fun mouseClicked(e: MouseEvent) = content.dispatchEvent(e)
        override fun mousePressed(e: MouseEvent) = content.dispatchEvent(e)
        override fun mouseReleased(e: MouseEvent) = content.dispatchEvent(e)
        override fun mouseEntered(e: MouseEvent) = content.dispatchEvent(e)
        override fun mouseExited(e: MouseEvent) = content.dispatchEvent(e)
        override fun mouseDragged(e: MouseEvent) = content.dispatchEvent(e)
        override fun mouseMoved(e: MouseEvent) = content.dispatchEvent(e)
      }
      addMouseListener(handler)
      addMouseMotionListener(handler)
    }

  private fun overlay(captureRange: Range, selection: MultiSelectionModel<*>, shouldGrayOutAll: Boolean) = object : JComponent() {
    val observer = AspectObserver()
    init {
      selection.addDependency(observer)
        .onChange(MultiSelectionModel.Aspect.ACTIVE_SELECTION_CHANGED, ::repaint)
        .onChange(MultiSelectionModel.Aspect.SELECTIONS_CHANGED, ::repaint)
    }
    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      (selection.activeSelectionKey as? AndroidFrameTimelineEvent)?.let { event ->
        if (shouldGrayOutAll) {
          g.grayOutPixels(0, width)
        } else {
          val start = event.expectedStartUs
          val end = event.actualEndUs
          if (start > captureRange.min || end < captureRange.max) {
            g.grayOut(captureRange.min, max(captureRange.min, start.toDouble()))
            g.grayOut(min(captureRange.max, end.toDouble()), captureRange.max)
          }
        }
      }
    }

    private fun Graphics.grayOut(left: Double, right: Double) {
      val length = captureRange.length
      val leftCoord = ((left - captureRange.min) / length) * width
      val rightCoord = ((right - captureRange.min) / length) * width
      grayOutPixels(leftCoord.toInt(), rightCoord.toInt())
    }

    private fun Graphics.grayOutPixels(left: Int, right: Int) {
      color = TRANSLUCENT_GRAY
      fillRect(left, 0, right - left, height)
    }
  }
}

private val TRANSLUCENT_GRAY = JBColor(Color(.5f, .5f, .5f, .5f), Color(.5f, .5f, .5f, .5f))