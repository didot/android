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
import static com.android.tools.profilers.memory.adapters.MemoryObject.INVALID_VALUE;

import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.StreamingTimeline;
import com.android.tools.adtui.model.formatter.NumberFormatter;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.adtui.stdui.CommonTabbedPane;
import com.android.tools.adtui.stdui.StandardColors;
import com.android.tools.profilers.ContextMenuInstaller;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.CaptureObject.InstanceAttribute;
import com.android.tools.profilers.memory.adapters.ClassSet;
import com.android.tools.profilers.memory.adapters.ClassifierSet;
import com.android.tools.profilers.memory.adapters.FieldObject;
import com.android.tools.profilers.memory.adapters.HeapSet;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.android.tools.profilers.memory.adapters.MemoryObject;
import com.android.tools.profilers.memory.adapters.ReferenceObject;
import com.android.tools.profilers.memory.adapters.ValueObject;
import com.android.tools.profilers.memory.instanceviewers.BitmapViewer;
import com.android.tools.profilers.memory.instanceviewers.InstanceViewer;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import com.android.tools.profilers.stacktrace.StackTraceView;
import com.google.common.annotations.VisibleForTesting;
import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A view object that is responsible for displaying the callstack + references of an {@link InstanceObject} based on whether the
 * information is available. If no detailed information can be obtained from the InstanceObject, this UI is responsible
 * for automatically hiding itself.
 */
final class MemoryInstanceDetailsView extends AspectObserver {
  private static final String TITLE_TAB_REFERENCES = "References";
  private static final String TITLE_TAB_ALLOCATION_CALLSTACK = "Allocation Call Stack";
  private static final String TITLE_TAB_DEALLOCATION_CALLSTACK = "Deallocation Call Stack";
  private static final int LABEL_COLUMN_WIDTH = 500;
  private static final int DEFAULT_COLUMN_WIDTH = 80;

  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private final StreamingTimeline myTimeline;

  @NotNull private final IdeProfilerComponents myIdeProfilerComponents;

  @NotNull private final JTabbedPane myTabsPanel;

  @NotNull private final StackTraceView myAllocationStackTraceView;

  @NotNull private final StackTraceView myDeallocationStackTraceView;

  @Nullable private JComponent myReferenceColumnTree;

  @Nullable private JTree myReferenceTree;

  @NotNull private final Map<InstanceAttribute, AttributeColumn> myAttributeColumns = new HashMap<>();

  @NotNull private final List<InstanceViewer> myInstanceViewers = new ArrayList<>();

  MemoryInstanceDetailsView(@NotNull MemoryProfilerStage stage, @NotNull IdeProfilerComponents ideProfilerComponents) {
    myStage = stage;
    myTimeline = myStage.getTimeline();
    myStage.getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.CURRENT_INSTANCE, this::instanceChanged)
      .onChange(MemoryProfilerAspect.CURRENT_FIELD_PATH, this::instanceChanged);
    myIdeProfilerComponents = ideProfilerComponents;

    myTabsPanel = new CommonTabbedPane();
    myTabsPanel.addChangeListener(this::trackActiveTab);
    myAllocationStackTraceView = ideProfilerComponents.createStackView(stage.getAllocationStackTraceModel());
    myDeallocationStackTraceView = ideProfilerComponents.createStackView(stage.getDeallocationStackTraceModel());

    myInstanceViewers.add(new BitmapViewer());

    myAttributeColumns.put(
      InstanceAttribute.LABEL,
      new AttributeColumn<ValueObject>(
        "Reference",
        () -> new SimpleColumnRenderer<ValueObject>(
          node -> {
            StringBuilder builder = new StringBuilder();
            if (node.getAdapter() instanceof ReferenceObject) {
              builder.append(node.getAdapter().getName());
              builder.append(" in ");
            }
            builder.append(node.getAdapter().getValueText());
            return builder.toString();
          },
          value -> MemoryProfilerStageView.getValueObjectIcon(value.getAdapter()),
          SwingConstants.LEFT),
        SwingConstants.LEFT,
        LABEL_COLUMN_WIDTH,
        SortOrder.ASCENDING,
        Comparator.comparing(o -> (o.getAdapter()).getName())));
    myAttributeColumns.put(
      InstanceAttribute.DEPTH,
      new AttributeColumn<ValueObject>(
        "Depth",
        () -> new SimpleColumnRenderer<ValueObject>(value -> {
          int depth = value.getAdapter().getDepth();
          if (depth >= 0 && depth < Integer.MAX_VALUE) {
            return NumberFormatter.formatInteger(depth);
          }
          return "";
        }, value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.ASCENDING,
        Comparator.comparingInt(o -> o.getAdapter().getDepth())));
    myAttributeColumns.put(
      InstanceAttribute.ALLOCATION_TIME,
      new AttributeColumn<>(
        "Alloc Time",
        () -> new SimpleColumnRenderer<>(value -> {
          MemoryObject node = value.getAdapter();
          if (node instanceof InstanceObject) {
            InstanceObject instanceObject = (InstanceObject)node;
            if (instanceObject.getAllocTime() > Long.MIN_VALUE) {
              return TimeFormatter.getSemiSimplifiedClockString(myTimeline.convertToRelativeTimeUs(instanceObject.getAllocTime()));
            }
          }
          return "";
        }, value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.ASCENDING,
        Comparator.comparingLong(o -> ((InstanceObject)o.getAdapter()).getAllocTime())));
    myAttributeColumns.put(
      InstanceAttribute.DEALLOCATION_TIME,
      new AttributeColumn<>(
        "Dealloc Time",
        () -> new SimpleColumnRenderer<>(value -> {
          MemoryObject node = value.getAdapter();
          if (node instanceof InstanceObject) {
            InstanceObject instanceObject = (InstanceObject)node;
            if (instanceObject.getDeallocTime() < Long.MAX_VALUE) {
              return TimeFormatter.getSemiSimplifiedClockString(myTimeline.convertToRelativeTimeUs(instanceObject.getDeallocTime()));
            }
          }
          return "";
        }, value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.DESCENDING,
        Comparator.comparingLong(o -> ((InstanceObject)o.getAdapter()).getDeallocTime())));
    myAttributeColumns.put(
      InstanceAttribute.NATIVE_SIZE,
      new AttributeColumn<ValueObject>(
        "Native Size",
        () -> new SimpleColumnRenderer<ValueObject>(
          value -> value.getAdapter().getNativeSize() != INVALID_VALUE
                   ? NumberFormatter.formatInteger(value.getAdapter().getNativeSize())
                   : "",
          value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.DESCENDING,
        Comparator.comparingLong(o -> o.getAdapter().getNativeSize())));
    myAttributeColumns.put(
      InstanceAttribute.SHALLOW_SIZE,
      new AttributeColumn<ValueObject>(
        "Shallow Size",
        () -> new SimpleColumnRenderer<ValueObject>(
          value -> value.getAdapter().getShallowSize() != INVALID_VALUE
                   ? NumberFormatter.formatInteger(value.getAdapter().getShallowSize())
                   : "",
          value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.DESCENDING,
        Comparator.comparingInt(o -> o.getAdapter().getShallowSize())));
    myAttributeColumns.put(
      InstanceAttribute.RETAINED_SIZE,
      new AttributeColumn<ValueObject>(
        "Retained Size",
        () -> new SimpleColumnRenderer<ValueObject>(
          value -> value.getAdapter().getRetainedSize() != INVALID_VALUE ? NumberFormatter
            .formatInteger(value.getAdapter().getRetainedSize()) : "",
          value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.DESCENDING,
        Comparator.comparingLong(o -> o.getAdapter().getRetainedSize())));

    // Fires the handler once at the beginning to ensure we are sync'd with the latest selection state in the MemoryProfilerStage.
    instanceChanged();
  }

  private void trackActiveTab(ChangeEvent event) {
    if (myTabsPanel.getSelectedIndex() < 0) {
      return;
    }

    FeatureTracker featureTracker = myStage.getStudioProfilers().getIdeServices().getFeatureTracker();
    switch (myTabsPanel.getTitleAt(myTabsPanel.getSelectedIndex())) {
      case TITLE_TAB_REFERENCES:
        featureTracker.trackSelectMemoryReferences();
        break;
      case TITLE_TAB_ALLOCATION_CALLSTACK:
      case TITLE_TAB_DEALLOCATION_CALLSTACK:
        featureTracker.trackSelectMemoryStack();
        break;
      default:
        // Intentional no-op
        break;
    }
  }

  @NotNull
  JComponent getComponent() {
    return myTabsPanel;
  }

  @VisibleForTesting
  @Nullable
  JTree getReferenceTree() {
    return myReferenceTree;
  }

  @VisibleForTesting
  @Nullable
  JComponent getReferenceColumnTree() {
    return myReferenceColumnTree;
  }

  private void instanceChanged() {
    CaptureObject capture = myStage.getSelectedCapture();
    InstanceObject instance = myStage.getSelectedInstanceObject();
    List<FieldObject> fieldPath = myStage.getSelectedFieldObjectPath();

    if (capture == null || instance == null) {
      myReferenceTree = null;
      myReferenceColumnTree = null;
      myTabsPanel.setVisible(false);
      return;
    }

    myTabsPanel.removeAll();
    boolean hasContent = false;

    if (!fieldPath.isEmpty()) {
      InstanceObject fieldInstance = fieldPath.get(fieldPath.size() - 1).getAsInstance();
      if (fieldInstance != null) {
        instance = fieldInstance;
      }
    }

    // Populate references
    myReferenceColumnTree = buildReferenceColumnTree(capture, instance);
    if (myReferenceColumnTree != null) {
      myTabsPanel.addTab(TITLE_TAB_REFERENCES, myReferenceColumnTree);
      hasContent = true;
    }

    // Populate Callstacks
    List<CodeLocation> allocCallStack = instance.getAllocationCodeLocations();
    if (!allocCallStack.isEmpty()) {
      myAllocationStackTraceView.getModel().setStackFrames(instance.getAllocationThreadId(), allocCallStack);
      JComponent stackTraceView = myAllocationStackTraceView.getComponent();
      stackTraceView.setBorder(DEFAULT_TOP_BORDER);
      myTabsPanel.addTab(TITLE_TAB_ALLOCATION_CALLSTACK, stackTraceView);
      hasContent = true;
    }

    List<CodeLocation> deallocCallStack = instance.getDeallocationCodeLocations();
    if (!deallocCallStack.isEmpty()) {
      myDeallocationStackTraceView.getModel().setStackFrames(instance.getDeallocationThreadId(), deallocCallStack);
      JComponent stackTraceView = myDeallocationStackTraceView.getComponent();
      stackTraceView.setBorder(DEFAULT_TOP_BORDER);
      myTabsPanel.addTab(TITLE_TAB_DEALLOCATION_CALLSTACK, stackTraceView);
      hasContent = true;
    }

    final InstanceObject finalInstance = instance;
    myInstanceViewers.forEach(viewer -> {
      JComponent component = viewer.createComponent(myIdeProfilerComponents, capture, finalInstance);
      if (component != null) {
        myTabsPanel.addTab(viewer.getTitle(), component);
      }
    });

    myTabsPanel.setVisible(hasContent);
  }

  @Nullable
  private JComponent buildReferenceColumnTree(@NotNull CaptureObject captureObject, @NotNull InstanceObject instance) {
    if (instance.getReferences().isEmpty()) {
      myReferenceTree = null;
      return null;
    }

    myReferenceTree = buildTree(instance);
    ColumnTreeBuilder builder = new ColumnTreeBuilder(myReferenceTree);
    for (InstanceAttribute attribute : captureObject.getInstanceAttributes()) {
      ColumnTreeBuilder.ColumnBuilder column = myAttributeColumns.get(attribute).getBuilder();
      if (attribute == InstanceAttribute.DEPTH) {
        column.setInitialOrder(attribute.getSortOrder());
      }
      builder.addColumn(column);
    }
    builder.setTreeSorter((Comparator<MemoryObjectTreeNode<MemoryObject>> comparator, SortOrder sortOrder) -> {
      assert myReferenceTree.getModel() instanceof DefaultTreeModel;
      DefaultTreeModel treeModel = (DefaultTreeModel)myReferenceTree.getModel();
      assert treeModel.getRoot() instanceof MemoryObjectTreeNode;
      //noinspection unchecked
      MemoryObjectTreeNode<MemoryObject> root = (MemoryObjectTreeNode<MemoryObject>)treeModel.getRoot();
      root.sort(comparator);
      treeModel.nodeStructureChanged(root);
    });
    builder.setHoverColor(StandardColors.HOVER_COLOR);
    builder.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    builder.setBorder(DEFAULT_TOP_BORDER);
    builder.setShowVerticalLines(true);
    builder.setTableIntercellSpacing(new Dimension());
    return builder.build();
  }

  @VisibleForTesting
  @NotNull
  JTree buildTree(@NotNull InstanceObject instance) {
    Comparator<MemoryObjectTreeNode<ValueObject>> comparator = null;
    if (myReferenceTree != null && myReferenceTree.getModel() != null && myReferenceTree.getModel().getRoot() != null) {
      Object root = myReferenceTree.getModel().getRoot();
      if (root instanceof ReferenceTreeNode) {
        comparator = ((ReferenceTreeNode)root).getComparator();
      }
    }

    final ReferenceTreeNode treeRoot = new ReferenceTreeNode(instance);
    treeRoot.expandNode();

    if (comparator != null) {
      treeRoot.sort(comparator);
    }

    final DefaultTreeModel treeModel = new DefaultTreeModel(treeRoot);
    // Use JTree instead of IJ's tree, because IJ's tree does not happen border's Insets.
    final JTree tree = new JTree(treeModel);
    int defaultFontHeight = tree.getFontMetrics(tree.getFont()).getHeight();
    tree.setRowHeight(defaultFontHeight + ROW_HEIGHT_PADDING);
    tree.setBorder(TABLE_ROW_BORDER);
    tree.setRootVisible(true);
    tree.setShowsRootHandles(true);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    // Not all nodes have been populated during buildReferenceColumnTree. Here we capture the TreeExpansionEvent to check whether any children
    // under the expanded node need to be populated.
    tree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        TreePath path = event.getPath();

        assert path.getLastPathComponent() instanceof MemoryObjectTreeNode;
        ReferenceTreeNode treeNode = (ReferenceTreeNode)path.getLastPathComponent();
        treeNode.expandNode();
        treeModel.nodeStructureChanged(treeNode);
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
      }
    });

    ContextMenuInstaller contextMenuInstaller = myIdeProfilerComponents.createContextMenuInstaller();
    contextMenuInstaller.installNavigationContextMenu(tree, myStage.getStudioProfilers().getIdeServices().getCodeNavigator(), () -> {
      TreePath selection = tree.getSelectionPath();
      if (selection == null) {
        return null;
      }

      MemoryObject memoryObject = ((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter();
      if (memoryObject instanceof InstanceObject) {
        return new CodeLocation.Builder(((InstanceObject)memoryObject).getClassEntry().getClassName()).build();
      }
      else {
        assert memoryObject instanceof ReferenceObject;
        return new CodeLocation.Builder(((ReferenceObject)memoryObject).getReferenceInstance().getClassEntry().getClassName()).build();
      }
    });

    contextMenuInstaller.installGenericContextMenu(tree, new ContextMenuItem() {
      @NotNull
      @Override
      public String getText() {
        return "Go to Instance";
      }

      @Nullable
      @Override
      public Icon getIcon() {
        return null;
      }

      @Override
      public boolean isEnabled() {
        return tree.getSelectionPath() != null;
      }

      @Override
      public void run() {
        CaptureObject captureObject = myStage.getSelectedCapture();
        TreePath selection = tree.getSelectionPath();
        assert captureObject != null && selection != null;
        MemoryObject memoryObject = ((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter();
        if (memoryObject instanceof InstanceObject) {
          assert memoryObject == myStage.getSelectedInstanceObject();
          // don't do anything because the only instance object in the tree is the one already selected
        }
        else {
          assert memoryObject instanceof ReferenceObject;
          InstanceObject targetInstance = ((ReferenceObject)memoryObject).getReferenceInstance();
          HeapSet heapSet = captureObject.getHeapSet(targetInstance.getHeapId());
          assert heapSet != null;
          myStage.selectHeapSet(heapSet);
          ClassifierSet classifierSet = heapSet.findContainingClassifierSet(targetInstance);
          assert classifierSet instanceof ClassSet;
          myStage.selectClassSet((ClassSet)classifierSet);
          myStage.selectInstanceObject(targetInstance);
        }
      }
    });

    tree.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (tree.getSelectionCount() == 0 && tree.getRowCount() != 0) {
          tree.setSelectionRow(0);
        }
      }
    });

    return tree;
  }

  private static class ReferenceTreeNode extends LazyMemoryObjectTreeNode<ValueObject> {
    @Nullable
    private List<ReferenceObject> myReferenceObjects = null;

    private ReferenceTreeNode(@NotNull ValueObject valueObject) {
      super(valueObject, false);
    }

    @Override
    public int computeChildrenCount() {
      if (myReferenceObjects == null) {
        if (getAdapter() instanceof InstanceObject) {
          myReferenceObjects = ((InstanceObject)getAdapter()).getReferences();
        }
        else if (getAdapter() instanceof ReferenceObject) {
          myReferenceObjects = ((ReferenceObject)getAdapter()).getReferenceInstance().getReferences();
        }
        else {
          myReferenceObjects = Collections.emptyList();
        }
      }

      return myReferenceObjects.size();
    }

    @Override
    public void expandNode() {
      getChildCount(); // ensure we grab all the references
      assert myReferenceObjects != null;
      if (myMemoizedChildrenCount != myChildren.size()) {
        myReferenceObjects.forEach(reference -> {
          ReferenceTreeNode node = new ReferenceTreeNode(reference);
          node.setTreeModel(getTreeModel());
          add(node);
        });
      }
    }
  }
}
