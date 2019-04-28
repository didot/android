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
package com.android.tools.idea.testartifacts.junit;

import com.android.tools.idea.AndroidPsiUtils;
import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationProducer;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.PatternConfigurationProducer;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.testartifacts.junit.AndroidJUnitConfigurations.isFromContext;
import static com.android.tools.idea.testartifacts.junit.AndroidJUnitConfigurations.shouldUseAndroidJUnitConfigurations;

/**
 * Android implementation of {@link AbstractTestMethodConfigurationProducer} so some behaviors can be overridden.
 */
final class TestMethodAndroidConfigurationProducer extends JUnitConfigurationProducer implements AndroidJUnitConfigurationProducer {
  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return AndroidJUnitConfigurationType.getInstance();
  }

  @Override
  public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
    return super.isPreferredConfiguration(self, other)
           && shouldUseAndroidJUnitConfigurations(self, other);
  }

  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return super.isPreferredConfiguration(self, other)
           && shouldUseAndroidJUnitConfigurations(self, other);
  }

  @Override
  protected boolean setupConfigurationFromContext(@NotNull JUnitConfiguration configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    if (RunConfigurationProducer.getInstance(PatternConfigurationProducer.class).isMultipleElementsSelected(context)) {
      return false;
    }
    final Location contextLocation = context.getLocation();
    assert contextLocation != null;
    PsiMethod method = getTestMethod(contextLocation);
    if (method == null || method.getContainingClass() == null) {
      return false;
    }
    Location<PsiMethod> methodLocation = MethodLocation.elementInClass(method, method.getContainingClass());
    if (methodLocation == null) return false;

    if (contextLocation instanceof PsiMemberParameterizedLocation) {
      final String paramSetName = ((PsiMemberParameterizedLocation)contextLocation).getParamSetName();
      if (paramSetName != null) {
        configuration.setProgramParameters(paramSetName);
      }
      PsiClass containingClass = ((PsiMemberParameterizedLocation)contextLocation).getContainingClass();
      if (containingClass != null) {
        methodLocation = MethodLocation.elementInClass(methodLocation.getPsiElement(), containingClass);
      }
    }
    sourceElement.set(methodLocation.getPsiElement());
    setupConfigurationModule(context, configuration);
    final Module originalModule = configuration.getConfigurationModule().getModule();
    configuration.beMethodConfiguration(methodLocation);
    configuration.restoreOriginalModule(originalModule);
    JavaRunConfigurationExtensionManager.getInstance().extendCreatedConfiguration(configuration, contextLocation);
    configuration.setForkMode(JUnitConfiguration.FORK_NONE);
    return true;
  }

  private static PsiMethod getTestMethod(final Location<?> location) {
    PsiMethod elementMethod = AndroidPsiUtils.getPsiParentOfType(location.getPsiElement(), PsiMethod.class, false);
    return isTestMethod(elementMethod) ? elementMethod : null;
  }

  private static boolean isTestMethod(@Nullable PsiMethod method) {
    if (method == null) {
      return false;
    }

    PsiClass testClass = method.getContainingClass();
    if (testClass != null && JUnitUtil.isTestClass(testClass)) {
      return new JUnitUtil.TestMethodFilter(testClass).value(method);
    }
    return false;
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull JUnitConfiguration unitConfiguration, @NotNull ConfigurationContext context) {
    return isFromContext(unitConfiguration, context, getConfigurationFactory());
  }
}
