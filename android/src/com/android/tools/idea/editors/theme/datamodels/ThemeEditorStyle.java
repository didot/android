/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.datamodels;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a style in ThemeEditor. Knows about the style in all {@link FolderConfiguration}s.
 */
public class ThemeEditorStyle {
  private static final Logger LOG = Logger.getInstance(ThemeEditorStyle.class);

  @NotNull private final ConfigurationManager myManager;
  @NotNull private final ResourceReference myStyleReference;

  /**
   * @deprecated Use {@link #ThemeEditorStyle(ConfigurationManager, ResourceReference)}.
   */
  @Deprecated
  public ThemeEditorStyle(@NotNull ConfigurationManager manager, @NotNull String qualifiedName) {
    this(manager, ResolutionUtils.getStyleReference(qualifiedName));
  }

  public ThemeEditorStyle(@NotNull ConfigurationManager manager, @NotNull ResourceReference styleReference) {
    myManager = manager;
    myStyleReference = styleReference;
  }

  /**
   * Returns the style reference.
   */
  @NotNull
  public final ResourceReference getStyleReference() {
    return myStyleReference;
  }

  /**
   * If the theme's namespace matches the current module, returns the simple name of the theme.
   * Otherwise returns the name of the theme prefixed by the theme's package name.
   *
   * <p>Note: The returned qualified name is intended for displaying to the user and should not
   * be used for anything else.
   */
  @NotNull
  public String getQualifiedName() {
    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getInstance(myManager.getModule());
    if (repositoryManager == null || repositoryManager.getNamespace().equals(myStyleReference.getNamespace())) {
      return myStyleReference.getName();
    }
    return myStyleReference.getQualifiedName();
  }

  /**
   * Returns the style name without namespaces or prefixes.
   */
  @NotNull
  public String getName() {
    return myStyleReference.getName();
  }

  public boolean isFramework() {
    return myStyleReference.getNamespace().equals(ResourceNamespace.ANDROID);
  }

  public boolean isProjectStyle() {
    if (isFramework()) {
      return false;
    }
    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getInstance(myManager.getModule());
    assert repositoryManager != null;
    LocalResourceRepository repository = repositoryManager.getProjectResources();
    return repository.hasResources(myStyleReference.getNamespace(), myStyleReference.getResourceType(), myStyleReference.getName());
  }

  /**
   * Returns all the {@link ResourceItem} where this style is defined. This includes all the definitions in the
   * different resource folders.
   */
  @NotNull
  protected Collection<ResourceItem> getStyleResourceItems() {
    assert !isFramework();

    Module module = myManager.getModule();
    if (isProjectStyle()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      assert facet != null : module.getName() + " module doesn't have AndroidFacet";

      // We need to keep a Set of ResourceItems to override them. The key is the folder configuration + the name.
      HashMap<String, ResourceItem> resourceItems = new HashMap<>();
      ThemeEditorUtils.acceptResourceResolverVisitor(facet, (resources, moduleName, variantName, isSourceSelected) -> {
        if (!isSourceSelected) {
          // Currently we ignore the source sets that are not active.
          // TODO: Process all source sets
          return;
        }

        List<ResourceItem> items =
            resources.getResources(myStyleReference.getNamespace(), myStyleReference.getResourceType(), myStyleReference.getName());

        for (ResourceItem item : items) {
          String key = item.getConfiguration().toShortDisplayString() + "/" + item.getName();
          resourceItems.put(key, item);
        }
      });

      return ImmutableList.copyOf(resourceItems.values());
    }
    else {
      LocalResourceRepository resources = ResourceRepositoryManager.getAppResources(module);
      assert resources != null;
      return resources.getResources(myStyleReference.getNamespace(), myStyleReference.getResourceType(), myStyleReference.getName());
    }
  }

  /**
   * Returns the folder configurations where this style is defined.
   */
  @NotNull
  public Collection<FolderConfiguration> getFolders() {
    if (isFramework()) {
      return ImmutableList.of(new FolderConfiguration());
    }
    ImmutableList.Builder<FolderConfiguration> result = ImmutableList.builder();
    for (ResourceItem styleItem : getStyleResourceItems()) {
      result.add(styleItem.getConfiguration());
    }
    return result.build();
  }

  /**
   * @param configuration FolderConfiguration of the style to lookup
   * @return all values defined in this style with a folder configuration
   */
  @NotNull
  public Collection<StyleItemResourceValue> getValues(@NotNull FolderConfiguration configuration) {
    if (isFramework()) {
      IAndroidTarget target = myManager.getHighestApiTarget();
      assert target != null;

      ResourceRepository frameworkResources =
          myManager.getResolverCache().getFrameworkResources(new FolderConfiguration(), target);
      if (frameworkResources != null) {
        List<ResourceItem> styleItems = frameworkResources.getResources(ResourceNamespace.ANDROID, ResourceType.STYLE, getName());

        for (ResourceItem item : styleItems) {
          if (item.getConfiguration().equals(configuration)) {
            StyleResourceValue style = (StyleResourceValue)item.getResourceValue();
            if (style != null) {
              return style.getDefinedItems();
            }
          }
        }
      }
      throw new IllegalArgumentException("bad folder config " + configuration);
    }

    for (ResourceItem styleItem : getStyleResourceItems()) {
      if (configuration.equals(styleItem.getConfiguration())) {
        StyleResourceValue style = (StyleResourceValue)styleItem.getResourceValue();
        if (style == null) {
          // Style might be null if the value fails to parse.
          continue;
        }
        return style.getDefinedItems();
      }
    }
    throw new IllegalArgumentException("bad folder config " + configuration);
  }

  /**
   * @param configuration FolderConfiguration of the style to lookup
   * @return parent this style with a folder configuration
   */
  @Nullable/*if there is no of this style*/
  public String getParentName(@NotNull FolderConfiguration configuration) {
    if (isFramework()) {
      IAndroidTarget target = myManager.getHighestApiTarget();
      assert target != null;

      ResourceRepository frameworkResources =
          myManager.getResolverCache().getFrameworkResources(new FolderConfiguration(), target);
      if (frameworkResources != null) {
        List<ResourceItem> styleItems = frameworkResources.getResources(ResourceNamespace.ANDROID, ResourceType.STYLE, getName());

        for (ResourceItem item : styleItems) {
          if (item.getConfiguration().equals(configuration)) {
            StyleResourceValue style = (StyleResourceValue)item.getResourceValue();
            if (style != null) {
              return ResolutionUtils.getParentQualifiedName(style);
            }
          }
        }
      }
      throw new IllegalArgumentException("bad folder config " + configuration);
    }

    for (ResourceItem styleItem : getStyleResourceItems()) {
      if (configuration.equals(styleItem.getConfiguration())) {
        StyleResourceValue style = (StyleResourceValue)styleItem.getResourceValue();
        assert style != null;
        return ResolutionUtils.getParentQualifiedName(style);
      }
    }
    throw new IllegalArgumentException("bad folder config " + configuration);
  }

  /**
   * @param configuration FolderConfiguration of the style
   * @return XmlTag of this style coming from folder with corresponding FolderConfiguration
   */
  @Nullable/*if there is no style from this configuration*/
  private XmlTag findXmlTagFromConfiguration(@NotNull FolderConfiguration configuration) {
    for (ResourceItem item : getStyleResourceItems()) {
      if (item.getConfiguration().equals(configuration)) {
        return LocalResourceRepository.getItemTag(myManager.getProject(), item);
      }
    }
    return null;
  }

  /**
   * Finds best to be copied {@link FolderConfiguration}s
   * e.g if style is defined in "port-v8", "port-v18", "port-v22", "night-v20" and desiredApi = 21,
   * then result is {"port-v18", "night-v20"}.
   *
   * @param desiredApi new api level of {@link FolderConfiguration}s after being copied
   * @return Collection of FolderConfigurations which are going to be copied to version desiredApi
   */
  @NotNull
  private ImmutableCollection<FolderConfiguration> findToBeCopied(int desiredApi) {
    // Keeps closest VersionQualifier to 'desiredApi'
    // e.g. desiredApi = 21, "en-port", "en-port-v18", "en-port-v19", "en-port-v22" then
    // bestVersionCopyFrom will contain {"en-port" -> v19}, as it is closest one to v21
    HashMap<FolderConfiguration, VersionQualifier> bestVersionCopyFrom = new HashMap<>();

    for (ResourceItem styleItem : getStyleResourceItems()) {
      FolderConfiguration configuration = FolderConfiguration.copyOf(styleItem.getConfiguration());
      int styleItemVersion = ThemeEditorUtils.getVersionFromConfiguration(configuration);

      // We want to get the best from port-v19 port-v20 port-v23. so we need to remove the version qualifier to compare them
      configuration.setVersionQualifier(null);

      if (styleItemVersion > desiredApi) {
        // VersionQualifier of the 'styleItem' is higher than 'desiredApi'.
        // Thus, we don't need to copy it, we are going to just modify it.
        continue;
      }
      // If 'version' is closer to 'desiredApi' than we have found
      if (!bestVersionCopyFrom.containsKey(configuration) || bestVersionCopyFrom.get(configuration).getVersion() < styleItemVersion) {
        bestVersionCopyFrom.put(configuration, new VersionQualifier(styleItemVersion));
      }
    }

    ImmutableList.Builder<FolderConfiguration> toBeCopied = ImmutableList.builder();

    for (FolderConfiguration key : bestVersionCopyFrom.keySet()) {
      FolderConfiguration configuration = FolderConfiguration.copyOf(key);
      VersionQualifier version = bestVersionCopyFrom.get(key);

      if (version.getVersion() != -1) {
        configuration.setVersionQualifier(version);
      }

      // If configuration = 'en-port-v19' and desiredApi = 'v21', then we should copy 'en-port-v19' to 'en-port-v21'
      // But If configuration = 'en-port-v21' and desiredApi = 'v21, then we don't need to copy
      // Version can't be bigger as we have filtered above
      if (version.getVersion() < desiredApi) {
        toBeCopied.add(configuration);
      }
    }

    return toBeCopied.build();
  }

  /**
   * @param attribute The style attribute name.
   * @return the XmlTag that contains the value for a given attribute in the current style.
   */
  @Nullable/*if the attribute does not exist in this theme*/
  private XmlTag getValueTag(@NotNull XmlTag sourceTag, @NotNull String attribute) {
    if (!isProjectStyle()) {
      // Non project styles do not contain local values.
      return null;
    }

    Ref<XmlTag> resultXmlTag = new Ref<>();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    sourceTag.acceptChildren(new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);

        if (!(element instanceof XmlTag)) {
          return;
        }

        XmlTag tag = (XmlTag)element;
        if (SdkConstants.TAG_ITEM.equals(tag.getName()) && attribute.equals(tag.getAttributeValue(SdkConstants.ATTR_NAME))) {
          resultXmlTag.set(tag);
        }
      }
    });

    return resultXmlTag.get();
  }

  /**
   * Returns a PsiElement of the name attribute for this theme made from a <b>random</b> source XML.
   */
  @Nullable
  public PsiElement getNamePsiElement() {
    Collection<ResourceItem> resources = getStyleResourceItems();
    if (resources.isEmpty()) {
      return null;
    }
    // Any sourceXml will do to get the name attribute from
    XmlTag sourceXml = LocalResourceRepository.getItemTag(myManager.getProject(), resources.iterator().next());
    assert sourceXml != null;
    XmlAttribute nameAttribute = sourceXml.getAttribute("name");
    if (nameAttribute == null) {
      return null;
    }

    XmlAttributeValue attributeValue = nameAttribute.getValueElement();
    if (attributeValue == null) {
      return null;
    }

    return new ValueResourceElementWrapper(attributeValue);
  }

  /**
   * Returns whether this style is public.
   */
  public boolean isPublic() {
    if (!isFramework()) {
      return true;
    }

    IAndroidTarget target = myManager.getTarget();
    if (target == null) {
      LOG.error("Unable to get IAndroidTarget.");
      return false;
    }

    AndroidTargetData androidTargetData = AndroidTargetData.getTargetData(target, myManager.getModule());
    if (androidTargetData == null) {
      LOG.error("Unable to get AndroidTargetData.");
      return false;
    }

    return androidTargetData.isResourcePublic(ResourceType.STYLE.getName(), getName());
  }
}
