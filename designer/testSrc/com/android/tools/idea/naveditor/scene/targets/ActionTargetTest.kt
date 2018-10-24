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
package com.android.tools.idea.naveditor.scene.targets

import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.surface.InteractionManager
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.naveditor.surface.NavView
import com.android.tools.idea.uibuilder.LayoutTestUtilities
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.`when`
import java.awt.event.MouseEvent.BUTTON1

/**
 * Tests for [ActionTarget]
 */
class ActionTargetTest : NavTestCase() {
  fun testSelect() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1") {
          action("action1", destination = "fragment2")
        }
        fragment("fragment2")
      }
    }

    val surface = model.surface as NavDesignSurface
    val view = NavView(surface, surface.sceneManager!!)
    `when`<SceneView>(surface.currentSceneView).thenReturn(view)
    `when`<SceneView>(surface.getSceneView(anyInt(), anyInt())).thenReturn(view)

    val scene = model.surface.scene!!
    val component = scene.getSceneComponent("fragment1")!!
    val component2 = scene.getSceneComponent("fragment2")!!

    component.setPosition(0, 0)
    component2.setPosition(500, 0)

    scene.layout(0, SceneContext.get())
    scene.buildDisplayList(DisplayList(), 0, view)


    val interactionManager = InteractionManager(surface)
    interactionManager.startListening()

    LayoutTestUtilities.clickMouse(interactionManager, BUTTON1, 1, Coordinates.getSwingXDip(view, 300),
                                   Coordinates.getSwingYDip(view, component.centerY), 0)

    assertEquals(model.find("action1"), surface.selectionModel.primary)
    interactionManager.stopListening()
  }

  fun testHighlight() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1") {
          action("action1", destination = "fragment2")
        }
        fragment("fragment2")
      }
    }

    val scene = model.surface.scene!!

    scene.getSceneComponent("fragment1")!!.setPosition(200, 20)
    scene.getSceneComponent("fragment2")!!.setPosition(20, 20)
    scene.sceneManager.layout(false)

    val list = DisplayList()
    val navView = NavView(model.surface as NavDesignSurface, scene.sceneManager)
    val context = SceneContext.get(navView)
    scene.layout(0, context)
    scene.buildDisplayList(list, 0, context)

    val displayListTemplate = "Clip,0,0,967,928\n" +
                              "DrawAction,REGULAR,490.0x400.0x76.5x128.0,400.0x400.0x76.5x128.0,%1\$s\n" +
                              "DrawArrow,2,UP,435.25x532.0x6.0x5.0,%1\$s\n" +
                              "\n" +
                              "DrawFilledRectangle,0,491.0x401.0x74.5x126.0,fffdfdfd\n" +
                              "DrawRectangle,1,490.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
                              "DrawTruncatedText,3,fragment1,498.0x390.0x68.5x5.0,ff656565,Default:0:9,false\n" +
                              "DrawIcon,490.0x389.0x7.0x7.0,START_DESTINATION\n" +
                              "DrawLine,5,491.0x401.0,565.5x527.0,ffa7a7a7,1:2:0\n" +
                              "DrawLine,5,491.0x527.0,565.5x401.0,ffa7a7a7,1:2:0\n" +
                              "\n" +
                              "DrawFilledRectangle,0,401.0x401.0x74.5x126.0,fffdfdfd\n" +
                              "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
                              "DrawTruncatedText,3,fragment2,400.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
                              "DrawLine,5,401.0x401.0,475.5x527.0,ffa7a7a7,1:2:0\n" +
                              "DrawLine,5,401.0x527.0,475.5x401.0,ffa7a7a7,1:2:0\n" +
                              "\n" +
                              "UNClip\n"

    assertEquals(displayListTemplate.format("b2a7a7a7"), list.generateSortedDisplayList())

    model.surface.selectionModel.setSelection(listOf(model.find("action1")))
    list.clear()
    scene.buildDisplayList(list, 0, context)

    assertEquals(displayListTemplate.format("ff1886f7"), list.generateSortedDisplayList())
  }

  fun testDirection() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1")
        fragment("fragment2") {
          action(id = "action1", destination = "fragment1")
        }
        fragment("fragment3") {
          action(id = "action2", destination = "fragment1")
        }
        fragment("fragment4") {
          action(id = "action3", destination = "fragment1")
        }
        fragment("fragment5") {
          action(id = "action4", destination = "fragment1")
        }
        fragment("fragment6") {
          action(id = "action5", destination = "fragment1")
        }
      }
    }

    val scene = model.surface.scene!!

    //  |---------|
    //  |    2  3 |
    //  | 4  1    |
    //  |    5  6 |
    //  |---------|
    scene.getSceneComponent("fragment1")!!.setPosition(500, 500)
    scene.getSceneComponent("fragment2")!!.setPosition(500, 0)
    scene.getSceneComponent("fragment3")!!.setPosition(1000, 0)
    scene.getSceneComponent("fragment4")!!.setPosition(0, 500)
    scene.getSceneComponent("fragment5")!!.setPosition(500, 1000)
    scene.getSceneComponent("fragment6")!!.setPosition(1000, 1000)

    scene.sceneManager.layout(false)
    val list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    // Arrows should be down for 2 and 3, right for 4, up for 5 and 6
    assertEquals(
      "Clip,0,0,1377,1428\n" +
      "DrawAction,REGULAR,650.0x400.0x76.5x128.0,650.0x650.0x76.5x128.0,b2a7a7a7\n" +
      "DrawArrow,2,DOWN,685.25x630.0x6.0x5.0,b2a7a7a7\n" +
      "\n" +
      "DrawAction,REGULAR,900.0x400.0x76.5x128.0,650.0x650.0x76.5x128.0,b2a7a7a7\n" +
      "DrawArrow,2,DOWN,685.25x630.0x6.0x5.0,b2a7a7a7\n" +
      "\n" +
      "DrawAction,REGULAR,400.0x650.0x76.5x128.0,650.0x650.0x76.5x128.0,b2a7a7a7\n" +
      "DrawArrow,2,RIGHT,641.0x711.0x5.0x6.0,b2a7a7a7\n" +
      "\n" +
      "DrawAction,REGULAR,650.0x900.0x76.5x128.0,650.0x650.0x76.5x128.0,b2a7a7a7\n" +
      "DrawArrow,2,UP,685.25x782.0x6.0x5.0,b2a7a7a7\n" +
      "\n" +
      "DrawAction,REGULAR,900.0x900.0x76.5x128.0,650.0x650.0x76.5x128.0,b2a7a7a7\n" +
      "DrawArrow,2,UP,685.25x782.0x6.0x5.0,b2a7a7a7\n" +
      "\n" +
      "DrawFilledRectangle,0,651.0x651.0x74.5x126.0,fffdfdfd\n" +
      "DrawRectangle,1,650.0x650.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawTruncatedText,3,fragment1,658.0x640.0x68.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawIcon,650.0x639.0x7.0x7.0,START_DESTINATION\n" +
      "DrawLine,5,651.0x651.0,725.5x777.0,ffa7a7a7,1:2:0\n" +
      "DrawLine,5,651.0x777.0,725.5x651.0,ffa7a7a7,1:2:0\n" +
      "\n" +
      "DrawFilledRectangle,0,651.0x401.0x74.5x126.0,fffdfdfd\n" +
      "DrawRectangle,1,650.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawTruncatedText,3,fragment2,650.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawLine,5,651.0x401.0,725.5x527.0,ffa7a7a7,1:2:0\n" +
      "DrawLine,5,651.0x527.0,725.5x401.0,ffa7a7a7,1:2:0\n" +
      "\n" +
      "DrawFilledRectangle,0,901.0x401.0x74.5x126.0,fffdfdfd\n" +
      "DrawRectangle,1,900.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawTruncatedText,3,fragment3,900.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawLine,5,901.0x401.0,975.5x527.0,ffa7a7a7,1:2:0\n" +
      "DrawLine,5,901.0x527.0,975.5x401.0,ffa7a7a7,1:2:0\n" +
      "\n" +
      "DrawFilledRectangle,0,401.0x651.0x74.5x126.0,fffdfdfd\n" +
      "DrawRectangle,1,400.0x650.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawTruncatedText,3,fragment4,400.0x640.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawLine,5,401.0x651.0,475.5x777.0,ffa7a7a7,1:2:0\n" +
      "DrawLine,5,401.0x777.0,475.5x651.0,ffa7a7a7,1:2:0\n" +
      "\n" +
      "DrawFilledRectangle,0,651.0x901.0x74.5x126.0,fffdfdfd\n" +
      "DrawRectangle,1,650.0x900.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawTruncatedText,3,fragment5,650.0x890.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawLine,5,651.0x901.0,725.5x1027.0,ffa7a7a7,1:2:0\n" +
      "DrawLine,5,651.0x1027.0,725.5x901.0,ffa7a7a7,1:2:0\n" +
      "\n" +
      "DrawFilledRectangle,0,901.0x901.0x74.5x126.0,fffdfdfd\n" +
      "DrawRectangle,1,900.0x900.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawTruncatedText,3,fragment6,900.0x890.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawLine,5,901.0x901.0,975.5x1027.0,ffa7a7a7,1:2:0\n" +
      "DrawLine,5,901.0x1027.0,975.5x901.0,ffa7a7a7,1:2:0\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )
  }

  fun testTooltips() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          action ("a1", destination = "f2")
          action("self", destination = "f1")
        }
        fragment("f2")
      }
    }
    val scene = model.surface.scene!!
    val target1 = ActionTarget(scene.getSceneComponent("a1")!!, scene.getSceneComponent("f1")!!, scene.getSceneComponent("f2")!!)
    assertEquals("a1", target1.toolTipText)
    assertEquals("a1", target1.toolTipText)
    val target2 = ActionTarget(scene.getSceneComponent("self")!!, scene.getSceneComponent("f1")!!, scene.getSceneComponent("f1")!!)
    assertEquals("self", target2.toolTipText)
  }
}

