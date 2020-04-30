/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.run.activity.manifest;

import java.util.HashSet;
import java.util.Set;

public class IntentFilter {
  private Set<String> myActions = new HashSet();
  private Set<String> myCategories = new HashSet();

  IntentFilter() {}

  void addAction(String action) {
    myActions.add(action);
  }

  void addCategory(String category) {
    myCategories.add(category);
  }

  public boolean hasAction(String action) {
    return myActions.contains(action);
  }

  public boolean hasCategory(String category) {
    return myCategories.contains(category);
  }

  public Set<String> getActions() {
    return myActions;
  }

  public Set<String> getCategories() {
    return myCategories;
  }
}
