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

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_TOP_BORDER;
import static com.android.tools.profilers.ProfilerLayout.ROW_HEIGHT_PADDING;
import static com.android.tools.profilers.ProfilerLayout.TABLE_ROW_BORDER;
import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_CLASS;

import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.adtui.instructions.InstructionsPanel;
import com.android.tools.adtui.instructions.NewRowInstruction;
import com.android.tools.adtui.instructions.TextInstruction;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.formatter.NumberFormatter;
import com.android.tools.adtui.stdui.StandardColors;
import com.android.tools.profilers.ContextMenuInstaller;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerFonts;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute;
import com.android.tools.profilers.memory.adapters.FieldObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.android.tools.profilers.memory.adapters.MemoryObject;
import com.android.tools.profilers.memory.adapters.classifiers.AllHeapSet;
import com.android.tools.profilers.memory.adapters.classifiers.ClassSet;
import com.android.tools.profilers.memory.adapters.classifiers.ClassifierSet;
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet;
import com.android.tools.profilers.memory.adapters.classifiers.MethodSet;
import com.android.tools.profilers.memory.adapters.classifiers.NativeAllocationMethodSet;
import com.android.tools.profilers.memory.adapters.classifiers.NativeCallStackSet;
import com.android.tools.profilers.memory.adapters.classifiers.PackageSet;
import com.android.tools.profilers.memory.adapters.classifiers.ThreadSet;
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.LoadingPanel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtilities;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class MemoryClassifierView extends AspectObserver {
  private static final int LABEL_COLUMN_WIDTH = 800;
  private static final int MODULE_COLUMN_WIDTH = 100;
  private static final int HEAP_UPDATING_DELAY_MS = 250;
  private static final int MIN_COLUMN_WIDTH = 16;

  private static final String HELP_TIP_HEADER_LIVE_ALLOCATION = "Selected range has no allocations or deallocations";
  private static final String HELP_TIP_DESCRIPTION_LIVE_ALLOCATION =
    "Select a valid range in the timeline where the Java memory is changing to view allocations and deallocations.";
  private static final String HELP_TIP_HEADER_EXPLICIT_CAPTURE = "Selected capture has no contents";
  private static final String HELP_TIP_DESCRIPTION_EXPLICIT_CAPTURE = "There are no allocations in the selected capture.";
  private static final String HELP_TIP_HEADER_FILTER_NO_MATCH = "Selected filters have no match";

  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private final ContextMenuInstaller myContextMenuInstaller;

  @NotNull private final Map<ClassifierAttribute, AttributeColumn<ClassifierSet>> myAttributeColumns = new HashMap<>();

  @Nullable private CaptureObject myCaptureObject = null;

  @Nullable private HeapSet myHeapSet = null;

  @Nullable private ClassSet myClassSet = null;

  @Nullable private ClassifierSet mySelectedClassifierSet = null;

  @NotNull private final JPanel myPanel = new JPanel(new BorderLayout());

  @NotNull private final JPanel myClassifierPanel = new JPanel(new BorderLayout());

  @NotNull private final LoadingPanel myLoadingPanel;

  @Nullable private InstructionsPanel myHelpTipPanel; // Panel to let user know to select a range with allocations in it.

  @Nullable private JComponent myColumnTree;

  @Nullable private JTree myTree;

  @Nullable private DefaultTreeModel myTreeModel;

  @Nullable private TableColumnModel myTableColumnModel;

  @Nullable private MemoryClassifierTreeNode myTreeRoot;

  @Nullable private Comparator<MemoryObjectTreeNode<ClassifierSet>> myInitialComparator;

  MemoryClassifierView(@NotNull MemoryProfilerStage stage, @NotNull IdeProfilerComponents ideProfilerComponents) {
    myStage = stage;
    myContextMenuInstaller = ideProfilerComponents.createContextMenuInstaller();
    myLoadingPanel = ideProfilerComponents.createLoadingPanel(HEAP_UPDATING_DELAY_MS);
    myLoadingPanel.setLoadingText("");

    myStage.getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.CURRENT_LOADING_CAPTURE, this::loadCapture)
      .onChange(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE, this::refreshCapture)
      .onChange(MemoryProfilerAspect.CURRENT_HEAP, this::refreshHeapSet)
      .onChange(MemoryProfilerAspect.CURRENT_HEAP_UPDATING, this::startHeapLoadingUi)
      .onChange(MemoryProfilerAspect.CURRENT_HEAP_UPDATED, this::stopHeapLoadingUi)
      .onChange(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS, this::refreshTree)
      .onChange(MemoryProfilerAspect.CURRENT_CLASS, this::refreshClassSet)
      .onChange(MemoryProfilerAspect.CLASS_GROUPING, this::refreshGrouping)
      .onChange(MemoryProfilerAspect.CURRENT_FILTER, this::refreshFilter);

    myAttributeColumns.put(
      ClassifierAttribute.LABEL,
      new AttributeColumn<>(
        "Class Name", this::getNameColumnRenderer, SwingConstants.LEFT, LABEL_COLUMN_WIDTH, SortOrder.ASCENDING,
        createTreeNodeComparator(Comparator.comparing(ClassifierSet::getName), Comparator.comparing(ClassSet::getName))));
    myAttributeColumns.put(
      ClassifierAttribute.MODULE,
      new AttributeColumn<>(
        "Module Name", this::getModuleColumnRenderer, SwingConstants.LEFT, MODULE_COLUMN_WIDTH, SortOrder.ASCENDING,
        createTreeNodeComparator(Comparator.comparing(NativeCallStackSet::getModuleName))));
    myAttributeColumns.put(
      ClassifierAttribute.ALLOCATIONS,
      makeColumn("Allocations", ClassifierSet::getDeltaAllocationCount));
    myAttributeColumns.put(
      ClassifierAttribute.DEALLOCATIONS,
      makeColumn("Deallocations", ClassifierSet::getDeltaDeallocationCount));
    myAttributeColumns.put(
      ClassifierAttribute.TOTAL_COUNT,
      makeColumn("Total Count", ClassifierSet::getTotalObjectCount));
    myAttributeColumns.put(
      ClassifierAttribute.NATIVE_SIZE,
      makeColumn("Native Size", ClassifierSet::getTotalNativeSize, Comparator.comparing(ClassifierSet::getName)));
    myAttributeColumns.put(
      ClassifierAttribute.SHALLOW_SIZE,
      makeColumn("Shallow Size", ClassifierSet::getTotalShallowSize, Comparator.comparing(ClassifierSet::getName)));
    myAttributeColumns.put(
      ClassifierAttribute.RETAINED_SIZE,
      makeColumn("Retained Size", ClassifierSet::getTotalRetainedSize));
    myAttributeColumns.put(
      ClassifierAttribute.ALLOCATIONS_SIZE,
      makeColumn("Allocations Size", ClassifierSet::getAllocationSize));
    myAttributeColumns.put(
      ClassifierAttribute.DEALLOCATIONS_SIZE,
      makeColumn("Deallocations Size", ClassifierSet::getDeallocationSize));
    myAttributeColumns.put(
      ClassifierAttribute.REMAINING_SIZE,
      makeColumn("Remaining Size", ClassifierSet::getTotalRemainingSize));
  }

  /**
   * Make right-aligned, descending column displaying integer property with custom order for non-ClassSet values
   */
  private AttributeColumn<ClassifierSet> makeColumn(@NotNull String name,
                                                    @NotNull ToLongFunction<ClassifierSet> prop,
                                                    @NotNull Comparator<ClassifierSet> comp) {

    Function<MemoryObjectTreeNode<ClassifierSet>, String> textGetter = node ->
      NumberFormatter.formatInteger(prop.applyAsLong(node.getAdapter()));
    final ColoredTreeCellRenderer renderer;
    if (myStage.getStudioProfilers().getIdeServices().getFeatureConfig().isSeparateHeapDumpUiEnabled()) {
      // Progress-bar style background that reflects percentage contribution
      renderer = new PercentColumnRenderer<>(
        textGetter, v -> null, SwingConstants.RIGHT,
        node -> {
          MemoryObjectTreeNode<ClassifierSet> parent = node.myParent;
          if (parent == null) {
            return 0;
          }
          else {
            assert myTreeRoot != null;
            // Compute relative contribution with respect to top-most parent
            long myVal = prop.applyAsLong(node.getAdapter());
            ClassifierSet root = myTreeRoot.getAdapter();
            long parentVal = prop.applyAsLong(root);
            return parentVal == 0 ? 0 : (int)(myVal * 100 / parentVal);
          }
        }
      );
    }
    else {
      // Legacy renderer
      renderer = new SimpleColumnRenderer<>(textGetter, v -> null, SwingConstants.RIGHT);
    }

    return new AttributeColumn<>(
      name,
      () -> renderer,
      SwingConstants.RIGHT,
      SimpleColumnRenderer.DEFAULT_COLUMN_WIDTH,
      SortOrder.DESCENDING,
      createTreeNodeComparator(comp, Comparator.comparingLong(prop))
    );
  }

  /**
   * Make right-aligned, descending column displaying integer property
   */
  private AttributeColumn<ClassifierSet> makeColumn(String name, ToLongFunction<ClassifierSet> prop) {
    return makeColumn(name, prop, Comparator.comparingLong(prop));
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

  @VisibleForTesting
  @NotNull
  JPanel getClassifierPanel() {
    return myClassifierPanel;
  }

  /**
   * Must manually remove from parent container!
   */
  private void reset() {
    myCaptureObject = null;
    myHeapSet = null;
    myClassSet = null;
    myClassifierPanel.removeAll();
    myHelpTipPanel = null;
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

  private void refreshFilter() {
    if (myHeapSet != null) {
      refreshTree();
    }
  }

  private void refreshCapture() {
    myCaptureObject = myStage.getSelectedCapture();
    if (myCaptureObject == null) {
      reset();
      return;
    }

    assert myColumnTree == null && myTreeModel == null && myTreeRoot == null && myTree == null;

    // Use JTree instead of IJ's tree, because IJ's tree does not happen border's Insets.
    //noinspection UndesirableClassUsage
    myTree = new JTree();
    int defaultFontHeight = myTree.getFontMetrics(myTree.getFont()).getHeight();
    myTree.setRowHeight(defaultFontHeight + ROW_HEIGHT_PADDING);
    myTree.setBorder(TABLE_ROW_BORDER);
    myTree.setRootVisible(true);
    myTree.setShowsRootHandles(false);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (myTree.getSelectionCount() == 0) {
          myTree.setSelectionRow(0);
        }
      }
    });

    myTree.addTreeSelectionListener(e -> {
      TreePath path = e.getPath();
      if (!e.isAddedPath()) {
        return;
      }

      assert path.getLastPathComponent() instanceof MemoryClassifierTreeNode;
      MemoryClassifierTreeNode classifierNode = (MemoryClassifierTreeNode)path.getLastPathComponent();

      mySelectedClassifierSet = classifierNode.getAdapter();

      if (classifierNode.getAdapter() instanceof ClassSet && myClassSet != classifierNode.getAdapter()) {
        myClassSet = (ClassSet)classifierNode.getAdapter();
        myStage.selectClassSet(myClassSet);
      }
    });

    myContextMenuInstaller.installNavigationContextMenu(myTree, myStage.getStudioProfilers().getIdeServices().getCodeNavigator(), () -> {
      TreePath selection = myTree.getSelectionPath();
      if (selection == null || !(selection.getLastPathComponent() instanceof MemoryObjectTreeNode)) {
        return null;
      }

      MemoryObject treeNodeAdapter = ((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter();
      if (treeNodeAdapter instanceof ClassSet) {
        ClassSet classSet = (ClassSet)treeNodeAdapter;
        return new CodeLocation.Builder(classSet.getClassEntry().getClassName()).build();
      }
      if (treeNodeAdapter instanceof NativeCallStackSet) {
        NativeCallStackSet nativeSet = (NativeCallStackSet)treeNodeAdapter;
        if (!Strings.isNullOrEmpty(nativeSet.getFileName())) {
          return new CodeLocation.Builder(nativeSet.getName()) // Expects class name but we don't have that so we use the function.
            .setMethodName(nativeSet.getName())
            .setFileName(nativeSet.getFileName())
            .setLineNumber(nativeSet.getLineNumber() - 1) // Line numbers from symbolizer are 1 based UI is 0 based.
            .build();
        }
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
      columnBuilder.setMinWidth(MIN_COLUMN_WIDTH);
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
    builder.setHoverColor(StandardColors.HOVER_COLOR);
    builder.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    builder.setBorder(DEFAULT_TOP_BORDER);
    builder.setShowVerticalLines(true);
    builder.setTableIntercellSpacing(new Dimension());
    myColumnTree = builder.build();

    myHelpTipPanel = myStage.getSelectedCapture().isExportable() ?
                     makeInstructionsPanel(HELP_TIP_HEADER_EXPLICIT_CAPTURE, HELP_TIP_DESCRIPTION_EXPLICIT_CAPTURE) :
                     makeInstructionsPanel(HELP_TIP_HEADER_LIVE_ALLOCATION, HELP_TIP_DESCRIPTION_LIVE_ALLOCATION);
    myPanel.add(myClassifierPanel, BorderLayout.CENTER);
  }

  private InstructionsPanel makeInstructionsPanel(String header, String desc) {
    return new InstructionsPanel.Builder(
      new TextInstruction(UIUtilities.getFontMetrics(myClassifierPanel, ProfilerFonts.H3_FONT), header),
      new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN),
      new TextInstruction(UIUtilities.getFontMetrics(myClassifierPanel, ProfilerFonts.STANDARD_FONT), desc))
      .setColors(JBColor.foreground(), null)
      .build();
  }

  private void startHeapLoadingUi() {
    if (myColumnTree == null) {
      return;
    }
    myPanel.remove(myClassifierPanel);
    myPanel.add(myLoadingPanel.getComponent(), BorderLayout.CENTER);
    myLoadingPanel.setChildComponent(myClassifierPanel);
    myLoadingPanel.startLoading();
  }

  private void stopHeapLoadingUi() {
    if (myColumnTree == null) {
      return;
    }

    myPanel.remove(myLoadingPanel.getComponent());
    myPanel.add(myClassifierPanel, BorderLayout.CENTER);
    // Loading panel is registered with the project. Be extra careful not have it reference anything when we are done with it.
    myLoadingPanel.setChildComponent(null);
    myLoadingPanel.stopLoading();
  }

  private void refreshClassifierPanel() {
    assert myTreeRoot != null && myColumnTree != null && myHelpTipPanel != null;
    myClassifierPanel.removeAll();
    if (myTreeRoot.getAdapter().isEmpty()) {
      if (myCaptureObject != null && !myCaptureObject.getSelectedInstanceFilters().isEmpty()) {
        List<String> filterNames = myCaptureObject.getSelectedInstanceFilters().stream()
          .map(CaptureObjectInstanceFilter::getDisplayName)
          .collect(Collectors.toList());
        String msg = String.format("There are no allocations satisfying selected filter%s: %s",
                                   filterNames.size() > 1 ? "s" : "",
                                   String.join(", ", filterNames));
        myClassifierPanel.add(makeInstructionsPanel(HELP_TIP_HEADER_FILTER_NO_MATCH, msg), BorderLayout.CENTER);
      }
      else {
        myClassifierPanel.add(myHelpTipPanel, BorderLayout.CENTER);
      }
    }
    else {
      myClassifierPanel.add(myColumnTree, BorderLayout.CENTER);
    }
    myClassifierPanel.revalidate();
    myClassifierPanel.repaint();
  }

  private void refreshTree() {
    if (myHeapSet == null) {
      return;
    }

    assert myTreeRoot != null && myTreeModel != null && myTree != null;
    refreshClassifierPanel();

    myTreeRoot.reset();
    myTreeRoot.expandNode();
    myTreeModel.nodeStructureChanged(myTreeRoot);

    // re-select ClassifierSet
    if (mySelectedClassifierSet != null) {
      if (!mySelectedClassifierSet.isEmpty()) {
        MemoryObjectTreeNode nodeToSelect = findSmallestSuperSetNode(myTreeRoot, mySelectedClassifierSet);
        if (nodeToSelect != null && nodeToSelect.getAdapter().equals(mySelectedClassifierSet)) {
          TreePath treePath = new TreePath(nodeToSelect.getPathToRoot().toArray());
          myTree.expandPath(treePath.getParentPath());
          myTree.setSelectionPath(treePath);
          myTree.scrollPathToVisible(treePath);
        }
        else {
          mySelectedClassifierSet = null;
        }
      }
      else {
        mySelectedClassifierSet = null;
      }
    }

    if (!myStage.getFilterHandler().getFilter().isEmpty()) {
      MemoryClassifierTreeNode treeNode = myTreeRoot;
      while (treeNode != null) {
        if (treeNode.getAdapter().getIsMatched()) {
          TreePath treePath = new TreePath(treeNode.getPathToRoot().toArray());
          myTree.expandPath(treePath.getParentPath());
          break;
        }

        treeNode.expandNode();
        myTreeModel.nodeStructureChanged(treeNode);
        MemoryClassifierTreeNode nextNode = null;
        for (MemoryObjectTreeNode<ClassifierSet> child : treeNode.getChildren()) {
          assert !child.getAdapter().getIsFiltered();
          assert child instanceof MemoryClassifierTreeNode;
          nextNode = (MemoryClassifierTreeNode)child;
          break;
        }
        treeNode = nextNode;
      }
    }
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

    // When the root is "all"-heap, hide it
    if (myHeapSet instanceof AllHeapSet) {
      myTree.setRootVisible(false);
      myTree.setShowsRootHandles(true);
    }
    else {
      myTree.setRootVisible(true);
      myTree.setShowsRootHandles(false);
    }
  }

  /**
   * Refreshes the view based on the "group by" selection from the user.
   */
  private void refreshGrouping() {
    HeapSet heapSet = myStage.getSelectedHeapSet();
    // This gets called when a capture is loading, or we change the profiler configuration.
    // During a loading capture we adjust which configurations are available and reset set the selection to the first one.
    // This triggers this callback to be fired before we have a heapset. In this scenario we just early exit.
    if (heapSet == null || myCaptureObject == null || myTree == null) {
      return;
    }

    Comparator<MemoryObjectTreeNode<ClassifierSet>> comparator = myTreeRoot == null ? myInitialComparator : myTreeRoot.getComparator();

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
      case NATIVE_ARRANGE_BY_CALLSTACK:
        headerName = "Callstack Name";
        break;
      case ARRANGE_BY_PACKAGE:
        headerName = "Package Name";
        break;
      case NATIVE_ARRANGE_BY_ALLOCATION_METHOD:
        headerName = "Allocation function";
        break;
    }
    assert myTableColumnModel != null;
    myTableColumnModel.getColumn(0).setHeaderValue(headerName);

    // Attempt to reselect the previously selected ClassSet node or FieldPath.
    ClassSet selectedClassSet = myStage.getSelectedClassSet();
    InstanceObject selectedInstance = myStage.getSelectedInstanceObject();
    List<FieldObject> fieldPath = myStage.getSelectedFieldObjectPath();

    refreshClassifierPanel();

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
    if (myClassSet != null && !myClassSet.isEmpty()) {
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

    if (myClassSet == null) {
      mySelectedClassifierSet = null;
      myTree.clearSelection();
    }
  }

  @NotNull
  @VisibleForTesting
  ColoredTreeCellRenderer getModuleColumnRenderer() {
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
        if (node.getAdapter() instanceof NativeCallStackSet) {
          NativeCallStackSet set = (NativeCallStackSet)node.getAdapter();
          String name = set.getModuleName();
          if (!Strings.isNullOrEmpty(name) && name.contains("/")) {
            name = name.substring(name.lastIndexOf("/") + 1);
            append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES, name);
          }
        }
        setTextAlign(SwingConstants.LEFT);
      }
    };
  }

  @NotNull
  @VisibleForTesting
  ColoredTreeCellRenderer getNameColumnRenderer() {
    return new ColoredTreeCellRenderer() {
      private long myLeakCount = 0;

      @Override
      protected void paintComponent(Graphics g) {
        if (myLeakCount > 0) {
          int width = getWidth();
          int height = getHeight();

          Icon i = StudioIcons.Common.WARNING;
          int iconWidth = i.getIconWidth();
          int iconHeight = i.getIconHeight();
          i.paintIcon(this, g, width - iconWidth, (height - iconHeight) / 2);

          String text = String.valueOf(myLeakCount);
          ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
          int textWidth = g.getFontMetrics().stringWidth(text);
          g.drawString(text, width - iconWidth - textWidth - 4, (height + iconHeight) / 2 - 1);
        }
        // paint real content last
        super.paintComponent(g);
      }

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

          setIcon(((ClassSet)node.getAdapter()).hasStackInfo() ? StudioIcons.Profiler.Overlays.CLASS_STACK : PlatformIcons.CLASS_ICON);

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
          setIcon(set.hasStackInfo() ? StudioIcons.Profiler.Overlays.PACKAGE_STACK : PlatformIcons.PACKAGE_ICON);
          String name = set.getName();
          append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES, name);
        }
        else if (node.getAdapter() instanceof MethodSet) {
          setIcon(PlatformIcons.METHOD_ICON);

          MethodSet methodObject = (MethodSet)node.getAdapter();
          String name = methodObject.getMethodName();
          String className = methodObject.getClassName();

          String nameAndLine = name + "()";
          append(nameAndLine, SimpleTextAttributes.REGULAR_ATTRIBUTES, nameAndLine);

          if (!Strings.isNullOrEmpty(className)) {
            String classNameText = " (" + className + ")";
            append(classNameText, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES, classNameText);
          }
        }
        else if (node.getAdapter() instanceof ThreadSet) {
          setIcon(AllIcons.Debugger.ThreadSuspended);
          String threadName = node.getAdapter().getName();
          append(threadName, SimpleTextAttributes.REGULAR_ATTRIBUTES, threadName);
        }
        else if (node.getAdapter() instanceof HeapSet) {
          ClassifierSet set = (ClassifierSet)node.getAdapter();
          setIcon(set.hasStackInfo() ? StudioIcons.Profiler.Overlays.PACKAGE_STACK : PlatformIcons.PACKAGE_ICON);
          String name = set.getName() + " heap";
          append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES, name);
        }
        else if (node.getAdapter() instanceof NativeCallStackSet) {
          ClassifierSet set = (ClassifierSet)node.getAdapter();
          setIcon(StudioIcons.Profiler.Overlays.METHOD_STACK);
          String name = set.getName();
          append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES, name);
        }
        else if (node.getAdapter() instanceof NativeAllocationMethodSet) {
          ClassifierSet set = (ClassifierSet)node.getAdapter();
          setIcon(StudioIcons.Profiler.Overlays.ARRAY_STACK);
          String name = set.getName();
          append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES, name);
        }

        if (node.getAdapter() instanceof ClassifierSet) {
          CaptureObjectInstanceFilter leakFilter = myCaptureObject.getActivityFragmentLeakFilter();
          myLeakCount = leakFilter != null ?
                        ((ClassifierSet)node.getAdapter()).getInstanceFilterMatchCount(leakFilter) :
                        0;
        }
        setTextAlign(SwingConstants.LEFT);
      }
    };
  }

  private static Comparator<MemoryObjectTreeNode<ClassifierSet>> createTreeNodeComparator(
    @NotNull Comparator<NativeCallStackSet> comparator) {
    return (o1, o2) -> {
      ClassifierSet firstArg = o1.getAdapter();
      ClassifierSet secondArg = o2.getAdapter();
      if (firstArg instanceof NativeCallStackSet && secondArg instanceof NativeCallStackSet) {
        return comparator.compare((NativeCallStackSet)firstArg, (NativeCallStackSet)secondArg);
      }
      else {
        return 0;
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
  private static Comparator<MemoryObjectTreeNode<ClassifierSet>> createTreeNodeComparator(
    @NotNull Comparator<ClassifierSet> classifierSetComparator, @NotNull Comparator<ClassSet> classSetComparator) {
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
