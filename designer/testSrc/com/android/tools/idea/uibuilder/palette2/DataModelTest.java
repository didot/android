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
package com.android.tools.idea.uibuilder.palette2;

import static com.android.SdkConstants.APP_BAR_LAYOUT;
import static com.android.SdkConstants.BOTTOM_APP_BAR;
import static com.android.SdkConstants.BOTTOM_NAVIGATION_VIEW;
import static com.android.SdkConstants.BUTTON;
import static com.android.SdkConstants.CHIP;
import static com.android.SdkConstants.CHIP_GROUP;
import static com.android.SdkConstants.FLOATING_ACTION_BUTTON;
import static com.android.SdkConstants.IMAGE_VIEW;
import static com.android.SdkConstants.NAVIGATION_VIEW;
import static com.android.SdkConstants.RECYCLER_VIEW;
import static com.android.SdkConstants.SCROLL_VIEW;
import static com.android.SdkConstants.SWITCH;
import static com.android.SdkConstants.TAB_ITEM;
import static com.android.SdkConstants.TAB_LAYOUT;
import static com.android.SdkConstants.TEXT_INPUT_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;
import static com.android.SdkConstants.VIEW_FRAGMENT;
import static com.android.tools.idea.uibuilder.palette2.DataModel.FAVORITE_ITEMS;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.adtui.workbench.PropertiesComponentMock;
import com.android.tools.idea.common.model.NlLayoutType;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.palette.Palette;
import com.intellij.ide.util.PropertiesComponent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ListModel;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class DataModelTest extends AndroidTestCase {
  private DataModel myDataModel;
  private CategoryListModel myCategoryListModel;
  private ItemListModel myItemListModel;
  private DependencyManager myDependencyManager;
  private boolean myUseAndroidxDependencies = true;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDependencyManager = mock(DependencyManager.class);
      when(myDependencyManager.useAndroidXDependencies()).thenAnswer(new Answer<Boolean>() {
      @Override
      public Boolean answer(@NotNull InvocationOnMock invocation) {
        return myUseAndroidxDependencies;
      }
    });
    myDataModel = new DataModel(myDependencyManager);
    myCategoryListModel = myDataModel.getCategoryListModel();
    myItemListModel = myDataModel.getItemListModel();
    registerApplicationComponent(PropertiesComponent.class, new PropertiesComponentMock());
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    myDataModel = null;
    myCategoryListModel = null;
    myItemListModel = null;
    myDependencyManager = null;
    myUseAndroidxDependencies = false;
  }

  public void testEmptyModelHoldsUsableListModels() {
    assertThat(myCategoryListModel.getSize()).isEqualTo(0);
    assertThat(myItemListModel.getSize()).isEqualTo(0);
  }

  public void testCommonLayoutGroup() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT);
    assertThat(myCategoryListModel.getSize()).isEqualTo(8);
    assertThat(myCategoryListModel.getElementAt(0)).isEqualTo(DataModel.COMMON);
    assertThat(getElementsAsStrings(myItemListModel)).isEmpty();
    myDataModel.categorySelectionChanged(DataModel.COMMON);
    assertThat(getElementsAsStrings(myItemListModel))
      .containsExactly("TextView", "Button", "ImageView", "RecyclerView", "<fragment>", "ScrollView", "Switch").inOrder();
  }

  public void testAddFavorite() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT);
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(2));
    myDataModel.addFavoriteItem(myDataModel.getPalette().getItemById(FLOATING_ACTION_BUTTON.newName()));
    assertThat(PropertiesComponent.getInstance().getValues(FAVORITE_ITEMS)).asList()
                                                                           .containsExactly(TEXT_VIEW, BUTTON, IMAGE_VIEW, RECYCLER_VIEW.oldName(), RECYCLER_VIEW.newName(), VIEW_FRAGMENT, SCROLL_VIEW, SWITCH,
                                                                                            FLOATING_ACTION_BUTTON.newName()).inOrder();
    myDataModel.categorySelectionChanged(DataModel.COMMON);
    assertThat(getElementsAsStrings(myItemListModel))
      .containsExactly("TextView", "Button", "ImageView", "RecyclerView", "<fragment>", "ScrollView", "Switch", "FloatingActionButton")
      .inOrder();
  }

  public void testRemoveFavorite() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT);
    myDataModel.categorySelectionChanged(DataModel.COMMON);
    myDataModel.removeFavoriteItem(myDataModel.getPalette().getItemById("Button"));
    assertThat(PropertiesComponent.getInstance().getValues(FAVORITE_ITEMS)).asList()
                                                                           .containsExactly(TEXT_VIEW, IMAGE_VIEW, RECYCLER_VIEW.oldName(), RECYCLER_VIEW.newName(), VIEW_FRAGMENT, SCROLL_VIEW, SWITCH).inOrder();
    assertThat(getElementsAsStrings(myItemListModel))
      .containsExactly("TextView", "ImageView", "RecyclerView", "<fragment>", "ScrollView", "Switch").inOrder();
  }

  public void testButtonsGroup() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT);
    assertThat(myCategoryListModel.getSize()).isEqualTo(8);
    assertThat(myCategoryListModel.getElementAt(2).getName()).isEqualTo("Buttons");
    assertThat(myCategoryListModel.hasMatchCounts()).isFalse();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(2));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly(
      "Button", "ImageButton", "ChipGroup", "Chip", "CheckBox", "RadioGroup", "RadioButton", "ToggleButton", "Switch",
      "FloatingActionButton").inOrder();
  }

  public void testContainersGroup() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT);
    assertThat(myCategoryListModel.getSize()).isEqualTo(8);
    assertThat(myCategoryListModel.getElementAt(5).getName()).isEqualTo("Containers");
    assertThat(myCategoryListModel.hasMatchCounts()).isFalse();
    StudioFlags.ENABLE_NAV_EDITOR.override(true);
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(5));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly(
      "Spinner", "RecyclerView", "ScrollView", "HorizontalScrollView", "NestedScrollView", "ViewPager", "CardView",
      "AppBarLayout", "BottomAppBar", "NavigationView", "BottomNavigationView", "Toolbar", "TabLayout", "TabItem", "ViewStub",
      "<include>", "<fragment>", "NavHostFragment", "<view>", "<requestFocus>").inOrder();
    assertThat(getElementsAsStrings(myItemListModel)).contains("NavHostFragment");
    StudioFlags.ENABLE_NAV_EDITOR.override(false);
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(5));
    assertThat(getElementsAsStrings(myItemListModel)).doesNotContain("NavHostFragment");
    StudioFlags.ENABLE_NAV_EDITOR.clearOverride();
  }

  public void testSearch() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT);
    myDataModel.setFilterPattern("ima");
    assertThat(getElementsAsStrings(myCategoryListModel))
      .containsExactly(DataModel.RESULTS.getName(), "Text", "Buttons", "Widgets").inOrder();
    assertThat(getMatchCounts()).containsExactly(3, 1, 1, 1).inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("Number (Decimal)", "ImageButton", "ImageView").inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(1));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("Number (Decimal)");
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(2));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("ImageButton");
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(3));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("ImageView");
    myDataModel.setFilterPattern("Floating");
    assertThat(getElementsAsStrings(myCategoryListModel))
      .containsExactly(DataModel.RESULTS.getName(), "Buttons").inOrder();
    assertThat(getMatchCounts()).containsExactly(1, 1).inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("FloatingActionButton").inOrder();
    assertThat(myItemListModel.getElementAt(0).getTagName()).isEqualTo(FLOATING_ACTION_BUTTON.newName());
    
    myUseAndroidxDependencies = false;
    myDataModel.setFilterPattern("Floating");
    assertThat(getMatchCounts()).containsExactly(1, 1).inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("FloatingActionButton").inOrder();
    assertThat(myItemListModel.getElementAt(0).getTagName()).isEqualTo(FLOATING_ACTION_BUTTON.oldName());
  }

  public void testMetaSearch() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT);
    myDataModel.setFilterPattern("material");
    assertThat(getElementsAsStrings(myCategoryListModel))
      .containsExactly(DataModel.RESULTS.getName(), "Text", "Buttons", "Containers").inOrder();
    assertThat(getMatchCounts()).containsExactly(10, 1, 3, 6).inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly(
      "TextInputLayout", "ChipGroup", "Chip", "FloatingActionButton", "AppBarLayout", "BottomAppBar", "NavigationView",
      "BottomNavigationView", "TabLayout", "TabItem").inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(1));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("TextInputLayout");
    assertThat(getElementsAsTagNames(myItemListModel)).containsExactly(TEXT_INPUT_LAYOUT.newName());
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(2));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("ChipGroup", "Chip", "FloatingActionButton").inOrder();
    assertThat(getElementsAsTagNames(myItemListModel)).containsExactly(CHIP_GROUP, CHIP, FLOATING_ACTION_BUTTON.newName());
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(3));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly(
      "AppBarLayout", "BottomAppBar", "NavigationView", "BottomNavigationView", "TabLayout", "TabItem").inOrder();
    assertThat(getElementsAsTagNames(myItemListModel)).containsExactly(
      APP_BAR_LAYOUT.newName(), BOTTOM_APP_BAR, NAVIGATION_VIEW.newName(), BOTTOM_NAVIGATION_VIEW.newName(), TAB_LAYOUT.newName(),
      TAB_ITEM.newName()).inOrder();
  }

  public void testMenuType() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.MENU);
    assertThat(myCategoryListModel.getSize()).isEqualTo(1);
    myDataModel.categorySelectionChanged(DataModel.COMMON);
    assertThat(getElementsAsStrings(myItemListModel))
      .containsExactly("Cast Button", "Menu Item", "Search Item", "Switch Item", "Menu", "Group").inOrder();
  }

  public void testUsingAndroidxDependencies() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT);
    myUseAndroidxDependencies = true;
    myDataModel.setFilterPattern("Floating");
    assertThat(getMatchCounts()).containsExactly(1, 1).inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("FloatingActionButton").inOrder();
    assertThat(myItemListModel.getElementAt(0).getTagName()).isEqualTo(FLOATING_ACTION_BUTTON.newName());
    // Check meta-search
    myDataModel.setFilterPattern("material");
    assertThat(getElementsAsStrings(myCategoryListModel))
      .containsExactly(DataModel.RESULTS.getName(), "Text", "Buttons", "Containers").inOrder();
    assertThat(getMatchCounts()).containsExactly(10, 1, 3, 6).inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(1));
    assertThat(myItemListModel.getElementAt(0).getTagName()).isEqualTo(TEXT_INPUT_LAYOUT.newName());
  }

  public void testUsingOldDependencies() {
    myUseAndroidxDependencies = false;

    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT);
    myDataModel.setFilterPattern("Floating");
    assertThat(getMatchCounts()).containsExactly(1, 1).inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("FloatingActionButton").inOrder();
    assertThat(myItemListModel.getElementAt(0).getTagName()).isEqualTo(FLOATING_ACTION_BUTTON.oldName());
    // Check meta-search
    myDataModel.setFilterPattern("material");
    assertThat(getElementsAsStrings(myCategoryListModel))
      .containsExactly(DataModel.RESULTS.getName(), "Text", "Buttons", "Containers").inOrder();
    assertThat(getMatchCounts()).containsExactly(7, 1, 1, 5).inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(1));
    assertThat(myItemListModel.getElementAt(0).getTagName()).isEqualTo(TEXT_INPUT_LAYOUT.oldName());
  }

  @NotNull
  private static List<String> getElementsAsStrings(@NotNull ListModel<?> model) {
    List<String> elements = new ArrayList<>();
    for (int index = 0; index < model.getSize(); index++) {
      elements.add(model.getElementAt(index).toString());
    }
    return elements;
  }

  @NotNull
  private static List<String> getElementsAsTagNames(@NotNull ListModel<Palette.Item> model) {
    List<String> elements = new ArrayList<>();
    for (int index = 0; index < model.getSize(); index++) {
      elements.add(model.getElementAt(index).getTagName());
    }
    return elements;
  }

  @NotNull
  private List<Integer> getMatchCounts() {
    List<Integer> matchCounts = new ArrayList<>();
    for (int index = 0; index < myCategoryListModel.getSize(); index++) {
      matchCounts.add(myCategoryListModel.getMatchCountAt(index));
    }
    return matchCounts;
  }
}
