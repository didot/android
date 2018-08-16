/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.view

import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import java.awt.image.BufferedImage
import java.util.*
import javax.swing.*

class SingleImageViewTest {

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      JFrame().apply {
        val imageIcon = ImageIcon(UIUtil.createImage(200, 300, BufferedImage.TYPE_INT_ARGB).apply {
          createGraphics().apply {
            this.color = Color.BLUE
            this.fillRect(0, 0, 200, 300)
          }
        })

        val jLabel = JLabel(imageIcon).apply {
          isOpaque = false
        }
        contentPane = JList(arrayOf(1)).apply {
          cellRenderer = object : ListCellRenderer<Int> {
            private val random = Random()
            val card = SingleAssetCard().apply {
              withChessboard = true
              title = "title"
              subtitle = "Subtitle"
              thumbnail = jLabel
            }

            override fun getListCellRendererComponent(list: JList<out Int>?,
                                                      value: Int?,
                                                      index: Int,
                                                      isSelected: Boolean,
                                                      cellHasFocus: Boolean): Component {
              imageIcon.image = UIUtil.createImage(200, 300, BufferedImage.TYPE_INT_ARGB).apply {
                createGraphics().apply {
                  this.color = Color(random.nextInt())
                  this.fillRect(0, 0, 200, 300)
                }
              }
              return card
            }
          }
        }
        pack()
        isVisible = true
      }
    }
  }
}