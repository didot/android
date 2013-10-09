/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.structure;

import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.ChooseElementsDialog;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.CellAppearanceEx;
import com.intellij.openapi.roots.ui.util.SimpleTextCellAppearance;
import com.intellij.openapi.ui.ComboBoxTableRenderer;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ActionRunner;
import com.intellij.util.PlatformIcons;
import icons.MavenIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.List;

/**
 * A GUI object that displays and modifies dependencies for an Android-Gradle module.
 */
public class ModuleDependenciesPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance(ModuleDependenciesPanel.class);
  private static final int SCOPE_COLUMN_WIDTH = 120;
  private final JBTable myEntryTable;
  private final ModuleDependenciesTableModel myModel;
  private final String myModulePath;
  private final Project myProject;
  private final GradleBuildFile myGradleBuildFile;
  private final GradleSettingsFile myGradleSettingsFile;
  private AnActionButton myRemoveButton;

  public ModuleDependenciesPanel(Project project, String modulePath) {
    super(new BorderLayout());

    myModulePath = modulePath;
    myProject = project;
    myModel = new ModuleDependenciesTableModel();
    myGradleSettingsFile = GradleSettingsFile.get(myProject);

    GradleBuildFile buildFile = null;
    if (myGradleSettingsFile != null) {
      buildFile = myGradleSettingsFile.getModuleBuildFile(myModulePath);
      if (buildFile != null && buildFile.canParseValue(BuildFileKey.DEPENDENCIES)) {
        List<Dependency> dependencies = (List<Dependency>)buildFile.getValue(BuildFileKey.DEPENDENCIES);
        if (dependencies != null) {
          for (Dependency dependency : dependencies) {
            myModel.addItem(new ModuleDependenciesTableItem(dependency));
          }
        }
      } else {
        LOG.warn("Unable to find Gradle build file for module " + myModulePath);
      }
    }
    myGradleBuildFile = buildFile;
    myModel.resetModified();

    myEntryTable = new JBTable(myModel);
    myEntryTable.setShowGrid(false);
    myEntryTable.setDragEnabled(false);
    myEntryTable.setIntercellSpacing(new Dimension(0, 0));

    myEntryTable.setDefaultRenderer(ModuleDependenciesTableItem.class, new TableItemRenderer());

    JComboBox scopeEditor = new JComboBox(new EnumComboBoxModel<Dependency.Scope>(Dependency.Scope.class));
    myEntryTable.setDefaultEditor(Dependency.Scope.class, new DefaultCellEditor(scopeEditor));
    myEntryTable.setDefaultRenderer(Dependency.Scope.class, new ComboBoxTableRenderer<Dependency.Scope>(Dependency.Scope.values()) {
        @Override
        protected String getTextFor(@NotNull final Dependency.Scope value) {
          return value.getDisplayName();
        }
      });

    myEntryTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    new SpeedSearchBase<JBTable>(myEntryTable) {
      @Override
      public int getSelectedIndex() {
        return myEntryTable.getSelectedRow();
      }

      @Override
      protected int convertIndexToModel(int viewIndex) {
        return myEntryTable.convertRowIndexToModel(viewIndex);
      }

      @Override
      @NotNull
      public Object[] getAllElements() {
        return myModel.getItems().toArray();
      }

      @Override
      @NotNull
      public String getElementText(Object element) {
        return getCellAppearance((ModuleDependenciesTableItem)element).getText();
      }

      @Override
      public void selectElement(@NotNull Object element, @NotNull String selectedText) {
        final int count = myModel.getRowCount();
        for (int row = 0; row < count; row++) {
          if (element.equals(myModel.getItemAt(row))) {
            final int viewRow = myEntryTable.convertRowIndexToView(row);
            myEntryTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
            TableUtil.scrollSelectionToVisible(myEntryTable);
            break;
          }
        }
      }
    };

    TableColumn column = myEntryTable.getTableHeader().getColumnModel().getColumn(ModuleDependenciesTableModel.SCOPE_COLUMN);
    column.setResizable(false);
    column.setMaxWidth(SCOPE_COLUMN_WIDTH);
    column.setMinWidth(SCOPE_COLUMN_WIDTH);

    add(createTableWithButtons(), BorderLayout.CENTER);

    if (myEntryTable.getRowCount() > 0) {
      myEntryTable.getSelectionModel().setSelectionInterval(0, 0);
    }

    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(myRemoveButton);
    PopupHandler.installPopupHandler(myEntryTable, actionGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());
  }

  @NotNull
  private JComponent createTableWithButtons() {
    myEntryTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        updateButtons();
      }
    });

    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myEntryTable);
    decorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        ImmutableList<PopupAction> popupActions = ImmutableList.of(
          new PopupAction(MavenIcons.MavenLogo, 1, "Maven dependency") {
            @Override
            public void run() {
              addExternalDependency();
            }
          }, new PopupAction(PlatformIcons.LIBRARY_ICON, 2, "File dependency") {
            @Override
            public void run() {
              addFileDependency();
            }
          }, new PopupAction(AllIcons.Nodes.Module, 3, "Module dependency") {
            @Override
            public void run() {
              addModuleDependency();
            }
          }
        );
        final JBPopup popup = JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<PopupAction>(null, popupActions) {
          @Override
          public Icon getIconFor(PopupAction value) {
            return value.myIcon;
          }

          @Override
          public boolean hasSubstep(PopupAction value) {
            return false;
          }

          @Override
          public boolean isMnemonicsNavigationEnabled() {
            return true;
          }

          @Override
          public PopupStep onChosen(final PopupAction value, final boolean finalChoice) {
            return doFinalStep(new Runnable() {
              @Override
              public void run() {
                value.run();
              }
            });
          }

          @Override
          @NotNull
          public String getTextFor(PopupAction value) {
            return "&" + value.myIndex + "  " + value.myTitle;
          }
        });
        popup.show(button.getPreferredPopupPoint());
      }
    });
    decorator.setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          removeSelectedItems(TableUtil.removeSelectedItems(myEntryTable));
        }
      });
    decorator.setMoveUpAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        moveSelectedRows(-1);
      }
    });
    decorator.setMoveDownAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          moveSelectedRows(+1);
        }
      });

    final JPanel panel = decorator.createPanel();
    myRemoveButton = ToolbarDecorator.findRemoveButton(panel);
    return panel;
  }

  private void addExternalDependency() {
    MavenDependencyLookupDialog dialog = new MavenDependencyLookupDialog(myProject, null);
    dialog.setTitle("Choose Maven Dependency");
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      String coordinateText = dialog.getCoordinateText();
      myModel.addItem(new ModuleDependenciesTableItem(
          new Dependency(Dependency.Scope.COMPILE, Dependency.Type.EXTERNAL, coordinateText)));
    }
  }

  private void addFileDependency() {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, false, true, true, false, false);
    VirtualFile moduleFile = myGradleBuildFile.getFile();
    VirtualFile parent = moduleFile.getParent();
    descriptor.setRoots(parent);
    VirtualFile virtualFile = FileChooser.chooseFile(descriptor, myProject, null);
    if (virtualFile != null) {
      String path = virtualFile.getPath();
      String parentPath = parent.getPath();
      if (path.startsWith(parentPath)) {
        path = path.substring(parentPath.length());
      }
      myModel.addItem(new ModuleDependenciesTableItem(new Dependency(Dependency.Scope.COMPILE, Dependency.Type.FILES, path)));
    }
  }

  private void addModuleDependency() {
    List<String> modules = Lists.newArrayList();
    for (String s : myGradleSettingsFile.getModules()) {
      modules.add(s);
    }
    List<Dependency> dependencies = (List<Dependency>)myGradleBuildFile.getValue(BuildFileKey.DEPENDENCIES);
    if (dependencies != null) {
      for (Dependency dependency : dependencies) {
        modules.remove(dependency.data);
      }
    }
    modules.remove(myModulePath);
    final Component parent = this;
    final List<String> items = modules;
    final String title = ProjectBundle.message("classpath.chooser.title.add.module.dependency");
    final String description = ProjectBundle.message("classpath.chooser.description.add.module.dependency");
    ChooseElementsDialog<String> dialog = new ChooseElementsDialog<String>(parent, items, title, description, true) {
      @Override
      protected Icon getItemIcon(final String item) {
        return AllIcons.Nodes.Module;
      }

      @Override
      protected String getItemText(final String item) {
        return item;
      }
    };
    dialog.show();
    for (String module : dialog.getChosenElements()) {
      myModel.addItem(new ModuleDependenciesTableItem(
          new Dependency(Dependency.Scope.COMPILE, Dependency.Type.MODULE, module)));
    }
  }

  @Override
  public void addNotify() {
    super.addNotify();
    updateButtons();
  }

  private void updateButtons() {
    final int[] selectedRows = myEntryTable.getSelectedRows();
    boolean removeButtonEnabled = true;
    int minRow = myEntryTable.getRowCount() + 1;
    int maxRow = -1;
    for (final int selectedRow : selectedRows) {
      minRow = Math.min(minRow, selectedRow);
      maxRow = Math.max(maxRow, selectedRow);
      final ModuleDependenciesTableItem item = myModel.getItemAt(selectedRow);
      if (!item.isRemovable()) {
        removeButtonEnabled = false;
      }
    }
    if (myRemoveButton != null) {
      myRemoveButton.setEnabled(removeButtonEnabled && selectedRows.length > 0);
    }
  }

  private void removeSelectedItems(@NotNull final List removedRows) {
    if (removedRows.isEmpty()) {
      return;
    }
    final int[] selectedRows = myEntryTable.getSelectedRows();
    myModel.fireTableDataChanged();
    myModel.setModified();
    TableUtil.selectRows(myEntryTable, selectedRows);
  }

  private void moveSelectedRows(int increment) {
    if (increment == 0) {
      return;
    }
    if (myEntryTable.isEditing()){
      myEntryTable.getCellEditor().stopCellEditing();
    }
    final ListSelectionModel selectionModel = myEntryTable.getSelectionModel();
    for (int row = increment < 0 ? 0 : myModel.getRowCount() - 1; increment < 0? row < myModel.getRowCount() : row >= 0; row +=
      increment < 0? +1 : -1) {
      if (selectionModel.isSelectedIndex(row)) {
        final int newRow = moveRow(row, increment);
        selectionModel.removeSelectionInterval(row, row);
        selectionModel.addSelectionInterval(newRow, newRow);
      }
    }
    myModel.fireTableRowsUpdated(0, myModel.getRowCount() - 1);
    Rectangle cellRect = myEntryTable.getCellRect(selectionModel.getMinSelectionIndex(), 0, true);
    myEntryTable.scrollRectToVisible(cellRect);
    myEntryTable.repaint();
  }

  private int moveRow(final int row, final int increment) {
    int newIndex = Math.abs(row + increment) % myModel.getRowCount();
    final ModuleDependenciesTableItem item = myModel.removeDataRow(row);
    myModel.addItemAt(item, newIndex);
    return newIndex;
  }

  @NotNull
  private static CellAppearanceEx getCellAppearance(@NotNull final ModuleDependenciesTableItem item) {
    Dependency entry = item.getEntry();
    String data = "";
    Icon icon = null;
    if (entry != null) {
      data = entry.data;
      switch(item.getEntry().type) {
        case EXTERNAL:
          icon = MavenIcons.MavenLogo;
          break;
        case FILES:
          icon = PlatformIcons.LIBRARY_ICON;
          break;
        case MODULE:
          icon = AllIcons.Nodes.Module;
          break;
      }
    }
    return SimpleTextCellAppearance.regular(data, icon);
  }

  public void apply() {
    List<ModuleDependenciesTableItem> items = myModel.getItems();
    final List<Dependency> dependencies = Lists.newArrayListWithExpectedSize(items.size());
    for (ModuleDependenciesTableItem item : items) {
      dependencies.add(item.getEntry());
    }
    try {
      ActionRunner.runInsideWriteAction(new ActionRunner.InterruptibleRunnable() {
        @Override
        public void run() throws Exception {
          myGradleBuildFile.setValue(BuildFileKey.DEPENDENCIES, dependencies);
        }
      });
    }
    catch (Exception e) {
      LOG.error("Unable to commit dependency changes", e);
    }
    myModel.resetModified();
  }

  public boolean isModified() {
    return myModel.isModified();
  }

  private static class TableItemRenderer extends ColoredTableCellRenderer {
    private final Border NO_FOCUS_BORDER = BorderFactory.createEmptyBorder(1, 1, 1, 1);

    @Override
    protected void customizeCellRenderer(@NotNull JTable table, @NotNull Object value, boolean selected, boolean hasFocus,
                                         int row, int column) {
      setPaintFocusBorder(false);
      setFocusBorderAroundIcon(true);
      setBorder(NO_FOCUS_BORDER);
      if (value instanceof ModuleDependenciesTableItem) {
        final ModuleDependenciesTableItem tableItem = (ModuleDependenciesTableItem)value;
        getCellAppearance(tableItem).customize(this);
        setToolTipText(tableItem.getTooltipText());
      }
    }
  }

  private abstract static class PopupAction implements Runnable {
    private Icon myIcon;
    private Object myIndex;
    private Object myTitle;

    protected PopupAction(Icon icon, Object index, Object title) {
      myIcon = icon;
      myIndex = index;
      myTitle = title;
    }
  }
}
