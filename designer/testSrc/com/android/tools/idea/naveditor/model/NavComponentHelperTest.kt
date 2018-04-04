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
package com.android.tools.idea.naveditor.model

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.intellij.openapi.command.WriteCommandAction
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class NavComponentHelperTest {

  @Test
  fun testUiName() {
    val component = mock(NlComponent::class.java)
    `when`(component.id).thenCallRealMethod()
    `when`(component.tagName).thenReturn("myTag")
    assertEquals("myTag", component.uiName)
    `when`(component.resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)).thenReturn("com.example.Foo")
    assertEquals("Foo", component.uiName)
    `when`(component.resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)).thenReturn("Bar")
    assertEquals("Bar", component.uiName)
    `when`(component.resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID)).thenReturn("@+id/myId")
    assertEquals("myId", component.uiName)
  }
}

class NavComponentHelperTest2 : NavTestCase() {

  fun testVisibleDestinations() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1") {
          action("a1", destination = "subnav1")
          action("a2", destination = "activity1")
        }
        activity("activity1")
        navigation("subnav1") {
          fragment("f2")
          fragment("f3")
        }
        navigation("subnav2") {
          fragment("f4")
          navigation("subsubnav") {
            fragment("f5")
          }
        }
      }
    }

    val root = model.find("root")
    val f1 = model.find("f1")
    val f2 = model.find("f2")
    val f3 = model.find("f3")
    val f4 = model.find("f4")
    val f5 = model.find("f5")
    val a1 = model.find("activity1")
    val subnav1 = model.find("subnav1")
    val subnav2 = model.find("subnav2")
    val subsubnav = model.find("subsubnav")

    assertSameElements(f1!!.visibleDestinations, listOf(root, f1, a1, subnav1, subnav2))
    assertSameElements(f2!!.visibleDestinations, listOf(root, f1, a1, f2, f3, subnav1, subnav2))
    assertSameElements(f4!!.visibleDestinations, listOf(root, f1, a1, subnav1, subnav2, f4, subsubnav))
    assertSameElements(f5!!.visibleDestinations, listOf(root, f1, a1, subnav1, subnav2, f4, subsubnav, f5))
    assertSameElements(model.find("root")!!.visibleDestinations, listOf(root, f1, a1, subnav1, subnav2))
    assertSameElements(subnav1!!.visibleDestinations, listOf(root, f1, a1, subnav1, f2, f3, subnav2))
  }

  fun testFindVisibleDestination() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        activity("activity1")
        navigation("subnav1") {
          fragment("f3")
        }
        navigation("subnav2") {
          fragment("f1")
          navigation("subsubnav") {
            fragment("f5")
          }
        }
      }
    }

    assertEquals(model.components[0].getChild(0), model.find("activity1")!!.findVisibleDestination("f1"))
    assertEquals(model.components[0].getChild(0), model.find("f3")!!.findVisibleDestination("f1"))
    assertEquals(model.find("subnav2")!!.getChild(0), model.find("f5")!!.findVisibleDestination("f1"))
  }

  fun testActionDestination() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1") {
          withAttribute("test2", "val2")
        }
        activity("activity1")
        navigation("subnav1") {
          fragment("f3") {
            action("a2", destination = "f1")
          }
        }
        navigation("subnav2") {
          fragment("f1") {
            withAttribute("test1", "val1")
          }
          navigation("subsubnav") {
            fragment("f5") {
              action("a1", destination = "f1")
            }
          }
        }
      }
    }

    assertEquals("val1", model.find("a1")?.actionDestination?.getAttribute(null, "test1"))
    assertEquals("val2", model.find("a2")?.actionDestination?.getAttribute(null, "test2"))
  }

  fun testActionDestinationId() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          action("a1")
        }
      }
    }

    val action = model.find("a1")!!
    val fragment = model.find("f1")!!

    assertNull(action.actionDestinationId)

    WriteCommandAction.runWriteCommandAction(project) { action.actionDestinationId = "f1" }
    assertEquals(fragment, action.actionDestination)
    assertEquals("f1", action.actionDestinationId)

    WriteCommandAction.runWriteCommandAction(project) { action.actionDestinationId = null }
    assertNull(action.actionDestination)
    assertNull(action.actionDestinationId)
  }

  fun testEffectiveDestinationId() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        fragment("f2")
        navigation("nav1") {
          fragment("f3") {
            action("a1", popUpTo = "f1")
            action("a2", popUpTo = "f1", inclusive = true)
            action("a3", destination = "f1", popUpTo = "f2")
          }
        }
      }
    }

    val action1 = model.find("a1")!!
    assertEquals(action1.effectiveDestinationId, "f1")
    val action2 = model.find("a2")!!
    assertNull(action2.effectiveDestinationId)
    val action3 = model.find("a3")!!
    assertEquals(action3.effectiveDestinationId, "f1")
  }

  fun testDefaultActionIds() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1")
        fragment("f2")
      }
    }

    val f1 = model.find("f1")!!
    val root = model.components[0]!!
    WriteCommandAction.runWriteCommandAction(project) { assertEquals("action_f1_to_f2", f1.createAction("f2").id) }
    WriteCommandAction.runWriteCommandAction(project) { assertEquals("action_f1_self", f1.createAction("f1").id) }
    WriteCommandAction.runWriteCommandAction(project) { assertEquals("action_f1_self2", f1.createAction("f1").id) }
    WriteCommandAction.runWriteCommandAction(project) {
      assertEquals(
          "action_f1_pop",
          f1.createAction {
            popUpTo = "f1"
            inclusive = true
          }.id)
    }
    WriteCommandAction.runWriteCommandAction(project) {
      assertEquals(
          "action_f1_pop_including_f2",
          f1.createAction {
            popUpTo = "f2"
            inclusive = true
          }.id)
    }
    WriteCommandAction.runWriteCommandAction(project) { assertEquals("action_global_f1", root.createAction("f1").id) }
  }

  fun testGenerateActionId() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1")
        navigation("subnav")
      }
    }

    assertEquals("action_f1_self", generateActionId(model.find("f1")!!, "f1", null, false))
    assertEquals("action_f1_self", generateActionId(model.find("f1")!!, "f1", "f2", false))
    assertEquals("action_f1_self", generateActionId(model.find("f1")!!, "f1", "f2", true))

    assertEquals("action_subnav_self", generateActionId(model.find("subnav")!!, "subnav", "f2", true))

    assertEquals("action_f1_to_f2", generateActionId(model.find("f1")!!, "f2", null, false))
    assertEquals("action_f1_to_f2", generateActionId(model.find("f1")!!, "f2", "f1", false))
    assertEquals("action_f1_to_f2", generateActionId(model.find("f1")!!, "f2", "f3", true))

    assertEquals("action_global_f1", generateActionId(model.find("subnav")!!, "f1", null, false))
    assertEquals("action_global_f1", generateActionId(model.find("subnav")!!, "f1", "f2", false))
    assertEquals("action_global_f1", generateActionId(model.find("subnav")!!, null, "f1", false))

    assertEquals("action_f1_pop", generateActionId(model.find("f1")!!, null, "f1", true))
    assertEquals("action_f1_pop_including_f2", generateActionId(model.find("f1")!!, null, "f2", true))

    assertEquals("action_nav_pop_including_f1", generateActionId(model.components[0]!!, null, "f1", true))

    assertEquals("", generateActionId(model.find("f1")!!, null, null, true))
  }

  fun testCreateAction() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1")
        navigation("subnav")
      }
    }

    val f1 = model.find("f1")!!
    WriteCommandAction.runWriteCommandAction(project) { f1.createAction("f2") }
    var newAction = model.find("action_f1_to_f2")!!
    assertEquals(f1, newAction.parent)
    assertEquals("f2", newAction.actionDestinationId)

    WriteCommandAction.runWriteCommandAction(project) {
      f1.createAction {
        popUpTo = "f1"
        inclusive = true
      }
    }
    newAction = model.find("action_f1_pop")!!
    assertEquals(f1, newAction.parent)
    assertNull(newAction.actionDestinationId)
    assertEquals("f1", newAction.popUpTo)
  }
}
