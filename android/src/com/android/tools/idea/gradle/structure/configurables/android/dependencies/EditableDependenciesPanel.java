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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies;

import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidModuleModel;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.table.TableView;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.PlatformIcons.LIBRARY_ICON;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION;

/**
 * Panel that displays the table of "editable" dependencies.
 */
class EditableDependenciesPanel extends JPanel implements Disposable {
  @NotNull private final PsdAndroidModuleModel myModuleModel;
  @NotNull private final EditableDependenciesTableModel myDependenciesTableModel;
  @NotNull private final TableView<PsdAndroidDependencyModel> myDependenciesTable;
  @NotNull private final ListSelectionListener myTableSelectionListener;

  @NotNull private final List<SelectionListener> mySelectionListeners = Lists.newCopyOnWriteArrayList();

  private List<AbstractPopupAction> myPopupActions;

  EditableDependenciesPanel(@NotNull PsdAndroidModuleModel moduleModel) {
    super(new BorderLayout());
    myModuleModel = moduleModel;

    List<PsdAndroidDependencyModel> dependencies = myModuleModel.getDeclaredDependencies();
    myDependenciesTableModel = new EditableDependenciesTableModel(dependencies);
    myDependenciesTable = new TableView<PsdAndroidDependencyModel>(myDependenciesTableModel);

    myTableSelectionListener = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        PsdAndroidDependencyModel selected = getSingleSelection();
        if (selected != null) {
          for (SelectionListener listener : mySelectionListeners) {
            listener.dependencyModelSelected(selected);
          }
        }
      }
    };

    ListSelectionModel tableSelectionModel = myDependenciesTable.getSelectionModel();
    tableSelectionModel.addListSelectionListener(myTableSelectionListener);
    tableSelectionModel.setSelectionMode(MULTIPLE_INTERVAL_SELECTION);
    if (!myDependenciesTable.getItems().isEmpty()) {
      myDependenciesTable.changeSelection(0, 0, false, false);
    }

    myDependenciesTable.setDragEnabled(false);
    myDependenciesTable.setIntercellSpacing(new Dimension(0, 0));
    myDependenciesTable.setShowGrid(false);

    JScrollPane scrollPane = createScrollPane(myDependenciesTable);
    scrollPane.setBorder(IdeBorderFactory.createEmptyBorder());
    add(scrollPane, BorderLayout.CENTER);

    add(createActionsPanel(), BorderLayout.NORTH);

    updateTableColumnSizes();
  }

  @NotNull
  private JPanel createActionsPanel() {
    final JPanel actionsPanel = new JPanel(new BorderLayout());

    DefaultActionGroup actions = new DefaultActionGroup();

    AnAction addDependencyAction = new DumbAwareAction("Add Dependency", "", IconUtil.getAddIcon()) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        initPopupActions();
        JBPopup popup = JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<AbstractPopupAction>(null, myPopupActions) {
          @Override
          public Icon getIconFor(AbstractPopupAction action) {
            return action.icon;
          }

          @Override
          public boolean isMnemonicsNavigationEnabled() {
            return true;
          }

          @Override
          public PopupStep onChosen(final AbstractPopupAction action, boolean finalChoice) {
            return doFinalStep(new Runnable() {
              @Override
              public void run() {
                action.execute();
              }
            });
          }

          @Override
          @NotNull
          public String getTextFor(AbstractPopupAction action) {
            return "&" + action.index + "  " + action.text;
          }
        });
        popup.show(new RelativePoint(actionsPanel, new Point(0, actionsPanel.getHeight() - 1)));
      }
    };
    actions.add(addDependencyAction);

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("TOP", actions, true);
    JComponent toolbarComponent = toolbar.getComponent();
    toolbarComponent.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    actionsPanel.add(toolbarComponent, BorderLayout.CENTER);

    return actionsPanel;
  }

  private void initPopupActions() {
    if (myPopupActions == null) {
      List<AbstractPopupAction> actions = Lists.newArrayList();
      actions.add(new AddDependencyAction());
      myPopupActions = actions;
    }
  }

  void updateTableColumnSizes() {
    myDependenciesTable.updateColumnSizes();
  }

  @Override
  public void dispose() {
    mySelectionListeners.clear();
  }

  void add(@NotNull SelectionListener listener) {
    PsdAndroidDependencyModel selected = getSingleSelection();
    if (selected != null) {
      listener.dependencyModelSelected(selected);
    }
    mySelectionListeners.add(listener);
  }

  @Nullable
  private PsdAndroidDependencyModel getSingleSelection() {
    Collection<PsdAndroidDependencyModel> selection = myDependenciesTable.getSelection();
    if (selection.size() == 1) {
      PsdAndroidDependencyModel selected = getFirstItem(selection);
      assert selected != null;
      return selected;
    }
    return null;
  }

  void select(@NotNull PsdAndroidDependencyModel model) {
    ListSelectionModel tableSelectionModel = myDependenciesTable.getSelectionModel();
    // Remove ListSelectionListener. We only want the selection event when the user selects a table cell directly. If we got here is
    // because the user selected a dependency in the "Variants" tree view, and we are simply syncing the table.
    tableSelectionModel.removeListSelectionListener(myTableSelectionListener);

    myDependenciesTable.setSelection(Collections.singleton(model));

    // Add ListSelectionListener again, to react when user selects a table cell directly.
    tableSelectionModel.addListSelectionListener(myTableSelectionListener);
  }

  public interface SelectionListener {
    void dependencyModelSelected(@NotNull PsdAndroidDependencyModel model);
  }

  private class AddDependencyAction extends AbstractPopupAction {
    AddDependencyAction() {
      super("Artifact Dependency", LIBRARY_ICON, 1);
    }

    @Override
    void execute() {
      AddArtifactDependencyDialog dialog = new AddArtifactDependencyDialog(myModuleModel);
      dialog.showAndGet();
    }
  }

  private static abstract class AbstractPopupAction implements ActionListener {
    @NotNull final String text;
    @NotNull final Icon icon;

    final int index;

    AbstractPopupAction(@NotNull String text, @NotNull Icon icon, int index) {
      this.text = text;
      this.icon = icon;
      this.index = index;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      execute();
    }

    abstract void execute();
  }
}
