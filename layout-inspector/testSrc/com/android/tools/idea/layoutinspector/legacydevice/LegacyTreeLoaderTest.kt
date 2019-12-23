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
package com.android.tools.idea.layoutinspector.legacydevice

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ALPHA
import com.android.SdkConstants.ATTR_GRAVITY
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_TOP
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.ATTR_TEXT_COLOR
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LegacyTreeLoaderTest {

  private val example =
    "androidx.appcompat.widget.AppCompatTextView@29a4a1f text:mCurTextColor=11,-1979711488 text:mGravity=7,8388659 " +
    "text:mText=21,Hello\\nWorld , =  @ : getEllipsize()=4,null text:getScaledTextSize()=4,14.0 text:getSelectionEnd()=2,-1 " +
    "text:getSelectionStart()=2,-1 text:getTextSize()=4,49.0 text:getTypefaceStyle()=6,NORMAL bg_=4,null layout:mBottom=3,189 " +
    "drawing:mClipBounds=4,null theme:com.example.myapplication:style/AppTheme()=6,forced " +
    "theme:android:style/Theme.DeviceDefault.Light.DarkActionBar()=6,forced fg_=4,null mID=11,id/textView drawing:mLayerType=4,NONE " +
    "layout:mLeft=1,0 measurement:mMeasuredHeight=3,123 measurement:mMeasuredWidth=4,1432 measurement:mMinHeight=1,0 " +
    "measurement:mMinWidth=1,0 padding:mPaddingBottom=1,0 padding:mPaddingLeft=1,0 padding:mPaddingRight=1,0 padding:mPaddingTop=1,0 " +
    "mPrivateFlags_DRAWN=4,0x20 mPrivateFlags=9,0x1008830 layout:mRight=4,1432 scrolling:mScrollX=1,0 scrolling:mScrollY=1,0 " +
    "mSystemUiVisibility=3,0x0 layout:mTop=2,66 padding:mUserPaddingBottom=1,0 padding:mUserPaddingEnd=11,-2147483648 " +
    "padding:mUserPaddingLeft=1,0 padding:mUserPaddingRight=1,0 padding:mUserPaddingStart=11,-2147483648 mViewFlags=10,0x18000010 " +
    "drawing:getAlpha()=3,1.0 layout:getBaseline()=2,52 accessibility:getContentDescription()=4,null " +
    "focus:getDefaultFocusHighlightEnabled()=4,true drawing:getElevation()=3,0.0 getFilterTouchesWhenObscured()=5,false " +
    "getFitsSystemWindows()=5,false focus:getFocusable()=14,FOCUSABLE_AUTO layout:getHeight()=3,123 " +
    "accessibility:getImportantForAccessibility()=3,yes getImportantForAutofill()=3,yes accessibility:getLabelFor()=2,-1 " +
    "layout:getLayoutDirection()=22,RESOLVED_DIRECTION_LTR layout:layout_gravity=4,NONE layout:layout_weight=3,0.0 " +
    "layout:layout_bottomMargin=1,0 layout:layout_endMargin=11,-2147483648 layout:layout_leftMargin=1,0 " +
    "layout:layout_mMarginFlags_LEFT_MARGIN_UNDEFINED_MASK=3,0x4 layout:layout_mMarginFlags_RIGHT_MARGIN_UNDEFINED_MASK=3,0x8 " +
    "layout:layout_mMarginFlags=4,0x0C layout:layout_rightMargin=1,0 layout:layout_startMargin=11,-2147483648 " +
    "layout:layout_topMargin=1,0 layout:layout_height=12,WRAP_CONTENT layout:layout_width=12,MATCH_PARENT " +
    "layout:getLocationOnScreen_x()=1,4 layout:getLocationOnScreen_y()=3,350 measurement:getMeasuredHeightAndState()=3,123 " +
    "measurement:getMeasuredWidthAndState()=4,1432 drawing:getPivotX()=5,716.0 drawing:getPivotY()=4,61.5 " +
    "layout:getRawLayoutDirection()=7,INHERIT text:getRawTextAlignment()=7,GRAVITY text:getRawTextDirection()=7,INHERIT " +
    "drawing:getRotation()=3,0.0 drawing:getRotationX()=3,0.0 drawing:getRotationY()=3,0.0 drawing:getScaleX()=3,1.0 " +
    "drawing:getScaleY()=3,1.0 getScrollBarStyle()=14,INSIDE_OVERLAY drawing:getSolidColor()=1,0 getTag()=4,null " +
    "text:getTextAlignment()=7,GRAVITY text:getTextDirection()=12,FIRST_STRONG drawing:getTransitionAlpha()=3,1.0 " +
    "getTransitionName()=4,null drawing:getTranslationX()=3,0.0 drawing:getTranslationY()=3,0.0 drawing:getTranslationZ()=3,0.0 " +
    "getVisibility()=7,VISIBLE layout:getWidth()=4,1432 drawing:getX()=3,0.0 drawing:getY()=4,66.0 drawing:getZ()=3,0.0 " +
    "focus:hasFocus()=5,false drawing:hasOverlappingRendering()=5,false drawing:hasShadow()=5,false layout:hasTransientState()=5,false " +
    "isActivated()=5,false isClickable()=5,false drawing:isDrawingCacheEnabled()=5,false isEnabled()=4,true focus:isFocusable()=5,false " +
    "focus:isFocusableInTouchMode()=5,false focus:isFocused()=5,false focus:isFocusedByDefault()=5,false " +
    "isHapticFeedbackEnabled()=4,true drawing:isHardwareAccelerated()=4,true isHovered()=5,false isInTouchMode()=4,true " +
    "focus:isKeyboardNavigationCluster()=5,false layout:isLayoutRtl()=5,false drawing:isOpaque()=5,false isPressed()=5,false " +
    "isSelected()=5,false isSoundEffectsEnabled()=4,true drawing:willNotCacheDrawing()=5,false drawing:willNotDraw()=5,false\nDONE.\n"


  @Test
  fun testExample() {
    val provider = LegacyPropertiesProvider()
    val propertiesUpdater = LegacyPropertiesProvider.Updater()
    val (root, hash) = LegacyTreeLoader.parseLiveViewNode(example.toByteArray(Charsets.UTF_8), propertiesUpdater)!!
    propertiesUpdater.apply(provider)
    var properties = PropertiesTable.emptyTable<InspectorPropertyItem>()
    provider.resultListeners.add { _, _, table -> properties = table }
    provider.requestProperties(root)
    assertThat(hash).isEqualTo("androidx.appcompat.widget.AppCompatTextView@29a4a1f")
    assertThat(root.drawId).isEqualTo(0x29a4a1f)
    assertThat(root.x).isEqualTo(4)
    assertThat(root.y).isEqualTo(350)
    assertThat(root.width).isEqualTo(1432)
    assertThat(root.height).isEqualTo(123)
    assertThat(root.scrollX).isEqualTo(0)
    assertThat(root.scrollY).isEqualTo(0)
    assertThat(root.viewId.toString()).isEqualTo("ResourceReference{namespace=apk/res-auto, type=id, name=textView}")
    assertThat(properties[ANDROID_URI, ATTR_ID].value).isEqualTo("@id/textView")
    assertThat(properties[ANDROID_URI, ATTR_TEXT].value).isEqualTo("Hello\\nWorld , =  @ :")
    assertThat(properties[ANDROID_URI, ATTR_TEXT_COLOR].value).isEqualTo("#8A000000")
    assertThat(properties[ANDROID_URI, ATTR_ALPHA].value).isEqualTo("1.0")
    assertThat(properties[ANDROID_URI, ATTR_GRAVITY].value).isEqualTo("top|start")
    assertThat(properties[ANDROID_URI, ATTR_LAYOUT_MARGIN_TOP].value).isEqualTo("0")
    assertThat(properties[ANDROID_URI, ATTR_LAYOUT_MARGIN_BOTTOM].value).isEqualTo("0")
    assertThat(properties[ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT].value).isEqualTo("0")
    assertThat(properties[ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT].value).isEqualTo("0")
  }
}
