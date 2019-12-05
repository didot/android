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
package com.android.tools.idea.templates

import com.android.testutils.TestUtils
import com.android.tools.idea.npw.platform.Language
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.openapi.util.SystemInfo
import java.io.File
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions

open class TemplateTest : TemplateTestBase() {
  private val withKotlin = { templateMap: MutableMap<String, Any>, projectMap: MutableMap<String, Any> ->
    projectMap[TemplateAttributes.ATTR_KOTLIN_VERSION] = TestUtils.getKotlinVersionForTests()
    projectMap[TemplateAttributes.ATTR_LANGUAGE] = Language.KOTLIN.toString()
    templateMap[TemplateAttributes.ATTR_LANGUAGE] = Language.KOTLIN.toString()
    templateMap[TemplateAttributes.ATTR_PACKAGE_NAME] = "test.pkg.in" // Add in a Kotlin keyword ("in") in the package name to trigger escape code too
  }

  private val withCpp = { templateMap: MutableMap<String, Any>, projectMap: MutableMap<String, Any> ->
    projectMap[TemplateAttributes.ATTR_CPP_SUPPORT] = true
    projectMap[TemplateAttributes.ATTR_CPP_FLAGS] = ""
    templateMap[TemplateAttributes.ATTR_CPP_SUPPORT] = true
    templateMap[TemplateAttributes.ATTR_CPP_FLAGS] = ""
  }

  private val withNewRenderingContext = { templateMap: MutableMap<String, Any>, _: MutableMap<String, Any> ->
    templateMap[COMPARE_NEW_RENDERING_CONTEXT] = true
  }

  private fun withNewLocation(location: String) = { templateMap: MutableMap<String, Any>, _: MutableMap<String, Any> ->
    templateMap["newLocation"] = location
  }

  //--- Activity templates ---
  @TemplateCheck
  fun testNewBasicActivity() {
    checkCreateTemplate("activities", "BasicActivity", ActivityCreationMode.WITHOUT_PROJECT, true)
  }

  @TemplateCheck
  fun testCompareNewBasicActivity() {
    checkCreateTemplate("activities", "BasicActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewBasicActivityWithKotlin() {
    checkCreateTemplate("activities", "BasicActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withKotlin)
  }

  @TemplateCheck
  fun testCompareNewBasicActivityWithKotlin() {
    checkCreateTemplate("activities", "BasicActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareNewEmptyActivityWithKotlin() {
    checkCreateTemplate("activities", "EmptyActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewProjectWithBasicActivity() {
    checkCreateTemplate("activities", "BasicActivity", ActivityCreationMode.WITH_PROJECT, true)
  }

  @TemplateCheck
  fun testNewThingsActivity() {
    checkCreateTemplate("activities", "AndroidThingsActivity", ActivityCreationMode.WITHOUT_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithThingsActivity() {
    checkCreateTemplate("activities", "AndroidThingsActivity", ActivityCreationMode.WITH_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithThingsActivityWithKotlin() {
    checkCreateTemplate("activities", "AndroidThingsActivity", ActivityCreationMode.WITH_PROJECT, true, withKotlin)
  }

  @TemplateCheck
  fun testNewEmptyActivity() {
    checkCreateTemplate("activities", "EmptyActivity", ActivityCreationMode.WITHOUT_PROJECT, true)
  }

  @TemplateCheck
  fun testCompareNewEmptyActivity() {
    checkCreateTemplate("activities", "EmptyActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewEmptyActivityWithKotlin() {
    checkCreateTemplate("activities", "EmptyActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withKotlin)
  }

  @TemplateCheck
  fun testNewProjectWithEmptyActivity() {
    checkCreateTemplate("activities", "EmptyActivity", ActivityCreationMode.WITH_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithEmptyActivityWithCpp() {
    // See b/144352075
    if (SystemInfo.isWindows) {
      return
    }
    checkCreateTemplate("activities", "EmptyActivity", ActivityCreationMode.WITH_PROJECT, true, withCpp)
  }

  @TemplateCheck
  fun testNewViewModelActivity() {
    checkCreateTemplate("activities", "ViewModelActivity", ActivityCreationMode.WITHOUT_PROJECT, true)
  }

  @TemplateCheck
  fun testNewViewModelActivityWithKotlin() {
    checkCreateTemplate("activities", "ViewModelActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withKotlin)
  }

  @TemplateCheck
  fun testCompareViewModelActivity() {
    checkCreateTemplate("activities", "ViewModelActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareViewModelActivityWithKotlin() {
    checkCreateTemplate("activities", "ViewModelActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewProjectWithViewModelActivity() {
    checkCreateTemplate("activities", "ViewModelActivity", ActivityCreationMode.WITH_PROJECT, true)
  }

  @TemplateCheck
  fun testNewTabbedActivity() {
    checkCreateTemplate("activities", "TabbedActivity", ActivityCreationMode.WITHOUT_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithTabbedActivity() {
    checkCreateTemplate("activities", "TabbedActivity", ActivityCreationMode.WITH_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithTabbedActivityWithKotlin() {
    checkCreateTemplate("activities", "TabbedActivity", ActivityCreationMode.WITH_PROJECT, true, withKotlin)
  }

  @TemplateCheck
  fun testCompareTabbedActivity() {
    checkCreateTemplate("activities", "TabbedActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareTabbedActivityWithKotlin() {
    checkCreateTemplate("activities", "TabbedActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewBlankWearActivity() {
    checkCreateTemplate("activities", "BlankWearActivity", ActivityCreationMode.WITHOUT_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithBlankWearActivity() {
    checkCreateTemplate("activities", "BlankWearActivity", ActivityCreationMode.WITH_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithBlankWearActivityWithKotlin() {
    checkCreateTemplate("activities", "BlankWearActivity", ActivityCreationMode.WITH_PROJECT, true, withKotlin)
  }

  @TemplateCheck
  fun testNewNavigationDrawerActivity() {
    checkCreateTemplate("activities", "NavigationDrawerActivity", ActivityCreationMode.WITHOUT_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithNavigationDrawerActivity() {
    checkCreateTemplate("activities", "NavigationDrawerActivity", ActivityCreationMode.WITH_PROJECT, true)
  }

  @TemplateCheck
  fun testCompareNavigationDrawerActivity() {
    checkCreateTemplate("activities", "NavigationDrawerActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewNavigationDrawerActivityWithKotlin() {
    checkCreateTemplate("activities", "NavigationDrawerActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withKotlin)
  }

  @TemplateCheck
  fun testNewMasterDetailFlow() {
    checkCreateTemplate("activities", "MasterDetailFlow", ActivityCreationMode.WITHOUT_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithMasterDetailFlow() {
    checkCreateTemplate("activities", "MasterDetailFlow", ActivityCreationMode.WITH_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithMasterDetailFlowWithKotlin() {
    checkCreateTemplate("activities", "MasterDetailFlow", ActivityCreationMode.WITH_PROJECT, true, withKotlin)
  }

  @TemplateCheck
  fun testNewFullscreenActivity() {
    checkCreateTemplate("activities", "FullscreenActivity", ActivityCreationMode.WITHOUT_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithFullscreenActivity() {
    checkCreateTemplate("activities", "FullscreenActivity", ActivityCreationMode.WITH_PROJECT, true)
  }

  @TemplateCheck
  fun testCompareNewFullscreenActivity() {
    checkCreateTemplate("activities", "FullscreenActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareNewFullscreenActivityWithKotlin() {
    checkCreateTemplate("activities", "FullscreenActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewProjectWithFullscreenActivityWithKotlin() {
    checkCreateTemplate("activities", "FullscreenActivity", ActivityCreationMode.WITH_PROJECT, true, withKotlin)
  }

  @TemplateCheck
  fun testNewLoginActivity() {
    checkCreateTemplate("activities", "LoginActivity", ActivityCreationMode.WITHOUT_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithLoginActivity() {
    checkCreateTemplate("activities", "LoginActivity", ActivityCreationMode.WITH_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithLoginActivityWithKotlin() {
    checkCreateTemplate("activities", "LoginActivity", ActivityCreationMode.WITH_PROJECT, true, withKotlin)
  }

  @TemplateCheck
  fun testCompareLoginActivity() {
    checkCreateTemplate("activities", "LoginActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareLoginActivityWithKotlin() {
    checkCreateTemplate("activities", "LoginActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewScrollActivity() {
    checkCreateTemplate("activities", "ScrollActivity", ActivityCreationMode.WITHOUT_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithScrollActivity() {
    checkCreateTemplate("activities", "ScrollActivity", ActivityCreationMode.WITH_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithScrollActivityWithKotlin() {
    checkCreateTemplate(
      "activities", "ScrollActivity", ActivityCreationMode.WITH_PROJECT, true,
      withKotlin, withNewLocation("menu_scroll_activity")
    )
  }

  @TemplateCheck
  fun testCompareNewScrollActivity() {
    checkCreateTemplate("activities", "ScrollActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareNewScrollActivityWithKotlin() {
    checkCreateTemplate("activities", "ScrollActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewSettingsActivity() {
    checkCreateTemplate("activities", "SettingsActivity", ActivityCreationMode.WITHOUT_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithSettingsActivity() {
    checkCreateTemplate("activities", "SettingsActivity", ActivityCreationMode.WITH_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithSettingsActivityWithKotlin() {
    checkCreateTemplate("activities", "SettingsActivity", ActivityCreationMode.WITH_PROJECT, true, withKotlin)
  }

  @TemplateCheck
  fun testCompareSettingsActivity() {
    checkCreateTemplate("activities", "SettingsActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareSettingsActivityWithKotlin() {
    checkCreateTemplate("activities", "SettingsActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testBottomNavigationActivity() {
    checkCreateTemplate("activities", "BottomNavigationActivity", ActivityCreationMode.WITHOUT_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithBottomNavigationActivity() {
    checkCreateTemplate("activities", "BottomNavigationActivity", ActivityCreationMode.WITH_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithBottomNavigationActivityWithKotlin() {
    checkCreateTemplate("activities", "BottomNavigationActivity", ActivityCreationMode.WITH_PROJECT, true, withKotlin)
  }

  @TemplateCheck
  fun testNewTvActivity() {
    checkCreateTemplate("activities", "AndroidTVActivity", ActivityCreationMode.WITHOUT_PROJECT, true)
  }

  @TemplateCheck
  fun testNewTvActivityWithKotlin() {
    checkCreateTemplate("activities", "AndroidTVActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withKotlin)
  }

  @TemplateCheck
  fun testNewProjectWithTvActivity() {
    checkCreateTemplate("activities", "AndroidTVActivity", ActivityCreationMode.WITH_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithTvActivityWithKotlin() {
    checkCreateTemplate("activities", "AndroidTVActivity", ActivityCreationMode.WITH_PROJECT, true, withKotlin)
  }

  @TemplateCheck
  fun testGoogleAdMobAdsActivity() {
    checkCreateTemplate("activities", "GoogleAdMobAdsActivity", ActivityCreationMode.WITHOUT_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithGoogleAdMobAdsActivity() {
    checkCreateTemplate("activities", "GoogleAdMobAdsActivity", ActivityCreationMode.WITH_PROJECT, true)
  }

  @TemplateCheck
  fun testGoogleMapsActivity() {
    checkCreateTemplate("activities", "GoogleMapsActivity", ActivityCreationMode.WITHOUT_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithGoogleMapsActivity() {
    checkCreateTemplate("activities", "GoogleMapsActivity", ActivityCreationMode.WITH_PROJECT, true)
  }

  @TemplateCheck
  fun testGoogleMapsWearActivity() {
    checkCreateTemplate("activities", "GoogleMapsWearActivity", ActivityCreationMode.WITHOUT_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithGoogleMapsWearActivity() {
    checkCreateTemplate("activities", "GoogleMapsWearActivity", ActivityCreationMode.WITH_PROJECT, true)
  }

  @TemplateCheck
  fun testNewProjectWithGoogleMapsWearActivityWithKotlin() {
    checkCreateTemplate("activities", "GoogleMapsWearActivity", ActivityCreationMode.WITH_PROJECT, true, withKotlin)
  }

  @TemplateCheck
  fun testNewAutomotiveProjectWithMediaService() {
    checkCreateTemplate("other", "AutomotiveMediaService", ActivityCreationMode.WITH_PROJECT, true)
  }

  @TemplateCheck
  fun testNewAutomotiveProjectWithMediaServiceWithKotlin() {
    checkCreateTemplate("other", "AutomotiveMediaService", ActivityCreationMode.WITH_PROJECT, true, withKotlin)
  }

  @TemplateCheck
  fun testNewProjectWithComposeActivity() {
    checkCreateTemplate("activities", "ComposeActivity", ActivityCreationMode.WITH_PROJECT, true, withKotlin) // Compose is always kotlin
  }

  @TemplateCheck
  fun testComposeActivity() {
    checkCreateTemplate("activities", "ComposeActivity", ActivityCreationMode.WITHOUT_PROJECT, true, withKotlin) // Compose is always kotlin
  }

  //--- Non-activity templates ---

  @TemplateCheck
  fun testCompareNewAndroidModule() {
    checkCreateTemplate("gradle-projects", "NewAndroidModule", ActivityCreationMode.DO_NOT_CREATE, false, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareNewAutomotiveModule() {
    checkCreateTemplate("gradle-projects", "NewAndroidAutomotiveModule", ActivityCreationMode.DO_NOT_CREATE, false, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareNewThingsModule() {
    checkCreateTemplate("gradle-projects", "NewAndroidThingsModule", ActivityCreationMode.DO_NOT_CREATE, false, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareNewTvModule() {
    checkCreateTemplate("gradle-projects", "NewAndroidTVModule", ActivityCreationMode.DO_NOT_CREATE, false, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareNewWearModule() {
    checkCreateTemplate("gradle-projects", "AndroidWearModule", ActivityCreationMode.DO_NOT_CREATE, false, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareNewBenchmarkModule() {
    checkCreateTemplate("gradle-projects", "NewBenchmarkModule", ActivityCreationMode.DO_NOT_CREATE, false, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareNewPureLibrary() {
    checkCreateTemplate("gradle-projects", "NewJavaOrKotlinLibrary", ActivityCreationMode.DO_NOT_CREATE, false, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewBroadcastReceiver() {
    // No need to try this template with multiple platforms, one is adequate
    checkCreateTemplate("other", "BroadcastReceiver", ActivityCreationMode.WITHOUT_PROJECT)
  }

  @TemplateCheck
  fun testNewBroadcastReceiverWithKotlin() {
    // No need to try this template with multiple platforms, one is adequate
    checkCreateTemplate("other", "BroadcastReceiver", ActivityCreationMode.WITHOUT_PROJECT, false, withKotlin)
  }

  @TemplateCheck
  fun testNewContentProvider() {
    checkCreateTemplate("other", "ContentProvider")
  }

  @TemplateCheck
  fun testNewContentProviderWithKotlin() {
    checkCreateTemplate("other", "ContentProvider", ActivityCreationMode.WITHOUT_PROJECT, false, withKotlin)
  }

  @TemplateCheck
  fun testNewSliceProvider() {
    checkCreateTemplate("other", "SliceProvider", ActivityCreationMode.WITHOUT_PROJECT, false)
  }

  @TemplateCheck
  fun testNewSliceProviderWithKotlin() {
    checkCreateTemplate("other", "SliceProvider", ActivityCreationMode.WITHOUT_PROJECT, false, withKotlin)
  }

  @TemplateCheck
  fun testNewCustomView() {
    checkCreateTemplate("other", "CustomView")
  }

  @TemplateCheck
  fun testNewIntentService() {
    checkCreateTemplate("other", "IntentService")
  }

  @TemplateCheck
  fun testNewIntentServiceWithKotlin() {
    checkCreateTemplate("other", "IntentService", ActivityCreationMode.WITHOUT_PROJECT,  false,withKotlin)
  }

  @TemplateCheck
  fun testNewListFragment() {
    checkCreateTemplate("fragments", "ListFragment")
  }

  @TemplateCheck
  fun testNewListFragmentWithKotlin() {
    checkCreateTemplate("fragments", "ListFragment", ActivityCreationMode.WITHOUT_PROJECT, false, withKotlin)
  }

  @TemplateCheck
  fun testNewModalBottomSheet() {
    checkCreateTemplate("fragments", "ModalBottomSheet")
  }

  @TemplateCheck
  fun testNewAppWidget() {
    checkCreateTemplate("other", "AppWidget")
  }

  @TemplateCheck
  fun testNewBlankFragment() {
    checkCreateTemplate("fragments", "BlankFragment")
  }

  @TemplateCheck
  fun testNewBlankFragmentWithKotlin() {
    checkCreateTemplate("fragments", "BlankFragment", ActivityCreationMode.WITHOUT_PROJECT, false, withKotlin)
  }

  @TemplateCheck
  fun testCompareBlankFragment() {
    checkCreateTemplate("fragments", "BlankFragment",
                        ActivityCreationMode.WITHOUT_PROJECT, false, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareBlankFragmentWithKotlin() {
    checkCreateTemplate("fragments", "BlankFragment",
                        ActivityCreationMode.WITHOUT_PROJECT, false, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewSettingsFragment() {
    checkCreateTemplate("fragments", "SettingsFragment", ActivityCreationMode.WITH_PROJECT, false)
  }

  @TemplateCheck
  fun testNewSettingsFragmentWithKotlin() {
    checkCreateTemplate("fragments", "SettingsFragment", ActivityCreationMode.WITHOUT_PROJECT, false, withKotlin)
  }

  @TemplateCheck
  fun testCompareSettingsFragment() {
    checkCreateTemplate("fragments", "SettingsFragment",
                        ActivityCreationMode.WITH_PROJECT, false, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareSettingsFragmentWithKotlin() {
    checkCreateTemplate("fragments", "SettingsFragment",
                        ActivityCreationMode.WITH_PROJECT, false, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewViewModelFragment() {
    checkCreateTemplate("fragments", "ViewModelFragment")
  }

  @TemplateCheck
  fun testNewViewModelFragmentWithKotlin() {
    checkCreateTemplate("fragments", "ViewModelFragment", ActivityCreationMode.WITHOUT_PROJECT, false, withKotlin)
  }

  @TemplateCheck
  fun testCompareViewModelFragment() {
    checkCreateTemplate("fragments", "ViewModelFragment", ActivityCreationMode.WITHOUT_PROJECT,
                        false, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareViewModelFragmentWithKotlin() {
    checkCreateTemplate("fragments", "ViewModelFragment", ActivityCreationMode.WITHOUT_PROJECT,
                        false, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewScrollFragment() {
    checkCreateTemplate("fragments", "ScrollFragment")
  }

  @TemplateCheck
  fun testNewScrollFragmentWithKotlin() {
    checkCreateTemplate("fragments", "ScrollFragment", ActivityCreationMode.WITHOUT_PROJECT, false, withKotlin)
  }

  @TemplateCheck
  fun testCompareScrollFragment() {
    checkCreateTemplate("fragments", "ScrollFragment",
                        ActivityCreationMode.WITHOUT_PROJECT, false, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareScrollFragmentWithKotlin() {
    checkCreateTemplate("fragments", "ScrollFragment",
                        ActivityCreationMode.WITHOUT_PROJECT, false, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewFullscreenFragment() {
    checkCreateTemplate("fragments", "FullscreenFragment")
  }

  @TemplateCheck
  fun testNewFullscreenFragmentWithKotlin() {
    checkCreateTemplate("fragments", "FullscreenFragment", ActivityCreationMode.WITHOUT_PROJECT, false, withKotlin)
  }

  @TemplateCheck
  fun testCompareFullscreenFragment() {
    checkCreateTemplate("fragments", "FullscreenFragment",
                        ActivityCreationMode.WITHOUT_PROJECT, false, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareFullscreenFragmentWithKotlin() {
    checkCreateTemplate("fragments", "FullscreenFragment",
                        ActivityCreationMode.WITHOUT_PROJECT, false, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewGoogleMapsFragment() {
    checkCreateTemplate("fragments", "GoogleMapsFragment")
  }

  @TemplateCheck
  fun testNewGoogleMapsFragmentWithKotlin() {
    checkCreateTemplate("fragments", "GoogleMapsFragment", ActivityCreationMode.WITHOUT_PROJECT, false, withKotlin)
  }

  @TemplateCheck
  fun testNewGoogleAdMobFragment() {
    checkCreateTemplate("fragments", "GoogleAdMobAdsFragment")
  }

  @TemplateCheck
  fun testNewGoogleAdMobFragmentWithKotlin() {
    checkCreateTemplate("fragments", "GoogleAdMobAdsFragment", ActivityCreationMode.WITHOUT_PROJECT, false, withKotlin)
  }

  @TemplateCheck
  fun testCompareGoogleAdMobFragment() {
    checkCreateTemplate("fragments", "GoogleAdMobAdsFragment",
                        ActivityCreationMode.WITHOUT_PROJECT, false, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareGoogleAdMobFragmentWithKotlin() {
    checkCreateTemplate("fragments", "GoogleAdMobAdsFragment",
                        ActivityCreationMode.WITHOUT_PROJECT, false, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testLoginFragment() {
    checkCreateTemplate("fragments", "LoginFragment")
  }

  @TemplateCheck
  fun testLoginFragmentWithKotlin() {
    checkCreateTemplate("fragments", "LoginFragment", ActivityCreationMode.WITHOUT_PROJECT, false, withKotlin)
  }

  @TemplateCheck
  fun testCompareLoginFragment() {
    checkCreateTemplate("fragments", "LoginFragment",
                        ActivityCreationMode.WITHOUT_PROJECT, false, withNewRenderingContext)
  }

  @TemplateCheck
  fun testCompareLoginFragmentWithKotlin() {
    checkCreateTemplate("fragments", "LoginFragment",
                        ActivityCreationMode.WITHOUT_PROJECT, false, withKotlin, withNewRenderingContext)
  }

  @TemplateCheck
  fun testNewService() {
    checkCreateTemplate("other", "Service")
  }

  @TemplateCheck
  fun testNewServiceWithKotlin() {
    checkCreateTemplate("other", "Service", ActivityCreationMode.WITHOUT_PROJECT, false, withKotlin)
  }

  @TemplateCheck
  fun testNewAidlFile() {
    checkCreateTemplate("other", "AidlFile")
  }

  @TemplateCheck
  fun testNewFolders() {
    checkCreateTemplate("other", "AidlFolder", ActivityCreationMode.WITHOUT_PROJECT, false, withNewLocation("foo"))
    checkCreateTemplate("other", "AssetsFolder", ActivityCreationMode.WITHOUT_PROJECT, false, withNewLocation("src/main/assets"))
    checkCreateTemplate("other", "FontFolder", ActivityCreationMode.WITHOUT_PROJECT, false, withNewLocation( "src/main/res/font"))
    checkCreateTemplate("other", "JavaFolder", ActivityCreationMode.WITHOUT_PROJECT, false, withNewLocation("src/main/java"))
    checkCreateTemplate("other", "JniFolder", ActivityCreationMode.WITHOUT_PROJECT, false, withNewLocation( "src/main/jni"))
    checkCreateTemplate("other", "RawFolder", ActivityCreationMode.WITHOUT_PROJECT, false, withNewLocation( "src/main/res/raw"))
    checkCreateTemplate("other", "ResFolder", ActivityCreationMode.WITHOUT_PROJECT, false, withNewLocation( "src/main/res"))
    checkCreateTemplate("other", "ResourcesFolder", ActivityCreationMode.WITHOUT_PROJECT, false, withNewLocation( "src/main/resources"))
    checkCreateTemplate("other", "RsFolder", ActivityCreationMode.WITHOUT_PROJECT, false, withNewLocation( "src/main/rs"))
    checkCreateTemplate("other", "XmlFolder", ActivityCreationMode.WITHOUT_PROJECT, false, withNewLocation( "src/main/res/xml"))
  }

  @TemplateCheck
  fun testAndroidManifest() {
    checkCreateTemplate("other", "AndroidManifest", ActivityCreationMode.WITHOUT_PROJECT, false, withNewLocation("src/foo/AndroidManifest.xml"))
  }


  @TemplateCheck
  fun testNewLayoutResourceFile() {
    checkCreateTemplate("other", "LayoutResourceFile")
  }

  @TemplateCheck
  fun testNewAppActionsResourceFile() {
    checkCreateTemplate("other", "AppActionsResourceFile")
  }

  @TemplateCheck
  fun testAutomotiveMediaService() {
    checkCreateTemplate("other", "AutomotiveMediaService", ActivityCreationMode.WITHOUT_PROJECT, false)
  }

  @TemplateCheck
  fun testAutomotiveMediaServiceWithKotlin() {
    checkCreateTemplate("other", "AutomotiveMediaService", ActivityCreationMode.WITHOUT_PROJECT, false, withKotlin)
  }

  @TemplateCheck
  fun testAutomotiveMessagingService() {
    checkCreateTemplate("other", "AutomotiveMessagingService")
  }

  @TemplateCheck
  fun testAutomotiveMessagingServiceWithKotlin() {
    checkCreateTemplate("other", "AutomotiveMessagingService", ActivityCreationMode.WITHOUT_PROJECT, false, withKotlin)
  }

  @TemplateCheck
  fun testWatchFaceService() {
    checkCreateTemplate("other", "WatchFaceService")
  }

  @TemplateCheck
  fun testWatchFaceServiceWithKotlin() {
    checkCreateTemplate("other", "WatchFaceService", ActivityCreationMode.WITH_PROJECT, false, withKotlin)
  }

  @TemplateCheck
  fun testNewValueResourceFile() {
    checkCreateTemplate("other", "ValueResourceFile")
  }

  open fun testAllTemplatesCovered() {
    if (DISABLED) {
      return
    }
    CoverageChecker().testAllTemplatesCovered()
  }

  // Create a dummy version of this class that just collects all the templates it will test when it is run.
  // It is important that this class is not run by JUnit!
  class CoverageChecker : TemplateTest() {
    override fun shouldRunTest(): Boolean = false

    // Set of templates tested with unit test
    private val templatesChecked = mutableSetOf<String>()

    private fun gatherMissedTests(templateFile: File, activityCreationMode: ActivityCreationMode): String? {
      val category: String = templateFile.parentFile.name
      val name: String = templateFile.name

      return "\nCategory: \"$category\" Name: \"$name\" activityCreationMode: $activityCreationMode".takeUnless {
        isBroken(name) || getCheckKey(category, name, activityCreationMode) in templatesChecked
      }
    }

    override fun checkCreateTemplate(
      category: String, name: String, activityCreationMode: ActivityCreationMode, apiSensitive: Boolean, vararg customizers: ProjectStateCustomizer
    ) {
      templatesChecked.add(getCheckKey(category, name, activityCreationMode))
    }

    // The actual implementation of the test
    override fun testAllTemplatesCovered() {
      this::class.memberFunctions
        .filter { it.findAnnotation<TemplateCheck>() != null && it.name.startsWith("test") }
        .forEach { it.call(this) }
      val manager = TemplateManager.getInstance()

      val failureMessages = sequence {
        for (templateFile in manager.getTemplates("other")) {
          yield(gatherMissedTests(templateFile, ActivityCreationMode.WITHOUT_PROJECT))
        }

        // Also try creating templates, not as part of creating a project
        for (templateFile in manager.getTemplates("activities")) {
          yield(gatherMissedTests(templateFile, ActivityCreationMode.WITH_PROJECT))
          yield(gatherMissedTests(templateFile, ActivityCreationMode.WITHOUT_PROJECT))
        }
      }.filterNotNull().toList()

      val failurePrefix = """
        The following templates were not covered by TemplateTest. Please ensure that tests are added to cover
        these templates and that they are annotated with @TemplateCheck.
        """.trimIndent()
      assertWithMessage(failurePrefix).that(failureMessages).isEmpty()
    }
  }
}
