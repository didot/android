/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.surface

import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.common.editor.DesignerEditorPanel
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.ModelListener
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceListener
import com.android.tools.idea.common.surface.InteractionManager
import com.android.tools.idea.common.surface.Layer
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.analytics.NavLogEvent
import com.android.tools.idea.naveditor.analytics.TestNavUsageTracker
import com.android.tools.idea.naveditor.editor.NAV_EDITOR_ID
import com.android.tools.idea.naveditor.editor.NavEditor
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.scene.NavSceneManager
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.LayoutTestUtilities
import com.google.common.collect.ImmutableList
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.impl.ComponentManagerImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.registerComponentInstance
import com.intellij.ui.docking.DockManager
import com.intellij.util.indexing.UnindexedFilesUpdater
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.refactoring.setAndroidxProperties
import org.jetbrains.android.sdk.AndroidSdkData
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doCallRealMethod
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.io.File
import java.util.concurrent.Future
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JViewport
import kotlin.test.assertNotEquals

/**
 * Tests for [NavDesignSurface]
 */
class NavDesignSurfaceTest : NavTestCase() {

  fun testSwitchTabMetrics() {
    val model = model("nav.xml") { navigation() }
    val file = model.virtualFile
    val fileEditorManager = FileEditorManagerImpl(project)
    (project as ComponentManagerImpl).registerComponentInstance(FileEditorManager::class.java, fileEditorManager)

    val editors = fileEditorManager.openFile(file, true)
    val surface = editors.firstIsInstance<NavEditor>().component.surface
    // When the file is opened we create the surface synchronously but initialize the model asynchronously. We have to wait until it's set
    // to continue, since metrics logging depends on it.
    // If there's some problem, stop after three seconds.
    val startTime = System.currentTimeMillis()
    while (surface.model == null && System.currentTimeMillis() < startTime + TimeUnit.SECONDS.toMillis(3)) {
      UIUtil.dispatchAllInvocationEvents()
    }
    TestNavUsageTracker.create(surface.model!!).use { tracker ->
      fileEditorManager.setSelectedEditor(file, TextEditorProvider.getInstance().editorTypeId)
      verify(tracker).logEvent(NavEditorEvent.newBuilder().setType(NavEditorEvent.NavEditorEventType.SELECT_XML_TAB).build())
      fileEditorManager.setSelectedEditor(file, NAV_EDITOR_ID)
      verify(tracker).logEvent(NavEditorEvent.newBuilder().setType(NavEditorEvent.NavEditorEventType.SELECT_DESIGN_TAB).build())
    }
  }

  fun testOpenFileMetrics() {
    val surface = NavDesignSurface(project, project)

    val model = model("nav2.xml") {
      navigation {
        fragment("f1")
        activity("a1")
      }
    }
    TestNavUsageTracker.create(model).use { tracker ->
      surface.model = model

      val expectedEvent = NavLogEvent(NavEditorEvent.NavEditorEventType.OPEN_FILE, tracker)
        .withNavigationContents()
        .getProtoForTest()
      assertEquals(1, expectedEvent.contents.fragments)
      verify(tracker).logEvent(expectedEvent)
    }
  }

  private fun <T> any(): T = ArgumentMatchers.any() as T

  fun testSkipContentResize() {
    val surface = NavDesignSurface(project, myRootDisposable)
    surface.model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
      }
    }
    LayoutTestCase.assertFalse(surface.isSkipContentResize)
    surface.zoomToFit()
    LayoutTestCase.assertFalse(surface.isSkipContentResize)
    surface.zoom(ZoomType.ACTUAL)
    LayoutTestCase.assertTrue(surface.isSkipContentResize)
    surface.zoom(ZoomType.IN)
    LayoutTestCase.assertTrue(surface.isSkipContentResize)
    surface.zoomToFit()
    LayoutTestCase.assertFalse(surface.isSkipContentResize)
    surface.setScale(1.23, 100, 100)
    LayoutTestCase.assertTrue(surface.isSkipContentResize)
  }

  fun testLayers() {
    val droppedLayers: ImmutableList<Layer>

    val surface = NavDesignSurface(project, myRootDisposable)
    assertEmpty(surface.myLayers)

    val model = model("nav.xml") { navigation("root") }
    surface.model = model
    assertEquals(1, surface.myLayers.size)

    droppedLayers = ImmutableList.copyOf(surface.myLayers)
    surface.model = null
    assertEmpty(surface.myLayers)
    // Make sure all dropped layers are disposed.
    assertEmpty(droppedLayers.filter { layer -> !Disposer.isDisposed(layer) })
  }

  fun testComponentActivated() {
    val surface = NavDesignSurface(project, myRootDisposable)
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1", layout = "activity_main", name = "mytest.navtest.MainActivity")
        fragment("fragment2", layout = "fragment_blank", name = "mytest.navtest.BlankFragment")
      }
    }
    surface.model = model
    TestNavUsageTracker.create(model).use { tracker ->
      surface.notifyComponentActivate(model.find("fragment1")!!)
      val editorManager = FileEditorManager.getInstance(project)
      assertEquals("activity_main.xml", editorManager.openFiles[0].name)

      editorManager.closeFile(editorManager.openFiles[0])
      surface.notifyComponentActivate(model.find("fragment2")!!)
      assertEquals("fragment_blank.xml", editorManager.openFiles[0].name)
      verify(tracker, times(2)).logEvent(NavEditorEvent.newBuilder().setType(NavEditorEvent.NavEditorEventType.ACTIVATE_LAYOUT).build())
    }
  }

  fun testNoLayoutComponentActivated() {
    val surface = NavDesignSurface(project, myRootDisposable)
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1", name = "mytest.navtest.MainActivity")
        fragment("fragment2", name = "mytest.navtest.BlankFragment")
      }
    }
    surface.model = model
    TestNavUsageTracker.create(model).use { tracker ->
      surface.notifyComponentActivate(model.find("fragment1")!!)
      val editorManager = FileEditorManager.getInstance(project)
      assertEquals("MainActivity.java", editorManager.openFiles[0].name)
      editorManager.closeFile(editorManager.openFiles[0])
      surface.notifyComponentActivate(model.find("fragment2")!!)
      assertEquals("BlankFragment.java", editorManager.openFiles[0].name)
      verify(tracker, times(2)).logEvent(NavEditorEvent.newBuilder().setType(NavEditorEvent.NavEditorEventType.ACTIVATE_CLASS).build())
    }
  }

  fun testSubflowActivated() {
    val surface = NavDesignSurface(project, myRootDisposable)
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        navigation("subnav") {
          fragment("fragment2")
        }
      }
    }
    surface.model = model
    TestNavUsageTracker.create(model).use { tracker ->
      assertEquals(model.components[0], surface.currentNavigation)
      val subnav = model.find("subnav")!!
      surface.notifyComponentActivate(subnav)
      assertEquals(subnav, surface.currentNavigation)
      verify(tracker).logEvent(NavEditorEvent.newBuilder().setType(NavEditorEvent.NavEditorEventType.ACTIVATE_NESTED).build())
    }
  }

  fun testIncludeActivated() {
    val surface = NavDesignSurface(project, myRootDisposable)
    val model = model("nav.xml") {
      navigation("root") {
        include("navigation")
      }
    }
    surface.model = model
    TestNavUsageTracker.create(model).use { tracker ->
      surface.notifyComponentActivate(model.find("nav")!!)
      val editorManager = FileEditorManager.getInstance(project)
      assertEquals("navigation.xml", editorManager.openFiles[0].name)
      verify(tracker).logEvent(NavEditorEvent.newBuilder().setType(NavEditorEvent.NavEditorEventType.ACTIVATE_INCLUDE).build())
    }
  }

  fun testRootActivated() {
    val surface = NavDesignSurface(project, myRootDisposable)
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        navigation("subnav") {
          fragment("fragment2")
        }
      }
    }
    surface.model = model
    val modelListener = mock(ModelListener::class.java)
    val surfaceListener = mock(DesignSurfaceListener::class.java)
    model.addListener(modelListener)
    surface.addListener(surfaceListener)
    assertEquals(model.components[0], surface.currentNavigation)
    val root = model.find("root")!!
    surface.notifyComponentActivate(root)
    assertEquals(root, surface.currentNavigation)
    verifyZeroInteractions(modelListener)
    verifyZeroInteractions(surfaceListener)
  }

  fun testDoubleClickFragment() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1", layout = "activity_main")
        fragment("fragment2", layout = "activity_main2")
      }
    }

    val surface = model.surface as NavDesignSurface
    `when`(surface.layeredPane).thenReturn(mock(JComponent::class.java))
    val interactionManager = InteractionManager(surface)
    interactionManager.startListening()

    val view = NavView(surface, surface.sceneManager!!)
    `when`<SceneView>(surface.getSceneView(anyInt(), anyInt())).thenReturn(view)

    surface.scene!!.layout(0, SceneContext.get(view))
    val fragment = surface.scene!!.getSceneComponent("fragment1")!!
    val x = Coordinates.getSwingX(view, fragment.drawX) + 5
    val y = Coordinates.getSwingY(view, fragment.drawY) + 5
    LayoutTestUtilities.clickMouse(interactionManager, MouseEvent.BUTTON1, 2, x, y, 0)

    verify(surface).notifyComponentActivate(eq(fragment.nlComponent), anyInt(), anyInt())
  }

  fun testScrollToCenter() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        fragment("fragment2")
        fragment("fragment3")
      }
    }
    val surface = model.surface as NavDesignSurface
    val viewport = mock(JViewport::class.java)
    val scrollPane = mock(JScrollPane::class.java)
    `when`(surface.scrollPane).thenReturn(scrollPane)
    `when`(scrollPane.viewport).thenReturn(viewport)
    `when`(viewport.extentSize).thenReturn(Dimension(1000, 1000))
    val view = NavView(surface, surface.sceneManager!!)
    `when`<SceneView>(surface.currentSceneView).thenReturn(view)
    `when`(surface.scrollDurationMs).thenReturn(1)
    val scheduleRef = AtomicReference<Future<*>>()
    `when`(surface.scheduleRef).thenReturn(scheduleRef)
    doCallRealMethod().`when`(surface).scrollToCenter(any())
    val scrollPosition = Point()
    doAnswer { invocation ->
      scrollPosition.setLocation(invocation.getArgument(0), invocation.getArgument<Int>(1))
      null
    }.`when`(surface).setScrollPosition(anyInt(), anyInt())

    val f1 = model.find("fragment1")!!
    val f2 = model.find("fragment2")!!
    val f3 = model.find("fragment3")!!

    surface.scene!!.getSceneComponent(f1)!!.setPosition(0, 0)
    surface.scene!!.getSceneComponent(f2)!!.setPosition(100, 100)
    surface.scene!!.getSceneComponent(f3)!!.setPosition(200, 200)
    (surface.sceneManager as NavSceneManager).layout(false)

    verifyScroll(ImmutableList.of(f2), surface, scheduleRef, scrollPosition, -12, 14)
    verifyScroll(ImmutableList.of(f1, f2), surface, scheduleRef, scrollPosition, -37, -11)
    verifyScroll(ImmutableList.of(f1, f3), surface, scheduleRef, scrollPosition, -12, 14)
    verifyScroll(ImmutableList.of(f3), surface, scheduleRef, scrollPosition, 38, 64)
  }

  private fun verifyScroll(
    components: List<NlComponent>,
    surface: NavDesignSurface,
    scheduleRef: AtomicReference<Future<*>>,
    scrollPosition: Point,
    expectedX: Int,
    expectedY: Int
  ) {
    surface.scrollToCenter(components)

    while (scheduleRef.get() != null && !scheduleRef.get().isCancelled) {
      UIUtil.dispatchAllInvocationEvents()
    }
    assertEquals(Point(expectedX, expectedY), scrollPosition)
  }

  fun testDragSelect() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
        fragment("fragment2")
      }
    }

    val surface = model.surface as NavDesignSurface
    val scene = surface.scene!!
    scene.layout(0, SceneContext.get())
    val sceneView = NavView(surface, surface.sceneManager!!)
    `when`<SceneView>(surface.currentSceneView).thenReturn(sceneView)
    `when`<SceneView>(surface.getSceneView(anyInt(), anyInt())).thenReturn(sceneView)

    model.surface.selectionModel.setSelection(ImmutableList.of(model.find("fragment1")!!))

    val manager = InteractionManager(surface)
    manager.startListening()

    val fragment1 = scene.getSceneComponent("fragment1")!!
    val fragment2 = scene.getSceneComponent("fragment2")!!

    val rect1 = fragment1.fillDrawRect(0, null)
    rect1.grow(5, 5)
    dragSelect(manager, sceneView, rect1)
    assertTrue(fragment1.isSelected)
    assertFalse(fragment2.isSelected)
    dragRelease(manager, sceneView, rect1)
    assertTrue(fragment1.isSelected)
    assertFalse(fragment2.isSelected)

    val rect2 = fragment2.fillDrawRect(0, null)
    rect2.grow(5, 5)
    dragSelect(manager, sceneView, rect2)
    assertFalse(fragment1.isSelected)
    assertTrue(fragment2.isSelected)
    dragRelease(manager, sceneView, rect2)
    assertFalse(fragment1.isSelected)
    assertTrue(fragment2.isSelected)

    val rect3 = Rectangle()
    rect3.add(rect1)
    rect3.add(rect2)
    rect3.grow(5, 5)
    dragSelect(manager, sceneView, rect3)
    assertTrue(fragment1.isSelected)
    assertTrue(fragment2.isSelected)
    dragRelease(manager, sceneView, rect3)
    assertTrue(fragment1.isSelected)
    assertTrue(fragment2.isSelected)

    val rect4 = Rectangle(rect3.x + rect3.width + 10, rect3.y + rect3.height + 10, 100, 100)
    dragSelect(manager, sceneView, rect4)
    assertFalse(fragment1.isSelected)
    assertFalse(fragment2.isSelected)
    dragRelease(manager, sceneView, rect4)
    assertFalse(fragment1.isSelected)
    assertFalse(fragment2.isSelected)

    manager.stopListening()
  }

  fun testRefreshRoot() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
        navigation("subnav") {
          navigation("duplicate")
        }
        navigation("othersubnav") {
          navigation("duplicate")
        }
        fragment("oldfragment")
      }
    }

    val surface = NavDesignSurface(project, project)
    surface.model = model

    val root = model.components[0]
    assertEquals(root, surface.currentNavigation)
    surface.refreshRoot()
    assertEquals(root, surface.currentNavigation)

    val subnav = model.find("subnav")!!
    surface.currentNavigation = subnav
    surface.refreshRoot()
    assertEquals(subnav, surface.currentNavigation)

    val orig = model.find("othersubnav")!!.getChild(0)!!
    surface.currentNavigation = orig
    val model2 = model("nav.xml") {
      navigation("foo") {
        fragment("fragment1")
        navigation("subnav") {
          navigation("duplicate")
        }
        navigation("othersubnav") {
          navigation("duplicate")
        }
        activity("newactivity")
      }
    }

    NavSceneManager.updateHierarchy(model, model2)
    val newVersion = model.find("othersubnav")!!.getChild(0)!!
    assertNotEquals(orig, newVersion)
    surface.refreshRoot()
    assertEquals(newVersion, surface.currentNavigation)
  }

  // TODO: Add a similar test that manipulates the NlModel directly instead of changing the XML
  fun testUpdateXml() {
    val model = model("nav.xml") {
      navigation {
        navigation("navigation1") {
          fragment("fragment1")
        }
      }
    }

    val surface = NavDesignSurface(project, project)
    surface.model = model

    var root = model.components[0]
    assertEquals(root, surface.currentNavigation)

    var navigation1 = model.find("navigation1")!!
    surface.currentNavigation = navigation1
    assertEquals(navigation1, surface.currentNavigation)

    // Paste in the same xml and verify that the current navigation is unchanged
    WriteCommandAction.runWriteCommandAction(project) {
      val manager = PsiDocumentManager.getInstance(project)
      val document = manager.getDocument(model.file)!!
      document.setText("<navigation xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                       "            xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                       "    <navigation android:id=\"@+id/navigation1\" app:startDestination=\"@id/fragment1\">\n" +
                       "        <fragment android:id=\"@+id/fragment1\"/>\n" +
                       "    </navigation>\n" +
                       "</navigation>")
      manager.commitAllDocuments()
    }

    navigation1 = model.find("navigation1")!!
    assertEquals(navigation1, surface.currentNavigation)

    // Paste in xml that invalidates the current navigation and verify that the current navigation gets reset to the root
    WriteCommandAction.runWriteCommandAction(project) {
      val manager = PsiDocumentManager.getInstance(project)
      val document = manager.getDocument(model.file)!!
      document.setText("<navigation xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                       "            xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                       "    <xnavigation android:id=\"@+id/navigation1\" app:startDestination=\"@id/fragment1\">\n" +
                       "        <fragment android:id=\"@+id/fragment1\"/>\n" +
                       "    </xnavigation>\n" +
                       "</navigation>")
      manager.commitAllDocuments()
    }

    NavSceneManager.updateHierarchy(model, model)

    root = model.components[0]
    val component = surface.currentNavigation
    assertEquals(root, component)
  }

  fun testConfiguration() {
    val defaultConfigurationManager = ConfigurationManager.getOrCreateInstance(myFacet)
    val navConfigurationManager = NavDesignSurface(project, project).getConfigurationManager(myFacet)
    assertNotEquals(defaultConfigurationManager, navConfigurationManager)

    val navFile = VfsUtil.findFileByIoFile(File(project.basePath, "../unitTest/res/navigation/navigation.xml"), true)!!
    val defaultConfiguration = defaultConfigurationManager.getConfiguration(navFile)
    val navConfiguration = navConfigurationManager.getConfiguration(navFile)
    val navDevice = navConfiguration.device
    val pixelC = AndroidSdkData.getSdkData(myFacet)!!.deviceManager.getDevice("pixel_c", "Google")!!
    // in order to unset the cached derived device in the configuration you have to set it to something else first
    navConfiguration.setDevice(pixelC, false)
    navConfiguration.setDevice(null, false)

    // Select a device in the default (layout) ConfigurationManager. It shouldn't affect the nav editor device.
    defaultConfigurationManager.selectDevice(pixelC)

    assertEquals(navDevice, navConfiguration.device)
    assertEquals(pixelC, defaultConfiguration.device)
  }

  fun testActivateWithSchemaChange() {
    NavigationSchema.createIfNecessary(myModule)
    val editor = mock(DesignerEditorPanel::class.java)
    val surface = NavDesignSurface(project, editor, project)
    surface.model = model("nav.xml") { navigation() }
    @Suppress("UNCHECKED_CAST")
    val workbench = mock(WorkBench::class.java) as WorkBench<DesignSurface>
    `when`(editor.workBench).thenReturn(workbench)
    val lock = Semaphore(1)
    lock.acquire()
    // This should indicate that the relevant logic is complete
    `when`(workbench.hideLoading()).then { lock.release() }

    val navigator = addClass("import androidx.navigation.*;\n" +
                             "@Navigator.Name(\"activity_sub\")\n" +
                             "public class TestListeners extends ActivityNavigator {}\n")
    NavigationSchema.get(myModule).rebuildSchema().get()
    val initialSchema = NavigationSchema.get(myModule)

    updateContent(navigator, "import androidx.navigation.*;\n" +
                             "@Navigator.Name(\"activity_sub2\")\n" +
                             "public class TestListeners extends ActivityNavigator {}\n")

    surface.activate()
    // wait for the relevant logic to complete
    var completed = false
    for (i in 0..5) {
      UIUtil.dispatchAllInvocationEvents()
      if (lock.tryAcquire(1, TimeUnit.SECONDS)) {
        completed = true
        break
      }
    }
    assertTrue("hideLoading never executed", completed)
    assertNotEquals(initialSchema, NavigationSchema.get(myModule))
    verify(workbench).showLoading("Refreshing Navigators...")
    verify(workbench).hideLoading()
  }

  private fun addClass(@Language("JAVA") content: String): PsiClass {
    val result = WriteCommandAction.runWriteCommandAction(project, Computable<PsiClass> {
      myFixture.addClass(content)
    })
    WriteAction.runAndWait<RuntimeException> { PsiDocumentManager.getInstance(myModule.project).commitAllDocuments() }
    val dumbService = DumbService.getInstance(project)
    dumbService.queueTask(UnindexedFilesUpdater(project))
    dumbService.completeJustSubmittedTasks()
    return result
  }

  private fun updateContent(psiClass: PsiClass, @Language("JAVA") newContent: String) {
    WriteCommandAction.runWriteCommandAction(
      project) {
      try {
        psiClass.containingFile.virtualFile.setBinaryContent(newContent.toByteArray())
      }
      catch (e: Exception) {
        fail(e.message)
      }
    }
    WriteAction.runAndWait<RuntimeException> { PsiDocumentManager.getInstance(myModule.project).commitAllDocuments() }
    val dumbService = DumbService.getInstance(project)
    dumbService.queueTask(UnindexedFilesUpdater(project))
    dumbService.completeJustSubmittedTasks()
  }

  fun testActivateAddNavigator() {
    NavigationSchema.createIfNecessary(myModule)
    val surface = NavDesignSurface(project, mock(DesignerEditorPanel::class.java), project)
    surface.model = model("nav.xml") { navigation() }

    addClass("import androidx.navigation.*;\n" +
             "@Navigator.Name(\"activity_sub\")\n" +
             "public class TestListeners extends ActivityNavigator {}\n")
    val initialSchema = NavigationSchema.get(myModule)

    surface.activate()
    initialSchema.rebuildTask?.get()
    assertNotEquals(initialSchema, NavigationSchema.get(myModule))
  }

  fun testGetDependencies() {
    testDependencies(false, "android.arch.navigation")
    testDependencies(true, "androidx.navigation")
  }

  private fun testDependencies(androidX: Boolean, groupId: String) {
    WriteCommandAction.runWriteCommandAction(project) { project.setAndroidxProperties(androidX.toString()) }

    val dependencies = NavDesignSurface.getDependencies(myModule)
    val artifactIds = arrayOf("navigation-fragment", "navigation-ui")
    assertEquals(dependencies.count(), artifactIds.count())

    for (i in 0 until dependencies.count()) {
      assertEquals(groupId, dependencies[i].groupId)
      assertEquals(artifactIds[i], dependencies[i].artifactId)
    }
  }

  private fun dragSelect(manager: InteractionManager, sceneView: SceneView, @NavCoordinate rect: Rectangle) {
    @SwingCoordinate val x1 = Coordinates.getSwingX(sceneView, rect.x)
    @SwingCoordinate val y1 = Coordinates.getSwingY(sceneView, rect.y)
    @SwingCoordinate val x2 = Coordinates.getSwingX(sceneView, rect.x + rect.width)
    @SwingCoordinate val y2 = Coordinates.getSwingY(sceneView, rect.y + rect.height)

    LayoutTestUtilities.pressMouse(manager, MouseEvent.BUTTON1, x1, y1, 0)
    LayoutTestUtilities.dragMouse(manager, x1, y1, x2, y2, 0)
  }

  private fun dragRelease(manager: InteractionManager, sceneView: SceneView, @NavCoordinate rect: Rectangle) {
    @SwingCoordinate val x2 = Coordinates.getSwingX(sceneView, rect.x + rect.width)
    @SwingCoordinate val y2 = Coordinates.getSwingY(sceneView, rect.y + rect.height)

    LayoutTestUtilities.releaseMouse(manager, MouseEvent.BUTTON1, x2, y2, 0)
  }
}
