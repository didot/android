/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers;

import com.android.tools.profilers.cpu.CpuProfilerConfigModel;
import com.android.tools.profilers.cpu.ProfilingConfiguration;
import com.android.tools.profilers.dataviewer.DataViewer;
import com.android.tools.profilers.dataviewer.ImageDataViewer;
import com.android.tools.profilers.stacktrace.*;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FakeIdeProfilerComponents implements IdeProfilerComponents {
  @NotNull private Map<JComponent, Supplier<CodeLocation>> myComponentNavigations = new HashMap<>();
  @NotNull private Map<JComponent, List<ContextMenuItem>> myComponentContextMenus = new HashMap<>();

  @NotNull
  @Override
  public LoadingPanel createLoadingPanel(int delayMs) {
    return new LoadingPanel() {
      private JPanel myPanel = new JPanel(new BorderLayout());

      @NotNull
      @Override
      public JComponent getComponent() {
        return myPanel;
      }

      @Override
      public void setLoadingText(@NotNull String loadingText) {
      }

      @Override
      public void setChildComponent(@Nullable Component comp) {
      }

      @Override
      public void startLoading() {
      }

      @Override
      public void stopLoading() {
      }
    };
  }

  @NotNull
  @Override
  public StackTraceGroup createStackGroup() {
    return new StackTraceGroupStub();
  }

  @NotNull
  @Override
  public ContextMenuInstaller createContextMenuInstaller() {
    return new ContextMenuInstaller() {
      @Override
      public void installGenericContextMenu(@NotNull JComponent component, @NotNull ContextMenuItem contextMenuItem,
                                            @NotNull IntPredicate itemEnabled, @NotNull IntConsumer callback) {
        List<ContextMenuItem> menus = myComponentContextMenus.computeIfAbsent(component, k -> new ArrayList<>());
        menus.add(contextMenuItem);
      }

      @Override
      public void installNavigationContextMenu(@NotNull JComponent component,
                                               @NotNull CodeNavigator navigator,
                                               @NotNull Supplier<CodeLocation> codeLocationSupplier) {
        assertFalse(myComponentNavigations.containsKey(component));
        myComponentNavigations.put(component, codeLocationSupplier);
      }
    };
  }

  @NotNull
  @Override
  public ExportDialog createExportDialog() {
    return new ExportDialog() {
      @Override
      public void open(@NotNull Supplier<String> dialogTitleSupplier,
                       @NotNull Supplier<String> fileNameSupplier,
                       @NotNull Supplier<String> extensionSupplier,
                       @NotNull Consumer<File> saveToFile) {
      }
    };
  }

  @NotNull
  @Override
  public ImportDialog createImportDialog() {
    return new ImportDialog() {
      @Override
      public void open(@NotNull Supplier<String> dialogTitleSupplier,
                       @NotNull List<String> validExtensions,
                       @NotNull Consumer<VirtualFile> fileOpenedCallback) {
      }
    };
  }

  @Nullable
  public Supplier<CodeLocation> getCodeLocationSupplier(@NotNull JComponent component) {
    assertTrue(myComponentNavigations.containsKey(component));
    return myComponentNavigations.get(component);
  }

  @Nullable
  public List<ContextMenuItem> getComponentContextMenus(@NotNull JComponent component) {
    return myComponentContextMenus.get(component);
  }

  @NotNull
  public List<ContextMenuItem> getAllContextMenuItems() {
    return myComponentContextMenus.values().stream().flatMap(List::stream).collect(Collectors.toList());
  }

  public void clearContextMenuItems() {
    myComponentContextMenus.clear();
  }

  @NotNull
  @Override
  public DataViewer createDataViewer(@NotNull byte[] content, @NotNull ContentType contentType, @NotNull DataViewer.Style styleHint) {
    if (contentType.isImageType()) {
      return new ImageDataViewer() {
        private final BufferedImage DUMMY_IMAGE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        private final JComponent DUMMY_COMPONENT = new JLabel(new ImageIcon(DUMMY_IMAGE));

        @NotNull
        @Override
        public BufferedImage getImage() {
          return DUMMY_IMAGE;
        }

        @NotNull
        @Override
        public JComponent getComponent() {
          return DUMMY_COMPONENT;
        }
      };
    }
    else {
      return new DataViewer() {
        private final JComponent DUMMY_COMPONENT = new JPanel();

        @NotNull
        @Override
        public JComponent getComponent() {
          return DUMMY_COMPONENT;
        }

        @NotNull
        @Override
        public Style getStyle() {
          return Style.RAW;
        }
      };
    }
  }

  @NotNull
  @Override
  public JComponent createResizableImageComponent(@NotNull BufferedImage image) {
    return new JPanel();
  }

  @NotNull
  @Override
  public UiMessageHandler createUiMessageHandler() {
    return new UiMessageHandler() {
      @Override
      public void displayErrorMessage(@NotNull JComponent parent, @NotNull String title, @NotNull String message) {
        parent.add(new JLabel(message));
      }

      @Override
      public boolean displayOkCancelMessage(@NotNull String title,
                                            @NotNull String message,
                                            @NotNull String okText,
                                            @NotNull String cancelText,
                                            @Nullable Icon icon,
                                            @NotNull com.intellij.util.Consumer<Boolean> doNotShowSettingSaver) {
        return true;
      }
    };
  }

  @Override
  public void openCpuProfilingConfigurationsDialog(@NotNull CpuProfilerConfigModel model, int deviceLevel,
                                                   @NotNull Consumer<ProfilingConfiguration> callbackDialog) {
    // No-op.
  }

  public static final class StackTraceGroupStub implements StackTraceGroup {
    @NotNull
    @Override
    public StackTraceView createStackView(@NotNull StackTraceModel model) {
      return new StackTraceViewStub(model);
    }
  }

  public static final class StackTraceViewStub implements StackTraceView {
    private StackTraceModel myModel;

    private JPanel myComponent = new JPanel();

    public StackTraceViewStub(@NotNull StackTraceModel model) {
      myModel = model;
    }

    @NotNull
    @Override
    public StackTraceModel getModel() {
      return myModel;
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return myComponent;
    }
  }
}
