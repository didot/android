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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerIcons;
import com.android.tools.profilers.memory.adapters.*;
import com.android.tools.profilers.memory.adapters.CaptureObject.CaptureChangedListener;
import com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.*;
import java.util.List;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_TOP_BORDER;
import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_CLASS;

final class MemoryClassifierView extends AspectObserver {
  private static final int LABEL_COLUMN_WIDTH = 800;
  private static final int DEFAULT_COLUMN_WIDTH = 80;

  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private final IdeProfilerComponents myIdeProfilerComponents;

  @NotNull private final Map<ClassifierAttribute, AttributeColumn<ClassifierSet>> myAttributeColumns = new HashMap<>();

  @Nullable private CaptureObject myCaptureObject = null;

  @Nullable private HeapSet myHeapSet = null;

  @Nullable private ClassSet myClassSet = null;

  @NotNull private final JPanel myPanel = new JPanel(new BorderLayout());

  @Nullable private JComponent myColumnTree;

  @Nullable private JTree myTree;

  @Nullable private DefaultTreeModel myTreeModel;

  @Nullable private TableColumnModel myTableColumnModel;

  @Nullable private MemoryClassifierTreeNode myTreeRoot;

  @Nullable private Comparator<MemoryObjectTreeNode<ClassifierSet>> myInitialComparator;

  public MemoryClassifierView(@NotNull MemoryProfilerStage stage, @NotNull IdeProfilerComponents ideProfilerComponents) {
    myStage = stage;
    myIdeProfilerComponents = ideProfilerComponents;

    myStage.getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.CURRENT_LOADING_CAPTURE, this::loadCapture)
      .onChange(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE, this::refreshCapture)
      .onChange(MemoryProfilerAspect.CURRENT_HEAP, this::refreshHeapSet)
      .onChange(MemoryProfilerAspect.CURRENT_CLASS, this::refreshClassSet)
      .onChange(MemoryProfilerAspect.CLASS_GROUPING, this::refreshGrouping);

    myAttributeColumns.put(
      ClassifierAttribute.LABEL,
      new AttributeColumn<>(
        "Class Name", this::getNameColumnRenderer, SwingConstants.LEFT, LABEL_COLUMN_WIDTH,
        createTreeNodeComparator(Comparator.comparing(ClassifierSet::getName), Comparator.comparing(ClassSet::getName))));
    myAttributeColumns.put(
      ClassifierAttribute.ALLOC_COUNT,
      new AttributeColumn<>(
        "Alloc Count",
        () -> new SimpleColumnRenderer<ClassifierSet>(
          value -> Integer.toString(value.getAdapter().getAllocatedCount()),
          value -> null,
          SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        createTreeNodeComparator(Comparator.comparingInt(ClassifierSet::getAllocatedCount),
                                 Comparator.comparingInt(ClassSet::getAllocatedCount))));
    myAttributeColumns.put(
      ClassifierAttribute.DEALLOC_COUNT,
      new AttributeColumn<>(
        "Dealloc Count",
        () -> new SimpleColumnRenderer<ClassifierSet>(
          value -> Integer.toString(value.getAdapter().getDeallocatedCount()),
          value -> null,
          SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        createTreeNodeComparator(Comparator.comparingInt(ClassifierSet::getDeallocatedCount),
                                 Comparator.comparingInt(ClassSet::getDeallocatedCount))));
    myAttributeColumns.put(
      ClassifierAttribute.SHALLOW_SIZE,
      new AttributeColumn<>(
        "Shallow Size",
        () -> new SimpleColumnRenderer<ClassifierSet>(
          value -> Long.toString(value.getAdapter().getTotalShallowSize()),
          value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        createTreeNodeComparator(Comparator.comparingLong(ClassSet::getTotalShallowSize))));
    myAttributeColumns.put(
      ClassifierAttribute.RETAINED_SIZE,
      new AttributeColumn<>(
        "Retained Size",
        () -> new SimpleColumnRenderer<ClassifierSet>(
          value -> Long.toString(value.getAdapter().getTotalRetainedSize()),
          value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        createTreeNodeComparator(Comparator.comparingLong(ClassifierSet::getTotalRetainedSize),
                                 Comparator.comparingLong(ClassSet::getTotalRetainedSize))));
  }

  @NotNull
  JComponent getComponent() {
    return myPanel;
  }

  @VisibleForTesting
  @Nullable
  JTree getTree() {
    return myTree;
  }

  @VisibleForTesting
  @Nullable
  JComponent getColumnTree() {
    return myColumnTree;
  }

  @VisibleForTesting
  @Nullable
  TableColumnModel getTableColumnModel() {
    return myTableColumnModel;
  }

  /**
   * Must manually remove from parent container!
   */
  private void reset() {
    myCaptureObject = null;
    myHeapSet = null;
    myClassSet = null;
    myColumnTree = null;
    myTree = null;
    myTreeRoot = null;
    myTreeModel = null;
    myPanel.removeAll();
    myStage.selectClassSet(null);
  }

  private void loadCapture() {
    if (myStage.getSelectedCapture() == null || myCaptureObject != myStage.getSelectedCapture()) {
      reset();
    }
  }

  private void refreshCapture() {
    myCaptureObject = myStage.getSelectedCapture();
    if (myCaptureObject == null) {
      reset();
      return;
    }

    assert myColumnTree == null && myTreeModel == null && myTreeRoot == null && myTree == null;

    myTree = new Tree();
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    myTree.addTreeSelectionListener(e -> {
      TreePath path = e.getPath();
      if (!e.isAddedPath()) {
        return;
      }

      assert path.getLastPathComponent() instanceof MemoryClassifierTreeNode;
      MemoryClassifierTreeNode classifierNode = (MemoryClassifierTreeNode)path.getLastPathComponent();
      if (classifierNode.getAdapter() instanceof ClassSet && myClassSet != classifierNode.getAdapter()) {
        myClassSet = (ClassSet)classifierNode.getAdapter();
        myStage.selectClassSet(myClassSet);
      }
    });

    myIdeProfilerComponents.installNavigationContextMenu(myTree, myStage.getStudioProfilers().getIdeServices().getCodeNavigator(), () -> {
      TreePath selection = myTree.getSelectionPath();
      if (selection == null || !(selection.getLastPathComponent() instanceof MemoryObjectTreeNode)) {
        return null;
      }

      if (((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter() instanceof ClassSet) {
        ClassSet classSet = (ClassSet)((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter();
        return new CodeLocation.Builder(classSet.getClassEntry().getClassName()).build();
      }
      return null;
    });

    List<ClassifierAttribute> attributes = myCaptureObject.getClassifierAttributes();
    myTableColumnModel = new DefaultTableColumnModel();
    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree, myTableColumnModel);
    ClassifierAttribute sortAttribute = Collections.max(attributes, Comparator.comparingInt(ClassifierAttribute::getWeight));
    for (ClassifierAttribute attribute : attributes) {
      AttributeColumn<ClassifierSet> column = myAttributeColumns.get(attribute);
      ColumnTreeBuilder.ColumnBuilder columnBuilder = column.getBuilder();
      if (sortAttribute == attribute) {
        columnBuilder.setInitialOrder(attribute.getSortOrder());
        myInitialComparator =
          attribute.getSortOrder() == SortOrder.ASCENDING ? column.getComparator() : Collections.reverseOrder(column.getComparator());
      }
      builder.addColumn(columnBuilder);
    }
    builder.setTreeSorter((Comparator<MemoryObjectTreeNode<ClassifierSet>> comparator, SortOrder sortOrder) -> {
      if (myTreeRoot != null && myTreeModel != null) {
        TreePath selectionPath = myTree.getSelectionPath();
        myTreeRoot.sort(comparator);
        myTreeModel.nodeStructureChanged(myTreeRoot);
        if (selectionPath != null) {
          myTree.expandPath(selectionPath.getParentPath());
          myTree.setSelectionPath(selectionPath);
          myTree.scrollPathToVisible(selectionPath);
        }
      }
    });

    builder.setHoverColor(ProfilerColors.DEFAULT_HOVER_COLOR);
    builder.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    builder.setBorder(DEFAULT_TOP_BORDER);
    myColumnTree = builder.build();
    myPanel.add(myColumnTree, BorderLayout.CENTER);

    //noinspection Convert2Lambda
    myCaptureObject.addCaptureChangedListener(new CaptureChangedListener() {
      @Override
      public void heapChanged() {
        // TODO handle multiple heaps other than default heap
        if (myHeapSet == null) {
          return;
        }

        assert myTreeRoot != null;
        myTreeRoot.reset();
        myTreeRoot.expandNode();
        myTreeModel.nodeStructureChanged(myTreeRoot);
      }
    });
  }

  private void refreshHeapSet() {
    assert myCaptureObject != null && myTree != null;

    HeapSet heapSet = myStage.getSelectedHeapSet();
    if (heapSet == myHeapSet) {
      return;
    }

    myHeapSet = heapSet;

    if (myHeapSet != null) {
      refreshGrouping();
    }
  }

  /**
   * Refreshes the view based on the "group by" selection from the user.
   */
  private void refreshGrouping() {
    assert myCaptureObject != null && myTree != null;

    Comparator<MemoryObjectTreeNode<ClassifierSet>> comparator = myTreeRoot == null ? myInitialComparator : myTreeRoot.getComparator();
    HeapSet heapSet = myStage.getSelectedHeapSet();
    assert heapSet != null;
    heapSet.setClassGrouping(myStage.getConfiguration().getClassGrouping());
    myTreeRoot = new MemoryClassifierTreeNode(heapSet);
    myTreeRoot.expandNode(); // Expand it once to get all the children, since we won't display the tree root (HeapSet) by default.
    if (comparator != null) {
      myTreeRoot.sort(comparator);
    }

    myTreeModel = new DefaultTreeModel(myTreeRoot);
    myTree.setModel(myTreeModel);

    // Rename class column depending on group by mechanism
    assert myColumnTree != null;
    String headerName = null;
    switch (myStage.getConfiguration().getClassGrouping()) {
      case ARRANGE_BY_CLASS:
        headerName = "Class Name";
        break;
      case ARRANGE_BY_CALLSTACK:
        headerName = "Callstack Name";
        break;
      case ARRANGE_BY_PACKAGE:
        headerName = "Package Name";
    }
    myTableColumnModel.getColumn(0).setHeaderValue(headerName);

    // Attempt to reselect the previously selected ClassSet node or FieldPath.
    ClassSet selectedClassSet = myStage.getSelectedClassSet();
    InstanceObject selectedInstance = myStage.getSelectedInstanceObject();
    List<FieldObject> fieldPath = myStage.getSelectedFieldObjectPath();

    if (selectedClassSet == null) {
      return;
    }

    MemoryObjectTreeNode<ClassifierSet> nodeToSelect = findSmallestSuperSetNode(myTreeRoot, selectedClassSet);
    if ((nodeToSelect == null || !(nodeToSelect.getAdapter() instanceof ClassSet)) && selectedInstance != null) {
      ClassifierSet classifierSet = myTreeRoot.getAdapter().findContainingClassifierSet(selectedInstance);
      if (classifierSet != null) {
        nodeToSelect = findSmallestSuperSetNode(myTreeRoot, classifierSet);
      }
    }

    if (nodeToSelect == null || !(nodeToSelect.getAdapter() instanceof ClassSet)) {
      myStage.selectClassSet(null);
      return;
    }

    assert myTree != null;
    TreePath treePath = new TreePath(nodeToSelect.getPathToRoot().toArray());
    myClassSet = (ClassSet)nodeToSelect.getAdapter();
    myTree.expandPath(treePath.getParentPath());
    myTree.setSelectionPath(treePath);
    myTree.scrollPathToVisible(treePath);
    myStage.selectClassSet(myClassSet);
    myStage.selectInstanceObject(selectedInstance);
    myStage.selectFieldObjectPath(fieldPath);
  }

  /**
   * Scan through child {@link ClassifierSet}s for the given {@link InstanceObject}s and return the "path" containing all the target instances.
   *
   * @param rootNode  the root from where to start the search
   * @param targetSet target set of {@link InstanceObject}s to search for
   * @return the path of chained {@link ClassifierSet} that leads to the given instanceObjects, or throws an exception if not found.
   */
  @Nullable
  private static MemoryObjectTreeNode<ClassifierSet> findSmallestSuperSetNode(@NotNull MemoryObjectTreeNode<ClassifierSet> rootNode,
                                                                              @NotNull ClassifierSet targetSet) {
    if (rootNode.getAdapter().isSupersetOf(targetSet)) {
      for (MemoryObjectTreeNode<ClassifierSet> child : rootNode.getChildren()) {
        MemoryObjectTreeNode<ClassifierSet> result = findSmallestSuperSetNode(child, targetSet);
        if (result != null) {
          return result;
        }
      }

      return rootNode;
    }

    return null;
  }

  /**
   * Refreshes the view based on the selected {@link ClassSet}.
   */
  private void refreshClassSet() {
    if (myTreeRoot == null || myTreeModel == null || myTree == null || myClassSet == myStage.getSelectedClassSet()) {
      return;
    }

    myClassSet = myStage.getSelectedClassSet();
    if (myClassSet != null) {
      MemoryObjectTreeNode<ClassifierSet> node = findSmallestSuperSetNode(myTreeRoot, myClassSet);
      if (node != null) {
        TreePath treePath = new TreePath(node.getPathToRoot().toArray());
        myTree.expandPath(treePath.getParentPath());
        myTree.setSelectionPath(treePath);
        myTree.scrollPathToVisible(treePath);
      }
      else {
        myClassSet = null;
        myStage.selectClassSet(null);
      }
    }
  }

  @NotNull
  private ColoredTreeCellRenderer getNameColumnRenderer() {
    return new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        if (!(value instanceof MemoryObjectTreeNode)) {
          return;
        }

        MemoryObjectTreeNode node = (MemoryObjectTreeNode)value;
        if (node.getAdapter() instanceof ClassSet) {
          ClassSet classSet = (ClassSet)node.getAdapter();

          setIcon(((ClassSet)node.getAdapter()).hasStackInfo() ? ProfilerIcons.CLASS_STACK : PlatformIcons.CLASS_ICON);

          String className = classSet.getClassEntry().getSimpleClassName();
          String packageName = classSet.getClassEntry().getPackageName();
          append(className, SimpleTextAttributes.REGULAR_ATTRIBUTES, className);
          if (myStage.getConfiguration().getClassGrouping() == ARRANGE_BY_CLASS) {
            if (!packageName.isEmpty()) {
              String packageText = " (" + packageName + ")";
              append(packageText, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES, packageText);
            }
          }
        }
        else if (node.getAdapter() instanceof PackageSet) {
          ClassifierSet set = (ClassifierSet)node.getAdapter();
          setIcon(set.hasStackInfo() ? ProfilerIcons.PACKAGE_STACK : PlatformIcons.PACKAGE_ICON);
          String name = set.getName();
          append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES, name);
        }
        else if (node.getAdapter() instanceof MethodSet) {
          setIcon(PlatformIcons.METHOD_ICON);

          MethodSet methodObject = (MethodSet)node.getAdapter();
          String name = methodObject.getCodeLocation().getMethodName();
          int lineNumber = methodObject.getCodeLocation().getLineNumber();
          String className = methodObject.getCodeLocation().getClassName();

          if (name != null) {
            String nameAndLine = name + "()" + (lineNumber == CodeLocation.INVALID_LINE_NUMBER ? "" : ":" + lineNumber);
            append(nameAndLine, SimpleTextAttributes.REGULAR_ATTRIBUTES, nameAndLine);
          }
          else {
            name = "<unknown method>";
            append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES, name);
          }

          String classNameText = " (" + className + ")";
          append(classNameText, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES, classNameText);
        }
        else if (node.getAdapter() instanceof ThreadSet) {
          setIcon(AllIcons.Debugger.ThreadSuspended);
          String threadName = node.getAdapter().getName();
          append(threadName, SimpleTextAttributes.REGULAR_ATTRIBUTES, threadName);
        }

        setTextAlign(SwingConstants.LEFT);
      }
    };
  }

  /**
   * Creates a comparator function for the given {@link ClassifierSet}-specific and {@link ClassSet}-specific comparators.
   *
   * @param classifierSetComparator is a comparator for {@link ClassifierSet} objects, and not {@link ClassSet}
   * @return a {@link Comparator} that order all non-{@link ClassSet}s before {@link ClassSet}s, and orders according to the given
   * two params when the base class is the same
   */
  @VisibleForTesting
  static Comparator<MemoryObjectTreeNode<ClassifierSet>> createTreeNodeComparator(@NotNull Comparator<ClassifierSet> classifierSetComparator,
                                                                                  @NotNull Comparator<ClassSet> classSetComparator) {
    return (o1, o2) -> {
      int compareResult;
      ClassifierSet firstArg = o1.getAdapter();
      ClassifierSet secondArg = o2.getAdapter();
      if (firstArg instanceof ClassSet && secondArg instanceof ClassSet) {
        compareResult = classSetComparator.compare((ClassSet)firstArg, (ClassSet)secondArg);
      }
      else if (firstArg instanceof ClassSet) {
        compareResult = 1;
      }
      else if (secondArg instanceof ClassSet) {
        compareResult = -1;
      }
      else {
        compareResult = classifierSetComparator.compare(firstArg, secondArg);
      }
      return compareResult;
    };
  }

  /**
   * Convenience method for {@link #createTreeNodeComparator(Comparator, Comparator)}.
   */
  @VisibleForTesting
  static Comparator<MemoryObjectTreeNode<ClassifierSet>> createTreeNodeComparator(@NotNull Comparator<ClassSet> classObjectComparator) {
    return createTreeNodeComparator(Comparator.comparing(ClassifierSet::getName), classObjectComparator);
  }

  private static class MemoryClassifierTreeNode extends LazyMemoryObjectTreeNode<ClassifierSet> {
    private MemoryClassifierTreeNode(@NotNull ClassifierSet classifierSet) {
      super(classifierSet, false);
    }

    @Override
    public void add(@NotNull MemoryObjectTreeNode child) {
      if (myMemoizedChildrenCount == myChildren.size()) {
        super.add(child);
        myMemoizedChildrenCount++;
      }
    }

    @Override
    public void remove(@NotNull MutableTreeNode child) {
      if (myMemoizedChildrenCount == myChildren.size()) {
        super.remove(child);
        myMemoizedChildrenCount--;
      }
    }

    @Override
    public int computeChildrenCount() {
      return getAdapter().getChildrenClassifierSets().size();
    }

    @Override
    public void expandNode() {
      if (myMemoizedChildrenCount == myChildren.size()) {
        return;
      }

      getChildCount();
      getAdapter().getChildrenClassifierSets().forEach(set -> {
        MemoryClassifierTreeNode node = new MemoryClassifierTreeNode(set);
        node.setTreeModel(getTreeModel());
        insert(node, myChildren.size());
      });
    }
  }
}
