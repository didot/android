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
package com.android.tools.idea.gradle.run;

import com.android.builder.model.AndroidProject;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceUrl;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.idea.fd.BuildSelection;
import com.android.tools.idea.fd.FileChangeListener;
import com.android.tools.idea.fd.InstantRunContext;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.gradle.InstantRunGradleUtils;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.res.AppResourceRepository;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.google.common.base.Charsets.UTF_8;

public class GradleInstantRunContext implements InstantRunContext {
  private final String myApplicationId;
  private final AndroidFacet myFacet;
  private final AndroidGradleModel myModel;
  private BuildSelection myBuildChoice;

  public GradleInstantRunContext(@NotNull String applicationId, @NotNull AndroidFacet appFacet) {
    myApplicationId = applicationId;
    myFacet = appFacet;
    myModel = AndroidGradleModel.get(appFacet);
  }

  @Nullable
  @Override
  public InstantRunBuildInfo getInstantRunBuildInfo() {
    return InstantRunGradleUtils.getBuildInfo(myModel);
  }

  @Override
  public void setBuildSelection(@NotNull BuildSelection buildSelection) {
    myBuildChoice = buildSelection;
  }

  @Nullable
  @Override
  public BuildSelection getBuildSelection() {
    return myBuildChoice;
  }

  @NotNull
  @Override
  public String getApplicationId() {
    return myApplicationId;
  }

  @NotNull
  @Override
  public HashCode getManifestResourcesHash() {
    Document manifest = MergedManifest.get(myFacet).getDocument();
    if (manifest == null || manifest.getDocumentElement() == null) {
      return HashCode.fromInt(0);
    }

    final Hasher hasher = Hashing.goodFastHash(32).newHasher();
    Set<ResourceUrl> appResourceReferences = getAppResourceReferences(manifest.getDocumentElement());
    AppResourceRepository appResources = AppResourceRepository.getAppResources(myFacet, true);

    // read action needed when reading the values for app resources
    ApplicationManager.getApplication().runReadAction(() -> {
      hashResources(appResourceReferences, appResources, hasher);
    });

    return hasher.hash();
  }

  @VisibleForTesting
  static SortedSet<ResourceUrl> getAppResourceReferences(@NotNull Element element) {
    SortedSet<ResourceUrl> refs = new TreeSet<>(new Comparator<ResourceUrl>() {
      @Override
      public int compare(ResourceUrl o1, ResourceUrl o2) {
        return o1.toString().compareTo(o2.toString());
      }
    });
    addAppResourceReferences(element, refs);
    return refs;
  }

  private static void addAppResourceReferences(@NotNull Element element, @NotNull Set<ResourceUrl> refs) {
    NamedNodeMap attributes = element.getAttributes();
    if (attributes != null) {
      for (int i = 0, n = attributes.getLength(); i < n; i++) {
        Node attribute = attributes.item(i);
        String value = attribute.getNodeValue();
        if (value.startsWith(PREFIX_RESOURCE_REF)) {
          ResourceUrl url = ResourceUrl.parse(value);
          if (url != null && !url.framework) {
            refs.add(url);
          }
        }
      }
    }

    NodeList children = element.getChildNodes();
    for (int i = 0, n = children.getLength(); i < n; i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE) {
        addAppResourceReferences((Element)child, refs);
      }
    }
  }

  private static void hashResources(@NotNull Set<ResourceUrl> appResources, @NotNull AppResourceRepository resources, @NotNull Hasher hasher) {
    for (ResourceUrl url : appResources) {
      List<ResourceItem> items = resources.getResourceItem(url.type, url.name);
      if (items == null) {
        continue;
      }

      for (ResourceItem item : items) {
        ResourceValue resourceValue = item.getResourceValue(false);
        if (resourceValue != null) {
          String text = resourceValue.getValue();
          if (text != null) {
            hasher.putString(text, UTF_8);
          }
        }
      }
    }
  }

  @Override
  public boolean usesMultipleProcesses() {
    Document manifest = MergedManifest.get(myFacet).getDocument();
    if (manifest == null) {
      return false;
    }

    // TODO: this needs to be fixed to search through the attributes
    return manifestSpecifiesMultiProcess(manifest.getTextContent(), InstantRunManager.ALLOWED_MULTI_PROCESSES);
  }

  @Nullable
  @Override
  public FileChangeListener.Changes getFileChangesAndReset() {
    return InstantRunManager.get(myFacet.getModule().getProject()).getChangesAndReset();
  }

  @NotNull
  @Override
  public List<String> getCustomBuildArguments() {
    if (myModel.isLibrary()) {
      return Collections.emptyList();
    }

    AndroidGradleFacet facet = AndroidGradleFacet.getInstance(myFacet.getModule());
    if (facet == null) {
      Logger.getInstance(GradleInstantRunContext.class).warn("Unable to obtain gradle facet for module " + myFacet.getModule().getName());
      return Collections.emptyList();
    }

    // restrict the variants that get configured
    return ImmutableList.of(AndroidGradleSettings.createProjectProperty(AndroidProject.PROPERTY_RESTRICT_VARIANT_NAME,
                                                                        myModel.getSelectedVariant().getName()),
                            AndroidGradleSettings.createProjectProperty(AndroidProject.PROPERTY_RESTRICT_VARIANT_PROJECT,
                                                                        facet.getConfiguration().GRADLE_PROJECT_PATH));
  }

  /**
   * Returns whether the given manifest file uses multiple processes other than the specified ones.
   */
  static boolean manifestSpecifiesMultiProcess(@NotNull String manifest, @NotNull Set<String> allowedProcesses) {
    Matcher m = Pattern.compile("android:process\\s?=\\s?\"(.*)\"").matcher(manifest);
    while (m.find()) {
      String group = m.group(1);
      if (!allowedProcesses.contains(group)) {
        return true;
      }
    }

    return false;
  }
}
