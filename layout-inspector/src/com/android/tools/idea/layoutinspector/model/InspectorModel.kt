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
package com.android.tools.idea.layoutinspector.model

import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.intellij.openapi.project.Project
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.properties.Delegates

class InspectorModel(val project: Project) {
  val selectionListeners = mutableListOf<(ViewNode?, ViewNode?) -> Unit>()
  val modificationListeners = mutableListOf<(ViewNode?, ViewNode?, Boolean) -> Unit>()
  val connectionListeners = mutableListOf<(InspectorClient?) -> Unit>()
  val resourceLookup = ResourceLookup(project)

  private fun findSubimages(root: ViewNode?) =
    root?.flatten()?.minus(root)?.any { it.imageBottom != null || it.imageTop != null } == true

  var selection: ViewNode? by Delegates.observable(null as ViewNode?) { _, old, new ->
    if (new != old) {
      selectionListeners.forEach { it(old, new) }
    }
  }

  val hoverListeners = mutableListOf<(ViewNode?, ViewNode?) -> Unit>()
  var hoveredNode: ViewNode? by Delegates.observable(null as ViewNode?) { _, old, new ->
    if (new != old) {
      hoverListeners.forEach { it(old, new) }
    }
  }

  private val roots = mutableMapOf<Any, ViewNode>()
  // dummy node to hold the roots of the current windows.
  val root = ViewNode(-1, "root - hide", null, 0, 0, 0, 0, 0, 0, null, "", 0)

  var hasSubImages = false
    private set

  /** Whether there are currently any views in this model */
  val isEmpty
    get() = root.children.isEmpty()

  /**
   * Get a ViewNode by drawId
   */
  operator fun get(id: Long) = root.flatten().find { it.drawId == id }

  /**
   * Get a ViewNode by viewId name
   */
  operator fun get(id: String) = root.flatten().find { it.viewId?.name == id }

  /**
   * Update [root]'s bounds and children based on any updates to [roots]
   * Also adds a dark layer between windows if DIM_BEHIND is set.
   */
  private fun updateRoot(allIds: List<Long>) {
    root.children.clear()
    val maxWidth = roots.values.map { it.width }.max() ?: 0
    val maxHeight = roots.values.map { it.height }.max() ?: 0
    root.width = maxWidth
    root.height = maxHeight
    for (id in allIds) {
      val viewNode = roots[id] ?: continue
      if (viewNode.isDimBehind) {
        val dimmer = ViewNode(-1, "DIM_BEHIND", null, 0, 0, 0, 0, maxWidth, maxHeight, null, "", 0)
        // TODO: subclass ViewNode so we don't have to create and hold on to this image
        val image = BufferedImage(maxWidth, maxHeight, BufferedImage.TYPE_INT_ARGB)
        dimmer.imageBottom = image
        val graphics = image.graphics
        graphics.color = Color(0.0f, 0.0f, 0.0f, 0.5f)
        graphics.fillRect(0, 0, maxWidth, maxHeight)
        root.children.add(dimmer)
        dimmer.parent = root
      }
      root.children.add(viewNode)
      viewNode.parent = root
    }
  }

  fun updateConnection(client: InspectorClient?) {
    connectionListeners.forEach { it(client) }
  }

  /**
   * Replaces all subtrees with differing root IDs. Existing views are updated.
   */
  fun update(newRoot: ViewNode?, id: Long, allIds: List<Long>) {
    var structuralChange: Boolean = roots.keys.retainAll(allIds)
    val oldRoot = roots[id]
    if (newRoot == oldRoot && !structuralChange) {
      return
    }
    if (newRoot?.drawId != oldRoot?.drawId || newRoot?.qualifiedName != oldRoot?.qualifiedName) {
      if (newRoot != null) {
        roots[id] = newRoot
      }
      else {
        roots.remove(id)
      }
      structuralChange = true
    }
    else {
      if (oldRoot == null || newRoot == null) {
        structuralChange = true
      }
      else {
        val updater = Updater(oldRoot, newRoot)
        structuralChange = updater.update() || structuralChange
      }
    }

    updateRoot(allIds)
    hasSubImages = root.children.any { findSubimages(it) }
    modificationListeners.forEach { it(oldRoot, roots[id], structuralChange) }
  }

  fun notifyModified() = modificationListeners.forEach { it(root, root, false) }

  private class Updater(private val oldRoot: ViewNode, private val newRoot: ViewNode) {
    private val oldNodes = oldRoot.flatten().associateBy { it.drawId }

    fun update(): Boolean {
      return update(oldRoot, oldRoot.parent, newRoot)
    }

    private fun update(oldNode: ViewNode, parent: ViewNode?, newNode: ViewNode): Boolean {
      var modified = (parent != oldNode.parent) || !sameChildren(oldNode, newNode)
      // TODO: should changes below cause modified to be set to true?
      // Maybe each view should have its own modification listener that can listen for such changes?
      oldNode.imageBottom = newNode.imageBottom
      oldNode.imageTop = newNode.imageTop
      oldNode.width = newNode.width
      oldNode.height = newNode.height
      oldNode.x = newNode.x
      oldNode.y = newNode.y
      oldNode.parent = parent

      oldNode.children.clear()
      for (newChild in newNode.children) {
        val oldChild = oldNodes[newChild.drawId]
        if (oldChild != null) {
          modified = update(oldChild, oldNode, newChild) || modified
          oldNode.children.add(oldChild)
        } else {
          oldNode.children.add(newChild)
          newChild.parent = oldNode
        }
      }
      return modified
    }

    private fun sameChildren(oldNode: ViewNode?, newNode: ViewNode?): Boolean {
      if (oldNode?.children?.size != newNode?.children?.size) {
        return false
      }
      return oldNode?.children?.indices?.all { oldNode.children[it].drawId == newNode?.children?.get(it)?.drawId } ?: true
    }
  }
}