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
package com.android.tools.idea.gradle.structure.navigation;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class PsNavigationPath implements Comparable<PsNavigationPath> {
  @NonNls public static final String GO_TO_PATH_TYPE = "psdGoTo://";

  @NotNull
  public static final PsNavigationPath EMPTY_PATH = new PsNavigationPath() {
    @Override
    @NotNull
    public String getHtml() {
      return getPlainText();
    }

    @Override
    @NotNull
    public String getPlainText() {
      return "";
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return 1;
    }

    @Override
    public String toString() {
      return "<Empty Path>";
    }
  };

  @Override
  public int compareTo(PsNavigationPath path) {
    return getPlainText().compareTo(path.getPlainText());
  }

  @NotNull
  public abstract String getPlainText();

  @NotNull
  public abstract String getHtml();
}
