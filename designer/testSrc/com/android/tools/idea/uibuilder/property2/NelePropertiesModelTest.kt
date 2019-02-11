/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property2

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.ATTR_TEXT_APPEARANCE
import com.android.SdkConstants.IMAGE_VIEW
import com.android.SdkConstants.TEXT_VIEW
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.model.NlModel
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertiesModelListener
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.scene.SyncLayoutlibSceneManager
import com.google.common.truth.Truth.assertThat
import com.intellij.util.ui.UIUtil
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class NelePropertiesModelTest: LayoutTestCase() {

  fun testPropertiesGeneratedEventWhenDesignSurfaceIsHookedUp() {
    // setup
    @Suppress("UNCHECKED_CAST")
    val listener = mock(PropertiesModelListener::class.java) as PropertiesModelListener<NelePropertyItem>
    val model = createModel()
    val nlModel = createNlModel(TEXT_VIEW)
    model.addListener(listener)

    // test
    model.surface = nlModel.surface
    waitUntilEventsProcessed(model)
    verify(listener).propertiesGenerated(model)
  }

  fun testPropertiesGeneratedEventWhenSwitchingDesignSurface() {
    // setup
    @Suppress("UNCHECKED_CAST")
    val listener = mock(PropertiesModelListener::class.java) as PropertiesModelListener<NelePropertyItem>
    val model = createModel()
    val nlModelA = createNlModel(IMAGE_VIEW)
    val nlModelB = createNlModel(TEXT_VIEW)
    val textView = nlModelB.find(TEXT_VIEW)!!
    nlModelB.surface.selectionModel.setSelection(listOf(textView))
    model.surface = nlModelA.surface
    waitUntilEventsProcessed(model)
    model.addListener(listener)

    // test
    model.surface = nlModelB.surface
    waitUntilEventsProcessed(model)
    verify(listener).propertiesGenerated(model)
    assertThat(model.properties[ANDROID_URI, ATTR_TEXT].components[0].model).isEqualTo(nlModelB)
  }

  fun testPropertiesGeneratedEventAfterSelectionChange() {
    // setup
    @Suppress("UNCHECKED_CAST")
    val listener = mock(PropertiesModelListener::class.java) as PropertiesModelListener<NelePropertyItem>
    val model = createModel()
    val nlModel = createNlModel(TEXT_VIEW)
    model.surface = nlModel.surface
    waitUntilEventsProcessed(model)
    model.addListener(listener)
    val textView = nlModel.find(TEXT_VIEW)!!

    // test
    nlModel.surface.selectionModel.setSelection(listOf(textView))
    waitUntilEventsProcessed(model)
    verify(listener).propertiesGenerated(model)
  }

  fun testPropertyValuesChangedEventAfterModelChange() {
    // setup
    @Suppress("UNCHECKED_CAST")
    val listener = mock(PropertiesModelListener::class.java) as PropertiesModelListener<NelePropertyItem>
    val model = createModel()
    val nlModel = createNlModel(TEXT_VIEW)
    model.surface = nlModel.surface
    waitUntilEventsProcessed(model)
    model.addListener(listener)

    nlModel.notifyModified(NlModel.ChangeType.EDIT)
    UIUtil.dispatchAllInvocationEvents()
    verify(listener).propertyValuesChanged(model)
  }

  fun testPropertyValuesChangedEventAfterLiveModelChange() {
    // setup
    @Suppress("UNCHECKED_CAST")
    val listener = mock(PropertiesModelListener::class.java) as PropertiesModelListener<NelePropertyItem>
    val model = createModel()
    val nlModel = createNlModel(TEXT_VIEW)
    model.surface = nlModel.surface
    waitUntilEventsProcessed(model)
    model.addListener(listener)

    nlModel.notifyLiveUpdate(false)
    UIUtil.dispatchAllInvocationEvents()
    verify(listener).propertyValuesChanged(model)
  }

  fun testPropertyValuesChangedEventAfterLiveComponentChange() {
    // setup
    @Suppress("UNCHECKED_CAST")
    val listener = mock(PropertiesModelListener::class.java) as PropertiesModelListener<NelePropertyItem>
    val model = createModel()
    val nlModel = createNlModel(TEXT_VIEW)
    val textView = nlModel.find(TEXT_VIEW)!!
    model.surface = nlModel.surface
    nlModel.surface.selectionModel.setSelection(listOf(textView))
    waitUntilEventsProcessed(model)
    model.addListener(listener)

    textView.fireLiveChangeEvent()
    UIUtil.dispatchAllInvocationEvents()
    verify(listener).propertyValuesChanged(model)
  }

  fun testAccessToDefaultPropertiesViaModel() {
    // setup
    val model = createModel()
    val nlModel = createNlModel(TEXT_VIEW)
    val textView = nlModel.find(TEXT_VIEW)!!
    val view = nlModel.surface.currentSceneView!!
    val manager = view.sceneManager as SyncLayoutlibSceneManager
    val property = NelePropertyItem(ANDROID_URI, ATTR_TEXT_APPEARANCE, NelePropertyType.STYLE,
                                    null, "", "", model, null, listOf(textView))
    manager.putDefaultPropertyValue(textView, ResourceNamespace.ANDROID, ATTR_TEXT_APPEARANCE, "?attr/textAppearanceSmall")
    model.surface = nlModel.surface
    waitUntilEventsProcessed(model)

    // test
    assertThat(model.provideDefaultValue(property)?.value).isEqualTo("?attr/textAppearanceSmall")
  }

  fun testPropertyValuesChangesAfterRendering() {
    // setup
    @Suppress("UNCHECKED_CAST")
    val listener = mock(PropertiesModelListener::class.java) as PropertiesModelListener<NelePropertyItem>
    val model = createModel()
    val nlModel = createNlModel(TEXT_VIEW)
    val textView = nlModel.find(TEXT_VIEW)!!
    val view = nlModel.surface.currentSceneView!!
    val manager = view.sceneManager as SyncLayoutlibSceneManager
    val property = NelePropertyItem(ANDROID_URI, ATTR_TEXT_APPEARANCE, NelePropertyType.STYLE, null, "", "", model, null, listOf(textView))
    manager.putDefaultPropertyValue(textView, ResourceNamespace.ANDROID, ATTR_TEXT_APPEARANCE, "?attr/textAppearanceSmall")
    model.surface = nlModel.surface
    model.addListener(listener)
    waitUntilEventsProcessed(model)
    assertThat(model.provideDefaultValue(property)?.value).isEqualTo("?attr/textAppearanceSmall")

    // Value changed should not be reported if the default values are unchanged
    manager.fireRenderCompleted()
    UIUtil.dispatchAllInvocationEvents()
    verify(listener, never()).propertyValuesChanged(model)

    // Value changed notification is expected since the default values have changed
    manager.putDefaultPropertyValue(textView, ResourceNamespace.ANDROID, ATTR_TEXT_APPEARANCE, "?attr/textAppearanceLarge")
    manager.fireRenderCompleted()
    UIUtil.dispatchAllInvocationEvents()
    verify(listener).propertyValuesChanged(model)
  }

  fun testListenersAreConcurrentModificationSafe() {
    // Make sure that ConcurrentModificationException is NOT generated from the code below:
    val model = createModel()
    val listener = RecursiveValueChangedListener()
    model.addListener(listener)
    model.firePropertiesGenerated()
    model.firePropertyValueChange()
    assertThat(listener.called).isEqualTo(2)
  }

  private fun createNlModel(tag: String): SyncNlModel {
    val builder = model(
      "linear.xml",
      component(SdkConstants.LINEAR_LAYOUT)
        .withBounds(0, 0, 1000, 1500)
        .id("@id/linear")
        .matchParentWidth()
        .matchParentHeight()
        .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_CONTEXT, "com.example.MyActivity")
        .children(
          component(tag)
            .withBounds(100, 100, 100, 100)
            .id("@id/$tag")
            .width("wrap_content")
            .height("wrap_content")
        )
    )
    return builder.build()
  }

  // The production code passes the property creation to a queue.
  // This code changes the queue to do a pass through during this test.
  private fun createModel(): NelePropertiesModel {
    val model = NelePropertiesModel(testRootDisposable, myFacet)
    model.updateQueue.isPassThrough = true
    return model
  }

  private class RecursiveValueChangedListener : PropertiesModelListener<NelePropertyItem> {
    var called = 0

    override fun propertiesGenerated(model: PropertiesModel<NelePropertyItem>) {
      model.addListener(RecursiveValueChangedListener())
      called++
    }

    override fun propertyValuesChanged(model: PropertiesModel<NelePropertyItem>) {
      model.addListener(RecursiveValueChangedListener())
      called++
    }
  }

  companion object {

    // Ugly hack:
    // The production code is executing the properties creation on a separate thread.
    // This code makes sure that the last scheduled worker thread is finished,
    // then we also need to wait for events on the UI thread.
    fun waitUntilEventsProcessed(model: NelePropertiesModel) {
      if (model.lastSelectionUpdate.get()) {
        while (!model.lastUpdateCompleted) {
          UIUtil.dispatchAllInvocationEvents()
        }
      }
    }
  }
}
