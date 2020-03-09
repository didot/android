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

import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.view
import com.intellij.openapi.project.Project
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class InspectorModelTest {
  @Test
  fun testUpdatePropertiesOnly() {
    val model = model {
      view(ROOT, 1, 2, 3, 4, "rootType") {
        view(VIEW1, 4, 3, 2, 1, "v1Type") {
          view(VIEW3, 5, 6, 7, 8, "v3Type")
        }
        view(VIEW2, 8, 7, 6, 5, "v2Type")
      }
    }
    val origRoot = model.root.children[0]
    var isModified = false
    var newRootReported: ViewNode? = null
    model.modificationListeners.add { _, newRoot, structuralChange ->
      newRootReported = newRoot
      isModified = structuralChange
    }

    val newRoot =
      view(ROOT, 2, 4, 6, 8, "rootType") {
        view(VIEW1, 8, 6, 4, 2, "v1Type") {
          view(VIEW3, 9, 8, 7, 6, "v3Type")
        }
        view(VIEW2, 6, 7, 8, 9, "v2Type")
      }


    val origNodes = model.root.flatten().associateBy { it.drawId }

    model.update(newRoot, ROOT, listOf(ROOT))
    // property change doesn't count as "modified."
    // TODO: confirm this behavior is as desired
    assertFalse(isModified)

    for ((id, orig) in origNodes) {
      assertSame(orig, model[id])
    }
    assertEquals(2, model[ROOT]?.x)
    assertEquals(6, model[VIEW3]?.height)
    assertSame(origRoot, newRootReported)
  }

  @Test
  fun testChildCreated() {
    val model = model {
      view(ROOT, 1, 2, 3, 4, "rootType") {
        view(VIEW1, 4, 3, 2, 1, "v1Type")
      }
    }
    var isModified = false
    model.modificationListeners.add { _, _, structuralChange -> isModified = structuralChange }

    val newRoot =
      view(ROOT, 1, 2, 3, 4, "rootType") {
        view(VIEW1, 4, 3, 2, 1, "v1Type") {
          view(VIEW3, 9, 8, 7, 6, "v3Type")
        }
      }

    val origNodes = model.root.flatten().associateBy { it.drawId }

    model.update(newRoot, ROOT, listOf(ROOT))
    assertTrue(isModified)

    val newNodes = model.root.flatten().associateBy { it.drawId }
    assertSameElements(newNodes.keys, origNodes.keys.plus(VIEW3))
    assertSameElements(origNodes[VIEW1]?.children!!, newNodes[VIEW3])
  }

  @Test
  fun testNodeDeleted() {
    val model = model {
      view(ROOT, 1, 2, 3, 4, "rootType") {
        view(VIEW1, 4, 3, 2, 1, "v1Type") {
          view(VIEW3, 9, 8, 7, 6, "v3Type")
        }
      }
    }
    var isModified = false
    model.modificationListeners.add { _, _, structuralChange -> isModified = structuralChange }

    val newRoot =
      view(ROOT, 1, 2, 3, 4, "rootType") {
        view(VIEW1, 4, 3, 2, 1, "v1Type")
      }

    val origNodes = model.root.flatten().associateBy { it.drawId }

    model.update(newRoot, ROOT, listOf(ROOT))
    assertTrue(isModified)

    val newNodes = model.root.flatten().associateBy { it.drawId }
    assertSameElements(newNodes.keys.plus(VIEW3), origNodes.keys)
    assertEquals(true, origNodes[VIEW1]?.children?.isEmpty())
  }

  @Test
  fun testNodeChanged() {
    val model = model {
      view(ROOT, 2, 4, 6, 8, "rootType") {
        view(VIEW1, 8, 6, 4, 2, "v1Type") {
          view(VIEW3, 9, 8, 7, 6, "v3Type")
        }
        view(VIEW2, 6, 7, 8, 9, "v2Type")
      }
    }
    var isModified = false
    model.modificationListeners.add { _, _, structuralChange -> isModified = structuralChange }

    val newRoot =
      view(ROOT, 2, 4, 6, 8, "rootType") {
        view(VIEW4, 8, 6, 4, 2, "v4Type") {
          view(VIEW3, 9, 8, 7, 6, "v3Type")
        }
        view(VIEW2, 6, 7, 8, 9, "v2Type")
      }

    val origNodes = model.root.flatten().associateBy { it.drawId }

    model.update(newRoot, ROOT, listOf(ROOT))
    assertTrue(isModified)

    assertSame(origNodes[ROOT], model[ROOT])
    assertSame(origNodes[VIEW2], model[VIEW2])

    assertNotSame(origNodes[VIEW1], model[VIEW4])
    assertSameElements(model[ROOT]!!.children.map { it.drawId }, VIEW4, VIEW2)
    assertEquals("v4Type", model[VIEW4]?.qualifiedName)
    assertEquals("v3Type", model[VIEW3]?.qualifiedName)
    assertEquals(8, model[VIEW3]?.y)
  }

  @Test
  fun testWindows() {
    val model = InspectorModel(mock(Project::class.java))
    assertTrue(model.isEmpty)

    // add first window
    val window1 = view(ROOT, 2, 4, 6, 8, "rootType") {
      view(VIEW1, 8, 6, 4, 2, "v1Type")
    }
    model.update(window1, ROOT, listOf(ROOT))
    assertFalse(model.isEmpty)
    assertNotNull(model[VIEW1])
    assertEquals(listOf(ROOT), model.root.children.map { it.drawId })

    // add second window
    var window2 = view(VIEW2, 2, 4, 6, 8, "root2Type") {
      view(VIEW3, 8, 6, 4, 2, "v3Type")
    }
    model.update(window2, VIEW2, listOf(ROOT, VIEW2))
    assertFalse(model.isEmpty)
    assertNotNull(model[VIEW1])
    assertNotNull(model[VIEW3])
    assertEquals(listOf(ROOT, VIEW2), model.root.children.map { it.drawId })

    // reverse order of windows
    // same content but new instances, so model.update sees a change
    window2 = view(VIEW2, 2, 4, 6, 8, "root2Type") {
      view(VIEW3, 8, 6, 4, 2, "v3Type")
    }
    model.update(window2, VIEW2, listOf(VIEW2, ROOT))
    assertEquals(listOf(VIEW2, ROOT), model.root.children.map { it.drawId })

    // remove a window
    model.update(null, 0, listOf(VIEW2))
    assertEquals(listOf(VIEW2), model.root.children.map { it.drawId })
    assertNull(model[VIEW1])
    assertNotNull(model[VIEW3])

    // clear
    model.update(null, 0, listOf<Any>())
    assertEmpty(model.root.children)
    assertTrue(model.isEmpty)
  }
}