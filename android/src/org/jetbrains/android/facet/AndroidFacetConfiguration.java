/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.facet;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_DYNAMIC_FEATURE;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_FEATURE;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;

import com.android.sdklib.IAndroidTarget;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import java.util.ArrayList;
import java.util.List;
import org.jdom.Element;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidNativeLibData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.AndroidImportableProperty;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidFacetConfiguration implements FacetConfiguration, PersistentStateComponent<JpsAndroidModuleProperties> {
  private static final FacetEditorTab[] NO_EDITOR_TABS = new FacetEditorTab[0];
  private Project myProject;

  private JpsAndroidModuleProperties myProperties = new JpsAndroidModuleProperties();

  public void init(@NotNull Module module, @NotNull VirtualFile contentRoot) {
    init(module, contentRoot.getPath());
  }

  public void init(@NotNull Module module, @NotNull String baseDirectoryPath) {
    final String s = AndroidRootUtil.getPathRelativeToModuleDir(module, baseDirectoryPath);
    if (s == null || s.isEmpty()) {
      return;
    }
    myProperties.GEN_FOLDER_RELATIVE_PATH_APT = '/' + s + myProperties.GEN_FOLDER_RELATIVE_PATH_APT;
    myProperties.GEN_FOLDER_RELATIVE_PATH_AIDL = '/' + s + myProperties.GEN_FOLDER_RELATIVE_PATH_AIDL;
    myProperties.MANIFEST_FILE_RELATIVE_PATH = '/' + s + myProperties.MANIFEST_FILE_RELATIVE_PATH;
    myProperties.RES_FOLDER_RELATIVE_PATH = '/' + s + myProperties.RES_FOLDER_RELATIVE_PATH;
    myProperties.ASSETS_FOLDER_RELATIVE_PATH = '/' + s + myProperties.ASSETS_FOLDER_RELATIVE_PATH;
    myProperties.LIBS_FOLDER_RELATIVE_PATH = '/' + s + myProperties.LIBS_FOLDER_RELATIVE_PATH;
    myProperties.PROGUARD_LOGS_FOLDER_RELATIVE_PATH = '/' + s + myProperties.PROGUARD_LOGS_FOLDER_RELATIVE_PATH;

    for (int i = 0; i < myProperties.RES_OVERLAY_FOLDERS.size(); i++) {
      myProperties.RES_OVERLAY_FOLDERS.set(i, '/' + s + myProperties.RES_OVERLAY_FOLDERS.get(i));
    }
  }

  /**
   * @deprecated use {@link AndroidFacet#getAndroidPlatform()} instead
   */
  @Deprecated
  @Nullable
  public AndroidPlatform getAndroidPlatform() {
    if (myProject == null) return null;
    AndroidFacet androidFacet = ContainerUtil.find(ProjectFacetManager.getInstance(myProject).getFacets(AndroidFacet.ID),
                                                   facet -> facet.getConfiguration() == this);
    return androidFacet != null ? androidFacet.getAndroidPlatform() : null;
  }

  /**
   * @deprecated use {@link AndroidFacet#getAndroidTarget()} instead
   */
  @Nullable
  @Deprecated
  public IAndroidTarget getAndroidTarget() {
    AndroidPlatform platform = getAndroidPlatform();
    return platform != null ? platform.getTarget() : null;
  }

  void setProject(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    JpsAndroidModuleProperties state = getState();
    assert state != null;
    if (state.ALLOW_USER_CONFIGURATION) {
      return new FacetEditorTab[]{new AndroidFacetEditorTab(editorContext, this)};
    } else {
      return new FacetEditorTab[]{new NotEditableAndroidFacetEditorTab()};
    }
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
  }

  public void setAdditionalNativeLibraries(@NotNull List<AndroidNativeLibData> additionalNativeLibraries) {
    myProperties.myNativeLibs = new ArrayList<>(additionalNativeLibraries.size());

    for (AndroidNativeLibData lib : additionalNativeLibraries) {
      JpsAndroidModuleProperties.AndroidNativeLibDataEntry data = new JpsAndroidModuleProperties.AndroidNativeLibDataEntry();
      data.myArchitecture = lib.getArchitecture();
      data.myUrl = VfsUtilCore.pathToUrl(lib.getPath());
      data.myTargetFileName = lib.getTargetFileName();
      myProperties.myNativeLibs.add(data);
    }
  }

  public boolean isImportedProperty(@NotNull AndroidImportableProperty property) {
    return !myProperties.myNotImportedProperties.contains(property);
  }

  public boolean isIncludeAssetsFromLibraries() {
    return myProperties.myIncludeAssetsFromLibraries;
  }

  public void setIncludeAssetsFromLibraries(boolean includeAssetsFromLibraries) {
    myProperties.myIncludeAssetsFromLibraries = includeAssetsFromLibraries;
  }

  public boolean isAppProject() {
    int projectType = getState().PROJECT_TYPE;
    return projectType == PROJECT_TYPE_APP || projectType == PROJECT_TYPE_INSTANTAPP;
  }

  public boolean isAppOrFeature() {
    int projectType = getState().PROJECT_TYPE;
    return projectType == PROJECT_TYPE_APP ||
           projectType == PROJECT_TYPE_INSTANTAPP ||
           projectType == PROJECT_TYPE_FEATURE ||
           projectType == PROJECT_TYPE_DYNAMIC_FEATURE;
  }

  @Nullable
  @Override
  public JpsAndroidModuleProperties getState() {
    return myProperties;
  }

  @Override
  public void loadState(@NotNull JpsAndroidModuleProperties properties) {
    myProperties = properties;
  }

  public boolean canBeDependency() {
    int projectType = getState().PROJECT_TYPE;
    return projectType == PROJECT_TYPE_LIBRARY || projectType == PROJECT_TYPE_FEATURE;
  }

  public boolean isLibraryProject() {
    return getState().PROJECT_TYPE == PROJECT_TYPE_LIBRARY;
  }

  public int getProjectType() {
    return getState().PROJECT_TYPE;
  }

  public void setProjectType(int type) {
    getState().PROJECT_TYPE = type;
  }
}
