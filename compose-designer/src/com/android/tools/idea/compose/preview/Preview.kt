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

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.common.actions.IssueNotificationAction
import com.android.tools.idea.common.editor.ActionsToolbar
import com.android.tools.idea.common.editor.DesignFileEditor
import com.android.tools.idea.common.editor.SeamlessTextEditorWithPreview
import com.android.tools.idea.common.editor.SmartAutoRefresher
import com.android.tools.idea.common.editor.SmartRefreshable
import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.error.IssuePanelSplitter
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.compose.preview.ComposePreviewToolbar.ForceCompileAndRefreshAction
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.util.StopWatch
import com.android.tools.idea.rendering.RefreshRenderAction.clearCacheAndRefreshSurface
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.SceneMode
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.EditorNotifications
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.KotlinFileType
import java.awt.BorderLayout
import java.util.concurrent.CompletableFuture
import javax.swing.JPanel

/** Preview element name */
const val PREVIEW_NAME = "Preview"

/** Package containing the preview definitions */
const val PREVIEW_PACKAGE = "com.android.tools.preview"

/** Only composables with this annotation will be rendered to the surface */
const val PREVIEW_ANNOTATION_FQN = "$PREVIEW_PACKAGE.$PREVIEW_NAME"

const val COMPOSABLE_ANNOTATION_FQN = "androidx.compose.Composable"

/** View included in the runtime library that will wrap the @Composable element so it gets rendered by layoutlib */
const val COMPOSE_VIEW_ADAPTER = "$PREVIEW_PACKAGE.ComposeViewAdapter"

/** [COMPOSE_VIEW_ADAPTER] view attribute containing the FQN of the @Composable name to call */
const val COMPOSABLE_NAME_ATTR = "tools:composableName"

/**
 * Transforms a dimension given on the [PreviewConfiguration] into the string value. If the dimension is [UNDEFINED_DIMENSION], the value
 * is converted to `wrap_content`. Otherwise, the value is returned concatenated with `dp`.
 */
private fun dimensionToString(dimension: Int) = if (dimension == UNDEFINED_DIMENSION) {
  "wrap_content"
}
else {
  "${dimension}dp"
}

/**
 * Generates the XML string wrapper for one [PreviewElement]
 */
private fun PreviewElement.toPreviewXmlString() =
  """
      <$COMPOSE_VIEW_ADAPTER xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:tools="http://schemas.android.com/tools"
        android:layout_width="${dimensionToString(configuration.width)}"
        android:layout_height="${dimensionToString(configuration.height)}"
        android:padding="5dp"
        $COMPOSABLE_NAME_ATTR="$composableMethodFqn"/>
  """.trimIndent()

val FAKE_LAYOUT_RES_DIR = LightVirtualFile("layout")

/**
 * A [LightVirtualFile] defined to allow quickly identifying the given file as an XML that is used as adapter
 * to be able to preview composable methods.
 * The contents of the file only reside in memory and contain some XML that will be passed to Layoutlib.
 */
private class ComposeAdapterLightVirtualFile(name: String, content: String) : LightVirtualFile(name, content) {
  override fun getParent() = FAKE_LAYOUT_RES_DIR
}

/**
 * Interface that provides access to the Compose Preview logic.
 */
interface ComposePreviewManager {
  /**
   * Returns whether the preview needs a re-build to work. We detect this by looking into the rendering errors
   * and checking if there are any classes missing.
   */
  fun needsBuild(): Boolean

  /**
   * Requests a preview refresh
   */
  fun refresh()
}

/**
 * A [FileEditor] that displays a preview of composable elements defined in the given [psiFile].
 *
 * The editor will display previews for all declared `@Composable` methods that also use the `@Preview` (see [PREVIEW_ANNOTATION_FQN])
 * annotation.
 * For every preview element a small XML is generated that allows Layoutlib to render a `@Composable` method.
 *
 * @param psiFile [PsiFile] pointing to the Kotlin source containing the code to preview.
 * @param previewProvider call to obtain the [PreviewElement]s from the file.
 */
private class PreviewEditor(private val psiFile: PsiFile,
                    private val previewProvider: () -> List<PreviewElement>) : ComposePreviewManager, SmartRefreshable, DesignFileEditor(psiFile.virtualFile!!) {
    private val LOG = Logger.getInstance(PreviewEditor::class.java)
  private val project = psiFile.project

  private val surface = NlDesignSurface.builder(project, this)
    .setIsPreview(true)
    .showModelNames()
    .setSceneManagerProvider { surface, model ->
      NlDesignSurface.defaultSceneManagerProvider(surface, model).apply {
        enableTransparentRendering()
        enableShrinkRendering()
      }
    }
    .build()
    .apply {
      setScreenMode(SceneMode.SCREEN_COMPOSE_ONLY, true)
    }

  /**
   * List of [PreviewElement] being rendered by this editor
   */
  var previewElements: List<PreviewElement> = emptyList()

  /**
   * Callback called after refresh has happened
   */
  var onRefresh: (() -> Unit)? = null

  /**
   * [WorkBench] used to contain all the preview elements.
   */
  override val workbench = WorkBench<DesignSurface>(project, "Compose Preview", this).apply {
    isOpaque = true

    val actionsToolbar = ActionsToolbar(this@PreviewEditor, surface)
    val surfacePanel = JPanel(BorderLayout()).apply {
      add(actionsToolbar.toolbarComponent, BorderLayout.NORTH)
      add(surface, BorderLayout.CENTER)
    }
    val issueErrorSplitter = IssuePanelSplitter(surface, surfacePanel)

    init(issueErrorSplitter, surface, listOf())
    showLoading("Waiting for build to finish...")
  }

  /**
   * Calls refresh method on the the successful gradle build
   */
  private val refresher = SmartAutoRefresher(psiFile, this, workbench)

  override fun needsBuild(): Boolean = surface.models.asSequence()
    .mapNotNull { surface.getSceneManager(it) }
    .filterIsInstance<LayoutlibSceneManager>()
    .mapNotNull { it.renderResult?.logger?.brokenClasses?.values }
    .flatten()
    .any {
      it is ReflectiveOperationException && it.stackTrace.any { ex -> COMPOSE_VIEW_ADAPTER == ex.className }
    }

  /**
   * Refresh the preview surfaces. This will retrieve all the Preview annotations and render those elements.
   */
  override fun refresh() {
    val filePreviewElements = previewProvider()

    if (filePreviewElements == previewElements) {
      // There are not elements, skip model creation
      clearCacheAndRefreshSurface(surface)
      return
    }

    val stopwatch = if (LOG.isDebugEnabled) StopWatch() else null
    previewElements = filePreviewElements
    val newModels = previewElements
      .onEach {
        if (LOG.isDebugEnabled) {
          LOG.debug("""Preview found at ${stopwatch?.duration?.toMillis()}ms

              ${it.toPreviewXmlString()}
          """.trimIndent())
        }
      }
      .map { Pair(it, ComposeAdapterLightVirtualFile("testFile.xml", it.toPreviewXmlString())) }
      .map {
        val (previewElement, file) = it
        val facet = AndroidFacet.getInstance(psiFile)!!
        val configurationManager = ConfigurationManager.getOrCreateInstance(facet)
        val configuration = Configuration.create(configurationManager, null, FolderConfiguration.createDefault())
        val model = NlModel.create(this@PreviewEditor,
                                   previewElement.displayName,
                                   facet,
                                   file,
                                   configuration,
                                   surface.componentRegistrar)

        Pair(previewElement, model)
      }
      .map {
        val (previewElement, model) = it

        previewElement.configuration.applyTo(model.configuration)

        model
      }
      .toList()

    if (newModels.isEmpty()) {
      workbench.loadingStopped("No previews defined")
    }

    // All models are now ready, remove the old ones and add the new ones
    surface.models.forEach { surface.removeModel(it) }
    val renders = newModels.map { surface.addModel(it) }

    CompletableFuture.allOf(*(renders.toTypedArray()))
      .whenComplete { _, ex ->
        if (LOG.isDebugEnabled) {
          LOG.debug("Render completed in ${stopwatch?.duration?.toMillis()}ms")

          // Log any rendering errors
          surface.models.asSequence()
            .mapNotNull { surface.getSceneManager(it) }
            .filterIsInstance<LayoutlibSceneManager>()
            .forEach {
              val modelName = it.model.modelDisplayName
              it.renderResult?.let { result ->
                val logger = result.logger
                LOG.debug("""modelName="$modelName" result
                  | ${result}
                  | hasErrors=${logger.hasErrors()}
                  | missingClasses=${logger.missingClasses}
                  | messages=${logger.messages}
                  | exceptions=${logger.brokenClasses.values + logger.classesWithIncorrectFormat.values}
                """.trimMargin())
              }
            }
        }

        if (needsBuild()) {
          LOG.debug("needsBuild")
          workbench.loadingStopped("Some classes could not be found")
        }
        else {
          LOG.debug("hideLoading")
          workbench.hideLoading()
        }
        if (ex != null) {
          LOG.warn(ex)
        }

        // Make sure all notifications are cleared-up
        EditorNotifications.getInstance(project).updateNotifications(file)

        onRefresh?.invoke()
      }

  }

  override fun getName(): String = "Compose Preview"
}

/**
 * Extension method that returns if the file is a Kotlin file. This method first checks for the extension to fail fast without having to
 * actually trigger the potentially costly [VirtualFile#fileType] call.
 */
private fun VirtualFile.isKotlinFileType(): Boolean =
  extension == KotlinFileType.INSTANCE.defaultExtension && fileType == KotlinFileType.INSTANCE

/**
 * [ToolbarActionGroups] that includes the [ForceCompileAndRefreshAction]
 */
private class ComposePreviewToolbar(private val surface: DesignSurface) : ToolbarActionGroups(surface) {
  /**
   * [AnAction] that triggers a compilation of the current module. The build will automatically trigger a refresh
   * of the surface.
   */
  private inner class ForceCompileAndRefreshAction :
    AnAction("Build & Refresh", null, AllIcons.Actions.ForceRefresh) {
    override fun actionPerformed(e: AnActionEvent) {
      val module = surface.model?.module ?: return
      requestBuild(surface.project, module)
    }
  }

  override fun getNorthGroup(): ActionGroup = DefaultActionGroup(listOf(
    ForceCompileAndRefreshAction()
  ))

  override fun getNorthEastGroup(): ActionGroup = DefaultActionGroup().apply {
    addAll(getZoomActionsWithShortcuts(surface, this@ComposePreviewToolbar))
    add(IssueNotificationAction(surface))
  }
}

private class ComposeTextEditorWithPreview constructor(editor: TextEditor, val preview: PreviewEditor) :
  SeamlessTextEditorWithPreview(editor, preview, "Compose Editor")

/**
 * Returns the Compose [PreviewEditor] or null if this [FileEditor] is not a Compose preview.
 */
fun FileEditor.getComposePreviewManager(): ComposePreviewManager? = (this as? ComposeTextEditorWithPreview)?.preview

/**
 * Provider for Compose Preview editors.
 */
class ComposeFileEditorProvider : FileEditorProvider, DumbAware {
  private val LOG = Logger.getInstance(ComposeFileEditorProvider::class.java)
  private val previewElemementProvider = AnnotationPreviewElementFinder

  init {
    if (StudioFlags.COMPOSE_PREVIEW.get()) {
      DesignerTypeRegistrar.register(object : DesignerEditorFileType {
        override fun isResourceTypeOf(file: PsiFile): Boolean =
          file.virtualFile is ComposeAdapterLightVirtualFile

        override fun getToolbarActionGroups(surface: DesignSurface): ToolbarActionGroups =
          ComposePreviewToolbar(surface)
      })
    }
  }

  override fun accept(project: Project, file: VirtualFile): Boolean {
    if (!StudioFlags.COMPOSE_PREVIEW.get() || !file.isKotlinFileType()) {
      return false
    }

    val hasPreviewMethods = previewElemementProvider.hasPreviewMethods(project, file)
    if (LOG.isDebugEnabled) {
      LOG.debug("${file.path} hasPreviewMethods=${hasPreviewMethods}")
    }

    return hasPreviewMethods
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    if (LOG.isDebugEnabled) {
      LOG.debug("createEditor file=${file.path}")
    }
    val psiFile = PsiManager.getInstance(project).findFile(file)!!
    val textEditor = getInstance().createEditor(project, file) as TextEditor
    val previewEditor = PreviewEditor(psiFile = psiFile, previewProvider = { previewElemementProvider.findPreviewMethods(project, file) })
    val composeEditorWithPreview = ComposeTextEditorWithPreview(textEditor, previewEditor)

    // Queue to avoid refreshing notifications on every key stroke
    val modificationQueue = MergingUpdateQueue("Notifications Update queue",
                                               100,
                                               true,
                                               null,
                                               composeEditorWithPreview)
      .apply {
        setRestartTimerOnAdd(true)
      }

    // Update that triggers a preview refresh. It does not trigger a recompile.
    val refreshPreview = object : Update("refreshPreview") {
      override fun run() {
        LOG.debug("refreshPreview requested")
        previewEditor.refresh()
      }
    }

    val updateNotifications = object : Update("updateNotifications") {
      override fun run() {
        LOG.debug("updateNotifications requested")
        if (composeEditorWithPreview.isModified) {
          EditorNotifications.getInstance(project).updateNotifications(file)
        }
      }
    }

    previewEditor.onRefresh = {
      composeEditorWithPreview.isPureTextEditor = previewEditor.previewElements.isEmpty()
    }

    PsiDocumentManager.getInstance(project).getDocument(psiFile)!!.addDocumentListener(object: DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        if (event.isWholeTextReplaced) {
          modificationQueue.queue(refreshPreview)
          return
        }

        val currentPreviewElements = previewEditor.previewElements
        // Check if the change was done to the code block
        val isCodeChange = currentPreviewElements.mapNotNull {
          TextRange.create(it.previewBodyPsi?.range ?: return@mapNotNull null)
        }.any {
          it.contains(event.offset)
        }

        if (isCodeChange) {
          // Source code was changed, trigger notification update
          modificationQueue.queue(updateNotifications)
        }
        else {
          // The code has not changed so check if the preview definitions have changed
          modificationQueue.queue(refreshPreview)
        }
      }
    }, composeEditorWithPreview)

    return composeEditorWithPreview
  }

  override fun getEditorTypeId() = "ComposeEditor"

  override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}