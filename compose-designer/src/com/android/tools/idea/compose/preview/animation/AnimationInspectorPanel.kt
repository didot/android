/*
 * Copyright (C) 2020 The Android Open Source Project
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

import androidx.compose.animation.tooling.ComposeAnimatedProperty
import androidx.compose.animation.tooling.ComposeAnimation
import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.stdui.CommonTabbedPane
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.util.ControllableTicker
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.layoutlibSceneManagers
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.AnActionButton
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.SystemProperties
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseEvent
import java.time.Duration
import java.util.Dictionary
import java.util.Hashtable
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.SwingConstants
import javax.swing.border.MatteBorder
import javax.swing.plaf.basic.BasicSliderUI
import kotlin.math.ceil

private val LOG = Logger.getInstance(AnimationInspectorPanel::class.java)

/**
 * Displays details about animations belonging to a Compose Preview. Allows users to see all the properties (e.g. `ColorPropKeys`) being
 * animated grouped by animation (e.g. `TransitionAnimation`, `AnimatedValue`). In addition, [TransitionDurationTimeline] is a timeline view
 * that can be controlled by scrubbing or through a set of controllers, such as play/pause and jump to end. The [AnimationInspectorPanel]
 * therefore allows a detailed inspection of Compose animations.
 */
class AnimationInspectorPanel(private val surface: DesignSurface) : JPanel(TabularLayout("Fit,*", "Fit,*")), Disposable {

  /**
   * [CommonTabbedPane] where each tab represents a single animation being inspected. All tabs share the same [TransitionDurationTimeline],
   * but have their own playback toolbar, from/to state combo boxes and animated properties panel.
   */
  private val tabbedPane = CommonTabbedPane().apply {
    border = MatteBorder(1, 0, 0, 0, JBColor.border())

    addChangeListener {
      if (selectedIndex < 0) return@addChangeListener

      (getComponentAt(selectedIndex) as? AnimationTab)?.let { tab ->
        // Swing components cannot be placed into different containers, so we add the shared timeline to the active tab on tab change.
        tab.addTimeline()
        timeline.selectedTab = tab
      }
    }
  }

  /**
   * Maps animation objects to the [AnimationTab] that represents them.
   */
  private val animationTabs = HashMap<ComposeAnimation, AnimationTab>()

  /**
   * [tabbedPane]'s tab titles mapped to the amount of tabs using that title. The count is used to differentiate tabs, which are named
   * "tabTitle #1", "tabTitle #2", etc., instead of multiple "tabTitle" tabs.
   */
  private val tabNamesCount = HashMap<String, Int>()

  /**
   * Panel displayed when the preview has no animations subscribed.
   */
  private val noAnimationsPanel = JPanel(BorderLayout()).apply {
    add(JBLabel(message("animation.inspector.no.animations.panel.message"), SwingConstants.CENTER), BorderLayout.CENTER)
    border = MatteBorder(1, 0, 0, 0, JBColor.border())
  }

  private val timeline = TransitionDurationTimeline()

  private val playPauseAction = PlayPauseAction()

  private val timelineSpeedAction = TimelineSpeedAction()

  private val timelineLoopAction = TimelineLoopAction()

  /**
   * Wrapper of the `PreviewAnimationClock` that animations inspected in this panel are subscribed to. Null when there are no animations.
   */
  internal var animationClock: AnimationClock? = null

  init {
    name = "Animation Inspector"
    border = MatteBorder(1, 0, 1, 0, JBColor.border())
    var composableTitle = JBLabel(message("animation.inspector.panel.title")).apply {
      border = JBUI.Borders.empty(5)
    }

    add(composableTitle, TabularLayout.Constraint(0, 0))
    add(noAnimationsPanel, TabularLayout.Constraint(1, 0, 2))
  }

  /**
   * Updates the `from` and `to` state combo boxes to display the states of the given animation, and resets the timeline.
   */
  fun updateTransitionStates(animation: ComposeAnimation, states: Set<Any>) {
    animationTabs[animation]?.let { tab ->
      tab.updateStateComboboxes(states.toTypedArray())
    }
    timeline.jumpToStart()
    timeline.setClockTime(0) // Make sure that clock time is actually set in case timeline was already in 0.
  }

  /**
   * Remove all tabs from [tabbedPane], replace it with [noAnimationsPanel], and clears the cached animations.
   */
  internal fun invalidatePanel() {
    tabbedPane.removeAll()
    animationTabs.clear()
    showNoAnimationsPanel()
  }

  /**
   * Replaces the [tabbedPane] with [noAnimationsPanel].
   */
  private fun showNoAnimationsPanel() {
    remove(tabbedPane)
    add(noAnimationsPanel, TabularLayout.Constraint(1, 0, 2))
    // Reset tab names, so when new tabs are added they start as #1
    tabNamesCount.clear()
  }

  /**
   * Adds an [AnimationTab] corresponding to the given [animation] to [tabbedPane].
   */
  internal fun addTab(animation: ComposeAnimation) {
    if (tabbedPane.tabCount == 0) {
      // There are no tabs and we're about to add one. Replace the placeholder panel with the TabbedPane.
      remove(noAnimationsPanel)
      add(tabbedPane, TabularLayout.Constraint(1, 0, 2))
    }

    val animationTab = AnimationTab(animation)
    animationTabs[animation] = animationTab
    val tabName = animation.label ?: message("animation.inspector.tab.default.title")
    tabNamesCount[tabName] = tabNamesCount.getOrDefault(tabName, 0) + 1
    tabbedPane.addTab("${tabName} #${tabNamesCount[tabName]}", animationTab)
  }

  /**
   * Removes the [AnimationTab] corresponding to the given [animation] from [tabbedPane].
   */
  internal fun removeTab(animation: ComposeAnimation) {
    tabbedPane.remove(animationTabs[animation])
    animationTabs.remove(animation)

    if (tabbedPane.tabCount == 0) {
      // There are no more tabs. Replace the TabbedPane with the placeholder panel.
      showNoAnimationsPanel()
    }
  }

  override fun dispose() {
    playPauseAction.dispose()
    animationTabs.clear()
    tabNamesCount.clear()
  }

  /**
   * Content of a tab representing an animation. All the elements that aren't shared between tabs and need to be exposed should be defined
   * in this class, e.g. from/to state combo boxes.
   */
  private inner class AnimationTab(val animation: ComposeAnimation) : JPanel(TabularLayout("Fit,*,Fit", "Fit,*")) {

    /**
     * Listens to changes in either [startStateComboBox] ot [endStateComboBox].
     */
    private val stateChangeListener = object : ActionListener {
      override fun actionPerformed(e: ActionEvent?) {
        if (isSwappingStates) {
          // The is no need to trigger the callback, since we're going to make a follow up call to update the other state.
          isSwappingStates = false
          return
        }

        updateSeekableAnimation()
      }
    }

    private val startStateComboBox = ComboBox(DefaultComboBoxModel(arrayOf<Any>()))
    private val endStateComboBox = ComboBox(DefaultComboBoxModel(arrayOf<Any>()))

    /**
     * Flag to be used when the [SwapStartEndStatesAction] is triggered, in order to prevent the listener to be executed twice.
     */
    private var isSwappingStates = false

    /**
     * Displays the animated properties and their value at the current timeline time.
     */
    private val propsTextArea = JBTextArea(message("animation.inspector.no.properties.message")).apply { isEditable = false }

    private val timelinePanel = JPanel(BorderLayout())

    /**
     * Horizontal [JBSplitter] comprising of the animated properties panel and the animation timeline.
     */
    private val propertiesTimelineSplitter = JBSplitter(0.2f).apply {
      firstComponent = createAnimatedPropertiesPanel()
      secondComponent = timelinePanel
      dividerWidth = 1
    }

    init {
      startStateComboBox.addActionListener(stateChangeListener)
      endStateComboBox.addActionListener(stateChangeListener)

      add(createPlaybackControllers(), TabularLayout.Constraint(0, 0))
      add(createAnimationStateComboboxes(), TabularLayout.Constraint(0, 2))
      val splitterWrapper = JPanel(BorderLayout()).apply {
        border = MatteBorder(1, 0, 0, 0, JBColor.border()) // Top border separating the splitter and the playback toolbar
      }
      splitterWrapper.add(propertiesTimelineSplitter, BorderLayout.CENTER)
      add(splitterWrapper, TabularLayout.Constraint(1, 0, 3))
    }

    /**
     * Updates the actual animation in Compose to set its start and end states to the ones selected in the respective combo boxes.
     */
    fun updateSeekableAnimation() {
      val clock = animationClock ?: return
      val startState = startStateComboBox.selectedItem
      val toState = endStateComboBox.selectedItem

      clock.updateSeekableAnimationFunction.call(clock.clock, animation, startState, toState)
      surface.layoutlibSceneManagers.single().executeCallbacksAndRequestRender { clock.updateAnimationStatesFunction.call(clock.clock) }
      timeline.jumpToStart()
      timeline.setClockTime(0) // Make sure that clock time is actually set in case timeline was already in 0.

      updateTimelineWindowSize()
    }

    /**
     * Update the timeline window size, which is usually the duration of the longest animation being tracked. However, repeatable animations
     * are handled differently because they can have a large number of iterations resulting in a unrealistic duration. In that case, we take
     * the longest iteration instead to represent the window size and set the timeline max loop count to be large enough to display all the
     * iterations.
     */
    fun updateTimelineWindowSize() {
      val clock = animationClock ?: return
      val maxDurationPerIteration = clock.getMaxDurationPerIteration.call(clock.clock) as Long
      timeline.updateMaxDuration(maxDurationPerIteration)

      val maxDuration = clock.getMaxDurationFunction.call(clock.clock) as Long
      timeline.maxLoopCount = if (maxDuration > maxDurationPerIteration) {
        // The max duration is longer than the max duration per iteration. This means that a repeatable animation has multiple iterations,
        // so we need to add as many loops to the timeline as necessary to display all the iterations.
        ceil(maxDuration / maxDurationPerIteration.toDouble()).toLong()
      }
      // Othewise, the max duration fits the window, so we just need one loop that keeps repeating when loop mode is active.
      else 1
    }

    /**
     * Create a toolbar panel with actions to control the animation, e.g. play, pause and jump to start/end.
     *
     * TODO(b/157895086): Update action icons when we have the final Compose Animation tooling icons
     * TODO(b/157895086): Disable toolbar actions while build is in progress
     */
    private fun createPlaybackControllers(): JComponent = ActionManager.getInstance().createActionToolbar(
      "Animation inspector",
      DefaultActionGroup(listOf(
        timelineLoopAction,
        GoToStartAction(),
        playPauseAction,
        GoToEndAction(),
        timelineSpeedAction
      )),
      true).component

    /**
     * Creates a couple of comboboxes representing the start and end states of the animation.
     */
    private fun createAnimationStateComboboxes(): JComponent {
      val states = arrayOf(message("animation.inspector.states.combobox.placeholder.message"))
      val statesToolbar = JPanel(TabularLayout("Fit,Fit,Fit,Fit"))
      startStateComboBox.model = DefaultComboBoxModel(states)
      endStateComboBox.model = DefaultComboBoxModel(states)

      val swapStatesActionToolbar = object : ActionToolbarImpl("Swap States", DefaultActionGroup(SwapStartEndStatesAction()), true) {
        // From ActionToolbar#setMinimumButtonSize, all the toolbar buttons have 25x25 pixels by default. Set the preferred size of the
        // toolbar to be 5 pixels more in both height and width, so it fits exactly one button plus a margin
        override fun getPreferredSize() = JBUI.size(30, 30)
      }
      statesToolbar.add(swapStatesActionToolbar, TabularLayout.Constraint(0, 0))
      statesToolbar.add(startStateComboBox, TabularLayout.Constraint(0, 1))
      statesToolbar.add(JBLabel(message("animation.inspector.state.to.label")), TabularLayout.Constraint(0, 2))
      statesToolbar.add(endStateComboBox, TabularLayout.Constraint(0, 3))
      return statesToolbar
    }

    fun updateStateComboboxes(states: Array<Any>) {
      startStateComboBox.model = DefaultComboBoxModel(states)
      endStateComboBox.model = DefaultComboBoxModel(states)
      endStateComboBox.selectedIndex = if (endStateComboBox.itemCount > 1) 1 else 0
    }

    // TODO(b/157895086): Polish the animated properties panel.
    private fun createAnimatedPropertiesPanel() = JPanel(TabularLayout("*", "*")).apply {
      preferredSize = JBDimension(200, 200)
      add(propsTextArea, TabularLayout.Constraint(0, 0))
    }

    /**
     * Adds [timeline] to this tab's [timelinePanel]. The timeline is shared across all tabs, and a Swing component can't be added as a
     * child of multiple components simultaneously. Therefore, this method needs to be called everytime we change tabs.
     */
    fun addTimeline() {
      timelinePanel.add(timeline, BorderLayout.CENTER)
    }

    fun updateProperties() {
      val animClock = animationClock ?: return
      try {
        var animatedPropKeys = animClock.getAnimatedPropertiesFunction.call(animClock.clock, animation) as List<ComposeAnimatedProperty>
        propsTextArea.text = animatedPropKeys.joinToString(separator = SystemProperties.getLineSeparator()) { "${it.label}: ${it.value}" }
      }
      catch (e: Exception) {
        LOG.warn("Failed to get the Compose Animation properties", e)
      }
    }

    /**
     * Swap start and end animation states in the corresponding combo boxes.
     */
    private inner class SwapStartEndStatesAction()
      : AnActionButton(message("animation.inspector.action.swap.states"), StudioIcons.LayoutEditor.Motion.PLAY_YOYO) {
      override fun actionPerformed(e: AnActionEvent) {
        isSwappingStates = true
        val startState = startStateComboBox.selectedItem
        startStateComboBox.selectedItem = endStateComboBox.selectedItem
        endStateComboBox.selectedItem = startState
      }

      override fun updateButton(e: AnActionEvent) {
        super.updateButton(e)
        e.presentation.isEnabled = true
      }
    }

    /**
     * Snap the animation to the start state.
     */
    private inner class GoToStartAction
      : AnActionButton(message("animation.inspector.action.go.to.start"), StudioIcons.LayoutEditor.Motion.GO_TO_START) {
      override fun actionPerformed(e: AnActionEvent) {
        timeline.jumpToStart()
      }

      override fun updateButton(e: AnActionEvent) {
        super.updateButton(e)
        e.presentation.isEnabled = !timeline.isAtStart()
      }
    }

    /**
     * Snap the animation to the end state.
     */
    private inner class GoToEndAction
      : AnActionButton(message("animation.inspector.action.go.to.end"), StudioIcons.LayoutEditor.Motion.GO_TO_END) {
      override fun actionPerformed(e: AnActionEvent) {
        timeline.jumpToEnd()
      }

      override fun updateButton(e: AnActionEvent) {
        super.updateButton(e)
        e.presentation.isEnabled = !timeline.isAtEnd()
      }
    }
  }

  /**
   * Action to play and pause the animation. The icon and tooltip gets updated depending on the playing state.
   */
  private inner class PlayPauseAction : AnActionButton(message("animation.inspector.action.play"), StudioIcons.LayoutEditor.Motion.PLAY) {
    private val tickPeriod = Duration.ofMillis(30)

    /**
     *  Ticker that increment the animation timeline while it's playing.
     */
    private val ticker =
      ControllableTicker({
                           if (isPlaying) {
                             UIUtil.invokeLaterIfNeeded { timeline.incrementClockBy(tickPeriod.toMillis().toInt()) }
                             if (timeline.isAtEnd()) {
                               if (timeline.playInLoop) {
                                 handleLoopEnd()
                               }
                               else {
                                 pause()
                               }
                             }
                           }
                         }, tickPeriod)

    private var isPlaying = false

    override fun actionPerformed(e: AnActionEvent) = if (isPlaying) pause() else play()

    override fun updateButton(e: AnActionEvent) {
      super.updateButton(e)
      e.presentation.isEnabled = true
      e.presentation.apply {
        if (isPlaying) {
          icon = StudioIcons.LayoutEditor.Motion.PAUSE
          text = message("animation.inspector.action.pause")
        }
        else {
          icon = StudioIcons.LayoutEditor.Motion.PLAY
          text = message("animation.inspector.action.play")
        }
      }
    }

    private fun play() {
      if (timeline.isAtEnd()) {
        // If playing after reaching the timeline end, we should go back to start so the animation can be actually played.
        timeline.jumpToStart()
      }
      isPlaying = true
      ticker.start()
    }

    private fun pause() {
      isPlaying = false
      ticker.stop()
    }

    private fun handleLoopEnd() {
      UIUtil.invokeLaterIfNeeded { timeline.jumpToStart() }
      timeline.loopCount++
      if (timeline.loopCount == timeline.maxLoopCount) {
        timeline.loopCount = 0
      }
    }

    fun dispose() {
      ticker.dispose()
    }
  }

  private enum class TimelineSpeed(val speedMultiplier: Float, val displayText: String) {
    X_0_25(0.25f, "0.25x"),
    X_0_5(0.5f, "0.5x"),
    X_1(1f, "1x"),
    X_1_5(1.5f, "1.5x"),
    X_2(2f, "2x")
  }

  /**
   * Action to speed up or slow down the timeline. The clock runs faster/slower depending on the value selected.
   *
   * TODO(b/157895086): Add a proper icon for the action.
   */
  private inner class TimelineSpeedAction : DropDownAction(message("animation.inspector.action.speed"),
                                                           message("animation.inspector.action.speed"),
                                                           null) {

    init {
      enumValues<TimelineSpeed>().forEach { addAction(SpeedAction(it)) }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.text = timeline.speed.displayText
    }

    override fun displayTextInToolbar() = true

    private inner class SpeedAction(private val speed: TimelineSpeed) : ToggleAction("${speed.displayText}", "${speed.displayText}", null) {
      override fun isSelected(e: AnActionEvent) = timeline.speed == speed

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        timeline.speed = speed
      }
    }
  }

  /**
   * Action to keep the timeline playing in loop. When active, the timeline will keep playing indefinitely instead of stopping at the end.
   * When reaching the end of the window, the timeline will increment the loop count until it reaches its limit. When that happens, the
   * timelines jumps back to start.
   *
   * TODO(b/157895086): Add a proper icon for the action.
   */
  private inner class TimelineLoopAction : ToggleAction(message("animation.inspector.action.loop"),
                                                        message("animation.inspector.action.loop"),
                                                        StudioIcons.LayoutEditor.Motion.LOOP) {

    override fun isSelected(e: AnActionEvent) = timeline.playInLoop

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      timeline.playInLoop = state
      if (!state) {
        // Reset the loop when leaving playInLoop mode.
        timeline.loopCount = 0
      }
    }
  }

  /**
   *  Timeline panel ranging from 0 to the max duration (in ms) of the animations being inspected, listing all the animations and their
   *  corresponding range as well. The timeline should respond to mouse commands, allowing users to jump to specific points, scrub it, etc.
   */
  private inner class TransitionDurationTimeline : JPanel(BorderLayout()) {

    var selectedTab: AnimationTab? = null
      set(value) {
        field = value
        // Sets the clock time in compose so the animation corresponding to the selected tab can animate to the correct time.
        setClockTime(slider.value)
      }
    var cachedVal = -1

    /**
     * Speed multiplier of the timeline clock. [TimelineSpeed.X_1] by default (normal speed).
     */
    var speed: TimelineSpeed = TimelineSpeed.X_1

    /**
     * Whether the timeline should play in loop or stop when reaching the end.
     */
    var playInLoop = false

    /**
     * 0-based count representing the current loop the timeline is in. This should be used as a multiplier of the |windowSize| (slider
     * maximum) offset applied when setting the clock time.
     */
    var loopCount = 0L

    /**
     * The maximum amount of loops the timeline has. When [loopCount] reaches this value, it needs to be reset.
     */
    var maxLoopCount = 1L

    private val slider = object : JSlider(0, 10000, 0) {
      override fun updateUI() {
        setUI(TimelineSliderUI())
        updateLabelUIs()
      }

      override fun setMaximum(maximum: Int) {
        super.setMaximum(maximum)
        updateMajorTicks()
      }

      fun updateMajorTicks() {
        // First, calculate where the major ticks and labels are going to be painted, based on the maximum.
        val tickIncrement = maximum / 5
        setMajorTickSpacing(tickIncrement)
        // Now, add the "ms" suffix to each label.
        if (tickIncrement == 0) {
          // Handle the special case where maximum == 0 and we only have the "0ms" label.
          labelTable = createMsLabelTable(labelTable)
        }
        else {
          labelTable = createMsLabelTable(createStandardLabels(tickIncrement))
        }
      }

    }.apply {
      setPaintTicks(true)
      setPaintLabels(true)
      updateMajorTicks()
      setUI(TimelineSliderUI())
    }

    init {
      border = MatteBorder(0, 1, 0, 0, JBColor.border()) // Left border to separate the timeline from the properties panel

      add(slider, BorderLayout.CENTER)
      slider.addChangeListener {
        if (slider.value == cachedVal) return@addChangeListener // Ignore repeated values
        val newValue = slider.value
        cachedVal = newValue
        setClockTime(newValue)
      }
    }

    fun updateMaxDuration(durationMs: Long) {
      slider.maximum = durationMs.toInt()
    }

    fun setClockTime(newValue: Int) {
      if (animationClock == null || selectedTab == null) return

      var clockTimeMs = newValue.toLong()
      if (playInLoop) {
        // When playing in loop, we need to add an offset to slide the window and take repeatable animations into account when necessary
        clockTimeMs += slider.maximum * loopCount
      }

      surface.layoutlibSceneManagers.single().executeCallbacksAndRequestRender {
        animationClock!!.setClockTimeFunction.call(animationClock!!.clock, clockTimeMs)
      }
      selectedTab!!.updateProperties()
    }

    /**
     * Increments the clock by the given value, taking the current [speed] into account.
     */
    fun incrementClockBy(increment: Int) {
      slider.value += (increment * speed.speedMultiplier).toInt()
    }

    fun jumpToStart() {
      slider.value = 0
    }

    fun jumpToEnd() {
      slider.value = slider.maximum
    }

    fun isAtStart() = slider.value == 0

    fun isAtEnd() = slider.value == slider.maximum

    /**
     * Rewrite the labels by adding a `ms` suffix indicating the values are in milliseconds.
     */
    private fun createMsLabelTable(table: Dictionary<*, *>): Hashtable<Any, JBLabel> {
      val keys = table.keys()
      val labelTable = Hashtable<Any, JBLabel>()
      while (keys.hasMoreElements()) {
        val key = keys.nextElement()
        labelTable[key] = object : JBLabel("$key ms") {
          // Setting the enabled property to false is not enough because BasicSliderUI will check if the slider itself is enabled when
          // paiting the labels and set the label enable status to match the slider's. Thus, we force the label color to the disabled one.
          override fun getForeground() = UIUtil.getLabelDisabledForeground()
        }
      }
      return labelTable
    }

    /**
     * Modified [JSlider] UI to simulate a timeline-like view. In general lines, the following modifications are made:
     *   * The horizontal track is hidden, so only the vertical thumb is shown
     *   * The vertical thumb is a vertical line that matches the parent height
     *   * The tick lines also match the parent height
     */
    private inner class TimelineSliderUI : BasicSliderUI(slider) {

      override fun getThumbSize(): Dimension {
        val originalSize = super.getThumbSize()
        return if (slider.parent == null) originalSize else Dimension(originalSize.width, slider.parent.height - labelsAndTicksHeight())
      }

      override fun calculateTickRect() {
        // Make the vertical tick lines cover the entire panel.
        tickRect.x = thumbRect.x
        tickRect.y = thumbRect.y
        tickRect.width = thumbRect.width
        tickRect.height = thumbRect.height + labelsAndTicksHeight()
      }

      override fun calculateLabelRect() {
        super.calculateLabelRect()
        // Correct the label rect, so the tick rect overlaps with it.
        labelRect.y -= labelsAndTicksHeight()
      }

      override fun paintTrack(g: Graphics) {
        // Track should not be painted.
      }

      override fun paintFocus(g: Graphics?) {
        // BasicSliderUI paints a dashed rect around the slider when it's focused. We shouldn't paint anything.
      }

      override fun paintThumb(g: Graphics) {
        g as Graphics2D
        g.color = JBColor(0x4A81FF, 0xB4D7FF)
        g.stroke = BasicStroke(1f)
        val halfWidth = thumbRect.width / 2
        g.drawLine(thumbRect.x + halfWidth, thumbRect.y, thumbRect.x + halfWidth, thumbRect.height + labelsAndTicksHeight());
      }

      override fun paintMajorTickForHorizSlider(g: Graphics, tickBounds: Rectangle, x: Int) {
        g as Graphics2D
        g.color = JBColor.border()
        g.drawLine(x, tickRect.y, x, tickRect.height);
      }

      override fun createTrackListener(slider: JSlider) = TimelineTrackListener()

      private fun labelsAndTicksHeight() = tickLength + heightOfTallestLabel

      /**
       * [Tracklistener] to allow setting [slider] value when clicking and scrubbing the timeline.
       */
      private inner class TimelineTrackListener : TrackListener() {

        override fun mousePressed(e: MouseEvent) {
          // We override the parent class behavior completely because it executes more operations than we need, being less performant than
          // this method. Since it recalculates the geometry of all components, the resulting UI on mouse press is not what we aim for.
          currentMouseX = e.getX()
          updateThumbLocationAndSliderValue()
        }

        override fun mouseDragged(e: MouseEvent) {
          super.mouseDragged(e)
          updateThumbLocationAndSliderValue()
        }

        fun updateThumbLocationAndSliderValue() {
          val halfWidth = thumbRect.width / 2
          // Make sure the thumb X coordinate is within the slider's min and max. Also, subtract half of the width so the center is aligned.
          val thumbX = Math.min(Math.max(currentMouseX, xPositionForValue(slider.minimum)), xPositionForValue(slider.maximum)) - halfWidth
          setThumbLocation(thumbX, thumbRect.y)
          slider.value = valueForXPosition(currentMouseX)
        }
      }
    }
  }
}