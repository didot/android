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
package com.android.tools.idea.naveditor.editor

import com.android.tools.idea.common.util.NlTreeDumper
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.actions.AddGlobalAction
import com.android.tools.idea.naveditor.actions.NestedGraphToolbarAction
import com.android.tools.idea.naveditor.actions.ReturnToSourceAction
import com.android.tools.idea.naveditor.actions.SelectAllAction
import com.android.tools.idea.naveditor.actions.SelectNextAction
import com.android.tools.idea.naveditor.actions.SelectPreviousAction
import com.android.tools.idea.naveditor.actions.StartDestinationAction
import com.android.tools.idea.naveditor.actions.StartDestinationToolbarAction
import com.android.tools.idea.naveditor.actions.ToSelfAction
import com.android.tools.idea.naveditor.model.actionDestinationId
import com.android.tools.idea.naveditor.model.inclusive
import com.android.tools.idea.naveditor.model.isSelfAction
import com.android.tools.idea.naveditor.model.isStartDestination
import com.android.tools.idea.naveditor.model.popUpTo
import com.android.tools.idea.naveditor.model.startDestinationId
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.common.truth.Truth
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito.mock

/**
 * Tests for actions used by the nav editor
 */
class NavActionTest : NavTestCase() {
  fun testAddGlobalAction() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
      }
    }

    val component = model.find("fragment1")!!

    val action = AddGlobalAction(model.surface, component)
    action.actionPerformed(null)

    assertEquals(
      """
        NlComponent{tag=<navigation>, instance=0}
            NlComponent{tag=<fragment>, instance=1}
            NlComponent{tag=<action>, instance=2}
      """.trimIndent(),
      NlTreeDumper().toTree(model.components))

    val globalAction = model.find("action_global_fragment1")!!

    assertNotNull(globalAction.parent)
    assertNull(globalAction.parent?.id)
    assertEquals(globalAction.actionDestinationId, "fragment1")

    assertEquals(listOf(globalAction), model.surface.selectionModel.selection)
  }

  fun testReturnToSourceAction() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
      }
    }

    val component = model.find("fragment1")!!

    val action = ReturnToSourceAction(model.surface, component)
    action.actionPerformed(null)

    assertEquals(
      """
        NlComponent{tag=<navigation>, instance=0}
            NlComponent{tag=<fragment>, instance=1}
                NlComponent{tag=<action>, instance=2}
      """.trimIndent(),
      NlTreeDumper().toTree(model.components)
    )
    val returnToSourceAction = model.find("action_fragment1_pop")!!

    assertEquals(component.id, returnToSourceAction.popUpTo)
    assertTrue(returnToSourceAction.inclusive)
    assertEquals(listOf(returnToSourceAction), model.surface.selectionModel.selection)

    FileDocumentManager.getInstance().saveAllDocuments()
    val result = String(model.virtualFile.contentsToByteArray())
    Truth.assertThat(result.replace("\n *".toRegex(), "\n")).contains(
      """
        <action
        android:id="@+id/action_fragment1_pop"
        app:popUpTo="@id/fragment1"
        app:popUpToInclusive="true" />
      """.trimIndent())
  }

  fun testStartDestinationAction() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
      }
    }

    val component = model.find("fragment1")!!
    val action = StartDestinationAction(component)
    action.actionPerformed(null)

    assertEquals(
      """
        NlComponent{tag=<navigation>, instance=0}
            NlComponent{tag=<fragment>, instance=1}
      """.trimIndent(),
      NlTreeDumper().toTree(model.components)
    )

    assert(component.isStartDestination)
  }

  fun testToSelfAction() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
      }
    }

    val component = model.find("fragment1")!!

    val action = ToSelfAction(model.surface, component)
    action.actionPerformed(null)

    assertEquals(
      """
        NlComponent{tag=<navigation>, instance=0}
            NlComponent{tag=<fragment>, instance=1}
                NlComponent{tag=<action>, instance=2}
      """.trimIndent(),
      NlTreeDumper().toTree(model.components)
    )

    val selfAction = model.find("action_fragment1_self")!!
    assertTrue(selfAction.isSelfAction)
    assertEquals(listOf(selfAction), model.surface.selectionModel.selection)

    FileDocumentManager.getInstance().saveAllDocuments()
    val result = String(model.virtualFile.contentsToByteArray())
    Truth.assertThat(result.replace("\n *".toRegex(), "\n")).contains(
      """
        <action
        android:id="@+id/action_fragment1_self"
        app:destination="@id/fragment1" />
      """.trimIndent())
  }

  fun testSelectNextAction() {
    val model = model("nav.xml") {
      navigation("root") {
        action("action1", destination = "fragment1")
        fragment("fragment1") {
          action("action2", destination = "fragment2")
        }
        fragment("fragment2")
        fragment("fragment3")
        navigation("nested") {
          action("action3", destination = "fragment2")
          fragment("fragment4") {
            action("action4", destination = "fragment1")
            action("action5", destination = "fragment4")
          }
        }
      }
    }

    val surface = NavDesignSurface(project, project)
    surface.model = model

    val action = SelectNextAction(surface)

    performAction(action, surface, "action1")
    performAction(action, surface, "fragment1")
    performAction(action, surface, "action2")
    performAction(action, surface, "fragment2")
    performAction(action, surface, "fragment3")
    performAction(action, surface, "nested")
    performAction(action, surface, "action3")
    performAction(action, surface, "action4")
    performAction(action, surface, "root")
    performAction(action, surface, "action1")
  }

  fun testSelectPreviousAction() {
    val model = model("nav.xml") {
      navigation("root") {
        action("action1", destination = "fragment1")
        fragment("fragment1") {
          action("action2", destination = "fragment2")
        }
        fragment("fragment2")
        fragment("fragment3")
        navigation("nested") {
          action("action3", destination = "fragment2")
          fragment("fragment4") {
            action("action4", destination = "fragment1")
            action("action5", destination = "fragment4")
          }
        }
      }
    }

    val surface = NavDesignSurface(project, project)
    surface.model = model

    val action = SelectPreviousAction(surface)

    performAction(action, surface, "action4")
    performAction(action, surface, "action3")
    performAction(action, surface, "nested")
    performAction(action, surface, "fragment3")
    performAction(action, surface, "fragment2")
    performAction(action, surface, "action2")
    performAction(action, surface, "fragment1")
    performAction(action, surface, "action1")
    performAction(action, surface, "root")
    performAction(action, surface, "action4")
  }

  fun testSelectAllAction() {
    val model = model("nav.xml") {
      navigation {
        action("action1")
        fragment("fragment1")
        fragment("fragment2")
        fragment("fragment3")
      }
    }

    val fragment1 = model.find("fragment1")!!
    val fragment2 = model.find("fragment2")!!
    val fragment3 = model.find("fragment3")!!

    val surface = model.surface as NavDesignSurface
    val action = SelectAllAction(surface)

    action.actionPerformed(null)
    assertEquals(listOf(fragment1, fragment2, fragment3), model.surface.selectionModel.selection)
  }

  fun testStartDestinationToolbarAction() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
      }
    }

    val component = model.find("fragment1")!!

    val surface = model.surface as NavDesignSurface
    surface.selectionModel.setSelection(listOf(component))

    val action = StartDestinationToolbarAction(surface)
    action.actionPerformed(null)

    assertEquals(
      """
        NlComponent{tag=<navigation>, instance=0}
            NlComponent{tag=<fragment>, instance=1}
      """.trimIndent(),
      NlTreeDumper().toTree(model.components)
    )

    assert(component.isStartDestination)
  }

  /**
   *  Reparent fragments 2 and 3 into a new nested navigation
   *  After the reparent:
   *  The action from fragment1 to fragment2 should point to the new navigation
   *  The exit action from fragment4 to fragment2 should also point to the new navigation
   *  The action from fragment2 to fragment3 should remain unchanged
   */
  fun testNestedGraphToolbarAction() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1") {
          action("action1", "fragment2")
        }
        fragment("fragment2") {
          action("action2", "fragment3")
        }
        fragment("fragment3")
        navigation("navigation1") {
          fragment("fragment4") {
            action("action3", "fragment2")
          }
        }
      }
    }

    val surface = model.surface as NavDesignSurface
    surface.selectionModel.setSelection(listOf())
    val action = NestedGraphToolbarAction(surface)

    action.actionPerformed(null)

    assertEquals(
      """
        NlComponent{tag=<navigation>, instance=0}
            NlComponent{tag=<fragment>, instance=1}
                NlComponent{tag=<action>, instance=2}
            NlComponent{tag=<fragment>, instance=3}
                NlComponent{tag=<action>, instance=4}
            NlComponent{tag=<fragment>, instance=5}
            NlComponent{tag=<navigation>, instance=6}
                NlComponent{tag=<fragment>, instance=7}
                    NlComponent{tag=<action>, instance=8}
      """.trimIndent(),
      NlTreeDumper().toTree(model.components)
    )

    val fragment2 = model.find("fragment2")!!
    val fragment3 = model.find("fragment3")!!
    surface.selectionModel.setSelection(listOf(fragment2, fragment3))
    action.actionPerformed(null)

    assertEquals(
      """
        NlComponent{tag=<navigation>, instance=0}
            NlComponent{tag=<fragment>, instance=1}
                NlComponent{tag=<action>, instance=2}
            NlComponent{tag=<navigation>, instance=3}
                NlComponent{tag=<fragment>, instance=4}
                    NlComponent{tag=<action>, instance=5}
            NlComponent{tag=<navigation>, instance=6}
                NlComponent{tag=<fragment>, instance=7}
                    NlComponent{tag=<action>, instance=8}
                NlComponent{tag=<fragment>, instance=9}
      """.trimIndent(),
      NlTreeDumper().toTree(model.components)
    )

    val root = surface.currentNavigation
    val fragment1 = model.find("fragment1")!!
    assertEquals(fragment1.parent, root)

    val navigation1 = model.find("navigation1")
    assertEquals(navigation1?.parent, root)

    val newNavigation = model.find("navigation")
    assertEquals(newNavigation?.parent, root)
    assertEquals(newNavigation?.startDestinationId, "fragment2")

    assertEquals(fragment2.parent, newNavigation)
    assertEquals(fragment3.parent, newNavigation)

    val fragment4 = model.find("fragment4")!!
    assertEquals(fragment4.parent, navigation1)

    val action1 = model.find("action1")!!
    assertEquals(action1.parent, fragment1)
    assertEquals(action1.actionDestinationId, "navigation")

    val action2 = model.find("action2")!!
    assertEquals(action2.parent, fragment2)
    assertEquals(action2.actionDestinationId, "fragment3")

    val action3 = model.find("action3")!!
    assertEquals(action3.parent, fragment4)
    assertEquals(action3.actionDestinationId, "navigation")
  }

  private fun performAction(action: AnAction, surface: NavDesignSurface, id: String) {
    action.actionPerformed(mock(AnActionEvent::class.java))
    val component = surface.model?.find(id)!!
    AndroidTestCase.assertEquals(listOf(component), surface.selectionModel.selection)
  }
}