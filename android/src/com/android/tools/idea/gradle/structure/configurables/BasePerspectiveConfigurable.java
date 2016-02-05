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
package com.android.tools.idea.gradle.structure.configurables;

import com.android.tools.idea.gradle.structure.model.PsdModuleModel;
import com.android.tools.idea.gradle.structure.model.PsdProjectModel;
import com.android.tools.idea.gradle.util.ui.Header;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.navigation.Place;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.ChildFocusWatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.FocusEvent;

import static javax.swing.SwingUtilities.isDescendingFrom;

public abstract class BasePerspectiveConfigurable extends MasterDetailsComponent
  implements SearchableConfigurable, Disposable, Place.Navigator {

  @NotNull private final PsdProjectModel myProjectModel;

  protected boolean myUiDisposed = true;

  private boolean myWasTreeInitialized;

  private ChildFocusWatcher myFocusWatcher;

  protected BasePerspectiveConfigurable(@NotNull PsdProjectModel projectModel) {
    myProjectModel = projectModel;
  }

  @Override
  protected void reInitWholePanelIfNeeded() {
    if (!myToReInitWholePanel) {
      return;
    }
    super.reInitWholePanelIfNeeded();

    Splitter splitter = getSplitter();
    JComponent first = splitter.getFirstComponent();
    if (first instanceof JPanel) {
      final JPanel panel = (JPanel)first;
      final Header header = new Header("Modules") {
        @Override
        public boolean isActive() {
          KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
          Component focusOwner = focusManager.getFocusOwner();
          return focusOwner != null && isDescendingFrom(focusOwner, panel);
        }
      };
      header.addEventListener(new Header.HeaderEventListener() {
        @Override
        public void activated() {
          myTree.requestFocusInWindow();
        }
      }, this);

      myFocusWatcher = new ChildFocusWatcher(panel) {
        @Override
        protected void onFocusGained(FocusEvent event) {
          header.repaint();
        }

        @Override
        protected void onFocusLost(FocusEvent event) {
          header.repaint();
        }
      };

      panel.add(header, BorderLayout.NORTH);
    }
  }

  @Override
  public void reset() {
    myUiDisposed = false;

    if (!myWasTreeInitialized) {
      initTree();
      myTree.setShowsRootHandles(false);
      loadTree();
    }
    else {
      super.disposeUIResources();
      myTree.setShowsRootHandles(false);
      loadTree();
    }

    super.reset();
  }

  @Override
  protected void initTree() {
    if (myWasTreeInitialized) {
      return;
    }
    myWasTreeInitialized = true;
    super.initTree();
    myTree.setRootVisible(false);

    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      @Override
      public String convert(final TreePath treePath) {
        return ((MyNode)treePath.getLastPathComponent()).getDisplayName();
      }
    }, true);
    ToolTipManager.sharedInstance().registerComponent(myTree);
    // myTree.setCellRenderer(new ProjectStructureElementRenderer(myContext));
  }

  protected void loadTree() {
    createModuleNodes();
    ((DefaultTreeModel)myTree.getModel()).reload();
    myUiDisposed = false;
  }

  private void createModuleNodes() {
    for (PsdModuleModel moduleModel : myProjectModel.getModuleModels()) {
      NamedConfigurable<? extends PsdModuleModel> configurable = getConfigurable(moduleModel);
      if (configurable != null) {
        MyNode moduleNode = new MyNode(configurable);
        myRoot.add(moduleNode);
      }
    }
  }

  @Nullable
  protected abstract NamedConfigurable<? extends PsdModuleModel> getConfigurable(@NotNull PsdModuleModel moduleModel);

  @NotNull
  protected PsdProjectModel getProjectModel() {
    return myProjectModel;
  }

  @Override
  public void disposeUIResources() {
    if (myUiDisposed) {
      return;
    }
    super.disposeUIResources();
    myUiDisposed = true;
    myAutoScrollHandler.cancelAllRequests();
    Disposer.dispose(this);
  }

  @Override
  public void dispose() {
    if (myFocusWatcher != null) {
      Disposer.dispose(myFocusWatcher);
    }
  }

  @Override
  protected void processRemovedItems() {
  }

  @Override
  protected boolean wasObjectStored(Object editableObject) {
    return false;
  }

  @Override
  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}
