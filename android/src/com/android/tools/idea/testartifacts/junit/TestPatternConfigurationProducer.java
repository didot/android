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

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.TestMethodConfigurationProducer;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.testframework.AbstractPatternBasedConfigurationProducer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.android.tools.idea.testartifacts.junit.AndroidJUnitConfigurations.shouldUseAndroidJUnitConfigurations;

/**
 * Android implementation of {@link AbstractPatternBasedConfigurationProducer} so some behaviors can be overridden.
 */
public class TestPatternConfigurationProducer extends AbstractPatternBasedConfigurationProducer<AndroidJUnitConfiguration> implements AndroidJUnitConfigurationProducer {
  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return AndroidJUnitConfigurationType.getInstance();
  }

  @Override
  protected boolean isTestClass(PsiClass psiClass) {
    return JUnitUtil.isTestClass(psiClass);
  }

  @Override
  protected boolean isTestMethod(boolean checkAbstract, PsiMethod psiElement) {
    return JUnitUtil.getTestMethod(psiElement, checkAbstract) != null;
  }

  @Override
  protected boolean setupConfigurationFromContext(@NotNull AndroidJUnitConfiguration configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    LinkedHashSet<String> classes = new LinkedHashSet<>();
    PsiElement element = checkPatterns(context, classes);
    if (element == null) {
      return false;
    }
    sourceElement.set(element);
    JUnitConfiguration.Data data = configuration.getPersistentData();
    data.setPatterns(classes);
    data.TEST_OBJECT = JUnitConfiguration.TEST_PATTERN;
    data.setScope(setupPackageConfiguration(context, configuration, data.getScope()));
    configuration.setGeneratedName();
    Location contextLocation = context.getLocation();
    if (contextLocation instanceof PsiMemberParameterizedLocation) {
      String paramSetName = ((PsiMemberParameterizedLocation)contextLocation).getParamSetName();
      if (paramSetName != null) {
        configuration.setProgramParameters(paramSetName);
      }
    }
    return true;
  }

  @Override
  protected Module findModule(AndroidJUnitConfiguration configuration, Module contextModule) {
    Set<String> patterns = configuration.getPersistentData().getPatterns();
    return findModule(configuration, contextModule, patterns);
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull AndroidJUnitConfiguration unitConfiguration, @NotNull ConfigurationContext context) {
    if (JUnitConfiguration.TEST_PATTERN.equals(unitConfiguration.getPersistentData().TEST_OBJECT)) {
      Set<String> patterns = unitConfiguration.getPersistentData().getPatterns();
      if (isConfiguredFromContext(context, patterns)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
    return !other.isProducedBy(TestMethodConfigurationProducer.class)
           && shouldUseAndroidJUnitConfigurations(self, other);
  }

  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return !other.isProducedBy(TestMethodConfigurationProducer.class)
           && shouldUseAndroidJUnitConfigurations(self, other);
  }
}
