/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.palette;

import com.android.xml.XmlBuilder;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.handlers.TextViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.common.model.NlComponent;
import com.google.common.base.Splitter;
import com.intellij.openapi.util.text.StringUtil;
import icons.AndroidIcons;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.xml.ws.Holder;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.api.PaletteComponentHandler.IN_PLATFORM;
import static com.android.tools.idea.uibuilder.api.PaletteComponentHandler.NO_PREVIEW;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test case base class with assert methods for checking palette items.
 */
public abstract class PaletteTestCase extends AndroidTestCase {
  private static final ViewHandler STANDARD_VIEW = new ViewHandler();
  private static final ViewHandler STANDARD_TEXT = new TextViewHandler();
  private static final ViewHandler STANDARD_LAYOUT = new ViewGroupHandler();
  private static final Splitter SPLITTER = Splitter.on("\n").trimResults();
  private static final double NO_SCALE = 1.0;

  @NotNull
  public static Palette.Item findItem(@NotNull Palette palette, @NotNull String tagName) {
    Holder<Palette.Item> found = new Holder<>();
    palette.accept(item -> {
      if (item.getTagName().equals(tagName)) {
        found.value = item;
      }
    });
    if (found.value == null) {
      throw new RuntimeException("The item: " + tagName + " was not found on the palette.");
    }
    return found.value;
  }

  public static Palette.Group assertIsGroup(@NotNull Palette.BaseItem item, @NotNull String name) {
    assertTrue(item instanceof Palette.Group);
    Palette.Group group = (Palette.Group)item;
    assertEquals(name, group.getName());
    return group;
  }

  public void assertTextViewItem(@NotNull Palette.BaseItem item) {
    assertStandardTextView(item, TEXT_VIEW, IN_PLATFORM);
  }

  public void assertButton(@NotNull Palette.BaseItem item) {
    assertStandardTextView(item, BUTTON, IN_PLATFORM);
  }

  public void assertToggleButton(@NotNull Palette.BaseItem item) {
    assertStandardTextView(item, TOGGLE_BUTTON, IN_PLATFORM);
  }

  public void assertCheckBox(@NotNull Palette.BaseItem item) {
    assertStandardTextView(item, CHECK_BOX, IN_PLATFORM);
  }

  public void assertRadioButton(@NotNull Palette.BaseItem item) {
    assertStandardTextView(item, RADIO_BUTTON, IN_PLATFORM);
  }

  public void assertCheckedTextView(@NotNull Palette.BaseItem item) {
    assertStandardTextView(item, CHECKED_TEXT_VIEW, IN_PLATFORM);
  }

  @Language("XML")
  private static final String SPINNER_XML =
    "<Spinner\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"wrap_content\" />\n";

  @Language("XML")
  private static final String SPINNER_PREVIEW_XML =
    "<Spinner\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:entries=\"@android:array/postalAddressTypes\" />\n";

  public void assertSpinner(@NotNull Palette.BaseItem item) {
    checkItem(item, SPINNER, "Spinner", AndroidIcons.Views.Spinner, SPINNER_XML, SPINNER_PREVIEW_XML,
              SPINNER_XML, IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(SPINNER), "Spinner", AndroidIcons.Views.Spinner);
  }

  @Language("XML")
  private static final String NORMAL_PROGRESS_XML =
    "<ProgressBar\n" +
    "  style=\"?android:attr/progressBarStyle\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "/>\n";

  public void assertNormalProgressBarItem(@NotNull Palette.BaseItem item) {
    checkItem(item, "ProgressBar", "ProgressBar", AndroidIcons.Views.ProgressBar, NORMAL_PROGRESS_XML, NORMAL_PROGRESS_XML,
              NORMAL_PROGRESS_XML, IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent("ProgressBar"), "ProgressBar", AndroidIcons.Views.ProgressBar);
  }

  @Language("XML")
  private static final String HORIZONTAL_PROGRESS_XML =
    "<ProgressBar\n" +
    "  style=\"?android:attr/progressBarStyleHorizontal\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "/>\n";

  @Language("XML")
  private static final String HORIZONTAL_PROGRESS_PREVIEW_XML =
    "<ProgressBar\n" +
    "  android:id=\"@+id/HorizontalProgressBar\"\n" +
    "  style=\"?android:attr/progressBarStyleHorizontal\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "/>\n";

  public void assertHorizontalProgressBarItem(@NotNull Palette.BaseItem item) {
    checkItem(item, "ProgressBar", "ProgressBar (Horizontal)", AndroidIcons.Views.ProgressBarHorizontal, HORIZONTAL_PROGRESS_XML,
              HORIZONTAL_PROGRESS_PREVIEW_XML, HORIZONTAL_PROGRESS_XML, IN_PLATFORM, 2.0);
    NlComponent component = createMockComponent("ProgressBar");
    when(component.getAttribute(null, TAG_STYLE)).thenReturn(ANDROID_STYLE_RESOURCE_PREFIX + "Widget.ProgressBar.Horizontal");
    checkComponent(component, "ProgressBar (Horizontal)", AndroidIcons.Views.ProgressBar);
  }

  public void assertSeekBar(@NotNull Palette.BaseItem item) {
    assertStandardView(item, SEEK_BAR, IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent("SeekBar"), "SeekBar", AndroidIcons.Views.SeekBar);
  }

  @Language("XML")
  private static final String DISCRETE_SEEK_BAR_XML =
    "<SeekBar\n" +
    "  style=\"@style/Widget.AppCompat.SeekBar.Discrete\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:max=\"10\"\n" +
    "  android:progress=\"3\"\n" +
    "/>\n";

  @Language("XML")
  private static final String DISCRETE_SEEK_BAR_PREVIEW_XML =
    "<SeekBar\n" +
    "  android:id=\"@+id/DiscreteSeekBar\"\n" +
    "  style=\"@style/Widget.AppCompat.SeekBar.Discrete\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:max=\"10\"\n" +
    "  android:progress=\"3\"\n" +
    "/>\n";

  public void assertDiscreteSeekBar(@NotNull Palette.BaseItem item) {
    checkItem(item, "SeekBar", "SeekBar (Discrete)", AndroidIcons.Views.SeekBarDiscrete, DISCRETE_SEEK_BAR_XML,
              DISCRETE_SEEK_BAR_PREVIEW_XML,
              DISCRETE_SEEK_BAR_XML, IN_PLATFORM, 1.0);
    NlComponent component = createMockComponent("SeekBar");
    when(component.getAttribute(null, TAG_STYLE)).thenReturn(ANDROID_STYLE_RESOURCE_PREFIX + "Widget.Material.SeekBar.Discrete");
    checkComponent(component, "SeekBar (Discrete)", AndroidIcons.Views.SeekBarDiscrete);
  }

  @Language("XML")
  private static final String QUICK_CONTACT_BADGE_XML =
    "<QuickContactBadge\n" +
    "    android:src=\"@android:drawable/btn_star\"\n" +
    "    android:layout_width=\"wrap_content\"\n" +
    "    android:layout_height=\"wrap_content\" />\n";

  public void assertQuickContactBadge(@NotNull Palette.BaseItem item) {
    checkItem(item, QUICK_CONTACT_BADGE, "QuickContactBadge", AndroidIcons.Views.QuickContactBadge, QUICK_CONTACT_BADGE_XML,
              QUICK_CONTACT_BADGE_XML, QUICK_CONTACT_BADGE_XML, IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(QUICK_CONTACT_BADGE), "QuickContactBadge", AndroidIcons.Views.QuickContactBadge);
  }

  public void assertRatingBar(@NotNull Palette.BaseItem item) {
    assertStandardView(item, "RatingBar", IN_PLATFORM, 0.4);
  }

  public void assertSwitch(@NotNull Palette.BaseItem item) {
    assertStandardTextView(item, SWITCH, IN_PLATFORM);
  }

  public void assertSpace(@NotNull Palette.BaseItem item) {
    assertNoPreviewView(item, SPACE, IN_PLATFORM);
  }

  @Language("XML")
  private static final String PLAIN_EDIT_TEXT_XML =
    "<EditText\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:inputType=\"textPersonName\"\n" +
    "  android:text=\"Name\"\n" +
    "  android:ems=\"10\"\n" +
    "/>\n";

  @Language("XML")
  private static final String PLAIN_EDIT_TEXT_PREVIEW_XML =
    "<EditText\n" +
    "  android:text=\"abc\"\n" +
    "  android:layout_width=\"200dip\"\n" +
    "  android:layout_height=\"wrap_content\">\n" +
    "</EditText>\n";

  public void assertPlainTextEditText(@NotNull Palette.BaseItem item) {
    checkItem(item, EDIT_TEXT, "Plain Text", AndroidIcons.Views.EditText, PLAIN_EDIT_TEXT_XML, PLAIN_EDIT_TEXT_PREVIEW_XML,
              PLAIN_EDIT_TEXT_XML, IN_PLATFORM, 0.8);
    checkComponent(createMockComponent(EDIT_TEXT), "EditText - \"My value for EditText\"", AndroidIcons.Views.EditText);
  }

  final void assertConstraintLayout(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, "android.support.constraint.ConstraintLayout", CONSTRAINT_LAYOUT_LIB_ARTIFACT);
  }

  public void assertGridLayout(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, GRID_LAYOUT, IN_PLATFORM);
  }

  public void assertFlexboxLayout(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, FLEXBOX_LAYOUT, FLEXBOX_LAYOUT_LIB_ARTIFACT);
  }

  public void assertFrameLayout(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, FRAME_LAYOUT, IN_PLATFORM);
  }

  @Language("XML")
  private static final String HORIZONTAL_LINEAR_LAYOUT_XML =
    "<LinearLayout\n" +
    "  android:orientation=\"horizontal\"\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"match_parent\">\n" +
    "</LinearLayout>\n";

  public void assertLinearLayoutItem(@NotNull Palette.BaseItem item) {
    checkItem(item, LINEAR_LAYOUT, "LinearLayout (horizontal)", AndroidIcons.Views.LinearLayout, HORIZONTAL_LINEAR_LAYOUT_XML,
              NO_PREVIEW, NO_PREVIEW, IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(LINEAR_LAYOUT), "LinearLayout (horizontal)", AndroidIcons.Views.LinearLayout);
  }

  @Language("XML")
  private static final String VERTICAL_LINEAR_LAYOUT_XML =
    "<LinearLayout\n" +
    "  android:orientation=\"vertical\"\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"match_parent\">\n" +
    "</LinearLayout>\n";

  public void assertVerticalLinearLayoutItem(@NotNull Palette.BaseItem item) {
    checkItem(item, LINEAR_LAYOUT, "LinearLayout (vertical)", AndroidIcons.Views.VerticalLinearLayout, VERTICAL_LINEAR_LAYOUT_XML,
              NO_PREVIEW, NO_PREVIEW, IN_PLATFORM, NO_SCALE);
    NlComponent component = createMockComponent(LINEAR_LAYOUT);
    when(component.resolveAttribute(ANDROID_URI, ATTR_ORIENTATION)).thenReturn(VALUE_VERTICAL);
    checkComponent(component, "LinearLayout (vertical)", AndroidIcons.Views.VerticalLinearLayout);
  }

  public void assertRelativeLayout(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, RELATIVE_LAYOUT, IN_PLATFORM);
  }

  public void assertTableLayout(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, TABLE_LAYOUT, IN_PLATFORM);
  }

  public void assertTableRow(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, TABLE_ROW, IN_PLATFORM);
  }

  public void assertFragment(@NotNull Palette.BaseItem item) {
    checkItem(item, VIEW_FRAGMENT, "<fragment>", AndroidIcons.Views.Fragment,
              STANDARD_VIEW.getXml(VIEW_FRAGMENT, XmlType.COMPONENT_CREATION), NO_PREVIEW, NO_PREVIEW, IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(VIEW_FRAGMENT), "<fragment>", AndroidIcons.Views.Fragment);
  }

  public void assertRadioGroup(@NotNull Palette.BaseItem item) {
    checkItem(item, RADIO_GROUP, "RadioGroup", AndroidIcons.Views.RadioGroup, STANDARD_VIEW.getXml(RADIO_GROUP, XmlType.COMPONENT_CREATION),
              NO_PREVIEW, NO_PREVIEW, IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(RADIO_GROUP), "RadioGroup (horizontal)", AndroidIcons.Views.RadioGroup);
  }

  @Language("XML")
  private static final String LIST_VIEW_XML =
    "<ListView\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"match_parent\" />\n";

  @Language("XML")
  private static final String LIST_VIEW_PREVIEW_XML =
    "<ListView\n" +
    "  android:id=\"@+id/ListView\"\n" +
    "  android:layout_width=\"200dip\"\n" +
    "  android:layout_height=\"60dip\"\n" +
    "  android:divider=\"#333333\"\n" +
    "  android:dividerHeight=\"1px\" />\n";

  public void assertListView(@NotNull Palette.BaseItem item) {
    checkItem(item, LIST_VIEW, "ListView", AndroidIcons.Views.ListView, LIST_VIEW_XML, LIST_VIEW_PREVIEW_XML, LIST_VIEW_PREVIEW_XML,
              IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(LIST_VIEW), "ListView", AndroidIcons.Views.ListView);
  }

  public void assertGridView(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, GRID_VIEW, IN_PLATFORM);
  }

  @Language("XML")
  private static final String EXPANDABLE_LIST_VIEW_XML =
    "<ExpandableListView\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"match_parent\" />\n";

  @Language("XML")
  private static final String EXPANDABLE_LIST_VIEW_PREVIEW_XML =
    "<ExpandableListView\n" +
    "  android:id=\"@+id/ExpandableListView\"\n" +
    "  android:layout_width=\"200dip\"\n" +
    "  android:layout_height=\"60dip\"\n" +
    "  android:divider=\"#333333\"\n" +
    "  android:dividerHeight=\"1px\" />\n";

  public void assertExpandableListView(@NotNull Palette.BaseItem item) {
    checkItem(item, EXPANDABLE_LIST_VIEW, "ExpandableListView", AndroidIcons.Views.ExpandableListView, EXPANDABLE_LIST_VIEW_XML,
              EXPANDABLE_LIST_VIEW_PREVIEW_XML, EXPANDABLE_LIST_VIEW_PREVIEW_XML, IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(EXPANDABLE_LIST_VIEW), "ExpandableListView", AndroidIcons.Views.ExpandableListView);
  }

  public void assertScrollView(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, SCROLL_VIEW, IN_PLATFORM);
  }

  public void assertHorizontalScrollView(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, HORIZONTAL_SCROLL_VIEW, IN_PLATFORM);
  }

  @Language("XML")
  private static final String TAB_HOST_XML =
    "<TabHost\n" +
    "  android:layout_width=\"200dip\"\n" +
    "  android:layout_height=\"300dip\">\n" +
    "  <LinearLayout\n" +
    "    android:layout_width=\"match_parent\"\n" +
    "    android:layout_height=\"match_parent\"\n" +
    "    android:orientation=\"vertical\">\n" +
    "    <TabWidget\n" +
    "      android:id=\"@android:id/tabs\"\n" +
    "      android:layout_width=\"match_parent\"\n" +
    "      android:layout_height=\"wrap_content\" />\n" +
    "    <FrameLayout\n" +
    "      android:id=\"@android:id/tabcontent\"\n" +
    "      android:layout_width=\"match_parent\"\n" +
    "      android:layout_height=\"match_parent\">\n" +
    "      <LinearLayout\n" +
    "        android:id=\"@+id/tab1\"\n" +
    "        android:layout_width=\"match_parent\"\n" +
    "        android:layout_height=\"match_parent\"\n" +
    "        android:orientation=\"vertical\">\n" +
    "      </LinearLayout>\n" +
    "      <LinearLayout\n" +
    "        android:id=\"@+id/tab2\"\n" +
    "        android:layout_width=\"match_parent\"\n" +
    "        android:layout_height=\"match_parent\"\n" +
    "        android:orientation=\"vertical\">\n" +
    "      </LinearLayout>\n" +
    "      <LinearLayout\n" +
    "        android:id=\"@+id/tab3\"\n" +
    "        android:layout_width=\"match_parent\"\n" +
    "        android:layout_height=\"match_parent\"\n" +
    "        android:orientation=\"vertical\">\n" +
    "      </LinearLayout>\n" +
    "    </FrameLayout>\n" +
    "  </LinearLayout>\n" +
    "</TabHost>\n";

  public void assertTabHost(@NotNull Palette.BaseItem item) {
    checkItem(item, TAB_HOST, "TabHost", AndroidIcons.Views.TabHost, TAB_HOST_XML, NO_PREVIEW, TAB_HOST_XML, IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(TAB_HOST), "TabHost", AndroidIcons.Views.TabHost);
  }

  public void assertWebView(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, WEB_VIEW, IN_PLATFORM);
  }

  public void assertSearchView(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, "SearchView", IN_PLATFORM);
  }

  public void assertViewPager(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, "android.support.v4.view.ViewPager", SUPPORT_LIB_ARTIFACT);
  }

  @Language("XML")
  private static final String IMAGE_BUTTON_XML =
    "<ImageButton\n" +
    "    android:src=\"@android:drawable/btn_star\"\n" +
    "    android:layout_width=\"wrap_content\"\n" +
    "    android:layout_height=\"wrap_content\" />\n";

  public void assertImageButton(@NotNull Palette.BaseItem item) {
    checkItem(item, IMAGE_BUTTON, "ImageButton", AndroidIcons.Views.ImageButton, IMAGE_BUTTON_XML, IMAGE_BUTTON_XML, IMAGE_BUTTON_XML,
              IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(IMAGE_BUTTON), "ImageButton", AndroidIcons.Views.ImageButton);
  }

  @Language("XML")
  private static final String IMAGE_VIEW_XML =
    "<ImageView\n" +
    "    android:src=\"@android:drawable/btn_star\"\n" +
    "    android:layout_width=\"wrap_content\"\n" +
    "    android:layout_height=\"wrap_content\" />\n";

  public void assertImageView(@NotNull Palette.BaseItem item) {
    checkItem(item, IMAGE_VIEW, "ImageView", AndroidIcons.Views.ImageView, IMAGE_VIEW_XML, IMAGE_VIEW_XML, IMAGE_VIEW_XML, IN_PLATFORM,
              NO_SCALE);
    checkComponent(createMockComponent(IMAGE_VIEW), "ImageView", AndroidIcons.Views.ImageView);
  }

  public void assertVideoView(@NotNull Palette.BaseItem item) {
    assertNoPreviewView(item, "VideoView", IN_PLATFORM);
  }

  public void assertTimePicker(@NotNull Palette.BaseItem item) {
    assertStandardView(item, "TimePicker", IN_PLATFORM, 0.4);
  }

  public void assertDatePicker(@NotNull Palette.BaseItem item) {
    assertStandardView(item, "DatePicker", IN_PLATFORM, 0.4);
  }

  public void assertCalendarView(@NotNull Palette.BaseItem item) {
    assertStandardView(item, CALENDAR_VIEW, IN_PLATFORM, 0.4);
  }

  public void assertChronometer(@NotNull Palette.BaseItem item) {
    assertStandardView(item, CHRONOMETER, IN_PLATFORM, NO_SCALE);
  }

  public void assertTextClock(@NotNull Palette.BaseItem item) {
    assertStandardView(item, TEXT_CLOCK, IN_PLATFORM, NO_SCALE);
  }

  public void assertImageSwitcher(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, IMAGE_SWITCHER, IN_PLATFORM);
  }

  public void assertAdapterViewFlipper(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, "AdapterViewFlipper", IN_PLATFORM);
  }

  public void assertStackView(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, STACK_VIEW, IN_PLATFORM);
  }

  public void assertTextSwitcher(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, TEXT_SWITCHER, IN_PLATFORM);
  }

  public void assertViewAnimator(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, VIEW_ANIMATOR, IN_PLATFORM);
  }

  public void assertViewFlipper(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, VIEW_FLIPPER, IN_PLATFORM);
  }

  public void assertViewSwitcher(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, VIEW_SWITCHER, IN_PLATFORM);
  }

  public void assertIncludeItem(@NotNull Palette.BaseItem item) {
    checkItem(item, VIEW_INCLUDE, "<include>", AndroidIcons.Views.Include, "<include/>\n", NO_PREVIEW, NO_PREVIEW, IN_PLATFORM,
              NO_SCALE);
    checkComponent(createMockComponent(VIEW_INCLUDE), "<include>", AndroidIcons.Views.Include);
  }

  public void assertRequestFocus(@NotNull Palette.BaseItem item) {
    checkItem(item, REQUEST_FOCUS, "<requestFocus>", AndroidIcons.Views.RequestFocus, "<requestFocus/>\n", NO_PREVIEW, NO_PREVIEW,
              IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(REQUEST_FOCUS), "<requestFocus>", AndroidIcons.Views.RequestFocus);
  }

  public void assertViewTag(@NotNull Palette.BaseItem item) {
    checkItem(item, VIEW_TAG, "<view>", AndroidIcons.Views.Unknown, "<view/>\n", NO_PREVIEW, NO_PREVIEW, IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(VIEW_TAG), "<view>", AndroidIcons.Views.Unknown);
  }

  public void assertViewStub(@NotNull Palette.BaseItem item) {
    assertNoPreviewView(item, VIEW_STUB, IN_PLATFORM);
  }

  public void assertTextureView(@NotNull Palette.BaseItem item) {
    assertNoPreviewView(item, TEXTURE_VIEW, IN_PLATFORM);
  }

  public void assertSurfaceView(@NotNull Palette.BaseItem item) {
    assertNoPreviewView(item, SURFACE_VIEW, IN_PLATFORM);
  }

  public void assertNumberPicker(@NotNull Palette.BaseItem item) {
    assertNoPreviewView(item, "NumberPicker", IN_PLATFORM);
  }

  public void assertAdView(@NotNull Palette.BaseItem item) {
    assertNoPreviewView(item, AD_VIEW, ADS_ARTIFACT);
  }

  public void assertMapView(@NotNull Palette.BaseItem item) {
    assertNoPreviewView(item, MAP_VIEW, MAPS_ARTIFACT);
  }

  public void assertCoordinatorLayoutItem(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, COORDINATOR_LAYOUT, DESIGN_LIB_ARTIFACT);
  }

  public void assertAppBarLayoutItem(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, APP_BAR_LAYOUT, DESIGN_LIB_ARTIFACT);
  }

  public void assertTabLayout(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, TAB_LAYOUT, DESIGN_LIB_ARTIFACT);
  }

  public void assertTabItem(@NotNull Palette.BaseItem item) {
    assertNoPreviewView(item, TAB_ITEM, DESIGN_LIB_ARTIFACT);
  }

  public void assertNestedScrollViewItem(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, NESTED_SCROLL_VIEW, SUPPORT_LIB_ARTIFACT);
  }

  @Language("XML")
  private static final String FLOATING_ACTION_BUTTON_XML =
    "<android.support.design.widget.FloatingActionButton\n" +
    "  android:src=\"@android:drawable/ic_input_add\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:clickable=\"true\" />\n";

  @Language("XML")
  private static final String FLOATING_ACTION_BUTTON_PREVIEW_XML =
    "<android.support.design.widget.FloatingActionButton\n" +
    "  android:src=\"@android:drawable/ic_input_add\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:clickable=\"true\"\n" +
    "  app:elevation=\"0dp\" />\n";

  public void assertFloatingActionButtonItem(@NotNull Palette.BaseItem item) {
    checkItem(item, FLOATING_ACTION_BUTTON, "FloatingActionButton", AndroidIcons.Views.FloatingActionButton, FLOATING_ACTION_BUTTON_XML,
              FLOATING_ACTION_BUTTON_PREVIEW_XML, FLOATING_ACTION_BUTTON_XML, DESIGN_LIB_ARTIFACT, 0.8);
    checkComponent(createMockComponent(FLOATING_ACTION_BUTTON), "FloatingActionButton", AndroidIcons.Views.FloatingActionButton);
  }

  public void assertTextInputLayoutItem(@NotNull Palette.BaseItem item) {
    checkItem(item, TEXT_INPUT_LAYOUT, STANDARD_VIEW.getTitle(TEXT_INPUT_LAYOUT), STANDARD_LAYOUT.getIcon(TEXT_INPUT_LAYOUT),
              TEXT_INPUT_LAYOUT_XML, NO_PREVIEW, NO_PREVIEW,
              DESIGN_LIB_ARTIFACT, NO_SCALE);
  }

  public void assertCardView(@NotNull Palette.BaseItem item) {
    assertLimitedHeightLayout(item, CARD_VIEW, CARD_VIEW_LIB_ARTIFACT);
  }

  public void assertGridLayoutV7(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, GRID_LAYOUT_V7, GRID_LAYOUT_LIB_ARTIFACT);
  }

  public void assertRecyclerView(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, RECYCLER_VIEW, RECYCLER_VIEW_LIB_ARTIFACT);
  }

  @Language("XML")
  private static final String TEXT_INPUT_LAYOUT_XML =
    "<android.support.design.widget.TextInputLayout\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"wrap_content\">\n" +
    "  <android.support.design.widget.TextInputEditText\n" +
    "    android:layout_width=\"match_parent\"\n" +
    "    android:layout_height=\"wrap_content\"\n" +
    "    android:hint=\"hint\" />\n" +
    "  </android.support.design.widget.TextInputLayout>\n";

  @Language("XML")
  private static final String TOOLBAR_XML =
    "<android.support.v7.widget.Toolbar\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:background=\"?attr/colorPrimary\"\n" +
    "  android:theme=\"?attr/actionBarTheme\"\n" +
    "  android:minHeight=\"?attr/actionBarSize\" />\n";

  @Language("XML")
  private static final String TOOLBAR_PREVIEW_XML =
    "<android.support.v7.widget.Toolbar\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:background=\"?attr/colorPrimary\"\n" +
    "  android:theme=\"?attr/actionBarTheme\"\n" +
    "  android:minHeight=\"?attr/actionBarSize\"\n" +
    "  app:contentInsetStart=\"0dp\"\n" +
    "  app:contentInsetLeft=\"0dp\">\n" +
    "  <ImageButton\n" +
    "    android:src=\"?attr/homeAsUpIndicator\"\n" +
    "    android:layout_width=\"wrap_content\"\n" +
    "    android:layout_height=\"wrap_content\"\n" +
    "    android:tint=\"?attr/actionMenuTextColor\"\n" +
    "    android:style=\"?attr/toolbarNavigationButtonStyle\" />\n" +
    "  <TextView\n" +
    "    android:text=\"v7 Toolbar\"\n" +
    "    android:textAppearance=\"@style/TextAppearance.Widget.AppCompat.Toolbar.Title\"\n" +
    "    android:layout_width=\"wrap_content\"\n" +
    "    android:layout_height=\"wrap_content\"\n" +
    "    android:gravity=\"center_vertical\"\n" +
    "    android:ellipsize=\"end\"\n" +
    "    android:maxLines=\"1\" />\n" +
    "  <ImageButton\n" +
    "    android:src=\"@drawable/abc_ic_menu_moreoverflow_mtrl_alpha\"\n" +
    "    android:layout_width=\"40dp\"\n" +
    "    android:layout_height=\"wrap_content\"\n" +
    "    android:layout_gravity=\"right\"\n" +
    "    android:style=\"?attr/toolbarNavigationButtonStyle\"\n" +
    "    android:tint=\"?attr/actionMenuTextColor\" />\n" +
    "</android.support.v7.widget.Toolbar>\n";

  public void assertToolbarV7(@NotNull Palette.BaseItem item) {
    checkItem(item, TOOLBAR_V7, "Toolbar", AndroidIcons.Views.Toolbar, TOOLBAR_XML, TOOLBAR_PREVIEW_XML, TOOLBAR_PREVIEW_XML,
              APPCOMPAT_LIB_ARTIFACT, 0.5);
    checkComponent(createMockComponent(TOOLBAR_V7), "Toolbar", AndroidIcons.Views.Toolbar);
  }

  private void assertStandardView(@NotNull Palette.BaseItem item,
                                  @NotNull String tag,
                                  @NotNull String expectedGradleCoordinate,
                                  double expectedScale) {
    @Language("XML")
    String xml = STANDARD_VIEW.getXml(tag, XmlType.COMPONENT_CREATION);
    checkItem(item, tag, STANDARD_VIEW.getTitle(tag), STANDARD_VIEW.getIcon(tag), xml, xml, xml, expectedGradleCoordinate, expectedScale);
    checkComponent(createMockComponent(tag), STANDARD_VIEW.getTitle(tag), STANDARD_VIEW.getIcon(tag));
  }

  private void assertStandardLayout(@NotNull Palette.BaseItem item, @NotNull String tag, @NotNull String expectedGradleCoordinate) {
    checkItem(item, tag, STANDARD_VIEW.getTitle(tag), STANDARD_LAYOUT.getIcon(tag), STANDARD_LAYOUT.getXml(tag, XmlType.COMPONENT_CREATION),
              NO_PREVIEW, NO_PREVIEW, expectedGradleCoordinate, NO_SCALE);
    checkComponent(createMockComponent(tag), STANDARD_VIEW.getTitle(tag), STANDARD_LAYOUT.getIcon(tag));
  }

  private void assertNoPreviewView(@NotNull Palette.BaseItem item, @NotNull String tag, @NotNull String expectedGradleCoordinate) {
    checkItem(item, tag, STANDARD_VIEW.getTitle(tag), STANDARD_VIEW.getIcon(tag), STANDARD_VIEW.getXml(tag, XmlType.COMPONENT_CREATION),
              NO_PREVIEW, NO_PREVIEW, expectedGradleCoordinate, NO_SCALE);
    checkComponent(createMockComponent(tag), STANDARD_VIEW.getTitle(tag), STANDARD_VIEW.getIcon(tag));
  }

  private void assertStandardTextView(@NotNull Palette.BaseItem item, @NotNull String tag, @NotNull String expectedGradleCoordinate) {
    @Language("XML")
    String xml = STANDARD_TEXT.getXml(tag, XmlType.COMPONENT_CREATION);
    checkItem(item, tag, STANDARD_TEXT.getTitle(tag), STANDARD_TEXT.getIcon(tag), xml, xml, xml, expectedGradleCoordinate, NO_SCALE);
    checkComponent(createMockComponent(tag), String.format("%1$s - \"My value for %1$s\"", tag), STANDARD_TEXT.getIcon(tag));
  }

  private void assertLimitedHeightLayout(@NotNull Palette.BaseItem item, @NotNull String tag, @NotNull String expectedGradleCoordinate) {
    @Language("XML")
    String xml = new XmlBuilder()
      .startTag(tag)
      .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
      .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
      .endTag(tag)
      .toString();

    checkItem(item, tag, STANDARD_VIEW.getTitle(tag), STANDARD_LAYOUT.getIcon(tag), xml, NO_PREVIEW, NO_PREVIEW,
              expectedGradleCoordinate, NO_SCALE);
    checkComponent(createMockComponent(tag), STANDARD_VIEW.getTitle(tag), STANDARD_LAYOUT.getIcon(tag));
  }

  private static void checkItem(@NotNull Palette.BaseItem base,
                                @NotNull String expectedTag,
                                @NotNull String expectedTitle,
                                @NotNull Icon expectedIcon,
                                @NotNull @Language("XML") String expectedXml,
                                @NotNull @Language("XML") String expectedPreviewXml,
                                @NotNull @Language("XML") String expectedDragPreviewXml,
                                @NotNull String expectedGradleCoordinateId,
                                double expectedScale) {
    assertTrue(base instanceof Palette.Item);
    Palette.Item item = (Palette.Item)base;

    assertEquals(expectedTag + ".Tag", expectedTag, item.getTagName());
    assertEquals(expectedTag + ".Title", expectedTitle, item.getTitle());
    assertEquals(expectedTag + ".Icon", expectedIcon, item.getIcon());
    assertEquals(expectedTag + ".XML", formatXml(expectedXml), formatXml(item.getXml()));
    assertEquals(expectedTag + ".PreviewXML", formatXml(expectedPreviewXml), formatXml(item.getPreviewXml()));
    assertEquals(expectedTag + ".DragPreviewXML", formatXml(expectedDragPreviewXml), formatXml(item.getDragPreviewXml()));
    assertEquals(expectedTag + ".GradleCoordinateId", expectedGradleCoordinateId, item.getGradleCoordinateId());
    assertEquals(expectedTag + ".PreviewScale", expectedScale, item.getPreviewScale());
  }

  private void checkComponent(@NotNull NlComponent component, @NotNull String expectedTitle, @NotNull Icon expectedIcon) {
    ViewHandlerManager handlerManager = ViewHandlerManager.get(getProject());
    ViewHandler handler = handlerManager.getHandlerOrDefault(component);
    String title = handler.getTitle(component);
    String attrs = handler.getTitleAttributes(component);
    if (!StringUtil.isEmpty(attrs)) {
      title += " " + attrs;
    }
    assertEquals(component.getTagName() + ".Component.Title", expectedTitle, title);
    assertEquals(component.getTagName() + ".Component.Icon", expectedIcon, handler.getIcon(component));
  }

  private static NlComponent createMockComponent(@NotNull String tag) {
    NlComponent component = LayoutTestUtilities.createMockComponent();
    when(component.getTagName()).thenReturn(tag);
    when(component.getAttribute(ANDROID_URI, ATTR_TEXT)).thenReturn("My value for " + tag);
    return component;
  }

  @NotNull
  @Language("XML")
  private static String formatXml(@NotNull @Language("XML") String xml) {
    if (xml.equals(NO_PREVIEW)) {
      return xml;
    }
    StringBuilder text = new StringBuilder();
    int indent = 0;
    for (String line : SPLITTER.split(xml)) {
      if (!line.isEmpty()) {
        boolean decrementIndent = line.startsWith("</") || line.startsWith("/>");
        if (decrementIndent && indent > 0) {
          indent--;
        }
        for (int index = 0; index < indent; index++) {
          text.append("  ");
        }
        text.append(line).append("\n");
        if (!decrementIndent && line.startsWith("<")) {
          indent++;
        }
      }
    }
    return text.toString();
  }
}
