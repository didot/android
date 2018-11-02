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
package com.android.tools.idea.uibuilder.property2

import com.android.annotations.VisibleForTesting
import com.android.ide.common.rendering.api.ResourceValue
import com.android.tools.idea.common.analytics.NlUsageTrackerManager
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property2.api.PropertiesModel
import com.android.tools.idea.common.property2.api.PropertiesModelListener
import com.android.tools.idea.common.property2.api.PropertiesTable
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceListener
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface
import com.android.tools.idea.uibuilder.api.AccessorySelectionListener
import com.android.tools.idea.uibuilder.scene.RenderListener
import com.android.tools.idea.uibuilder.surface.AccessoryPanelListener
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Future

private const val UPDATE_QUEUE_NAME = "android.layout.propertysheet"
private const val UPDATE_IDENTITY = "updateProperies"
private const val UPDATE_DELAY_MILLI_SECONDS = 250

/**
 * [PropertiesModel] for Nele design surface properties.
 */
class NelePropertiesModel(parentDisposable: Disposable, val facet: AndroidFacet) : PropertiesModel<NelePropertyItem>, Disposable {
  private val provider = NelePropertiesProvider(this)
  private val listeners = mutableListOf<PropertiesModelListener<NelePropertyItem>>()
  private val designSurfaceListener = PropertiesDesignSurfaceListener()
  private val accessoryPanelListener = AccessoryPanelListener { panel: AccessoryPanelInterface? -> usePanel(panel) }
  private val accessorySelectionListener = AccessorySelectionListener { panel, selection -> handlePanelSelectionUpdate(panel, selection) }
  private val renderListener = RenderListener { renderCompleted() }
  private var activeSurface: DesignSurface? = null
  private var activeSceneView: SceneView? = null
  private var activePanel: AccessoryPanelInterface? = null
  private var defaultValueProvider: NeleDefaultPropertyProvider? = null

  var surface: DesignSurface?
    get() = activeSurface
    set(value) = useDesignSurface(value)

  /**
   * If true the value in an editor should show the resolved value of a property.
   */
  var showResolvedValues = true
    set (value) {
      field = value
      firePropertyValueChange()
    }

  @VisibleForTesting
  val updateQueue = createMergingUpdateQueue()

  @VisibleForTesting
  var lastSelectionUpdate: Future<*>? = null
    private set

  init {
    Disposer.register(parentDisposable, this)
  }

  override fun dispose() {
    properties = PropertiesTable.emptyTable()
    useDesignSurface(null)
  }

  override fun deactivate() {
    properties = PropertiesTable.emptyTable()
  }

  override fun addListener(listener: PropertiesModelListener<NelePropertyItem>) {
    listeners.add(listener)
  }

  override fun removeListener(listener: PropertiesModelListener<NelePropertyItem>) {
    listeners.remove(listener)
  }

  override var properties: PropertiesTable<NelePropertyItem> = PropertiesTable.emptyTable()
    private set

  @TestOnly
  fun setPropertiesInTest(testProperties: PropertiesTable<NelePropertyItem>) {
    properties = testProperties
  }

  fun logPropertyValueChanged(property: NelePropertyItem) {
    NlUsageTrackerManager.getInstance(activeSurface).logPropertyChange(property, -1)
  }

  fun provideDefaultValue(property: NelePropertyItem): ResourceValue? {
    return defaultValueProvider?.provideDefaultValue(property)
  }

  private fun createMergingUpdateQueue(): MergingUpdateQueue {
    return MergingUpdateQueue(UPDATE_QUEUE_NAME, UPDATE_DELAY_MILLI_SECONDS, true, null, this, null, Alarm.ThreadToUse.SWING_THREAD)
  }

  private fun useDesignSurface(surface: DesignSurface?) {
    if (surface == activeSurface) return

    activeSurface?.removeListener(designSurfaceListener)
    (activeSurface as? NlDesignSurface)?.accessoryPanel?.removeAccessoryPanelListener(accessoryPanelListener)
    activeSurface = surface
    activeSurface?.addListener(designSurfaceListener)
    (activeSurface as? NlDesignSurface)?.accessoryPanel?.addAccessoryPanelListener(accessoryPanelListener)
    useSceneView(activeSurface?.currentSceneView)
    usePanel((activeSurface as? NlDesignSurface)?.accessoryPanel?.currentPanel)
  }

  private fun useSceneView(sceneView: SceneView?) {
    if (sceneView == activeSceneView) return

    (activeSceneView as? ScreenView)?.sceneManager?.removeRenderListener(renderListener)
    activeSceneView = sceneView
    (activeSceneView as? ScreenView)?.sceneManager?.addRenderListener(renderListener)
    val components = activeSceneView?.selectionModel?.selection ?: emptyList<NlComponent>()
    scheduleSelectionUpdate(activeSurface, components)
  }

  private fun usePanel(panel: AccessoryPanelInterface?) {
    if (panel == activePanel) return

    activePanel?.removeListener(accessorySelectionListener)
    activePanel = panel
    activePanel?.addListener(accessorySelectionListener)
  }

  private fun scheduleSelectionUpdate(surface: DesignSurface?, components: List<NlComponent>) {
    updateQueue.queue(object : Update(UPDATE_IDENTITY) {
      override fun run() {
        handleSelectionUpdate(surface, components)
      }
    })
  }

  private fun handleSelectionUpdate(surface: DesignSurface?, components: List<NlComponent>) {
    // Obtaining the properties, especially the first time around on a big project
    // can take close to a second, so we do it on a separate thread..
    val application = ApplicationManager.getApplication()
    val future = application.executeOnPooledThread {
      if (surface != activeSurface || activePanel != null || facet.isDisposed) {
        return@executeOnPooledThread
      }
      val newProperties = provider.getProperties(components)

      UIUtil.invokeLaterIfNeeded {
        if (surface != activeSurface || activePanel != null || facet.isDisposed) {
          return@invokeLaterIfNeeded
        }
        properties = newProperties
        defaultValueProvider = createNeleDefaultPropertyProvider()
        firePropertiesGenerated()
      }
    }

    // Enable our testing code to wait for the above pooled thread execution.
    if (application.isUnitTestMode) {
      lastSelectionUpdate = future
    }
  }

  private fun handlePanelSelectionUpdate(panel: AccessoryPanelInterface, components: List<NlComponent>) {
    // Obtaining the properties, especially the first time around on a big project
    // can take close to a second, so we do it on a separate thread..
    val application = ApplicationManager.getApplication()
    val future = application.executeOnPooledThread {
      if (panel != activePanel || panel.selectedAccessory != null || facet.isDisposed) {
        return@executeOnPooledThread
      }
      val newProperties = provider.getProperties(components)

      UIUtil.invokeLaterIfNeeded {
        if (panel != activePanel || panel.selectedAccessory != null || facet.isDisposed) {
          return@invokeLaterIfNeeded
        }
        properties = newProperties
        defaultValueProvider = createNeleDefaultPropertyProvider()
        firePropertiesGenerated()
      }
    }

    // Enable our testing code to wait for the above pooled thread execution.
    if (application.isUnitTestMode) {
      lastSelectionUpdate = future
    }
  }

  private fun renderCompleted() {
    UIUtil.invokeLaterIfNeeded {
      // The default properties comes from layoutlib, so they may have changed:
      defaultValueProvider = createNeleDefaultPropertyProvider()
      firePropertyValueChange()
    }
  }

  @VisibleForTesting
  fun firePropertiesGenerated() {
    listeners.toTypedArray().forEach { it.propertiesGenerated(this) }
  }

  @VisibleForTesting
  fun firePropertyValueChange() {
    listeners.toTypedArray().forEach { it.propertyValuesChanged(this) }
  }

  private fun createNeleDefaultPropertyProvider(): NeleDefaultPropertyProvider? {
    val view = activeSceneView ?: return null
    return NeleDefaultPropertyProvider(view.sceneManager)
  }

  private inner class PropertiesDesignSurfaceListener : DesignSurfaceListener {
    override fun componentSelectionChanged(surface: DesignSurface, newSelection: List<NlComponent>) {
      scheduleSelectionUpdate(surface, newSelection)
    }
  }
}
