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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.HNode;
import com.android.tools.adtui.model.filter.Filter;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.profilers.cpu.CaptureNode.FilterType.MATCH;

public class CaptureNode implements HNode<CaptureNode> {

  /**
   * Start time with GLOBAL clock.
   */
  private long myStartGlobal;

  /**
   * End time with GLOBAL clock.
   */
  private long myEndGlobal;

  /**
   * Start time with THREAD clock.
   */
  private long myStartThread;

  /**
   * End time with THREAD clock.
   */
  private long myEndThread;

  @NotNull
  protected final List<CaptureNode> myChildren;

  @NotNull
  private ClockType myClockType;

  /**
   * The parent of its child is set to it when it is added {@link #addChild(CaptureNode)}
   */
  private CaptureNode myParent;

  /**
   * see {@link FilterType}.
   */
  @NotNull
  private FilterType myFilterType;

  /**
   * The shortest distance from the root.
   */
  private int myDepth;

  @NotNull
  private final CaptureNodeModel myData;

  public CaptureNode(@NotNull CaptureNodeModel model) {
    myChildren = new ArrayList<>();
    myClockType = ClockType.GLOBAL;
    myFilterType = MATCH;
    myDepth = 0;
    myData = model;
  }

  public void addChild(CaptureNode node) {
    myChildren.add(node);
    node.myParent = this;
  }

  @NotNull
  public List<CaptureNode> getChildren() {
    return myChildren;
  }

  @NotNull
  public CaptureNodeModel getData() {
    return myData;
  }

  @Override
  public int getChildCount() {
    return myChildren.size();
  }

  @NotNull
  @Override
  public CaptureNode getChildAt(int index) {
    return myChildren.get(index);
  }

  @Nullable
  @Override
  public CaptureNode getParent() {
    return myParent;
  }

  @Override
  public long getStart() {
    return myClockType == ClockType.THREAD ? myStartThread : myStartGlobal;
  }

  @Override
  public long getEnd() {
    return myClockType == ClockType.THREAD ? myEndThread : myEndGlobal;
  }

  @Override
  public int getDepth() {
    return myDepth;
  }

  public void setStartGlobal(long startGlobal) {
    myStartGlobal = startGlobal;
  }

  public long getStartGlobal() {
    return myStartGlobal;
  }

  public void setEndGlobal(long endGlobal) {
    myEndGlobal = endGlobal;
  }

  public long getEndGlobal() {
    return myEndGlobal;
  }

  public void setStartThread(long startThread) {
    myStartThread = startThread;
  }

  public long getStartThread() {
    return myStartThread;
  }

  public void setEndThread(long endThread) {
    myEndThread = endThread;
  }

  public long getEndThread() {
    return myEndThread;
  }

  public void setClockType(@NotNull ClockType clockType) {
    myClockType = clockType;
  }

  /**
   * Returns the proportion of time the method was using CPU relative to the total (wall-clock) time that passed.
   */
  public double threadGlobalRatio() {
    long durationThread = myEndThread - myStartThread;
    long durationGlobal = myEndGlobal - myStartGlobal;
    return (double)durationThread / durationGlobal;
  }

  @NotNull
  public ClockType getClockType() {
    return myClockType;
  }

  public void setDepth(int depth) {
    myDepth = depth;
  }

  /**
   * @return true if this node matches to the {@param filter}.
   * Note: this node matches to the empty {@param filter}.
   */
  public boolean matchesToFilter(@NotNull Filter filter) {
    return filter.matches(getData().getFullName());
  }

  @NotNull
  public FilterType getFilterType() {
    return myFilterType;
  }

  public void setFilterType(@NotNull FilterType type) {
    myFilterType = type;
  }

  public boolean isUnmatched() {
    return getFilterType() == FilterType.UNMATCH;
  }

  public enum FilterType {
    /**
     * This {@link CaptureNode} matches to the filter, i.e {@link #matchesToFilter(Filter)} is true.
     */
    EXACT_MATCH,

    /**
     * Either one of its ancestor is a {@link #EXACT_MATCH} or one of its descendant.
     */
    MATCH,

    /**
     * Neither this node matches to the filter nor one of its ancestor nor one of its descendant.
     */
    UNMATCH,
  }
}
