/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.profilers.stacktrace;

import com.android.tools.idea.profilers.IntellijContextMenuInstaller;
import com.android.tools.profilers.stacktrace.StackTraceGroup;
import com.android.tools.profilers.stacktrace.StackTraceModel;
import com.android.tools.profilers.stacktrace.StackTraceView;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public class IntelliJStackTraceGroup implements StackTraceGroup {
  private final Project myProject;
  private final BiFunction<Project, StackTraceModel, IntelliJStackTraceView> myViewGenerator;

  private final List<IntelliJStackTraceView> myStackTraceViews = new ArrayList<>();

  public IntelliJStackTraceGroup(@NotNull Project project) {
    this(project, (p, m) -> {
      IntelliJStackTraceView view = new IntelliJStackTraceView(p, m);
      view.installNavigationContextMenu(new IntellijContextMenuInstaller());
      return view;
    });
  }

  @VisibleForTesting
  IntelliJStackTraceGroup(@NotNull Project project,
                          @NotNull BiFunction<Project, StackTraceModel, IntelliJStackTraceView> viewGenerator) {
    myProject = project;
    myViewGenerator = viewGenerator;
  }

  @Override
  @NotNull
  public StackTraceView createStackView(@NotNull StackTraceModel model) {
    IntelliJStackTraceView view = myViewGenerator.apply(myProject, model);
    myStackTraceViews.add(view);

    view.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        // Ignore when a view had its selection cleared, since that shouldn't affect other views
        // in the group (and may have even been externally caused by another one)
        if (((JList)e.getSource()).getSelectedIndex() < 0) {
          return;
        }

        myStackTraceViews.forEach(otherView -> {
          if (otherView != view) {
            otherView.clearSelection();
          }
        });
      }
    });

    return view;
  }
}
