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
package com.android.tools.idea.naveditor.property

import com.android.SdkConstants.ATTR_URI
import com.android.SdkConstants.AUTO_URI
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase

class NavDeeplinksPropertyTest : NavTestCase() {
  private lateinit var model: SyncNlModel
  private val uri1 = "http://www.example.com"
  private val uri2 = "http://www.example2.com/and/then/some/long/stuff/after"

  override fun setUp() {
    super.setUp()
    model = model("nav.xml") {
      navigation {
        fragment("f1") {
          action("a1", destination = "f2")
        }
        fragment("f2") {
          deeplink(uri1)
          deeplink(uri2)
        }
        fragment("f3")
      }
    }
  }

  fun testMultipleLinks() {
    val property = NavDeeplinkProperty(listOf(model.find("f2")!!))
    assertSameElements(property.properties.keys(), listOf(uri1, uri2))
    assertSameElements(property.properties.values().map { it.name }, listOf(uri1, uri2))
  }

  fun testNoActions() {
    val property = NavDeeplinkProperty(listOf(model.find("f1")!!))
    assertTrue(property.properties.isEmpty())
  }

  fun testModify() {
    val fragment = model.find("f1")!!
    val property = NavDeeplinkProperty(listOf(fragment))
    val deeplink = model.find { it.getAttribute(AUTO_URI, ATTR_URI) == uri1 }!!
    fragment.addChild(deeplink)
    property.refreshList()
    assertEquals(deeplink, property.getChildProperties(uri1)[0].components[0])
    fragment.removeChild(deeplink)
    property.refreshList()
    assertTrue(property.properties.isEmpty())
  }
}