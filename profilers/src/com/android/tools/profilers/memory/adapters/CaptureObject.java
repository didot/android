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
package com.android.tools.profilers.memory.adapters;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Stream;

import static javax.swing.SortOrder.ASCENDING;
import static javax.swing.SortOrder.DESCENDING;

public interface CaptureObject extends MemoryObject {
  int DEFAULT_CLASSLOADER_ID = -1;
  String INVALID_HEAP_NAME = "INVALID";

  /**
   * Available attributes and sort preferences for {@link ClassifierSet}s. Implementations of {@link CaptureObject} should return a list
   * of the supported attributes.
   */
  enum ClassifierAttribute {
    LABEL(0, ASCENDING),
    ALLOC_COUNT(2, DESCENDING),
    DEALLOC_COUNT(1, DESCENDING),
    SHALLOW_SIZE(3, DESCENDING),
    RETAINED_SIZE(4, DESCENDING);

    private final int myWeight;

    @NotNull private final SortOrder mySortOrder;

    ClassifierAttribute(int weight, @NotNull SortOrder sortOrder) {
      myWeight = weight;
      mySortOrder = sortOrder;
    }

    public int getWeight() {
      return myWeight;
    }

    @NotNull
    public SortOrder getSortOrder() {
      return mySortOrder;
    }
  }

  /**
   * Available attributes and sort preferences for instances in {@link ClassSet}s. Implementations of {@link CaptureObject} should return a
   * list of the supported attributes.
   */
  enum InstanceAttribute {
    LABEL(1, ASCENDING),
    ALLOCATION_TIME(2, DESCENDING),
    DEALLOCATION_TIME(3, DESCENDING),
    DEPTH(0, ASCENDING),
    SHALLOW_SIZE(4, DESCENDING),
    RETAINED_SIZE(5, DESCENDING);

    private final int myWeight;

    @NotNull private final SortOrder mySortOrder;

    InstanceAttribute(int weight, @NotNull SortOrder sortOrder) {
      myWeight = weight;
      mySortOrder = sortOrder;
    }

    public int getWeight() {
      return myWeight;
    }

    @NotNull
    public SortOrder getSortOrder() {
      return mySortOrder;
    }
  }

  interface CaptureChangedListener {
    final class ChangedNode {
      @NotNull private final Map<ClassifierSet, ChangedNode> myChildChangedNodes = new HashMap<>();
      @NotNull private final ClassifierSet myClassifierSet;

      public ChangedNode(@NotNull ClassifierSet classifierSet) {
        myClassifierSet = classifierSet;
      }

      public boolean containsChild(@NotNull ClassifierSet childClassifierSet) {
        return myChildChangedNodes.containsKey(childClassifierSet);
      }

      public ChangedNode getChild(@NotNull ClassifierSet childClassifierSet) {
        return myChildChangedNodes.get(childClassifierSet);
      }

      public void addPath(@NotNull List<ClassifierSet> path) {
        assert path.get(0) == myClassifierSet;
        if (path.size() > 1) {
          ChangedNode child = myChildChangedNodes.computeIfAbsent(path.get(1), ChangedNode::new);
          if (path.size() > 2) {
            child.addPath(path.subList(1, path.size()));
          }
        }
      }

      @NotNull
      public ClassifierSet getClassifierSet() {
        return myClassifierSet;
      }

      @NotNull
      public Set<ClassifierSet> getChildClassifierSets() {
        return myChildChangedNodes.keySet();
      }
    }

    void heapChanged(@NotNull ChangedNode changedNode, boolean clear);
  }

  @Nullable
  String getExportableExtension();

  void saveToFile(@NotNull OutputStream outputStream) throws IOException;

  @NotNull
  List<ClassifierAttribute> getClassifierAttributes();

  @NotNull
  List<InstanceAttribute> getInstanceAttributes();

  @NotNull
  Collection<HeapSet> getHeapSets();

  @NotNull
  String getHeapName(int heapId);

  @Nullable
  HeapSet getHeapSet(int heapId);

  @NotNull
  Stream<InstanceObject> getInstances();

  long getStartTimeNs();

  long getEndTimeNs();

  boolean load();

  boolean isDoneLoading();

  boolean isError();

  void unload();

  default void addCaptureChangedListener(@NotNull CaptureChangedListener listener) {}
}
