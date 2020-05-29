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
package com.android.tools.idea.emulator

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.createDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.emulator.actions.BootMode
import com.android.tools.idea.emulator.actions.BootType
import com.android.tools.idea.emulator.actions.SnapshotInfo
import com.android.tools.idea.emulator.actions.SnapshotManager
import com.android.tools.idea.protobuf.TextFormat
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.registerComponentInstance
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.`when`
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JRadioButton
import javax.swing.JTextField

/**
 * Tests for snapshot-related emulator toolbar actions.
 */
@RunsInEdt
class SnapshotActionsTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val emulatorRule = FakeEmulatorRule()
  private var nullableEmulator: FakeEmulator? = null
  private val filesOpened = mutableListOf<VirtualFile>()
  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(emulatorRule).around(EdtRule())

  private var emulator: FakeEmulator
    get() = nullableEmulator ?: throw IllegalStateException()
    set(value) { nullableEmulator = value }

  private val testRootDisposable
    get() = projectRule.fixture.testRootDisposable

  @Before
  fun setUp() {
    FakeUi.setPortableUiFont()
    val fileEditorManager = mock<FileEditorManagerEx>()
    `when`(fileEditorManager.openFile(any(), anyBoolean())).thenAnswer { invocation ->
      filesOpened.add(invocation.getArgument(0))
      return@thenAnswer emptyArray<FileEditor>()
    }
    `when`(fileEditorManager.openFiles).thenReturn(VirtualFile.EMPTY_ARRAY)
    projectRule.project.registerComponentInstance(FileEditorManager::class.java, fileEditorManager, testRootDisposable)
    enableHeadlessDialogs(testRootDisposable)
  }

  @Test
  fun testSnapshotActions() {
    val view = createEmulatorView()

    val defaultBootMode = SnapshotManager(emulator.avdFolder, emulator.avdId).readBootMode()
    assertThat(SnapshotManager(emulator.avdFolder, emulator.avdId).readBootMode()).isEqualTo(defaultBootMode)

    val configIni = emulator.avdFolder.resolve("config.ini")
    val oldSize = Files.size(configIni)

    // Check snapshot creation.
    performActionAndInteractWithDialog("android.emulator.create.snapshot", view) { dialog ->
      // Executed when the CreateSnapshotDialog opens.
      val rootPane = dialog.rootPane
      val ui = FakeUi(rootPane)
      val textField = checkNotNull(ui.findComponent { it is JTextField }) as JTextField
      textField.text = "first snapshot"
      val checkBox = checkNotNull(ui.findComponent { it is JCheckBox && it.text == "Boot from this snapshot" }) as JCheckBox
      checkBox.doClick()
      val okButton = rootPane.defaultButton
      assertThat(okButton.text).isEqualTo("OK")
      ui.clickOn(okButton)
    }

    val call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.SnapshotService/SaveSnapshot")
    assertThat(TextFormat.shortDebugString(call.request)).isEqualTo("snapshot_id: \"first snapshot\"")

    waitForCondition(2, TimeUnit.SECONDS) { Files.size(configIni) != oldSize }
    val bootMode = SnapshotManager(emulator.avdFolder, emulator.avdId).readBootMode()
    assertThat(bootMode).isEqualTo(BootMode(BootType.SNAPSHOT, "first snapshot"))

    // Check switching to the quick boot mode.
    performActionAndInteractWithDialog("android.emulator.boot.options", view) { dialog ->
      // Executed when the CreateSnapshotDialog opens.
      val rootPane = dialog.rootPane
      val ui = FakeUi(rootPane)
      val snapshotRadio = checkNotNull(ui.findComponent { it is JRadioButton && it.text == "Boot from snapshot" }) as JRadioButton
      assertThat(snapshotRadio.isSelected).isTrue()
      val comboBox = checkNotNull(ui.findComponent { it is JComboBox<*> }) as JComboBox<*>
      waitForCondition(2, TimeUnit.SECONDS) {
        comboBox.itemCount != 0
      }
      assertThat((comboBox.selectedItem as SnapshotInfo).displayName).isEqualTo("first snapshot")
      val quickBootRadio = checkNotNull(ui.findComponent { it is JRadioButton && it.text == "Quick boot (default)" }) as JRadioButton
      quickBootRadio.doClick()
      val okButton = rootPane.defaultButton
      assertThat(okButton.text).isEqualTo("OK")
      ui.clickOn(okButton)
    }

    waitForCondition(2, TimeUnit.SECONDS) {
      SnapshotManager(emulator.avdFolder, emulator.avdId).readBootMode()?.bootType == BootType.QUICK
    }
  }

  private fun performActionAndInteractWithDialog(actionId: String, emulatorView: EmulatorView, interactor: (DialogWrapper) -> Unit) {
    createDialogAndInteractWithIt({ performAction(actionId, emulatorView) }, interactor)
  }

  private fun performAction(actionId: String, emulatorView: EmulatorView) {
    val actionManager = ActionManager.getInstance()
    val action = actionManager.getAction(actionId)
    val event = AnActionEvent(null, TestDataContext(emulatorView), ActionPlaces.UNKNOWN, Presentation(), actionManager, 0)
    action.actionPerformed(event)
  }

  private fun createEmulatorView(): EmulatorView {
    val catalog = RunningEmulatorCatalog.getInstance()
    val tempFolder = emulatorRule.root.toPath()
    emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder), 8554)
    emulator.start()
    val emulators = catalog.updateNow().get()
    assertThat(emulators).hasSize(1)
    val emulatorController = emulators.first()
    val view = EmulatorView(emulatorController, testRootDisposable, false)
    waitForCondition(2, TimeUnit.SECONDS) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    emulator.getNextGrpcCall(2, TimeUnit.SECONDS) // Skip the initial "getVmState" call.
    return view
  }

  private inner class TestDataContext(private val emulatorView: EmulatorView) : DataContext {

    override fun getData(dataId: String): Any? {
      return when (dataId) {
        EMULATOR_CONTROLLER_KEY.name -> emulatorView.emulator
        EMULATOR_VIEW_KEY.name, ZOOMABLE_KEY.name -> emulatorView
        CommonDataKeys.PROJECT.name -> projectRule.project
        else -> null
      }
    }
  }
}
