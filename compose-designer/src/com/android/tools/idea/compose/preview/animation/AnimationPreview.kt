/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation

import androidx.compose.animation.tooling.ComposeAnimation
import androidx.compose.animation.tooling.ComposeAnimationType
import androidx.compose.animation.tooling.TransitionInfo
import com.android.tools.adtui.TabularLayout
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.analytics.AnimationToolingEvent
import com.android.tools.idea.compose.preview.analytics.AnimationToolingUsageTracker
import com.android.tools.idea.compose.preview.animation.AnimationPreview.Timeline
import com.android.tools.idea.compose.preview.animation.timeline.ElementState
import com.android.tools.idea.compose.preview.animation.timeline.TimelineLine
import com.android.tools.idea.compose.preview.animation.timeline.TransitionCurve
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.layoutlibSceneManagers
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.MoreExecutors
import com.google.wireless.android.sdk.stats.ComposeAnimationToolingEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.border.MatteBorder
import kotlin.math.ceil
import kotlin.math.max

private val LOG = Logger.getInstance(AnimationPreview::class.java)

/**
 * Displays details about animations belonging to a Compose Preview. Allows users to see all the properties (e.g. `ColorPropKeys`) being
 * animated grouped by animation (e.g. `TransitionAnimation`, `AnimatedValue`). In addition, [Timeline] is a timeline view
 * that can be controlled by scrubbing or through a set of controllers, such as play/pause and jump to end. The [AnimationPreview]
 * therefore allows a detailed inspection of Compose animations.
 */
class AnimationPreview(override val surface: DesignSurface) : JPanel(
  TabularLayout("Fit,*", "Fit,*,Fit")), ComposeAnimationPreview {

  private val tracker: (ComposeAnimationToolingEvent.ComposeAnimationToolingEventType) -> Unit = { type: ComposeAnimationToolingEvent.ComposeAnimationToolingEventType ->
    AnimationToolingUsageTracker.getInstance(surface).logEvent(AnimationToolingEvent(type))
  }

  private val previewState = object : AnimationPreviewState {
    override fun isCoordinationAvailable(): Boolean =
      animationClock?.coordinationIsSupported() == true
  }

  /**
   * Tabs panel where each tab represents a single animation being inspected. First tab is a coordination tab.
   * All tabs share the same [Timeline], but have their own playback toolbar and from/to state combo boxes.
   */
  @VisibleForTesting
  val tabbedPane = AnimationTabs(surface).apply {
    addListener(TabChangeListener())
  }

  /** Selected single animation. */
  private var selectedAnimation: AnimationManager? = null

  private inner class TabChangeListener : TabsListener {
    override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
      val component = tabbedPane.selectedInfo?.component ?: return
      //If single animation tab is selected.
      animations.find { it.tabComponent == component }?.let { tab ->
        if (newSelection == oldSelection) return
        // Swing components cannot be placed into different containers, so we add the shared timeline to the active tab on tab change.
        tab.addTimeline()
        selectedAnimation = tab
        tab.loadProperties()
        createTimelineElements(listOf(tab))
        return@selectionChanged
      }
      //If coordination tab is selected.
      if (component is AllTabPanel) {
        coordinationTab.addTimeline(timeline)
        createTimelineElements(animations)
      }
      selectedAnimation = null
    }
  }

  /**
   * Maps animation objects to the [AnimationManager] that represents them.
   */
  private val animationsMap = HashMap<ComposeAnimation, AnimationManager>()

  /** List of [AnimationManager], same as in [animationsMap] but in order as they appear in the list. */
  private val animations = mutableListOf<AnimationManager>()

  /** Generates unique tab names for each tab e.g "tabTitle(1)", "tabTitle(2)". */
  private val tabNames = TabNamesGenerator()

  /** Loading panel displayed when the preview has no animations subscribed. */
  private val noAnimationsPanel = JBLoadingPanel(BorderLayout(), this).apply {
    name = "Loading Animations Panel"
    setLoadingText(message("animation.inspector.loading.animations.panel.message"))
  }

  private val timeline = Timeline()

  private val clockControl = SliderClockControl(timeline)

  private val playbackControls = PlaybackControls(clockControl, tracker, surface, this)

  private val bottomPanel = BottomPanel(previewState, surface, tracker).apply {
    timeline.addChangeListener {
      clockTimeMs = timeline.value
    }
    addResetListener {
      timeline.sliderUI.elements.forEach { it.reset() }
      animations.forEach { it.elementState.valueOffset = 0 }
      timeline.repaint()
    }
  }

  private val coordinationTab = AllTabPanel().apply {
    addPlayback(playbackControls.createToolbar())
  }

  override val component = this

  override fun animationsCount(): Int = animations.size

  /**
   * Wrapper of the `PreviewAnimationClock` that animations inspected in this panel are subscribed to.
   * Null when there are no animations.
   */
  override var animationClock: AnimationClock? = null

  private var maxDurationPerIteration = DEFAULT_MAX_DURATION_MS
    set(value) {
      field = value
      clockControl.updateMaxDuration(value)
    }

  /** Create list of [TimelineElement] for selected [AnimationManager]s. */
  private fun createTimelineElements(tabs: Collection<AnimationManager>) {
    executeOnRenderThread(false) {
      var minY = InspectorLayout.TIMELINE_TOP_OFFSET
      // Call once to update all sizes as all curves / lines required it.
      timeline.revalidate()
      invokeLater {
        timeline.repaint()
        timeline.sliderUI.elements = if (tabs.size == 1) {
          // Paint single selected animation.
          val curve = TransitionCurve.create(tabs.first().elementState, tabs.first().currentTransition, minY,
                                             timeline.sliderUI.positionProxy)
          tabs.first().selectedPropertiesCallback = { curve.timelineUnits = it }
          curve.timelineUnits = tabs.first().selectedProperties
          mutableListOf(curve)
        }
        else tabs
          .map { tab ->
            tab.card.expandedSize = TransitionCurve.expectedHeight(tab.currentTransition)
            val line = if (tab.elementState.expanded) {
              val curve = TransitionCurve.create(tab.elementState, tab.currentTransition, minY,
                                                 timeline.sliderUI.positionProxy)
              tab.selectedPropertiesCallback = { curve.timelineUnits = it }
              curve.timelineUnits = tab.selectedProperties
              curve
            }
            else TimelineLine(tab.elementState, tab.currentTransition, minY, timeline.sliderUI.positionProxy)
            minY += line.height
            tab.card.setDuration(tab.currentTransition.duration)
            line
          }.toMutableList()
      }
      timeline.revalidate()
      coordinationTab.revalidate()
    }
  }

  /** Update currently displayed [TimelineElement]s. */
  private fun updateTimelineElements() {
    createTimelineElements(selectedAnimation?.let { listOf(it) } ?: animations)
  }

  private fun setClockTime(newValue: Int, longTimeout: Boolean = false) {
    animationClock?.apply {
      var clockTimeMs = newValue.toLong()
      if (clockControl.playInLoop) {
        // When playing in loop, we need to add an offset to slide the window and take repeatable animations into account when necessary
        clockTimeMs += timeline.maximum * clockControl.loopCount
      }

      if (!executeOnRenderThread(longTimeout) {
          if (coordinationIsSupported())
            setClockTimes(animationsMap.mapValues {
              (if (it.value.elementState.locked) it.value.elementState.lockedValue.toLong() else clockTimeMs) - it.value.elementState.valueOffset
            })
          // Fall back to `setClockTime` if coordination is nor available.
          else setClockTime(clockTimeMs)
        }) return

      // Load all properties.
      animations.forEach { it.loadProperties() }
    }
  }


  /**
   * Updates the `from` and `to` state combo boxes to display the states of the given animation, and resets the timeline. Invokes a given
   * callback once everything is populated.
   */
  override fun updateTransitionStates(animation: ComposeAnimation, states: Set<Any>, callback: () -> Unit) {
    animationsMap[animation]?.let { tab ->
      tab.stateComboBox.updateStates(states)
      val transition = animation.animationObject
      transition::class.java.methods.singleOrNull { it.name == "getCurrentState" }?.let {
        it.isAccessible = true
        it.invoke(transition)?.let { state ->
          tab.stateComboBox.setStartState(state)
        }
      }

      // Call updateAnimationStartAndEndStates directly here to set the initial animation states in PreviewAnimationClock
      updateAnimationStatesExecutor.execute {
        // Use a longer timeout the first time we're updating the start and end states. Since we're running off EDT, the UI will not freeze.
        // This is necessary here because it's the first time the animation mutable states will be written, when setting the clock, and
        // read, when getting its duration. These operations take longer than the default 30ms timeout the first time they're executed.
        tab.updateAnimationStartAndEndStates(longTimeout = true)
        tab.loadTransitionFromCacheOrLib(longTimeout = true)
        tab.loadProperties()
        // Set up the combo box listeners so further changes to the selected state will trigger a call to updateAnimationStartAndEndStates.
        // Note: this is called only once per tab, in this method, when creating the tab.
        tab.stateComboBox.setupListeners()
        callback.invoke()
      }
    }
  }

  /**
   * Updates the combo box that displays the possible states of an `AnimatedVisibility` animation, and resets the timeline. Invokes a given
   * callback once the combo box is populated.
   */
  override fun updateAnimatedVisibilityStates(animation: ComposeAnimation, callback: () -> Unit) {
    animationsMap[animation]?.let { tab ->
      tab.stateComboBox.updateStates(animation.states)

      updateAnimationStatesExecutor.execute {
        // Update the animated visibility combo box with the correct initial state, obtained from PreviewAnimationClock.
        var state: Any? = null
        executeOnRenderThread(useLongTimeout = true) {
          val clock = animationClock ?: return@executeOnRenderThread
          // AnimatedVisibilityState is an inline class in Compose that maps to a String. Therefore, calling `getAnimatedVisibilityState`
          // via reflection will return a String rather than an AnimatedVisibilityState. To work around that, we select the initial combo
          // box item by checking the display value.
          state = clock.getAnimatedVisibilityState(animation)
        }
        tab.stateComboBox.setStartState(state)

        // Use a longer timeout the first time we're updating the AnimatedVisiblity state. Since we're running off EDT, the UI will not
        // freeze. This is necessary here because it's the first time the animation mutable states will be written, when setting the clock,
        // and read, when getting its duration. These operations take longer than the default 30ms timeout the first time they're executed.
        tab.updateAnimatedVisibility(longTimeout = true)
        tab.loadTransitionFromCacheOrLib(longTimeout = true)
        // Set up the combo box listener so further changes to the selected state will trigger a call to updateAnimatedVisibility.
        // Note: this is called only once per tab, in this method, when creating the tab.
        tab.stateComboBox.setupListeners()
        callback.invoke()
      }
    }
  }

  private fun resetTimelineAndUpdateWindowSize(longTimeout: Boolean) {
    // Set the timeline to 0
    setClockTime(0, longTimeout)
    updateTimelineWindowSize(longTimeout)
    // Update the cached value manually to prevent the timeline to set the clock time to 0 using the short timeout.
    timeline.cachedVal = 0
    // Move the timeline slider to 0.
    UIUtil.invokeLaterIfNeeded { clockControl.jumpToStart() }
  }

  /**
   * Update the timeline window size, which is usually the duration of the longest animation being tracked. However, repeatable animations
   * are handled differently because they can have a large number of iterations resulting in a unrealistic duration. In that case, we take
   * the longest iteration instead to represent the window size and set the timeline max loop count to be large enough to display all the
   * iterations.
   */
  private fun updateTimelineWindowSize(longTimeout: Boolean = false) {
    val clock = animationClock ?: return

    if (!executeOnRenderThread(longTimeout) {
        maxDurationPerIteration = clock.getMaxDurationMsPerIteration()
      }) return

    var maxDuration = DEFAULT_MAX_DURATION_MS
    if (!executeOnRenderThread(longTimeout) { maxDuration = clock.getMaxDurationMs() }) return

    clockControl.maxLoopCount = if (maxDuration > maxDurationPerIteration) {
      // The max duration is longer than the max duration per iteration. This means that a repeatable animation has multiple iterations,
      // so we need to add as many loops to the timeline as necessary to display all the iterations.
      ceil(maxDuration / maxDurationPerIteration.toDouble()).toLong()
    }
    // Otherwise, the max duration fits the window, so we just need one loop that keeps repeating when loop mode is active.
    else 1
  }

  /** Replaces the [tabbedPane] with [noAnimationsPanel]. */
  private fun showNoAnimationsPanel() {
    remove(tabbedPane.component)
    remove(bottomPanel)
    noAnimationsPanel.startLoading()
    add(noAnimationsPanel, TabularLayout.Constraint(1, 0, 2))
    // Reset tab names, so when new tabs are added they start as #1
    tabNames.clear()
    timeline.cachedVal = -1 // Reset the timeline cached value, so when new tabs are added, any new value will trigger an update
    // The animation panel might not have the focus when the "No animations" panel is displayed, i.e. when a live literal is changed in the
    // editor and we need to refresh the animation preview so it displays the most up-to-date animations. For that reason, we need to make
    // sure the animation panel is repainted correctly.
    repaint()
    playbackControls.pause()
  }

  /**
   * Creates an [AnimationManager] corresponding to the given [animation] and add it to the [animations] map.
   * Note: this method does not add the tab to [tabbedPane]. For that, [addTab] should be used.
   */
  override fun createTab(animation: ComposeAnimation) {
    animationsMap[animation] = AnimationManager(animation)
  }

  /** Adds an [AnimationManager] card corresponding to the given [animation] to [coordinationTab]. */
  override fun addTab(animation: ComposeAnimation) {
    val animationTab = animationsMap[animation] ?: return

    val isAddingFirstTab = tabbedPane.tabCount == 0
    coordinationTab.addCard(animationTab.card)
    animations.add(animationTab)

    if (isAddingFirstTab) {
      // There are no tabs and we're about to add one. Replace the placeholder panel with the TabbedPane.
      noAnimationsPanel.stopLoading()
      remove(noAnimationsPanel)
      tabbedPane.addTab(TabInfo(coordinationTab).apply { text = "${message("animation.inspector.tab.all.title")}  " }, 0)
      coordinationTab.addTimeline(timeline)
      add(tabbedPane.component, TabularLayout.Constraint(1, 0, 2))
      add(bottomPanel, TabularLayout.Constraint(2, 0, 2))
    }
  }

  /** Removes the [AnimationManager] card and tab corresponding to the given [animation] from [tabbedPane]. */
  override fun removeTab(animation: ComposeAnimation) {
    animationsMap[animation]?.let { tab ->
      coordinationTab.removeCard(tab.card)
      tabbedPane.tabs.find { it.component == tab.tabComponent }?.let { tabbedPane.removeTab(it) }
      animations.remove(tab)
    }
    animationsMap.remove(animation)

    if (tabbedPane.tabCount == 1) {
      tabbedPane.removeTab(tabbedPane.getTabAt(0))
      // There are no more tabs. Replace the TabbedPane with the placeholder panel.
      showNoAnimationsPanel()
    }
    else if (tabbedPane.tabCount != 0) {
      tabbedPane.select(tabbedPane.getTabAt(0), true)
    }
  }

  /** Remove all tabs from [tabbedPane], replace it with [noAnimationsPanel], and clears the cached animations.*/
  override fun invalidatePanel() {
    tabbedPane.removeAllTabs()
    animationsMap.clear()
    animations.clear()
    tabNames.clear()
    showNoAnimationsPanel()
  }

  override fun dispose() {
    animationsMap.clear()
    animations.clear()
    tabNames.clear()
  }

  init {
    name = "Animation Preview"
    noAnimationsPanel.startLoading()
    add(noAnimationsPanel, TabularLayout.Constraint(1, 0, 2))
  }

  /**
   *
   */
  private inner class AnimationManager(val animation: ComposeAnimation) {

    private val tabTitle = tabNames.createName(animation)

    /** [StateComboBox] for single animation tab. Should not be used directly. Use [stateComboBox] to control the state. */
    private val stateComboBoxInTab = createComboBox()

    /** [StateComboBox] for animation card.  Should not be used directly. Use [stateComboBox] to control the state. */
    private val stateComboBoxInCard = createComboBox()

    /** Wrapper around two [InspectorPainter.StateComboBox] as state should be shared between them. */
    val stateComboBox = InspectorPainter.StateComboBoxes(listOf(stateComboBoxInTab, stateComboBoxInCard))

    /** State of animation, shared between single animation tab and coordination panel. */
    val elementState = ElementState(tabTitle).apply {
      addExpandedListener {
        updateTimelineElements()
      }
      addLockedListener {
        timeline.repaint()
        if (!this.locked) {
          setClockTime(timeline.value)
          loadProperties()
        }
        lockedValue = timeline.value
      }
      addValueOffsetListener { setClockTime(timeline.value) }
    }

    /** [AnimationCard] for coordination panel. */
    val card = AnimationCard(previewState, surface, elementState, tracker).apply {

      /** [TabInfo] for the animation when it is opened in a new tab. */
      var tabInfo: TabInfo? = null

      /** Create if required and open the tab. */
      fun addTabToPane() {
        if (tabInfo == null) {
          tabInfo = TabInfo(tabComponent).apply {
            text = tabTitle
            tabbedPane.addTabWithCloseButton(this) {
              tabInfo = null
            }
          }
        }
        tabInfo?.let { tabbedPane.select(it, true) }
      }
      this.addStateComponent(stateComboBoxInCard.component)
      this.addOpenInTabListener { addTabToPane() }
    }

    private val tabScrollPane = JBScrollPane().apply {
      border = MatteBorder(1, 1, 0, 0, JBColor.border())
    }

    /** [Timeline] parent when animation in new tab is selected. */
    private val tabTimelineParent = JPanel(BorderLayout())

    val tabComponent = JPanel(TabularLayout("Fit,*,Fit", "Fit,*")).apply {
      add(stateComboBoxInTab.component, TabularLayout.Constraint(0, 2))
      add(tabScrollPane, TabularLayout.Constraint(1, 0, 3))
      tabScrollPane.setViewportView(tabTimelineParent)
      add(playbackControls.createToolbar(listOf(LockAction(previewState, elementState, tracker))), TabularLayout.Constraint(0, 0))
      isFocusable = false
      focusTraversalPolicy = LayoutFocusTraversalPolicy()
    }

    private val cachedTransitions: MutableMap<Int, Transition> = mutableMapOf()

    var currentTransition = Transition()
      private set(value) {
        field = value
        // If transition has changed, reset it offset.
        elementState.valueOffset = 0
        updateTimelineElements()
      }

    var selectedPropertiesCallback: (List<ComposeUnit.TimelineUnit>) -> Unit = {}
    var selectedProperties = listOf<ComposeUnit.TimelineUnit>()
      private set(value) {
        field = value
        selectedPropertiesCallback(value)
      }

    private fun createComboBox(): InspectorPainter.StateComboBox {
      return when (animation.type) {
        ComposeAnimationType.TRANSITION_ANIMATION -> InspectorPainter.StartEndComboBox(surface, tracker) {
          updateAnimationStartAndEndStates()
          loadTransitionFromCacheOrLib()
          loadProperties()
        }
        ComposeAnimationType.ANIMATED_VISIBILITY -> InspectorPainter.AnimatedVisibilityComboBox(tracker) {
          updateAnimatedVisibility()
          loadTransitionFromCacheOrLib()
          loadProperties()
        }
        ComposeAnimationType.ANIMATED_VALUE -> InspectorPainter.EmptyComboBox()
      }
    }

    /**
     * Updates the actual animation in Compose to set its start and end states to the ones selected in the respective combo boxes.
     */
    fun updateAnimationStartAndEndStates(longTimeout: Boolean = false) {
      animationClock?.apply {
        val startState = stateComboBox.getState(0)
        val toState = stateComboBox.getState(1)

        if (!executeOnRenderThread(longTimeout) {
            updateFromAndToStates(animation, startState, toState)
          }) return
        resetTimelineAndUpdateWindowSize(longTimeout)
      }
    }

    /**
     * Updates the actual animation in Compose to set its state based on the selected value of [stateComboBox].
     */
    fun updateAnimatedVisibility(longTimeout: Boolean = false) {
      animationClock?.apply {
        if (!executeOnRenderThread(longTimeout) {
            updateAnimatedVisibilityState(animation, stateComboBox.getState())
          }) return
        resetTimelineAndUpdateWindowSize(longTimeout)
      }
    }

    /**
     * Load transition for current start and end state. If transition was loaded before, the cached result is used.
     */
    fun loadTransitionFromCacheOrLib(longTimeout: Boolean = false) {
      val stateHash = stateComboBox.stateHashCode()

      cachedTransitions[stateHash]?.let {
        currentTransition = it
        return@loadTransitionFromCacheOrLib
      }

      val clock = animationClock ?: return

      executeOnRenderThread(longTimeout) {
        val transition = loadTransitionsFromLib(clock)
        cachedTransitions[stateHash] = transition
        currentTransition = transition
      }
    }

    private fun loadTransitionsFromLib(clock: AnimationClock): Transition {
      val builders: MutableMap<Int, AnimatedProperty.Builder> = mutableMapOf()
      val clockTimeMsStep = max(1, maxDurationPerIteration / DEFAULT_CURVE_POINTS_NUMBER)

      fun getTransitions() {
        val composeTransitions = clock.getTransitionsFunction?.invoke(clock.clock, animation, clockTimeMsStep) as List<TransitionInfo>
        for ((index, composeTransition) in composeTransitions.withIndex()) {
          val builder = AnimatedProperty.Builder()
            .setStartTimeMs(composeTransition.startTimeMillis.toInt())
            .setEndTimeMs(composeTransition.endTimeMillis.toInt())
          composeTransition.values.mapValues {
            ComposeUnit.parseValue(it.value)
          }.forEach { (ms, unit) ->
            unit?.let {
              builder.add(ms.toInt(), unit)
            }
          }
          builders[index] = builder
        }
      }

      fun getAnimatedProperties() {
        for (clockTimeMs in 0..maxDurationPerIteration step clockTimeMsStep) {
          clock.setClockTime(clockTimeMs)
          val properties = clock.getAnimatedProperties(animation)
          for ((index, property) in properties.withIndex()) {
            ComposeUnit.parse(property)?.let { unit ->
              builders.getOrPut(index) { AnimatedProperty.Builder() }.add(clockTimeMs.toInt(), unit)
            }
          }
        }
      }

      try {
        if (clock.getTransitionsFunction != null) getTransitions()
        else getAnimatedProperties()

      }
      catch (e: Exception) {
        LOG.warn("Failed to load the Compose Animation properties", e)
      }

      builders.mapValues { it.value.build() }.let {
        return Transition(it)
      }
    }

    fun loadProperties() {
      animationClock?.apply {
        try {
          selectedProperties = getAnimatedProperties(animation).map { ComposeUnit.TimelineUnit(it.label, ComposeUnit.parse(it)) }
        }
        catch (e: Exception) {
          LOG.warn("Failed to get the Compose Animation properties", e)
        }
      }
    }

    /**
     * Adds [timeline] to this tab's layout. The timeline is shared across all tabs, and a Swing component can't be added as a
     * child of multiple components simultaneously. Therefore, this method needs to be called everytime we change tabs.
     */
    fun addTimeline() {
      tabTimelineParent.add(timeline, BorderLayout.CENTER)
      timeline.revalidate()
      tabScrollPane.revalidate()
    }
  }

  /**
   *  Timeline panel ranging from 0 to the max duration (in ms) of the animations being inspected, listing all the animations and their
   *  corresponding range as well. The timeline should respond to mouse commands, allowing users to jump to specific points, scrub it, etc.
   */
  private inner class Timeline : TimelinePanel(previewState, tracker) {
    var cachedVal = -1

    init {
      addChangeListener {
        if (value == cachedVal) return@addChangeListener // Ignore repeated values
        cachedVal = value
        setClockTime(value)
      }
      addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) = updateTimelineElements()
      })
    }
  }

  /**
   * Executor responsible for updating animation states off EDT.
   */
  private val updateAnimationStatesExecutor =
    if (ApplicationManager.getApplication().isUnitTestMode)
      MoreExecutors.directExecutor()
    else
      AppExecutorUtil.createBoundedApplicationPoolExecutor("Animation States Updater", 1)

  private fun executeOnRenderThread(useLongTimeout: Boolean, callback: () -> Unit): Boolean {
    val (time, timeUnit) = if (useLongTimeout) {
      // Make sure we don't block the UI thread when setting a large timeout
      ApplicationManager.getApplication().assertIsNonDispatchThread()
      5L to TimeUnit.SECONDS
    }
    else {
      30L to TimeUnit.MILLISECONDS
    }
    return surface.layoutlibSceneManagers.singleOrNull()?.executeCallbacksAndRequestRender(time, timeUnit) {
      callback()
    } ?: false
  }
}