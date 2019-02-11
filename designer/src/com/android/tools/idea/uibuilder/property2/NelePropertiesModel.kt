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

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceValue
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.common.model.ModelListener
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertiesModelListener
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceListener
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.uibuilder.analytics.NlUsageTracker
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface
import com.android.tools.idea.uibuilder.api.AccessorySelectionListener
import com.android.tools.idea.uibuilder.scene.RenderListener
import com.android.tools.idea.uibuilder.surface.AccessoryPanelListener
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Future
import javax.swing.event.ChangeListener

private const val UPDATE_QUEUE_NAME = "android.layout.propertysheet"
private const val UPDATE_IDENTITY = "updateProperies"
private const val UPDATE_DELAY_MILLI_SECONDS = 250

/**
 * [PropertiesModel] for Nele design surface properties.
 */
open class NelePropertiesModel(parentDisposable: Disposable,
                               val provider: PropertiesProvider,
                               val facet: AndroidFacet,
                               private val updateOnComponentSelectionChanges: Boolean) : PropertiesModel<NelePropertyItem>, Disposable {
  val project: Project = facet.module.project

  private val listeners: MutableList<PropertiesModelListener<NelePropertyItem>> = mutableListOf()
  private val designSurfaceListener = PropertiesDesignSurfaceListener()
  private val modelListener = NlModelListener()
  private val accessoryPanelListener = AccessoryPanelListener { panel: AccessoryPanelInterface? -> usePanel(panel) }
  private val accessorySelectionListener = AccessorySelectionListener { panel, selection -> handlePanelSelectionUpdate(panel, selection) }
  private val renderListener = RenderListener { handleRenderingCompleted() }
  private var activeSurface: DesignSurface? = null
  private var activeSceneView: SceneView? = null
  private var activePanel: AccessoryPanelInterface? = null
  private var defaultValueProvider: NeleDefaultPropertyProvider? = null
  private val liveComponents = mutableListOf<NlComponent>()
  private val liveChangeListener: ChangeListener = ChangeListener { firePropertyValueChange() }

  constructor(parentDisposable: Disposable, facet: AndroidFacet) :
    this(parentDisposable, NelePropertiesProvider(facet), facet, true)

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
  var lastSelectionUpdate: Future<Boolean> = Futures.immediateFuture(false)
    private set

  @VisibleForTesting
  var lastUpdateCompleted: Boolean = true
    private set

  init {
    @Suppress("LeakingThis")
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
    protected set

  @TestOnly
  fun setPropertiesInTest(testProperties: PropertiesTable<NelePropertyItem>) {
    properties = testProperties
  }

  private fun logPropertyValueChanged(property: NelePropertyItem) {
    NlUsageTracker.getInstance(activeSurface).logPropertyChange(property, -1)
  }

  fun provideDefaultValue(property: NelePropertyItem): ResourceValue? {
    return defaultValueProvider?.provideDefaultValue(property)
  }

  open fun getPropertyValue(property: NelePropertyItem): String? {
    ApplicationManager.getApplication().assertIsDispatchThread()
    var prev: String? = null
    for (component in property.components) {
      val value = component.getLiveAttribute(property.namespace, property.name) ?: return null
      prev = prev ?: value
      if (value != prev) return null
    }
    return prev
  }

  open fun setPropertyValue(property: NelePropertyItem, newValue: String?) {
    assert(ApplicationManager.getApplication().isDispatchThread)
    if (property.project.isDisposed || property.components.isEmpty()) {
      return
    }
    val componentName = if (property.components.size == 1) property.components[0].tagName else "Multiple"

    TransactionGuard.submitTransaction(this, Runnable {
      NlWriteCommandActionUtil.run(property.components, "Set $componentName.${property.name} to $newValue") {
        property.components.forEach { it.setAttribute(property.namespace, property.name, newValue) }
        logPropertyValueChanged(property)
        if (property.namespace == SdkConstants.TOOLS_URI) {
          if (newValue != null) {
            // A tools property may not be in the current set of possible properties. So add it now:
            if (properties.isEmpty) {
              properties = provider.createEmptyTable()
            }
            properties.put(property)
          }

          if (property.name == SdkConstants.ATTR_PARENT_TAG) {
            // When the "parentTag" attribute is set on a <merge> tag,
            // we may have a different set of available properties available,
            // since the attributes of the "parentTag" are included if set.
            firePropertiesGenerated()
          }
        }
      }
    })
  }

  private fun createMergingUpdateQueue(): MergingUpdateQueue {
    return MergingUpdateQueue(UPDATE_QUEUE_NAME, UPDATE_DELAY_MILLI_SECONDS, true, null, this, null, Alarm.ThreadToUse.SWING_THREAD)
  }

  private fun useDesignSurface(surface: DesignSurface?) {
    if (surface != activeSurface) {
      updateDesignSurface(activeSurface, surface)
      activeSurface = surface
      (activeSceneView as? ScreenView)?.sceneManager?.removeRenderListener(renderListener)
      activeSceneView = surface?.currentSceneView
      (activeSceneView as? ScreenView)?.sceneManager?.addRenderListener(renderListener)
    }
    if (surface != null && wantComponentSelectionUpdate(surface, activeSurface, activePanel)) {
      scheduleSelectionUpdate(surface, activeSceneView?.selectionModel?.selection ?: emptyList())
    }
  }

  private fun updateDesignSurface(old: DesignSurface?, new: DesignSurface?) {
    old?.model?.removeListener(modelListener)
    new?.model?.addListener(modelListener)
    if (updateOnComponentSelectionChanges) {
      old?.removeListener(designSurfaceListener)
      new?.addListener(designSurfaceListener)
    }
    (old as? NlDesignSurface)?.accessoryPanel?.removeAccessoryPanelListener(accessoryPanelListener)
    (new as? NlDesignSurface)?.accessoryPanel?.addAccessoryPanelListener(accessoryPanelListener)
    useCurrentPanel(new)
  }

  private fun useCurrentPanel(surface: DesignSurface?) {
    usePanel((surface as? NlDesignSurface)?.accessoryPanel?.currentPanel)
  }

  private fun usePanel(panel: AccessoryPanelInterface?) {
    if (panel != activePanel) {
      setAccessorySelectionListener(activePanel, panel)
      activePanel = panel
    }
  }

  private fun setAccessorySelectionListener(old: AccessoryPanelInterface?, new: AccessoryPanelInterface?) {
    old?.removeListener(accessorySelectionListener)
    new?.addListener(accessorySelectionListener)
  }

  private fun scheduleSelectionUpdate(surface: DesignSurface?, components: List<NlComponent>) {
    updateQueue.queue(object : Update(UPDATE_IDENTITY) {
      override fun run() {
        handleSelectionUpdate(surface, components)
      }
    })
  }

  protected open fun wantComponentSelectionUpdate(surface: DesignSurface?,
                                                  activeSurface: DesignSurface?,
                                                  activePanel: AccessoryPanelInterface?): Boolean {
    return surface == activeSurface && activePanel == null && !facet.isDisposed
  }

  private fun handleSelectionUpdate(surface: DesignSurface?, components: List<NlComponent>) {
    updateLiveListeners(components)

    // Obtaining the properties, especially the first time around on a big project
    // can take close to a second, so we do it on a separate thread..
    val application = ApplicationManager.getApplication()
    val wantUpdate = { wantComponentSelectionUpdate(surface, activeSurface, activePanel) }
    val future = application.executeOnPooledThread<Boolean> { handleUpdate(null, components, wantUpdate) }

    // Enable our testing code to wait for the above pooled thread execution.
    if (application.isUnitTestMode) {
      lastSelectionUpdate = future
    }
  }

  private fun updateLiveListeners(components: List<NlComponent>) {
    liveComponents.forEach { it.removeLiveChangeListener(liveChangeListener) }
    liveComponents.clear()
    liveComponents.addAll(components)
    liveComponents.forEach { it.addLiveChangeListener(liveChangeListener) }
  }

  protected open fun wantPanelSelectionUpdate(panel: AccessoryPanelInterface, activePanel: AccessoryPanelInterface?): Boolean {
    return panel == activePanel && panel.selectedAccessory == null && !facet.isDisposed
  }

  private fun handlePanelSelectionUpdate(panel: AccessoryPanelInterface, components: List<NlComponent>) {
    // Obtaining the properties, especially the first time around on a big project
    // can take close to a second, so we do it on a separate thread..
    val application = ApplicationManager.getApplication()
    val wantUpdate = { wantPanelSelectionUpdate(panel, activePanel) }
    val future = application.executeOnPooledThread<Boolean> { handleUpdate(panel.selectedAccessory, components, wantUpdate) }

    // Enable our testing code to wait for the above pooled thread execution.
    if (application.isUnitTestMode) {
      lastSelectionUpdate = future
    }
  }

  private fun handleUpdate(accessory: Any?, components: List<NlComponent>, wantUpdate: () -> Boolean): Boolean {
    if (!wantUpdate()) {
      return false
    }
    val newProperties = provider.getProperties(this, accessory, components)
    lastUpdateCompleted = false
    defaultValueProvider?.clearLookups()

    UIUtil.invokeLaterIfNeeded {
      try {
        if (wantUpdate()) {
          properties = newProperties
          defaultValueProvider = createNeleDefaultPropertyProvider()
          firePropertiesGenerated()
        }
      }
      finally {
        lastUpdateCompleted = true
      }
    }
    return true
  }

  private fun handleRenderingCompleted() {
    if (defaultValueProvider?.hasDefaultValuesChanged() == true) {
      ApplicationManager.getApplication().invokeLater { firePropertyValueChange() }
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

  private inner class NlModelListener : ModelListener {
    override fun modelChanged(model: NlModel) {
      // Move the handling onto the event dispatch thread in case this notification is sent from a different thread:
      ApplicationManager.getApplication().invokeLater { firePropertyValueChange() }
    }

    override fun modelLiveUpdate(model: NlModel, animate: Boolean) {
      // Move the handling onto the event dispatch thread in case this notification is sent from a different thread:
      ApplicationManager.getApplication().invokeLater { firePropertyValueChange() }
    }
  }
}
