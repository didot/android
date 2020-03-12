/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues;

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.ProductFlavorModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.RemoveSdkFromManifestHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.UsesSdk;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.android.sdklib.SdkVersionInfo.*;
import static org.jetbrains.android.facet.AndroidRootUtil.getPrimaryManifestFile;

/**
 * Handles the sync issue when min sdk version is defined in manifest files.
 */
public abstract class SdkInManifestIssuesReporter extends SimpleDeduplicatingSyncIssueReporter {
  public enum SdkProperty {
    MIN("minSdkVersion", LOWEST_ACTIVE_API, UsesSdk::getMinSdkVersion, ProductFlavorModel::minSdkVersion),
    TARGET("targetSdkVersion", HIGHEST_KNOWN_STABLE_API, UsesSdk::getTargetSdkVersion, ProductFlavorModel::targetSdkVersion),
    MAX("maxSdkVersion", HIGHEST_KNOWN_API, UsesSdk::getMaxSdkVersion, ProductFlavorModel::maxSdkVersion)
    ;

    @NotNull private final String myName;
    private final int myDefaultValue;
    @NotNull private final Function<UsesSdk, AndroidAttributeValue<String>> myManifestFunction;
    @NotNull private final Function<ProductFlavorModel, ResolvedPropertyModel> myBuildFileFunction;

    SdkProperty(@NotNull String propertyName,
                int defaultValue,
                @NotNull Function<UsesSdk, AndroidAttributeValue<String>> manifestFunction,
                @NotNull Function<ProductFlavorModel, ResolvedPropertyModel> buildFileFunction) {
      myName = propertyName;
      myDefaultValue = defaultValue;
      myManifestFunction = manifestFunction;
      myBuildFileFunction = buildFileFunction;
    }

    @NotNull
    public String getPropertyName() {
      return myName;
    }

    public int getDefaultValue() {
      return myDefaultValue;
    }

    @NotNull
    public Function<UsesSdk, AndroidAttributeValue<String>> getManifestFunction() {
      return myManifestFunction;
    }

    @NotNull
    public Function<ProductFlavorModel, ResolvedPropertyModel> getBuildFileFunction() {
      return myBuildFileFunction;
    }
  }

  @Override
  protected abstract int getSupportedIssueType();

  @NotNull
  protected abstract SdkProperty getProperty();

  @Override
  @NotNull
  protected OpenFileHyperlink createModuleLink(@NotNull Project project,
                                               @NotNull Module module,
                                               @NotNull List<SyncIssue> syncIssues,
                                               @NotNull VirtualFile buildFile) {
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet != null) {
      Manifest manifest = Manifest.getMainManifest(androidFacet);
      PsiElement element = null;
      if (manifest != null) {
        element = ApplicationManager.getApplication().runReadAction((Computable<PsiElement>)() -> {
          List<UsesSdk> usesSdks = manifest.getUsesSdks();
          if (!usesSdks.isEmpty()) {
            return usesSdks.get(0).getXmlElement();
          }
          return null;
        });
      }

      VirtualFile manifestFile = getPrimaryManifestFile(androidFacet);
      if (manifestFile != null) {
        int lineNumber = element != null ? getLineNumberForElement(project, element) : -1;
        return new OpenFileHyperlink(manifestFile.getPath(), module.getName(), lineNumber, -1);
      }
    }
    return super.createModuleLink(project, module, syncIssues, buildFile);
  }

  @NotNull
  @Override
  protected List<NotificationHyperlink> getCustomLinks(@NotNull Project project,
                                                       @NotNull List<SyncIssue> syncIssues,
                                                       @NotNull List<Module> affectedModules,
                                                       @NotNull Map<Module, VirtualFile> buildFileMap) {
    if (affectedModules.isEmpty()) {
      return ImmutableList.of();
    }
    else {
      return ImmutableList.of(new RemoveSdkFromManifestHyperlink(affectedModules, getProperty()));
    }
  }
}
