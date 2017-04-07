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
package com.android.tools.idea.testartifacts.junit;

import com.android.tools.idea.testartifacts.scopes.TestArtifactSearchScopes;
import com.intellij.execution.Executor;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.openapi.module.Module;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Android implementation of {@link JUnitConsoleProperties} so some behaviors can be overridden.
 */
public class AndroidJUnitConsoleProperties extends JUnitConsoleProperties {
  public AndroidJUnitConsoleProperties(@NotNull AndroidJUnitConfiguration configuration,
                                       Executor executor) {
    super(configuration, executor);
  }

  @NotNull
  @Override
  protected GlobalSearchScope initScope() {
    GlobalSearchScope scope = super.initScope();

    for (Module each : getConfiguration().getModules()) {
      // AndroidTest scope in each module is excluded from the scope used to find JUnitTests
      TestArtifactSearchScopes testArtifactSearchScopes = TestArtifactSearchScopes.get(each);
      if (testArtifactSearchScopes != null) {
        scope = scope.intersectWith(GlobalSearchScope.notScope(testArtifactSearchScopes.getAndroidTestSourceScope()));
      }
    }
    return scope;
  }
}
