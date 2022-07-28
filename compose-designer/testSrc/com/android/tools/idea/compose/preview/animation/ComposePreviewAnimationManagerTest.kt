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
import androidx.compose.animation.tooling.ComposeAnimationType
import androidx.compose.animation.tooling.TransitionInfo
import com.android.SdkConstants
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.compose.preview.animation.TestUtils.createComposeAnimation
import com.android.tools.idea.compose.preview.animation.TestUtils.findLabel
import com.android.tools.idea.compose.preview.animation.managers.AnimationManager
import com.android.tools.idea.compose.preview.animation.managers.UnsupportedAnimationManager
import com.android.tools.idea.rendering.classloading.NopClassLocator
import com.android.tools.idea.rendering.classloading.PreviewAnimationClockMethodTransform
import com.android.tools.idea.rendering.classloading.loaders.AsmTransformingLoader
import com.android.tools.idea.rendering.classloading.loaders.ClassLoaderLoader
import com.android.tools.idea.rendering.classloading.loaders.DelegatingClassLoader
import com.android.tools.idea.rendering.classloading.toClassTransform
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.assertInstanceOf
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.containers.getIfSingle
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.uipreview.createUrlClassLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito
import java.awt.Dimension
import java.io.IOException
import java.util.stream.Collectors
import javax.swing.JSlider

@RunWith(Parameterized::class)
class ComposePreviewAnimationManagerTest(private val clockType: ClockType) {

  enum class ClockType {
    DEFAULT,
    WITH_TRANSITIONS,
    WITH_COORDINATION
  }

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var parentDisposable: Disposable

  private lateinit var surface: NlDesignSurface

  private val animations = listOf(createComposeAnimation("animation1", type = ComposeAnimationType.ANIMATED_VISIBILITY),
                                  createComposeAnimation("animation2", type = ComposeAnimationType.TRANSITION_ANIMATION),
                                  createComposeAnimation("animation3", type = ComposeAnimationType.ANIMATED_VALUE),
                                  createComposeAnimation("animation4", type = ComposeAnimationType.TRANSITION_ANIMATION))

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "Clock type:{0}")
    fun parameters() = listOf(arrayOf<Any>(ClockType.DEFAULT),
                              arrayOf<Any>(ClockType.WITH_TRANSITIONS),
                              arrayOf<Any>(ClockType.WITH_COORDINATION))
  }

  private fun getClock(): TestClock =
    when (clockType) {
      ClockType.DEFAULT -> TestClock()
      ClockType.WITH_TRANSITIONS -> TestClockWithTransitions()
      ClockType.WITH_COORDINATION -> TestClockWithCoordination()
    }


  @Before
  fun setUp() {
    parentDisposable = projectRule.fixture.testRootDisposable
    val model = runInEdtAndGet {
      NlModelBuilderUtil.model(
        projectRule,
        "layout",
        "layout.xml",
        ComponentDescriptor(SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER)
      ).build()
    }
    surface = NlDesignSurface.builder(projectRule.project, parentDisposable).build()
    surface.addModelWithoutRender(model)
  }

  @After
  fun tearDown() {
    ComposePreviewAnimationManager.closeCurrentInspector()
  }

  @Test
  fun subscribeAndUnsubscribe() {
    createAndOpenInspector()

    val animation = createComposeAnimation()
    assertTrue(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())

    ComposePreviewAnimationManager.onAnimationSubscribed(getClock(), animation)
    assertFalse(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())

    val otherAnimation = createComposeAnimation()
    ComposePreviewAnimationManager.onAnimationUnsubscribed(otherAnimation)
    assertFalse(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())

    ComposePreviewAnimationManager.onAnimationUnsubscribed(animation)
    assertTrue(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())
  }

  @Test
  fun closingInspectorClearsSubscriptions() {
    createAndOpenInspector()

    ComposePreviewAnimationManager.onAnimationSubscribed(getClock(), createComposeAnimation())
    assertFalse(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())

    ComposePreviewAnimationManager.closeCurrentInspector()
    assertTrue(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())
  }

  @Test
  fun noAnimationsPanelShownWhenNoAnimationsAreSubscribed() {
    val inspector = createAndOpenInspector()
    UIUtil.pump() // Wait for UI to dispatch all events
    // When first opening the inspector, we show the panel informing there are no supported animations to be displayed
    assertNotNull(inspector.noAnimationsPanel())
    assertNull(inspector.tabbedPane.parent)
    assertEquals(0, inspector.tabCount())

    // After subscribing an animation, we should display the tabbedPane
    val animation = createComposeAnimation()
    ComposePreviewAnimationManager.onAnimationSubscribed(getClock(), animation)
    UIUtil.pump() // Wait for the tab to be added on the UI thread
    assertNull(inspector.noAnimationsPanel())
    assertNotNull(inspector.tabbedPane.parent)
    assertEquals(1, inspector.tabCount())

    // After unsubscribing all animations, we should hide the tabbed panel and again display the no animations panel
    ComposePreviewAnimationManager.onAnimationUnsubscribed(animation)
    UIUtil.pump() // Wait for the tab to be removed on the UI thread
    assertNotNull(inspector.noAnimationsPanel())
    assertNull(inspector.tabbedPane.parent)
    assertEquals(0, inspector.tabCount())
  }

  @Test
  fun oneTabPerSubscribedAnimation() {
    val inspector = createAndOpenInspector()
    assertNull(inspector.tabbedPane.parent)
    assertEquals(0, inspector.tabCount())

    val animation1 = createComposeAnimation()
    val clock = getClock()
    ComposePreviewAnimationManager.onAnimationSubscribed(clock, animation1)
    UIUtil.pump() // Wait for the tab to be added on the UI thread
    assertNotNull(inspector.tabbedPane.parent)
    assertEquals(1, inspector.tabCount())

    val animation2 = createComposeAnimation()
    ComposePreviewAnimationManager.onAnimationSubscribed(clock, animation2)
    UIUtil.pump() // Wait for the tab to be added on the UI thread
    assertEquals(2, inspector.tabCount())

    ComposePreviewAnimationManager.onAnimationUnsubscribed(animation1)
    UIUtil.pump() // Wait for the tab to be removed on the UI thread
    assertEquals(1, inspector.tabCount())
  }

  @Test
  fun subscriptionNewClockClearsPreviousClockAnimations() {
    val inspector = createAndOpenInspector()
    assertNull(inspector.tabbedPane.parent)
    assertEquals(0, inspector.tabCount())

    val clock = getClock()
    ComposePreviewAnimationManager.onAnimationSubscribed(clock, createComposeAnimation())
    UIUtil.pump() // Wait for the tab to be added on the UI thread
    assertNotNull(inspector.tabbedPane.parent)
    assertEquals(1, inspector.tabCount())

    val anotherClock = getClock()
    ComposePreviewAnimationManager.onAnimationSubscribed(anotherClock, createComposeAnimation())
    UIUtil.pump() // Wait for the tab to be added on the UI thread
    assertEquals(1, inspector.tabCount())

    ComposePreviewAnimationManager.onAnimationSubscribed(anotherClock, createComposeAnimation())
    UIUtil.pump() // Wait for the tab to be added on the UI thread
    assertEquals(2, inspector.tabCount())
  }

  @Test
  fun onOpenNewInspectorCallbackClearedWhenClosingInspector() {
    var callbackCalls = 0
    ComposePreviewAnimationManager.createAnimationInspectorPanel(surface, parentDisposable) { callbackCalls++ }
    ComposePreviewAnimationManager.onAnimationInspectorOpened()
    ComposePreviewAnimationManager.onAnimationInspectorOpened()
    assertEquals(2, callbackCalls)

    ComposePreviewAnimationManager.closeCurrentInspector()
    ComposePreviewAnimationManager.onAnimationInspectorOpened()
    assertEquals(2, callbackCalls)
  }

  @Test
  fun comboBoxesDisplayComposeAnimationStates() {
    val inspector = createAndOpenInspector()

    val animationStates = setOf("State1", "State2", "State3")

    val transitionAnimation = object : ComposeAnimation {
      override val animationObject = object {
        @Suppress("unused") // Method is called via reflection.
        fun getCurrentState() = "State1"
      }
      override val type = ComposeAnimationType.TRANSITION_ANIMATION
      override val states = animationStates
    }

    ComposePreviewAnimationManager.onAnimationSubscribed(getClock(), transitionAnimation)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    // We can get any of the combo boxes, since "from" and "to" states should be the same.
    val stateComboBoxes = TreeWalker(inspector.component).descendantStream().filter { it is ComboBox<*> }.collect(Collectors.toList())
    assertEquals(2, stateComboBoxes.size) // "start" combobox and  "end" combobox.
    val startStateComboBox = stateComboBoxes[0] as ComboBox<*>
    val endStateComboBox = stateComboBoxes[1] as ComboBox<*>

    assertEquals(3, startStateComboBox.itemCount)
    assertEquals("State1", startStateComboBox.getItemAt(0))
    assertEquals("State2", startStateComboBox.getItemAt(1))
    assertEquals("State3", startStateComboBox.getItemAt(2))

    assertEquals("State1", startStateComboBox.selectedItem)
    // The "end" combo box does not display the same state as the "start" combo box if possible
    assertEquals("State2", endStateComboBox.selectedItem)

    // Change state of the comboBox.
    startStateComboBox.selectedItem = "State2"
    UIUtil.pump() // Wait for all changes in UI thread

    // Change state of the comboBox back to previous state - cached transition info should be used.
    startStateComboBox.selectedItem = "State1"
    UIUtil.pump() // Wait for all changes in UI thread
  }

  @Test
  fun animatedVisibilityComboBoxDisplayAllVisibilityStates() {
    val inspector = createAndOpenInspector()

    val animatedVisibilityAnimation = object : ComposeAnimation {
      override val animationObject = Any()
      override val type = ComposeAnimationType.ANIMATED_VISIBILITY
      override val states = setOf("Enter", "Exit")
    }

    ComposePreviewAnimationManager.onAnimationSubscribed(getClock(), animatedVisibilityAnimation)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    val stateComboBoxes = TreeWalker(inspector.component).descendantStream().filter { it is ComboBox<*> }.collect(Collectors.toList())
    assertEquals(1, stateComboBoxes.size) // AnimatedVisibility has a single combo box
    val animatedVisibilityComboBox = stateComboBoxes[0] as ComboBox<*>

    assertEquals(2, animatedVisibilityComboBox.itemCount)
    assertEquals("Enter", animatedVisibilityComboBox.getItemAt(0))
    assertEquals("Exit", animatedVisibilityComboBox.getItemAt(1))
    assertEquals("Enter", animatedVisibilityComboBox.selectedItem)

    // Change state of the comboBox.
    animatedVisibilityComboBox.selectedItem = "Exit"
    UIUtil.pump() // Wait for all changes in UI thread

    // Change state of the comboBox back to previous state - cached transition info should be used.
    animatedVisibilityComboBox.selectedItem = "Enter"
    UIUtil.pump() // Wait for all changes in UI thread
  }

  @Test
  fun changeClockTime() {
    val inspector = createAndOpenInspector()

    val transitionAnimation = object : ComposeAnimation {
      override val animationObject = object {
        @Suppress("unused") // Method is called via reflection.
        fun getCurrentState() = "State1"
      }
      override val type = ComposeAnimationType.TRANSITION_ANIMATION
      override val states = setOf("State1", "State2", "State3")
    }

    ComposePreviewAnimationManager.onAnimationSubscribed(getClock(), transitionAnimation)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    // We can get any of the combo boxes, since "from" and "to" states should be the same.
    val sliders = TreeWalker(inspector.component).descendantStream().filter { it is JSlider }.collect(Collectors.toList())
    assertEquals(1, sliders.size) //
    val timelineSlider = sliders[0] as JSlider
    timelineSlider.value = 100
    UIUtil.pump() // Wait for all changes in UI thread
    timelineSlider.value = 200
    UIUtil.pump() // Wait for all changes in UI thread
  }

  @Test
  fun playbackControlActions() {
    val numberOfPlaybackControls = 7
    val inspector = createAndOpenInspector()

    val transitionAnimation = object : ComposeAnimation {
      override val animationObject = object {
        @Suppress("unused") // Method is called via reflection.
        fun getCurrentState() = "State1"
      }
      override val type = ComposeAnimationType.TRANSITION_ANIMATION
      override val states = setOf("State1", "State2", "State3")
    }

    ComposePreviewAnimationManager.onAnimationSubscribed(getClock(), transitionAnimation)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    val toolbars = TreeWalker(inspector.component).descendantStream().filter { it is ActionToolbarImpl }.collect(
      Collectors.toList()).map { it as ActionToolbarImpl }
    val playbackControls = toolbars.firstOrNull { it.place == "Animation Preview" }
    assertNotNull(playbackControls)
    assertEquals(numberOfPlaybackControls, playbackControls!!.actions.size)
    val actionEvent = Mockito.mock(AnActionEvent::class.java)
    // Press loop
    val loopAction = playbackControls.actions[0] as ToggleAction
    loopAction.setSelected(actionEvent, true)
    UIUtil.pump() // Wait for all changes in UI thread
    // Play and pause
    val playAction = playbackControls.actions[2]
    playAction.actionPerformed(actionEvent)
    UIUtil.pump() // Wait for all changes in UI thread
    playAction.actionPerformed(actionEvent)
    UIUtil.pump() // Wait for all changes in UI thread
    // Go to start.
    val goToStart = playbackControls.actions[1]
    goToStart.actionPerformed(actionEvent)
    UIUtil.pump() // Wait for all changes in UI thread
    // Go to end.
    val toToEnd = playbackControls.actions[3]
    toToEnd.actionPerformed(actionEvent)
    UIUtil.pump() // Wait for all changes in UI thread
    // Un-press loop
    loopAction.setSelected(actionEvent, false)
    UIUtil.pump() // Wait for all changes in UI thread
  }

  @Test
  fun resizeInspector() {
    val inspector = createAndOpenInspector()

    val transitionAnimation = object : ComposeAnimation {
      override val animationObject = object {
        @Suppress("unused") // Method is called via reflection.
        fun getCurrentState() = "State1"
      }
      override val type = ComposeAnimationType.TRANSITION_ANIMATION
      override val states = setOf("State1", "State2", "State3")
    }

    ComposePreviewAnimationManager.onAnimationSubscribed(getClock(), transitionAnimation)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    inspector.component.setSize(inspector.component.size.width * 2, inspector.component.size.height * 2)
    UIUtil.pump() // Wait for all changes in UI thread
  }

  @Test
  fun animationStatesInferredForBoolean() {
    val inspector = createAndOpenInspector()
    val transitionAnimation = object : ComposeAnimation {
      override val animationObject = Any()
      override val type = ComposeAnimationType.TRANSITION_ANIMATION
      override val states = setOf(true) // Note that `false` is not provided
    }

    ComposePreviewAnimationManager.onAnimationSubscribed(getClock(), transitionAnimation)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    // We can get any of the combo boxes, since "from" and "to" states should be the same.
    val stateComboBoxes = TreeWalker(inspector.component).descendantStream().filter { it is ComboBox<*> }.collect(Collectors.toList())
    val startStateComboBox = stateComboBoxes[0] as ComboBox<*>

    assertEquals(2, startStateComboBox.itemCount)
    assertEquals(true, startStateComboBox.getItemAt(0))
    assertEquals(false, startStateComboBox.getItemAt(1)) // false inferred because the animation states received had a boolean
  }

  @Test
  fun tabsAreNamedFromAnimationLabel() {
    val inspector = createAndOpenInspector()
    val clock = getClock()

    val animation1 = createComposeAnimation("repeatedLabel")
    ComposePreviewAnimationManager.onAnimationSubscribed(clock, animation1)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    val animationWithSameLabel = createComposeAnimation("repeatedLabel")
    ComposePreviewAnimationManager.onAnimationSubscribed(clock, animationWithSameLabel)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    val animatedValueWithNullLabel = createComposeAnimation(type = ComposeAnimationType.ANIMATED_VALUE)
    ComposePreviewAnimationManager.onAnimationSubscribed(clock, animatedValueWithNullLabel)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    val transitionAnimationWithNullLabel = createComposeAnimation(type = ComposeAnimationType.TRANSITION_ANIMATION)
    ComposePreviewAnimationManager.onAnimationSubscribed(clock, transitionAnimationWithNullLabel)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    val animatedVisibilityWithNullLabel = createComposeAnimation(type = ComposeAnimationType.ANIMATED_VISIBILITY)
    ComposePreviewAnimationManager.onAnimationSubscribed(clock, animatedVisibilityWithNullLabel)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    assertEquals(5, inspector.tabCount())

    assertEquals("repeatedLabel", inspector.getAnimationTitleAt(0))
    assertEquals("repeatedLabel (1)", inspector.getAnimationTitleAt(1)) // repeated titles get their index incremented
    assertEquals("Animated Value", inspector.getAnimationTitleAt(2)) // null labels use default title
    assertEquals("Transition Animation", inspector.getAnimationTitleAt(3)) // null labels use default title
    assertEquals("Animated Visibility", inspector.getAnimationTitleAt(4)) // null labels use default title
  }

  @Test
  fun `cards and timeline elements are added to coordination panel`() {
    val inspector = createAndOpenInspector()
    val clock = getClock()
    animations.forEach { ComposePreviewAnimationManager.onAnimationSubscribed(clock, it) }
    UIUtil.pump() // Wait for cards to be added on the UI thread
    val cards = TestUtils.findAllCards(inspector.component)
    val timeline = TestUtils.findTimeline(inspector.component)

    invokeAndWaitIfNeeded {
      // 4 cards and 4 TimelineElements are added to coordination panel.
      assertEquals(4, cards.size)
      assertInstanceOf<AnimationCard>(cards[0])
      assertInstanceOf<AnimationCard>(cards[1])
      assertInstanceOf<LabelCard>(cards[2])
      assertInstanceOf<AnimationCard>(cards[3])
      assertEquals(4, timeline.sliderUI.elements.size)
      // Only coordination tab is opened.
      assertEquals(1, inspector.tabbedPane.tabCount)
    }
  }
  @Test
  fun `managers are created for each animation`() {
    val inspector = createAndOpenInspector()
    val clock = getClock()
    animations.forEach { ComposePreviewAnimationManager.onAnimationSubscribed(clock, it) }
    UIUtil.pump() // Wait for cards to be added on the UI thread
    assertEquals(4, inspector.animations.size)
    assertInstanceOf<AnimationManager>(inspector.animations[0])
    assertInstanceOf<AnimationManager>(inspector.animations[1])
    assertInstanceOf<UnsupportedAnimationManager>(inspector.animations[2])
    assertInstanceOf<AnimationManager>(inspector.animations[3])
  }
  @Test
  fun `preview inspector`() {
    val inspector = createAndOpenInspector()
    val clock = getClock()
    animations.forEach { ComposePreviewAnimationManager.onAnimationSubscribed(clock, it) }
    UIUtil.pump() // Wait for cards to be added on the UI thread

    invokeAndWaitIfNeeded {
      val ui = FakeUi(inspector.apply { component.size = Dimension(600, 500) }.component)
      ui.updateToolbars()
      ui.layoutAndDispatchEvents()
      // Uncomment to preview.
      //ui.render()
    }
  }

  @Test
  fun invalidateInspectorShouldClearTabsAndShowNoAnimationsPanel() {
    val inspector = createAndOpenInspector()
    ComposePreviewAnimationManager.onAnimationSubscribed(getClock(), createComposeAnimation())
    UIUtil.pump() // Wait for the tab to be added on the UI
    assertNotNull(inspector.tabbedPane.parent)
    assertEquals(1, inspector.tabCount())
    assertNull(inspector.noAnimationsPanel())
    assertEquals(1, inspector.animationPreviewCardsCount())

    ComposePreviewAnimationManager.invalidate()
    UIUtil.pump() // Wait for the tab to be added on the UI
    assertNotNull(inspector.noAnimationsPanel())
    assertNull(inspector.tabbedPane.parent)
    assertEquals(0, inspector.tabCount())
    assertEquals(0, inspector.animationPreviewCardsCount())
  }

  @Test
  @Throws(IOException::class, ClassNotFoundException::class)
  fun classLoaderRedirectsSubscriptionToAnimationManager() {
    class PreviewAnimationClockClassLoader : DelegatingClassLoader(this.javaClass.classLoader,
                                                                   AsmTransformingLoader(
                                                                     toClassTransform({ PreviewAnimationClockMethodTransform(it) }),
                                                                     ClassLoaderLoader(
                                                                       createUrlClassLoader(listOf(
                                                                         resolveWorkspacePath(
                                                                           "tools/adt/idea/compose-designer/testData/classloader").resolve(
                                                                           "composeanimation.jar")
                                                                       ))
                                                                     ),
                                                                     NopClassLocator
                                                                   )) {
      fun loadPreviewAnimationClock(): Class<*> =
        loadClass("androidx.compose.ui.tooling.animation.PreviewAnimationClock")
    }
    createAndOpenInspector()

    val previewAnimationClockClassLoader = PreviewAnimationClockClassLoader()
    val previewAnimationClock = previewAnimationClockClassLoader.loadPreviewAnimationClock()
    val notifySubscribe = previewAnimationClock.getDeclaredMethod("notifySubscribe", ComposeAnimation::class.java)
    val animation = createComposeAnimation()
    notifySubscribe.invoke(previewAnimationClock.newInstance(), animation)
    assertFalse(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())

    val notifyUnsubscribe = previewAnimationClock.getDeclaredMethod("notifyUnsubscribe", ComposeAnimation::class.java)
    notifyUnsubscribe.invoke(previewAnimationClock.newInstance(), animation)
    assertTrue(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())
  }

  @Test
  fun animationClockWrapsComposeClockViaReflection() {
    val animationClock = AnimationClock(getClock())
    // Check that we can find a couple of methods from TestClock
    assertNotNull(animationClock.getAnimatedPropertiesFunction)
    assertNotNull(animationClock.updateAnimatedVisibilityStateFunction)
    // getAnimatedVisibilityState is mangled in TestClock, but we should be able to find it.
    assertNotNull(animationClock.getAnimatedVisibilityStateFunction)

    try {
      // We should throw an Exception if we can't find the given function in the underlying clock, and it's up to the caller to handle this.
      animationClock.findClockFunction("unknownFunction")
      fail("Expected to fail, as `unknownFunction` is not a function of TestClock.")
    }
    catch (ignored: NullPointerException) {
    }

    // getAnimatedVisibilityState is a supported function, but its name is mangled. We should find it when looking for the function without
    // the hash suffix, not when we specify it.
    assertNotNull(animationClock.findClockFunction("getAnimatedVisibilityState"))
    try {
      animationClock.findClockFunction("getAnimatedVisibilityState-xga21d")
      fail("Expected to fail, as `getAnimatedVisibilityState-xga21d` should not be found when looking for the mangled name.")
    }
    catch (ignored: NullPointerException) {
    }
  }

  private fun createAndOpenInspector(): AnimationPreview {
    assertFalse(ComposePreviewAnimationManager.isInspectorOpen())
    ComposePreviewAnimationManager.createAnimationInspectorPanel(surface, parentDisposable) { }
    assertTrue(ComposePreviewAnimationManager.isInspectorOpen())
    return ComposePreviewAnimationManager.currentInspector!!
  }

  private fun AnimationPreview.tabCount() = invokeAndWaitIfNeeded { animationsCount() }

  private fun AnimationPreview.getAnimationTitleAt(index: Int): String = invokeAndWaitIfNeeded {
    TestUtils.findAllCards(this.component)[index].findLabel().text
  }

  private fun AnimationPreview.noAnimationsPanel() =
    TreeWalker(this.component).descendantStream().filter { it.name == "Loading Animations Panel" }.getIfSingle()

  private fun AnimationPreview.animationPreviewCardsCount() = invokeAndWaitIfNeeded { coordinationTab.cards.size }


  /**
   * [TestClock] with available [setClockTimes] method.
   */
  private class TestClockWithCoordination : TestClockWithTransitions() {
    fun setClockTimes(clockTimeMillis: Map<ComposeAnimation, Long>) {}
  }


  /**
   * [TestClock] with available [getTransitions] method.
   */
  private open class TestClockWithTransitions : TestClock() {
    fun getTransitions(animation: Any, clockTimeMsStep: Long) = listOf(
      TransitionInfo("Int", "specType",
                     startTimeMillis = 0,
                     endTimeMillis = 100,
                     values = mapOf(0L to 1, 50L to 2, 100L to 3)),
      TransitionInfo("IntSnap", "Snap",
                     startTimeMillis = 0,
                     endTimeMillis = 0,
                     values = mapOf(0L to 100)),
      TransitionInfo("Float", "specType",
                     startTimeMillis = 100,
                     endTimeMillis = 200,
                     values = mapOf(100L to 1f, 150L to 0f, 200L to 2f)),
      TransitionInfo("Double", "specType",
                     startTimeMillis = 0,
                     endTimeMillis = 100,
                     values = mapOf(0L to 1.0, 50L to 10.0, 100L to 2.0))
    )
  }

  /**
   * Fake class with methods matching PreviewAnimationClock method signatures, so the code doesn't break when the test tries to call them
   * via reflection.
   */
  private open class TestClock {
    fun getAnimatedProperties(animation: Any) = listOf<ComposeAnimatedProperty>(
      ComposeAnimatedProperty("Int", 1),
      ComposeAnimatedProperty("IntSnap", 1),
      ComposeAnimatedProperty("Float", 1f),
      ComposeAnimatedProperty("Double", 1.0)
    )

    fun getMaxDuration() = 1000L
    fun getMaxDurationPerIteration() = 1000L
    fun updateAnimationStates() {}
    fun updateSeekableAnimation(animation: Any, fromState: Any, toState: Any) {}
    fun setClockTime(time: Long) {}
    fun updateAnimatedVisibilityState(animation: Any, state: Any) {}
    fun `getAnimatedVisibilityState-xga21d`(animation: Any) = "Enter"
  }
}