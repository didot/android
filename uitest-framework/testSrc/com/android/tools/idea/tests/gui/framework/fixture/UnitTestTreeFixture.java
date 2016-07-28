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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.Wait;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.TestTreeView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fixture for the tree widget, on the left hand side of "Run" window (when running tests).
 */
public class UnitTestTreeFixture {
  private ExecutionToolWindowFixture.ContentFixture myContentFixture;
  private final TestTreeView myTreeView;

  public UnitTestTreeFixture(@NotNull ExecutionToolWindowFixture.ContentFixture contentFixture,
                             @NotNull TestTreeView treeView) {
    myContentFixture = contentFixture;
    myTreeView = treeView;
  }

  @Nullable
  public TestFrameworkRunningModel getModel() {
    Wait.seconds(30).expecting("the test results model").until(() -> myTreeView.getData(TestTreeView.MODEL_DATA_KEY.getName()) != null);

    return (TestFrameworkRunningModel)myTreeView.getData(TestTreeView.MODEL_DATA_KEY.getName());
  }

  public boolean isAllTestsPassed() {
    return getFailingTestsCount() == 0;
  }

  public int getFailingTestsCount() {
    int count = 0;
    AbstractTestProxy root = getModel().getRoot();
    for (AbstractTestProxy test : root.getAllTests()) {
      if (test.isLeaf() && test.isDefect()) {
        count++;
      }
    }
    return count;
  }

  public int getAllTestsCount() {
    int count = 0;
    AbstractTestProxy root = getModel().getRoot();
    for (AbstractTestProxy test : root.getAllTests()) {
      if (test.isLeaf()) {
        count++;
      }
    }
    return count;
  }

  @NotNull
  public ExecutionToolWindowFixture.ContentFixture getContent() {
    return myContentFixture;
  }
}
