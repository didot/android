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
package org.jetbrains.android.dom;

import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.tools.idea.AndroidTextUtils;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.psi.TagToClassMapper;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.DomExtension;
import org.jetbrains.android.dom.animation.InterpolatorElement;
import org.jetbrains.android.dom.animation.fileDescriptions.InterpolatorDomFileDescription;
import org.jetbrains.android.dom.attrs.*;
import org.jetbrains.android.dom.converters.CompositeConverter;
import org.jetbrains.android.dom.converters.ManifestPlaceholderConverter;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.layout.*;
import org.jetbrains.android.dom.manifest.AndroidManifestUtils;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.ManifestElement;
import org.jetbrains.android.dom.manifest.UsesSdk;
import org.jetbrains.android.dom.menu.MenuItem;
import org.jetbrains.android.dom.navigation.NavDestinationElement;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.android.dom.raw.XmlRawResourceElement;
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil;
import org.jetbrains.android.dom.xml.Intent;
import org.jetbrains.android.dom.xml.XmlResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.LayoutViewClassUtils;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.resourceManagers.FrameworkResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.SdkConstants.*;
import static org.jetbrains.android.util.AndroidUtils.SYSTEM_RESOURCE_PACKAGE;
import static org.jetbrains.android.util.AndroidUtils.VIEW_CLASS_NAME;

/**
 * Utility functions for enumerating available children attribute types in the context of a given XML tag.
 *
 * Entry point is {@link #processAttributes(AndroidDomElement, AndroidFacet, boolean, AttributeProcessor)},
 * look for a Javadoc there.
 */
public class AttributeProcessingUtil {
  private static final String PREFERENCE_TAG_NAME = "Preference";

  private static final ImmutableSet<String> SIZE_NOT_REQUIRED_TAG_NAMES =
      ImmutableSet.of(VIEW_MERGE, TABLE_ROW, VIEW_INCLUDE, REQUEST_FOCUS, TAG_LAYOUT, TAG_DATA, TAG_IMPORT, TAG);
  private static final ImmutableSet<String> SIZE_NOT_REQUIRED_PARENT_TAG_NAMES = ImmutableSet.of(
      TABLE_ROW, TABLE_LAYOUT, VIEW_MERGE, GRID_LAYOUT, FQCN_GRID_LAYOUT_V7.oldName(), FQCN_GRID_LAYOUT_V7.newName(),
      CLASS_PERCENT_RELATIVE_LAYOUT, CLASS_PERCENT_FRAME_LAYOUT);

  private AttributeProcessingUtil() {
  }

  private static Logger getLog() {
    return Logger.getInstance(AttributeProcessingUtil.class);
  }

  /**
   * Check whether layout tag attribute with given name should be marked as required.
   * Currently, tests for layout_width and layout_height attribute and marks them as required in appropriate context.
   */
  public static boolean isLayoutAttributeRequired(@NotNull XmlName attributeName, @NotNull DomElement element) {
    // Mark layout_width and layout_height required - if the context calls for it
    String localName = attributeName.getLocalName();
    if (!(ATTR_LAYOUT_WIDTH.equals(localName) || ATTR_LAYOUT_HEIGHT.equals(localName))) {
      return false;
    }

    if ((element instanceof LayoutViewElement || element instanceof Fragment) && NS_RESOURCES.equals(attributeName.getNamespaceKey())) {
      XmlElement xmlElement = element.getXmlElement();
      XmlTag tag = xmlElement instanceof XmlTag ? (XmlTag)xmlElement : null;
      String tagName = tag != null ? tag.getName() : null;

      if (!SIZE_NOT_REQUIRED_TAG_NAMES.contains(tagName) && (tag == null || tag.getAttribute(ATTR_STYLE) == null)) {
        XmlTag parentTag = tag != null ? tag.getParentTag() : null;
        String parentTagName = parentTag != null ? parentTag.getName() : null;

        if (!SIZE_NOT_REQUIRED_PARENT_TAG_NAMES.contains(parentTagName)) {
          return true;
        }
      }
    }

    return false;
  }

  @Nullable
  private static String getNamespaceUriByResourcePackage(@NotNull AndroidFacet facet, @Nullable String resPackage) {
    if (resPackage == null) {
      if (!facet.getConfiguration().isAppProject() || facet.requiresAndroidModel()) {
        return AUTO_URI;
      }
      Manifest manifest = facet.getManifest();
      if (manifest != null) {
        String aPackage = manifest.getPackage().getValue();
        if (aPackage != null && !aPackage.isEmpty()) {
          return URI_PREFIX + aPackage;
        }
      }
    }
    else if (resPackage.equals(SYSTEM_RESOURCE_PACKAGE)) {
      return ANDROID_URI;
    }
    return null;
  }

  private static void registerStyleableAttributes(@NotNull DomElement element,
                                                  @NotNull StyleableDefinition styleable,
                                                  @Nullable String namespace,
                                                  @NotNull AttributeProcessor callback,
                                                  @NotNull Set<XmlName> skippedAttributes) {
    for (AttributeDefinition attrDef : styleable.getAttributes()) {
      String attrName = attrDef.getName();
      XmlName xmlName = new XmlName(attrName, namespace);
      if (skippedAttributes.add(xmlName)) {
        registerAttribute(attrDef, styleable.getName(), namespace, element, callback);
      }
    }
  }

  private static boolean mustBeSoft(@NotNull Converter converter, @NotNull Collection<AttributeFormat> formats) {
    if (converter instanceof CompositeConverter || converter instanceof ResourceReferenceConverter) {
      return false;
    }
    return formats.size() > 1;
  }

  private static void registerAttribute(@NotNull AttributeDefinition attrDef,
                                        @Nullable String parentStyleableName,
                                        @Nullable String namespaceUri,
                                        @NotNull DomElement element,
                                        @NotNull AttributeProcessor callback) {
    String name = attrDef.getName();
    if (!NS_RESOURCES.equals(namespaceUri) && name.startsWith(PREFIX_ANDROID)) {
      // A styleable-definition in the app namespace (user specified or from a library) can include
      // a reference to a platform attribute. In such a case, register it under the android namespace
      // as opposed to the app namespace. See https://code.google.com/p/android/issues/detail?id=171162
      name = name.substring(PREFIX_ANDROID.length());
      namespaceUri = NS_RESOURCES;
    }
    XmlName xmlName = new XmlName(name, namespaceUri);
    DomExtension extension = callback.processAttribute(xmlName, attrDef, parentStyleableName);

    if (extension == null) {
      return;
    }
    Converter converter = AndroidDomUtil.getSpecificConverter(xmlName, element);
    if (converter == null) {
      if (TOOLS_URI.equals(namespaceUri)) {
        converter = ToolsAttributeUtil.getConverter(attrDef);
      }
      else {
        converter = AndroidDomUtil.getConverter(attrDef);

        if (converter != null && element.getParentOfType(Manifest.class, true) != null) {
          converter = new ManifestPlaceholderConverter(converter);
        }
      }
    }

    if (converter != null) {
      extension.setConverter(converter, mustBeSoft(converter, attrDef.getFormats()));
    }

    // Check whether attribute is required. If it is, add an annotation to let
    // IntelliJ know about it so it would be, e.g. inserted automatically on
    // tag completion. If attribute is not required, no additional action is needed.
    if (element instanceof LayoutElement && isLayoutAttributeRequired(xmlName, element) ||
        element instanceof ManifestElement && AndroidManifestUtils.isRequiredAttribute(xmlName, element)) {
      extension.addCustomAnnotation(new RequiredImpl());
    }
  }

  private static void registerAttributes(@NotNull AndroidFacet facet,
                                         @NotNull DomElement element,
                                         @NotNull String styleableName,
                                         @Nullable String resPackage,
                                         @NotNull AttributeProcessor callback,
                                         @NotNull Set<XmlName> skipNames) {
    ResourceManager manager = ModuleResourceManagers.getInstance(facet).getResourceManager(resPackage);
    if (manager == null) {
      return;
    }

    AttributeDefinitions attrDefs = manager.getAttributeDefinitions();
    if (attrDefs == null) {
      return;
    }

    String namespace = getNamespaceUriByResourcePackage(facet, resPackage);
    StyleableDefinition styleable = attrDefs.getStyleableByName(styleableName);
    if (styleable != null) {
      registerStyleableAttributes(element, styleable, namespace, callback, skipNames);
    }
    // It's a good idea to add a warning when styleable not found, to make sure that code doesn't
    // try to use attributes that don't exist. However, current AndroidDomExtender code relies on
    // a lot of "heuristics" that fail quite a lot (like adding a bunch of suffixes to short class names)
    // TODO: add a warning when rest of the code of AndroidDomExtender is cleaned up
  }

  private static void registerAttributesForClassAndSuperclasses(@NotNull AndroidFacet facet,
                                                                @NotNull DomElement element,
                                                                @Nullable PsiClass c,
                                                                @NotNull AttributeProcessor callback,
                                                                @NotNull Set<XmlName> skipNames) {
    while (c != null) {
      String styleableName = c.getName();
      if (styleableName != null) {
        registerAttributes(facet, element, styleableName, getResourcePackage(c), callback, skipNames);
      }
      for (PsiClass additional : getAdditionalAttributesClasses(facet, c)) {
        String additionalStyleableName = additional.getName();
        if (additionalStyleableName != null) {
          registerAttributes(facet, element, additionalStyleableName, getResourcePackage(additional), callback, skipNames);
        }
      }
      c = getSuperclass(c);
    }
  }

  /**
   * Return the classes that hold attributes used in the specified class c.
   * This is for classes from support libaries without attrs.xml like support lib v4.
   */
  private static Collection<PsiClass> getAdditionalAttributesClasses(@NotNull AndroidFacet facet, @NotNull PsiClass c) {
    if (CLASS_NESTED_SCROLL_VIEW.isEquals(StringUtil.notNullize(c.getQualifiedName()))) {
      return Collections.singleton(getViewClassMap(facet).get(SCROLL_VIEW));
    }

    return Collections.emptySet();
  }

  @Nullable
  private static String getResourcePackage(@NotNull PsiClass psiClass) {
    // TODO: Replace this with the namespace of the styleableName when that is available.
    String qualifiedName = psiClass.getQualifiedName();
    return qualifiedName != null &&
           qualifiedName.startsWith(ANDROID_PKG_PREFIX) &&
           !qualifiedName.startsWith(ANDROID_SUPPORT_PKG_PREFIX) &&
           !qualifiedName.startsWith(ANDROIDX_PKG_PREFIX) &&
           !qualifiedName.startsWith(ANDROID_ARCH_PKG_PREFIX) ? SYSTEM_RESOURCE_PACKAGE : null;
  }

  @Nullable
  private static PsiClass getSuperclass(@NotNull PsiClass c) {
    return ApplicationManager.getApplication().runReadAction((Computable<PsiClass>)() -> c.isValid() ? c.getSuperClass() : null);
  }

  /**
   * Yield attributes for resources in xml/ folder
   */
  public static void processXmlAttributes(@NotNull AndroidFacet facet,
                                          @NotNull XmlTag tag,
                                          @NotNull XmlResourceElement element,
                                          @NotNull Set<XmlName> skipAttrNames,
                                          @NotNull AttributeProcessor callback) {
    String tagName = tag.getName();
    String styleableName = AndroidXmlResourcesUtil.SPECIAL_STYLEABLE_NAMES.get(tagName);
    if (styleableName != null) {
      Set<XmlName> newSkipAttrNames = new HashSet<>();
      if (element instanceof Intent) {
        newSkipAttrNames.add(new XmlName("action", NS_RESOURCES));
      }

      registerAttributes(facet, element, styleableName, SYSTEM_RESOURCE_PACKAGE, callback, newSkipAttrNames);
    }

    // for preferences
    Map<String, PsiClass> prefClassMap = getPreferencesClassMap(facet);
    String prefClassName = element.getXmlTag().getName();
    PsiClass c = prefClassMap.get(prefClassName);

    // register attributes by preference class
    registerAttributesForClassAndSuperclasses(facet, element, c, callback, skipAttrNames);

    // register attributes by widget
    String widgetClassName = AndroidTextUtils.trimEndOrNullize(prefClassName, PREFERENCE_TAG_NAME);
    if (widgetClassName != null) {
      PsiClass widgetClass = LayoutViewClassUtils.findClassByTagName(facet, widgetClassName, VIEW_CLASS_NAME);
      if (widgetClass != null) {
        registerAttributesForClassAndSuperclasses(facet, element, widgetClass, callback, skipAttrNames);
      }
    }
  }

  @NotNull
  public static Map<String, PsiClass> getPreferencesClassMap(@NotNull AndroidFacet facet) {
    return getClassMap(facet, CLASS_PREFERENCE);
  }

  public static Map<String, PsiClass> getViewClassMap(@NotNull AndroidFacet facet) {
    return getClassMap(facet, VIEW_CLASS_NAME);
  }

  private static Map<String, PsiClass> getClassMap(@NotNull AndroidFacet facet, @NotNull String className) {
    if (DumbService.isDumb(facet.getModule().getProject())) {
      return Collections.emptyMap();
    }
    return TagToClassMapper.getInstance(facet.getModule()).getClassMap(className);
  }

  private static void registerAttributesFromSuffixedStyleables(@NotNull AndroidFacet facet,
                                                               @NotNull DomElement element,
                                                               @NotNull PsiClass psiClass,
                                                               @NotNull AttributeProcessor callback,
                                                               @NotNull Set<XmlName> skipAttrNames) {
    String viewName = psiClass.getName();
    if (viewName == null) {
      return;
    }

    // Not using Map here for lookup by prefix for performance reasons - using switch instead of ImmutableMap makes
    // attribute highlighting 20% faster as measured by AndroidLayoutDomTest#testCustomAttrsPerformance
    String styleableName;
    switch (viewName) {
      case "ViewGroup":
        styleableName = "ViewGroup_MarginLayout";
        break;
      case "TableRow":
        styleableName = "TableRow_Cell";
        break;
      case "CollapsingToolbarLayout":
        // Support library doesn't have particularly consistent naming
        // Styleable definition: https://android.googlesource.com/platform/frameworks/support/+/master/design/res/values/attrs.xml
        registerAttributes(facet, element, "CollapsingAppBarLayout_LayoutParams", null, callback, skipAttrNames);

        styleableName = viewName + "_Layout";  // This is what it should be... (may be fixed in the future)
        break;
      case "CoordinatorLayout":
        // Support library doesn't have particularly consistent naming
        // Styleable definition: https://android.googlesource.com/platform/frameworks/support/+/master/design/res/values/attrs.xml
        registerAttributes(facet, element, "CoordinatorLayout_LayoutParams", null, callback, skipAttrNames);

        styleableName = viewName + "_Layout";  // This is what it should be... (may be fixed in the future)
        break;
      case "AppBarLayout":
        // Support library doesn't have particularly consistent naming
        // Styleable definition: https://android.googlesource.com/platform/frameworks/support/+/master/design/res/values/attrs.xml
        registerAttributes(facet, element, "AppBarLayout_LayoutParams", null, callback, skipAttrNames);

        styleableName = viewName + "_Layout";  // This is what it should be... (may be fixed in the future)
        break;
      default:
        styleableName = viewName + "_Layout";
    }

    registerAttributes(facet, element, styleableName, getResourcePackage(psiClass), callback, skipAttrNames);
  }

  /**
   * Entry point for XML elements in navigation XMLs.
   */
  public static void processNavAttributes(@NotNull AndroidFacet facet,
                                          @NotNull XmlTag tag,
                                          @NotNull NavDestinationElement element,
                                          @NotNull Set<XmlName> skipAttrNames,
                                          @NotNull AttributeProcessor callback) {
    try {
      NavigationSchema.createIfNecessary(facet);
    }
    catch (ClassNotFoundException e) {
      // The nav dependency wasn't added yet. Give up.
      return;
    }
    NavigationSchema schema = NavigationSchema.get(facet);
    for (PsiClass psiClass : schema.getDestinationClassesByTagSlowly(tag.getName())) {
      registerAttributesForClassAndSuperclasses(facet, element, psiClass, callback, skipAttrNames);
    }
  }

  /**
   * Entry point for XML elements in layout XMLs
   */
  public static void processLayoutAttributes(@NotNull AndroidFacet facet,
                                             @NotNull XmlTag tag,
                                             @NotNull LayoutElement element,
                                             @NotNull Set<XmlName> skipAttrNames,
                                             @NotNull AttributeProcessor callback) {
    Map<String, PsiClass> map = getViewClassMap(facet);

    // Add tools namespace attributes to layout tags, but not those that are databinding-specific ones.
    if (!(element instanceof DataBindingElement)) {
      registerToolsAttribute(ATTR_TARGET_API, callback);
      if (tag.getParentTag() == null) {
        registerToolsAttribute(ATTR_CONTEXT, callback);
        registerToolsAttribute(ATTR_MENU, callback);
        registerToolsAttribute(ATTR_ACTION_BAR_NAV_MODE, callback);
        registerToolsAttribute(ATTR_SHOW_IN, callback);
      }

      // AdapterView resides in android.widget package and thus is acquired from class map by short name.
      PsiClass adapterView = map.get(ADAPTER_VIEW);
      PsiClass psiClass = map.get(tag.getName());
      if (adapterView != null && psiClass != null && psiClass.isInheritor(adapterView, true)) {
        registerToolsAttribute(ATTR_LISTITEM, callback);
        registerToolsAttribute(ATTR_LISTHEADER, callback);
        registerToolsAttribute(ATTR_LISTFOOTER, callback);
      }

      PsiClass oldDrawerLayout = map.get(CLASS_DRAWER_LAYOUT.oldName());
      if (oldDrawerLayout != null && psiClass != null &&
          (psiClass.isEquivalentTo(oldDrawerLayout) || psiClass.isInheritor(oldDrawerLayout, true))) {
        registerToolsAttribute(ATTR_OPEN_DRAWER, callback);
      }

      PsiClass newDrawerLayout = map.get(CLASS_DRAWER_LAYOUT.newName());
      if (newDrawerLayout != null && psiClass != null &&
          (psiClass.isEquivalentTo(newDrawerLayout) || psiClass.isInheritor(newDrawerLayout, true))) {
        registerToolsAttribute(ATTR_OPEN_DRAWER, callback);
      }

      // Mockup attributes can be associated with any View, even include tag
      if (StudioFlags.NELE_MOCKUP_EDITOR.get()) {
        registerToolsAttribute(ATTR_MOCKUP, callback);
        registerToolsAttribute(ATTR_MOCKUP_CROP, callback);
        registerToolsAttribute(ATTR_MOCKUP_OPACITY, callback);
      }
    }

    if (element instanceof Tag || element instanceof Data) {
      // don't want view attributes inside these tags
      return;
    }

    String tagName = tag.getName();
    switch (tagName) {
      case VIEW_FRAGMENT:
        registerToolsAttribute(ATTR_LAYOUT, callback);
        break;

      case VIEW_TAG:
        // In Android layout XMLs, one can write, e.g.
        //   <view class="LinearLayout" />
        //
        // instead of
        //   <LinearLayout />
        //
        // In this case code here treats <view> tag as a special case, in which it adds all the attributes
        // from all available styleables that have the same simple names as found descendants of View class.
        //
        // See LayoutInflater#createViewFromTag in Android framework for inflating code

        for (PsiClass aClass : map.values()) {
          String name = aClass.getName();
          if (name == null) {
            continue;
          }
          registerAttributes(facet, element, name, getResourcePackage(aClass), callback, skipAttrNames);
        }
        break;

      case VIEW_MERGE:
        if (tag.getParentTag() == null) {
          registerToolsAttribute(ATTR_PARENT_TAG, callback);
        }
        registerAttributesForClassAndSuperclasses(facet, element, map.get(VIEW_MERGE), callback, skipAttrNames);

        String parentTagName = tag.getAttributeValue(ATTR_PARENT_TAG, TOOLS_URI);
        if (parentTagName != null) {
          registerAttributesForClassAndSuperclasses(facet, element, map.get(parentTagName), callback, skipAttrNames);
        }
        break;

      default:
        PsiClass c = map.get(tagName);
        registerAttributesForClassAndSuperclasses(facet, element, c, callback, skipAttrNames);
        break;
    }

    if (tagName.equals(VIEW_MERGE)) {
      // A <merge> does not have layout attributes.
      // Instead the children of the merge tag are considered the top elements.
      return;
    }

    XmlTag parentTag = tag.getParentTag();
    if (parentTag != null) {
      String parentTagName = parentTag.getName();

      if (VIEW_MERGE.equals(parentTagName)) {
        parentTagName = parentTag.getAttributeValue(ATTR_PARENT_TAG, TOOLS_URI);
      }

      if (TAG_LAYOUT.equals(parentTagName)) {
        // Data binding: ensure that the children of the <layout> tag
        // pick up layout params from ViewGroup (layout_width and layout_height)
        parentTagName = VIEW_GROUP;
      }
      if (parentTagName != null) {
        PsiClass c = map.get(parentTagName);
        while (c != null) {
          registerAttributesFromSuffixedStyleables(facet, element, c, callback, skipAttrNames);
          c = getSuperclass(c);
        }
        return;
      }
    }

    // We don't know what the parent is: include all layout attributes from all layout classes.
    for (PsiClass c : map.values()) {
      registerAttributesFromSuffixedStyleables(facet, element, c, callback, skipAttrNames);
    }
  }

  /**
   * Enumerate attributes that are available for the given XML tag, represented by {@link AndroidDomElement},
   * and "return" them via {@link AttributeProcessor}.
   *
   * Primary user is {@link AndroidDomExtender}, which uses it to provide code completion facilities when
   * editing XML files in text editor.
   *
   * Implementation of the method implements {@link Styleable} annotation handling and dispatches on tag type
   * using instanceof checks for adding attributes that don't come from styleable definitions with statically
   * known names.
   *
   * @param processAllExistingAttrsFirst whether already existing attributes should be returned first
   */
  public static void processAttributes(@NotNull AndroidDomElement element,
                                       @NotNull AndroidFacet facet,
                                       boolean processAllExistingAttrsFirst,
                                       @NotNull AttributeProcessor callback) {
    if (DumbService.getInstance(facet.getModule().getProject()).isDumb()) {
      return;
    }
    XmlTag tag = element.getXmlTag();

    Set<XmlName> skippedAttributes =
      processAllExistingAttrsFirst ? registerExistingAttributes(facet, tag, element, callback) : new HashSet<>();

    if (element instanceof ManifestElement) {
      processManifestAttributes(tag, element, callback);
    }
    else if (element instanceof LayoutElement) {
      processLayoutAttributes(facet, tag, (LayoutElement)element, skippedAttributes, callback);
    }
    else if (element instanceof XmlResourceElement) {
      processXmlAttributes(facet, tag, (XmlResourceElement)element, skippedAttributes, callback);
    }
    else if (element instanceof XmlRawResourceElement) {
      processRawAttributes(tag, callback);
    }
    else if (element instanceof NavDestinationElement) {
      processNavAttributes(facet, tag, (NavDestinationElement)element, skippedAttributes, callback);
    }

    // If DOM element is annotated with @Styleable annotation, load a styleable definition
    // from Android framework or a library with the name provided in annotation and register all attributes
    // from it for code highlighting and completion.
    Styleable styleableAnnotation = element.getAnnotation(Styleable.class);
    if (styleableAnnotation == null) {
      return;
    }
    boolean isSystem = styleableAnnotation.packageName().equals(ANDROID_PKG);
    AttributeDefinitions definitions;
    if (isSystem) {
      FrameworkResourceManager manager = ModuleResourceManagers.getInstance(facet).getFrameworkResourceManager();
      if (manager == null) {
        return;
      }

      definitions = manager.getAttributeDefinitions();
      if (definitions == null) {
        return;
      }
    }
    else {
      definitions = ModuleResourceManagers.getInstance(facet).getLocalResourceManager().getAttributeDefinitions();
    }

    if (element instanceof MenuItem) {
      processMenuItemAttributes(facet, element, skippedAttributes, callback);
      return;
    }

    for (String styleableName : styleableAnnotation.value()) {
      StyleableDefinition styleable = definitions.getStyleableByName(styleableName);
      if (styleable != null) {
        // TODO(namespaces): if !isSystem and we're namespace-aware we should use the library-specific namespace
        registerStyleableAttributes(element, styleable, isSystem ? ANDROID_URI : AUTO_URI, callback, skippedAttributes);
      }
      else if (isSystem) {
        // DOM element is annotated with @Styleable annotation, but styleable definition with
        // provided name is not there in Android framework. This is a bug, so logging it as a warning.
        getLog().warn(String.format("@Styleable(%s) annotation doesn't point to existing styleable", styleableName));
      }
    }

    // Handle interpolator XML tags: they don't have their own DomElement interfaces, and all use InterpolatorElement at the moment.
    // Thus, they can't use @Styleable annotation and there is a mapping from tag name to styleable name that's used below.

    // This snippet doesn't look much different from lines above for handling @Styleable annotations above,
    // but is used to provide customized warning message
    // TODO: figure it out how to make it DRY without introducing new method with lots of arguments
    if (element instanceof InterpolatorElement) {
      String styleableName = InterpolatorDomFileDescription.getInterpolatorStyleableByTagName(tag.getName());
      if (styleableName != null) {
        StyleableDefinition styleable = definitions.getStyleableByName(styleableName);
        if (styleable == null) {
          getLog().warn(String.format("%s doesn't point to existing styleable for interpolator", styleableName));
        }
        else {
          registerStyleableAttributes(element, styleable, ANDROID_URI, callback, skippedAttributes);
        }
      }
    }
  }

  /**
   * Handle attributes for XML elements in raw/ resource folder
   */
  public static void processRawAttributes(@NotNull XmlTag tag, @NotNull AttributeProcessor callback) {
    // For Resource Shrinking
    if (TAG_RESOURCES.equals(tag.getName())) {
      registerToolsAttribute(ATTR_SHRINK_MODE, callback);
      registerToolsAttribute(ATTR_KEEP, callback);
      registerToolsAttribute(ATTR_DISCARD, callback);
    }
  }

  /**
   * Handle attributes for XML elements from AndroidManifest.xml
   */
  public static void processManifestAttributes(@NotNull XmlTag tag,
                                               @NotNull AndroidDomElement element,
                                               @NotNull AttributeProcessor callback) {
    // Don't register manifest merger attributes for root element
    if (tag.getParentTag() != null) {
      registerToolsAttribute(ToolsAttributeUtil.ATTR_NODE, callback);
      registerToolsAttribute(ToolsAttributeUtil.ATTR_STRICT, callback);
      registerToolsAttribute(ToolsAttributeUtil.ATTR_REMOVE, callback);
      registerToolsAttribute(ToolsAttributeUtil.ATTR_REPLACE, callback);
    }

    if (element instanceof UsesSdk) {
      registerToolsAttribute(ToolsAttributeUtil.ATTR_OVERRIDE_LIBRARY, callback);
    }
  }

  private static void processMenuItemAttributes(@NotNull AndroidFacet facet,
                                                @NotNull DomElement element,
                                                @NotNull Collection<XmlName> skippedAttributes,
                                                @NotNull AttributeProcessor callback) {
    ResourceManager manager = ModuleResourceManagers.getInstance(facet).getFrameworkResourceManager();
    if (manager == null) {
      return;
    }

    AttributeDefinitions styleables = manager.getAttributeDefinitions();
    if (styleables == null) {
      return;
    }

    StyleableDefinition styleable = styleables.getStyleableByName("MenuItem");
    if (styleable == null) {
      getLog().warn("No StyleableDefinition for MenuItem");
      return;
    }

    for (AttributeDefinition attribute : styleable.getAttributes()) {
      String name = attribute.getName();

      // android:showAsAction was introduced in API Level 11. Use the app: one if the project depends on appcompat.
      // See com.android.tools.lint.checks.AppCompatResourceDetector.
      if (name.equals(ATTR_SHOW_AS_ACTION)) {
        boolean hasAppCompat = DependencyManagementUtil.dependsOn(facet.getModule(), GoogleMavenArtifactId.APP_COMPAT_V7) ||
                               DependencyManagementUtil.dependsOn(facet.getModule(), GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7);
        if (hasAppCompat) {
          if (skippedAttributes.add(new XmlName(name, AUTO_URI))) {
            registerAttribute(attribute, "MenuItem", AUTO_URI, element, callback);
          }

          continue;
        }
      }

      if (skippedAttributes.add(new XmlName(name, ANDROID_URI))) {
        registerAttribute(attribute, "MenuItem", ANDROID_URI, element, callback);
      }
    }
  }

  private static void registerToolsAttribute(@NotNull String attributeName, @NotNull AttributeProcessor callback) {
    AttributeDefinition definition = ToolsAttributeUtil.getAttrDefByName(attributeName);
    if (definition != null) {
      XmlName name = new XmlName(attributeName, TOOLS_URI);
      DomExtension domExtension = callback.processAttribute(name, definition, null);
      ResolvingConverter converter = ToolsAttributeUtil.getConverter(definition);
      if (domExtension != null && converter != null) {
        domExtension.setConverter(converter);
      }
    }
    else {
      getLog().warn("No attribute definition for tools attribute " + attributeName);
    }
  }

  @NotNull
  private static Set<XmlName> registerExistingAttributes(@NotNull AndroidFacet facet,
                                                         @NotNull XmlTag tag,
                                                         @NotNull AndroidDomElement element,
                                                         @NotNull AttributeProcessor callback) {
    Set<XmlName> result = new HashSet<>();
    XmlAttribute[] attrs = tag.getAttributes();

    for (XmlAttribute attr : attrs) {
      String localName = attr.getLocalName();

      if (!localName.endsWith(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)) {
        if (!"xmlns".equals(attr.getNamespacePrefix())) {
          AttributeDefinition attrDef = AndroidDomUtil.getAttributeDefinition(facet, attr);

          if (attrDef != null) {
            String namespace = attr.getNamespace();
            result.add(new XmlName(attr.getLocalName(), attr.getNamespace()));
            registerAttribute(attrDef, null, !namespace.isEmpty() ? namespace : null, element, callback);
          }
        }
      }
    }
    return result;
  }

  public interface AttributeProcessor {
    @Nullable
    DomExtension processAttribute(@NotNull XmlName xmlName, @NotNull AttributeDefinition attrDef, @Nullable String parentStyleableName);
  }
}
