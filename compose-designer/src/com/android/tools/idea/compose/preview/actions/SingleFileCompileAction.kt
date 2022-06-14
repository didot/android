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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.idea.compose.preview.ComposePreviewBundle.message
import com.android.tools.idea.compose.preview.ComposePreviewManager
import com.android.tools.idea.compose.preview.ComposePreviewRepresentation
import com.android.tools.idea.compose.preview.PREVIEW_NOTIFICATION_GROUP_ID
import com.android.tools.idea.compose.preview.findComposePreviewManagersForContext
import com.android.tools.idea.compose.preview.isAnyPreviewRefreshing
import com.android.tools.idea.compose.preview.liveEdit.CompilationResult
import com.android.tools.idea.compose.preview.liveEdit.PreviewLiveEditManager
import com.android.tools.idea.compose.preview.util.toDisplayString
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.flags.StudioFlags.COMPOSE_LIVE_EDIT_PREVIEW
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.google.common.base.Stopwatch
import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.ui.JBColor
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.time.withTimeout
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.jetbrains.kotlin.base.util.module
import java.io.File
import java.time.Duration

private fun ProjectSystemBuildManager.BuildStatus.isSuccessOrUnknown() =
  this == ProjectSystemBuildManager.BuildStatus.SUCCESS || this == ProjectSystemBuildManager.BuildStatus.UNKNOWN

/**
 * Maximum amount of time to wait for a fast compilation to happen.
 */
private val LIVE_EDIT_PREVIEW_COMPILE_TIMEOUT = java.lang.Long.getLong("preview.live.edit.daemon.compile.seconds.timeout", 20)
private val SINGLE_FILE_REFRESH_ICON = ColoredIconGenerator.generateColoredIcon(AllIcons.Actions.ForceRefresh,
                                                                                JBColor(0x5969A8, 0x49549C))
/**
 * [AnAction] that triggers a compilation of the current module. The build will automatically trigger a refresh
 * of the surface.
 */
internal class SingleFileCompileAction :
  AnAction(message("action.live.edit.refresh.title"), null, SINGLE_FILE_REFRESH_ICON) {

  override fun actionPerformed(e: AnActionEvent) {
    val previewManager = findComposePreviewManagersForContext(e.dataContext).singleOrNull() ?: return

    FileDocumentManager.getInstance().saveAllDocuments()
    compile(previewManager, true)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val presentation = e.presentation
    val isBuildSuccessfulOrUnknown = project?.getProjectSystem()?.getBuildManager()
                                       ?.getLastBuildResult()
                                       ?.status
                                       ?.isSuccessOrUnknown() ?: false
    val isPreviewRefreshing = isAnyPreviewRefreshing(e.dataContext)
    presentation.isVisible = project != null && COMPOSE_LIVE_EDIT_PREVIEW.get()
    presentation.isEnabled =
      presentation.isVisible &&
      !isPreviewRefreshing &&
      isBuildSuccessfulOrUnknown
    presentation.description = if (isBuildSuccessfulOrUnknown)
      message("action.live.edit.refresh.description")
    else
      message("action.live.edit.refresh.description.needs.build")
  }

  companion object {
    /**
     * Starts a new fast compilation for the current file in the Preview. If [refreshAfterBuild] is set to true, after the compilation
     * has finished, the preview will automatically refresh.
     */
    fun compile(previewManager: ComposePreviewManager, refreshAfterBuild: Boolean = false) {
      val file = previewManager.previewedFile ?: return
      val contextModule = file.module ?: return
      val project = file.project
      val stopWatch = Stopwatch.createStarted()
      object : Task.Backgroundable(project, message("notification.compiling"), false) {
        override fun run(indicator: ProgressIndicator) {
          AndroidCoroutineScope(previewManager).async {
            val (result, outputAbsolutePath) = withTimeout(Duration.ofSeconds(LIVE_EDIT_PREVIEW_COMPILE_TIMEOUT)) {
              PreviewLiveEditManager.getInstance(project).compileRequest(listOf(file), contextModule, indicator)
            }
            val durationString = stopWatch.elapsed().toDisplayString()
            val isSuccess = result == CompilationResult.Success
            val buildMessage = if (isSuccess)
              message("event.log.live.edit.build.successful", durationString)
            else
              message("event.log.live.edit.build.failed", durationString)
            Notification(PREVIEW_NOTIFICATION_GROUP_ID,
                         buildMessage,
                         NotificationType.INFORMATION)
              .notify(project)
            if (isSuccess) {
              ModuleClassLoaderOverlays.getInstance(contextModule).overlayPath = File(outputAbsolutePath).toPath()
              if (refreshAfterBuild) {
                (previewManager as ComposePreviewRepresentation).forceRefresh()
              }
            }
          }.asCompletableFuture().join()
        }
      }.queue()
    }
  }
}