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
package com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node;


import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.SwitchCaseGraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;

/**
 * Created by haowei on 6/13/16.
 */
public interface SwitchBranchingNode extends GraphNode {

  public Value getCheckedValue();

  public GraphNode getDefaultTarget();

  public void setDefaultTarget(GraphNode target);

  /**
   * The type should be constants
   *
   * @return
   */
  public Value[] getKeys();

  public GraphNode getTargetViaKey(Value key);

  public void setTargetViaKey(Value key, GraphNode target);

  public void setSwitchCaseGraph(SwitchCaseGraph graph);

  public SwitchCaseGraph getSwitchCaseGraph();

}
