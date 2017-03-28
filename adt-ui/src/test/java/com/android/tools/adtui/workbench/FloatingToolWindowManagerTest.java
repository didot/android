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

import com.android.tools.adtui.workbench.FloatingToolWindowManager.FloatingToolWindowFactory;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import javax.swing.*;
import java.awt.*;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(JUnit4.class)
public class FloatingToolWindowManagerTest {
  // Hack to avoid: "java.lang.Error: Cannot load com.apple.laf.AquaLookAndFeel"
  @SuppressWarnings("unused")
  private static volatile boolean DARK = UIUtil.isUnderDarcula();

  @Rule
  public FrameworkRule myFrameworkRule = new FrameworkRule();
  @Mock
  private FileEditor myFileEditor1;
  @Mock
  private FileEditor myFileEditor2;
  @Mock
  private WorkBench myWorkBench1;
  @Mock
  private WorkBench myWorkBench2;
  @Mock
  private AttachedToolWindow myAttachedToolWindow1;
  @Mock
  private AttachedToolWindow myAttachedToolWindow2;
  @Mock
  private MessageBus myMessageBus;
  @Mock
  private MessageBusConnection myConnection;
  @Mock
  private VirtualFile myVirtualFile;
  @Mock
  private FloatingToolWindow myFloatingToolWindow1;
  @Mock
  private FloatingToolWindow myFloatingToolWindow2;
  @Mock
  private ContentManager myContentManager1;
  @Mock
  private ContentManager myContentManager2;
  @Mock
  private FloatingToolWindowFactory myFloatingToolWindowFactory;
  @Mock
  private KeyboardFocusManager myKeyboardFocusManager;

  private FileEditorManager myEditorManager;
  private FileEditorManagerListener myListener;
  private FloatingToolWindowManager myManager;

  @Before
  public void before() {
    initMocks(this);
    KeyboardFocusManager.setCurrentKeyboardFocusManager(myKeyboardFocusManager);
    Project project = ProjectManager.getInstance().getDefaultProject();
    myEditorManager = FileEditorManager.getInstance(project);
    when(myWorkBench1.getFloatingToolWindows()).thenReturn(ImmutableList.of(myAttachedToolWindow1));
    when(myWorkBench2.getFloatingToolWindows()).thenReturn(ImmutableList.of(myAttachedToolWindow2));
    when(myAttachedToolWindow1.getDefinition()).thenReturn(PalettePanelToolContent.getDefinition());
    when(myAttachedToolWindow2.getDefinition()).thenReturn(PalettePanelToolContent.getOtherDefinition());
    when(myFloatingToolWindowFactory.create(any(Project.class), any(ToolWindowDefinition.class)))
      .thenReturn(myFloatingToolWindow1, myFloatingToolWindow2, null);

    myManager = new FloatingToolWindowManager(
      ApplicationManager.getApplication(),
      project,
      StartupManager.getInstance(project),
      FileEditorManager.getInstance(project));
    myManager.initComponent();
    myManager.setFloatingToolWindowFactory(myFloatingToolWindowFactory);
    assert myManager.getComponentName().equals("FloatingToolWindowManager");

    when(myEditorManager.getSelectedEditors()).thenReturn(new FileEditor[0]);
    when(project.getMessageBus()).thenReturn(myMessageBus);
    when(myMessageBus.connect(eq(project))).thenReturn(myConnection).thenReturn(null);
    myManager.projectOpened();
    ArgumentCaptor<FileEditorManagerListener> captor = ArgumentCaptor.forClass(FileEditorManagerListener.class);
    //noinspection unchecked
    verify(myConnection).subscribe(any(Topic.class), captor.capture());
    when(myFileEditor1.getComponent()).thenReturn(new JPanel());
    when(myFileEditor2.getComponent()).thenReturn(new JPanel());
    myListener = captor.getValue();
    myManager.register(myFileEditor1, myWorkBench1);
    myManager.register(myFileEditor2, myWorkBench2);
  }

  @After
  public void after() {
    KeyboardFocusManager.setCurrentKeyboardFocusManager(null);
    myManager.projectClosed();
    myManager.disposeComponent();
  }

  @Test
  @Ignore
  public void testProjectClosed() {
    myManager.projectClosed();
    verify(myConnection).disconnect();
  }

  @Test
  @Ignore
  @SuppressWarnings("unchecked")
  public void testRestoreDefaultLayout() {
    when(myKeyboardFocusManager.getFocusOwner()).thenReturn(myWorkBench1);

    myManager.restoreDefaultLayout();
    verify(myFloatingToolWindow1).show(eq(myAttachedToolWindow1));
  }

  @Test
  @Ignore
  public void testFileOpened() {
    myListener.fileOpened(myEditorManager, myVirtualFile);
  }

  @Test
  @Ignore
  public void testFileOpenedCausingFloatingToolWindowToDisplay() {
    when(myEditorManager.getSelectedEditors()).thenReturn(new FileEditor[]{myFileEditor1});
    myListener.fileOpened(myEditorManager, myVirtualFile);
    //noinspection unchecked
    verify(myFloatingToolWindow1).show(eq(myAttachedToolWindow1));
  }

  @Test
  @Ignore
  public void testFileOpenedCausingFloatingToolWindowToDisplay2() {
    when(myEditorManager.getSelectedEditors()).thenReturn(new FileEditor[]{myFileEditor1, myFileEditor2});
    myListener.fileOpened(myEditorManager, myVirtualFile);
    //noinspection unchecked
    verify(myFloatingToolWindow1).show(eq(myAttachedToolWindow1));
  }

  @Test
  @SuppressWarnings("unchecked")
  @Ignore
  public void testSwitchingBetweenTwoEditorsWithDifferentFloatingToolWindows() {
    when(myKeyboardFocusManager.getFocusOwner()).thenReturn(myWorkBench1, myWorkBench2);
    myListener.fileOpened(myEditorManager, myVirtualFile);
    verify(myFloatingToolWindow1).show(eq(myAttachedToolWindow1));
    myListener.fileOpened(myEditorManager, myVirtualFile);
    verify(myFloatingToolWindow1).hide();
    verify(myFloatingToolWindow2).show(eq(myAttachedToolWindow2));

    FileEditorManagerEvent event1 = new FileEditorManagerEvent(myEditorManager, null, null, null, myFileEditor1);
    FileEditorManagerEvent event2 = new FileEditorManagerEvent(myEditorManager, null, null, null, myFileEditor2);

    myListener.selectionChanged(event1);
    verify(myFloatingToolWindow2).hide();
    verify(myFloatingToolWindow1, times(2)).show(eq(myAttachedToolWindow1));
    myListener.selectionChanged(event2);
    verify(myFloatingToolWindow1, times(2)).hide();
    verify(myFloatingToolWindow2, times(2)).show(eq(myAttachedToolWindow2));

    // Now unregister one of them:
    myManager.unregister(myFileEditor1);
    myListener.selectionChanged(event1);
    verify(myFloatingToolWindow1, times(3)).hide();
    verify(myFloatingToolWindow2, times(2)).hide();
  }

  @Test
  @SuppressWarnings("unchecked")
  @Ignore
  public void testFileCloseCausingFloatingToolWindowToHide() {
    when(myKeyboardFocusManager.getFocusOwner()).thenReturn(myWorkBench1, new JLabel());
    myListener.fileOpened(myEditorManager, myVirtualFile);
    verify(myFloatingToolWindow1).show(eq(myAttachedToolWindow1));

    myListener.fileClosed(myEditorManager, myVirtualFile);
    verify(myFloatingToolWindow1).hide();
  }
}
