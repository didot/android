/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.configurations

import com.android.tools.adtui.actions.prettyPrintActions
import com.android.tools.idea.flags.StudioFlags
import org.mockito.Mockito
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.AnAction
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.registerServiceInstance
import com.google.common.truth.Truth
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.runInEdtAndGet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DeviceMenuAction2Test {

  @JvmField
  @Rule
  val appRule = ApplicationRule()

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.withAndroidModel().onEdt()

  @Before
  fun setUp() {
    StudioFlags.NELE_NEW_DEVICE_MENU.override(true)
    ApplicationManager.getApplication().registerServiceInstance(AdditionalDeviceService::class.java,
                                                                AdditionalDeviceService(),
                                                                projectRule.testRootDisposable)
    // Initial the window size devices, which is lazy.
    AdditionalDeviceService.getInstance()!!.getWindowSizeDevices()
  }

  @After
  fun tearDown() {
    StudioFlags.NELE_NEW_DEVICE_MENU.clearOverride()
  }

  @Test
  fun testActions() {
    val configuration = Mockito.mock(Configuration::class.java)
    Mockito.`when`(configuration.module).thenReturn(projectRule.projectRule.module)
    Mockito.`when`(configuration.configurationManager).thenReturn(ConfigurationManager.getOrCreateInstance(projectRule.projectRule.module))
    val holder = ConfigurationHolder { configuration }
    val menuAction = DeviceMenuAction2(holder)
    menuAction.updateActions(DataContext.EMPTY_CONTEXT)
    val presentationFactory = PresentationFactory()
    val actual = runInEdtAndGet {
      Utils.expandActionGroup(false, menuAction, presentationFactory, DataContext.EMPTY_CONTEXT, ActionPlaces.TOOLBAR)
      prettyPrintActions(menuAction, { action: AnAction -> !isAvdAction(action) }, presentationFactory)
    }
    val expected = """
    Reference Devices
    Phone (411 × 891 dp, xxhdpi)
    Foldable (674 × 841 dp, xxhdpi)
    Tablet (1280 × 800 dp, xxhdpi)
    Desktop (1920 × 1080 dp, xxhdpi)
    Phones and Tablets
        Pixel 5 (393 × 851 dp, xxhdpi)
        Pixel 4 (393 × 829 dp, xxhdpi)
        Pixel 4 XL (411 × 869 dp, xxxhdpi)
        Pixel 4a (393 × 851 dp, xxhdpi)
        Pixel 3 (393 × 785 dp, xxhdpi)
        Pixel 3 XL (411 × 846 dp, xxxhdpi)
        Pixel 3a (393 × 807 dp, xxhdpi)
        Pixel 3a XL (432 × 864 dp, xxhdpi)
        Pixel 2 (411 × 731 dp, xxhdpi)
        Pixel 2 XL (411 × 823 dp, 560dpi)
        Pixel (411 × 731 dp, 420dpi)
        Pixel XL (411 × 731 dp, 560dpi)
        Pixel C (1280 × 900 dp, xhdpi)
        Nexus 10 (1280 × 800 dp, xhdpi)
        Nexus 9 (1024 × 768 dp, xhdpi)
        Nexus 7 (600 × 960 dp, xhdpi)
        Nexus 7 (2012) (601 × 962 dp, hdpi)
        Nexus 6 (411 × 731 dp, 560dpi)
        Nexus 6P (411 × 731 dp, 560dpi)
        Nexus 5X (411 × 731 dp, 420dpi)
    ------------------------------------------------------
    Wear
    Wear OS Round Chin (240 × 218 dp, hdpi)
    ------------------------------------------------------
    TV
    Android TV (4K) (960 × 540 dp, xhdpi)
    Android TV (1080p) (960 × 540 dp, xhdpi)
    Android TV (720p) (962 × 541 dp, tvdpi)
    ------------------------------------------------------
    Auto
    Automotive (1024p landscape) (1024 × 768 dp, mdpi)
    ------------------------------------------------------
    Custom
    ------------------------------------------------------
    Generic Devices
        2.7" QVGA (320 × 427 dp, ldpi)
        2.7" QVGA slider (320 × 427 dp, ldpi)
        3.2" HVGA slider (ADP1) (320 × 480 dp, mdpi)
        3.2" QVGA (ADP2) (320 × 480 dp, mdpi)
        3.3" WQVGA (320 × 533 dp, ldpi)
        3.4" WQVGA (320 × 576 dp, ldpi)
        3.7" WVGA (Nexus One) (320 × 533 dp, hdpi)
        3.7" FWVGA slider (320 × 569 dp, hdpi)
        4" WVGA (Nexus S) (320 × 533 dp, hdpi)
        4.65" 720p (Galaxy Nexus) (360 × 640 dp, xhdpi)
        4.7" WXGA (640 × 360 dp, xhdpi)
        5.1" WVGA (480 × 800 dp, mdpi)
        5.4" FWVGA (480 × 854 dp, mdpi)
        Phone (411 × 891 dp, xxhdpi)
        6.7" Horizontal Fold-in (360 × 879 dp, xxhdpi)
        Foldable (674 × 841 dp, xxhdpi)
        7" WSVGA (Tablet) (1024 × 600 dp, mdpi)
        Resizable (674 × 841 dp, xxhdpi)
        7.4" Rollable (610 × 925 dp, xxhdpi)
        7.6" Fold-in with outer display (589 × 736 dp, xxhdpi)
        8" Fold-out (838 × 945 dp, xxhdpi)
        Tablet (1280 × 800 dp, xxhdpi)
        10.1" WXGA (Tablet) (1280 × 800 dp, mdpi)
        13.5" Freeform (1707 × 960 dp, hdpi)
        Desktop (1920 × 1080 dp, xxhdpi)
    Add Device Definition
"""
    Truth.assertThat(actual).isEqualTo(expected)
  }

  companion object {
    private fun isAvdAction(action: AnAction): Boolean {
      val text = action.templatePresentation.text
      return text != null && text.startsWith("AVD:")
    }
  }
}
