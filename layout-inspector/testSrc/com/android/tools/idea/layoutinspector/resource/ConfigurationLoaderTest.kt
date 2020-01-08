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
package com.android.tools.idea.layoutinspector.resource

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.layoutinspector.common.StringTable
import com.android.tools.idea.layoutinspector.common.StringTableImpl
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Configuration
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Resource
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.ResourceConfiguration
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.StringEntry
import com.google.common.truth.Truth.assertThat
import java.lang.String.join
import org.junit.After
import org.junit.Test

private const val APP_PACKAGE = "com.example"
private const val APP_THEME = "AppTheme"

class ConfigurationLoaderTest {
  private var stringIndex = 0
  private val table = mutableMapOf<String, Int>()

  @After
  fun cleanUp() {
    stringIndex = 0
    table.clear()
  }

  @Test
  fun testConfigurationLoader() {
    val configuration = ResourceConfiguration.newBuilder()
      .setAppPackageName(id(APP_PACKAGE))
      .setTheme(reference(ResourceType.STYLE, APP_PACKAGE, APP_THEME))
      .setApiLevel(AndroidVersion.VersionCodes.Q)
      .setConfiguration(
        Configuration.newBuilder()
          .setFontScale(1.0f)
          .setCountryCode(310)
          .setNetworkCode(410)
          .setScreenLayout(SCREENLAYOUT_SIZE_SMALL or SCREENLAYOUT_LONG_YES or SCREENLAYOUT_LAYOUTDIR_RTL or SCREENLAYOUT_ROUND_YES)
          .setColorMode(COLOR_MODE_WIDE_COLOR_GAMUT_YES or COLOR_MODE_HDR_YES)
          .setTouchScreen(TOUCHSCREEN_STYLUS)
          .setKeyboard(KEYBOARD_QWERTY)
          .setKeyboardHidden(KEYBOARDHIDDEN_NO)
          .setHardKeyboardHidden(KEYBOARDHIDDEN_NO)
          .setNavigation(NAVIGATION_WHEEL)
          .setNavigationHidden(NAVIGATIONHIDDEN_NO)
          .setUiMode(UI_MODE_TYPE_NORMAL or UI_MODE_NIGHT_NO)
          .setSmallestScreenWidth(200)
          .setDensity(0)
          .setOrientation(ORIENTATION_PORTRAIT)
          .setScreenWidth(480)
          .setScreenHeight(800)
      )
      .build()
    val table = stringTable()
    val loader = ConfigurationLoader(configuration, table)
    assertThat(loader.packageName).isEqualTo(APP_PACKAGE)
    assertThat(loader.theme).isEquivalentAccordingToCompareTo(
      ResourceReference(ResourceNamespace.fromPackageName(APP_PACKAGE), ResourceType.STYLE, APP_THEME))
    assertThat(loader.version).isEquivalentAccordingToCompareTo(AndroidVersion(AndroidVersion.VersionCodes.Q))
    assertThat(loader.folderConfiguration.qualifierString).isEqualTo(join("-",
        "mcc310",
        "mnc410",
        "ldrtl",
        "sw200dp",
        "w480dp",
        "h800dp",
        "small",
        "long",
        "round",
        "widecg",
        "highdr",
        "port",
        "notnight",
        "stylus",
        "keysexposed",
        "qwerty",
        "navexposed",
        "wheel",
        "v29"))
  }

  private fun reference(type: ResourceType, namespace: String, name: String): Resource =
    Resource.newBuilder()
      .setType(id(type.getName()))
      .setNamespace(id(namespace))
      .setName(id(name))
      .build()

  private fun id(str: String): Int = table[str] ?: addId(++stringIndex, str)

  private fun addId(id: Int, str: String): Int {
    table[str] = id
    return id
  }

  private fun stringTable(): StringTable =
    StringTableImpl(table.entries.map { StringEntry.newBuilder().setId(it.value).setStr(it.key).build() })
}
