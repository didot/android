package com.android.tools.idea.gradle.project;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.settings.*;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

public class AndroidStudioGradleSettingsControlProvider extends GradleSettingsControlProvider {
  @Override
  public String getPlatformPrefix() {
    return "AndroidStudio";
  }

  @Override
  public GradleSystemSettingsControlBuilder getSystemSettingsControlBuilder(@NotNull GradleSettings initialSettings) {
    return new IdeaGradleSystemSettingsControlBuilder(initialSettings).dropVmOptions();
  }

  @Override
  public GradleProjectSettingsControlBuilder getProjectSettingsControlBuilder(@NotNull GradleProjectSettings initialSettings) {
    return new JavaGradleProjectSettingsControlBuilder(initialSettings)
      .dropCustomizableWrapperButton()
      .dropUseBundledDistributionButton()
      .dropGradleJdkComponents()
      .dropUseAutoImportBox()
      .dropResolveModulePerSourceSetCheckBox()
      .dropDelegateBuildCombobox()
      .dropTestRunnerCombobox();
  }
}
