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
package com.android.tools.idea.stats

import com.android.ddmlib.IDevice
import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.CommonMetricsData
import com.android.tools.analytics.HostData
import com.android.tools.analytics.UsageTracker
import com.google.common.base.Strings
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.google.wireless.android.sdk.stats.DisplayDetails
import com.google.wireless.android.sdk.stats.IdePlugin
import com.google.wireless.android.sdk.stats.IdePluginInfo
import com.google.wireless.android.sdk.stats.MachineDetails
import com.google.wireless.android.sdk.stats.ProductDetails
import com.google.wireless.android.sdk.stats.ProductDetails.SoftwareLifeCycleChannel
import com.google.wireless.android.sdk.stats.StudioProjectChange
import com.google.wireless.android.sdk.stats.UserSentiment
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.editor.actionSystem.LatencyListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.ProjectLifecycleListener
import com.intellij.openapi.updateSettings.impl.ChannelStatus
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import java.io.File
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Tracks Android Studio specific metrics
 */
object AndroidStudioUsageTracker {
  private const val IDLE_TIME_BEFORE_SHOWING_DIALOG = 3 * 60 * 1000

  @JvmStatic
  val productDetails: ProductDetails
    get() {
      val application = ApplicationInfo.getInstance()

      return ProductDetails.newBuilder()
        .setProduct(ProductDetails.ProductKind.STUDIO)
        .setBuild(application.build.asString())
        .setVersion(application.strictVersion)
        .setOsArchitecture(CommonMetricsData.osArchitecture)
        .setChannel(lifecycleChannelFromUpdateSettings())
        .setTheme(currentIdeTheme())
        .build()
    }

  /** Gets information about all the displays connected to this machine.  */
  private val displayDetails: Iterable<DisplayDetails>
    get() {
      val displays = ArrayList<DisplayDetails>()

      val graphics = HostData.graphicsEnvironment!!
      if (!graphics.isHeadlessInstance) {
        for (device in graphics.screenDevices) {
          val defaultConfiguration = device.defaultConfiguration
          val bounds = defaultConfiguration.bounds
          displays.add(
            DisplayDetails.newBuilder()
              .setHeight(bounds.height.toLong())
              .setWidth(bounds.width.toLong())
              .setSystemScale(JBUIScale.sysScale(defaultConfiguration))
              .build())
        }
      }
      return displays
    }

  /**
   * Gets details about the machine this code is running on.
   *
   * @param homePath path to use to track total disk space.
   */
  @JvmStatic
  fun getMachineDetails(homePath: File): MachineDetails {
    val osBean = HostData.osBean!!

    return MachineDetails.newBuilder()
      .setAvailableProcessors(osBean.availableProcessors)
      .setTotalRam(osBean.totalPhysicalMemorySize)
      .setTotalDisk(homePath.totalSpace)
      .addAllDisplay(displayDetails)
      .build()
  }

  @JvmStatic
  fun setup(scheduler: ScheduledExecutorService) {
    scheduler.submit { runStartupReports() }
    // Send initial report immediately, daily from then on.
    scheduler.scheduleWithFixedDelay({ runDailyReports() }, 0, 1, TimeUnit.DAYS)
    // Send initial report immediately, hourly from then on.
    scheduler.scheduleWithFixedDelay({ runHourlyReports() }, 0, 1, TimeUnit.HOURS)

    subscribeToEvents()
  }

  private fun subscribeToEvents() {
    val app = ApplicationManager.getApplication()
    val connection = app.messageBus.connect()
    connection.subscribe(ProjectLifecycleListener.TOPIC, ProjectLifecycleTracker())
    connection.subscribe(LatencyListener.TOPIC, TypingLatencyTracker)
  }

  private fun runStartupReports() {
    reportEnabledPlugins()
  }

  private fun reportEnabledPlugins() {
    val plugins = PluginManagerCore.getLoadedPlugins(null)
    val pluginInfoProto = IdePluginInfo.newBuilder()

    for (plugin in plugins) {
      if (!plugin.isEnabled) continue
      val id = plugin.pluginId?.idString ?: continue

      val pluginProto = IdePlugin.newBuilder()
      pluginProto.id = id.take(256)
      plugin.version?.take(256)?.let { pluginProto.version = it }
      pluginProto.bundled = plugin.isBundled

      pluginInfoProto.addPlugins(pluginProto)
    }

    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.IDE_PLUGIN_INFO)
        .setIdePluginInfo(pluginInfoProto))
  }

  private fun runDailyReports() {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setCategory(AndroidStudioEvent.EventCategory.PING)
        .setKind(AndroidStudioEvent.EventKind.STUDIO_PING)
        .setProductDetails(productDetails)
        .setMachineDetails(getMachineDetails(File(PathManager.getHomePath())))
        .setJvmDetails(CommonMetricsData.jvmDetails))

    processUserSentiment()
  }

  private fun processUserSentiment() {
    if (!AnalyticsSettings.shouldRequestUserSentiment()) {
      return
    }
    requestUserSentiment()
  }

  /**
   * returning UNKNOWN_SATISFACTION_LEVEL means that the user hit the Cancel button in the dialog.
   */
  fun requestUserSentiment() {
    val eventQueue = IdeEventQueue.getInstance()

    lateinit var runner: Runnable
    runner = Runnable {
      // Ensure we're invoked only once.
      eventQueue.removeIdleListener(runner)

      val now = AnalyticsSettings.dateProvider.now()
      val dialog = SatisfactionDialog()
      dialog.showAndGetOk().doWhenDone(Runnable {
        val result = dialog.selectedSentiment
        UsageTracker.log(AndroidStudioEvent.newBuilder().apply {
          kind = AndroidStudioEvent.EventKind.USER_SENTIMENT
          userSentiment = UserSentiment.newBuilder().apply {
            state = UserSentiment.SentimentState.POPUP_QUESTION
            level = result
          }.build()
        })

        AnalyticsSettings.lastSentimentQuestionDate = now
        AnalyticsSettings.lastSentimentAnswerDate = now
        AnalyticsSettings.saveSettings()
      })
    }

    eventQueue.addIdleListener(runner, IDLE_TIME_BEFORE_SHOWING_DIALOG)
  }

  private fun runHourlyReports() {
    UsageTracker.log(AndroidStudioEvent.newBuilder()
                       .setCategory(AndroidStudioEvent.EventCategory.SYSTEM)
                       .setKind(AndroidStudioEvent.EventKind.STUDIO_PROCESS_STATS)
                       .setJavaProcessStats(CommonMetricsData.javaProcessStats))

    TypingLatencyTracker.reportTypingLatency()
  }

  /**
   * Creates a [DeviceInfo] from a [IDevice] instance.
   */
  @JvmStatic
  fun deviceToDeviceInfo(device: IDevice): DeviceInfo {
    return DeviceInfo.newBuilder()
      .setAnonymizedSerialNumber(AnonymizerUtil.anonymizeUtf8(device.serialNumber))
      .setBuildTags(Strings.nullToEmpty(device.getProperty(IDevice.PROP_BUILD_TAGS)))
      .setBuildType(Strings.nullToEmpty(device.getProperty(IDevice.PROP_BUILD_TYPE)))
      .setBuildVersionRelease(Strings.nullToEmpty(device.getProperty(IDevice.PROP_BUILD_VERSION)))
      .setBuildApiLevelFull(Strings.nullToEmpty(device.getProperty(IDevice.PROP_BUILD_API_LEVEL)))
      .setCpuAbi(CommonMetricsData.applicationBinaryInterfaceFromString(device.getProperty(IDevice.PROP_DEVICE_CPU_ABI)))
      .setManufacturer(Strings.nullToEmpty(device.getProperty(IDevice.PROP_DEVICE_MANUFACTURER)))
      .setDeviceType(if (device.isEmulator) DeviceInfo.DeviceType.LOCAL_EMULATOR else DeviceInfo.DeviceType.LOCAL_PHYSICAL)
      .setModel(Strings.nullToEmpty(device.getProperty(IDevice.PROP_DEVICE_MODEL))).build()
  }

  /**
   * Retrieves the corresponding [ProductDetails.IdeTheme] based on current IDE's settings
   */
  private fun currentIdeTheme(): ProductDetails.IdeTheme {
    return when {
      UIUtil.isUnderDarcula() ->
        // IJ's custom theme are based off of Darcula. We look at the LafManager to determine whether the actual selected theme is
        // darcular, high contrast, or some other custom theme
        when (LafManager.getInstance().currentLookAndFeel?.name?.toLowerCase(Locale.US)) {
          "darcula" -> ProductDetails.IdeTheme.DARCULA
          "high contrast" -> ProductDetails.IdeTheme.HIGH_CONTRAST
          else -> ProductDetails.IdeTheme.CUSTOM
        }
      UIUtil.isUnderGTKLookAndFeel() -> ProductDetails.IdeTheme.GTK
      UIUtil.isUnderIntelliJLaF() ->
        // When the theme is IntelliJ, there are mac and window specific registries that govern whether the theme refers to the native
        // themes, or the newer, platform-agnostic Light theme. UIUtil.isUnderWin10LookAndFeel() and UIUtil.isUnderDefaultMacTheme() take
        // care of these checks for us.
        when {
          UIUtil.isUnderWin10LookAndFeel() -> ProductDetails.IdeTheme.LIGHT_WIN_NATIVE
          UIUtil.isUnderDefaultMacTheme() -> ProductDetails.IdeTheme.LIGHT_MAC_NATIVE
          else -> ProductDetails.IdeTheme.LIGHT
        }
      else -> ProductDetails.IdeTheme.UNKNOWN_THEME
    }
  }

  /**
   * Creates a [DeviceInfo] from a [IDevice] instance
   * containing api level only.
   */
  @JvmStatic
  fun deviceToDeviceInfoApilLevelOnly(device: IDevice): DeviceInfo {
    return DeviceInfo.newBuilder()
      .setBuildApiLevelFull(Strings.nullToEmpty(device.getProperty(IDevice.PROP_BUILD_API_LEVEL)))
      .build()
  }

  /**
   * Reads the channel selected by the user from UpdateSettings and converts it into a [SoftwareLifeCycleChannel] value.
   */
  private fun lifecycleChannelFromUpdateSettings(): SoftwareLifeCycleChannel {
    return when (UpdateSettings.getInstance().selectedChannelStatus) {
      ChannelStatus.EAP -> SoftwareLifeCycleChannel.CANARY
      ChannelStatus.MILESTONE -> SoftwareLifeCycleChannel.DEV
      ChannelStatus.BETA -> SoftwareLifeCycleChannel.BETA
      ChannelStatus.RELEASE -> SoftwareLifeCycleChannel.STABLE
      else -> SoftwareLifeCycleChannel.UNKNOWN_LIFE_CYCLE_CHANNEL
    }
  }

  /**
   * Tracks use of projects (open, close, # of projects) in an instance of Android Studio.
   */
  private class ProjectLifecycleTracker : ProjectLifecycleListener {
    override fun beforeProjectLoaded(project: Project) {
      val projectsOpen = ProjectManager.getInstance().openProjects.size
      UsageTracker.log(AndroidStudioEvent.newBuilder()
                         .setKind(AndroidStudioEvent.EventKind.STUDIO_PROJECT_OPENED)
                         .setStudioProjectChange(StudioProjectChange.newBuilder()
                                                   .setProjectsOpen(projectsOpen)))


    }

    override fun afterProjectClosed(project: Project) {
      val projectsOpen = ProjectManager.getInstance().openProjects.size
      UsageTracker.log(AndroidStudioEvent.newBuilder()
                         .setKind(AndroidStudioEvent.EventKind.STUDIO_PROJECT_CLOSED)
                         .setStudioProjectChange(StudioProjectChange.newBuilder()
                                                   .setProjectsOpen(projectsOpen)))

    }
  }
}
