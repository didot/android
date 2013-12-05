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
package com.android.tools.idea.wizard;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * In-memory tree representation of a file tree. Must be created with a file tree,
 * and then additional files (which may or may not exist) may be added to the representation.
 * Can be rendered to a {@link JTree} using the {@link FileTreeCellRenderer}.
 */
public class FileTreeModel implements TreeModel {

  /**
   * Root file that this model was created with.
   */
  private File myRoot;

  /**
   * Root of the data structure representation.
   */
  private Node myRootNode;

  public FileTreeModel(@NotNull File root) {
    myRoot = root;
    myRootNode = makeTree(root);
  }

  /**
   * Return the root {@link Node} of this representation.
   */
  @Override
  public Object getRoot() {
    return myRootNode;
  }

  /**
   * Get the Nth child {@link Node} of the given parent.
   */
  @Override
  public Object getChild(Object parent, int index) {
    return ((Node)parent).children.get(index);
  }

  /**
   * Get the number of children that the given parent {@link Node} has.
   */
  @Override
  public int getChildCount(Object parent) {
    return ((Node)parent).children.size();
  }

  /**
   * Returns true iff the given {@link Node} has no children (is a leaf)
   */
  @Override
  public boolean isLeaf(Object node) {
    return ((Node)node).children.isEmpty();
  }

  @Override
  public void valueForPathChanged(TreePath path, Object newValue) {
    // Not implemented
  }

  /**
   * Returns the index of the given child inside the given parent or -1 if given node is not a child of the parent.
   */
  @Override
  public int getIndexOfChild(Object parent, Object child) {
    //noinspection SuspiciousMethodCalls
    return ((Node)parent).children.indexOf(child);
  }

  @Override
  public void addTreeModelListener(TreeModelListener l) {
    // Not implemented
  }

  @Override
  public void removeTreeModelListener(TreeModelListener l) {
    // Not implemented
  }

  /**
   * Add the given file to the representation.
   * This is a no-op if the given path already exists within the tree.
   */
  public void addFile(@NotNull File f) {
    addFile(f, null);
  }

  /**
   * Add the given file to the representation and mark it with the given icon.
   * This is a no-op if the given path already exists within the tree.
   */
  public void addFile(@NotNull File f, @Nullable Icon ic) {
    String s = f.isAbsolute() ? FileUtil.getRelativePath(myRoot, f) : f.getPath();
    if (s != null) {
      List<String> parts = Lists.newLinkedList(Splitter.on(File.separatorChar).split(s));
      makeNode(myRootNode, parts, ic);
    }
  }

  /**
   * Representation of a node within the tree
   */
  protected static class Node {
    public String name;
    public List<Node> children = Lists.newLinkedList();
    public boolean existsOnDisk;
    public Icon icon;

    @Override
    public String toString() {
      return name;
    }

    /**
     * Returns true iff this node has a child with the given name.
     */
    public boolean hasChild(String name) {
      for (Node child : children) {
        if (child.name.equals(name)) {
          return true;
        }
      }
      return false;
    }

    /**
     * Returns the child with the given name or null
     */
    @Nullable
    public Node getChild(String name) {
      for (Node child : children) {
        if (child.name.equals(name)) {
          return child;
        }
      }
      return null;
    }
  }

  /**
   * Recursively build the node(s) specified in the given path hierarchy starting at the given root.
   * Mark the last node in the path with the given icon.
   */
  private static void makeNode(@NotNull Node root, @NotNull List<String> path, @Nullable Icon ic) {
    if (path.isEmpty()) {
      return;
    }

    String name = path.get(0);

    if (root.name.equals(name)) {
      // Continue down along already-created paths
      makeNode(root, rest(path), ic);
    } else if (root.hasChild(name)) {
      // Allow paths relative to root (rather than including root explicitly)
      //noinspection ConstantConditions
      makeNode(root.getChild(name), rest(path), ic);
    } else {
      // If this node in the path doesn't exist, then create it.
      Node n = new Node();
      n.name = name;
      root.children.add(n);
      if (path.size() == 1) {
        // If this is the end of the path, mark with the given icon
        n.icon = ic;
      } else {
        // Continue down to create the rest of the path
        makeNode(n, rest(path), ic);
      }
    }
  }

  /**
   * Populate a tree from the file hierarchy rooted at the given file.
   */
  private static Node makeTree(@NotNull File root) {
    Node n = new Node();
    n.name = root.getName();
    n.existsOnDisk = root.exists();
    if (root.isDirectory()) {
      File[] children = root.listFiles();
      if (children != null) {
        for (File f : children) {
          n.children.add(makeTree(f));
        }
      }
    }
    return n;
  }

  /**
   * Convenience function. Operates on a list and returns a list containing all elements but the first.
   */
  private static <T> List<T> rest(List<T> list) {
    return list.subList(1, list.size());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    toString(sb, myRootNode);
    return sb.toString();
  }

  /**
   * DFS over the tree to build a string representation e.g. (root (child (grandchild) (grandchild)) (child))
   */
  private void toString(StringBuilder sb, Node root) {
    sb.append('(');
    sb.append(root.name);
    if (!isLeaf(root)) {
      sb.append(' ');
    }
    for (Node child : root.children) {
      toString(sb, child);
    }
    sb.append(')');
  }
}
