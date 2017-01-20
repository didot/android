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

package com.android.tools.adtui.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// TODO: cleanup this class and its usages. Make sure to document that afterwards.
public class HNode<T> {

  private long myStart;
  private long myEnd;
  @Nullable private T myData;
  @NotNull private List<HNode<T>> myNodes;
  @Nullable private HNode<T> myParent;
  private int myDepth;

  public HNode() {
    this(null, 0, 0);
  }

  public HNode(@Nullable T data, long start, long end) {
    myNodes = new ArrayList<>();
    myData = data;
    myStart = start;
    myEnd = end;
  }

  @NotNull
  public List<HNode<T>> getChildren() {
    return myNodes;
  }

  public void addHNode(HNode<T> node) {
    myNodes.add(node);
    node.myParent = this;
  }

  @Nullable
  public HNode<T> getParent() {
    return myParent;
  }

  @Nullable
  public HNode<T> getLastChild() {
    if (myNodes.isEmpty()) {
      return null;
    }
    return myNodes.get(myNodes.size() - 1);
  }

  @Nullable
  public HNode<T> getFirstChild() {
    if (myNodes.isEmpty()) {
      return null;
    }
    return myNodes.get(0);
  }

  public long getEnd() {
    return myEnd;
  }

  public void setEnd(long end) {
    myEnd = end;
  }

  public long getStart() {
    return myStart;
  }

  public void setStart(long start) {
    myStart = start;
  }

  @Nullable
  public T getData() {
    return myData;
  }

  public void setData(@Nullable T data) {
    myData = data;
  }

  public int getDepth() {
    return myDepth;
  }

  public void setDepth(int depth) {
    myDepth = depth;
  }

  public long duration() {
    return myEnd - myStart;
  }
}
