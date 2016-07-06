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
package com.android.tools.idea.gradle.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.HyperlinkAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;

import static com.android.SdkConstants.GRADLE_EXPERIMENTAL_PLUGIN_LATEST_VERSION;
import static com.android.SdkConstants.GRADLE_PLUGIN_LATEST_VERSION;
import static com.intellij.ide.BrowserUtil.browse;
import static javax.swing.Action.NAME;
import static org.jetbrains.android.util.AndroidUiUtil.setUpAsHtmlLabel;

public class PluginVersionForcedUpdateDialog extends DialogWrapper {
  private JPanel myCenterPanel;
  private JEditorPane myMessagePane;

  public PluginVersionForcedUpdateDialog(@Nullable Project project, boolean usingExperimentalPlugin) {
    super(project);
    setTitle("Android Gradle " + (usingExperimentalPlugin ? "Experimental " : "") + "Plugin Update Required");
    init();

    setUpAsHtmlLabel(myMessagePane);
    String msg = "<b>The project is using an incompatible version of the Android Gradle " +
                 (usingExperimentalPlugin ? "Experimental " : "") + "plugin.</b><br/<br/>" +
                 "To continue opening the project, the IDE will update the Android Gradle " +
                 (usingExperimentalPlugin ? "Experimental " : "") + "plugin to version " +
                 (usingExperimentalPlugin ? GRADLE_EXPERIMENTAL_PLUGIN_LATEST_VERSION : GRADLE_PLUGIN_LATEST_VERSION) + ".<br/><br/>" +
                 "You can learn more about this version of the plugin from the " +
                 "<a href='http://tools.android.com/tech-docs/new-build-system" + (usingExperimentalPlugin ? "/gradle-experimental" : "") +
                 "'>release notes</a>.<br/><br/>";
    myMessagePane.setText(msg);
    myMessagePane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        browse(e.getURL());
      }
    });
  }

  @Override
  @NotNull
  protected JComponent createCenterPanel() {
    return myCenterPanel;
  }

  @Override
  @NotNull
  protected Action getOKAction() {
    Action action = super.getOKAction();
    action.putValue(NAME, "Update");
    return action;
  }

  @Override
  @NotNull
  protected Action getCancelAction() {
    Action action = super.getCancelAction();
    action.putValue(NAME, "Cancel and update manually");
    return action;
  }
}
