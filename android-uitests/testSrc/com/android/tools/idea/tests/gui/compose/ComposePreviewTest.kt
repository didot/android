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
package com.android.tools.idea.tests.gui.compose

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.CommandHandler
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.tools.idea.bleak.UseBleak
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.RunToolWindowFixture
import com.android.tools.idea.tests.gui.framework.fixture.compose.getNotificationsFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.SplitEditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.getSplitEditorFixture
import com.android.tools.idea.tests.gui.uibuilder.RenderTaskLeakCheckRule
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import icons.StudioIcons
import junit.framework.TestCase.assertFalse
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.fixture.JPopupMenuFixture
import org.fest.swing.timing.Wait
import org.fest.swing.util.PatternTextMatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.KeyEvent
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.swing.JMenuItem

@RunWith(GuiTestRemoteRunner::class)
class ComposePreviewTest {
  @get:Rule
  val guiTest = GuiTestRule().withTimeout(5, TimeUnit.MINUTES)
  @get:Rule
  val renderTaskLeakCheckRule = RenderTaskLeakCheckRule()

  private val commandHandler = DeployPreviewCommandHandler()

  @get:Rule
  val adbRule: FakeAdbRule = FakeAdbRule().initAbdBridgeDuringSetup(false).withDeviceCommandHandler(commandHandler)

  @Before
  fun setUp() {
    StudioFlags.NELE_SCENEVIEW_TOP_TOOLBAR.override(true)
    StudioFlags.COMPOSE_PREVIEW.override(true)
    StudioFlags.COMPOSE_ANIMATED_PREVIEW.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.NELE_SCENEVIEW_TOP_TOOLBAR.clearOverride()
    StudioFlags.COMPOSE_PREVIEW.clearOverride()
    StudioFlags.COMPOSE_ANIMATED_PREVIEW.clearOverride()
  }

  private fun openComposePreview(fixture: IdeFrameFixture, fileName: String = "MainActivity.kt"): SplitEditorFixture {
    // Open the main compose activity and check that the preview is present
    val editor = fixture.editor
    val file = "app/src/main/java/google/simpleapplication/$fileName"
    editor.open(file)

    fixture.invokeAndWaitForBuildAction("Build", "Make Project")

    return editor.getSplitEditorFixture()
  }

  @Test
  @Throws(Exception::class)
  fun testOpenAndClosePreview() {
    openAndClosePreview(guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleComposeApplication"))
  }

  @Test
  @Throws(Exception::class)
  fun testCopyPreviewImage() {
    val fixture = guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleComposeApplication")
    val composePreview = openComposePreview(fixture)

    composePreview
      .waitForRenderToFinish()
      .getNotificationsFixture()
      .assertNoNotifications()

    assertFalse(composePreview.hasRenderErrors())

    clearClipboard()
    assertFalse(Toolkit.getDefaultToolkit().systemClipboard.getContents(this).isDataFlavorSupported(DataFlavor.imageFlavor))

    val designSurfaceTarget = composePreview.designSurface.target()
    composePreview.robot.click(designSurfaceTarget)
    JPopupMenuFixture(composePreview.robot(), composePreview.robot.showPopupMenu(designSurfaceTarget))
      .menuItem(object : GenericTypeMatcher<JMenuItem>(JMenuItem::class.java) {
        override fun isMatching(component: JMenuItem): Boolean {
          return "Copy Image" == component.text
        }
      }).click()

    assertTrue(getClipboardContents().isDataFlavorSupported(DataFlavor.imageFlavor))

    fixture.editor.close()
  }

  /**
   * Returns the current system clipboard contents.
   */
  private fun getClipboardContents(): Transferable = Toolkit.getDefaultToolkit().systemClipboard.getContents(this)

  /**
   * Clears the system clipboard by copying an "Empty" [Transferable]. This can be used to verify that copy operations of other elements
   * do succeed.
   */
  private fun clearClipboard() {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(object : Transferable {
      override fun getTransferData(flavor: DataFlavor?): Any = when (flavor) {
        DataFlavor.stringFlavor -> "Empty"
        else -> throw UnsupportedFlavorException(flavor)
      }

      override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor == DataFlavor.stringFlavor

      override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.stringFlavor)

    }, null)
  }

  @Test
  @UseBleak
  @RunIn(TestGroup.PERFORMANCE)
  @Throws(Exception::class)
  fun testOpenAndClosePreviewWithBleak() {
    val fixture = guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleComposeApplication")
    guiTest.runWithBleak { openAndClosePreview(fixture) }
  }

  @Throws(Exception::class)
  private fun openAndClosePreview(fixture: IdeFrameFixture) {
    val composePreview = openComposePreview(fixture)

    composePreview
      .waitForRenderToFinish()
      .getNotificationsFixture()
      .assertNoNotifications()

    assertFalse(composePreview.hasRenderErrors())

    // Verify that the element rendered correctly by checking it's not empty
    val previewElementRender = composePreview.designSurface.scene.sceneComponents.single()
    assertTrue(previewElementRender.width > 10 && previewElementRender.height > 10)

    val editor = fixture.editor

    // Now let's make a change on the source code and check that the notification displays
    val modification = "Random modification"
    editor.typeText(modification)

    guiTest.robot().waitForIdle()

    composePreview
      .getNotificationsFixture()
      .waitForNotificationContains("out of date")

    // Undo modifications and close editor to return to the initial state
    editor.select("(${modification})")
    editor.invokeAction(EditorFixture.EditorAction.BACK_SPACE)
    editor.close()
  }

  @Test
  @Throws(Exception::class)
  fun testRemoveExistingPreview() {
    val fixture = guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleComposeApplication")
    val composePreview = openComposePreview(fixture)

    composePreview
      .waitForRenderToFinish()
      .getNotificationsFixture()
      .assertNoNotifications()

    assertFalse(composePreview.hasRenderErrors())

    val editor = fixture.editor
    editor.select("(@Preview)")
    editor.invokeAction(EditorFixture.EditorAction.BACK_SPACE)
    guiTest.ideFrame().invokeMenuPath("Code", "Optimize Imports") // This will remove the Preview import
    composePreview
      .designSurface
      .waitUntilNotShowing(Wait.seconds(10));

    editor.close()
  }

  @Test
  @RunIn(TestGroup.UNRELIABLE) // b/149464527
  @Throws(Exception::class)
  fun testAddAdditionalPreview() {
    val fixture = guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleComposeApplication")
    val composePreview = openComposePreview(fixture)

    composePreview
      .waitForRenderToFinish()
      .getNotificationsFixture()
      .assertNoNotifications()

    assertFalse(composePreview.hasRenderErrors())
    assertEquals(1, composePreview.designSurface.allSceneViews.count())

    val editor = fixture.editor
    editor.invokeAction(EditorFixture.EditorAction.TEXT_END)
      .pressAndReleaseKeys(KeyEvent.VK_ENTER)
      // The closing braces are not needed since they are added by the editor automatically
      .typeText("""
        @Preview(name = "Second")
        @Composable
        fun SecondPreview() {
          MaterialTheme {
            Text(text = "A second preview")
      """.trimIndent())

    composePreview
      .getNotificationsFixture()
      .waitForNotificationContains("out of date")

    fixture.actAndWaitForBuildToFinish {
      composePreview
        .findActionButtonByText("Build  Refresh")
        .waitUntilEnabledAndShowing()
        .click()
    }

    composePreview.waitForRenderToFinish()

    // Check the new preview has been added
    assertEquals(2, composePreview.designSurface.allSceneViews.count())

    editor.close()
  }

  @Test
  @Throws(Exception::class)
  fun testInteractiveSwitch() {
    val fixture = guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleComposeApplication")
    val composePreview = openComposePreview(fixture, "MultipleComposePreviews.kt")

    composePreview
      .waitForRenderToFinish()
      .getNotificationsFixture()
      .assertNoNotifications()

    assertEquals(3, composePreview.designSurface
      .allSceneViews
      .size)

    composePreview.designSurface
      .allSceneViews
      .first()
      .toolbar()
      .clickButtonByIcon(StudioIcons.Compose.INTERACTIVE_PREVIEW)

    composePreview
      .waitForRenderToFinish()

    assertEquals(1, composePreview.designSurface
      .allSceneViews
      .size)

    composePreview
      .findActionButtonByText("Stop Interactive Preview")
      .click()

    composePreview
      .waitForRenderToFinish()

    assertEquals(3, composePreview.designSurface
      .allSceneViews
      .size)

    fixture.editor.close()
  }

  @Test
  @RunIn(TestGroup.UNRELIABLE) // b/150391302
  @Throws(Exception::class)
  fun testDeployPreview() {
    val fixture = guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleComposeApplication")

    // Enable the fake ADB server and attach a fake device to which the preview will be deployed.
    AndroidDebugBridge.enableFakeAdbServerMode(adbRule.fakeAdbServerPort)
    adbRule.attachDevice("42", "Google", "Pix3l", "versionX", "29", DeviceState.HostConnectionType.USB)

    val composePreview = openComposePreview(fixture, "MultipleComposePreviews.kt").waitForRenderToFinish()
    commandHandler.composablePackageName = "google.simpleapplication"
    commandHandler.composableFqn = "google.simpleapplication.MultipleComposePreviewsKt.Preview1"

    composePreview.designSurface
      .allSceneViews
      .first()
      .toolbar()
      .clickButtonByIcon(StudioIcons.Compose.RUN_ON_DEVICE)

    val runToolWindowFixture = RunToolWindowFixture(guiTest.ideFrame())
    val contentFixture = runToolWindowFixture.findContent("Preview1")
    // We should display "Launching '<Compose Preview Configuration Name>' on <Device>"
    val launchingPreview = Pattern.compile(".*Launching 'Preview1' on Google Pix3l.*", Pattern.DOTALL)
    contentFixture.waitForOutput(PatternTextMatcher(launchingPreview), 10)
    // We should display the adb shell command containing androidx.ui.tooling.preview.PreviewActivity, which wraps the @Composable
    val previewActivity = Pattern.compile(".*androidx\\.ui\\.tooling\\.preview\\.PreviewActivity.*", Pattern.DOTALL)
    contentFixture.waitForOutput(PatternTextMatcher(previewActivity), 10)

    guiTest.ideFrame().invokeMenuPath("Run", "Stop 'Preview1'")
    fixture.editor.close()
  }

  private class DeployPreviewCommandHandler : DeviceCommandHandler("shell") {
    var composablePackageName: String = "com.example"
    var composableFqn: String = "com.example.MyComposable"

    override fun accept(server: FakeAdbServer, socket: Socket, device: DeviceState, command: String, args: String): Boolean {
      val deployArgs = "am start -n \"$composablePackageName/androidx.ui.tooling.preview.PreviewActivity\"" +
                       " -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --es composable $composableFqn"
      val stopArgs = "am force-stop $composablePackageName"
      when (args) {
        deployArgs, stopArgs -> {
          CommandHandler.writeOkay(socket.getOutputStream())
          return true
        }
        else -> return false
      }
    }
  }
}
