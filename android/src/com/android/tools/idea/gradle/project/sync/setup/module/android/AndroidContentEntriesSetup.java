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
package com.android.tools.idea.gradle.project.sync.setup.module.android;

import static com.android.tools.idea.gradle.util.ContentEntries.findParentContentEntry;
import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static org.jetbrains.jps.model.java.JavaResourceRootType.RESOURCE;
import static org.jetbrains.jps.model.java.JavaResourceRootType.TEST_RESOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.SOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE;

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProvider;
import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeBaseArtifact;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.setup.module.common.ContentEntriesSetup;
import com.android.tools.idea.gradle.util.GeneratedSourceFolders;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import java.io.File;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

final class AndroidContentEntriesSetup extends ContentEntriesSetup {
  static class Factory {
    @NotNull
    AndroidContentEntriesSetup create(@NotNull AndroidModuleModel androidModel,
                                      @NotNull ModifiableRootModel moduleModel,
                                      boolean hasNativeModel) {
      return new AndroidContentEntriesSetup(androidModel, moduleModel, hasNativeModel);
    }
  }

  @NotNull private final AndroidModuleModel myAndroidModel;
  @NotNull private final GeneratedSourceFolders myGeneratedSourceFolders;

  // Native sources from AndroidGradleModel needs to be added only when NativeAndroidGradleModel is not present.
  private final boolean myHasNativeModel;

  private AndroidContentEntriesSetup(@NotNull AndroidModuleModel androidModel,
                                     @NotNull ModifiableRootModel moduleModel,
                                     boolean hasNativeModel) {
    super(moduleModel);
    myAndroidModel = androidModel;
    myHasNativeModel = hasNativeModel;
    myGeneratedSourceFolders = new GeneratedSourceFolders();
  }

  @Override
  public void execute(@NotNull List<ContentEntry> contentEntries) {
    IdeVariant selectedVariant = myAndroidModel.getSelectedVariant();

    IdeAndroidArtifact mainArtifact = selectedVariant.getMainArtifact();
    addSourceFolders(mainArtifact, contentEntries, false);

    for (IdeBaseArtifact artifact : selectedVariant.getTestArtifacts()) {
      addSourceFolders(artifact, contentEntries, true);
    }

    for (String flavorName : selectedVariant.getProductFlavors()) {
      ProductFlavorContainer flavor = myAndroidModel.findProductFlavor(flavorName);
      if (flavor != null) {
        addSourceFolder(flavor, contentEntries);
      }
    }

    String buildTypeName = selectedVariant.getBuildType();
    BuildTypeContainer buildTypeContainer = myAndroidModel.findBuildType(buildTypeName);
    if (buildTypeContainer != null) {
      addSourceFolder(buildTypeContainer.getSourceProvider(), contentEntries, false);

      Collection<SourceProvider> testSourceProviders = myAndroidModel.getTestSourceProviders(buildTypeContainer.getExtraSourceProviders());
      for (SourceProvider testSourceProvider : testSourceProviders) {
        addSourceFolder(testSourceProvider, contentEntries, true);
      }
    }

    ProductFlavorContainer defaultConfig = getAndroidProject().getDefaultConfig();
    addSourceFolder(defaultConfig, contentEntries);

    addExcludedOutputFolders(contentEntries);
    addOrphans();
  }

  private void addSourceFolders(@NotNull IdeBaseArtifact artifact, @NotNull List<ContentEntry> contentEntries, boolean isTest) {
    addGeneratedSourceFolders(artifact, contentEntries, isTest);

    SourceProvider variantSourceProvider = artifact.getVariantSourceProvider();
    if (variantSourceProvider != null) {
      addSourceFolder(variantSourceProvider, contentEntries, isTest);
    }

    SourceProvider multiFlavorSourceProvider = artifact.getMultiFlavorSourceProvider();
    if (multiFlavorSourceProvider != null) {
      addSourceFolder(multiFlavorSourceProvider, contentEntries, isTest);
    }
  }

  private void addGeneratedSourceFolders(@NotNull IdeBaseArtifact artifact, @NotNull List<ContentEntry> contentEntries, boolean isTest) {
    JpsModuleSourceRootType sourceType = getSourceType(isTest);

    GradleVersion modelVersion = myAndroidModel.getModelVersion();
    if (artifact instanceof AndroidArtifact || (modelVersion != null && modelVersion.compareIgnoringQualifiers("1.2") >= 0)) {
      addSourceFolders(GradleUtil.getGeneratedSourceFoldersToUse(artifact, myAndroidModel), contentEntries, sourceType, true);
    }

    if (artifact instanceof AndroidArtifact) {
      sourceType = getResourceSourceType(isTest);
      AndroidArtifact androidArtifact = (AndroidArtifact)artifact;
      addSourceFolders(androidArtifact.getGeneratedResourceFolders(), contentEntries, sourceType, true);
    }
  }

  private void addSourceFolder(@NotNull ProductFlavorContainer flavor, @NotNull List<ContentEntry> contentEntries) {
    addSourceFolder(flavor.getSourceProvider(), contentEntries, false);

    Collection<SourceProvider> testSourceProviders = myAndroidModel.getTestSourceProviders(flavor.getExtraSourceProviders());
    for (SourceProvider sourceProvider : testSourceProviders) {
      addSourceFolder(sourceProvider, contentEntries, true);
    }
  }

  private void addSourceFolder(@NotNull SourceProvider sourceProvider, @NotNull List<ContentEntry> contentEntries, boolean isTest) {
    JpsModuleSourceRootType sourceType = getResourceSourceType(isTest);

    File manifestFolder = sourceProvider.getManifestFile().getParentFile();
    if (manifestFolder != null) {
      ContentEntry containingEntry = findParentContentEntry(manifestFolder, contentEntries.stream());
      if (containingEntry == null) {
        myModuleModel.addContentEntry(pathToIdeaUrl(manifestFolder));
      }
    }

    addSourceFolders(sourceProvider.getResDirectories(), contentEntries, sourceType, false);
    addSourceFolders(sourceProvider.getResourcesDirectories(), contentEntries, sourceType, false);
    addSourceFolders(sourceProvider.getAssetsDirectories(), contentEntries, sourceType, false);

    sourceType = getSourceType(isTest);
    addSourceFolders(sourceProvider.getAidlDirectories(), contentEntries, sourceType, false);
    addSourceFolders(sourceProvider.getJavaDirectories(), contentEntries, sourceType, false);

    if (myHasNativeModel) {
      addSourceFolders(sourceProvider.getCDirectories(), contentEntries, sourceType, false);
      addSourceFolders(sourceProvider.getCppDirectories(), contentEntries, sourceType, false);
    }

    addSourceFolders(sourceProvider.getRenderscriptDirectories(), contentEntries, sourceType, false);
    if (myAndroidModel.getFeatures().isShadersSupported()) {
      addSourceFolders(sourceProvider.getShadersDirectories(), contentEntries, sourceType, false);
    }
  }

  @NotNull
  private static JpsModuleSourceRootType getResourceSourceType(boolean isTest) {
    return isTest ? TEST_RESOURCE : RESOURCE;
  }

  @NotNull
  private static JpsModuleSourceRootType getSourceType(boolean isTest) {
    return isTest ? TEST_SOURCE : SOURCE;
  }

  private void addSourceFolders(@NotNull Collection<File> folderPaths,
                                @NotNull List<ContentEntry> contentEntries,
                                @NotNull JpsModuleSourceRootType type,
                                boolean generated) {
    for (File folderPath : folderPaths) {
      if (generated && !myGeneratedSourceFolders.isFolderGeneratedInCorrectLocation(folderPath, getAndroidProject())) {
        myAndroidModel.registerExtraGeneratedSourceFolder(folderPath);
      }
      addSourceFolder(folderPath, contentEntries, type, generated);
    }
  }

  private void addExcludedOutputFolders(@NotNull List<ContentEntry> contentEntries) {
    File buildFolderPath = getAndroidProject().getBuildFolder();
    ContentEntry parentContentEntry = findParentContentEntry(buildFolderPath, contentEntries.stream());
    if (parentContentEntry != null) {
      addExcludedFolder(parentContentEntry, buildFolderPath);
    }
  }

  @NotNull
  private IdeAndroidProject getAndroidProject() {
    return myAndroidModel.getAndroidProject();
  }
}
