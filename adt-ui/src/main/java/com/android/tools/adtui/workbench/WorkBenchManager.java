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
package com.android.tools.adtui.workbench;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * All the settings of an {@link AttachedToolWindow} are shared globally for all instances
 * of the same {@link WorkBench} and tool window combination.<br/>
 * This class is responsible for notifying other {@link WorkBench}es that something has
 * changed.
 */
public class WorkBenchManager {
  private Multimap<String, WorkBench> myWorkBenches;

  public static WorkBenchManager getInstance() {
    return ServiceManager.getService(WorkBenchManager.class);
  }

  public WorkBenchManager() {
    myWorkBenches = Multimaps.synchronizedListMultimap(ArrayListMultimap.create());
  }

  public void register(@NotNull WorkBench workBench) {
    myWorkBenches.put(workBench.getName(), workBench);
  }

  public void unregister(@NotNull WorkBench workBench) {
    myWorkBenches.remove(workBench.getName(),workBench);
  }

  public void updateOtherWorkBenches(@NotNull WorkBench workBench) {
    Collection<WorkBench> workBenches = myWorkBenches.get(workBench.getName());
    workBenches.stream().filter(bench -> bench != workBench).forEach(WorkBench::updateModel);
  }
}
