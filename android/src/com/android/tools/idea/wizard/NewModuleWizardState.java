/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.wizard;

import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.SdkManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.templates.RepositoryUrls.*;
import static com.android.tools.idea.templates.TemplateMetadata.*;

/**
 * Value object which holds the current state of the wizard pages for the
 * {@link com.android.tools.idea.wizard.NewModuleWizard}
 */
public class NewModuleWizardState extends TemplateWizardState {
  public static final String ATTR_CREATE_ACTIVITY = "createActivity";
  public static final String ATTR_PROJECT_LOCATION = "projectLocation";
  public static final String APP_NAME = "app";
  public static final String LIB_NAME = "lib";

  /**
   * State for the template wizard, used to embed an activity template
   */
  protected final TemplateWizardState myActivityTemplateState;

  /**
   * State for the page that lets users create custom launcher icons
   */
  protected final AssetStudioWizardState myLauncherIconState;

  /**
   * True if the module being created is an Android module (as opposed to a generic Java module with no Android support)
   */
  protected boolean myIsAndroidModule;

  public NewModuleWizardState() {
    myActivityTemplateState = new TemplateWizardState();
    myLauncherIconState = new AssetStudioWizardState();

    myHidden.add(ATTR_PROJECT_LOCATION);
    myHidden.remove(ATTR_IS_LIBRARY_MODULE);
    myHidden.add(ATTR_APP_TITLE);

    put(ATTR_IS_LAUNCHER, false);
    put(ATTR_CREATE_ICONS, true);
    put(ATTR_IS_NEW_PROJECT, true);
    put(ATTR_CREATE_ACTIVITY, true);
    put(ATTR_IS_LIBRARY_MODULE, false);

    final SdkManager sdkManager = AndroidSdkUtils.tryToChooseAndroidSdk();
    BuildToolInfo buildTool = sdkManager != null ? sdkManager.getLatestBuildTool() : null;
    if (buildTool != null) {
      // If buildTool is null, the template will use buildApi instead, which might be good enough.
      put(ATTR_BUILD_TOOLS_VERSION, buildTool.getRevision().toString());
    }

    if (sdkManager != null) {
      // Gradle expects a platform-neutral path
      put(ATTR_SDK_DIR, FileUtil.toSystemIndependentName(sdkManager.getLocation()));
    }

    myActivityTemplateState.myHidden.add(ATTR_PACKAGE_NAME);
    myActivityTemplateState.myHidden.add(ATTR_APP_TITLE);
    myActivityTemplateState.myHidden.add(ATTR_MIN_API);
    myActivityTemplateState.myHidden.add(ATTR_MIN_API_LEVEL);
    myActivityTemplateState.myHidden.add(ATTR_TARGET_API);
    myActivityTemplateState.myHidden.add(ATTR_BUILD_API);
    myActivityTemplateState.myHidden.add(ATTR_COPY_ICONS);
    myActivityTemplateState.myHidden.add(ATTR_IS_LAUNCHER);
    myActivityTemplateState.myHidden.add(ATTR_PARENT_ACTIVITY_CLASS);
    myActivityTemplateState.myHidden.add(ATTR_ACTIVITY_TITLE);

    updateParameters();
  }

  @NotNull
  public TemplateWizardState getActivityTemplateState() {
    return myActivityTemplateState;
  }

  @NotNull
  public AssetStudioWizardState getLauncherIconState() {
    return myLauncherIconState;
  }

  @Override
  public void setTemplateLocation(@NotNull File file) {
    super.setTemplateLocation(file);
    myIsAndroidModule = myTemplate.getMetadata().getParameter(ATTR_MIN_API) != null;
  }

  /**
   * Call this to update the list of dependencies to be compiled into the template
   */
  public void updateDependencies() {
    // Take care of dependencies selected through the wizard
    Set<String> dependencySet = new HashSet<String>();
    if (myParameters.containsKey(ATTR_DEPENDENCIES_LIST)) {
      dependencySet.addAll((Collection<String>)get(ATTR_DEPENDENCIES_LIST));
    }

    // Support Library
    if ((get(ATTR_FRAGMENTS_EXTRA) != null && Boolean.parseBoolean(get(ATTR_FRAGMENTS_EXTRA).toString())) ||
        (get(ATTR_NAVIGATION_DRAWER_EXTRA) != null && Boolean.parseBoolean(get(ATTR_NAVIGATION_DRAWER_EXTRA).toString()))) {
      dependencySet.add(getLibraryCoordinate(SUPPORT_ID_V4));
    }

    // AppCompat Library
    if (get(ATTR_ACTION_BAR_EXTRA) != null && Boolean.parseBoolean(get(ATTR_ACTION_BAR_EXTRA).toString())) {
      dependencySet.add(getLibraryCoordinate(APP_COMPAT_ID_V7));
    }

    // GridLayout Library
    if (get(ATTR_GRID_LAYOUT_EXTRA) != null && Boolean.parseBoolean(get(ATTR_GRID_LAYOUT_EXTRA).toString())) {
      dependencySet.add(getLibraryCoordinate(GRID_LAYOUT_ID_V7));
    }

    put(ATTR_DEPENDENCIES_LIST, new LinkedList<String>(dependencySet));
  }

  /**
   * Call this to have this state object propagate common parameter values to sub-state objects
   * (i.e. states for other template wizards that are part of the same dialog).
   */
  public void updateParameters() {
    put(ATTR_COPY_ICONS, !Boolean.parseBoolean(get(ATTR_CREATE_ICONS).toString()));
    copyParameters(myParameters, myActivityTemplateState.myParameters, ATTR_PACKAGE_NAME, ATTR_APP_TITLE, ATTR_MIN_API, ATTR_MIN_API_LEVEL,
                   ATTR_TARGET_API, ATTR_BUILD_API, ATTR_COPY_ICONS, ATTR_IS_NEW_PROJECT, ATTR_IS_LAUNCHER, ATTR_CREATE_ACTIVITY,
                   ATTR_CREATE_ICONS, ATTR_IS_GRADLE, ATTR_TOP_OUT, ATTR_PROJECT_OUT, ATTR_SRC_OUT, ATTR_RES_OUT, ATTR_MANIFEST_OUT);
    copyParameters(myParameters, myLauncherIconState.myParameters, ATTR_PACKAGE_NAME, ATTR_APP_TITLE, ATTR_MIN_API, ATTR_MIN_API_LEVEL,
                   ATTR_TARGET_API, ATTR_BUILD_API, ATTR_COPY_ICONS, ATTR_IS_NEW_PROJECT, ATTR_IS_LAUNCHER, ATTR_CREATE_ACTIVITY,
                   ATTR_CREATE_ICONS, ATTR_IS_GRADLE, ATTR_TOP_OUT, ATTR_PROJECT_OUT, ATTR_SRC_OUT, ATTR_RES_OUT, ATTR_MANIFEST_OUT);
  }

  protected void copyParameters(@NotNull Map<String, Object> from, @NotNull Map<String, Object> to, String... keys) {
    for (String key : keys) {
      to.put(key, from.get(key));
    }
  }
}
