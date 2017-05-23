/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.structure;

import com.android.tools.idea.uibuilder.model.*;
import com.google.common.collect.ImmutableList;
import com.intellij.util.enumeration.EmptyEnumeration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@SuppressWarnings("ConstantConditions")
public class NlDropInsertionPickerTest {

  public static final int NODE_SIZE = 10;
  public static final int COMPONENT_NUMBER = 10;
  public static final int LAST_COMPONENT_Y_POSITION = COMPONENT_NUMBER * NODE_SIZE - NODE_SIZE / 2;

  @Mock
  private NlModel myModel;

  private DummyComponentGroup ourRoot;
  private DummyTreePath[] myTreePaths;
  private ImmutableList<NlComponent> myDragged;
  private NlDropInsertionPicker myPicker;

  @NotNull
  private DummyComponentGroup buildDummyComponentHierarchy() {
    return new DummyComponentGroup(0, myModel, false)
      .setChildren(
        new DummyComponent(1, myModel, true),
        new DummyComponent(2, myModel, true),
        new DummyComponentGroup(3, myModel, true)
          .setChildren(
            new DummyComponent(4, myModel, true),
            new DummyComponent(5, myModel, false)),
        new DummyComponentGroup(6, myModel, false)
          .setChildren(
            new DummyComponent(7, myModel, true),
            new DummyComponentGroup(8, myModel, true),
            new DummyComponent(9, myModel, false))
      );
  }

  /**
   * Build a list of tree path with mock coordinates using from the given component hierarchy as they would be in the {@link NlComponentTree}
   */
  @NotNull
  private DummyTreePath[] buildDummyTreePathArray(@NotNull DummyComponent root) {

    ImmutableList.Builder<DummyTreePath> builder = ImmutableList.builder();
    buildDummyTreePathArray(builder, root, 0, -1, 0);
    ImmutableList<DummyTreePath> pathsList = builder.build();
    return pathsList.toArray(new DummyTreePath[pathsList.size()]);
  }

  /**
   * Build a list of tree path with mock coordinates using from the given component hierarchy as they would be in the {@link NlComponentTree}
   *
   * @param builder      The builder used to build the list
   * @param current      The current {@link DummyComponent} to add to the list
   * @param currentRow   The row of the current component in the Tree
   * @param parentRow    This component's parent's row number
   * @param currentDepth The current depth of the component (used to compute the x coordinate of the component)
   * @return The row of the previously inserted {@link DummyTreePath}
   */
  private int buildDummyTreePathArray(@NotNull ImmutableList.Builder<DummyTreePath> builder,
                                      @NotNull DummyComponent current,
                                      int currentRow, int parentRow,
                                      int currentDepth) {
    builder.add(new DummyTreePath(current, parentRow, currentDepth * NODE_SIZE));
    int childRow = currentRow + 1;
    if (current instanceof DummyComponentGroup) {
      for (int i = 0; i < current.getChildCount(); i++) {
        childRow =
          buildDummyTreePathArray(builder, (DummyComponent)current.getChildren().get(i), childRow, currentRow, currentDepth + 1);
      }
    }
    return childRow;
  }

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(myModel.canAddComponents(anyList(), any(DummyComponent.class), any())).thenReturn(false);
    when(myModel.canAddComponents(anyList(), any(DummyComponentGroup.class), any())).thenReturn(true);

    ourRoot = buildDummyComponentHierarchy();
    myTreePaths = buildDummyTreePathArray(ourRoot);

    myPicker = getDefaultPicker();
    myDragged = ImmutableList.of(new DummyComponent(-1, myModel, false));
  }

  @Test
  public void testDummyTree() throws Exception {
    DummyTree tree = new DummyTree();
    assertEquals(myTreePaths[0], tree.getClosestPathForLocation(0, 0));
    assertEquals(ourRoot, tree.getClosestPathForLocation(0, 0).getLastPathComponent());
    assertEquals(myTreePaths[1], tree.getClosestPathForLocation(0, 10));
    assertEquals(ourRoot.getChild(0), tree.getClosestPathForLocation(0, 10).getLastPathComponent());
    assertEquals(ourRoot.getChild(1), tree.getClosestPathForLocation(0, 20).getLastPathComponent());
    assertEquals(ourRoot.getChild(3).getChild(2), tree.getClosestPathForLocation(0, 90).getLastPathComponent());
    assertEquals(myTreePaths[3], myTreePaths[4].getParentPath());
    assertTrue(myTreePaths[myTreePaths.length - 1].getLastPathComponent() instanceof DummyComponent);
    assertEquals(((DummyComponent)myTreePaths[myTreePaths.length - 1].getLastPathComponent()).myId, myTreePaths.length - 1);
  }

  @Test
  public void testInsertAtRoot() throws Exception {
    NlDropInsertionPicker.Result result = myPicker.findInsertionPointAt(new Point(0, 0), myDragged);
    assertEquals(ourRoot, result.receiver);
    assertEquals(ourRoot.getChild(0), result.nextComponent);
    assertEquals(1, result.depth);
    assertEquals(0, result.row);
  }

  @Test
  public void testInsertLast() throws Exception {

    NlDropInsertionPicker.Result result = myPicker.findInsertionPointAt(new Point(30, LAST_COMPONENT_Y_POSITION), myDragged);
    assertEquals(ourRoot.getChild(3), result.receiver);
    assertNull(result.nextComponent);
    assertEquals(0, result.depth);
    assertEquals(COMPONENT_NUMBER - 1, result.row);
  }

  @Test
  public void testInsertParentFromLast() throws Exception {
    NlDropInsertionPicker.Result result = myPicker.findInsertionPointAt(new Point(5, LAST_COMPONENT_Y_POSITION), myDragged);
    assertEquals(ourRoot, result.receiver);
    assertNull(result.nextComponent);
    assertEquals(-1, result.depth);
    assertEquals(COMPONENT_NUMBER - 1, result.row);
  }

  @Test
  public void testInsertInViewGroup() throws Exception {
    NlDropInsertionPicker.Result result = myPicker.findInsertionPointAt(new Point(15, 35), myDragged);
    assertEquals(ourRoot.getChild(2), result.receiver);
    assertEquals(ourRoot.getChild(2).getChild(0), result.nextComponent);
    assertEquals(1, result.depth);
    assertEquals(3, result.row);
  }

  @Test
  public void testInsertInEmptyViewGroup() throws Exception {
    NlDropInsertionPicker.Result result = myPicker.findInsertionPointAt(new Point(15, 85), myDragged);
    assertEquals(ourRoot.getChild(3).getChild(1), result.receiver);
    assertNull(result.nextComponent);
    assertEquals(1, result.depth);
    assertEquals(8, result.row);
  }

  @Test
  public void testInsertBetween() throws Exception {
    NlDropInsertionPicker.Result result = myPicker.findInsertionPointAt(new Point(15, 15), myDragged);
    assertEquals(ourRoot, result.receiver);
    assertEquals(ourRoot.getChild(1), result.nextComponent);
    assertEquals(0, result.depth);
    assertEquals(1, result.row);
  }

  @Test
  public void testInsertInParentIfLastIsViewGroup() throws Exception {
    DummyComponentGroup root =
      new DummyComponentGroup(0, myModel, false).setChildren(
        new DummyComponent(1, myModel, true),
        new DummyComponentGroup(2, myModel, false));

    myTreePaths = buildDummyTreePathArray(root);
    myTreePaths[myTreePaths.length - 1].setCollapsed();
    DummyTree tree = new DummyTree();
    NlDropInsertionPicker picker = new NlDropInsertionPicker(tree);
    NlDropInsertionPicker.Result result = picker.findInsertionPointAt(new Point(5, 25), myDragged);
    assertEquals(root, result.receiver);
    assertNull(result.nextComponent);
    assertEquals(0, result.depth);
    assertEquals(2, result.row);
  }

  @NotNull
  private NlDropInsertionPicker getDefaultPicker() {
    DummyTree tree = new DummyTree();
    return new NlDropInsertionPicker(tree);
  }

  /* ***********************************************************************/
  /* ************************ DUMMY CLASSES ********************************/
  /* ***********************************************************************/

  /**
   * Dummy JTree
   */
  private class DummyTree extends JTree {

    @Override
    public TreePath getClosestPathForLocation(int x, int y) {
      return myTreePaths[Math.max(0, Math.min(myTreePaths.length - 1, y / NODE_SIZE))];
    }

    @Override
    public int getRowForPath(@NotNull TreePath path) {
      for (int i = 0; i < myTreePaths.length; i++) {
        if (myTreePaths[i] == path) {
          return i;
        }
      }
      return -1;
    }

    @Nullable
    @Override
    public TreePath getPathForRow(int row) {
      if (row < 0 || row >= myTreePaths.length) {
        return null;
      }
      return myTreePaths[row];
    }

    @Nullable
    @Override
    public Rectangle getPathBounds(@NotNull TreePath path) {
      for (int i = 0; i < myTreePaths.length; i++) {
        if (myTreePaths[i] == path) {
          return new Rectangle(myTreePaths[i].myPosition, i * NODE_SIZE, NODE_SIZE, NODE_SIZE);
        }
      }
      return null;
    }

    @Override
    public int getRowCount() {
      return myTreePaths.length;
    }

    @Nullable
    @Override
    public Enumeration<TreePath> getExpandedDescendants(@NotNull TreePath parent) {
      Object lastPathComponent = parent.getLastPathComponent();
      if (lastPathComponent instanceof DummyComponentGroup
          && ((DummyTreePath)parent).isExpanded()
          && ((DummyComponentGroup)lastPathComponent).getChildCount() > 0) {
        return EmptyEnumeration.getInstance();
      }
      return null;
    }
  }

  /**
   * Dummy NlComponent
   */
  private class DummyComponent extends NlComponent {
    private final boolean mySibling;
    private final int myId;
    @Nullable private DummyComponentGroup myDummyParent = null;

    public DummyComponent(int id, @NotNull NlModel model, boolean hasSibling) {
      super(model, new EmptyXmlTag());
      mySibling = hasSibling;
      myId = id;
    }

    @Nullable
    @Override
    public NlComponent getNextSibling() {
      if (!mySibling) {
        return null;
      }

      int index = -1;
      for (int i = 0; i < myTreePaths.length; i++) {
        if (myTreePaths[i].getLastPathComponent() == this) {
          index = i;
          break;
        }
      }
      if (index < 0 || index == myTreePaths.length - 1
          || (myTreePaths[index + 1].myParentRow != myTreePaths[index].myParentRow)) {
        return null;
      }
      return (NlComponent)myTreePaths[index + 1].getLastPathComponent();
    }

    @Nullable
    @Override
    public NlComponent getParent() {
      return myDummyParent;
    }

    public void setDummyParent(@Nullable DummyComponentGroup parent) {
      myDummyParent = parent;
    }

    @NotNull
    @Override
    public String toString() {
      return "#" + myId;
    }
  }

  /**
   * Dummy component to mock a ViewGroup
   */
  private class DummyComponentGroup extends DummyComponent {
    @NotNull private DummyComponent[] myChildren = new DummyComponent[0];

    public DummyComponentGroup(int i, @NotNull NlModel model, boolean hasSibling) {
      super(i, model, hasSibling);
    }

    @NotNull
    public DummyComponentGroup setChildren(@NotNull DummyComponent... children) {
      myChildren = children;
      for (DummyComponent aMyChildren : myChildren) {
        aMyChildren.setDummyParent(this);
      }
      return this;
    }

    @Override
    public int getChildCount() {
      return myChildren.length;
    }

    @Nullable
    @Override
    public NlComponent getChild(int index) {
      return index > myChildren.length - 1 ? null : myChildren[index];
    }

    @NotNull
    @Override
    public List<NlComponent> getChildren() {
      return Arrays.asList(myChildren);
    }
  }

  /**
   * Dummy TreePath
   */
  private class DummyTreePath extends TreePath {

    private final int myPosition;
    private int myParentRow;
    private boolean myIsExpanded = true;

    /**
     * @param parentRow index of the parent of the this {@link DummyTreePath} in {@link NlDropInsertionPickerTest#myTreePaths}
     * @param xPosition Mock x coordinate of the node in the tree.
     *                  0 is the root, and we increment by 10 for each deeper level
     */
    public DummyTreePath(@NotNull NlComponent lastComponent, int parentRow, int xPosition) {
      super(lastComponent);
      myPosition = xPosition;
      myParentRow = parentRow;
    }

    @Nullable
    @Override
    public TreePath getParentPath() {
      return myParentRow < 0 ? null : myTreePaths[myParentRow];
    }

    public boolean isExpanded() {
      return myIsExpanded;
    }

    public void setCollapsed() {
      myIsExpanded = false;
    }
  }
}
