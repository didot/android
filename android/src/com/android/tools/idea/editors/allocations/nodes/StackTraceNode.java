/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.allocations.nodes;

import com.android.ddmlib.AllocationInfo;
import gnu.trove.TIntObjectHashMap;

public class StackTraceNode extends AbstractTreeNode {
  private TIntObjectHashMap<ThreadNode> myChildrenMap = new TIntObjectHashMap<ThreadNode>();

  public void insert(AllocationInfo alloc) {
    short id = alloc.getThreadId();
    ThreadNode thread = myChildrenMap.get(id);
    if (thread == null) {
      thread = new ThreadNode(id);
      myChildrenMap.put(id, thread);
      addChild(thread);
    }
    thread.insert(alloc, 0);
  }
}
