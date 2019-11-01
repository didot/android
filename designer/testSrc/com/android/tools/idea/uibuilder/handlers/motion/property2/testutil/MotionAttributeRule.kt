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
package com.android.tools.idea.uibuilder.handlers.motion.property2.testutil

import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.LayoutTestCase.getDesignerPluginHome
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface
import com.android.tools.idea.uibuilder.handlers.motion.property2.MotionLayoutAttributesModel
import com.android.tools.idea.uibuilder.handlers.motion.property2.MotionSelection
import com.android.tools.idea.uibuilder.property2.NelePropertiesModelTest
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.idea.uibuilder.surface.AccessoryPanel
import com.android.tools.idea.util.androidFacet
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.truth.Truth
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.text.LineColumn
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.ComponentStack
import org.junit.rules.ExternalResource
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.times

class MotionAttributeRule(
  private val projectRule: AndroidProjectRule,
  private val motionLayoutFilename: String,
  private val motionSceneFilename: String
) : ExternalResource() {
  private var componentStack: ComponentStack? = null
  private var selectionFactory: MotionSelectionFactory? = null
  private var timeline: FakeMotionAccessoryPanel? = null
  private var model: MotionLayoutAttributesModel? = null
  private var fileManager: FileEditorManager? = null
  private var matchCount: Int = 0

  fun selectConstraintSet(id: String) {
    select(selectionFactory!!.createConstraintSet(id))
  }

  fun selectConstraint(setId: String, id: String) {
    select(selectionFactory!!.createConstraint(setId, id))
  }

  fun selectTransition(start: String, end: String) {
    select(selectionFactory!!.createTransition(start, end))
  }

  fun selectKeyFrame(start: String, end: String, keyType: String, framePosition: Int, target: String) {
    select(selectionFactory!!.createKeyFrame(start, end, keyType, framePosition, target))
  }

  fun property(namespace: String, name: String, subTag: String = ""): NelePropertyItem {
    return model!!.allProperties!![subTag]!![namespace, name]
  }

  val properties: Map<String, PropertiesTable<NelePropertyItem>>
    get() = model!!.allProperties!!

  val attributesModel: MotionLayoutAttributesModel
    get() = model!!

  fun enableFileOpenCaptures() {
    fileManager = Mockito.mock(FileEditorManager::class.java)
    componentStack!!.registerComponentInstance(FileEditorManager::class.java, fileManager!!)
    Mockito.`when`(fileManager!!.openEditor(ArgumentMatchers.any(OpenFileDescriptor::class.java), ArgumentMatchers.anyBoolean()))
      .thenReturn(listOf(Mockito.mock(FileEditor::class.java)))
  }

  fun checkEditor(fileName: String, lineNumber: Int, text: String) {
    val file = ArgumentCaptor.forClass(OpenFileDescriptor::class.java)
    Mockito.verify(fileManager!!, times(++matchCount)).openEditor(file.capture(), ArgumentMatchers.eq(true))
    val descriptor = file.value
    val line = findLineAtOffset(descriptor.file, descriptor.offset)
    Truth.assertThat(descriptor.file.name).isEqualTo(fileName)
    Truth.assertThat(line.second).isEqualTo(text)
    Truth.assertThat(line.first.line + 1).isEqualTo(lineNumber)
  }

  override fun before() {
    componentStack = ComponentStack(projectRule.project)
    projectRule.fixture.testDataPath = getDesignerPluginHome() + "/testData/motion"
    val facet = projectRule.module.androidFacet!!
    projectRule.fixture.copyFileToProject("attrs.xml", "res/values/attrs.xml")
    val layout = projectRule.fixture.copyFileToProject(motionLayoutFilename, "res/layout/$motionLayoutFilename")
    val layoutFile = AndroidPsiUtils.getPsiFileSafely(projectRule.project, layout) as XmlFile
    val scene = projectRule.fixture.copyFileToProject(motionSceneFilename, "res/xml/$motionSceneFilename")
    val sceneFile = AndroidPsiUtils.getPsiFileSafely(projectRule.project, scene) as XmlFile
    timeline = FakeMotionAccessoryPanel()
    runInEdtAndWait {
      val nlModel = createNlModel(layoutFile, timeline!!)
      selectionFactory = MotionSelectionFactory(nlModel, sceneFile)
      model = MotionLayoutAttributesModel(projectRule.fixture.projectDisposable, facet)
      model!!.updateQueue.isPassThrough = true
      model!!.surface = nlModel.surface
    }
  }

  override fun after() {
    componentStack!!.restore()
    componentStack = null
    fileManager = null
    selectionFactory = null
    timeline = null
    model = null
  }

  private fun createNlModel(layout: XmlFile, timeline: AccessoryPanelInterface): SyncNlModel {
    val facet = projectRule.module.androidFacet!!
    val model = NlModelBuilderUtil.model(facet, projectRule.fixture, "layout", "layout.xml", ComponentDescriptorUtil.component(layout)).build()
    val surface = model.surface
    val panel = Mockito.mock(AccessoryPanel::class.java)
    Mockito.`when`(surface.accessoryPanel).thenReturn(panel)
    Mockito.`when`(panel.currentPanel).thenReturn(timeline)
    return model
  }

  private fun select(selection: MotionSelection) {
    timeline!!.select(selection)
    NelePropertiesModelTest.waitUntilEventsProcessed(model!!)
  }

  private fun findLineAtOffset(file: VirtualFile, offset: Int): Pair<LineColumn, String> {
    val text = String(file.contentsToByteArray(), Charsets.UTF_8)
    val line = StringUtil.offsetToLineColumn(text, offset)
    val lineText = text.substring(offset - line.column, text.indexOf('\n', offset))
    return Pair(line, lineText.trim())
  }
}
