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
package com.android.tools.idea.apk.viewer.diff;

import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.apk.analyzer.internal.ApkDiffEntry;
import com.android.tools.apk.analyzer.internal.ApkEntry;
import com.android.tools.idea.apk.viewer.ApkViewPanel.FutureCallBackAdapter;
import com.android.tools.idea.apk.viewer.ApkViewPanel.NameRenderer;
import com.android.tools.idea.ddms.EdtExecutor;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Function;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import static com.android.tools.idea.apk.viewer.ApkViewPanel.getHumanizedSize;

public class ApkDiffPanel {
  private JPanel myContainer;
  private JScrollPane myColumnTreePane;

  private Tree myTree;
  private DefaultTreeModel myTreeModel;

  public ApkDiffPanel(ApkDiff apkDiffParser) {
    // construct the main tree
    ListenableFuture<DefaultMutableTreeNode> treeStructureFuture = apkDiffParser.constructTreeStructure();
    FutureCallBackAdapter<DefaultMutableTreeNode> setRootNode = new FutureCallBackAdapter<DefaultMutableTreeNode>() {
      @Override
      public void onSuccess(DefaultMutableTreeNode result) {
        setRootNode(result);
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        super.onFailure(t);
      }
    };
    Futures.addCallback(treeStructureFuture, setRootNode, EdtExecutor.INSTANCE);
  }

  private void createUIComponents() {
    myTreeModel = new DefaultTreeModel(new LoadingNode());
    myTree = new Tree(myTreeModel);
    myTree.setShowsRootHandles(true);
    myTree.setRootVisible(true); // show root node only when showing LoadingNode
    myTree.setPaintBusy(true);

    Convertor<TreePath, String> convertor = new Convertor<TreePath, String>() {
      @Override
      public String convert(TreePath path) {
        ApkEntry e = ApkEntry.fromNode(path.getLastPathComponent());
        if (e == null) {
          return null;
        }

        return e.getPath().toString();
      }
    };

    TreeSpeedSearch treeSpeedSearch = new TreeSpeedSearch(myTree, convertor, true);

    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree)
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("File")
                   .setPreferredWidth(600)
                   .setHeaderAlignment(SwingConstants.LEADING)
                   .setRenderer(new NameRenderer(treeSpeedSearch)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Old Size")
                   .setPreferredWidth(150)
                   .setHeaderAlignment(SwingConstants.TRAILING)
                   .setRenderer(new SizeRenderer(ApkDiffEntry::getOldSize)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("New Size")
                   .setPreferredWidth(150)
                   .setHeaderAlignment(SwingConstants.TRAILING)
                   .setRenderer(new SizeRenderer(ApkDiffEntry::getNewSize)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Diff Size")
                   .setPreferredWidth(150)
                   .setHeaderAlignment(SwingConstants.TRAILING)
                   .setRenderer(new SizeRenderer(ApkEntry::getSize)));
    myColumnTreePane = (JScrollPane)builder.build();
  }

  @NotNull
  public JComponent getContainer() {
    return myContainer;
  }

  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  private void setRootNode(@NotNull DefaultMutableTreeNode root) {
    myTreeModel = new DefaultTreeModel(root);

    ApkEntry entry = ApkEntry.fromNode(root);
    assert entry != null;

    myTree.setPaintBusy(false);
    myTree.setRootVisible(false);
    myTree.setModel(myTreeModel);
  }

  // Duplicated from ApkViewPanel.SizeRenderer until the diff entries are unified into the ArchiveEntry data class.
  public static class SizeRenderer extends ColoredTreeCellRenderer {
    private Function<ApkEntry, Long> mySizeMapper;

    public SizeRenderer(Function<ApkEntry, Long> sizeMapper) {
      mySizeMapper = sizeMapper;
      setTextAlign(SwingConstants.RIGHT);
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      ApkEntry entry = ApkEntry.fromNode(value);
      ApkEntry root = ApkEntry.fromNode(tree.getModel().getRoot());

      if (entry == null || root == null) {
        return;
      }

      append(getHumanizedSize(mySizeMapper.fun(entry)));
    }
  }
}
