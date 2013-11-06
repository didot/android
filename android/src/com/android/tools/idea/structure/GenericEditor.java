/*
 * Copyright 2004-2005 Alexey Efimov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.structure;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.util.ActionRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.Callable;

/**
 * A project structure pane that lets you add and remove dependencies for individual modules. It is largely a wrapper for
 * {@linkplain ModuleDependenciesPanel}
 */
public class GenericEditor<E extends EditorPanel> implements ModuleConfigurationEditor {
  private static final Logger LOG = Logger.getInstance(GenericEditor.class);

  private final String myName;
  private E myPanel;
  private final Callable<E> myPanelFactory;

  public GenericEditor(String name, Callable<E> panelFactory) {
    myName = name;
    myPanelFactory = panelFactory;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    try {
      myPanel = myPanelFactory.call();
    } catch (Exception e) {
      LOG.error("Error while creating dialog", e);
    }
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified();
  }

  @Override
  @Nullable
  public String getHelpTopic() {
    return null;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return myName;
  }

  @Override
  public void saveData() {
  }

  @Override
  public void moduleStateChanged() {
  }

  @Override
  public void apply() throws ConfigurationException {
    try {
      ActionRunner.runInsideWriteAction(new ActionRunner.InterruptibleRunnable() {
        @Override
        public void run() throws Exception {
            myPanel.apply();
          }
      });
    } catch (Exception e) {
      LOG.error("Error while applying changes", e);
    }
  }

  @Override
  public void reset() {
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

}
