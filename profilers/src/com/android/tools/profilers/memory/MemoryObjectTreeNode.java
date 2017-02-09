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

import com.android.tools.profilers.memory.adapters.MemoryObject;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

/**
 * Model class that represents each row in the memory allocation table view.
 * TODO we should merge this with what's used in the current studio allocation view once we finalize on the device data structure.
 * e.g. {@link com.android.tools.idea.editors.allocations.nodes.AllocNode}
 */
public class MemoryObjectTreeNode<T extends MemoryObject> implements MutableTreeNode {
  @Nullable protected MemoryObjectTreeNode<T> myParent;

  @NotNull protected List<MemoryObjectTreeNode<T>> myChildren = new ArrayList<>();

  @Nullable protected Comparator<MemoryObjectTreeNode<T>> myComparator = null;

  @NotNull private final T myAdapter;

  public MemoryObjectTreeNode(@NotNull T adapter) {
    myAdapter = adapter;
  }

  @Override
  public TreeNode getChildAt(int i) {
    ensureOrder();
    return myChildren.get(i);
  }

  @Override
  public int getChildCount() {
    return myChildren.size();
  }

  @Override
  public TreeNode getParent() {
    return myParent;
  }

  @Override
  public int getIndex(TreeNode treeNode) {
    assert treeNode instanceof MemoryObjectTreeNode;
    return myChildren.indexOf(treeNode);
  }

  @Override
  public boolean isLeaf() {
    return myChildren.size() == 0;
  }

  @Override
  public Enumeration children() {
    ensureOrder();
    return Collections.enumeration(myChildren);
  }

  @NotNull
  public ImmutableList<MemoryObjectTreeNode<T>> getChildren() {
    return ContainerUtil.immutableList(myChildren);
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  public void add(@NotNull MemoryObjectTreeNode child) {
    insert(child, myChildren.size());
  }

  @Override
  public void insert(MutableTreeNode newChild, int childIndex) {
    assert newChild instanceof MemoryObjectTreeNode;
    MemoryObjectTreeNode child = (MemoryObjectTreeNode)newChild;
    if (child.myParent != null) {
      child.myParent.remove(child);
    }
    child.myParent = this;
    myChildren.add(childIndex, child);
  }

  @Override
  public void remove(int childIndex) {
    MemoryObjectTreeNode child = myChildren.get(childIndex);
    child.myParent = null;
    myChildren.remove(childIndex);
  }

  @Override
  public void remove(MutableTreeNode node) {
    assert node instanceof MemoryObjectTreeNode;
    ((MemoryObjectTreeNode)node).myParent = null;
    myChildren.remove(node);
  }

  public void removeAll() {
    myChildren.forEach(child -> child.myParent = null);
    myChildren.clear();
  }

  @Override
  public void setUserObject(Object object) {
    throw new RuntimeException("Not implemented, use setData/getAdapter instead.");
  }

  @NotNull
  public T getAdapter() {
    return myAdapter;
  }

  @Override
  public void removeFromParent() {
    if (myParent != null) {
      myParent.remove(this);
    }
  }

  @Override
  public void setParent(@Nullable MutableTreeNode newParent) {
    removeFromParent();
    if (newParent instanceof MemoryObjectTreeNode) {
      myParent = (MemoryObjectTreeNode<T>)newParent;
    }
  }

  public void sort(@NotNull Comparator<MemoryObjectTreeNode<T>> comparator) {
    assert myParent == null;
    myComparator = comparator;
    ensureOrder();
  }

  @NotNull
  public List<MemoryObjectTreeNode<T>> getPathToRoot() {
    List<MemoryObjectTreeNode<T>> path = new ArrayList<>();
    MemoryObjectTreeNode<T> currentNode = this;
    MemoryObjectTreeNode<T> cycleDetector = this;
    while (currentNode != null) {
      for (int i = 0; i < 2 && cycleDetector != null; i++) {
        assert cycleDetector.myParent != currentNode;
        cycleDetector = cycleDetector.myParent;
      }

      path.add(currentNode);
      currentNode = currentNode.myParent;
    }
    Collections.reverse(path);
    return path;
  }

  private void ensureOrder() {
    if ((myParent != null && myParent.myComparator != myComparator)
        || myParent == null && myComparator != null) {
      myComparator = myParent != null ? myParent.myComparator : myComparator;
      myChildren.sort(myComparator);
    }
  }
}
