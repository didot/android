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
package com.android.tools.adtui.common

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.ui.JBColor
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.Math.abs

/**
 * Helper class to deserialize the color json palette. This is based on the format of the auto-generated data-visualization-palette.json.
 */
private data class DataVisualizationTheme(
  @SerializedName("name")
  val name: String,
  @SerializedName("light")
  val light: List<String>,
  @SerializedName("dark")
  val dark: List<String>)

/**
 * Palette file is visible for testing.
 */
object DataVisualizationColors {
  const val PRIMARY_DATA_COLOR = "Vivid Blue"
  const val BACKGROUND_DATA_COLOR = "Gray"

  val palette = LinkedHashMap<String, List<JBColor>>()
  var numberOfTonesPerColor = 0
  var isInitialized = false

  fun initialize(paletteStream: InputStream = javaClass.getResourceAsStream("/palette/data-visualization-palette.json")) {
    if (isInitialized) {
      return
    }
    loadColorPalette(paletteStream)
    numberOfTonesPerColor = palette.values.first().size
    isInitialized = true
  }

  /**
   * Returns a color that is mapped to an index. Index values that are outside the total number of colors are wrapped.
   * The map of colors can be thought of as a table of colors by tones. Each row is a new color each column is a new tone.  We start at 0,0
   * and enumerate the table row first until we run out of colors. At this point we increment our column and repeat.
   * Example:
   * [COLORA_TONE0, COLORA_TONE1]
   * [COLORB_TONE0, COLORB_TONE1]
   *
   * Index 0 maps to COLORA_TONE0
   * Index 1 maps to COLORB_TONE0
   * Index 2 maps to COLORA_TONE1
   * Index 3 maps to COLORB_TONE1.
   */
  fun getColor(index: Int): JBColor {
    return getColor(index, false)
  }

  fun getColor(index: Int, isFocused: Boolean): JBColor {
    return getColor(index, 0, isFocused)
  }

  fun getColor(index: Int, toneIndex: Int, isFocused: Boolean): JBColor {
    if (!isInitialized) {
      initialize()
    }
    val boundIndex = abs(index + numberOfTonesPerColor * toneIndex) % (palette.size * numberOfTonesPerColor)
    val colorIndex = boundIndex % palette.size
    val toneIndex = (boundIndex / palette.size) % numberOfTonesPerColor
    val color = palette.values.elementAt(colorIndex)[toneIndex]
    return if (isFocused) getFocusColor(color) else color
  }

  fun getColor(name: String, toneIndex: Int): JBColor {
    return getColor(name, toneIndex, false)
  }

  fun getColor(name: String, isFocused: Boolean): JBColor {
    return getColor(name, 0, isFocused)
  }

  fun getColor(name: String, toneIndex: Int, isFocused: Boolean): JBColor {
    if (!isInitialized) {
      initialize()
    }
    assert(palette.containsKey(name)) { "Palette does not contain specified key: ${name}." }
    val color = palette[name]!![toneIndex]
    return if (isFocused) getFocusColor(color) else color
  }

  /**
   * Returns a new color that represents the focused state.
   */
  fun getFocusColor(color: JBColor): JBColor {
    return JBColor(color.brighter(), color.darker())
  }

  /**
   * Helper function to parse and load the color palette json. The format of the json is expected to contain light, and dark mode colors
   * along with an array of tones for each color. The array of tones is expected to be a fixed length for all colors defined in the palette.
   *
   * Note: The data-visualization-palette.json is an auto generated file and should be updated when the design team creates new colors.
   */
  private fun loadColorPalette(paletteStream: InputStream) {
    val gsonParser = Gson()

    val colors = gsonParser.fromJson<Array<DataVisualizationTheme>>(
      InputStreamReader(paletteStream), Array<DataVisualizationTheme>::class.java)
    colors.forEach {
      assert(it.light.size == it.dark.size) {
        "Expected light (${it.light.size}), and dark (${it.dark.size}) palette to have same number of colors"
      }
      val jbColors = mutableListOf<JBColor>()
      assert(it.light.size == it.dark.size) { "Expected the same number of light mode and dark mode colors." }
      it.light.forEachIndexed { idx, color ->
        jbColors.add(JBColor(Integer.parseInt(color, 16), Integer.parseInt(it.dark[idx], 16)))
      }
      palette[it.name] = jbColors;
    }
  }

}