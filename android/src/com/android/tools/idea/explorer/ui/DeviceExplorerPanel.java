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
package com.android.tools.idea.explorer.ui;

import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.idea.apk.viewer.ApkViewPanel;
import com.android.tools.idea.explorer.DeviceFileEntryNode;
import com.android.tools.idea.explorer.ErrorNode;
import com.android.tools.idea.explorer.MyLoadingNode;
import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import com.android.tools.idea.explorer.fs.DeviceFileSystem;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;

public class DeviceExplorerPanel {
  private static final int TEXT_RENDERER_HORIZ_PADDING = 6;
  private static final int TEXT_RENDERER_VERT_PADDING = 4;
  private JComboBox myDeviceCombo;
  @SuppressWarnings("unused") private JBScrollPane myColumnTreePane;
  private JPanel myComponent;
  private JPanel myToolbarPanel;

  private Tree myTree;

  public DeviceExplorerPanel() {
    createToolbar();
  }

  @NotNull
  public JPanel getComponent() {
    return myComponent;
  }

  @NotNull
  public JComboBox<DeviceFileSystem> getDeviceCombo() {
    //noinspection unchecked
    return myDeviceCombo;
  }

  @TestOnly
  public JBScrollPane getColumnTreePane() { return myColumnTreePane; }

  private void createToolbar() {
    final ActionManager actionManager = ActionManager.getInstance();
    ActionToolbar actionToolbar = actionManager.createActionToolbar("Device Explorer Toolbar",
                                                                    (DefaultActionGroup)actionManager
                                                                      .getAction("Android.DeviceExplorer.ActionsToolbar"),
                                                                    true);

    actionToolbar.setTargetComponent(myTree);
    myToolbarPanel.add(actionToolbar.getComponent(), BorderLayout.CENTER);
  }

  private void createUIComponents() {
    createTree();
  }

  private void createTree() {
    DefaultTreeModel treeModel = new DefaultTreeModel(new LoadingNode());
    myTree = new Tree(treeModel);
    myTree.setShowsRootHandles(true);
    myTree.setRootVisible(true);

    TreeSpeedSearch treeSpeedSearch = new TreeSpeedSearch(myTree, path -> {
      DeviceFileEntryNode node = DeviceFileEntryNode.fromNode(path.getLastPathComponent());
      if (node == null) {
        return null;
      }

      return node.getEntry().getName();
    }, true);

    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree)
      .setBackground(UIUtil.getTreeBackground())
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Name")
                   .setPreferredWidth(JBUI.scale(600))
                   .setHeaderAlignment(SwingConstants.LEADING)
                   .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
                   .setRenderer(new NameRenderer(treeSpeedSearch)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Permissions")
                   .setPreferredWidth(JBUI.scale(190))
                   .setHeaderAlignment(SwingConstants.LEADING)
                   .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
                   .setRenderer(new PermissionsRenderer()))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Date")
                   .setPreferredWidth(JBUI.scale(280))
                   .setHeaderAlignment(SwingConstants.LEADING)
                   .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
                   .setRenderer(new DateRenderer()))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Size")
                   .setPreferredWidth(JBUI.scale(130))
                   .setHeaderAlignment(SwingConstants.LEADING)
                   .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
                   .setRenderer(new SizeRenderer())
      );
    myColumnTreePane = (JBScrollPane)builder.build();
  }

  @NotNull
  public Tree getTree() {
    return myTree;
  }

  private static class NameRenderer extends ColoredTreeCellRenderer {
    @NotNull private final TreeSpeedSearch mySpeedSearch;

    public NameRenderer(@NotNull TreeSpeedSearch speedSearch) {
      mySpeedSearch = speedSearch;
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      @Nullable Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      setToolTipText(null);
      setIcon(null);
      setIpad(JBUI.insets(0, 0, 0, TEXT_RENDERER_HORIZ_PADDING));

      DeviceFileEntryNode node = DeviceFileEntryNode.fromNode(value);
      if (node != null) {
        setIcon(getIconFor(node));

        // Add name fragment (with speed search support)
        SimpleTextAttributes attr = SimpleTextAttributes.REGULAR_ATTRIBUTES;
        SearchUtil.appendFragments(mySpeedSearch.getEnteredPrefix(), node.getEntry().getName(), attr.getStyle(), attr.getFgColor(),
                                   attr.getBgColor(), this);
        if (node.isDownloading()) {
          // Download progress
          if (node.getTotalDownloadBytes() > 0) {
            append(String.format(" (%s / %s) ",
                                 ApkViewPanel.getHumanizedSize(node.getDownloadedBytes()),
                                 ApkViewPanel.getHumanizedSize(node.getTotalDownloadBytes())),
                   SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
          } else if (node.getDownloadedBytes() > 0) {
            append(String.format(" (%s) ",
                                 ApkViewPanel.getHumanizedSize(node.getDownloadedBytes())),
                   SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
          } else {
            appendProgress(node.getDownloadingTick());
          }
        }
        String linkTarget = node.getEntry().getSymbolicLinkTarget();
        if (!StringUtil.isEmpty(linkTarget)) {
          setToolTipText("Link target: " + linkTarget);
        }
      } else if (value instanceof ErrorNode) {
        ErrorNode errorNode = (ErrorNode)value;
        append(errorNode.getText(), SimpleTextAttributes.ERROR_ATTRIBUTES);
      } else if (value instanceof MyLoadingNode) {
        MyLoadingNode loadingNode = (MyLoadingNode)value;
        append("loading", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
        appendProgress(loadingNode.getDownloadingTick());
      }
    }

    private void appendProgress(int tick) {
      // "..." text, moving left to right and back according to tick
      int mod = 20;
      int progressTick = (tick % mod);
      if (progressTick >= (mod / 2)) {
        progressTick = mod - progressTick;
      }
      append(StringUtil.repeatSymbol(' ', progressTick));
      append(StringUtil.repeatSymbol('.', 3));
    }

    @NotNull
    private static Icon getIconFor(@NotNull DeviceFileEntryNode node) {
      Icon icon = getIconForImpl(node);
      if (node.getEntry().isSymbolicLink()) {
        return new LayeredIcon(icon, PlatformIcons.SYMLINK_ICON);
      }
      return icon;
    }

    @NotNull
    private static Icon getIconForImpl(@NotNull DeviceFileEntryNode node) {
      DeviceFileEntry entry = node.getEntry();
      if (entry.isDirectory()) {
        return AllIcons.Nodes.Folder;
      }
      else if (entry.isFile()) {
        LightVirtualFile file = new LightVirtualFile(entry.getName());
        Icon ftIcon = file.getFileType().getIcon();
        return ftIcon == null ? AllIcons.FileTypes.Any_type : ftIcon;
      }
      else {
        return AllIcons.FileTypes.Any_type;
      }
    }
  }

  private static class PermissionsRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      @Nullable Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      DeviceFileEntryNode node = DeviceFileEntryNode.fromNode(value);
      if (node != null) {
        append(node.getEntry().getPermissions().getText());
      }
      setIpad(JBUI.insets(0, TEXT_RENDERER_HORIZ_PADDING, 0, TEXT_RENDERER_HORIZ_PADDING));
    }
  }

  private static class DateRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      @Nullable Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      DeviceFileEntryNode node = DeviceFileEntryNode.fromNode(value);
      if (node != null) {
        DeviceFileEntry.DateTime date = node.getEntry().getLastModifiedDate();
        append(date.getText());
      }
      setIpad(JBUI.insets(0, TEXT_RENDERER_HORIZ_PADDING, 0, TEXT_RENDERER_HORIZ_PADDING));
    }
  }

  private static class SizeRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      @Nullable Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      DeviceFileEntryNode node = DeviceFileEntryNode.fromNode(value);
      if (node != null) {
        long size = node.getEntry().getSize();
        if (size >= 0) {
          setTextAlign(SwingConstants.RIGHT);
          append(ApkViewPanel.getHumanizedSize(size));
        }
      }
      setIpad(JBUI.insets(0, TEXT_RENDERER_HORIZ_PADDING, 0, TEXT_RENDERER_HORIZ_PADDING));
    }
  }
}
