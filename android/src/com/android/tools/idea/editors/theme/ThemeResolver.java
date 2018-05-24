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
package com.android.tools.idea.editors.theme;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourceValueMap;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.configurations.ResourceResolverCache;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.ide.common.resources.ResourceResolver.THEME_NAME;
import static com.android.ide.common.resources.ResourceResolver.THEME_NAME_DOT;
import static com.google.common.collect.Maps.newHashMapWithExpectedSize;

/**
 * Resolves themes for a given configuration.
 */
public class ThemeResolver {
  private final Map<ResourceReference, ConfiguredThemeEditorStyle> myThemesByStyle = new HashMap<>();
  private final ImmutableList<ConfiguredThemeEditorStyle> myFrameworkThemes;
  private final ImmutableList<ConfiguredThemeEditorStyle> myLocalThemes;
  private final ImmutableList<ConfiguredThemeEditorStyle> myExternalLibraryThemes;

  private final Configuration myConfiguration;
  private final ResourceResolver myResolver;

  public ThemeResolver(@NotNull Configuration configuration) {
    myConfiguration = configuration;

    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getOrCreateInstance(configuration.getModule());
    if (repositoryManager == null) {
      throw new IllegalArgumentException("\"" + configuration.getModule().getName() + "\" is not an Android module");
    }

    myResolver = configuration.getResourceResolver();
    if (myResolver == null) {
      throw new IllegalArgumentException("Acquired ResourceResolver is null, not an Android module?");
    }

    myFrameworkThemes = fillThemeResolverFromStyles(resolveFrameworkThemes());

    ImmutableList.Builder<ConfiguredThemeEditorStyle> localThemes = ImmutableList.builder();
    for (Pair<StyleResourceValue, Module> pair : resolveLocallyDefinedModuleThemes()) {
      ConfiguredThemeEditorStyle theme = constructThemeFromResourceValue(pair.getFirst(), pair.getSecond());
      if (theme != null) {
        localThemes.add(theme);
      }
    }
    myLocalThemes = localThemes.build();

    // resolveNonFrameworkThemes() returns all themes available from the current module, including library themes.
    // Because all local themes would be added at previous step to myLocalThemes, they'll be ignored
    // at this step, and all that we've got here is library themes.
    myExternalLibraryThemes = fillThemeResolverFromStyles(resolveNonFrameworkThemes());
  }

  /**
   * Creates a ThemeEditorStyle instance stored in ThemeResolver, which can be added to one of theme lists.
   *
   * @returns The style, or null if theme with this name was already added or resolution has failed
   */
  @Nullable
  private ConfiguredThemeEditorStyle constructThemeFromResourceValue(@NotNull StyleResourceValue value, @Nullable Module sourceModule) {
    ResourceReference styleReference = value.asReference();

    if (myThemesByStyle.containsKey(styleReference)) {
      return null;
    }

    ConfiguredThemeEditorStyle theme = ResolutionUtils.getThemeEditorStyle(myConfiguration, styleReference, sourceModule);
    if (theme != null) {
      myThemesByStyle.put(styleReference, theme);
    }

    return theme;
  }

  private ImmutableList<ConfiguredThemeEditorStyle> fillThemeResolverFromStyles(@NotNull List<StyleResourceValue> source) {
    ImmutableList.Builder<ConfiguredThemeEditorStyle> builder = ImmutableList.builder();

    for (StyleResourceValue value : source) {
      ConfiguredThemeEditorStyle theme = constructThemeFromResourceValue(value, null);
      if (theme != null) {
        builder.add(theme);
      }
    }

    return builder.build();
  }

  @NotNull
  private List<StyleResourceValue> resolveFrameworkThemes() {
    ConfigurationManager configurationManager = myConfiguration.getConfigurationManager();
    ResourceResolverCache resolverCache = configurationManager.getResolverCache();

    IAndroidTarget target = myConfiguration.getTarget();
    if (target == null) {
      return Collections.emptyList();
    }

    Map<ResourceType, ResourceValueMap> resources = resolverCache.getConfiguredFrameworkResources(target, myConfiguration.getFullConfig());
    ResourceValueMap styles = resources.get(ResourceType.STYLE);
    return getFrameworkThemes(styles);
  }

  /**
   * Resolves all non-framework themes available from module of passed Configuration
   */
  @NotNull
  private List<StyleResourceValue> resolveNonFrameworkThemes() {
    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getOrCreateInstance(myConfiguration.getModule());
    if (repositoryManager == null) {
      return Collections.emptyList();
    }
    LocalResourceRepository repository = repositoryManager.getAppResources(true);
    return getNonFrameworkThemes(repository.getConfiguredResources(repositoryManager.getNamespace(),
                                                                   ResourceType.STYLE,
                                                                   myConfiguration.getFullConfig()));
  }

  /**
   * Resolves all themes available from passed Configuration's source module and its dependencies which are defined
   * in the current project (doesn't include themes available from libraries)
   */
  @NotNull
  private List<Pair<StyleResourceValue, Module>> resolveLocallyDefinedModuleThemes() {
    Module module = myConfiguration.getModule();
    List<Pair<StyleResourceValue, Module>> result = new ArrayList<>();

    fillModuleResources(module, ResourceRepositoryManager.getModuleResources(module), result);

    List<AndroidFacet> allAndroidDependencies = AndroidUtils.getAllAndroidDependencies(module, false);
    for (AndroidFacet facet : allAndroidDependencies) {
      fillModuleResources(facet.getModule(), ResourceRepositoryManager.getModuleResources(facet), result);
    }

    return result;
  }

  private void fillModuleResources(@NotNull Module module,
                                   @Nullable LocalResourceRepository repository,
                                   @NotNull List<Pair<StyleResourceValue, Module>> sink) {
    if (repository == null) {
      return;
    }

    ResourceNamespace namespace = ((SingleNamespaceResourceRepository) repository).getNamespace();
    ResourceValueMap configuredResources = repository.getConfiguredResources(namespace,
                                                                             ResourceType.STYLE,
                                                                             myConfiguration.getFullConfig());
    for (StyleResourceValue value : getNonFrameworkThemes(configuredResources)) {
      sink.add(Pair.create(value, module));
    }
  }

  @NotNull
  private List<StyleResourceValue> getNonFrameworkThemes(@NotNull ResourceValueMap styles) {
    // Collect the themes out of all the styles.
    Collection<ResourceValue> values = styles.values();
    List<StyleResourceValue> themes = new ArrayList<>(values.size());

    Map<ResourceValue, Boolean> cache = newHashMapWithExpectedSize(values.size());
    for (ResourceValue value : values) {
      if (value instanceof StyleResourceValue) {
        StyleResourceValue styleValue = (StyleResourceValue)value;
        if (myResolver.isTheme(styleValue, cache)) {
          themes.add(styleValue);
        }
      }
    }
    return themes;
  }

  @NotNull
  private static List<StyleResourceValue> getFrameworkThemes(@NotNull ResourceValueMap styles) {
    // Collect the themes out of all the styles.
    Collection<ResourceValue> values = styles.values();
    List<StyleResourceValue> themes = new ArrayList<>(values.size());

    for (ResourceValue value : values) {
      String name = value.getName();
      if (name.startsWith(THEME_NAME_DOT) || name.equals(THEME_NAME)) {
        themes.add((StyleResourceValue)value);
      }
    }
    return themes;
  }

  /**
   * @deprecated Use {@link #getTheme(ResourceReference)}.
   */
  @Deprecated
  @Nullable
  public ConfiguredThemeEditorStyle getTheme(@NotNull String themeName) {
    ResourceReference styleReference = ResolutionUtils.getStyleReference(themeName);
    return myThemesByStyle.get(styleReference);
  }

  /**
   * Returns the configured theme given a style reference.
   *
   * @param styleReference the reference to the style to get the theme for
   * @return the theme, or null if there is no theme matching the style reference
   */
  @Nullable
  public ConfiguredThemeEditorStyle getTheme(@NotNull ResourceReference styleReference) {
    assert styleReference.getResourceType() == ResourceType.STYLE;
    return myThemesByStyle.get(styleReference);
  }

  /**
   * Returns the list of themes available from the module passed Configuration comes from and all its dependencies.
   */
  @NotNull
  public ImmutableList<ConfiguredThemeEditorStyle> getLocalThemes() {
    return myLocalThemes;
  }

  /**
   * Returns the list of themes that come from external libraries (e.g. AppCompat)
   */
  @NotNull
  public ImmutableList<ConfiguredThemeEditorStyle> getExternalLibraryThemes() {
    return myExternalLibraryThemes;
  }

  /**
   * Returns the list of available framework themes.
   */
  @NotNull
  public ImmutableList<ConfiguredThemeEditorStyle> getFrameworkThemes() {
    return myFrameworkThemes;
  }

  public int getThemesCount() {
    return myFrameworkThemes.size() + myExternalLibraryThemes.size() + myLocalThemes.size();
  }

  @NotNull
  Configuration getConfiguration() {
    return myConfiguration;
  }
}
