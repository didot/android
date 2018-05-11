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
package com.android.tools.idea.uibuilder;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.util.PropertiesMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.util.concurrency.EdtExecutorService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static com.android.SdkConstants.PREFIX_ANDROID;

/**
 * {@link LayoutlibSceneManager} used for tests that performs all operations synchronously.
 */
public class SyncLayoutlibSceneManager extends LayoutlibSceneManager {
  private final Map<Object, PropertiesMap> myDefaultProperties;
  private ViewEditor myCustomViewEditor;

  public SyncLayoutlibSceneManager(@NotNull SyncNlModel model) {
    super(model, model.getSurface(), EdtExecutorService.getInstance());
    myDefaultProperties = new HashMap<>();
  }

  @Override
  public void requestRender() {
    runAfterCommandIfNecessary(() -> render(getTriggerFromChangeType(getModel().getLastChangeType())));
  }

  @Override
  public void requestLayoutAndRender(boolean animate) {
    runAfterCommandIfNecessary(() -> render(getTriggerFromChangeType(getModel().getLastChangeType())));
  }

  @Override
  protected void requestModelUpdate() {
    runAfterCommandIfNecessary(this::updateModel);
  }

  private static void runAfterCommandIfNecessary(Runnable runnable) {
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      CommandProcessor.getInstance().addCommandListener(new CommandAdapter() {
        @Override
        public void commandFinished(CommandEvent event) {
          runnable.run();
          CommandProcessor.getInstance().removeCommandListener(this);
        }
      });
    }
    else {
      runnable.run();
    }
  }

  @Override
  protected void setupRenderTask(@Nullable RenderTask task) {
    super.setupRenderTask(task);

    if (task != null) {
      task.disableSecurityManager();
    }
  }

  @Override
  @NotNull
  public Map<Object, PropertiesMap> getDefaultProperties() {
    return myDefaultProperties;
  }

  public void putDefaultPropertyValue(@NotNull NlComponent component,
                                      @NotNull ResourceNamespace namespace,
                                      @NotNull String attributeName,
                                      @NotNull String resourceValue,
                                      @Nullable String value) {
    PropertiesMap map = myDefaultProperties.get(component.getSnapshot());
    if (map == null) {
      map = new PropertiesMap();
      myDefaultProperties.put(component.getSnapshot(), map);
    }
    // TODO: Update for namespace support:
    String key = (namespace == ResourceNamespace.ANDROID ? PREFIX_ANDROID : "") + attributeName;
    map.put(key, new PropertiesMap.Property(resourceValue, value));
  }

  public void setCustomViewEditor(@NotNull ViewEditor editor) {
    myCustomViewEditor = editor;
  }

  @NotNull
  @Override
  public ViewEditor getViewEditor() {
    return myCustomViewEditor != null ? myCustomViewEditor : super.getViewEditor();
  }
}
