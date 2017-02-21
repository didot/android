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
package com.android.tools.idea.explorer.mocks;

import com.android.tools.idea.explorer.*;
import com.android.tools.idea.explorer.fs.DeviceFileSystem;
import com.android.tools.idea.explorer.fs.DeviceFileSystemRenderer;
import com.android.tools.idea.explorer.fs.DeviceFileSystemService;
import com.android.tools.idea.explorer.ui.DeviceExplorerViewImpl;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"NullableProblems", "unused"})
public class MockDeviceExplorerView implements DeviceExplorerView {
  @NotNull private final List<DeviceExplorerViewListener> myListeners = new ArrayList<>();
  @NotNull private final DeviceExplorerViewImpl myViewImpl;
  @NotNull private final FutureValuesTracker<Void> myServiceSetupSuccessTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DeviceFileSystem> myDeviceSelectedTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DeviceFileEntryNode> myTreeNodeExpandingTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DeviceFileEntryNode> myOpenNodeInEditorInvokedTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DeviceFileEntryNode> mySaveNodeAsTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DeviceFileEntryNode> myCopyNodePathTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DeviceFileEntryNode> myNewDirectoryTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DeviceFileEntryNode> myNewFileTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DeviceFileSystem> myDeviceAddedTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DeviceFileSystem> myDeviceRemovedTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DeviceFileSystem> myDeviceUpdatedTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<Void> myAllDevicesRemovedTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DefaultTreeModel> myTreeModelChangedTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<TreeModelEvent> myTreeNodesChangedTacker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<TreeModelEvent> myTreeNodesInsertedTacker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<TreeModelEvent> myTreeNodesRemovedTacker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<TreeModelEvent> myTreeStructureChangedTacker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<String> myReportErrorRelatedToServiceTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<String> myReportErrorRelatedToDeviceTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<String> myReportErrorRelatedToNodeTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<Void> myStartTreeBusyIndicatorTacker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<Void> myStopTreeBusyIndicatorTacker = new FutureValuesTracker<>();

  public MockDeviceExplorerView(@NotNull Project project,
                                @NotNull ToolWindow toolWindow,
                                @NotNull DeviceFileSystemRenderer deviceRenderer,
                                @NotNull DeviceExplorerModel model) {
    myViewImpl = new DeviceExplorerViewImpl(project, toolWindow, deviceRenderer, model);
    myViewImpl.addListener(new MyDeviceExplorerViewListener());
    model.addListener(new MyDeviceExplorerModelListener());
  }

  public JComboBox<DeviceFileSystem> getDeviceCombo() {
    return myViewImpl.getDeviceCombo();
  }

  public JTree getTree() {
    return myViewImpl.getFileTree();
  }

  public ActionGroup getFileTreeActionGroup() {
    return myViewImpl.getFileTreeActionGroup();
  }

  @Override
  public void addListener(@NotNull DeviceExplorerViewListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeListener(@NotNull DeviceExplorerViewListener listener) {
    myListeners.remove(listener);
  }

  @Override
  public void setup() {
    myViewImpl.setup();

    // Force a layout so that the panel, tree view, combo, etc. have a non-empty size
    assert myViewImpl.getLoadingPanel() != null;
    assert myViewImpl.getDeviceExplorerPanel() != null;

    myViewImpl.getLoadingPanel().setSize(new Dimension(100, 300));
    myViewImpl.getDeviceExplorerPanel().getComponent().setSize(new Dimension(100, 300));
    myViewImpl.getDeviceExplorerPanel().getDeviceCombo().setSize(new Dimension(100, 30));
    myViewImpl.getDeviceExplorerPanel().getColumnTreePane().setSize(new Dimension(100, 300));

    myViewImpl.getLoadingPanel().doLayout();
    myViewImpl.getDeviceExplorerPanel().getComponent().doLayout();
    myViewImpl.getDeviceExplorerPanel().getDeviceCombo().doLayout();
    myViewImpl.getDeviceExplorerPanel().getColumnTreePane().doLayout();
    myViewImpl.getDeviceExplorerPanel().getColumnTreePane().getViewport().doLayout();
    myViewImpl.getDeviceExplorerPanel().getColumnTreePane().getViewport().getView().doLayout();
  }

  @Override
  public void reportErrorRelatedToService(@NotNull DeviceFileSystemService service, @NotNull String message, @NotNull Throwable t) {
    myReportErrorRelatedToServiceTracker.produce(message + getThrowableMessage(t));
    myViewImpl.reportErrorRelatedToService(service, message, t);
  }

  @Override
  public void reportErrorRelatedToDevice(@NotNull DeviceFileSystem fileSystem, @NotNull String message, @NotNull Throwable t) {
    myReportErrorRelatedToDeviceTracker.produce(message + getThrowableMessage(t));
    myViewImpl.reportErrorRelatedToDevice(fileSystem, message, t);
  }

  @Override
  public void reportErrorRelatedToNode(@NotNull DeviceFileEntryNode node, @NotNull String message, @NotNull Throwable t) {
    myReportErrorRelatedToNodeTracker.produce(message + getThrowableMessage(t));
    myViewImpl.reportErrorRelatedToNode(node, message, t);
  }

  @Override
  public void reportMessageRelatedToNode(@NotNull DeviceFileEntryNode node, @NotNull String message) {
    myViewImpl.reportMessageRelatedToNode(node, message);
  }

  @NotNull
  private static String getThrowableMessage(@NotNull Throwable t) {
    return t.getMessage() == null ? "" : ": " + t.getMessage();
  }

  @Override
  public void serviceSetupSuccess() {
    myViewImpl.serviceSetupSuccess();
    myServiceSetupSuccessTracker.produce(null);
  }

  @Override
  public void startTreeBusyIndicator() {
    myStartTreeBusyIndicatorTacker.produce(null);
    myViewImpl.startTreeBusyIndicator();
  }

  @Override
  public void stopTreeBusyIndicator() {
    myStopTreeBusyIndicatorTacker.produce(null);
    myViewImpl.stopTreeBusyIndicator();
  }

  public FutureValuesTracker<DeviceFileSystem> getDeviceAddedTracker() {
    return myDeviceAddedTracker;
  }

  public FutureValuesTracker<DeviceFileSystem> getDeviceRemovedTracker() {
    return myDeviceRemovedTracker;
  }

  public FutureValuesTracker<DeviceFileSystem> getDeviceUpdatedTracker() {
    return myDeviceUpdatedTracker;
  }

  @NotNull
  public FutureValuesTracker<Void> getServiceSetupSuccessTracker() {
    return myServiceSetupSuccessTracker;
  }

  @NotNull
  public FutureValuesTracker<DeviceFileSystem> getDeviceSelectedTracker() {
    return myDeviceSelectedTracker;
  }

  @NotNull
  public FutureValuesTracker<DeviceFileEntryNode> getOpenNodeInEditorInvokedTracker() {
    return myOpenNodeInEditorInvokedTracker;
  }

  @NotNull
  public FutureValuesTracker<DeviceFileEntryNode> getTreeNodeExpandingTracker() {
    return myTreeNodeExpandingTracker;
  }

  @NotNull
  public FutureValuesTracker<TreeModelEvent> getTreeNodesChangedTacker() {
    return myTreeNodesChangedTacker;
  }

  @NotNull
  public FutureValuesTracker<TreeModelEvent> getTreeNodesInsertedTacker() {
    return myTreeNodesInsertedTacker;
  }

  @NotNull
  public FutureValuesTracker<TreeModelEvent> getTreeNodesRemovedTacker() {
    return myTreeNodesRemovedTacker;
  }

  @NotNull
  public FutureValuesTracker<TreeModelEvent> getTreeStructureChangedTacker() {
    return myTreeStructureChangedTacker;
  }

  @NotNull
  public FutureValuesTracker<DefaultTreeModel> getTreeModelChangedTracker() {
    return myTreeModelChangedTracker;
  }

  @NotNull
  public FutureValuesTracker<Void> getAllDevicesRemovedTracker() {
    return myAllDevicesRemovedTracker;
  }

  @NotNull
  public FutureValuesTracker<String> getReportErrorRelatedToServiceTracker() {
    return myReportErrorRelatedToServiceTracker;
  }

  @NotNull
  public FutureValuesTracker<String> getReportErrorRelatedToDeviceTracker() {
    return myReportErrorRelatedToDeviceTracker;
  }

  @NotNull
  public FutureValuesTracker<String> getReportErrorRelatedToNodeTracker() {
    return myReportErrorRelatedToNodeTracker;
  }

  @NotNull
  public FutureValuesTracker<Void> getStartTreeBusyIndicatorTacker() {
    return myStartTreeBusyIndicatorTacker;
  }

  @NotNull
  public FutureValuesTracker<Void> getStopTreeBusyIndicatorTacker() {
    return myStopTreeBusyIndicatorTacker;
  }

  @NotNull
  public FutureValuesTracker<DeviceFileEntryNode> getSaveNodeAsTracker() {
    return mySaveNodeAsTracker;
  }

  @NotNull
  public FutureValuesTracker<DeviceFileEntryNode> getCopyNodePathTracker() {
    return myCopyNodePathTracker;
  }

  private class MyDeviceExplorerViewListener implements DeviceExplorerViewListener {
    @Override
    public void deviceSelected(@Nullable DeviceFileSystem device) {
      myDeviceSelectedTracker.produce(device);
      myListeners.forEach(l -> l.deviceSelected(device));
    }

    @Override
    public void openNodeInEditorInvoked(@NotNull DeviceFileEntryNode treeNode) {
      myOpenNodeInEditorInvokedTracker.produce(treeNode);
      myListeners.forEach(l -> l.openNodeInEditorInvoked(treeNode));
    }

    @Override
    public void treeNodeExpanding(@NotNull DeviceFileEntryNode treeNode) {
      myTreeNodeExpandingTracker.produce(treeNode);
      myListeners.forEach(l -> l.treeNodeExpanding(treeNode));
    }

    @Override
    public void saveNodeAsInvoked(@NotNull DeviceFileEntryNode treeNode) {
      mySaveNodeAsTracker.produce(treeNode);
      myListeners.forEach(l -> l.saveNodeAsInvoked(treeNode));
    }

    @Override
    public void copyNodePathInvoked(@NotNull DeviceFileEntryNode treeNode) {
      myCopyNodePathTracker.produce(treeNode);
      myListeners.forEach(l -> l.copyNodePathInvoked(treeNode));
    }

    @Override
    public void newDirectoryInvoked(@NotNull DeviceFileEntryNode parentTreeNode) {
      myNewDirectoryTracker.produce(parentTreeNode);
      myListeners.forEach(l -> l.newDirectoryInvoked(parentTreeNode));
    }

    @Override
    public void newFileInvoked(@NotNull DeviceFileEntryNode parentTreeNode) {
      myNewFileTracker.produce(parentTreeNode);
      myListeners.forEach(l -> l.newFileInvoked(parentTreeNode));
    }
  }

  private class MyDeviceExplorerModelListener implements DeviceExplorerModelListener {
    @Override
    public void allDevicesRemoved() {
      myAllDevicesRemovedTracker.produce(null);
    }

    @Override
    public void deviceAdded(@NotNull DeviceFileSystem device) {
      myDeviceAddedTracker.produce(device);
    }

    @Override
    public void deviceRemoved(@NotNull DeviceFileSystem device) {
      myDeviceRemovedTracker.produce(device);
    }

    @Override
    public void deviceUpdated(@NotNull DeviceFileSystem device) {
      myDeviceUpdatedTracker.produce(device);
    }

    @Override
    public void activeDeviceChanged(@Nullable DeviceFileSystem newActiveDevice) {
    }

    @Override
    public void treeModelChanged(@Nullable DefaultTreeModel newTreeModel) {
      myTreeModelChangedTracker.produce(newTreeModel);
      if (newTreeModel != null) {
        newTreeModel.addTreeModelListener(new MyTreeModelListener());
      }
    }
  }

  private class MyTreeModelListener implements TreeModelListener {
    @Override
    public void treeNodesChanged(TreeModelEvent e) {
      myTreeNodesChangedTacker.produce(e);
    }

    @Override
    public void treeNodesInserted(TreeModelEvent e) {
      myTreeNodesInsertedTacker.produce(e);
    }

    @Override
    public void treeNodesRemoved(TreeModelEvent e) {
      myTreeNodesRemovedTacker.produce(e);
    }

    @Override
    public void treeStructureChanged(TreeModelEvent e) {
      myTreeStructureChangedTacker.produce(e);
    }
  }
}
