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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.module;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.AbstractDeclaredDependenciesPanel;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.treeview.DependencySelection;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview.TargetAndroidModuleNode;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsModuleDependency;
import com.google.common.collect.Lists;
import com.intellij.ui.table.TableView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.structure.configurables.android.dependencies.UiUtil.isMetaOrCtrlKeyPressed;
import static com.intellij.ui.IdeBorderFactory.createEmptyBorder;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static java.awt.Cursor.*;
import static java.awt.event.KeyEvent.KEY_PRESSED;
import static java.awt.event.KeyEvent.KEY_RELEASED;
import static java.awt.event.MouseEvent.MOUSE_PRESSED;
import static javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION;
import static javax.swing.SwingUtilities.convertPointFromScreen;

/**
 * Panel that displays the table of "editable" dependencies.
 */
class DeclaredDependenciesPanel extends AbstractDeclaredDependenciesPanel implements DependencySelection {
  @NotNull private final PsContext myContext;

  @NotNull private final DeclaredDependenciesTableModel myDependenciesTableModel;
  @NotNull private final TableView<PsAndroidDependency> myDependenciesTable;
  @NotNull private final ListSelectionListener myTableSelectionListener;

  @NotNull private final List<SelectionListener> mySelectionListeners = Lists.newCopyOnWriteArrayList();

  private KeyEventDispatcher myKeyEventDispatcher;

  DeclaredDependenciesPanel(@NotNull PsAndroidModule module, @NotNull PsContext context) {
    super("Declared Dependencies", context, module.getParent(), module);
    myContext = context;

    getContentsPanel().add(createActionsPanel(), BorderLayout.NORTH);

    myDependenciesTableModel = new DeclaredDependenciesTableModel(module);
    myDependenciesTable = new TableView<PsAndroidDependency>(myDependenciesTableModel) {
      @Override
      protected void processMouseEvent(MouseEvent e) {
        int id = e.getID();
        if (id == MOUSE_PRESSED) {
          PsModuleDependency dependency = getIfHyperlink(e);
          if (dependency != null) {
            String name = dependency.getName();
            myContext.setSelectedModule(name, DeclaredDependenciesPanel.this);
            // Do not call super, to avoid selecting the 'module' node when clicking a hyperlink.
            return;
          }
        }
        super.processMouseEvent(e);
      }
    };

    ListSelectionModel tableSelectionModel = myDependenciesTable.getSelectionModel();
    tableSelectionModel.setSelectionMode(MULTIPLE_INTERVAL_SELECTION);

    if (!myDependenciesTable.getItems().isEmpty()) {
      myDependenciesTable.changeSelection(0, 0, false, false);
      updateEditor();
    }
    myTableSelectionListener = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        PsAndroidDependency selected = getSelection();
        if (selected != null) {
          for (SelectionListener listener : mySelectionListeners) {
            listener.dependencySelected(selected);
          }
        }
        updateEditor();
      }
    };
    tableSelectionModel.addListSelectionListener(myTableSelectionListener);

    addHyperlinkFunctionality();

    myDependenciesTable.setDragEnabled(false);
    myDependenciesTable.setIntercellSpacing(new Dimension(0, 0));
    myDependenciesTable.setShowGrid(false);

    JScrollPane scrollPane = createScrollPane(myDependenciesTable);
    scrollPane.setBorder(createEmptyBorder());
    getContentsPanel().add(scrollPane, BorderLayout.CENTER);

    updateTableColumnSizes();
  }

  private void addHyperlinkFunctionality() {
    myDependenciesTable.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        PsModuleDependency hovered = getIfHyperlink(e);
        setHoveredDependency(hovered);
      }
    });

    myKeyEventDispatcher = new KeyEventDispatcher() {
      @Override
      public boolean dispatchKeyEvent(KeyEvent e) {
        PsModuleDependency dependency = null;
        if (e.getID() == KEY_PRESSED) {
          if (isMetaOrCtrlKeyPressed(e)) {
            dependency = getDependencyUnderMousePointer();
          }
          setHoveredDependency(dependency);
        }
        else if (e.getID() == KEY_RELEASED) {
          if (isMetaOrCtrlKeyPressed(e)) {
            setHoveredDependency(null);
          }
        }
        return false;
      }
    };

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(myKeyEventDispatcher);
  }

  @Nullable
  private PsModuleDependency getIfHyperlink(@NotNull MouseEvent e) {
    if (isMetaOrCtrlKeyPressed(e)) {
      Point point = new Point(e.getX(), e.getY());
      return getDependencyForLocation(point);
    }
    return null;
  }

  private PsModuleDependency getDependencyUnderMousePointer() {
    PointerInfo pointerInfo = MouseInfo.getPointerInfo();
    if (pointerInfo != null) {
      Point location = pointerInfo.getLocation();
      convertPointFromScreen(location, myDependenciesTable);
      return getDependencyForLocation(location);
    }
    return null;
  }

  @Nullable
  public PsModuleDependency getDependencyForLocation(Point location) {
    int column = myDependenciesTable.columnAtPoint(location);
    if (column == 0) {
      // "Dependency" column
      int row = myDependenciesTable.rowAtPoint(location);
      if (row > -1) {
        PsAndroidDependency dependency = myDependenciesTableModel.getItem(row);
        if (dependency instanceof PsModuleDependency) {
          return (PsModuleDependency)dependency;
        }
      }
    }
    return null;
  }

  private void setHoveredDependency(@Nullable PsModuleDependency dependency) {
    myDependenciesTableModel.setHoveredDependency(dependency);
    Cursor cursor = getDefaultCursor();
    if (dependency != null) {
      cursor = getPredefinedCursor(HAND_CURSOR);
    }
    myDependenciesTable.setCursor(cursor);
    myDependenciesTable.repaint();
  }

  private void updateEditor() {
    Collection<PsAndroidDependency> selection = myDependenciesTable.getSelection();
    PsAndroidDependency selected = null;
    if (selection.size() == 1) {
      selected = getFirstItem(selection);
    }
    updateEditor(selected);
  }

  void updateTableColumnSizes() {
    myDependenciesTable.updateColumnSizes();
  }

  @Override
  public void dispose() {
    mySelectionListeners.clear();
    if (myKeyEventDispatcher != null) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(myKeyEventDispatcher);
    }
  }

  void add(@NotNull SelectionListener listener) {
    PsAndroidDependency selected = getSelection();
    if (selected != null) {
      listener.dependencySelected(selected);
    }
    mySelectionListeners.add(listener);
  }

  @Override
  @Nullable
  public PsAndroidDependency getSelection() {
    Collection<PsAndroidDependency> selection = myDependenciesTable.getSelection();
    if (selection.size() == 1) {
      PsAndroidDependency selected = getFirstItem(selection);
      assert selected != null;
      return selected;
    }
    return null;
  }

  @Override
  public void setSelection(@Nullable PsAndroidDependency selection) {
    ListSelectionModel tableSelectionModel = myDependenciesTable.getSelectionModel();
    // Remove ListSelectionListener. We only want the selection event when the user selects a table cell directly. If we got here is
    // because the user selected a dependency in the "Variants" tree view, and we are simply syncing the table.
    tableSelectionModel.removeListSelectionListener(myTableSelectionListener);

    if (selection == null) {
      myDependenciesTable.clearSelection();
    }
    else {
      myDependenciesTable.setSelection(Collections.singleton(selection));
    }
    updateEditor();

    // Add ListSelectionListener again, to react when user selects a table cell directly.
    tableSelectionModel.addListSelectionListener(myTableSelectionListener);
  }

  public interface SelectionListener {
    void dependencySelected(@NotNull PsAndroidDependency dependency);
  }
}
