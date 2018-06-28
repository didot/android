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
package com.android.tools.idea.profilers;

import com.android.tools.idea.profilers.profilingconfig.CpuProfilingConfigurationsDialog;
import com.android.tools.idea.profilers.stacktrace.IntelliJStackTraceGroup;
import com.android.tools.profilers.*;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.cpu.CpuProfilerConfigModel;
import com.android.tools.profilers.cpu.ProfilingConfiguration;
import com.android.tools.profilers.stacktrace.DataViewer;
import com.android.tools.profilers.stacktrace.LoadingPanel;
import com.android.tools.profilers.stacktrace.StackTraceGroup;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLoadingPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.function.Consumer;

public class IntellijProfilerComponents implements IdeProfilerComponents {

  @NotNull private final Project myProject;

  @NotNull private final FeatureTracker myFeatureTracker;

  public IntellijProfilerComponents(@NotNull Project project, @NotNull FeatureTracker featureTracker) {
    myProject = project;
    myFeatureTracker = featureTracker;
  }

  @NotNull
  @Override
  public LoadingPanel createLoadingPanel(int delayMs) {
    return new LoadingPanel() {
      private final JBLoadingPanel myLoadingPanel = new JBLoadingPanel(new BorderLayout(), myProject, delayMs);

      @NotNull
      @Override
      public JComponent getComponent() {
        return myLoadingPanel;
      }

      @Override
      public void setChildComponent(@Nullable Component comp) {
        myLoadingPanel.getContentPanel().removeAll();
        if (comp != null) {
          myLoadingPanel.add(comp);
        }
      }

      @Override
      public void setLoadingText(@NotNull String loadingText) {
        myLoadingPanel.setLoadingText(loadingText);
      }

      @Override
      public void startLoading() {
        myLoadingPanel.startLoading();
      }

      @Override
      public void stopLoading() {
        myLoadingPanel.stopLoading();
      }
    };
  }

  @NotNull
  @Override
  public StackTraceGroup createStackGroup() {
    return new IntelliJStackTraceGroup(myProject);
  }

  @NotNull
  @Override
  public ContextMenuInstaller createContextMenuInstaller() {
    return new IntellijContextMenuInstaller();
  }

  @NotNull
  @Override
  public ExportDialog createExportDialog() {
    return new IntellijExportDialog(myProject);
  }

  @NotNull
  @Override
  public ImportDialog createImportDialog() {
    return new IntellijImportDialog(myProject);
  }

  @NotNull
  @Override
  public DataViewer createDataViewer(@NotNull byte[] content, @NotNull ContentType contentType) {
    if (contentType.isImageType()) {
      try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
        BufferedImage image = ImageIO.read(inputStream);
        if (image != null) {
          return IntellijDataViewer.createImageViewer(image);
        }
      }
      catch (IOException ignored) {
      }
      return IntellijDataViewer.createInvalidViewer();
    }
    return IntellijDataViewer.createEditorViewer(myProject, new String(content), contentType.getFileType());
  }

  @NotNull
  @Override
  public JComponent createResizableImageComponent(@NotNull BufferedImage image) {
    return new ResizableImage(image);
  }

  @NotNull
  @Override
  public AutoCompleteTextField createAutoCompleteTextField(@NotNull String placeholder,
                                                           @NotNull String value,
                                                           @NotNull Collection<String> variants) {
    return new IntellijAutoCompleteTextField(myProject, placeholder, value, variants);
  }

  @NotNull
  @Override
  public UiMessageHandler createUiMessageHandler() {
    return new IntellijUiMessageHandler();
  }

  @Override
  public void openCpuProfilingConfigurationsDialog(@NotNull CpuProfilerConfigModel model, int deviceLevel,
                                                   @NotNull Consumer<ProfilingConfiguration> dialogCallback) {
    CpuProfilingConfigurationsDialog dialog = new CpuProfilingConfigurationsDialog(myProject,
                                                                                   deviceLevel,
                                                                                   model,
                                                                                   dialogCallback,
                                                                                   myFeatureTracker);
    dialog.show();
  }
}
