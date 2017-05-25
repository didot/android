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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Classifies {@link InstanceObject}s based on their {@link Class}.
 */
public class ClassSet extends ClassifierSet {
  @NotNull private final ClassDb.ClassEntry myClassEntry;

  @NotNull
  public static Classifier createDefaultClassifier() {
    return new ClassClassifier();
  }

  public ClassSet(@NotNull ClassDb.ClassEntry classEntry) {
    super(classEntry.getSimpleClassName());
    myClassEntry = classEntry;
  }

  @NotNull
  public ClassDb.ClassEntry getClassEntry() {
    return myClassEntry;
  }

  @NotNull
  @Override
  public Classifier createSubClassifier() {
    // Do nothing, as this is a leaf node (presently).
    return Classifier.IDENTITY_CLASSIFIER;
  }

  private static final class ClassClassifier extends Classifier {
    @NotNull private final Map<ClassDb.ClassEntry, ClassSet> myClassMap = new LinkedHashMap<>();

    @NotNull
    @Override
    public ClassifierSet getOrCreateClassifierSet(@NotNull InstanceObject instance) {
      return myClassMap.computeIfAbsent(instance.getClassEntry(), ClassSet::new);
    }

    @NotNull
    @Override
    public List<ClassifierSet> getClassifierSets() {
      return new ArrayList<>(myClassMap.values());
    }
  }
}
