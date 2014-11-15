/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElementNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.tree.TreeUtil;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;

public class ProjectViewFixture {
  @NotNull private final ProjectView myProjectView;

  ProjectViewFixture(@NotNull ProjectView projectView) {
    myProjectView = projectView;
  }

  @NotNull
  public PaneFixture selectProjectPane() {
    String id = "ProjectPane";
    myProjectView.changeView(id);
    return new PaneFixture(myProjectView.getProjectViewPaneById(id));
  }

  public static class PaneFixture {
    @NotNull private final AbstractProjectViewPane myPane;

    PaneFixture(@NotNull AbstractProjectViewPane pane) {
      myPane = pane;
    }

    @NotNull
    public PaneFixture expand() {
      GuiActionRunner.execute(new GuiTask() {
        @Override
        protected void executeInEDT() throws Throwable {
          TreeUtil.expandAll(myPane.getTree());
        }
      });
      return this;
    }

    @NotNull
    public NodeFixture findExternalLibrariesNode() {
      final AbstractTreeStructure treeStructure = myPane.getTreeBuilder().getTreeStructure();
      ExternalLibrariesNode node = GuiActionRunner.execute(new GuiQuery<ExternalLibrariesNode>() {
        @Nullable
        @Override
        protected ExternalLibrariesNode executeInEDT() throws Throwable {
          Object[] childElements = treeStructure.getChildElements(treeStructure.getRootElement());
          for (Object child : childElements) {
            if (child instanceof ExternalLibrariesNode) {
              return (ExternalLibrariesNode)child;
            }
          }
          return null;
        }
      });
      if (node != null) {
        return new NodeFixture(node, treeStructure);
      }
      throw new AssertionError("Unable to find 'External Libraries' node");
    }
  }

  public static class NodeFixture {
    @NotNull private final ProjectViewNode<?> myNode;
    @NotNull private final AbstractTreeStructure myTreeStructure;

    NodeFixture(@NotNull ProjectViewNode<?> node, @NotNull AbstractTreeStructure treeStructure) {
      myNode = node;
      myTreeStructure = treeStructure;
    }

    @NotNull
    public List<NodeFixture> getChildren() {
      final List<NodeFixture> children = Lists.newArrayList();
      GuiActionRunner.execute(new GuiTask() {
        @Override
        protected void executeInEDT() throws Throwable {
          for (Object child : myTreeStructure.getChildElements(myNode)) {
            if (child instanceof ProjectViewNode) {
              children.add(new NodeFixture((ProjectViewNode<?>)child, myTreeStructure));
            }
          }
        }
      });
      return children;
    }

    public boolean isJdk() {
      if (myNode instanceof NamedLibraryElementNode) {
        NamedLibraryElement value = ((NamedLibraryElementNode)myNode).getValue();
        LibraryOrSdkOrderEntry orderEntry = value.getOrderEntry();
        if (orderEntry instanceof JdkOrderEntry) {
          Sdk sdk = ((JdkOrderEntry)orderEntry).getJdk();
          return sdk.getSdkType() instanceof JavaSdk;
        }
      }
      return false;
    }

    @NotNull
    public NodeFixture requireDirectory(@NotNull String name) {
      assertThat(myNode).isInstanceOf(PsiDirectoryNode.class);
      VirtualFile file = myNode.getVirtualFile();
      assertNotNull(file);
      assertThat(file.getName()).isEqualTo(name);
      return this;
    }

    @Override
    public String toString() {
      return Strings.nullToEmpty(myNode.getName());
    }
  }
}
