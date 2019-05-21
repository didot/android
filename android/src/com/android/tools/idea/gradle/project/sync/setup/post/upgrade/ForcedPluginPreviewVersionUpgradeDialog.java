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
package com.android.tools.idea.gradle.project.sync.setup.post.upgrade;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.HyperlinkAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;

import static com.android.tools.adtui.HtmlLabel.setUpAsHtmlLabel;
import static com.android.tools.idea.gradle.project.sync.setup.post.upgrade.UpgradeDialogMetricUtilsKt.recordUpgradeDialogEvent;
import static com.google.wireless.android.sdk.stats.GradlePluginUpgradeDialogStats.UserAction.CANCEL;
import static com.google.wireless.android.sdk.stats.GradlePluginUpgradeDialogStats.UserAction.OK;
import static com.intellij.ide.BrowserUtil.browse;
import static javax.swing.Action.NAME;

public class ForcedPluginPreviewVersionUpgradeDialog extends DialogWrapper {
  private JPanel myCenterPanel;
  private JEditorPane myMessagePane;

  @NotNull private final Project myProject;
  @NotNull private final String myMessage;
  @NotNull private final String myRecommendedPluginVersion;
  @Nullable private final String myCurrentPluginVersion;

  private static TestDialog ourTestImplementation = TestDialog.DEFAULT;

  @TestOnly
  public static TestDialog setTestDialog(@NotNull TestDialog newValue) {
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      getLog().assertTrue(application.isUnitTestMode(), "This method is available for tests only");
    }
    TestDialog oldValue = ourTestImplementation;
    ourTestImplementation = newValue;
    return oldValue;
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(ForcedPluginPreviewVersionUpgradeDialog.class);
  }

  public ForcedPluginPreviewVersionUpgradeDialog(@NotNull Project project, @NotNull AndroidPluginInfo pluginInfo) {
    super(project);
    myProject = project;

    boolean experimental = pluginInfo.isExperimental();
    String pluginType = experimental ? "Experimental " : "";
    setTitle("Android Gradle " + pluginType + "Plugin Update Required");
    init();

    setUpAsHtmlLabel(myMessagePane);
    String pluginVersion = pluginInfo.getLatestKnownPluginVersionProvider().get();
    myRecommendedPluginVersion = pluginVersion;
    GradleVersion currentVersion = pluginInfo.getPluginVersion();
    myCurrentPluginVersion = (currentVersion != null) ? pluginInfo.getPluginVersion().toString() : null;
    myMessage = "<b>The project is using an incompatible version of the " + AndroidPluginInfo.DESCRIPTION + ".</b><br/><br/>" +
                "To continue opening the project, the IDE will update the plugin to version " + pluginVersion + ".<br/><br/>" +
                "You can learn more about this version of the plugin from the " +
                "<a href='https://developer.android.com/studio/" + (experimental ? "build/experimental-plugin.html" : "releases/gradle-plugin.html") +
                "'>release notes</a>.<br/><br/>";
    myMessagePane.setText(myMessage);
    myMessagePane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        browse(e.getURL());
      }
    });
  }

  @VisibleForTesting
  @NotNull
  String getDisplayedMessage() {
    return myMessage;
  }

  @Override
  @NotNull
  protected JComponent createCenterPanel() {
    return myCenterPanel;
  }

  @Override
  public void doCancelAction() {
    recordUpgradeDialogEvent(myProject, myCurrentPluginVersion, myRecommendedPluginVersion, CANCEL);
    super.doCancelAction();
  }

  @Override
  protected void doOKAction() {
    recordUpgradeDialogEvent(myProject, myCurrentPluginVersion, myRecommendedPluginVersion, OK);
    super.doOKAction();
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

  @Override
  public boolean showAndGet() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      int result = ourTestImplementation.show(myMessage);
      Disposer.dispose(getDisposable());
      return result == OK_EXIT_CODE;
    }
    return super.showAndGet();
  }
}
