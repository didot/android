/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.adtui.workbench;

import com.android.annotations.Nullable;
import com.android.tools.adtui.workbench.AttachedToolWindow.PropertyType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import static com.android.tools.adtui.workbench.AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX;
import static com.android.tools.adtui.workbench.AttachedToolWindow.TOOL_WINDOW_TOOLBAR_PLACE;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(JUnit4.class)
public class AttachedToolWindowTest {
  @Rule
  public FrameworkRule myFrameworkRule = new FrameworkRule();
  @Mock
  private AttachedToolWindow.ButtonDragListener<String> myDragListener;
  @Mock
  private SideModel<String> myModel;
  @Mock
  private DumbService myDumbService;
  private ActionManager myActionManager;
  private PropertiesComponent myPropertiesComponent;
  private ToolWindowDefinition<String> myDefinition;
  private AttachedToolWindow<String> myToolWindow;

  @Before
  public void before() {
    initMocks(this);
    Project project = ProjectManager.getInstance().getDefaultProject();
    when(myModel.getProject()).thenReturn(project);
    myPropertiesComponent = PropertiesComponent.getInstance();
    myDefinition = PalettePanelToolContent.getDefinition();
    myActionManager = ActionManager.getInstance();
    myToolWindow = new AttachedToolWindow<>(myDefinition, myDragListener, "DESIGNER", myModel);
  }

  @After
  public void after() {
    if (myToolWindow != null) {
      Disposer.dispose(myToolWindow);
    }
  }

  @Test
  public void testDefault() {
    assertThat(myToolWindow.getToolName()).isEqualTo("PALETTE");
    assertThat(myToolWindow.getDefinition()).isSameAs(myDefinition);
  }

  @Test
  public void testToolOrder() {
    myToolWindow.setToolOrder(77);
    assertThat(myToolWindow.getToolOrder()).isEqualTo(77);
    myToolWindow.setToolOrder(3);
    assertThat(myToolWindow.getToolOrder()).isEqualTo(3);
  }

  @Test
  public void testContext() {
    when(myModel.getContext()).thenReturn("Studio");
    assertThat(myToolWindow.getContext()).isEqualTo("Studio");
  }

  @Test
  public void testContentContext() {
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assertThat(panel).isNotNull();
    myToolWindow.setContext("Android");
    assertThat(panel.getToolContext()).isEqualTo("Android");
    myToolWindow.setContext("Google");
    assertThat(panel.getToolContext()).isEqualTo("Google");
  }

  @Test
  public void testDefaultPropertyValues() {
    assertThat(myToolWindow.isLeft()).isTrue();
    assertThat(myToolWindow.isMinimized()).isFalse();
    assertThat(myToolWindow.isSplit()).isFalse();
    assertThat(myToolWindow.isAutoHide()).isFalse();
    assertThat(myToolWindow.isFloating()).isFalse();

    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.LEFT")).isTrue();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED")).isFalse();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.SPLIT")).isFalse();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.AUTO_HIDE")).isFalse();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.FLOATING")).isFalse();
  }

  @Test
  public void testGettersAndSetters() {
    myToolWindow.setLeft(false);
    assertThat(myToolWindow.isLeft()).isFalse();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.LEFT")).isFalse();

    myToolWindow.setMinimized(true);
    assertThat(myToolWindow.isMinimized()).isTrue();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED")).isTrue();

    myToolWindow.setSplit(true);
    assertThat(myToolWindow.isSplit()).isTrue();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.SPLIT")).isTrue();

    myToolWindow.setAutoHide(true);
    assertThat(myToolWindow.isAutoHide()).isTrue();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.AUTO_HIDE")).isTrue();

    myToolWindow.setFloating(true);
    assertThat(myToolWindow.isFloating()).isTrue();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.FLOATING")).isTrue();
  }

  @Test
  public void testSetPropertyAndUpdateWillNotifyModelAndChangeContent() {
    myToolWindow.setPropertyAndUpdate(PropertyType.LEFT, false);
    assertThat(myToolWindow.isLeft()).isFalse();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.LEFT")).isFalse();
    assertThat(myToolWindow.getContent()).isNotNull();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.LEFT));

    myToolWindow.setPropertyAndUpdate(PropertyType.MINIMIZED, true);
    assertThat(myToolWindow.isMinimized()).isTrue();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED")).isTrue();
    assertThat(myToolWindow.getContent()).isNotNull();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.MINIMIZED));

    myToolWindow.setPropertyAndUpdate(PropertyType.SPLIT, true);
    assertThat(myToolWindow.isSplit()).isTrue();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.SPLIT")).isTrue();
    assertThat(myToolWindow.getContent()).isNotNull();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.SPLIT));

    myToolWindow.setPropertyAndUpdate(PropertyType.AUTO_HIDE, true);
    assertThat(myToolWindow.isAutoHide()).isTrue();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.AUTO_HIDE")).isTrue();
    assertThat(myToolWindow.getContent()).isNotNull();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.AUTO_HIDE));

    myToolWindow.setPropertyAndUpdate(PropertyType.FLOATING, true);
    assertThat(myToolWindow.isFloating()).isTrue();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.FLOATING")).isTrue();
    assertThat(myToolWindow.getContent()).isNull();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.FLOATING));
  }

  @Test
  public void testMinimizeAutoHideIsNotGlobal() {
    myToolWindow.setAutoHide(true);
    myToolWindow.setMinimized(true);
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED")).isFalse();
    myToolWindow.setMinimized(false);
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED")).isFalse();
    myToolWindow.setMinimized(true);
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED")).isFalse();
  }

  @Test
  public void testAutoClose() {
    myToolWindow.setAutoHide(true);
    myToolWindow.setMinimized(false);
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assert panel != null;
    panel.closeAutoHideWindow();
    assertThat(myToolWindow.isMinimized()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.MINIMIZED));
  }

  @Test
  public void testRestoreDefaultLayout() {
    myToolWindow.setMinimized(true);
    myToolWindow.setLeft(false);
    myToolWindow.setSplit(true);
    myToolWindow.setAutoHide(true);

    myToolWindow.restoreDefaultLayout();

    assertThat(myToolWindow.isMinimized()).isFalse();
    assertThat(myToolWindow.isLeft()).isTrue();
    assertThat(myToolWindow.isSplit()).isFalse();
    assertThat(myToolWindow.isAutoHide()).isFalse();
  }

  @Test
  public void testStoreAndRestoreDefaultLayout() {
    myToolWindow.setMinimized(true);
    myToolWindow.setLeft(false);
    myToolWindow.setSplit(true);
    myToolWindow.setAutoHide(true);

    myToolWindow.storeDefaultLayout();

    myToolWindow.setFloating(true);
    myToolWindow.setLeft(true);
    myToolWindow.setSplit(false);
    myToolWindow.setAutoHide(false);

    myToolWindow.restoreDefaultLayout();

    assertThat(myToolWindow.isMinimized()).isTrue();
    assertThat(myToolWindow.isLeft()).isFalse();
    assertThat(myToolWindow.isSplit()).isTrue();
    assertThat(myToolWindow.isAutoHide()).isTrue();
  }

  @Test
  public void testDraggedEvent() {
    AbstractButton button = myToolWindow.getMinimizedButton();
    button.setSize(20, 50);
    MouseEvent event = new MouseEvent(button, MouseEvent.MOUSE_DRAGGED, 1, InputEvent.BUTTON1_MASK, 20, 150, 1, false);
    fireMouseDragged(button, event);
    ArgumentCaptor<AttachedToolWindow.DragEvent> dragEventArgumentCaptor = ArgumentCaptor.forClass(AttachedToolWindow.DragEvent.class);
    verify(myDragListener).buttonDragged(eq(myToolWindow), dragEventArgumentCaptor.capture());
    AttachedToolWindow.DragEvent dragEvent = dragEventArgumentCaptor.getValue();
    assertThat(dragEvent.getDragPoint().x).isEqualTo(20);
    assertThat(dragEvent.getDragPoint().y).isEqualTo(150);
    assertThat(dragEvent.getMousePoint().x).isEqualTo(20);
    assertThat(dragEvent.getMousePoint().y).isEqualTo(150);
    assertThat(dragEvent.getDragImage()).isInstanceOf(JLabel.class);
    assertThat(((JLabel)dragEvent.getDragImage()).getIcon()).isNotNull();
  }

  @Test
  public void testDropEvent() {
    AbstractButton button = myToolWindow.getMinimizedButton();
    button.setSize(20, 50);
    MouseEvent event1 = new MouseEvent(button, MouseEvent.MOUSE_DRAGGED, 1, InputEvent.BUTTON1_MASK, 20, 150, 1, false);
    fireMouseDragged(button, event1);
    MouseEvent event2 = new MouseEvent(button, MouseEvent.MOUSE_RELEASED, 1, InputEvent.BUTTON1_MASK, 800, 450, 1, false);
    fireMouseReleased(button, event2);
    ArgumentCaptor<AttachedToolWindow.DragEvent> dragEventArgumentCaptor = ArgumentCaptor.forClass(AttachedToolWindow.DragEvent.class);
    verify(myDragListener).buttonDropped(eq(myToolWindow), dragEventArgumentCaptor.capture());
    AttachedToolWindow.DragEvent dragEvent = dragEventArgumentCaptor.getValue();
    assertThat(dragEvent.getDragPoint().x).isEqualTo(20);
    assertThat(dragEvent.getDragPoint().y).isEqualTo(150);
    assertThat(dragEvent.getMousePoint().x).isEqualTo(800);
    assertThat(dragEvent.getMousePoint().y).isEqualTo(450);
    assertThat(dragEvent.getDragImage()).isInstanceOf(JLabel.class);
    assertThat(((JLabel)dragEvent.getDragImage()).getIcon()).isNotNull();
  }

  @Test
  public void testButtonClickTogglesMinimizedState() {
    myToolWindow.setMinimized(false);
    AbstractButton button = myToolWindow.getMinimizedButton();

    MouseEvent event1 = new MouseEvent(button, MouseEvent.MOUSE_CLICKED, 1, InputEvent.BUTTON1_MASK, 20, 150, 1, false);
    fireMouseClicked(button, event1);
    assertThat(myToolWindow.isMinimized()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.MINIMIZED));

    MouseEvent event2 = new MouseEvent(button, MouseEvent.MOUSE_CLICKED, 1, InputEvent.BUTTON1_MASK, 20, 150, 1, false);
    fireMouseClicked(button, event2);
    assertThat(myToolWindow.isMinimized()).isFalse();
    verify(myModel, times(2)).update(eq(myToolWindow), eq(PropertyType.MINIMIZED));
  }

  @Test
  public void testAddedGearActionFromButtonRightClick() {
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assert panel != null;

    AnAction action = findActionWithName(getPopupMenuFromButtonRightClick(), "GearAction");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(panel.isGearActionPerformed()).isTrue();
  }

  @Test
  public void testSelectLeftFromButtonRightClick() {
    myToolWindow.setLeft(false);

    AnAction action = findActionWithName(getPopupMenuFromButtonRightClick(), "Left");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isLeft()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.LEFT));
  }

  @Test
  public void testSelectRightFromButtonRightClick() {
    myToolWindow.setLeft(true);

    AnAction action = findActionWithName(getPopupMenuFromButtonRightClick(), "Right");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isLeft()).isFalse();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.LEFT));
  }

  @Test
  public void testSelectSwapFromButtonRightClick() {
    myToolWindow.setLeft(true);

    AnAction action = findActionWithName(getPopupMenuFromButtonRightClick(), "Swap");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    verify(myModel).swap();
  }

  @Test
  public void testToggleDockModeFromButtonRightClick() {
    myToolWindow.setAutoHide(false);

    AnAction action = findActionWithName(getPopupMenuFromButtonRightClick(), "Docked");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isAutoHide()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.AUTO_HIDE));

    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isAutoHide()).isFalse();
    verify(myModel, times(2)).update(eq(myToolWindow), eq(PropertyType.AUTO_HIDE));
  }

  @Test
  public void testToggleFloatingModeFromButtonRightClick() {
    myToolWindow.setFloating(false);

    AnAction action = findActionWithName(getPopupMenuFromButtonRightClick(), "Floating");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isFloating()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.FLOATING));

    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isFloating()).isFalse();
    verify(myModel, times(2)).update(eq(myToolWindow), eq(PropertyType.FLOATING));
  }

  @Test
  public void testToggleSplitModeFromButtonRightClick() {
    myToolWindow.setSplit(false);

    AnAction action = findActionWithName(getPopupMenuFromButtonRightClick(), "Split");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isSplit()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.SPLIT));

    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isSplit()).isFalse();
    verify(myModel, times(2)).update(eq(myToolWindow), eq(PropertyType.SPLIT));
  }

  @Test
  public void testHideFromButtonInHeader() {
    myToolWindow.setFloating(false);

    ActionButton button = findButtonByName(myToolWindow.getComponent(), "Hide");
    assertThat(button).isNotNull();
    button.click();

    assertThat(myToolWindow.isMinimized()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.MINIMIZED));
  }

  @Test
  public void testAdditionalActionFromButtonInHeader() {
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assert panel != null;

    ActionButton button = findButtonByName(myToolWindow.getComponent(), "AdditionalAction");
    assertThat(button).isNotNull();
    button.click();

    assertThat(panel.isAdditionalActionPerformed()).isTrue();
  }

  @Test
  public void testSelectLeftFromGearButtonInHeader() {
    myToolWindow.setLeft(false);

    AnAction action = findActionWithName(getPopupMenuFromGearButtonInHeader(), "Left");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isLeft()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.LEFT));
  }

  @Test
  public void testSelectRightFromGearButtonInHeader() {
    myToolWindow.setLeft(true);

    AnAction action = findActionWithName(getPopupMenuFromGearButtonInHeader(), "Right");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isLeft()).isFalse();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.LEFT));
  }

  @Test
  public void testSelectSwapFromGearButtonInHeader() {
    myToolWindow.setLeft(true);

    AnAction action = findActionWithName(getPopupMenuFromGearButtonInHeader(), "Swap");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    verify(myModel).swap();
  }

  @Test
  public void testToggleDockModeFromGearButtonInHeader() {
    myToolWindow.setAutoHide(false);

    AnAction action = findActionWithName(getPopupMenuFromGearButtonInHeader(), "Docked");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isAutoHide()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.AUTO_HIDE));

    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isAutoHide()).isFalse();
    verify(myModel, times(2)).update(eq(myToolWindow), eq(PropertyType.AUTO_HIDE));
  }

  @Test
  public void testToggleFloatingModeFromGearButtonInHeader() {
    myToolWindow.setFloating(false);

    AnAction action = findActionWithName(getPopupMenuFromGearButtonInHeader(), "Floating");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isFloating()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.FLOATING));

    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isFloating()).isFalse();
    verify(myModel, times(2)).update(eq(myToolWindow), eq(PropertyType.FLOATING));
  }

  @Test
  public void testToggleSplitModeFromGearButtonInHeader() {
    myToolWindow.setSplit(false);

    AnAction action = findActionWithName(getPopupMenuFromGearButtonInHeader(), "Split");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isSplit()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.SPLIT));

    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isSplit()).isFalse();
    verify(myModel, times(2)).update(eq(myToolWindow), eq(PropertyType.SPLIT));
  }

  @Test
  public void testContentIsDisposed() {
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assert panel != null;
    Disposer.dispose(myToolWindow);
    myToolWindow = null;
    assertThat(panel.isDisposed()).isTrue();
  }

  private static void fireMouseDragged(@NotNull JComponent component, @NotNull MouseEvent event) {
    for (MouseMotionListener listener : component.getMouseMotionListeners()) {
      listener.mouseDragged(event);
    }
  }

  private static void fireMouseReleased(@NotNull JComponent component, @NotNull MouseEvent event) {
    for (MouseListener listener : component.getMouseListeners()) {
      listener.mouseReleased(event);
    }
  }

  private static void fireMouseClicked(@NotNull JComponent component, @NotNull MouseEvent event) {
    for (MouseListener listener : component.getMouseListeners()) {
      listener.mouseClicked(event);
    }
  }

  private DefaultActionGroup getPopupMenuFromButtonRightClick() {
    AbstractButton button = myToolWindow.getMinimizedButton();

    MouseEvent event1 = new MouseEvent(button, MouseEvent.MOUSE_CLICKED, 1, InputEvent.BUTTON3_MASK, 20, 150, 1, false);
    fireMouseClicked(button, event1);

    ArgumentCaptor<ActionGroup> menuCaptor = ArgumentCaptor.forClass(ActionGroup.class);
    verify(myActionManager).createActionPopupMenu(eq(ToolWindowContentUi.POPUP_PLACE), menuCaptor.capture());
    return (DefaultActionGroup)menuCaptor.getValue();
  }

  @Nullable
  private static AnAction findActionWithName(@NotNull DefaultActionGroup group, @NotNull String name) {
    for (AnAction action : group.getChildActionsOrStubs()) {
      if (name.equals(action.getTemplatePresentation().getText())) {
        return action;
      }
      if (action instanceof DefaultActionGroup) {
        AnAction childAction = findActionWithName((DefaultActionGroup)action, name);
        if (childAction != null) {
          return childAction;
        }
      }
    }
    return null;
  }

  private DefaultActionGroup getPopupMenuFromGearButtonInHeader() {
    ActionButton button = findButtonByName(myToolWindow.getComponent(), "Gear");
    assertThat(button).isNotNull();
    button.click();

    ArgumentCaptor<ActionGroup> menuCaptor = ArgumentCaptor.forClass(ActionGroup.class);
    verify(myActionManager).createActionPopupMenu(eq(ToolWindowContentUi.POPUP_PLACE), menuCaptor.capture());
    return (DefaultActionGroup)menuCaptor.getValue();
  }

  private static ActionButton findButtonByName(@NotNull Container container, @NotNull String name) {
    for (Component component : container.getComponents()) {
      if (component instanceof ActionButton) {
        ActionButton button = (ActionButton)component;
        AnAction action = button.getAction();
        if (action != null && name.equals(action.getTemplatePresentation().getText())) {
          return button;
        }
      }
      if (component instanceof Container) {
        ActionButton button = findButtonByName((Container)component, name);
        if (button != null) {
          return button;
        }
      }
    }
    return null;
  }

  private static AnActionEvent createActionEvent(@NotNull AnAction action) {
    DataContext dataContext = mock(DataContext.class);
    return new AnActionEvent(null, dataContext, TOOL_WINDOW_TOOLBAR_PLACE, action.getTemplatePresentation().clone(),
                             ActionManager.getInstance(), 0);
  }
}
