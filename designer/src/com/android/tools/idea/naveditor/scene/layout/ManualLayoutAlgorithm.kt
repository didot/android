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
package com.android.tools.idea.naveditor.scene.layout

import com.android.SdkConstants.*
import com.android.annotations.VisibleForTesting
import com.android.tools.idea.common.editor.NlEditor
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.naveditor.model.idPath
import com.android.tools.idea.naveditor.model.isDestination
import com.android.tools.idea.naveditor.model.isInclude
import com.android.tools.idea.naveditor.model.isNavigation
import com.android.tools.idea.naveditor.scene.NavSceneManager
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.dom.navigation.NavigationDomFileDescription

const val SKIP_PERSISTED_LAYOUT = "skipPersistedLayout"

/**
 * [NavSceneLayoutAlgorithm] that puts screens in locations that have been specified by the user
 */
class ManualLayoutAlgorithm(private val module: Module, private val sceneManager: NavSceneManager) : SingleComponentLayoutAlgorithm() {
  private var _storage: Storage? = null
  private val tagPositionMap: BiMap<SmartPsiElementPointer<XmlTag>, LayoutPositions> = HashBiMap.create()
  private val filePositionMap: MutableMap<XmlFile, LayoutPositions> = mutableMapOf()

  private val storage: Storage
    get() {
      var result = _storage
      if (result == null) {
        result = this.module.project.getComponent(Storage::class.java)!!
        _storage = result
      }
      return result
    }

  init {
    val connection = module.project.messageBus.connect()
    connection.subscribe(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER, object : FileEditorManagerListener.Before.Adapter() {
      override fun beforeFileClosed(source: FileEditorManager, file: VirtualFile) {
        if ((PsiUtil.getPsiFile(module.project, file) as? XmlFile)?.let { NavigationDomFileDescription.isNavFile(it) } == true) {
          for (editor in source.getAllEditors(file).filterIsInstance<NlEditor>()) {
            val layoutPositions = storage.state[file.name] ?: continue
            editor.component.surface.model?.let { rectifyIds(it.components.flatMap { it.children }, layoutPositions) }
          }
        }
      }
    })
  }

  @VisibleForTesting
  constructor(state: LayoutPositions, module: Module, sceneManager: NavSceneManager)
    : this(module, sceneManager) {
    _storage = Storage()
    storage.rootPositions = state
  }

  override fun doLayout(component: SceneComponent): Boolean {
    if (component.nlComponent.getClientProperty(SKIP_PERSISTED_LAYOUT) == true) {
      return false
    }

    if (!component.nlComponent.isDestination) {
      return false
    }
    reload(component.nlComponent.model.file)
    if (component.nlComponent.isNavigation && component.parent == null) {
      return true
    }

    val tag = SmartPointerManager.createPointer(component.nlComponent.tag)
    var positions = tagPositionMap[tag]
    if (positions == null) {
      reload(component.nlComponent.model.file, true)
      positions = tagPositionMap[SmartPointerManager.createPointer(component.nlComponent.tag)] ?: return false
    }
    val location = positions.myPosition ?: return false
    component.setPosition(location.x, location.y)
    return true
  }

  private fun reload(file: XmlFile, force: Boolean = false) {
    if (filePositionMap.containsKey(file) && !force) {
      return
    }

    val positions = storage.state[file.name] ?: return
    filePositionMap[file] = positions
    val component = file.rootTag ?: return
    reload(positions, component)
  }

  private fun reload(positions: LayoutPositions, tag: XmlTag) {
    // if a tag is recreated (e.g. by delete/undo) we might be coming in with the same "positions"
    // but a new tag. Delete the existing entry first.
    tagPositionMap.inverse().remove(positions)
    tagPositionMap[SmartPointerManager.createPointer(tag)] = positions
    for ((id, position) in positions.myPositions) {
      for (subtag in tag.subTags) {
        var subtagId = NlComponent.stripId(subtag.getAttributeValue(ATTR_ID, ANDROID_URI))
        if (subtagId == null && subtag.name == TAG_INCLUDE) {
          subtagId = subtag.getAttributeValue(ATTR_GRAPH, AUTO_URI)?.substring(NAVIGATION_PREFIX.length)
        }
        if (subtagId == id) {
          reload(position, subtag)
        }
      }
    }
  }

  override fun save(component: SceneComponent) {
    if (!component.nlComponent.isDestination) {
      return
    }
    val newPoint = Point(component.drawX, component.drawY)
    val oldPoint = getPositions(component)?.myPosition
    if (oldPoint != newPoint) {
      val model = component.nlComponent.model
      if (oldPoint != null) {
        WriteCommandAction.writeCommandAction(model.file).withName("Move Destination").run<Exception> {
          val path = component.nlComponent.idPath
          val action = object : BasicUndoableAction(model.virtualFile) {
            override fun undo() {
              component.setPosition(oldPoint.x, oldPoint.y)
              val positions = getPositionsFromPath(path)
              positions.myPosition = oldPoint
              tagPositionMap.inverse().remove(positions)
              sceneManager.requestRender()
            }

            override fun redo() {
              component.setPosition(newPoint.x, newPoint.y)
              val positions = getPositionsFromPath(path)
              positions.myPosition = newPoint
              tagPositionMap.inverse().remove(positions)
              sceneManager.requestRender()
            }
          }
          UndoManager.getInstance(component.nlComponent.model.project).undoableActionPerformed(action)
        }
      }
      val newPositions = getPositions(component)
      newPositions?.myPosition = newPoint
      tagPositionMap.inverse().remove(newPositions)
      tagPositionMap[component.nlComponent.tagPointer] = newPositions
      val fileName = component.nlComponent.model.virtualFile.name
      rectifyIds(model.components.flatMap { it.children }, storage.state[fileName]!!)
    }
  }

  private fun getPositions(component: SceneComponent): LayoutPositions? {
    val tag = component.nlComponent.tagPointer
    var componentPositions = tagPositionMap[tag]
    if (componentPositions == null) {
      val nlComponent = component.nlComponent

      val path: List<String?> = nlComponent.idPath
      componentPositions = getPositionsFromPath(path)
    }
    return componentPositions
  }

  private fun getPositionsFromPath(pathWithNulls: List<String?>): LayoutPositions {
    val path = pathWithNulls.map { it ?: "_null" }
    var positions = storage.state
    for (parentId in path) {
      var newPositions = positions[parentId]
      if (newPositions == null) {
        newPositions = LayoutPositions()
        positions.put(parentId, newPositions)
      }
      positions = newPositions
    }
    return positions
  }

  override fun restorePositionData(path: List<String>, position: Any) {
    if (position !is LayoutPositions) {
      return
    }
    val existing = getPositionsFromPath(path)
    existing.myPosition = position.myPosition
    existing.myPositions = position.myPositions
  }

  override fun getPositionData(component: SceneComponent) = getPositions(component)

  /**
   * This attempts to fix up the persisted information with any id changes and any deleted components.
   */
  private fun rectifyIds(components: Collection<NlComponent>,
                         layoutPositions: LayoutPositions) {
    val seenComponents = mutableSetOf<String>()
    for (component in components) {
      val tag = component.tagPointer
      val cachedPositions = tagPositionMap[tag] ?: LayoutPositions()
      val id = if (component.isInclude) {
        component.getAttribute(AUTO_URI, ATTR_GRAPH)?.substring(NAVIGATION_PREFIX.length)
      }
      else {
        component.id
      }
      val persistedPositions = layoutPositions[id]
      if (cachedPositions != persistedPositions) {
        val idMap = layoutPositions.myPositions
        val origId = idMap.entries.find { it.value == cachedPositions }?.key
        if (origId != null) {
          idMap.remove(origId)
        }
        if (id != null) {
          idMap[id] = cachedPositions
        }
      }
      if (id != null) {
        seenComponents.add(id)
      }
      rectifyIds(component.children, cachedPositions)
    }
    layoutPositions.myPositions.keys.retainAll(seenComponents)
  }

  @VisibleForTesting
  data class Point @JvmOverloads constructor(var x: Int = 0, var y: Int = 0)

  @VisibleForTesting
  class LayoutPositions {
    // Map of id to layout position

    // Somehow making it final breaks persistence
    var myPositions: MutableMap<String, LayoutPositions> = mutableMapOf()

    var myPosition: Point? = null

    operator fun get(id: String?): LayoutPositions? {
      return myPositions[id]
    }

    fun put(id: String, sub: LayoutPositions) {
      myPositions[id] = sub
    }
  }

  @State(name = "navEditor-manualLayoutAlgorithm2", storages = [com.intellij.openapi.components.Storage(file = "navEditor.xml")])
  private class Storage : PersistentStateComponent<ManualLayoutAlgorithm.LayoutPositions> {
    @VisibleForTesting
    internal var rootPositions: LayoutPositions? = null

    override fun getState(): LayoutPositions {
      var result = rootPositions
      if (result == null) {
        result = LayoutPositions()
        rootPositions = result
      }
      return result
    }

    override fun loadState(state: LayoutPositions) {
      rootPositions = state
    }
  }

  override fun canSave(): Boolean {
    return true
  }
}
