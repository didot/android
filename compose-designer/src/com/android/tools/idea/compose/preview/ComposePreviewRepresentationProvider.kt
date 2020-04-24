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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.common.actions.IssueNotificationAction
import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.compose.preview.actions.ForceCompileAndRefreshAction
import com.android.tools.idea.compose.preview.actions.GroupSwitchAction
import com.android.tools.idea.compose.preview.actions.ShowDebugBoundaries
import com.android.tools.idea.compose.preview.actions.ToggleAutoBuildAction
import com.android.tools.idea.compose.preview.util.ComposeAdapterLightVirtualFile
import com.android.tools.idea.compose.preview.util.FilePreviewElementFinder
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.compose.preview.util.isKotlinFileType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationProvider
import com.android.tools.idea.uibuilder.type.LayoutEditorFileType
import com.google.wireless.android.sdk.stats.LayoutEditorState
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

/**
 * [ToolbarActionGroups] that includes the [ForceCompileAndRefreshAction]
 */
private class ComposePreviewToolbar(private val surface: DesignSurface) :
  ToolbarActionGroups(surface) {

  override fun getNorthGroup(): ActionGroup = DefaultActionGroup(
    listOfNotNull(
      GroupSwitchAction(),
      if (StudioFlags.COMPOSE_PREVIEW_AUTO_BUILD.get()) ToggleAutoBuildAction() else null,
      ForceCompileAndRefreshAction(surface),
      if (StudioFlags.COMPOSE_DEBUG_BOUNDS.get()) ShowDebugBoundaries() else null
    )
  )

  override fun getNorthEastGroup(): ActionGroup = DefaultActionGroup().apply {
    addAll(getZoomActionsWithShortcuts(surface, this@ComposePreviewToolbar))
    add(IssueNotificationAction.getInstance())
  }
}

/**
 * A [PreviewRepresentationProvider] coupled with [ComposePreviewRepresentation].
 */
class ComposePreviewRepresentationProvider(
  private val filePreviewElementProvider: () -> FilePreviewElementFinder = ::defaultFilePreviewElementFinder
) : PreviewRepresentationProvider {
  private val LOG = Logger.getInstance(ComposePreviewRepresentationProvider::class.java)

  private object ComposeEditorFileType : LayoutEditorFileType() {
    override fun getLayoutEditorStateType() = LayoutEditorState.Type.COMPOSE

    override fun isResourceTypeOf(file: PsiFile): Boolean =
      file.virtualFile is ComposeAdapterLightVirtualFile

    override fun getToolbarActionGroups(surface: DesignSurface): ToolbarActionGroups =
      ComposePreviewToolbar(surface)

    override fun getSelectionContextToolbar(surface: DesignSurface, selection: List<NlComponent>): DefaultActionGroup =
      DefaultActionGroup()
  }

  init {
    if (StudioFlags.COMPOSE_PREVIEW.get()) {
      DesignerTypeRegistrar.register(ComposeEditorFileType)
    }
  }

  /**
   * Checks if the input [virtualFile] contains compose previews and therefore can be provided with the [PreviewRepresentation] of them.
   */
  override fun accept(project: Project, virtualFile: VirtualFile): Boolean {
    if (!StudioFlags.COMPOSE_PREVIEW.get() || !virtualFile.isKotlinFileType()) {
      return false
    }

    val hasPreviewMethods = filePreviewElementProvider().hasPreviewMethods(project, virtualFile)
    if (LOG.isDebugEnabled) {
      LOG.debug("${virtualFile.path} hasPreviewMethods=${hasPreviewMethods}")
    }

    return hasPreviewMethods
  }

  /**
   * Creates a [ComposePreviewRepresentation] for the input [psiFile].
   */
  override fun createRepresentation(psiFile: PsiFile): ComposePreviewRepresentation {
    val previewProvider = object : PreviewElementProvider {
      override val previewElements: Sequence<PreviewElement>
        get() = if (DumbService.isDumb(psiFile.project))
          emptySequence()
        else
          try {
            filePreviewElementProvider().findPreviewMethods(psiFile.project, psiFile.virtualFile)
          }
          catch (_: IndexNotReadyException) {
            emptySequence<PreviewElement>()
          }
    }
    return ComposePreviewRepresentation(psiFile, previewProvider)
  }

  override val displayName = message("representation.name")

}