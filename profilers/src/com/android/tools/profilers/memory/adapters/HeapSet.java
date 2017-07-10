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

import com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Classifies {@link InstanceObject}s based on their allocation's heap ID.
 */
public class HeapSet extends ClassifierSet {
  @NotNull private final CaptureObject myCaptureObject;
  @NotNull private ClassGrouping myClassGrouping = ClassGrouping.ARRANGE_BY_CLASS;
  private final int myId;

  public HeapSet(@NotNull CaptureObject captureObject, int id) {
    super(captureObject.getHeapName(id));
    myCaptureObject = captureObject;
    myId = id;
    setClassGrouping(ClassGrouping.ARRANGE_BY_CLASS);
  }

  public void setClassGrouping(@NotNull ClassGrouping classGrouping) {
    if (myClassGrouping == classGrouping) {
      return;
    }
    myClassGrouping = classGrouping;

    // Gather all the instances from the descendants and add them to the heap node.
    // Subsequent calls to getChildrenClassifierSets will re-partition them to the correct child ClassifierSet.
    List<InstanceObject> descendantsStream = getInstancesStream().collect(Collectors.toList());
    myInstances.clear();
    myClassifier = null;
    myInstances.addAll(descendantsStream);
  }

  public int getId() {
    return myId;
  }

  @NotNull
  @Override
  public Classifier createSubClassifier() {
    switch (myClassGrouping) {
      case ARRANGE_BY_CLASS:
        return ClassSet.createDefaultClassifier();
      case ARRANGE_BY_PACKAGE:
        return PackageSet.createDefaultClassifier(myCaptureObject);
      case ARRANGE_BY_CALLSTACK:
        return ThreadSet.createDefaultClassifier(myCaptureObject);
      default:
        throw new RuntimeException("Classifier type not implemented: " + myClassGrouping);
    }
  }
}
