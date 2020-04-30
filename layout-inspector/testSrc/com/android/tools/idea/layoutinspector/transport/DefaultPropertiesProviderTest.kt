/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.transport

import com.android.SdkConstants.ANDROID_URI
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.idea.layoutinspector.LayoutInspectorTransportRule
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.InspectorGroupPropertyItem
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.NAMESPACE_INTERNAL
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.android.tools.idea.layoutinspector.properties.PropertySection
import com.android.tools.idea.layoutinspector.resource.SourceLocation
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorCommand
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property.Type
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.StringEntry
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Event.EventGroupIds
import com.android.tools.profiler.proto.Common.Event.Kind
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.truth.Truth.assertThat
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiManager
import com.intellij.psi.util.ClassUtil
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.Color
import java.util.concurrent.TimeUnit

@RunsInEdt
class DefaultPropertiesProviderTest {
  private val inspectorRule = LayoutInspectorTransportRule(projectRule = AndroidProjectRule.withSdk())
    .withDefaultDevice(connected = true)
    .withDemoLayout()
    .withCommandHandler(LayoutInspectorCommand.Type.GET_PROPERTIES, ::handleGetPropertiesCommand)

  @get:Rule
  val ruleChain = RuleChain.outerRule(inspectorRule).around(EdtRule())!!

  @Test
  fun testRejectLatePropertyEvent() {
    val provider = inspectorRule.inspectorClient.provider
    val result = ProviderResult()
    provider.resultListeners.add(result::receiveProperties)

    // Request properties for a non existing view.
    // The method handleGetPropertiesCommand generate properties for another view.

    // This imitates that properties are requested on a view but the event is received
    // after the view is no longer accessible. We would want to ignore this event.
    val view = inspectorRule.inspectorModel.root
    provider.requestProperties(view)
    inspectorRule.advanceTime(110, TimeUnit.MILLISECONDS)
    assertThat(result.received).isFalse()
  }

  @Test
  fun testGetProperties() {
    val provider = inspectorRule.inspectorClient.provider
    val result = ProviderResult()
    provider.resultListeners.add(result::receiveProperties)
    val model = inspectorRule.inspectorModel
    val view = model["title"]!!
    val demo = ResourceReference(ResourceNamespace.fromPackageName("com.example"), ResourceType.LAYOUT, "demo")
    val textAppearance = ResourceReference(ResourceNamespace.fromPackageName("com.example"), ResourceType.STYLE, "MyTextStyle")
    val textAppearanceExtra = ResourceReference(ResourceNamespace.fromPackageName("com.example"), ResourceType.STYLE, "MyTextStyle.Extra")
    provider.requestProperties(view).get()
    inspectorRule.advanceTime(110, TimeUnit.MILLISECONDS)

    assertThat(result.provider).isSameAs(provider)
    assertThat(result.view).isSameAs(view)
    val table = result.table
    checkProperty(table, view, "name", Type.STRING, "android.widget.TextView", PropertySection.VIEW, null, NAMESPACE_INTERNAL)
    checkProperty(table, view, "x", Type.INT32, "200", PropertySection.DIMENSION, null, NAMESPACE_INTERNAL)
    checkProperty(table, view, "y", Type.INT32, "400", PropertySection.DIMENSION, null, NAMESPACE_INTERNAL)
    checkProperty(table, view, "width", Type.INT32, "400", PropertySection.DIMENSION, null, NAMESPACE_INTERNAL)
    checkProperty(table, view, "height", Type.INT32, "100", PropertySection.DIMENSION, null, NAMESPACE_INTERNAL)
    checkProperty(table, view, "focused", Type.BOOLEAN, "true", PropertySection.DEFAULT, null)
    checkProperty(table, view, "byte", Type.BYTE, "7", PropertySection.DEFAULT, null)
    checkProperty(table, view, "char", Type.CHAR, "g", PropertySection.DEFAULT, null)
    checkProperty(table, view, "double", Type.DOUBLE, "3.75", PropertySection.DEFAULT, null)
    checkProperty(table, view, "scaleX", Type.FLOAT, "1.75", PropertySection.DEFAULT, null)
    checkProperty(table, view, "scrollX", Type.INT32, "10", PropertySection.DEFAULT, null)
    checkProperty(table, view, "long", Type.INT64, "7000", PropertySection.DEFAULT, null)
    checkProperty(table, view, "short", Type.INT16, "70", PropertySection.DEFAULT, null)
    checkProperty(table, view, "text", Type.STRING, "Hello My World!", PropertySection.DECLARED, demo, ANDROID_URI, true)
    checkProperty(table, view, "textColor", Type.COLOR, "#FF0000", PropertySection.DECLARED, demo, ANDROID_URI, true,
                  detail = listOf(PropertyDetail("#888800", textAppearanceExtra), PropertyDetail("#2122F8", textAppearance)))
    checkProperty(table, view, "foregroundGravity", Type.GRAVITY, "top|fill_horizontal", PropertySection.DEFAULT, null)
    checkProperty(table, view, "visibility", Type.INT_ENUM, "invisible", PropertySection.DEFAULT, null)
    checkProperty(table, view, "labelFor", Type.RESOURCE, "@id/other", PropertySection.DEFAULT, null)
    checkProperty(table, view, "scrollIndicators", Type.INT_FLAG, "left|bottom", PropertySection.DEFAULT, null)
    checkProperty(table, view, "layout_width", Type.INT_ENUM, "match_parent", PropertySection.LAYOUT, null)
    checkProperty(table, view, "layout_height", Type.INT32, "400", PropertySection.LAYOUT, null)
    checkProperty(table, view, "anim", Type.ANIM, "@anim/?", PropertySection.DEFAULT, null)
    checkProperty(table, view, "anim_wcn", Type.ANIM, "@anim/?", PropertySection.DEFAULT, null, ANDROID_URI, true,
                  SourceLocation("AlphaAnimation", findNavigatableFor("android.view.animation.AlphaAnimation")))
    checkProperty(table, view, "animator", Type.ANIMATOR, "@animator/?", PropertySection.DEFAULT, null)
    checkProperty(table, view, "animator_wcn", Type.ANIMATOR, "@animator/?", PropertySection.DEFAULT, null, ANDROID_URI, true,
                  SourceLocation("TimeAnimator", findNavigatableFor("android.animation.TimeAnimator")))
    checkProperty(table, view, "drawable", Type.DRAWABLE, "@drawable/?", PropertySection.DEFAULT, null)
    checkProperty(table, view, "drawable_wcn", Type.DRAWABLE, "@drawable/?", PropertySection.DEFAULT, null, ANDROID_URI, true,
                  SourceLocation("VectorDrawable", findNavigatableFor("android.graphics.drawable.VectorDrawable")))
    checkProperty(table, view, "interpolator1", Type.INTERPOLATOR, "@interpolator/?", PropertySection.DEFAULT, null)
    checkProperty(table, view, "interpolator2", Type.INTERPOLATOR, "@interpolator/?", PropertySection.DEFAULT, null, ANDROID_URI, true,
                  SourceLocation("DecelerateInterpolator", findNavigatableFor("android.view.animation.DecelerateInterpolator")))
    checkProperty(table, view, "interpolator3", Type.INTERPOLATOR, "@android:interpolator/accelerate_decelerate",
                  PropertySection.DEFAULT, null, ANDROID_URI, true,
                  SourceLocation("AccelerateDecelerateInterpolator",
                                 findNavigatableFor("android.view.animation.AccelerateDecelerateInterpolator")))
    checkProperty(table, view, "interpolator4", Type.INTERPOLATOR, "@android:interpolator/anticipate",
                  PropertySection.DEFAULT, null, ANDROID_URI, true,
                  SourceLocation("AnticipateInterpolator",
                                 findNavigatableFor("android.view.animation.AnticipateInterpolator")))
    checkProperty(table, view, "interpolator5", Type.INTERPOLATOR, "@android:interpolator/anticipate_overshoot",
                  PropertySection.DEFAULT, null, ANDROID_URI, true,
                  SourceLocation("AnticipateOvershootInterpolator",
                                 findNavigatableFor("android.view.animation.AnticipateOvershootInterpolator")))
    checkProperty(table, view, "interpolator6", Type.INTERPOLATOR, "@android:interpolator/bounce",
                  PropertySection.DEFAULT, null, ANDROID_URI, true,
                  SourceLocation("BounceInterpolator",
                                 findNavigatableFor("android.view.animation.BounceInterpolator")))
    checkProperty(table, view, "interpolator7", Type.INTERPOLATOR, "@android:interpolator/cycle",
                  PropertySection.DEFAULT, null, ANDROID_URI, true,
                  SourceLocation("CycleInterpolator",
                                 findNavigatableFor("android.view.animation.CycleInterpolator")))
    checkProperty(table, view, "interpolator8", Type.INTERPOLATOR, "@android:interpolator/linear",
                  PropertySection.DEFAULT, null, ANDROID_URI, true,
                  SourceLocation("LinearInterpolator",
                                 findNavigatableFor("android.view.animation.LinearInterpolator")))
    checkProperty(table, view, "interpolator9", Type.INTERPOLATOR, "@android:interpolator/overshoot",
                  PropertySection.DEFAULT, null, ANDROID_URI, true,
                  SourceLocation("OvershootInterpolator",
                                 findNavigatableFor("android.view.animation.OvershootInterpolator")))
    checkProperty(table, view, "int32_zero", Type.INT32, "0", PropertySection.DEFAULT, null)
    checkProperty(table, view, "int32_null", Type.INT32, null, PropertySection.DEFAULT, null)
    checkProperty(table, view, "int64_zero", Type.INT64, "0", PropertySection.DEFAULT, null)
    checkProperty(table, view, "int64_null", Type.INT64, null, PropertySection.DEFAULT, null)
    checkProperty(table, view, "double_zero", Type.DOUBLE, "0.0", PropertySection.DEFAULT, null)
    checkProperty(table, view, "double_null", Type.DOUBLE, null, PropertySection.DEFAULT, null)
    checkProperty(table, view, "float_zero", Type.FLOAT, "0.0", PropertySection.DEFAULT, null)
    checkProperty(table, view, "float_null", Type.FLOAT, null, PropertySection.DEFAULT, null)
    assertThat(table.size).isEqualTo(44)
  }

  private fun findNavigatableFor(className: String): Navigatable {
    val psiManager = PsiManager.getInstance(inspectorRule.project)
    return ClassUtil.findPsiClass(psiManager, className) as Navigatable
  }

  private fun checkProperty(
    table: PropertiesTable<InspectorPropertyItem>,
    view: ViewNode,
    name: String,
    type: Type,
    value: String?,
    group: PropertySection,
    source: ResourceReference?,
    namespace: String = ANDROID_URI,
    expandable: Boolean = false,
    className: SourceLocation? = null,
    detail: List<PropertyDetail> = listOf()
  ) {
    val property = table[namespace, name]
    assertThat(property.name).isEqualTo(name)
    assertThat(property.attrName).isEqualTo(name)
    assertThat(property.namespace).isEqualTo(namespace)
    assertThat(property.type).isEqualTo(type)
    assertThat(property.value).isEqualTo(value)
    assertThat(property.group).isEqualTo(group)
    assertThat(property.source).isEqualTo(source)
    assertThat(property.view).isSameAs(view)
    if (namespace == NAMESPACE_INTERNAL) {
      assertThat(property.resourceLookup).isNull()
    }
    else {
      assertThat(property.resourceLookup).isSameAs(inspectorRule.inspectorModel.resourceLookup)
    }
    if (!expandable) {
      assertThat(property).isNotInstanceOf(InspectorGroupPropertyItem::class.java)
    }
    else {
      assertThat(property).isInstanceOf(InspectorGroupPropertyItem::class.java)
      val exProperty = property as InspectorGroupPropertyItem
      assertThat(exProperty.classLocation).isEqualTo(className)
      assertThat(exProperty.children.map { detailFromResolutionStackItem(it) }).containsExactlyElementsIn(detail).inOrder()
    }
  }

  /**
   * Simulate a property event from the agent.
   */
  private fun handleGetPropertiesCommand(command: Commands.Command, events: MutableList<Common.Event>) {
    val demo = LayoutInspectorProto.Resource.newBuilder().apply { type = 27; namespace = 1; name = 28 }.build()
    val other = LayoutInspectorProto.Resource.newBuilder().apply { type = 22; namespace = 1; name = 23 }.build()
    val textAppearance = LayoutInspectorProto.Resource.newBuilder().apply { type = 63; namespace = 1; name = 64 }.build()
    val textAppearanceExtra = LayoutInspectorProto.Resource.newBuilder().apply { type = 63; namespace = 1; name = 65 }.build()
    val stack = listOf(demo, textAppearanceExtra, textAppearance)
    events.add(Common.Event.newBuilder().apply {
      pid = command.pid
      kind = Kind.LAYOUT_INSPECTOR
      groupId = EventGroupIds.PROPERTIES.number.toLong()
      layoutInspectorEventBuilder.propertiesBuilder.apply {
        viewId = 1
        layout = demo
        addString(StringEntry.newBuilder().apply { id = 1; str = "com.example"})
        addString(StringEntry.newBuilder().apply { id = 2; str = "focused"})
        addString(StringEntry.newBuilder().apply { id = 3; str = "byte"})
        addString(StringEntry.newBuilder().apply { id = 4; str = "char"})
        addString(StringEntry.newBuilder().apply { id = 5; str = "double"})
        addString(StringEntry.newBuilder().apply { id = 6; str = "scaleX"})
        addString(StringEntry.newBuilder().apply { id = 7; str = "scrollX"})
        addString(StringEntry.newBuilder().apply { id = 8; str = "long"})
        addString(StringEntry.newBuilder().apply { id = 9; str = "short"})
        addString(StringEntry.newBuilder().apply { id = 10; str = "text"})
        addString(StringEntry.newBuilder().apply { id = 11; str = "textColor" })
        addString(StringEntry.newBuilder().apply { id = 12; str = "foregroundGravity"})
        addString(StringEntry.newBuilder().apply { id = 13; str = "visibility"})
        addString(StringEntry.newBuilder().apply { id = 14; str = "labelFor"})
        addString(StringEntry.newBuilder().apply { id = 15; str = "scrollIndicators"})
        addString(StringEntry.newBuilder().apply { id = 16; str = "layout_width"})
        addString(StringEntry.newBuilder().apply { id = 17; str = "layout_height"})
        addString(StringEntry.newBuilder().apply { id = 18; str = "Hello My World!"})
        addString(StringEntry.newBuilder().apply { id = 19; str = "top"})
        addString(StringEntry.newBuilder().apply { id = 20; str = "fill_horizontal"})
        addString(StringEntry.newBuilder().apply { id = 21; str = "invisible"})
        addString(StringEntry.newBuilder().apply { id = 22; str = "id"})
        addString(StringEntry.newBuilder().apply { id = 23; str = "other"})
        addString(StringEntry.newBuilder().apply { id = 24; str = "left"})
        addString(StringEntry.newBuilder().apply { id = 25; str = "bottom"})
        addString(StringEntry.newBuilder().apply { id = 26; str = "match_parent"})
        addString(StringEntry.newBuilder().apply { id = 27; str = "layout"})
        addString(StringEntry.newBuilder().apply { id = 28; str = "demo"})
        addString(StringEntry.newBuilder().apply { id = 29; str = "anim"})
        addString(StringEntry.newBuilder().apply { id = 30; str = "anim_wcn"})
        addString(StringEntry.newBuilder().apply { id = 31; str = "android.view.animation.AlphaAnimation"})
        addString(StringEntry.newBuilder().apply { id = 32; str = "animator"})
        addString(StringEntry.newBuilder().apply { id = 33; str = "animator_wcn"})
        addString(StringEntry.newBuilder().apply { id = 34; str = "android.animation.TimeAnimator"})
        addString(StringEntry.newBuilder().apply { id = 35; str = "drawable"})
        addString(StringEntry.newBuilder().apply { id = 36; str = "drawable_wcn"})
        addString(StringEntry.newBuilder().apply { id = 37; str = "android.graphics.drawable.VectorDrawable"})
        addString(StringEntry.newBuilder().apply { id = 38; str = "interpolator1"})
        addString(StringEntry.newBuilder().apply { id = 39; str = "interpolator2"})
        addString(StringEntry.newBuilder().apply { id = 40; str = "interpolator3"})
        addString(StringEntry.newBuilder().apply { id = 41; str = "interpolator4"})
        addString(StringEntry.newBuilder().apply { id = 42; str = "interpolator5"})
        addString(StringEntry.newBuilder().apply { id = 43; str = "interpolator6"})
        addString(StringEntry.newBuilder().apply { id = 44; str = "interpolator7"})
        addString(StringEntry.newBuilder().apply { id = 45; str = "interpolator8"})
        addString(StringEntry.newBuilder().apply { id = 46; str = "interpolator9"})
        addString(StringEntry.newBuilder().apply { id = 47; str = "android.view.animation.DecelerateInterpolator"})
        addString(StringEntry.newBuilder().apply { id = 48; str = "android.view.animation.AccelerateDecelerateInterpolator"})
        addString(StringEntry.newBuilder().apply { id = 49; str = "android.view.animation.AnticipateInterpolator"})
        addString(StringEntry.newBuilder().apply { id = 50; str = "android.view.animation.AnticipateOvershootInterpolator"})
        addString(StringEntry.newBuilder().apply { id = 51; str = "android.view.animation.BounceInterpolator"})
        addString(StringEntry.newBuilder().apply { id = 52; str = "android.view.animation.CycleInterpolator"})
        addString(StringEntry.newBuilder().apply { id = 53; str = "android.view.animation.LinearInterpolator"})
        addString(StringEntry.newBuilder().apply { id = 54; str = "android.view.animation.OvershootInterpolator"})
        addString(StringEntry.newBuilder().apply { id = 55; str = "int32_zero"})
        addString(StringEntry.newBuilder().apply { id = 56; str = "int32_null"})
        addString(StringEntry.newBuilder().apply { id = 57; str = "int64_zero"})
        addString(StringEntry.newBuilder().apply { id = 58; str = "int64_null"})
        addString(StringEntry.newBuilder().apply { id = 59; str = "double_zero"})
        addString(StringEntry.newBuilder().apply { id = 60; str = "double_null"})
        addString(StringEntry.newBuilder().apply { id = 61; str = "float_zero"})
        addString(StringEntry.newBuilder().apply { id = 62; str = "float_null"})
        addString(StringEntry.newBuilder().apply { id = 63; str = "style"})
        addString(StringEntry.newBuilder().apply { id = 64; str = "MyTextStyle"})
        addString(StringEntry.newBuilder().apply { id = 65; str = "MyTextStyle.Extra"})
        addProperty(Property.newBuilder().apply { name = 2; namespace = 1; type = Type.BOOLEAN; int32Value = 1 })
        addProperty(Property.newBuilder().apply { name = 3; namespace = 1; type = Type.BYTE; int32Value = 7 })
        addProperty(Property.newBuilder().apply { name = 4; namespace = 1; type = Type.CHAR; int32Value = 'g'.toInt() })
        addProperty(Property.newBuilder().apply { name = 5; namespace = 1; type = Type.DOUBLE; doubleValue = 3.75 })
        addProperty(Property.newBuilder().apply { name = 6; namespace = 1; type = Type.FLOAT; floatValue = 1.75f })
        addProperty(Property.newBuilder().apply { name = 7; namespace = 1; type = Type.INT32; int32Value = 10 })
        addProperty(Property.newBuilder().apply { name = 8; namespace = 1; type = Type.INT64; int64Value = 7000L })
        addProperty(Property.newBuilder().apply { name = 9; namespace = 1; type = Type.INT16; int32Value = 70 })
        addProperty(Property.newBuilder().apply { name = 10; namespace = 1; type = Type.STRING; int32Value = 18; source = demo })
        addProperty(Property.newBuilder().apply { name = 11; namespace = 1; type = Type.COLOR; int32Value = Color.RED.rgb; source = demo
          addAllResolutionStack(stack) })
        addProperty(Property.newBuilder().apply { name = 12; namespace = 1; type = Type.GRAVITY; flagValueBuilder.addFlag(19).addFlag(20) })
        addProperty(Property.newBuilder().apply { name = 13; namespace = 1; type = Type.INT_ENUM; int32Value = 21 })
        addProperty(Property.newBuilder().apply { name = 14; namespace = 1; type = Type.RESOURCE; resourceValue = other })
        addProperty(Property.newBuilder().apply { name = 15; namespace = 1; type = Type.INT_FLAG; flagValueBuilder.addFlag(24).addFlag(25)})
        addProperty(Property.newBuilder().apply { name = 16; namespace = 1; type = Type.INT_ENUM; int32Value = 26; isLayout = true })
        addProperty(Property.newBuilder().apply { name = 17; namespace = 1; type = Type.INT32; int32Value = 400; isLayout = true })
        addProperty(Property.newBuilder().apply { name = 29; namespace = 1; type = Type.ANIM })
        addProperty(Property.newBuilder().apply { name = 30; namespace = 1; type = Type.ANIM; int32Value = 31 })
        addProperty(Property.newBuilder().apply { name = 32; namespace = 1; type = Type.ANIMATOR })
        addProperty(Property.newBuilder().apply { name = 33; namespace = 1; type = Type.ANIMATOR; int32Value = 34 })
        addProperty(Property.newBuilder().apply { name = 35; namespace = 1; type = Type.DRAWABLE })
        addProperty(Property.newBuilder().apply { name = 36; namespace = 1; type = Type.DRAWABLE; int32Value = 37 })
        addProperty(Property.newBuilder().apply { name = 38; namespace = 1; type = Type.INTERPOLATOR })
        addProperty(Property.newBuilder().apply { name = 39; namespace = 1; type = Type.INTERPOLATOR; int32Value = 47 })
        addProperty(Property.newBuilder().apply { name = 40; namespace = 1; type = Type.INTERPOLATOR; int32Value = 48 })
        addProperty(Property.newBuilder().apply { name = 41; namespace = 1; type = Type.INTERPOLATOR; int32Value = 49 })
        addProperty(Property.newBuilder().apply { name = 42; namespace = 1; type = Type.INTERPOLATOR; int32Value = 50 })
        addProperty(Property.newBuilder().apply { name = 43; namespace = 1; type = Type.INTERPOLATOR; int32Value = 51 })
        addProperty(Property.newBuilder().apply { name = 44; namespace = 1; type = Type.INTERPOLATOR; int32Value = 52 })
        addProperty(Property.newBuilder().apply { name = 45; namespace = 1; type = Type.INTERPOLATOR; int32Value = 53 })
        addProperty(Property.newBuilder().apply { name = 46; namespace = 1; type = Type.INTERPOLATOR; int32Value = 54 })
        addProperty(Property.newBuilder().apply { name = 55; namespace = 1; type = Type.INT32; int32Value = 0 })
        addProperty(Property.newBuilder().apply { name = 56; namespace = 1; type = Type.INT32 })
        addProperty(Property.newBuilder().apply { name = 57; namespace = 1; type = Type.INT64; int64Value = 0 })
        addProperty(Property.newBuilder().apply { name = 58; namespace = 1; type = Type.INT64 })
        addProperty(Property.newBuilder().apply { name = 59; namespace = 1; type = Type.DOUBLE; doubleValue = 0.0 })
        addProperty(Property.newBuilder().apply { name = 60; namespace = 1; type = Type.DOUBLE })
        addProperty(Property.newBuilder().apply { name = 61; namespace = 1; type = Type.FLOAT; floatValue = 0.0f })
        addProperty(Property.newBuilder().apply { name = 62; namespace = 1; type = Type.FLOAT })
      }
    }.build())
  }

  /**
   * Helper class to receive a properties provider result.
   */
  private class ProviderResult {
    var received = false
    var provider: PropertiesProvider? = null
    var view: ViewNode? = null
    var table: PropertiesTable<InspectorPropertyItem> = PropertiesTable.emptyTable()

    fun receiveProperties(receivedProvider: PropertiesProvider,
                          receivedView: ViewNode,
                          receivedTable: PropertiesTable<InspectorPropertyItem>) {
      assertThat(received).named("Unexpected provider event received").isFalse()
      provider = receivedProvider
      view = receivedView
      table = receivedTable
      received = true
    }
  }

  private fun detailFromResolutionStackItem(item: InspectorPropertyItem) =
    PropertyDetail(item.value, item.source)

  private data class PropertyDetail(val value: String?, val source: ResourceReference?)
}
