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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.renderers.TreeRenderer;
import com.android.tools.idea.editors.gfxtrace.service.path.PathListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.SimpleTree;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;

public abstract class TreeController implements PathListener {
  public static final int TREE_ROW_HEIGHT = 19;

  @NotNull protected final GfxTraceEditor myEditor;
  @NotNull protected final JBLoadingPanel myLoadingPanel;
  @NotNull protected final SimpleTree myTree;

  public TreeController(@NotNull GfxTraceEditor editor, @NotNull JBScrollPane scrollPane, @NotNull String emptyText) {
    myEditor = editor;
    myEditor.addPathListener(this);
    myTree = new SimpleTree();
    myTree.setRowHeight(TREE_ROW_HEIGHT);
    myTree.setRootVisible(false);
    myTree.setLineStyleAngled();
    myTree.setCellRenderer(new TreeRenderer());
    myTree.getEmptyText().setText(emptyText);
    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), editor.getProject());
    myLoadingPanel.add(myTree);
    scrollPane.setViewportView(myLoadingPanel);
    scrollPane.getHorizontalScrollBar().setUnitIncrement(TREE_ROW_HEIGHT);
    scrollPane.getVerticalScrollBar().setUnitIncrement(TREE_ROW_HEIGHT);
  }

  public void clear() {
    myTree.setModel(null);
  }

  public void setRoot(DefaultMutableTreeNode root) {
    assert(ApplicationManager.getApplication().isDispatchThread());
    myTree.setModel(new DefaultTreeModel(root));
    myLoadingPanel.stopLoading();
    myLoadingPanel.revalidate();
  }


}
