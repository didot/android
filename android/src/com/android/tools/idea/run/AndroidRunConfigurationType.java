// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.run;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.configurations.*;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import icons.StudioIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public final class AndroidRunConfigurationType extends ConfigurationTypeBase {
  public AndroidRunConfigurationType() {
    super("AndroidRunConfigurationType", AndroidBundle.message("android.run.configuration.type.name"), AndroidBundle.message("android.run.configuration.type.description"),
          NotNullLazyValue.createValue(() -> StudioIcons.Shell.Filetree.ANDROID_PROJECT));

    addFactory(new AndroidRunConfigurationFactory(this, "Android App"));
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.AndroidRunConfigurationType";
  }

  public static class AndroidRunConfigurationFactory extends ConfigurationFactory {
    private @NotNull String myId;

    protected AndroidRunConfigurationFactory(@NotNull ConfigurationType type, @NotNull String id) {
      super(type);
      myId = id;
    }

    @NotNull
    @Override
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new AndroidRunConfiguration(project, this);
    }

    @NotNull
    @Override
    public RunConfigurationSingletonPolicy getSingletonPolicy() {
      return RunConfigurationSingletonPolicy.MULTIPLE_INSTANCE;
    }

    @Override
    public boolean isApplicable(@NotNull Project project) {
      return ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID);
    }

    @Override
    public void configureBeforeRunTaskDefaults(Key<? extends BeforeRunTask> providerID, BeforeRunTask task) {
      // Disable the default Make compile step for this run configuration type
      if (CompileStepBeforeRun.ID.equals(providerID)) {
        task.setEnabled(false);
      }
    }

    @Override
    public @NotNull String getId() {
      return myId;
    }
  }

  public static AndroidRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(AndroidRunConfigurationType.class);
  }

  public ConfigurationFactory getFactory() {
    return getConfigurationFactories()[0];
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
