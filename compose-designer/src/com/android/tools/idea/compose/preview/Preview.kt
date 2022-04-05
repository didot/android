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

import com.android.annotations.concurrency.GuardedBy
import com.android.ide.common.rendering.api.Bridge
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.render
import com.android.tools.idea.common.surface.handleLayoutlibNativeCrash
import com.android.tools.idea.common.util.ControllableTicker
import com.android.tools.idea.compose.preview.PreviewGroup.Companion.ALL_PREVIEW_GROUP
import com.android.tools.idea.compose.preview.actions.ForceCompileAndRefreshAction
import com.android.tools.idea.compose.preview.actions.PinAllPreviewElementsAction
import com.android.tools.idea.compose.preview.actions.UnpinAllPreviewElementsAction
import com.android.tools.idea.compose.preview.analytics.InteractivePreviewUsageTracker
import com.android.tools.idea.compose.preview.animation.ComposePreviewAnimationManager
import com.android.tools.idea.compose.preview.designinfo.hasDesignInfoProviders
import com.android.tools.idea.compose.preview.fast.CompilationResult
import com.android.tools.idea.compose.preview.fast.FastPreviewManager
import com.android.tools.idea.compose.preview.fast.FastPreviewSurface
import com.android.tools.idea.compose.preview.fast.fastCompile
import com.android.tools.idea.compose.preview.literals.LiveLiteralsPsiFileSnapshotFilter
import com.android.tools.idea.compose.preview.navigation.PreviewNavigationHandler
import com.android.tools.idea.compose.preview.util.CodeOutOfDateTracker
import com.android.tools.idea.compose.preview.util.FpsCalculator
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.compose.preview.util.PreviewElementInstance
import com.android.tools.idea.compose.preview.util.containsOffset
import com.android.tools.idea.compose.preview.util.invalidateCompositions
import com.android.tools.idea.compose.preview.util.isComposeErrorResult
import com.android.tools.idea.compose.preview.util.layoutlibSceneManagers
import com.android.tools.idea.compose.preview.util.sortByDisplayAndSourcePosition
import com.android.tools.idea.compose.preview.util.toDisplayString
import com.android.tools.idea.concurrency.AndroidCoroutinesAware
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.concurrency.disposableCallbackFlow
import com.android.tools.idea.concurrency.launchWithProgress
import com.android.tools.idea.concurrency.smartModeFlow
import com.android.tools.idea.editors.documentChangeFlow
import com.android.tools.idea.editors.literals.LiveLiteralsMonitorHandler
import com.android.tools.idea.editors.literals.LiveLiteralsService
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.log.LoggerWithFixedInfo
import com.android.tools.idea.projectsystem.BuildListener
import com.android.tools.idea.projectsystem.setupBuildListener
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.rendering.classloading.CooperativeInterruptTransform
import com.android.tools.idea.rendering.classloading.HasLiveLiteralsTransform
import com.android.tools.idea.rendering.classloading.LiveLiteralsTransform
import com.android.tools.idea.rendering.classloading.toClassTransform
import com.android.tools.idea.uibuilder.actions.LayoutManagerSwitcher
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationState
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.executeCallbacks
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.intellij.ide.ActivityTracker
import com.intellij.ide.PowerSaveMode
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.ui.EditorNotifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.util.module
import java.awt.Color
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import javax.swing.JComponent
import kotlin.concurrent.withLock
import kotlin.properties.Delegates

/**
 * Background color for the surface while "Interactive" is enabled.
 */
private val INTERACTIVE_BACKGROUND_COLOR = MEUI.ourInteractiveBackgroundColor

/**
 * [Notification] group ID. Must match the `groupNotification` entry of `compose-designer.xml`.
 */
val PREVIEW_NOTIFICATION_GROUP_ID = "Compose Preview Notification"

/**
 * [NlModel] associated preview data
 * @param project the [Project] used by the current view.
 * @param composePreviewManager [ComposePreviewManager] of the Preview.
 * @param previewElement the [PreviewElement] associated to this model
 */
private class PreviewElementDataContext(private val project: Project,
                                        private val composePreviewManager: ComposePreviewManager,
                                        private val previewElement: PreviewElement) : DataContext {
  override fun getData(dataId: String): Any? = when (dataId) {
    COMPOSE_PREVIEW_MANAGER.name -> composePreviewManager
    COMPOSE_PREVIEW_ELEMENT.name -> previewElement
    CommonDataKeys.PROJECT.name -> project
    else -> null
  }
}

/**
 * Returns true if change of values of any [LayoutlibSceneManager] properties would require necessary re-inflation. Namely, if we change
 * [LayoutlibSceneManager.isShowingDecorations], [LayoutlibSceneManager.isUsePrivateClassLoader] or if we transition from interactive to
 * static preview mode (not the other way around though) we need to re-inflate in order to update the preview layout.
 */
fun LayoutlibSceneManager.changeRequiresReinflate(showDecorations: Boolean, isInteractive: Boolean, usePrivateClassLoader: Boolean) =
  (showDecorations != isShowingDecorations) ||
  (interactive && !isInteractive) || // transition from interactive to static
  (usePrivateClassLoader != isUsePrivateClassLoader)


/**
 * Sets up the given [sceneManager] with the right values to work on the Compose Preview. Currently, this
 * will configure if the preview elements will be displayed with "full device size" or simply containing the
 * previewed components (shrink mode).
 * @param showDecorations when true, the rendered content will be shown with the full device size specified in
 * the device configuration and with the frame decorations.
 * @param isInteractive whether the scene displays an interactive preview.
 * @param requestPrivateClassLoader whether the scene manager should use a private ClassLoader.
 * @param isLiveLiteralsEnabled if true, the classes will be instrumented with live literals support.
 * @param onLiveLiteralsFound callback called when the classes have compiler live literals support. This callback will only be called if
 *  [isLiveLiteralsEnabled] is false. If true, the classes are assumed to have this support.
 * @param resetLiveLiteralsFound callback called when the classes are about to be reloaded so the live literals state can be discarded.
 */
@VisibleForTesting
fun configureLayoutlibSceneManager(sceneManager: LayoutlibSceneManager,
                                   showDecorations: Boolean,
                                   isInteractive: Boolean,
                                   requestPrivateClassLoader: Boolean,
                                   isLiveLiteralsEnabled: Boolean,
                                   onLiveLiteralsFound: () -> Unit,
                                   resetLiveLiteralsFound: () -> Unit): LayoutlibSceneManager =
  sceneManager.apply {
    val reinflate = changeRequiresReinflate(showDecorations, isInteractive, requestPrivateClassLoader)
    setTransparentRendering(!showDecorations)
    setShrinkRendering(!showDecorations)
    interactive = isInteractive
    isUsePrivateClassLoader = requestPrivateClassLoader
    setOnNewClassLoader(resetLiveLiteralsFound)
    if (isLiveLiteralsEnabled) {
      setProjectClassesTransform(
        toClassTransform(
          { if (StudioFlags.COMPOSE_PREVIEW_INTERRUPTIBLE.get()) CooperativeInterruptTransform(it) else it },
          { LiveLiteralsTransform(it) }
        ))
    }
    else {
      setProjectClassesTransform(
        toClassTransform(
          { if (StudioFlags.COMPOSE_PREVIEW_INTERRUPTIBLE.get()) CooperativeInterruptTransform(it) else it },
          // Live literals is not enabled but we pass the [HasLiveLiteralsTransform] to identify if the current project
          // has live literals enabled.
          { HasLiveLiteralsTransform(it, onLiveLiteralsFound = onLiveLiteralsFound) }
        )
      )
    }
    setQuality(if (PreviewPowerSaveManager.isInPowerSaveMode) 0.5f else 0.7f)
    setShowDecorations(showDecorations)
    // The Compose Preview has its own way to track out of date files so we ask the Layoutlib Scene Manager to not
    // report it via the regular log.
    doNotReportOutOfDateUserClasses()
    if (reinflate) {
      forceReinflate()
    }
  }

/**
 * Key for the persistent group state for the Compose Preview.
 */
private const val SELECTED_GROUP_KEY = "selectedGroup"

/**
 * Key for persisting the selected layout manager.
 */
private const val LAYOUT_KEY = "previewLayout"

/**
 * A [PreviewRepresentation] that provides a compose elements preview representation of the given `psiFile`.
 *
 * A [component] is implied to display previews for all declared `@Composable` functions that also use the `@Preview` (see
 * [com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_FQN]) annotation.
 * For every preview element a small XML is generated that allows Layoutlib to render a `@Composable` functions.
 *
 * @param psiFile [PsiFile] pointing to the Kotlin source containing the code to preview.
 * @param previewProvider [PreviewElementProvider] to obtain the [PreviewElement]s.
 * @param preferredInitialVisibility preferred [PreferredVisibility] for this representation.
 */
class ComposePreviewRepresentation(psiFile: PsiFile,
                                   previewProvider: PreviewElementProvider<PreviewElement>,
                                   override val preferredInitialVisibility: PreferredVisibility,
                                   composePreviewViewProvider: ComposePreviewViewProvider) :
  PreviewRepresentation, ComposePreviewManagerEx, UserDataHolderEx by UserDataHolderBase(), AndroidCoroutinesAware,
  FastPreviewSurface {
  /**
   * Fake device id to identify this preview with the live literals service. This allows live literals to track how
   * many "users" it has.
   */
  private val previewDeviceId = "Preview#${UUID.randomUUID()}"
  private val LOG = Logger.getInstance(ComposePreviewRepresentation::class.java)
  private val project = psiFile.project
  private val module = runReadAction { psiFile.module }
  private val psiFilePointer = runReadAction { SmartPointerManager.createPointer(psiFile) }

  private val projectBuildStatusManager = ProjectBuildStatusManager.create(this, psiFile, LiveLiteralsPsiFileSnapshotFilter(this, psiFile))

  /**
   * Frames per second limit for interactive preview.
   */
  private var fpsLimit = StudioFlags.COMPOSE_INTERACTIVE_FPS_LIMIT.get()

  init {
    val project = psiFile.project
    project.messageBus.connect(this).subscribe(PowerSaveMode.TOPIC, PowerSaveMode.Listener {
      fpsLimit = if (PreviewPowerSaveManager.isInPowerSaveMode) {
        StudioFlags.COMPOSE_INTERACTIVE_FPS_LIMIT.get() / 3
      }
      else {
        StudioFlags.COMPOSE_INTERACTIVE_FPS_LIMIT.get()
      }
      fpsCounter.resetAndStart()

      // When getting out of power save mode, request a refresh
      if (!PreviewPowerSaveManager.isInPowerSaveMode) requestRefresh()
    })
  }

  /**
   * Whether the preview needs a full refresh or not.
   */
  private val invalidated = AtomicBoolean(true)

  private val refreshFlow: MutableSharedFlow<RefreshRequest> = MutableSharedFlow(replay = 1)

  private val previewFreshnessTracker = CodeOutOfDateTracker.create(module, this) {
    invalidate()
    requestRefresh()
  }

  /**
   * [PreviewElementProvider] containing the pinned previews.
   */
  private val memoizedPinnedPreviewProvider = FilteredPreviewElementProvider(
    PinnedPreviewElementManager.getPreviewElementProvider(project)) {
    !(it.containingFile?.isEquivalentTo(psiFilePointer.containingFile) ?: false)
  }

  /**
   * [PreviewElementProvider] used to save the result of a call to `previewProvider`. Calls to `previewProvider` can potentially
   * be slow. This saves the last result and it is refreshed on demand when we know is not running on the UI thread.
   */
  private val memoizedElementsProvider = MemoizedPreviewElementProvider(previewProvider, previewFreshnessTracker)
  private val previewElementProvider = PreviewFilters(memoizedElementsProvider)

  override var groupFilter: PreviewGroup by Delegates.observable(ALL_PREVIEW_GROUP) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      LOG.debug("New group preview element selection: $newValue")
      previewElementProvider.groupNameFilter = newValue
      // Force refresh to ensure the new preview elements are picked up
      invalidate()
      requestRefresh()
    }
  }

  @Volatile
  override var availableGroups: Set<PreviewGroup> = emptySet()

  @Volatile
  private var interactiveMode = ComposePreviewManager.InteractiveMode.DISABLED
  private val navigationHandler = PreviewNavigationHandler()

  private val fpsCounter = FpsCalculator { System.nanoTime() }

  override val interactivePreviewElementInstance: PreviewElementInstance?
    get() = previewElementProvider.instanceFilter

  override suspend fun startInteractivePreview(element: PreviewElementInstance) {
    if (interactiveMode.isStartingOrReady()) return
    LOG.debug("New single preview element focus: $element")
    val isFromAnimationInspection = animationInspection.get()
    // The order matters because we first want to change the composable being previewed and then start interactive loop when enabled
    // but we want to stop the loop first and then change the composable when disabled
    if (isFromAnimationInspection) {
      onAnimationInspectionStop()
    }
    else {
      EditorNotifications.getInstance(project).updateNotifications(psiFilePointer.virtualFile!!)
    }
    interactiveMode = ComposePreviewManager.InteractiveMode.STARTING
    val quickRefresh = shouldQuickRefresh() && !isFromAnimationInspection // We should call this before assigning newValue to instanceIdFilter
    val peerPreviews = previewElementProvider.previewElements().count()
    previewElementProvider.instanceFilter = element
    composeWorkBench.hasComponentsOverlay = false
    val startUpStart = System.currentTimeMillis()
    forceRefresh(quickRefresh)?.invokeOnCompletion {
      surface.layoutlibSceneManagers.forEach { it.resetTouchEventsCounter() }
      if (!isFromAnimationInspection) { // Currently it will re-create classloader and will be slower that switch from static
        InteractivePreviewUsageTracker.getInstance(surface).logStartupTime(
          (System.currentTimeMillis() - startUpStart).toInt(), peerPreviews)
      }
      fpsCounter.resetAndStart()
      ticker.start()
      composeWorkBench.isInteractive = true

      if (StudioFlags.COMPOSE_ANIMATED_PREVIEW_SHOW_CLICK.get()) {
        // While in interactive mode, display a small ripple when clicking
        surface.enableMouseClickDisplay()
      }
      surface.background = INTERACTIVE_BACKGROUND_COLOR
      interactiveMode = ComposePreviewManager.InteractiveMode.READY
      ActivityTracker.getInstance().inc()
    }
  }

  override fun stopInteractivePreview() {
    if (interactiveMode.isStoppingOrDisabled()) return

    LOG.debug("Stopping interactive")
    onInteractivePreviewStop()
    EditorNotifications.getInstance(project).updateNotifications(psiFilePointer.virtualFile!!)
    onStaticPreviewStart()
    forceRefresh()?.invokeOnCompletion {
      interactiveMode = ComposePreviewManager.InteractiveMode.DISABLED
    }
  }

  private fun onStaticPreviewStart() {
    composeWorkBench.hasComponentsOverlay = true
    surface.background = defaultSurfaceBackground
  }

  private fun onInteractivePreviewStop() {
    interactiveMode = ComposePreviewManager.InteractiveMode.STOPPING
    surface.disableMouseClickDisplay()
    composeWorkBench.isInteractive = false
    ticker.stop()
    previewElementProvider.clearInstanceIdFilter()
    logInteractiveSessionMetrics()
  }

  private fun pauseInteractivePreview() {
    ticker.stop()
    surface.layoutlibSceneManagers.forEach { it.pauseSessionClock() }
  }

  private fun resumeInteractivePreview() {
    fpsCounter.resetAndStart()
    surface.layoutlibSceneManagers.forEach { it.resumeSessionClock() }
    ticker.start()
  }

  private val animationInspection = AtomicBoolean(false)

  override var animationInspectionPreviewElementInstance: PreviewElementInstance?
    set(value) {
      if ((!animationInspection.get() && value != null) || (animationInspection.get() && value == null)) {
        if (value != null) {
          if (interactiveMode != ComposePreviewManager.InteractiveMode.DISABLED) {
            onInteractivePreviewStop()
          }
          LOG.debug("Animation Preview open for preview: $value")
          ComposePreviewAnimationManager.onAnimationInspectorOpened()
          previewElementProvider.instanceFilter = value
          animationInspection.set(true)
          composeWorkBench.hasComponentsOverlay = false
          composeWorkBench.isAnimationPreview = true

          // Open the animation inspection panel
          composeWorkBench.bottomPanel = ComposePreviewAnimationManager.createAnimationInspectorPanel(surface, this) {
            // Close this inspection panel, making all the necessary UI changes (e.g. changing background and refreshing the preview) before
            // opening a new one.
            animationInspectionPreviewElementInstance = null
          }.component
          surface.background = INTERACTIVE_BACKGROUND_COLOR
        }
        else {
          onAnimationInspectionStop()
          onStaticPreviewStart()
        }
        forceRefresh()?.invokeOnCompletion {
          interactiveMode = ComposePreviewManager.InteractiveMode.DISABLED
          ActivityTracker.getInstance().inc()
        }
      }
    }
    get() = if (animationInspection.get()) previewElementProvider.instanceFilter else null

  private fun onAnimationInspectionStop() {
    animationInspection.set(false)
    // Close the animation inspection panel
    ComposePreviewAnimationManager.closeCurrentInspector()
    // Swap the components back
    composeWorkBench.bottomPanel = null
    composeWorkBench.isAnimationPreview = false
    previewElementProvider.instanceFilter = null
  }

  /**
   * Counter used to generate unique push ids.
   */
  private val pushIdCounter = AtomicLong()
  private val liveLiteralsManager = LiveLiteralsService.getInstance(project)

  override var hasLiveLiterals: Boolean = false
    private set(value) {
      field = value
      if (value) LiveLiteralsService.getInstance(project).liveLiteralsMonitorStarted(previewDeviceId,
                                                                                     LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    }

  override val isLiveLiteralsEnabled: Boolean
    get() = liveLiteralsManager.isEnabled

  override val hasDesignInfoProviders: Boolean
    get() = module?.let { hasDesignInfoProviders(it) } ?: false

  override var showDebugBoundaries: Boolean = false
    set(value) {
      field = value
      invalidate()
      requestRefresh()
    }

  override val previewedFile: PsiFile?
    get() = psiFilePointer.element

  private val composeWorkBench: ComposePreviewView = invokeAndWaitIfNeeded {
    composePreviewViewProvider.invoke(
      project,
      psiFilePointer,
      projectBuildStatusManager,
      navigationHandler, {
        return@invoke when (it) {
          COMPOSE_PREVIEW_MANAGER.name -> this@ComposePreviewRepresentation
          // The Compose preview NlModels do not point to the actual file but to a synthetic file
          // generated for Layoutlib. This ensures we return the right file.
          CommonDataKeys.VIRTUAL_FILE.name -> psiFilePointer.virtualFile
          CommonDataKeys.PROJECT.name -> project
          else -> null
        }
      }, this,
      PinAllPreviewElementsAction(
        {
          PinnedPreviewElementManager.getInstance(project).isPinned(psiFile)
        }, previewElementProvider),
      UnpinAllPreviewElementsAction)
  }

  private val pinnedSurface: NlDesignSurface
    get() = composeWorkBench.pinnedSurface
  private val surface: NlDesignSurface
    get() = composeWorkBench.mainSurface

  /**
   * Default background used by the surface. This is used to restore the state after disabling the interactive preview.
   */
  private val defaultSurfaceBackground: Color = surface.background

  /**
   * List of [PreviewElement] being rendered by this editor
   */
  private var renderedElements: List<PreviewElement> = emptyList()

  /**
   * Counts the current number of simultaneous executions of [refresh] method. Being inside the [refresh] indicates that the this preview
   * is being refreshed. Even though [requestRefresh] guarantees that only at most a single refresh happens at any point in time,
   * there might be several simultaneous calls to [refresh] method and therefore we need a counter instead of boolean flag.
   */
  private val refreshCallsCount = AtomicInteger(0)

  /**
   * This field will be false until the preview has rendered at least once. If the preview has not rendered once
   * we do not have enough information about errors and the rendering to show the preview. Once it has rendered,
   * even with errors, we can display additional information about the state of the preview.
   */
  private val hasRenderedAtLeastOnce = AtomicBoolean(false)

  /**
   * Callback first time after the preview has loaded the initial state and it's ready to restore
   * any saved state.
   */
  private var onRestoreState: (() -> Unit)? = null

  private val ticker = ControllableTicker({
                                            if (!RenderService.isBusy() && fpsCounter.getFps() <= fpsLimit) {
                                              fpsCounter.incrementFrameCounter()
                                              surface.layoutlibSceneManagers.firstOrNull()?.executeCallbacksAndRequestRender(null)
                                            }
                                          }, Duration.ofMillis(5))

  // region Lifecycle handling

  /**
   * [CoroutineScope] that is valid while this preview is active. The scope will be cancelled as soon as the preview becomes
   * inactive. Use this scope to launch tasks that only make sense while the preview is visible to the user.
   *
   * Certain things might make sense to run at the preview level scope and not this one. For example, the [refreshFlow] must keep listening
   * for calls that require to refresh the preview after it becomes active again.
   */
  @get:Synchronized
  @set:Synchronized
  private var activationScope: CoroutineScope? = null

  /**
   * Lock used during the [onActivate]/[onDeactivate]/[onDeactivationTimeout] to avoid activations happening in the middle.
   */
  private val activationLock = ReentrantLock()

  /**
   * Tracks whether this preview is active or not. The value tracks the [onActivate] and [onDeactivate] calls.
   */
  private val isActive = AtomicBoolean(false)

  /**
   * Tracks whether the preview has received an [onActivate] call before or not. This is used to decide whether
   * [onInit] must be called.
   */
  @GuardedBy("activationLock")
  private var isFirstActivation = true
  // endregion

  init {
    Disposer.register(this, ticker)
  }

  override val component: JComponent
    get() = composeWorkBench.component

  private data class RefreshRequest(val quickRefresh: Boolean) {
    val requestId = UUID.randomUUID().toString().substring(0, 5)
  }
  // region Lifecycle handling
  @TestOnly
  fun needsRefreshOnSuccessfulBuild() = previewFreshnessTracker.needsRefreshOnSuccessfulBuild()

  @TestOnly
  fun buildWillTriggerRefresh() = previewFreshnessTracker.buildWillTriggerRefresh()

  override fun invalidateSavedBuildStatus() {
    previewFreshnessTracker.invalidateSavedBuildStatus()
  }

  /**
   * Completes the initialization of the preview. This method is only called once after the first [onActivate]
   * happens.
   */
  private fun onInit() {
    LOG.debug("onInit")
    if (Disposer.isDisposed(this)) {
      LOG.info("Preview was closed before the initialization completed.")
    }
    val psiFile = psiFilePointer.element
    requireNotNull(psiFile) { "PsiFile was disposed before the preview initialization completed." }

    setupBuildListener(project, object : BuildListener {
      @GuardedBy("previewFreshnessLock")
      private var pendingBuildsCount = 0
      @GuardedBy("previewFreshnessLock")
      private var someConcurrentBuildFailed = false

      override fun buildSucceeded() {
        LOG.debug("buildSucceeded")
        module?.let {
          // When the build completes successfully, we do not need the overlay until a modifications has happened.
          ModuleClassLoaderOverlays.getInstance(it).overlayPath = null
        }

        val file = psiFilePointer.element
        if (file == null) {
          LOG.debug("invalid PsiFile")
          return
        }

        // If Fast Preview is enabled, prefetch the daemon for the current configuration.
        if (module != null
            && FastPreviewManager.getInstance(project).isEnabled) {
          FastPreviewManager.getInstance(project).preStartDaemon(module)
        }

        afterBuildComplete(true)
      }

      override fun buildFailed() {
        LOG.debug("buildFailed")

        afterBuildComplete(false)
      }

      override fun buildCleaned() {
        LOG.debug("buildCleaned")

        // After a clean build, we can not re-load the classes so we need to invalidate the Live Literals.
        LiveLiteralsService.getInstance(project).liveLiteralsMonitorStopped(previewDeviceId)
        buildFailed()
      }

      override fun buildStarted() {
        LOG.debug("buildStarted")

        composeWorkBench.updateProgress(message("panel.building"))
        afterBuildStarted()
      }
    }, this, allowMultipleSubscriptionsPerProject = true)

    // When the preview is opened we must trigger an initial refresh. We wait for the project to be smart and synched to do it.
    project.runWhenSmartAndSyncedOnEdt(this, {
      when (projectBuildStatusManager.status) {
        // Do not refresh if we still need to build the project. Instead, only update the empty panel and editor notifications if needed.
        ProjectStatus.NotReady, ProjectStatus.NeedsBuild -> composeWorkBench.updateVisibilityAndNotifications()
        else -> requestRefresh()
      }
    })

    FastPreviewManager.getInstance(project).addCompileListener(this, object: FastPreviewManager.Companion.CompileListener {
      override fun onCompilationStarted(files: Collection<PsiFile>) {
        psiFilePointer.element?.let { editorFile ->
          if (files.any { it.isEquivalentTo(editorFile) }) afterBuildStarted()
        }
      }

      override fun onCompilationComplete(result: CompilationResult, files: Collection<PsiFile>) {
        psiFilePointer.element?.let { editorFile ->
          if (files.any { it.isEquivalentTo(editorFile) }) afterBuildComplete(result == CompilationResult.Success)
        }
      }
    })
  }

  private fun afterBuildComplete(isSuccessful: Boolean) {
    if (isSuccessful && hasLiveLiterals) {
      LiveLiteralsService.getInstance(project).liveLiteralsMonitorStarted(previewDeviceId, LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    }

    EditorNotifications.getInstance(project).updateNotifications(psiFilePointer.virtualFile!!)
    // Force updating toolbar icons after build
    ActivityTracker.getInstance().inc()
  }

  private fun afterBuildStarted() {
    // Stop live literals monitoring for this preview. If the new build has live literals, they will
    // be re-enabled later automatically via the HasLiveLiterals check.
    LiveLiteralsService.getInstance(project).liveLiteralsMonitorStopped(previewDeviceId)
    // When building, invalidate the Animation Inspector, since the animations are now obsolete and new ones will be subscribed once
    // build is complete and refresh is triggered.
    ComposePreviewAnimationManager.invalidate()
    EditorNotifications.getInstance(project).updateNotifications(psiFilePointer.virtualFile!!)
    // Force updating toolbar icons after build
    ActivityTracker.getInstance().inc()
  }


  /**
   * Initializes the flows that will listen to different events and will call [requestRefresh].
   */
  @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
  private fun initializeFlows() = activationLock.withLock {
    activationScope?.cancel()
    with(createChildScope(true)) {
      activationScope = this
      // Launch all the listeners that are bound to the current activation.

      // Flow to collate and process requestRefresh requests.
      launch(workerThread) {
        refreshFlow.conflate().collect {
          refreshFlow.resetReplayCache() // Do not keep re-playing after we have received the element.
          refresh(it)?.join()
        }
      }

      launch(workerThread) {
        flowOf(
          // Flow handling switch to smart mode.
          smartModeFlow(project, this@ComposePreviewRepresentation, LOG),

          // Flow handling pinned elements updates.
          if (StudioFlags.COMPOSE_PIN_PREVIEW.get()) {
            disposableCallbackFlow("PinnedPreviewsFlow", LOG, this@ComposePreviewRepresentation) {
              val listener = PinnedPreviewElementManager.Listener { trySend(Unit) }
              PinnedPreviewElementManager.getInstance(project).addListener(listener)
              Disposer.register(disposable) {
                PinnedPreviewElementManager.getInstance(project).removeListener(listener)
              }
            }
          } else emptyFlow(),
        ).collectLatest {
          when (projectBuildStatusManager.status) {
            // Do not refresh if we still need to build the project. Instead, only update the empty panel and editor notifications if needed.
            ProjectStatus.NotReady, ProjectStatus.NeedsBuild -> composeWorkBench.updateVisibilityAndNotifications()
            else -> requestRefresh()
          }
        }
      }

      // Flow handling live literals updates.
      if (StudioFlags.COMPOSE_LIVE_LITERALS.get()) {
        launch(workerThread) {
          disposableCallbackFlow<Unit>("LiveLiteralsFlow", LOG, this@ComposePreviewRepresentation) {
            if (hasLiveLiterals) {
              LiveLiteralsService.getInstance(project).liveLiteralsMonitorStarted(previewDeviceId, LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
            }

            liveLiteralsManager.addOnLiteralsChangedListener(disposable) { if (isLiveLiteralsEnabled) { trySend(Unit) } }
          }.collectLatest {
            // We generate an id for the push of the new literals so it can be tracked by the metrics stats.
            val pushId = pushIdCounter.getAndIncrement().toString(16)
            activationScope?.launch {
              LiveLiteralsService.getInstance(project).liveLiteralPushStarted(previewDeviceId, pushId)
              surface.layoutlibSceneManagers.forEach { sceneManager ->
                sceneManager.invalidateCompositions(forceLayout = animationInspection.get())
                sceneManager.executeCallbacks()
                sceneManager.render()
                LiveLiteralsService.getInstance(project).liveLiteralPushed(previewDeviceId, pushId, listOf())
              }
            }
          }
        }
      }

      // Flow handling file changes.
      launch(workerThread) {
        val psiFile = psiFilePointer.element ?: return@launch
        documentChangeFlow(psiFile, this@ComposePreviewRepresentation, LOG)
          .debounce {
            // The debounce timer is smaller when running with Fast Preview so the changes are more responsive to typing.
            if (FastPreviewManager.getInstance(project).isAvailable) 250L else 1000L
          }
          .conflate()
          .collectLatest {
            if (FastPreviewManager.getInstance(project).isEnabled) {
              try {
                requestFastPreviewRefresh()
              } catch(_: Throwable) {
                // Ignore any cancellation exceptions
              }
              return@collectLatest
            }

          if (!PreviewPowerSaveManager.isInPowerSaveMode && interactiveMode.isStoppingOrDisabled() && !animationInspection.get()) requestRefresh()
        }
      }
    }
  }

  override fun onActivate() {
    activationLock.withLock {
      LOG.debug("onActivate")

      // Reset overlay every time we come back to the preview
      module?.let { ModuleClassLoaderOverlays.getInstance(it).overlayPath = null }

      initializeFlows()

      isActive.set(true)
      if (isFirstActivation) {
        isFirstActivation = false
        onInit()
      }
      else surface.activate()

      if (interactiveMode.isStartingOrReady()) {
        resumeInteractivePreview()
      }
    }
  }

  /**
   * This method will be called by [onDeactivate] after the deactivation timeout expires or the LRU queue is full.
   */
  private fun onDeactivationTimeout() {
    activationLock.withLock {
      // If the preview is still not active, deactivate the surface.
      if (!isActive.get()) {
        stopInteractivePreview()
        LOG.debug("Delayed surface deactivation")
        surface.deactivate()
      }
    }
  }

  override fun onDeactivate() {
    activationLock.withLock {
      LOG.debug("onDeactivate")
      activationScope?.cancel()
      activationScope = null
      if (interactiveMode.isStartingOrReady()) {
        pauseInteractivePreview()
      }
      isActive.set(false)
      // The editor is scheduled to be deactivated, deactivate its issue model to avoid updating publish the issue update event.
      surface.deactivateIssueModel()

      if  (PreviewPowerSaveManager.isInPowerSaveMode) {
        // When on power saving mode, deactivate immediately to free resources.
        onDeactivationTimeout()
      }
      else {
        project.getService(PreviewProjectService::class.java).deactivationQueue.addDelayedAction(this, this::onDeactivationTimeout)
      }
    }
  }
  // endregion

  override fun onCaretPositionChanged(event: CaretEvent, isModificationTriggered: Boolean) {
    if (PreviewPowerSaveManager.isInPowerSaveMode) return
    if (isModificationTriggered) return // We do not move the preview while the user is typing
    if (!StudioFlags.COMPOSE_PREVIEW_SCROLL_ON_CARET_MOVE.get()) return
    if (!isActive.get() || interactiveMode.isStartingOrReady()) return
    // If we have not changed line, ignore
    if (event.newPosition.line == event.oldPosition.line) return
    val offset = event.editor.logicalPositionToOffset(event.newPosition)

    activationScope?.launch(uiThread) {
      val filePreviewElements = withContext(workerThread) {
        memoizedElementsProvider.previewElements()
      }

      filePreviewElements.find { element ->
        element.previewBodyPsi?.psiRange.containsOffset(offset) || element.previewElementDefinitionPsi?.psiRange.containsOffset(offset)
      }?.let { selectedPreviewElement ->
        surface.models.find { it.dataContext.getData(COMPOSE_PREVIEW_ELEMENT) == selectedPreviewElement }
      }?.let {
        surface.scrollToVisible(it, true)
      }
    }
  }

  private fun logInteractiveSessionMetrics() {
    val touchEvents = surface.layoutlibSceneManagers.map { it.touchEventsCount }.sum()
    InteractivePreviewUsageTracker.getInstance(surface).logInteractiveSession(fpsCounter.getFps(), fpsCounter.getDurationMs(), touchEvents)
  }

  override fun dispose() {
    if (interactiveMode == ComposePreviewManager.InteractiveMode.READY) {
      logInteractiveSessionMetrics()
    }
    animationInspectionPreviewElementInstance = null
  }

  private var lastPinsModificationCount = -1L

  private fun hasErrorsAndNeedsBuild(): Boolean =
    renderedElements.isNotEmpty() &&
    (!hasRenderedAtLeastOnce.get() || surface.layoutlibSceneManagers.any { it.renderResult.isComposeErrorResult() })

  private fun hasSyntaxErrors(): Boolean = WolfTheProblemSolver.getInstance(project).isProblemFile(psiFilePointer.virtualFile)

  /** Cached previous [ComposePreviewManager.Status] used to trigger notifications if there's been a change. */
  private val previousStatusRef: AtomicReference<ComposePreviewManager.Status?> = AtomicReference(null)

  override fun status(): ComposePreviewManager.Status {
    val isRefreshing = (refreshCallsCount.get() > 0 ||
                        DumbService.isDumb(project) ||
                        projectBuildStatusManager.isBuilding)

    // If we are refreshing, we avoid spending time checking other conditions like errors or if the preview
    // is out of date.
    val newStatus = ComposePreviewManager.Status(
      !isRefreshing && hasErrorsAndNeedsBuild(),
      !isRefreshing && hasSyntaxErrors(),
      !isRefreshing && projectBuildStatusManager.status == ProjectStatus.OutOfDate,
      isRefreshing,
      interactiveMode)

    // This allows us to display notifications synchronized with any other change detection. The moment we detect a difference,
    // we immediately ask the editor to refresh the notifications.
    // For example, IntelliJ will periodically update the toolbar. If one of the actions checks the state and changes its UI, this will
    // allow for notifications to be refreshed at the same time.
    val previousStatus = previousStatusRef.getAndSet(newStatus)
    if (newStatus != previousStatus) {
      EditorNotifications.getInstance(project).updateNotifications(psiFilePointer.virtualFile!!)
    }

    return newStatus
  }

  /**
   * Method called when the notifications of the [PreviewRepresentation] need to be updated. This is called by the
   * [ComposePreviewNotificationProvider] when the editor needs to refresh the notifications.
   */
  override fun updateNotifications(parentEditor: FileEditor) = composeWorkBench.updateNotifications(parentEditor)

  private fun toPreviewXmlString(instance: PreviewElementInstance): String =
    instance.toPreviewXml()
      // Whether to paint the debug boundaries or not
      .toolsAttribute("paintBounds", showDebugBoundaries.toString())
      .toolsAttribute("findDesignInfoProviders", hasDesignInfoProviders.toString())
      .apply {
        if (animationInspection.get()) {
          // If the animation inspection is active, start the PreviewAnimationClock with the current epoch time.
          toolsAttribute("animationClockStartTime", System.currentTimeMillis().toString())
        }
      }
      .buildString()

  private fun getPreviewDataContextForPreviewElement(previewElement: PreviewElement) =
    PreviewElementDataContext(project, this@ComposePreviewRepresentation, previewElement)

  private fun configureLayoutlibSceneManagerForPreviewElement(previewElement: PreviewElement,
                                                              layoutlibSceneManager: LayoutlibSceneManager) =
    configureLayoutlibSceneManager(layoutlibSceneManager,
                                   showDecorations = previewElement.displaySettings.showDecoration,
                                   isInteractive = interactiveMode.isStartingOrReady(),
                                   requestPrivateClassLoader = usePrivateClassLoader(),
                                   isLiveLiteralsEnabled = isLiveLiteralsEnabled,
                                   onLiveLiteralsFound = { hasLiveLiterals = true },
                                   resetLiveLiteralsFound = { hasLiveLiterals = isLiveLiteralsEnabled })

  private fun onAfterRender() {
    composeWorkBench.hasRendered = true
    hasRenderedAtLeastOnce.set(true)
  }

  /**
   * Refresh the preview surfaces. This will retrieve all the Preview annotations and render those elements. The call will block until all
   * the given [PreviewElement]s have completed rendering. If [quickRefresh] is true the preview surfaces for the same [PreviewElement]s do
   * not get reinflated, this allows to save time for e.g. static to animated preview transition. A [ProgressIndicator] that runs while
   * refresh is in progress is given, and this method should return early if the indicator is cancelled.
   */
  private suspend fun doRefreshSync(filePreviewElements: List<PreviewElement>, quickRefresh: Boolean, progressIndicator: ProgressIndicator) {
    if (LOG.isDebugEnabled) LOG.debug("doRefresh of ${filePreviewElements.count()} elements.")
    val psiFile = runReadAction {
      val element = psiFilePointer.element

      return@runReadAction if (element == null || !element.isValid) {
        LOG.warn("doRefresh with invalid PsiFile")
        null
      }
      else {
        element
      }
    } ?: return

    // Cache available groups
    availableGroups = previewElementProvider.allAvailableGroups()

    // Restore
    onRestoreState?.invoke()
    onRestoreState = null

    val arePinsEnabled = StudioFlags.COMPOSE_PIN_PREVIEW.get() && interactiveMode.isStoppingOrDisabled() && !animationInspection.get()
    val hasPinnedElements = if (arePinsEnabled) {
      memoizedPinnedPreviewProvider.previewElements().any()
    } else false

    composeWorkBench.setPinnedSurfaceVisibility(hasPinnedElements)
    val pinnedManager = PinnedPreviewElementManager.getInstance(project)
    if (hasPinnedElements) {
      pinnedSurface.updatePreviewsAndRefresh(
        false,
        memoizedPinnedPreviewProvider,
        LOG,
        psiFile,
        this,
        progressIndicator,
        this::onAfterRender,
        this::toPreviewXmlString,
        this::getPreviewDataContextForPreviewElement,
        this::configureLayoutlibSceneManagerForPreviewElement
      )
    }
    lastPinsModificationCount = pinnedManager.modificationCount
    if (progressIndicator.isCanceled) return // Return early if user has cancelled the refresh

    val showingPreviewElements = surface.updatePreviewsAndRefresh(
      quickRefresh,
      previewElementProvider,
      LOG,
      psiFile,
      this,
      progressIndicator,
      this::onAfterRender,
      this::toPreviewXmlString,
      this::getPreviewDataContextForPreviewElement,
      this::configureLayoutlibSceneManagerForPreviewElement
    )
    if (progressIndicator.isCanceled) return // Return early if user has cancelled the refresh

    if (showingPreviewElements.size >= filePreviewElements.size) {
      renderedElements = filePreviewElements
    }
    else {
      // Some preview elements did not result in model creations. This could be because of failed PreviewElements instantiation.
      // TODO(b/160300892): Add better error handling for failed instantiations.
      LOG.warn("Some preview elements have failed")
    }
  }

  fun requestRefresh(quickRefresh: Boolean = false) = launch(workerThread) {
    refreshFlow.emit(RefreshRequest(quickRefresh))
  }

  /**
   * Requests a refresh the preview surfaces. This will retrieve all the Preview annotations and render those elements.
   * The refresh will only happen if the Preview elements have changed from the last render.
   */
  private fun refresh(refreshRequest: RefreshRequest): Job? {
    val requestLogger = LoggerWithFixedInfo(LOG, mapOf("requestId" to refreshRequest.requestId))
    requestLogger.debug("Refresh triggered. quickRefresh: ${refreshRequest.quickRefresh}")
    val refreshTrigger: Throwable? = if (LOG.isDebugEnabled) Throwable() else null
    val startTime = System.nanoTime()
    // Start a progress indicator so users are aware that a long task is running. Stop it by calling processFinish() if returning early.
    val refreshProgressIndicator = BackgroundableProcessIndicator(
      project,
      message("refresh.progress.indicator.title"),
      "",
      "",
      true
    )
    if (!Disposer.tryRegister(this, refreshProgressIndicator)) return null
    // This is not launched in the activation scope to avoid cancelling the refresh mid-way when the user changes tabs.
    val refreshJob = launchWithProgress(refreshProgressIndicator, uiThread) {
      requestLogger.debug("Refresh triggered (inside launchWithProgress scope)", refreshTrigger)

      if (DumbService.isDumb(project)) {
        requestLogger.debug("Project is in dumb mode, not able to refresh")
        return@launchWithProgress
      }

      if (projectBuildStatusManager.status == ProjectStatus.NeedsBuild) {
        // Project needs to be built before being able to refresh.
        requestLogger.debug("Project has not build, not able to refresh")
        return@launchWithProgress
      }

      if (Bridge.hasNativeCrash() && composeWorkBench is ComposePreviewViewImpl) {
        composeWorkBench.handleLayoutlibNativeCrash { requestRefresh() }
        return@launchWithProgress
      }

      composeWorkBench.updateVisibilityAndNotifications()
      refreshCallsCount.incrementAndGet()

      try {
        refreshProgressIndicator.text = message("refresh.progress.indicator.finding.previews")
        val filePreviewElements = withContext(workerThread) {
          memoizedElementsProvider.previewElements()
            .toList()
            .sortByDisplayAndSourcePosition()
        }

        val pinnedPreviewElements = if (StudioFlags.COMPOSE_PIN_PREVIEW.get()) {
          refreshProgressIndicator.text = message("refresh.progress.indicator.finding.pinned.previews")

          withContext(workerThread) {
            memoizedPinnedPreviewProvider.previewElements()
              .toList()
              .sortByDisplayAndSourcePosition()
          }
        } else emptyList()

        val needsFullRefresh = invalidated.getAndSet(false) ||
                               renderedElements != filePreviewElements ||
                               PinnedPreviewElementManager.getInstance(project).modificationCount != lastPinsModificationCount

        composeWorkBench.hasContent = filePreviewElements.isNotEmpty() || pinnedPreviewElements.isNotEmpty()
        if (!needsFullRefresh) {
          requestLogger.debug("No updates on the PreviewElements, just refreshing the existing ones")
          // In this case, there are no new previews. We need to make sure that the surface is still correctly
          // configured and that we are showing the right size for components. For example, if the user switches on/off
          // decorations, that will not generate/remove new PreviewElements but will change the surface settings.
          refreshProgressIndicator.text = message("refresh.progress.indicator.reusing.existing.previews")
          surface.refreshExistingPreviewElements(refreshProgressIndicator) { previewElement, sceneManager ->
            // When showing decorations, show the full device size
            configureLayoutlibSceneManager(sceneManager,
                                           showDecorations = previewElement.displaySettings.showDecoration,
                                           isInteractive = interactiveMode.isStartingOrReady(),
                                           requestPrivateClassLoader = usePrivateClassLoader(),
                                           isLiveLiteralsEnabled = isLiveLiteralsEnabled,
                                           onLiveLiteralsFound = { hasLiveLiterals = true },
                                           resetLiveLiteralsFound = { hasLiveLiterals = isLiveLiteralsEnabled })
          }
        }
        else {
          refreshProgressIndicator.text = message("refresh.progress.indicator.refreshing.all.previews")
          composeWorkBench.updateProgress(message("panel.initializing"))
          doRefreshSync(filePreviewElements, refreshRequest.quickRefresh, refreshProgressIndicator)
        }
      }
      catch (t: Throwable) {
        requestLogger.warn("Request failed", t)
      }
      finally {
        refreshCallsCount.decrementAndGet()
      }
    }

    refreshJob.invokeOnCompletion {
      LOG.debug("Completed")
      Disposer.dispose(refreshProgressIndicator)
      if (it is CancellationException) {
        composeWorkBench.onRefreshCancelledByTheUser()
      }
      else composeWorkBench.onRefreshCompleted()

      launch(uiThread) {
        if (!composeWorkBench.isMessageBeingDisplayed) {
          // Only notify the preview refresh time if there are previews to show.
          val durationString = Duration.ofMillis((System.nanoTime() - startTime) / 1_000_000).toDisplayString()
          val notification = Notification(
            PREVIEW_NOTIFICATION_GROUP_ID,
            message("event.log.refresh.title"),
            message("event.log.refresh.total.elapsed.time", durationString),
            NotificationType.INFORMATION
          )
          Notifications.Bus.notify(notification, project)
        }
      }
    }
    return refreshJob
  }

  override fun getState(): PreviewRepresentationState {
    val selectedGroupName = previewElementProvider.groupNameFilter.name ?: ""
    val selectedLayoutName = PREVIEW_LAYOUT_MANAGER_OPTIONS.find {
      (surface.sceneViewLayoutManager as LayoutManagerSwitcher).isLayoutManagerSelected(it.layoutManager)
    }?.displayName ?: ""
    return mapOf(
      SELECTED_GROUP_KEY to selectedGroupName,
      LAYOUT_KEY to selectedLayoutName)
  }

  override fun setState(state: PreviewRepresentationState) {
    val selectedGroupName = state[SELECTED_GROUP_KEY]
    val previewLayoutName = state[LAYOUT_KEY]
    onRestoreState = {
      if (!selectedGroupName.isNullOrEmpty()) {
        availableGroups.find { it.name == selectedGroupName }?.let {
          groupFilter = it
        }
      }

      PREVIEW_LAYOUT_MANAGER_OPTIONS.find { it.displayName == previewLayoutName }?.let {
        (surface.sceneViewLayoutManager as LayoutManagerSwitcher).setLayoutManager(it.layoutManager)
      }
    }
  }

  /**
   * Whether the scene manager should use a private ClassLoader. Currently, that's done for interactive preview and animation inspector,
   * where it's crucial not to share the state (which includes the compose framework).
   */
  private fun usePrivateClassLoader() = interactiveMode.isStartingOrReady() || animationInspection.get() || shouldQuickRefresh()

  private fun invalidate() {
    invalidated.set(true)
  }

  internal fun forceRefresh(quickRefresh: Boolean = false): Job? {
    invalidate()
    return refresh(RefreshRequest(quickRefresh))
  }

  override fun registerShortcuts(applicableTo: JComponent) {
    ForceCompileAndRefreshAction(surface).registerCustomShortcutSet(getBuildAndRefreshShortcut(), applicableTo, this)
  }

  /**
   * We will only do quick refresh if there is a single preview.
   * When live literals is enabled, we want to try to preserve the same class loader as much as possible.
   */
  private fun shouldQuickRefresh() =
    !isLiveLiteralsEnabled && StudioFlags.COMPOSE_QUICK_ANIMATED_PREVIEW.get() && renderedElements.count() == 1

  private suspend fun requestFastPreviewRefresh(): CompilationResult? = coroutineScope {
    val currentStatus = status()
    if (!currentStatus.hasSyntaxErrors) {
      psiFilePointer.element?.let {
        val result = fastCompile(this@ComposePreviewRepresentation, it)
        if (result is CompilationResult.Success) {
          forceRefresh()
        }
        return@coroutineScope result
      }
    }

    return@coroutineScope null
  }

  override fun requestFastPreviewRefreshAsync(): Deferred<CompilationResult?> =
    activationScope?.async { requestFastPreviewRefresh() } ?: CompletableDeferred(null)
}
