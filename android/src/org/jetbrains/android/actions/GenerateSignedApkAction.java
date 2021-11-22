package org.jetbrains.android.actions;

import static org.jetbrains.android.util.AndroidUtils.getApplicationFacets;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.google.wireless.android.vending.developer.signing.tools.extern.export.ExportEncryptedPrivateKeyTool;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import java.util.List;
import org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public class GenerateSignedApkAction extends AnAction {
  public GenerateSignedApkAction() {
    super(AndroidBundle.message(StudioFlags.RUNDEBUG_ANDROID_BUILD_BUNDLE_ENABLED.get() ? "android.generate.signed.apk.action.bundle.text" : "android.generate.signed.apk.action.text"));
  }

  @VisibleForTesting
  static boolean allowBundleSigning(@Nullable Project project) {
    return project != null &&
           StudioFlags.RUNDEBUG_ANDROID_BUILD_BUNDLE_ENABLED.get() &&
           !AndroidProjectInfo.getInstance(project).isApkProject();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;

    List<AndroidFacet> facets = getApplicationFacets(project);
    assert !facets.isEmpty();

    ExportSignedPackageWizard wizard =
      new ExportSignedPackageWizard(project, facets, true, allowBundleSigning(project), new ExportEncryptedPrivateKeyTool());
    wizard.show();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    boolean enabled = project != null && !getApplicationFacets(project).isEmpty() &&
                      /* Available for Gradle projects and legacy IDEA Android projects */
                      (GradleProjectInfo.getInstance(project).isBuildWithGradle() ||
                       !AndroidProjectInfo.getInstance(project).requiresAndroidModel());
    e.getPresentation().setEnabledAndVisible(enabled);
    if (enabled) {
      String actionText = allowBundleSigning(project) ?  "android.generate.signed.apk.action.bundle.text"
                                                      : "android.generate.signed.apk.action.text";
      e.getPresentation().setText(AndroidBundle.message(actionText));
    }
  }
}
