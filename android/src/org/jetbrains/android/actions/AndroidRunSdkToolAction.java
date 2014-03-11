package org.jetbrains.android.actions;

import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.intellij.CommonBundle;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class AndroidRunSdkToolAction extends AnAction {
  public AndroidRunSdkToolAction(String text) {
    super(text);
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null && ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).size() > 0);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    assert project != null;
    doAction(project);
  }

  public void doAction(@NotNull Project project) {
    if (AndroidStudioSpecificInitializer.isAndroidStudio()) {
      File androidHome = DefaultSdks.getDefaultAndroidHome();
      if (androidHome != null) {
        doRunTool(project, androidHome.getPath());
        return;
      }
    }
    List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    assert facets.size() > 0;
    Set<String> sdkSet = new HashSet<String>();
    for (AndroidFacet facet : facets) {
      AndroidSdkData sdkData = facet.getConfiguration().getAndroidSdk();
      if (sdkData != null) {
        sdkSet.add(sdkData.getPath());
      }
    }
    if (sdkSet.size() == 0) {
      Messages.showErrorDialog(project, AndroidBundle.message("specify.platform.error"), CommonBundle.getErrorTitle());
      return;
    }
    String sdkPath = sdkSet.iterator().next();
    if (sdkSet.size() > 1) {
      String[] sdks = ArrayUtil.toStringArray(sdkSet);
      int index = Messages.showChooseDialog(project, AndroidBundle.message("android.choose.sdk.label"),
                                            AndroidBundle.message("android.choose.sdk.title"),
                                            Messages.getQuestionIcon(), sdks, sdkPath);
      if (index < 0) {
        return;
      }
      sdkPath = sdks[index];
    }
    doRunTool(project, sdkPath);
  }

  protected abstract void doRunTool(@NotNull Project project, @NotNull String sdkPath);
}
