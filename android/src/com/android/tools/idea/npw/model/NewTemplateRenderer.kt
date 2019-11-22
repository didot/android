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
package com.android.tools.idea.npw.model

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.withProjectId
import com.android.tools.idea.templates.TemplateUtils
import com.android.tools.idea.templates.recipe.RenderingContext2
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.Template
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplateRenderer
import com.google.wireless.android.sdk.stats.KotlinSupport
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

private val log: Logger get() = logger<Template>()

fun Template.render(c: RenderingContext2, e: RecipeExecutor): Boolean {
  val success = if (c.project.isInitialized)
    recipe.doRender(c, e)
  else
    PostprocessReformattingAspect.getInstance(c.project).disablePostprocessFormattingInside<Boolean> {
      recipe.doRender(c, e)
    }

  if (!c.dryRun && this != Template.NoActivity) {
    logRendering(c.projectTemplateData, c.project)
  }

  if (!c.dryRun) {
    StartupManager.getInstance(c.project)
      .runWhenProjectIsInitialized { TemplateUtils.reformatAndRearrange(c.project, c.targetFiles) }
  }

  ApplicationManager.getApplication().invokeAndWait { PsiDocumentManager.getInstance(c.project).commitAllDocuments() }

  return success
}

fun Recipe.doRender(c: RenderingContext2, e: RecipeExecutor): Boolean {
  try {
    writeCommandAction(c.project).withName(c.commandName).run<IOException> {
      this(e, c.templateData)
    }
  }
  catch (e: IOException) {
    if (c.showErrors) {
      invokeAndWaitIfNeeded {
        Messages.showErrorDialog(
          c.project,
          formatErrorMessage(c.commandName, !c.dryRun, e),
          "${c.commandName} Failed")
      }
    }
    else {
      throw RuntimeException(e)
    }
    return false
  }

  if (c.warnings.isEmpty()) {
    return true
  }

  if (!c.showWarnings) {
    log.warn("WARNING: " + c.warnings)
    return true
  }

  val result = AtomicBoolean()
  ApplicationManager.getApplication().invokeAndWait {
    val userReply = Messages.showOkCancelDialog(
      c.project,
      formatWarningMessage(c),
      "${c.commandName}, Warnings",
      "Proceed Anyway", "Cancel", Messages.getWarningIcon())
    result.set(userReply == Messages.OK)
  }
  return result.get()
}

/**
 * If this is not a dry run, we may have created/changed some files and the project
 * may no longer compile. Let the user know about undo.
 */
fun formatErrorMessage(commandName: String, canCausePartialRendering: Boolean, ex: IOException): String =
  if (!canCausePartialRendering)
    ex.message ?: "Unknown IOException occurred"
  else """${ex.message}

$commandName was only partially completed.
Your project may not compile.
You may want to Undo to get back to the original state.
"""


fun formatWarningMessage(context: RenderingContext2): String {
  val maxWarnings = 10
  val warningCount = context.warnings.size
  var messages: MutableList<String> = context.warnings.toMutableList()
  if (warningCount > maxWarnings + 1) {  // +1 such that the message can say "warnings" in plural...
    // Guard against too many warnings (the dialog may become larger than the screen size)
    messages = messages.subList(0, maxWarnings)
    val strippedWarningsCount = warningCount - maxWarnings
    messages.add("And $strippedWarningsCount more warnings...")
  }
  messages.add("\nIf you proceed the resulting project may not compile or not work as intended.")
  return messages.joinToString("\n\n")
}

@VisibleForTesting
internal fun titleToTemplateRenderer(title: String): TemplateRenderer = when (title) {
  "" -> TemplateRenderer.UNKNOWN_TEMPLATE_RENDERER
  "Android Module" -> TemplateRenderer.ANDROID_MODULE
  "Android Project" -> TemplateRenderer.ANDROID_PROJECT
  "Empty Activity" -> TemplateRenderer.EMPTY_ACTIVITY
  "Blank Activity" -> TemplateRenderer.BLANK_ACTIVITY
  "Layout XML File" -> TemplateRenderer.LAYOUT_XML_FILE
  "Fragment (Blank)" -> TemplateRenderer.FRAGMENT_BLANK
  "Navigation Drawer Activity" -> TemplateRenderer.NAVIGATION_DRAWER_ACTIVITY
  "Values XML File" -> TemplateRenderer.VALUES_XML_FILE
  "Google Maps Activity" -> TemplateRenderer.GOOGLE_MAPS_ACTIVITY
  "Login Activity" -> TemplateRenderer.LOGIN_ACTIVITY
  "Assets Folder" -> TemplateRenderer.ASSETS_FOLDER
  "Tabbed Activity" -> TemplateRenderer.TABBED_ACTIVITY
  "Scrolling Activity" -> TemplateRenderer.SCROLLING_ACTIVITY
  "Fullscreen Activity" -> TemplateRenderer.FULLSCREEN_ACTIVITY
  "Service" -> TemplateRenderer.SERVICE
  "Java Library" -> TemplateRenderer.JAVA_LIBRARY
  "Settings Activity" -> TemplateRenderer.SETTINGS_ACTIVITY
  "Fragment (List)" -> TemplateRenderer.FRAGMENT_LIST
  "Master/Detail Flow" -> TemplateRenderer.MASTER_DETAIL_FLOW
  "Wear OS Module" -> TemplateRenderer.ANDROID_WEAR_MODULE
  "Broadcast Receiver" -> TemplateRenderer.BROADCAST_RECEIVER
  "AIDL File" -> TemplateRenderer.AIDL_FILE
  "Service (IntentService)" -> TemplateRenderer.INTENT_SERVICE
  "JNI Folder" -> TemplateRenderer.JNI_FOLDER
  "Java Folder" -> TemplateRenderer.JAVA_FOLDER
  "Custom View" -> TemplateRenderer.CUSTOM_VIEW
  "Android TV Module" -> TemplateRenderer.ANDROID_TV_MODULE
  "Google AdMob Ads Activity" -> TemplateRenderer.GOOGLE_ADMOBS_ADS_ACTIVITY
  "Always On Wear Activity" -> TemplateRenderer.ALWAYS_ON_WEAR_ACTIVITY
  "Res Folder" -> TemplateRenderer.RES_FOLDER
  "Android TV Activity" -> TemplateRenderer.ANDROID_TV_ACTIVITY
  "Blank Wear Activity" -> TemplateRenderer.BLANK_WEAR_ACTIVITY
  "Basic Activity" -> TemplateRenderer.BASIC_ACTIVITIY
  "App Widget" -> TemplateRenderer.APP_WIDGET
  "Instant App Project" -> TemplateRenderer.ANDROID_INSTANT_APP_PROJECT
  "Instant App" -> TemplateRenderer.ANDROID_INSTANT_APP_MODULE
  "Dynamic Feature (Instant App)" -> TemplateRenderer.ANDROID_INSTANT_APP_DYNAMIC_MODULE
  "Benchmark Module" -> TemplateRenderer.BENCHMARK_LIBRARY_MODULE
  "Empty Compose Activity" -> TemplateRenderer.COMPOSE_EMPTY_ACTIVITY
  else -> TemplateRenderer.CUSTOM_TEMPLATE_RENDERER
}

fun Template.logRendering(projectTemplateData: ProjectTemplateData, project: Project) {
  val aseBuilder = AndroidStudioEvent.newBuilder()
    .setCategory(AndroidStudioEvent.EventCategory.TEMPLATE)
    .setKind(AndroidStudioEvent.EventKind.TEMPLATE_RENDER)
    .setTemplateRenderer(titleToTemplateRenderer(this.name))
    .setKotlinSupport(
      KotlinSupport.newBuilder()
        .setIncludeKotlinSupport(projectTemplateData.language == Language.Kotlin)
        .setKotlinSupportVersion(projectTemplateData.kotlinVersion))
  UsageTracker.log(aseBuilder.withProjectId(project))

  /*TODO(qumeric)
  if (ATTR_DYNAMIC_IS_INSTANT_MODULE) {
    aseBuilder.templateRenderer = AndroidStudioEvent.TemplateRenderer.ANDROID_INSTANT_APP_DYNAMIC_MODULE
    UsageTracker.log(aseBuilder.withProjectId(project))
  }*/
}

