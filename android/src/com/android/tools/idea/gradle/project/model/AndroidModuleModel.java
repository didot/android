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
package com.android.tools.idea.gradle.project.model;

import static com.android.SdkConstants.ANDROIDX_DATA_BINDING_LIB_ARTIFACT;
import static com.android.SdkConstants.DATA_BINDING_LIB_ARTIFACT;
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static com.android.builder.model.AndroidProject.ARTIFACT_UNIT_TEST;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_TEST;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.dependsOn;
import static com.android.tools.lint.client.api.LintClient.getGradleDesugaring;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.util.ArrayUtil.contains;

import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaCompileOptions;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.ProjectSyncIssues;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.TestOptions;
import com.android.builder.model.Variant;
import com.android.ide.common.gradle.model.GradleModelConverterUtil;
import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeAndroidProjectImpl;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.ide.common.gradle.model.level2.IdeDependencies;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.ide.common.repository.GradleVersion;
import com.android.projectmodel.DynamicResourceValue;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.databinding.DataBindingMode;
import com.android.tools.idea.gradle.AndroidGradleClassJarProvider;
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.ClassJarProvider;
import com.android.tools.lint.detector.api.Desugaring;
import com.android.tools.lint.detector.api.Lint;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.serialization.PropertyMapping;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

/**
 * Contains Android-Gradle related state necessary for configuring an IDEA project based on a user-selected build variant.
 */
public class AndroidModuleModel implements AndroidModel, ModuleModel {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 4L;

  private static final String[] TEST_ARTIFACT_NAMES = {ARTIFACT_UNIT_TEST, ARTIFACT_ANDROID_TEST};
  private static final AndroidVersion NOT_SPECIFIED = new AndroidVersion(0, null);

  @NotNull private ProjectSystemId myProjectSystemId;
  @NotNull private String myModuleName;
  @NotNull private File myRootDirPath;
  @NotNull private IdeAndroidProject myAndroidProject;

  @NotNull private transient AndroidModelFeatures myFeatures;
  @Nullable private transient GradleVersion myModelVersion;
  @NotNull private String mySelectedVariantName;

  private transient VirtualFile myRootDir;

  @Nullable private Boolean myOverridesManifestPackage;
  @Nullable private transient AndroidVersion myMinSdkVersion;

  @NotNull private Map<String, BuildTypeContainer> myBuildTypesByName = new HashMap<>();
  @NotNull private Map<String, ProductFlavorContainer> myProductFlavorsByName = new HashMap<>();
  @NotNull private Map<String, IdeVariant> myVariantsByName = new HashMap<>();
  @NotNull private Set<File> myExtraGeneratedSourceFolders = new HashSet<>();

  @Nullable
  public static AndroidModuleModel get(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? get(facet) : null;
  }

  @Nullable
  public static AndroidModuleModel get(@NotNull AndroidFacet androidFacet) {
    AndroidModel androidModel = androidFacet.getConfiguration().getModel();
    return androidModel instanceof AndroidModuleModel ? (AndroidModuleModel)androidModel : null;
  }

  public static AndroidModuleModel create(@NotNull String moduleName,
                                          @NotNull File rootDirPath,
                                          @NotNull AndroidProject androidProject,
                                          @NotNull String selectedVariantName,
                                          @NotNull IdeDependenciesFactory dependenciesFactory) {
    return create(moduleName, rootDirPath, androidProject, selectedVariantName, dependenciesFactory, null, null);
  }

  public static AndroidModuleModel create(@NotNull String moduleName,
                                          @NotNull File rootDirPath,
                                          @NotNull IdeAndroidProject androidProject,
                                          @NotNull String variantName) {
    return new AndroidModuleModel(moduleName, rootDirPath, androidProject, variantName);
  }

  /**
   * @param moduleName          the name of the IDEA module, created from {@code delegate}.
   * @param rootDirPath         the root directory of the imported Android-Gradle project.
   * @param androidProject      imported Android-Gradle project.
   * @param variantName         the name of selected variant.
   * @param dependenciesFactory the factory instance to create {@link IdeDependencies}.
   * @param variantsToAdd       list of variants to add that were requested but not present in the {@link AndroidProject}.
   * @param syncIssues          Model containing all sync issues that were produced by Gradle.
   */
  public static AndroidModuleModel create(@NotNull String moduleName,
                                          @NotNull File rootDirPath,
                                          @NotNull AndroidProject androidProject,
                                          @NotNull String variantName,
                                          @NotNull IdeDependenciesFactory dependenciesFactory,
                                          @Nullable Collection<Variant> variantsToAdd,
                                          @Nullable ProjectSyncIssues syncIssues) {
    IdeAndroidProject ideAndroidProject = IdeAndroidProjectImpl.create(androidProject, dependenciesFactory, variantsToAdd, syncIssues);
    return new AndroidModuleModel(moduleName, rootDirPath, ideAndroidProject, variantName);
  }

  @PropertyMapping({"myModuleName", "myRootDirPath", "myAndroidProject", "mySelectedVariantName"})
  private AndroidModuleModel(@NotNull String moduleName,
                             @NotNull File rootDirPath,
                             @NotNull IdeAndroidProject androidProject,
                             @NotNull String variantName) {
    myAndroidProject = androidProject;

    myProjectSystemId = GRADLE_SYSTEM_ID;
    myModuleName = moduleName;
    myRootDirPath = rootDirPath;
    parseAndSetModelVersion();
    myFeatures = new AndroidModelFeatures(myModelVersion);

    populateBuildTypesByName();
    populateProductFlavorsByName();
    populateVariantsByName();

    mySelectedVariantName = findVariantToSelect(variantName);
  }


  private void populateBuildTypesByName() {
    for (BuildTypeContainer container : myAndroidProject.getBuildTypes()) {
      String name = container.getBuildType().getName();
      myBuildTypesByName.put(name, container);
    }
  }

  private void populateProductFlavorsByName() {
    for (ProductFlavorContainer container : myAndroidProject.getProductFlavors()) {
      String name = container.getProductFlavor().getName();
      myProductFlavorsByName.put(name, container);
    }
  }

  private void populateVariantsByName() {
    myAndroidProject.forEachVariant(variant -> myVariantsByName.put(variant.getName(), variant));
  }

  /**
   * @deprecated Use {@link #getSelectedMainCompileLevel2Dependencies()}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @NotNull
  public Dependencies getSelectedMainCompileDependencies() {
    AndroidArtifact mainArtifact = getMainArtifact();
    return mainArtifact.getDependencies();
  }

  /**
   * @return Instance of {@link IdeDependencies} from main artifact.
   */
  @NotNull
  public IdeDependencies getSelectedMainCompileLevel2Dependencies() {
    IdeAndroidArtifact mainArtifact = getMainArtifact();
    return mainArtifact.getLevel2Dependencies();
  }

  /**
   * @return Instance of {@link IdeDependencies} from test artifact, or {@code null} if current module has no test artifact.
   */
  @Nullable
  public IdeDependencies getSelectedAndroidTestCompileDependencies() {
    IdeAndroidArtifact androidTestArtifact = getSelectedVariant().getAndroidTestArtifact();
    if (androidTestArtifact == null) {
      // Only variants in the debug build type have an androidTest artifact.
      return null;
    }
    return androidTestArtifact.getLevel2Dependencies();
  }

  @NotNull
  public AndroidModelFeatures getFeatures() {
    return myFeatures;
  }

  @Nullable
  public GradleVersion getModelVersion() {
    return myModelVersion;
  }

  @NotNull
  public IdeAndroidArtifact getMainArtifact() {
    return getSelectedVariant().getMainArtifact();
  }

  @Override
  @NotNull
  public SourceProvider getDefaultSourceProvider() {
    return getAndroidProject().getDefaultConfig().getSourceProvider();
  }

  @Override
  @NotNull
  public List<SourceProvider> getActiveSourceProviders() {
    return getMainSourceProviders(mySelectedVariantName);
  }

  @NotNull
  private List<SourceProvider> getMainSourceProviders(@NotNull String variantName) {
    Variant variant = myVariantsByName.get(variantName);
    if (variant == null) {
      getLogger().error("Unknown variant name '" + variantName + "' found in the module '" + myModuleName + "'");
      return ImmutableList.of();
    }

    List<SourceProvider> providers = new ArrayList<>();
    // Main source provider.
    providers.add(getDefaultSourceProvider());
    // Flavor source providers.
    for (String flavor : variant.getProductFlavors()) {
      ProductFlavorContainer productFlavor = findProductFlavor(flavor);
      assert productFlavor != null;
      providers.add(productFlavor.getSourceProvider());
    }

    // Multi-flavor source provider.
    AndroidArtifact mainArtifact = variant.getMainArtifact();
    SourceProvider multiFlavorProvider = mainArtifact.getMultiFlavorSourceProvider();
    if (multiFlavorProvider != null) {
      providers.add(multiFlavorProvider);
    }

    // Build type source provider.
    BuildTypeContainer buildType = findBuildType(variant.getBuildType());
    assert buildType != null;
    providers.add(buildType.getSourceProvider());

    // Variant  source provider.
    SourceProvider variantProvider = mainArtifact.getVariantSourceProvider();
    if (variantProvider != null) {
      providers.add(variantProvider);
    }

    return providers;
  }

  @NotNull
  public Collection<SourceProvider> getTestSourceProviders(@NotNull Iterable<SourceProviderContainer> containers) {
    return getSourceProvidersForArtifacts(containers, TEST_ARTIFACT_NAMES);
  }

  @Override
  @NotNull
  public List<SourceProvider> getTestSourceProviders() {
    return getTestSourceProviders(mySelectedVariantName, TEST_ARTIFACT_NAMES);
  }

  @NotNull
  public List<SourceProvider> getTestSourceProviders(@NotNull String artifactName) {
    return getTestSourceProviders(mySelectedVariantName, artifactName);
  }

  @NotNull
  private List<SourceProvider> getTestSourceProviders(@NotNull String variantName, @NotNull String... testArtifactNames) {
    validateTestArtifactNames(testArtifactNames);

    // Collect the default config test source providers.
    Collection<SourceProviderContainer> extraSourceProviders = getAndroidProject().getDefaultConfig().getExtraSourceProviders();
    List<SourceProvider> providers = new ArrayList<>(getSourceProvidersForArtifacts(extraSourceProviders, testArtifactNames));

    Variant variant = myVariantsByName.get(variantName);
    assert variant != null;

    // Collect the product flavor test source providers.
    for (String flavor : variant.getProductFlavors()) {
      ProductFlavorContainer productFlavor = findProductFlavor(flavor);
      assert productFlavor != null;
      providers.addAll(getSourceProvidersForArtifacts(productFlavor.getExtraSourceProviders(), testArtifactNames));
    }

    // Collect the build type test source providers.
    BuildTypeContainer buildType = findBuildType(variant.getBuildType());
    assert buildType != null;
    providers.addAll(getSourceProvidersForArtifacts(buildType.getExtraSourceProviders(), testArtifactNames));

    // TODO: Does it make sense to add multi-flavor test source providers?
    // TODO: Does it make sense to add variant test source providers?
    return providers;
  }

  private static void validateTestArtifactNames(@NotNull String[] testArtifactNames) {
    for (String name : testArtifactNames) {
      if (!isTestArtifact(name)) {
        String msg = String.format("'%1$s' is not a test artifact", name);
        throw new IllegalArgumentException(msg);
      }
    }
  }

  private static boolean isTestArtifact(@Nullable String artifactName) {
    return contains(artifactName, TEST_ARTIFACT_NAMES);
  }

  /**
   * @return true if the variant model with given name has been requested before.
   */
  public boolean variantExists(@NotNull String variantName) {
    for (Variant variant : myAndroidProject.getVariants()) {
      if (variantName.equals(variant.getName())) {
        return true;
      }
    }
    return false;
  }

  @Override
  @NotNull
  public List<SourceProvider> getAllSourceProviders() {
    Collection<Variant> variants = myAndroidProject.getVariants();
    List<SourceProvider> providers = new ArrayList<>();

    // Add main source set
    providers.add(getDefaultSourceProvider());

    // Add all flavors
    Collection<ProductFlavorContainer> flavors = myAndroidProject.getProductFlavors();
    for (ProductFlavorContainer flavorContainer : flavors) {
      providers.add(flavorContainer.getSourceProvider());
    }

    // Add the multi-flavor source providers
    for (Variant variant : variants) {
      SourceProvider provider = variant.getMainArtifact().getMultiFlavorSourceProvider();
      if (provider != null) {
        providers.add(provider);
      }
    }

    // Add all the build types
    Collection<BuildTypeContainer> buildTypes = myAndroidProject.getBuildTypes();
    for (BuildTypeContainer btc : buildTypes) {
      providers.add(btc.getSourceProvider());
    }

    // Add all the variant source providers
    for (Variant variant : variants) {
      SourceProvider provider = variant.getMainArtifact().getVariantSourceProvider();
      if (provider != null) {
        providers.add(provider);
      }
    }

    return providers;
  }

  @Override
  @NotNull
  public String getApplicationId() {
    return getSelectedVariant().getMainArtifact().getApplicationId();
  }

  @Override
  @NotNull
  public Set<String> getAllApplicationIds() {
    Set<String> ids = new HashSet<>();
    for (Variant variant : myAndroidProject.getVariants()) {
      String applicationId = variant.getMergedFlavor().getApplicationId();
      if (applicationId != null) {
        ids.add(applicationId);
      }
    }
    return ids;
  }

  @Override
  public Boolean isDebuggable() {
    BuildTypeContainer buildTypeContainer = findBuildType(getSelectedVariant().getBuildType());
    if (buildTypeContainer != null) {
      return buildTypeContainer.getBuildType().isDebuggable();
    }
    return null;
  }

  /**
   * Returns the {@code minSdkVersion} specified by the user (in the default config or product flavors).
   * This is normally the merged value, but for example when using preview platforms, the Gradle plugin
   * will set minSdkVersion and targetSdkVersion to match the level of the compileSdkVersion; in this case
   * we want tools like lint's API check to continue to look for the intended minSdkVersion specified in
   * the build.gradle file
   *
   * @return the {@link AndroidVersion} to use for this Gradle project, or {@code null} if not specified.
   */
  @Override
  @Nullable
  public AndroidVersion getMinSdkVersion() {
    if (myMinSdkVersion == null) {
      ApiVersion minSdkVersion = getSelectedVariant().getMergedFlavor().getMinSdkVersion();
      if (minSdkVersion != null && minSdkVersion.getCodename() != null) {
        ApiVersion defaultConfigVersion = getAndroidProject().getDefaultConfig().getProductFlavor().getMinSdkVersion();
        if (defaultConfigVersion != null) {
          minSdkVersion = defaultConfigVersion;
        }

        List<String> flavors = getSelectedVariant().getProductFlavors();
        for (String flavor : flavors) {
          ProductFlavorContainer productFlavor = findProductFlavor(flavor);
          assert productFlavor != null;
          ApiVersion flavorVersion = productFlavor.getProductFlavor().getMinSdkVersion();
          if (flavorVersion != null) {
            minSdkVersion = flavorVersion;
            break;
          }
        }
      }
      myMinSdkVersion = minSdkVersion != null ? Lint.convertVersion(minSdkVersion, null) : NOT_SPECIFIED;
    }

    return myMinSdkVersion != NOT_SPECIFIED ? myMinSdkVersion : null;
  }

  @Override
  @Nullable
  public AndroidVersion getRuntimeMinSdkVersion() {
    ApiVersion minSdkVersion = getSelectedVariant().getMergedFlavor().getMinSdkVersion();
    return minSdkVersion != null ? Lint.convertVersion(minSdkVersion, null) : null;
  }

  @Override
  @Nullable
  public AndroidVersion getTargetSdkVersion() {
    ApiVersion targetSdkVersion = getSelectedVariant().getMergedFlavor().getTargetSdkVersion();
    return targetSdkVersion != null ? Lint.convertVersion(targetSdkVersion, null) : null;
  }

  /**
   * @return the version code associated with the merged flavor of the selected variant, or {@code null} if none have been set.
   */
  @Override
  @Nullable
  public Integer getVersionCode() {
    Variant variant = getSelectedVariant();
    ProductFlavor flavor = variant.getMergedFlavor();
    return flavor.getVersionCode();
  }

  @NotNull
  public ProjectSystemId getProjectSystemId() {
    return myProjectSystemId;
  }

  @Nullable
  public BuildTypeContainer findBuildType(@NotNull String name) {
    return myBuildTypesByName.get(name);
  }

  @NotNull
  public Set<String> getBuildTypes() {
    return myBuildTypesByName.keySet();
  }

  @NotNull
  public Set<String> getProductFlavors() {
    return myProductFlavorsByName.keySet();
  }

  @Nullable
  public ProductFlavorContainer findProductFlavor(@NotNull String name) {
    return myProductFlavorsByName.get(name);
  }

  @Override
  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  /**
   * @return the path of the root directory of the imported Android-Gradle project. The returned path belongs to the IDEA module containing
   * the build.gradle file.
   */
  @Override
  @NotNull
  public File getRootDirPath() {
    return myRootDirPath;
  }

  /**
   * @return the root directory of the imported Android-Gradle project. The returned path belongs to the IDEA module containing the
   * build.gradle file.
   */
  @Override
  @NotNull
  public VirtualFile getRootDir() {
    if (myRootDir == null) {
      VirtualFile found = findFileByIoFile(myRootDirPath, true);
      // the module's root directory can never be null.
      assert found != null;
      myRootDir = found;
    }
    return myRootDir;
  }

  @Override
  public boolean isGenerated(@NotNull VirtualFile file) {
    VirtualFile buildFolder = findFileByIoFile(myAndroidProject.getBuildFolder(), false);
    if (buildFolder != null && isAncestor(buildFolder, file, false)) {
      return true;
    }
    return false;
  }

  /**
   * @return the imported Android-Gradle project.
   */
  @NotNull
  public IdeAndroidProject getAndroidProject() {
    return myAndroidProject;
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(AndroidModuleModel.class);
  }

  /**
   * @return the selected build variant.
   */
  @NotNull
  public IdeVariant getSelectedVariant() {
    IdeVariant selected = myVariantsByName.get(mySelectedVariantName);
    assert selected != null;
    return selected;
  }

  @Nullable
  public Variant findVariantByName(@NotNull String variantName) {
    return myVariantsByName.get(variantName);
  }

  /**
   * Updates the name of the selected build variant. If the given name does not belong to an existing variant, this method will pick up
   * the first variant, in alphabetical order.
   *
   * @param name the new name.
   */
  public void setSelectedVariantName(@NotNull String name) {
    mySelectedVariantName = findVariantToSelect(name);

    // force lazy recompute
    myOverridesManifestPackage = null;
    myMinSdkVersion = null;
  }

  @VisibleForTesting
  @NotNull
  String findVariantToSelect(@NotNull String variantName) {
    String newVariantName;
    if (myVariantsByName.containsKey(variantName)) {
      newVariantName = variantName;
    }
    else {
      List<String> sorted = new ArrayList<>(myVariantsByName.keySet());
      Collections.sort(sorted);
      assert !myVariantsByName.isEmpty() : "There is no variant model in AndroidModuleModel!";
      newVariantName = sorted.get(0);
    }
    return newVariantName;
  }

  @NotNull
  private static Collection<SourceProvider> getSourceProvidersForArtifacts(@NotNull Iterable<SourceProviderContainer> containers,
                                                                           @NotNull String... artifactNames) {
    Set<SourceProvider> providers = new LinkedHashSet<>();
    for (SourceProviderContainer container : containers) {
      for (String artifactName : artifactNames) {
        if (artifactName.equals(container.getArtifactName())) {
          providers.add(container.getSourceProvider());
          break;
        }
      }
    }
    return providers;
  }

  @NotNull
  public Collection<String> getBuildTypeNames() {
    return myBuildTypesByName.keySet();
  }

  @NotNull
  public Collection<String> getProductFlavorNames() {
    return myProductFlavorsByName.keySet();
  }

  @NotNull
  public Collection<String> getVariantNames() {
    return myAndroidProject.getVariantNames();
  }

  @Nullable
  public LanguageLevel getJavaLanguageLevel() {
    JavaCompileOptions compileOptions = myAndroidProject.getJavaCompileOptions();
    String sourceCompatibility = compileOptions.getSourceCompatibility();
    return LanguageLevel.parse(sourceCompatibility);
  }

  /**
   * Returns whether this project fully overrides the manifest package (with applicationId in the
   * default config or one of the product flavors) in the current variant.
   *
   * @return true if the manifest package is overridden
   */
  @Override
  public boolean overridesManifestPackage() {
    if (myOverridesManifestPackage == null) {
      myOverridesManifestPackage = getAndroidProject().getDefaultConfig().getProductFlavor().getApplicationId() != null;

      Variant variant = getSelectedVariant();

      List<String> flavors = variant.getProductFlavors();
      for (String flavor : flavors) {
        ProductFlavorContainer productFlavor = findProductFlavor(flavor);
        assert productFlavor != null;
        if (productFlavor.getProductFlavor().getApplicationId() != null) {
          myOverridesManifestPackage = true;
          break;
        }
      }
      // The build type can specify a suffix, but it will be merged with the manifest
      // value if not specified in a flavor/default config, so only flavors count
    }

    return myOverridesManifestPackage.booleanValue();
  }

  /**
   * Registers the path of a source folder that has been incorrectly generated outside of the default location (${buildDir}/generated.)
   *
   * @param folderPath the path of the generated source folder.
   */
  public void registerExtraGeneratedSourceFolder(@NotNull File folderPath) {
    myExtraGeneratedSourceFolders.add(folderPath);
  }

  /**
   * @return the paths of generated sources placed at the wrong location (not in ${build}/generated.)
   */
  @NotNull
  public File[] getExtraGeneratedSourceFolderPaths() {
    return myExtraGeneratedSourceFolders.toArray(new File[0]);
  }

  @Nullable
  public Collection<SyncIssue> getSyncIssues() {
    if (getFeatures().isIssueReportingSupported()) {
      return myAndroidProject.getSyncIssues();
    }
    return null;
  }

  /**
   * Returns the {@link IdeAndroidArtifact} that should be used for instrumented testing.
   *
   * <p>For test-only modules this is the main artifact.
   */
  @Nullable
  public IdeAndroidArtifact getArtifactForAndroidTest() {
    return getAndroidProject().getProjectType() == PROJECT_TYPE_TEST ?
           getSelectedVariant().getMainArtifact() :
           getSelectedVariant().getAndroidTestArtifact();
  }

  @Nullable
  public TestOptions.Execution getTestExecutionStrategy() {
    IdeAndroidArtifact artifact = getArtifactForAndroidTest();
    if (artifact != null) {
      TestOptions testOptions = artifact.getTestOptions();
      if (testOptions != null) {
        return testOptions.getExecution();
      }
    }

    return null;
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeObject(myProjectSystemId);
    out.writeObject(myModuleName);
    out.writeObject(myRootDirPath);
    out.writeObject(myAndroidProject);
    out.writeObject(mySelectedVariantName);
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    myProjectSystemId = (ProjectSystemId)in.readObject();
    myModuleName = (String)in.readObject();
    myRootDirPath = (File)in.readObject();
    myAndroidProject = (IdeAndroidProject)in.readObject();
    String variantName = (String)in.readObject();

    parseAndSetModelVersion();
    myFeatures = new AndroidModelFeatures(myModelVersion);

    myBuildTypesByName = new HashMap<>();
    myProductFlavorsByName = new HashMap<>();
    myVariantsByName = new HashMap<>();
    myExtraGeneratedSourceFolders = new HashSet<>();

    populateBuildTypesByName();
    populateProductFlavorsByName();
    populateVariantsByName();

    setSelectedVariantName(variantName);
  }

  private void parseAndSetModelVersion() {
    // Old plugin versions do not return model version.
    myModelVersion = GradleVersion.tryParse(myAndroidProject.getModelVersion());
  }

  /**
   * Returns the source provider for the current build type, which will never be {@code null} for a project backed by an
   * {@link AndroidProject}, and always {@code null} for a legacy Android project.
   *
   * @return the build type source set or {@code null}.
   */
  @NotNull
  public SourceProvider getBuildTypeSourceProvider() {
    Variant selectedVariant = getSelectedVariant();
    BuildTypeContainer buildType = findBuildType(selectedVariant.getBuildType());
    assert buildType != null;
    return buildType.getSourceProvider();
  }

  /**
   * Returns the source providers for the available flavors, which will never be {@code null} for a project backed by an
   * {@link AndroidProject}, and always {@code null} for a legacy Android project.
   *
   * @return the flavor source providers or {@code null} in legacy projects.
   */
  @NotNull
  public List<SourceProvider> getFlavorSourceProviders() {
    Variant selectedVariant = getSelectedVariant();
    List<String> productFlavors = selectedVariant.getProductFlavors();
    List<SourceProvider> providers = new ArrayList<>();
    for (String flavor : productFlavors) {
      ProductFlavorContainer productFlavor = findProductFlavor(flavor);
      assert productFlavor != null;
      providers.add(productFlavor.getSourceProvider());
    }
    return providers;
  }

  public void syncSelectedVariantAndTestArtifact(@NotNull AndroidFacet facet) {
    IdeVariant variant = getSelectedVariant();
    JpsAndroidModuleProperties state = facet.getProperties();
    state.SELECTED_BUILD_VARIANT = variant.getName();

    IdeAndroidArtifact mainArtifact = variant.getMainArtifact();

    // When multi test artifacts are enabled, test tasks are computed dynamically.
    updateGradleTaskNames(state, mainArtifact);
  }

  private static void updateGradleTaskNames(@NotNull JpsAndroidModuleProperties state, @NotNull IdeAndroidArtifact mainArtifact) {
    state.ASSEMBLE_TASK_NAME = mainArtifact.getAssembleTaskName();
    state.COMPILE_JAVA_TASK_NAME = mainArtifact.getCompileTaskName();
    state.AFTER_SYNC_TASK_NAMES = new HashSet<>(mainArtifact.getIdeSetupTaskNames());

    state.ASSEMBLE_TEST_TASK_NAME = "";
    state.COMPILE_JAVA_TEST_TASK_NAME = "";
  }

  /**
   * Returns the source provider specific to the flavor combination, if any.
   *
   * @return the source provider or {@code null}.
   */
  @Nullable
  public SourceProvider getMultiFlavorSourceProvider() {
    AndroidArtifact mainArtifact = getSelectedVariant().getMainArtifact();
    return mainArtifact.getMultiFlavorSourceProvider();
  }

  /**
   * Returns the source provider specific to the variant, if any.
   *
   * @return the source provider or {@code null}.
   */
  @Nullable
  public SourceProvider getVariantSourceProvider() {
    AndroidArtifact mainArtifact = getSelectedVariant().getMainArtifact();
    return mainArtifact.getVariantSourceProvider();
  }

  @Override
  @NotNull
  public DataBindingMode getDataBindingMode() {
    if (dependsOn(this, ANDROIDX_DATA_BINDING_LIB_ARTIFACT)) {
      return DataBindingMode.ANDROIDX;
    }
    if (dependsOn(this, DATA_BINDING_LIB_ARTIFACT)) {
      return DataBindingMode.SUPPORT;
    }
    return DataBindingMode.NONE;
  }

  @Override
  @NotNull
  public ClassJarProvider getClassJarProvider() {
    return new AndroidGradleClassJarProvider();
  }

  @Override
  public boolean isClassFileOutOfDate(@NotNull Module module, @NotNull String fqcn, @NotNull VirtualFile classFile) {
    return testIsClassFileOutOfDate(module, fqcn, classFile);
  }

  public static boolean testIsClassFileOutOfDate(@NotNull Module module, @NotNull String fqcn, @NotNull VirtualFile classFile) {
    Project project = module.getProject();
    GlobalSearchScope scope = module.getModuleWithDependenciesScope();
    VirtualFile sourceFile =
      ApplicationManager.getApplication().runReadAction((Computable<VirtualFile>)() -> {
        PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(fqcn, scope);
        if (psiClass == null) {
          return null;
        }
        PsiFile psiFile = psiClass.getContainingFile();
        if (psiFile == null) {
          return null;
        }
        return psiFile.getVirtualFile();
      });

    if (sourceFile == null) {
      return false;
    }

    // Edited but not yet saved?
    if (FileDocumentManager.getInstance().isFileModified(sourceFile)) {
      return true;
    }

    // Check timestamp
    long sourceFileModified = sourceFile.getTimeStamp();

    // User modifications on the source file might not always result on a new .class file.
    // We use the project modification time instead to display the warning more reliably.
    long lastBuildTimestamp = classFile.getTimeStamp();
    Long projectBuildTimestamp = PostProjectBuildTasksExecutor.getInstance(project).getLastBuildTimestamp();
    if (projectBuildTimestamp != null) {
      lastBuildTimestamp = projectBuildTimestamp;
    }
    return sourceFileModified > lastBuildTimestamp && lastBuildTimestamp > 0L;
  }

  @NotNull
  @Override
  public AaptOptions.Namespacing getNamespacing() {
    return myAndroidProject.getAaptOptions().getNamespacing();
  }

  @NotNull
  @Override
  public Set<Desugaring> getDesugaring() {
    GradleVersion version = getModelVersion();
    if (version == null) {
      return Desugaring.NONE;
    }

    return getGradleDesugaring(version, getJavaLanguageLevel());
  }

  @Override
  @NotNull
  public Map<String, DynamicResourceValue> getResValues() {
    Variant selectedVariant = getSelectedVariant();

    // flavors and default config:
    Map<String, DynamicResourceValue> result =
      new HashMap<>(GradleModelConverterUtil.classFieldsToDynamicResourceValues(selectedVariant.getMergedFlavor().getResValues()));

    BuildTypeContainer buildType = findBuildType(selectedVariant.getBuildType());
    if (buildType != null) {
      result.putAll(GradleModelConverterUtil.classFieldsToDynamicResourceValues(buildType.getBuildType().getResValues()));
    }

    return result;
  }
}
