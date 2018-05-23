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

package org.jetbrains.android.util;

import com.android.ide.common.resources.FileResourceNameValidator;
import com.android.ide.common.resources.ValueXmlHelper;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.*;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.graph.Graph;
import org.jetbrains.android.AndroidFileTemplateProvider;
import org.jetbrains.android.actions.CreateTypedResourceFileAction;
import org.jetbrains.android.dom.AndroidDomElement;
import org.jetbrains.android.dom.color.ColorSelector;
import org.jetbrains.android.dom.drawable.DrawableSelector;
import org.jetbrains.android.dom.manifest.AndroidManifestUtils;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.resources.Item;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.android.SdkConstants.*;
import static com.android.resources.ResourceType.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourceUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.util.AndroidResourceUtil");

  public static final Set<ResourceType> VALUE_RESOURCE_TYPES = EnumSet.of(ResourceType.DRAWABLE, ResourceType.COLOR, ResourceType.DIMEN,
                                                                          ResourceType.STRING, ResourceType.STYLE, ResourceType.ARRAY,
                                                                          ResourceType.PLURALS, ResourceType.ID, ResourceType.BOOL,
                                                                          ResourceType.INTEGER, ResourceType.FRACTION,
                                                                          // For aliases only
                                                                          ResourceType.LAYOUT);

  public static final Set<ResourceType> ALL_VALUE_RESOURCE_TYPES = EnumSet.noneOf(ResourceType.class);

  public static final Set<ResourceType> REFERRABLE_RESOURCE_TYPES = EnumSet.noneOf(ResourceType.class);
  public static final Map<ResourceType, ResourceFolderType> XML_FILE_RESOURCE_TYPES = ImmutableMap.<ResourceType, ResourceFolderType>builder()
    .put(ResourceType.ANIM, ResourceFolderType.ANIM)
    .put(ResourceType.ANIMATOR, ResourceFolderType.ANIMATOR)
    .put(ResourceType.COLOR, ResourceFolderType.COLOR)
    .put(ResourceType.DRAWABLE, ResourceFolderType.DRAWABLE)
    .put(ResourceType.FONT, ResourceFolderType.FONT)
    .put(ResourceType.INTERPOLATOR, ResourceFolderType.INTERPOLATOR)
    .put(ResourceType.LAYOUT, ResourceFolderType.LAYOUT)
    .put(ResourceType.NAVIGATION, ResourceFolderType.NAVIGATION)
    .put(ResourceType.MENU, ResourceFolderType.MENU)
    .put(ResourceType.MIPMAP, ResourceFolderType.MIPMAP)
    .put(ResourceType.RAW, ResourceFolderType.RAW)
    .put(ResourceType.TRANSITION, ResourceFolderType.TRANSITION)
    .put(ResourceType.XML, ResourceFolderType.XML)
    .build();
  static final String ROOT_TAG_PROPERTY = "ROOT_TAG";
  static final String LAYOUT_WIDTH_PROPERTY = "LAYOUT_WIDTH";
  static final String LAYOUT_HEIGHT_PROPERTY = "LAYOUT_HEIGHT";

  private static final String RESOURCE_CLASS_SUFFIX = "." + AndroidUtils.R_CLASS_NAME;

  /**
   * Comparator which orders {@link PsiElement} items into a priority order most suitable for presentation
   * to the user; for example, it prefers base resource folders such as {@code values/} over resource
   * folders such as {@code values-en-rUS}
   */
  public static final Comparator<PsiElement> RESOURCE_ELEMENT_COMPARATOR = (e1, e2) -> {
    if (e1 instanceof LazyValueResourceElementWrapper && e2 instanceof LazyValueResourceElementWrapper) {
      return ((LazyValueResourceElementWrapper)e1).compareTo((LazyValueResourceElementWrapper)e2);
    }

    PsiFile file1 = e1.getContainingFile();
    PsiFile file2 = e2.getContainingFile();
    int delta = compareResourceFiles(file1, file2);
    if (delta != 0) {
      return delta;
    }
    return e1.getTextOffset() - e2.getTextOffset();
  };

  private AndroidResourceUtil() {
  }

  @NotNull
  public static String normalizeXmlResourceValue(@NotNull String value) {
    return ValueXmlHelper.escapeResourceString(value, false);
  }

  static {
    REFERRABLE_RESOURCE_TYPES.addAll(Arrays.asList(ResourceType.values()));
    REFERRABLE_RESOURCE_TYPES.remove(ATTR);
    REFERRABLE_RESOURCE_TYPES.remove(STYLEABLE);
    REFERRABLE_RESOURCE_TYPES.remove(PUBLIC);
    REFERRABLE_RESOURCE_TYPES.remove(ATTR);

    ALL_VALUE_RESOURCE_TYPES.addAll(VALUE_RESOURCE_TYPES);
    ALL_VALUE_RESOURCE_TYPES.add(ATTR);
    ALL_VALUE_RESOURCE_TYPES.add(STYLEABLE);
  }

  public static String packageToRClass(@NotNull String packageName) {
    return packageName + RESOURCE_CLASS_SUFFIX;
  }

  @NotNull
  public static PsiField[] findResourceFields(@NotNull AndroidFacet facet,
                                              @NotNull String resClassName,
                                              @NotNull String resourceName,
                                              boolean onlyInOwnPackages) {
    return findResourceFields(facet, resClassName, Collections.singleton(resourceName), onlyInOwnPackages);
  }

  /**
   * Like {@link #findResourceFields(AndroidFacet, String, String, boolean)} but
   * can match than more than a single field name
   */
  @NotNull
  public static PsiField[] findResourceFields(@NotNull AndroidFacet facet,
                                              @NotNull String resClassName,
                                              @NotNull Collection<String> resourceNames,
                                              boolean onlyInOwnPackages) {
    final List<PsiField> result = new ArrayList<>();
    for (PsiClass rClass : findRJavaClasses(facet, onlyInOwnPackages)) {
      findResourceFieldsFromClass(rClass, resClassName, resourceNames, result);
    }
    return result.toArray(PsiField.EMPTY_ARRAY);
  }

  private static void findResourceFieldsFromClass(@NotNull PsiClass rClass,
      @NotNull String resClassName, @NotNull Collection<String> resourceNames,
      @NotNull List<PsiField> result) {
    final PsiClass resourceTypeClass = rClass.findInnerClassByName(resClassName, false);

    if (resourceTypeClass != null) {
      for (String resourceName : resourceNames) {
        String fieldName = getRJavaFieldName(resourceName);
        final PsiField field = resourceTypeClass.findFieldByName(fieldName, false);

        if (field != null) {
          result.add(field);
        }
      }
    }
  }

  @NotNull
  private static Set<PsiClass> findRJavaClasses(@NotNull AndroidFacet facet, boolean onlyInOwnPackages) {
    final Module module = facet.getModule();
    final Project project = module.getProject();
    if (facet.getManifest() == null) {
      return Collections.emptySet();
    }

    Graph<Module> graph = ModuleManager.getInstance(project).moduleGraph();
    Set<Module> dependentModules = Sets.newHashSet();
    collectDependentModules(graph, module, dependentModules);

    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);

    Set<PsiClass> rClasses = Sets.newHashSet();
    String targetPackage = onlyInOwnPackages ? null : AndroidManifestUtils.getPackageName(facet);
    if (targetPackage != null) {
      GlobalSearchScope[] scopes = new GlobalSearchScope[dependentModules.size()];
      int i = 0;
      for (Module dependentModule : dependentModules) {
        scopes[i++] = dependentModule.getModuleScope();
      }
      rClasses.addAll(Arrays.asList(psiFacade.findClasses(packageToRClass(targetPackage), GlobalSearchScope.union(scopes))));
    }

    for (Module dependentModule : dependentModules) {
      AndroidFacet dependentFacet = AndroidFacet.getInstance(dependentModule);
      if (dependentFacet == null) {
        continue;
      }
      String dependentPackage = AndroidManifestUtils.getPackageName(dependentFacet);
      if (dependentPackage == null || dependentPackage.equals(targetPackage)) {
        continue;
      }
      rClasses.addAll(Arrays.asList(psiFacade.findClasses(packageToRClass(dependentPackage), dependentModule.getModuleScope())));
    }
    return rClasses;
  }

  private static void collectDependentModules(@NotNull Graph<Module> graph,
                                              @NotNull Module module,
                                              @NotNull Set<Module> result) {
    if (result.contains(module)) {
      return;
    }
    result.add(module);
    Iterator<Module> out = graph.getOut(module);
    while (out.hasNext()) {
      collectDependentModules(graph, out.next(), result);
    }
  }

  @NotNull
  public static PsiField[] findResourceFieldsForFileResource(@NotNull PsiFile file, boolean onlyInOwnPackages) {
    final AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return PsiField.EMPTY_ARRAY;
    }

    final String resourceType = ModuleResourceManagers.getInstance(facet).getLocalResourceManager().getFileResourceType(file);
    if (resourceType == null) {
      return PsiField.EMPTY_ARRAY;
    }

    final String resourceName = AndroidCommonUtils.getResourceName(resourceType, file.getName());
    return findResourceFields(facet, resourceType, resourceName, onlyInOwnPackages);
  }

  @NotNull
  public static PsiField[] findResourceFieldsForValueResource(XmlTag tag, boolean onlyInOwnPackages) {
    final AndroidFacet facet = AndroidFacet.getInstance(tag);
    if (facet == null) {
      return PsiField.EMPTY_ARRAY;
    }

    ResourceFolderType fileResType = ResourceHelper.getFolderType(tag.getContainingFile());
    final String resourceType = fileResType == ResourceFolderType.VALUES
                                ? getResourceTypeByValueResourceTag(tag)
                                : null;
    if (resourceType == null) {
      return PsiField.EMPTY_ARRAY;
    }

    String name = tag.getAttributeValue(ATTR_NAME);
    if (name == null) {
      return PsiField.EMPTY_ARRAY;
    }

    return findResourceFields(facet, resourceType, name, onlyInOwnPackages);
  }

  @NotNull
  public static PsiField[] findStyleableAttributeFields(XmlTag tag, boolean onlyInOwnPackages) {
    String tagName = tag.getName();
    if (TAG_DECLARE_STYLEABLE.equals(tagName)) {
      String styleableName = tag.getAttributeValue(ATTR_NAME);
      if (styleableName == null) {
        return PsiField.EMPTY_ARRAY;
      }
      AndroidFacet facet = AndroidFacet.getInstance(tag);
      if (facet == null) {
        return PsiField.EMPTY_ARRAY;
      }
      Set<String> names = Sets.newHashSet();
      for (XmlTag attr : tag.getSubTags()) {
        if (TAG_ATTR.equals(attr.getName())) {
          String attrName = attr.getAttributeValue(ATTR_NAME);
          if (attrName != null) {
            names.add(styleableName + '_' + attrName);
          }
        }
      }
      if (!names.isEmpty()) {
        return findResourceFields(facet, STYLEABLE.getName(), names, onlyInOwnPackages);
      }
    } else if (TAG_ATTR.equals(tagName)) {
      XmlTag parentTag = tag.getParentTag();
      if (parentTag != null && TAG_DECLARE_STYLEABLE.equals(parentTag.getName())) {
        String styleName = parentTag.getAttributeValue(ATTR_NAME);
        String attributeName = tag.getAttributeValue(ATTR_NAME);
        AndroidFacet facet = AndroidFacet.getInstance(tag);
        if (facet != null && styleName != null && attributeName != null) {
          return findResourceFields(facet, STYLEABLE.getName(), styleName + '_' + attributeName, onlyInOwnPackages);
        }
      }
    }

    return PsiField.EMPTY_ARRAY;
  }

  @NotNull
  public static String getRJavaFieldName(@NotNull String resourceName) {
    if (resourceName.indexOf('.') == -1) {
      return resourceName;
    }
    final String[] identifiers = resourceName.split("\\.");
    final StringBuilder result = new StringBuilder(resourceName.length());

    for (int i = 0, n = identifiers.length; i < n; i++) {
      result.append(identifiers[i]);
      if (i < n - 1) {
        result.append('_');
      }
    }
    return result.toString();
  }

  public static boolean isCorrectAndroidResourceName(@NotNull String resourceName) {
    // TODO: No, we need to check per resource folder type here. There is a validator for this!
    if (resourceName.isEmpty()) {
      return false;
    }
    if (resourceName.startsWith(".") || resourceName.endsWith(".")) {
      return false;
    }
    final String[] identifiers = resourceName.split("\\.");

    for (String identifier : identifiers) {
      if (!StringUtil.isJavaIdentifier(identifier)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  public static String getResourceTypeByValueResourceTag(@NotNull XmlTag tag) {
    String resClassName = tag.getName();
    resClassName = resClassName.equals("item")
                   ? tag.getAttributeValue("type", null)
                   : AndroidCommonUtils.getResourceTypeByTagName(resClassName);
    if (resClassName != null) {
      final String resourceName = tag.getAttributeValue("name");
      return resourceName != null ? resClassName : null;
    }
    return null;
  }

  @Nullable
  public static ResourceType getResourceForResourceTag(@NotNull XmlTag tag) {
    String typeName = getResourceTypeByValueResourceTag(tag);
    if (typeName != null) {
      return ResourceType.getEnum(typeName);
    }

    return null;
  }

  @Nullable
  public static String getResourceClassName(@NotNull PsiField field) {
    final PsiClass resourceClass = field.getContainingClass();

    if (resourceClass != null) {
      final PsiClass parentClass = resourceClass.getContainingClass();

      if (parentClass != null &&
          AndroidUtils.R_CLASS_NAME.equals(parentClass.getName()) &&
          parentClass.getContainingClass() == null) {
        return resourceClass.getName();
      }
    }
    return null;
  }

  // result contains XmlAttributeValue or PsiFile
  @NotNull
  public static List<PsiElement> findResourcesByField(@NotNull PsiField field) {
    final AndroidFacet facet = AndroidFacet.getInstance(field);
    return facet != null
           ? ModuleResourceManagers.getInstance(facet).getLocalResourceManager().findResourcesByField(field)
           : Collections.emptyList();
  }

  public static boolean isResourceField(@NotNull PsiField field) {
    PsiClass c = field.getContainingClass();
    if (c == null) return false;
    c = c.getContainingClass();
    if (c != null && AndroidUtils.R_CLASS_NAME.equals(c.getName())) {
      AndroidFacet facet = AndroidFacet.getInstance(field);
      if (facet != null) {
        PsiFile file = c.getContainingFile();
        if (file != null && isRJavaFile(facet, file)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isStringResource(@NotNull XmlTag tag) {
    return tag.getName().equals(TAG_STRING) && tag.getAttribute(ATTR_NAME) != null;
  }

  @NotNull
  public static PsiField[] findIdFields(@NotNull XmlAttributeValue value) {
    if (value.getParent() instanceof XmlAttribute) {
      return findIdFields((XmlAttribute)value.getParent());
    }
    return PsiField.EMPTY_ARRAY;
  }

  public static boolean isIdDeclaration(@Nullable String attrValue) {
    return attrValue != null && attrValue.startsWith(NEW_ID_PREFIX);
  }

  public static boolean isIdReference(@Nullable String attrValue) {
    return attrValue != null && attrValue.startsWith(ID_PREFIX);
  }

  public static boolean isIdDeclaration(@NotNull XmlAttributeValue value) {
    return isIdDeclaration(value.getValue());
  }

  public static boolean isConstraintReferencedIds(@Nullable String nsURI, @Nullable String nsPrefix, @Nullable String key) {
    return AUTO_URI.equals(nsURI) && APP_PREFIX.equals(nsPrefix) && CONSTRAINT_REFERENCED_IDS.equals(key);
  }

  public static boolean isConstraintReferencedIds(@NotNull XmlAttributeValue value) {
    PsiElement parent = value.getParent();
    if (parent instanceof XmlAttribute) {
      XmlAttribute xmlAttribute = (XmlAttribute) parent;

      String nsURI = xmlAttribute.getNamespace();
      String nsPrefix = xmlAttribute.getNamespacePrefix();
      String key = xmlAttribute.getLocalName();

      return isConstraintReferencedIds(nsURI, nsPrefix, key);
    }
    return false;
  }

  @NotNull
  public static PsiField[] findIdFields(@NotNull XmlAttribute attribute) {
    XmlAttributeValue valueElement = attribute.getValueElement();
    String value = attribute.getValue();

    if (valueElement != null && value != null && isIdDeclaration(valueElement)) {
      final String id = getResourceNameByReferenceText(value);

      if (id != null) {
        final AndroidFacet facet = AndroidFacet.getInstance(attribute);

        if (facet != null) {
          return findResourceFields(facet, ResourceType.ID.getName(), id, false);
        }
      }
    }
    return PsiField.EMPTY_ARRAY;
  }

  /**
   * Generate an extension-less file name based on a passed string, that should pass
   * validation as a resource file name by Gradle plugin.
   * <p/>
   * For names validation in the Gradle plugin, see {@link FileResourceNameValidator}
   */
  @NotNull
  public static String getValidResourceFileName(@NotNull String base) {
    return base.replace('-', '_').replace(' ', '_').toLowerCase(Locale.US);
  }

  @Nullable
  public static String getResourceNameByReferenceText(@NotNull String text) {
    int i = text.indexOf('/');
    if (i < text.length() - 1) {
      return text.substring(i + 1, text.length());
    }
    return null;
  }

  @NotNull
  public static ResourceElement addValueResource(@NotNull final ResourceType resType, @NotNull final Resources resources,
                                                 @Nullable final String value) {
    switch (resType) {
      case STRING:
        return resources.addString();
      case PLURALS:
        return resources.addPlurals();
      case DIMEN:
        if (value != null && value.trim().endsWith("%")) {
          // Deals with dimension values in the form of percentages, e.g. "65%"
          final Item item = resources.addItem();
          item.getType().setStringValue(ResourceType.DIMEN.getName());
          return item;
        }
        if (value != null && value.indexOf('.') > 0) {
          // Deals with dimension values in the form of floating-point numbers, e.g. "0.24"
          final Item item = resources.addItem();
          item.getType().setStringValue(ResourceType.DIMEN.getName());
          item.getFormat().setStringValue("float");
          return item;
        }
        return resources.addDimen();
      case COLOR:
        return resources.addColor();
      case DRAWABLE:
        return resources.addDrawable();
      case STYLE:
        return resources.addStyle();
      case ARRAY:
        // todo: choose among string-array, integer-array and array
        return resources.addStringArray();
      case INTEGER:
        return resources.addInteger();
      case FRACTION:
        return resources.addFraction();
      case BOOL:
        return resources.addBool();
      case ID:
        final Item item = resources.addItem();
        item.getType().setValue(ResourceType.ID.getName());
        return item;
      case ATTR:
        return resources.addAttr();
      case STYLEABLE:
        return resources.addDeclareStyleable();
      default:
        throw new IllegalArgumentException("Incorrect resource type");
    }
  }

  @NotNull
  public static List<VirtualFile> getResourceSubdirs(@NotNull ResourceFolderType resourceType,
                                                     @NotNull Collection<VirtualFile> resourceDirs) {
    final List<VirtualFile> dirs = new ArrayList<>();

    for (VirtualFile resourcesDir : resourceDirs) {
      if (resourcesDir == null || !resourcesDir.isValid()) {
        continue;
      }
      for (VirtualFile child : resourcesDir.getChildren()) {
        ResourceFolderType type = ResourceFolderType.getFolderType(child.getName());
        if (resourceType.equals(type)) dirs.add(child);
      }
    }
    return dirs;
  }

  @Nullable
  public static String getDefaultResourceFileName(@NotNull ResourceType type) {
    if (ResourceType.PLURALS == type || ResourceType.STRING == type) {
      return "strings.xml";
    }
    if (VALUE_RESOURCE_TYPES.contains(type)) {

      if (type == ResourceType.LAYOUT
          // Lots of unit tests assume drawable aliases are written in "drawables.xml" but going
          // forward lets combine both layouts and drawables in refs.xml as is done in the templates:
          || type == ResourceType.DRAWABLE && !ApplicationManager.getApplication().isUnitTestMode()) {
        return "refs.xml";
      }

      return type.getName() + "s.xml";
    }
    if (ATTR == type ||
        STYLEABLE == type) {
      return "attrs.xml";
    }
    return null;
  }

  @NotNull
  public static List<ResourceElement> getValueResourcesFromElement(@NotNull ResourceType resourceType, @NotNull Resources resources) {
    final List<ResourceElement> result = new ArrayList<>();

    //noinspection EnumSwitchStatementWhichMissesCases
    switch (resourceType) {
      case STRING:
        result.addAll(resources.getStrings());
        break;
      case PLURALS:
        result.addAll(resources.getPluralses());
        break;
      case DRAWABLE:
        result.addAll(resources.getDrawables());
        break;
      case COLOR:
        result.addAll(resources.getColors());
        break;
      case DIMEN:
        result.addAll(resources.getDimens());
        break;
      case STYLE:
        result.addAll(resources.getStyles());
        break;
      case ARRAY:
        result.addAll(resources.getStringArrays());
        result.addAll(resources.getIntegerArrays());
        result.addAll(resources.getArrays());
        break;
      case INTEGER:
        result.addAll(resources.getIntegers());
        break;
      case FRACTION:
        result.addAll(resources.getFractions());
        break;
      case BOOL:
        result.addAll(resources.getBools());
        break;
      default:
        break;
    }

    for (Item item : resources.getItems()) {
      String type = item.getType().getValue();
      if (resourceType.getName().equals(type)) {
        result.add(item);
      }
    }
    return result;
  }

  public static boolean isInResourceSubdirectory(@NotNull PsiFile file, @Nullable String resourceType) {
    file = file.getOriginalFile();
    PsiDirectory dir = file.getContainingDirectory();
    if (dir == null) return false;
    return isResourceSubdirectory(dir, resourceType);
  }

  public static boolean isResourceSubdirectory(@NotNull PsiDirectory directory, @Nullable String resourceType) {
    PsiDirectory dir = directory;

    String dirName = dir.getName();
    if (resourceType != null) {
      int typeLength = resourceType.length();
      int dirLength = dirName.length();
      if (dirLength < typeLength || !dirName.startsWith(resourceType) || dirLength > typeLength && dirName.charAt(typeLength) != '-') {
        return false;
      }
    }
    dir = dir.getParent();

    if (dir == null) {
      return false;
    }
    if ("default".equals(dir.getName())) {
      dir = dir.getParentDirectory();
    }
    return dir != null && isResourceDirectory(dir);
  }

  public static boolean isLocalResourceDirectory(@NotNull VirtualFile dir, @NotNull Project project) {
    final Module module = ModuleUtilCore.findModuleForFile(dir, project);

    if (module != null) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      return facet != null && ModuleResourceManagers.getInstance(facet).getLocalResourceManager().isResourceDir(dir);
    }
    return false;
  }

  public static boolean isResourceFile(@NotNull VirtualFile file, @NotNull AndroidFacet facet) {
    final VirtualFile parent = file.getParent();
    final VirtualFile resDir = parent != null ? parent.getParent() : null;
    return resDir != null && ModuleResourceManagers.getInstance(facet).getLocalResourceManager().isResourceDir(resDir);
  }

  public static boolean isResourceDirectory(@NotNull PsiDirectory directory) {
    PsiDirectory dir = directory;
    // check facet settings
    VirtualFile vf = dir.getVirtualFile();

    if (isLocalResourceDirectory(vf, dir.getProject())) {
      return true;
    }

    // method can be invoked for system resource dir, so we should check it
    if (!FD_RES.equals(dir.getName())) return false;
    dir = dir.getParent();
    if (dir != null) {
      if (dir.findFile(FN_ANDROID_MANIFEST_XML) != null) {
        return true;
      }
      dir = dir.getParent();
      if (dir != null) {
        if (containsAndroidJar(dir)) return true;
        dir = dir.getParent();
        if (dir != null) {
          return containsAndroidJar(dir);
        }
      }
    }
    return false;
  }

  private static boolean containsAndroidJar(@NotNull PsiDirectory psiDirectory) {
    return psiDirectory.findFile(FN_FRAMEWORK_LIBRARY) != null;
  }

  public static boolean isRJavaFile(@NotNull AndroidFacet facet, @NotNull PsiFile file) {
    if (file.getName().equals(AndroidCommonUtils.R_JAVA_FILENAME) && file instanceof PsiJavaFile) {
      final PsiJavaFile javaFile = (PsiJavaFile)file;

      final Manifest manifest = facet.getManifest();
      if (manifest != null) {
        final String manifestPackage = manifest.getPackage().getValue();
        if (manifestPackage != null && javaFile.getPackageName().equals(manifestPackage)) {
          return true;
        }
      }

      for (String aPackage : AndroidUtils.getDepLibsPackages(facet.getModule())) {
        if (javaFile.getPackageName().equals(aPackage)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isManifestJavaFile(@NotNull AndroidFacet facet, @NotNull PsiFile file) {
    if (file.getName().equals(AndroidCommonUtils.MANIFEST_JAVA_FILE_NAME) && file instanceof PsiJavaFile) {
      final Manifest manifest = facet.getManifest();
      final PsiJavaFile javaFile = (PsiJavaFile)file;
      return manifest != null && javaFile.getPackageName().equals(manifest.getPackage().getValue());
    }
    return false;
  }

  public static List<String> getNames(@NotNull Collection<ResourceType> resourceTypes) {
    if (resourceTypes.isEmpty()) {
      return Collections.emptyList();
    }
    final List<String> result = new ArrayList<>();

    for (ResourceType type : resourceTypes) {
      result.add(type.getName());
    }
    return result;
  }

  @NotNull
  public static String[] getNamesArray(@NotNull Collection<ResourceType> resourceTypes) {
    final List<String> names = getNames(resourceTypes);
    return ArrayUtil.toStringArray(names);
  }

  public static boolean createValueResource(@NotNull Project project,
                                            @NotNull VirtualFile resDir,
                                            @NotNull String resourceName,
                                            @Nullable String resourceValue,
                                            @NotNull final ResourceType resourceType,
                                            @NotNull String fileName,
                                            @NotNull List<String> dirNames,
                                            @NotNull Processor<ResourceElement> afterAddedProcessor) {
    try {
      return addValueResource(project, resDir, resourceName, resourceType, fileName, dirNames, resourceValue, afterAddedProcessor);
    }
    catch (Exception e) {
      final String message = CreateElementActionBase.filterMessage(e.getMessage());

      if (message == null || message.isEmpty()) {
        LOG.error(e);
      }
      else {
        LOG.info(e);
        AndroidUtils.reportError(project, message);
      }
      return false;
    }
  }

  public static boolean createValueResource(@NotNull Project project,
                                            @NotNull VirtualFile resDir,
                                            @NotNull String resourceName,
                                            @NotNull final ResourceType resourceType,
                                            @NotNull String fileName,
                                            @NotNull List<String> dirNames,
                                            @NotNull final String value) {
    return createValueResource(project, resDir, resourceName, resourceType, fileName, dirNames, value, null);
  }

  public static boolean createValueResource(@NotNull Project project,
                                            @NotNull VirtualFile resDir,
                                            @NotNull String resourceName,
                                            @NotNull final ResourceType resourceType,
                                            @NotNull String fileName,
                                            @NotNull List<String> dirNames,
                                            @NotNull final String value,
                                            @Nullable final List<ResourceElement> outTags) {
    return createValueResource(project, resDir, resourceName, value, resourceType, fileName, dirNames, element -> {
      if (!value.isEmpty()) {
        final String s = resourceType == ResourceType.STRING ? normalizeXmlResourceValue(value) : value;
        element.setStringValue(s);
      }
      else if (resourceType == STYLEABLE || resourceType == ResourceType.STYLE) {
        element.setStringValue("value");
        element.getXmlTag().getValue().setText("");
      }

      if (outTags != null) {
        outTags.add(element);
      }
      return true;
    });
  }

  private static boolean addValueResource(@NotNull Project project,
                                          @NotNull VirtualFile resDir,
                                          @NotNull final String resourceName,
                                          @NotNull final ResourceType resourceType,
                                          @NotNull String fileName,
                                          @NotNull List<String> dirNames,
                                          @Nullable final String resourceValue,
                                          @NotNull final Processor<ResourceElement> afterAddedProcessor) throws Exception {
    if (dirNames.isEmpty()) {
      return false;
    }
    final VirtualFile[] resFiles = new VirtualFile[dirNames.size()];

    for (int i = 0, n = dirNames.size(); i < n; i++) {
      String dirName = dirNames.get(i);
      resFiles[i] = WriteAction.compute(() -> findOrCreateResourceFile(project, resDir, fileName, dirName));
      if (resFiles[i] == null) {
        return false;
      }
    }

    if (!ReadonlyStatusHandler.ensureFilesWritable(project, resFiles)) {
      return false;
    }
    final Resources[] resourcesElements = new Resources[resFiles.length];

    for (int i = 0; i < resFiles.length; i++) {
      final Resources resources = AndroidUtils.loadDomElement(project, resFiles[i], Resources.class);
      if (resources == null) {
        AndroidUtils.reportError(project, AndroidBundle.message("not.resource.file.error", fileName));
        return false;
      }
      resourcesElements[i] = resources;
    }

    List<PsiFile> psiFiles = Lists.newArrayListWithExpectedSize(resFiles.length);
    PsiManager manager = PsiManager.getInstance(project);
    for (VirtualFile file : resFiles) {
      PsiFile psiFile = manager.findFile(file);
      if (psiFile != null) {
        psiFiles.add(psiFile);
      }
    }
    PsiFile[] files = psiFiles.toArray(PsiFile.EMPTY_ARRAY);
    WriteCommandAction<Void> action = new WriteCommandAction<Void>(project, "Add Resource", files) {
      @Override
      protected void run(@NotNull Result<Void> result) {
        for (Resources resources : resourcesElements) {
          final ResourceElement element = addValueResource(resourceType, resources, resourceValue);
          element.getName().setValue(resourceName);
          afterAddedProcessor.process(element);
        }
      }
    };
    action.execute();

    return true;
  }

  /**
   * Sets a new value for a resource.
   * @param project the project containing the resource
   * @param resDir the res/ directory containing the resource
   * @param name the name of the resource to be modified
   * @param newValue the new resource value
   * @param fileName the resource values file name
   * @param dirNames list of values directories where the resource should be changed
   * @param useGlobalCommand if true, the undo will be registered globally. This allows the command to be undone from anywhere in the IDE
   *                         and not only the XML editor
   * @return true if the resource value was changed
   */
  public static boolean changeValueResource(@NotNull final Project project,
                                            @NotNull VirtualFile resDir,
                                            @NotNull final String name,
                                            @NotNull final ResourceType resourceType,
                                            @NotNull final String newValue,
                                            @NotNull String fileName,
                                            @NotNull List<String> dirNames,
                                            final boolean useGlobalCommand) {
    if (dirNames.isEmpty()) {
      return false;
    }
    ArrayList<VirtualFile> resFiles = Lists.newArrayListWithExpectedSize(dirNames.size());

    for (String dirName : dirNames) {
      final VirtualFile resFile = findResourceFile(resDir, fileName, dirName);
      if (resFile != null) {
        resFiles.add(resFile);
      }
    }

    if (!ensureFilesWritable(project, resFiles)) {
      return false;
    }
    final Resources[] resourcesElements = new Resources[resFiles.size()];

    for (int i = 0; i < resFiles.size(); i++) {
      final Resources resources = AndroidUtils.loadDomElement(project, resFiles.get(i), Resources.class);
      if (resources == null) {
        AndroidUtils.reportError(project, AndroidBundle.message("not.resource.file.error", fileName));
        return false;
      }
      resourcesElements[i] = resources;
    }

    List<PsiFile> psiFiles = Lists.newArrayListWithExpectedSize(resFiles.size());
    PsiManager manager = PsiManager.getInstance(project);
    for (VirtualFile file : resFiles) {
      PsiFile psiFile = manager.findFile(file);
      if (psiFile != null) {
        psiFiles.add(psiFile);
      }
    }
    PsiFile[] files = psiFiles.toArray(PsiFile.EMPTY_ARRAY);
    WriteCommandAction<Boolean> action = new WriteCommandAction<Boolean>(project, "Change " + resourceType.getName() + " Resource", files) {
      @Override
      protected void run(@NotNull Result<Boolean> result) throws Throwable {
        if (useGlobalCommand) {
          CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);
        }

        result.setResult(false);
        for (Resources resources : resourcesElements) {
          for (ResourceElement element : getValueResourcesFromElement(resourceType, resources)) {
            String value = element.getName().getStringValue();
            if (name.equals(value)) {
              element.setStringValue(newValue);
              result.setResult(true);
            }
          }
        }
      }
    };

    return action.execute().getResultObject();
  }

  @Nullable
  private static VirtualFile findResourceFile(@NotNull VirtualFile resDir,
                                              @NotNull final String fileName,
                                              @NotNull String dirName) {
    VirtualFile dir = resDir.findChild(dirName);
    if (dir == null) {
      return null;
    }
    return dir.findChild(fileName);
  }

  @Nullable
  private static VirtualFile findOrCreateResourceFile(@NotNull Project project,
                                                      @NotNull VirtualFile resDir,
                                                      @NotNull final String fileName,
                                                      @NotNull String dirName) throws Exception {
    final VirtualFile dir = AndroidUtils.createChildDirectoryIfNotExist(project, resDir, dirName);
    final String dirPath = FileUtil.toSystemDependentName(resDir.getPath() + '/' + dirName);

    if (dir == null) {
      AndroidUtils.reportError(project, AndroidBundle.message("android.cannot.create.dir.error", dirPath));
      return null;
    }

    final VirtualFile file = dir.findChild(fileName);
    if (file != null) {
      return file;
    }

    AndroidFileTemplateProvider
      .createFromTemplate(project, dir, AndroidFileTemplateProvider.VALUE_RESOURCE_FILE_TEMPLATE, fileName);
    final VirtualFile result = dir.findChild(fileName);
    if (result == null) {
      AndroidUtils.reportError(project, AndroidBundle.message("android.cannot.create.file.error", dirPath + File.separatorChar + fileName));
    }
    return result;
  }

  @Nullable
  public static MyReferredResourceFieldInfo getReferredResourceOrManifestField(@NotNull AndroidFacet facet,
                                                                               @NotNull PsiReferenceExpression exp,
                                                                               boolean localOnly) {
    return getReferredResourceOrManifestField(facet, exp, null, localOnly);
  }

  @Nullable
  public static MyReferredResourceFieldInfo getReferredResourceOrManifestField(@NotNull AndroidFacet facet,
                                                                               @NotNull PsiReferenceExpression exp,
                                                                               @Nullable String className,
                                                                               boolean localOnly) {
    final String resFieldName = exp.getReferenceName();
    if (resFieldName == null || resFieldName.isEmpty()) {
      return null;
    }

    PsiExpression qExp = exp.getQualifierExpression();
    if (!(qExp instanceof PsiReferenceExpression)) {
      return null;
    }
    final PsiReferenceExpression resClassReference = (PsiReferenceExpression)qExp;

    final String resClassName = resClassReference.getReferenceName();
    if (resClassName == null || resClassName.isEmpty() ||
        className != null && !className.equals(resClassName)) {
      return null;
    }

    qExp = resClassReference.getQualifierExpression();
    if (!(qExp instanceof PsiReferenceExpression)) {
      return null;
    }

    final PsiElement resolvedElement = ((PsiReferenceExpression)qExp).resolve();
    if (!(resolvedElement instanceof PsiClass)) {
      return null;
    }
    Module resolvedModule = ModuleUtilCore.findModuleForPsiElement(resolvedElement);
    final PsiClass aClass = (PsiClass)resolvedElement;
    final String classShortName = aClass.getName();
    final boolean fromManifest = AndroidUtils.MANIFEST_CLASS_NAME.equals(classShortName);

    if (!fromManifest && !AndroidUtils.R_CLASS_NAME.equals(classShortName)) {
      return null;
    }
    if (!localOnly) {
      final String qName = aClass.getQualifiedName();

      if (CLASS_R.equals(qName) || AndroidInternalRClassFinder.INTERNAL_R_CLASS_QNAME.equals(qName)) {
        return new MyReferredResourceFieldInfo(resClassName, resFieldName, resolvedModule, true, false);
      }
    }
    final PsiFile containingFile = resolvedElement.getContainingFile();
    if (containingFile == null) {
      return null;
    }
    if (fromManifest ? !isManifestJavaFile(facet, containingFile) : !isRJavaFile(facet, containingFile)) {
      return null;
    }
    return new MyReferredResourceFieldInfo(resClassName, resFieldName, resolvedModule, false, fromManifest);
  }

  /**
   * Utility method suitable for Comparator implementations which order resource files,
   * which will sort files by base folder followed by alphabetical configurations. Prioritizes
   * XML files higher than non-XML files.
   */
  public static int compareResourceFiles(@Nullable VirtualFile file1, @Nullable VirtualFile file2) {
    //noinspection UseVirtualFileEquals
    if (file1 != null && file1.equals(file2) || file1 == file2) {
      return 0;
    }
    else if (file1 != null && file2 != null) {
      boolean xml1 = file1.getFileType() == StdFileTypes.XML;
      boolean xml2 = file2.getFileType() == StdFileTypes.XML;
      if (xml1 != xml2) {
        return xml1 ? -1 : 1;
      }
      VirtualFile parent1 = file1.getParent();
      VirtualFile parent2 = file2.getParent();
      if (parent1 != null && parent2 != null && !parent1.equals(parent2)) {
        String parentName1 = parent1.getName();
        String parentName2 = parent2.getName();
        boolean qualifier1 = parentName1.indexOf('-') != -1;
        boolean qualifier2 = parentName2.indexOf('-') != -1;
        if (qualifier1 != qualifier2) {
          return qualifier1 ? 1 : -1;
        }

        if (qualifier1) {
          // Sort in FolderConfiguration order
          FolderConfiguration config1 = FolderConfiguration.getConfigForFolder(parentName1);
          FolderConfiguration config2 = FolderConfiguration.getConfigForFolder(parentName2);
          if (config1 != null && config2 != null) {
            return config1.compareTo(config2);
          } else if (config1 != null) {
            return -1;
          } else if (config2 != null) {
            return 1;
          }

          int delta = parentName1.compareTo(parentName2);
          if (delta != 0) {
            return delta;
          }
        }
      }

      return file1.getPath().compareTo(file2.getPath());
    }
    else if (file1 != null) {
      return -1;
    }
    else {
      return 1;
    }
  }

  /**
   * Utility method suitable for Comparator implementations which order resource files,
   * which will sort files by base folder followed by alphabetical configurations. Prioritizes
   * XML files higher than non-XML files. (Resource file folders are sorted by folder configuration
   * order.)
   */
  public static int compareResourceFiles(@Nullable PsiFile file1, @Nullable PsiFile file2) {
    if (file1 == file2) {
      return 0;
    }
    else if (file1 != null && file2 != null) {
      boolean xml1 = file1.getFileType() == StdFileTypes.XML;
      boolean xml2 = file2.getFileType() == StdFileTypes.XML;
      if (xml1 != xml2) {
        return xml1 ? -1 : 1;
      }
      PsiDirectory parent1 = file1.getParent();
      PsiDirectory parent2 = file2.getParent();
      if (parent1 != null && parent2 != null && parent1 != parent2) {
        String parentName1 = parent1.getName();
        String parentName2 = parent2.getName();
        boolean qualifier1 = parentName1.indexOf('-') != -1;
        boolean qualifier2 = parentName2.indexOf('-') != -1;

        if (qualifier1 != qualifier2) {
          return qualifier1 ? 1 : -1;
        }

        if (qualifier1) {
          // Sort in FolderConfiguration order
          FolderConfiguration config1 = FolderConfiguration.getConfigForFolder(parentName1);
          FolderConfiguration config2 = FolderConfiguration.getConfigForFolder(parentName2);
          if (config1 != null && config2 != null) {
            return config1.compareTo(config2);
          } else if (config1 != null) {
            return -1;
          } else if (config2 != null) {
            return 1;
          }

          int delta = parentName1.compareTo(parentName2);
          if (delta != 0) {
            return delta;
          }
        }
      }

      return file1.getName().compareTo(file2.getName());
    }
    else if (file1 != null) {
      return -1;
    }
    else {
      return 1;
    }
  }

  public static boolean ensureFilesWritable(@NotNull Project project, @NotNull Collection<VirtualFile> files) {
    return !ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files).hasReadonlyFiles();
  }

  /**
   * Returns the type of the ResourceChooserItem based on a node's attributes.
   * @param node the node
   * @return the ResourceType or null if it could not be inferred.
   */
  @Nullable
  public static ResourceType getType(@NotNull XmlTag node) {
    String nodeName = node.getLocalName();
    String typeString = null;

    if (TAG_ITEM.equals(nodeName)) {
      String attribute = node.getAttributeValue(ATTR_TYPE);
      if (attribute != null) {
        typeString = attribute;
      }
    } else {
      // the type is the name of the node.
      typeString = nodeName;
    }

    return typeString == null ? null : ResourceType.getEnum(typeString);
  }

  /**
   * Grabs resource directories from the given facets and pairs the directory with an arbitrary
   * AndroidFacet which happens to depend on the directory.
   *
   * @param facets set of facets which may have resource directories
   */
  @NotNull
  public static Map<VirtualFile, AndroidFacet> getResourceDirectoriesForFacets(@NotNull List<AndroidFacet> facets) {
    Map<VirtualFile, AndroidFacet> resDirectories = new HashMap<>();
    for (AndroidFacet facet : facets) {
      for (VirtualFile resourceDir : ResourceFolderManager.getInstance(facet).getFolders()) {
        if (!resDirectories.containsKey(resourceDir)) {
          resDirectories.put(resourceDir, facet);
        }
      }
    }
    return resDirectories;
  }

  public static class MyReferredResourceFieldInfo {
    private final String myClassName;
    private final String myFieldName;
    private final Module myResolvedModule;
    private final boolean mySystem;
    private final boolean myFromManifest;

    public MyReferredResourceFieldInfo(
      @NotNull String className, @NotNull String fieldName, @Nullable Module resolvedModule, boolean system, boolean fromManifest) {
      myClassName = className;
      myFieldName = fieldName;
      myResolvedModule = resolvedModule;
      mySystem = system;
      myFromManifest = fromManifest;
    }

    @NotNull
    public String getClassName() {
      return myClassName;
    }

    @NotNull
    public String getFieldName() {
      return myFieldName;
    }

    @Nullable
    public Module getResolvedModule() {
      return myResolvedModule;
    }

    public boolean isSystem() {
      return mySystem;
    }

    public boolean isFromManifest() {
      return myFromManifest;
    }
  }

  @NotNull
  public static XmlFile createFileResource(@NotNull String fileName,
                                           @NotNull PsiDirectory resSubdir,
                                           @NotNull String rootTagName,
                                           @NotNull String resourceType,
                                           boolean valuesResourceFile) throws Exception {
    FileTemplateManager manager = FileTemplateManager.getInstance(resSubdir.getProject());
    String templateName = getTemplateName(resourceType, valuesResourceFile, rootTagName);
    FileTemplate template = manager.getJ2eeTemplate(templateName);
    Properties properties = new Properties();
    if (!valuesResourceFile) {
      properties.setProperty(ROOT_TAG_PROPERTY, rootTagName);
    }

    if (ResourceType.LAYOUT.getName().equals(resourceType)) {
      final Module module = ModuleUtilCore.findModuleForPsiElement(resSubdir);
      final AndroidPlatform platform = module != null ? AndroidPlatform.getInstance(module) : null;
      final int apiLevel = platform != null ? platform.getApiLevel() : -1;

      final String value = apiLevel == -1 || apiLevel >= 8
                           ? "match_parent" : "fill_parent";
      properties.setProperty(LAYOUT_WIDTH_PROPERTY, value);
      properties.setProperty(LAYOUT_HEIGHT_PROPERTY, value);
    }
    PsiElement createdElement = FileTemplateUtil.createFromTemplate(template, fileName, properties, resSubdir);
    assert createdElement instanceof XmlFile;
    return (XmlFile)createdElement;
  }

  private static String getTemplateName(@NotNull String resourceType, boolean valuesResourceFile, @NotNull String rootTagName) {
    if (valuesResourceFile) {
      return AndroidFileTemplateProvider.VALUE_RESOURCE_FILE_TEMPLATE;
    }
    if (LAYOUT.getName().equals(resourceType) && !TAG_LAYOUT.equals(rootTagName)) {
      return AndroidUtils.TAG_LINEAR_LAYOUT.equals(rootTagName)
             ? AndroidFileTemplateProvider.LAYOUT_RESOURCE_VERTICAL_FILE_TEMPLATE
             : AndroidFileTemplateProvider.LAYOUT_RESOURCE_FILE_TEMPLATE;
    }
    if (NAVIGATION.getName().equals(resourceType)) {
      return AndroidFileTemplateProvider.NAVIGATION_RESOURCE_FILE_TEMPLATE;
    }
    return AndroidFileTemplateProvider.RESOURCE_FILE_TEMPLATE;
  }

  @NotNull
  public static String getFieldNameByResourceName(@NotNull String styleName) {
    for (int i = 0, n = styleName.length(); i < n; i++) {
      char c = styleName.charAt(i);
      if (c == '.' || c == '-' || c == ':') {
        return styleName.replace('.', '_').replace('-', '_').replace(':', '_');
      }
    }

    return styleName;
  }

  /**
   * Finds and returns the resource files named stateListName in the directories listed in dirNames.
   * If some of the directories do not contain a file with that name, creates such a resource file.
   * @param project the project
   * @param resDir the res/ dir containing the directories under investigation
   * @param folderType Type of the directories under investigation
   * @param resourceType Type of the resource file to create if necessary
   * @param stateListName Name of the resource files to be returned
   * @param dirNames List of directory names to look into
   * @return List of found and created files
   */
  @Nullable
  public static List<VirtualFile> findOrCreateStateListFiles(@NotNull final Project project,
                                                             @NotNull final VirtualFile resDir,
                                                             @NotNull final ResourceFolderType folderType,
                                                             @NotNull final ResourceType resourceType,
                                                             @NotNull final String stateListName,
                                                             @NotNull final List<String> dirNames) {
    final PsiManager manager = PsiManager.getInstance(project);
    final List<VirtualFile> files = Lists.newArrayListWithCapacity(dirNames.size());
    boolean foundFiles = new WriteCommandAction<Boolean>(project, "Find statelists files") {
      @Override
      protected void run(@NotNull Result<Boolean> result) {
        result.setResult(true);
        try {
          String fileName = stateListName;
          if (!stateListName.endsWith(DOT_XML)) {
            fileName += DOT_XML;
          }

          for (String dirName : dirNames) {
            String dirPath = FileUtil.toSystemDependentName(resDir.getPath() + '/' + dirName);
            final VirtualFile dir;

            dir = AndroidUtils.createChildDirectoryIfNotExist(project, resDir, dirName);
            if (dir == null) {
              throw new IOException("cannot make " + resDir + File.separatorChar + dirName);
            }

            VirtualFile file = dir.findChild(fileName);
            if (file != null) {
              files.add(file);
              continue;
            }

            PsiDirectory directory = manager.findDirectory(dir);
            if (directory == null) {
              throw new IOException("cannot find " + resDir + File.separatorChar + dirName);
            }

            createFileResource(fileName, directory, CreateTypedResourceFileAction.getDefaultRootTagByResourceType(folderType),
                               resourceType.getName(), false);

            file = dir.findChild(fileName);
            if (file == null) {
              throw new IOException("cannot find " + Joiner.on(File.separatorChar).join(resDir, dirPath, fileName));
            }
            files.add(file);
          }
        }
        catch (Exception e) {
          LOG.error(e.getMessage());
          result.setResult(false);
        }
      }
    }.execute().getResultObject();

    return foundFiles ? files : null;
  }

  public static void updateStateList(@NotNull Project project, final @NotNull StateList stateList,
                                     @NotNull List<VirtualFile> files) {
    if (!ensureFilesWritable(project, files)) {
      return;
    }

    List<PsiFile> psiFiles = Lists.newArrayListWithCapacity(files.size());
    PsiManager manager = PsiManager.getInstance(project);
    for (VirtualFile file : files) {
      PsiFile psiFile = manager.findFile(file);
      if (psiFile != null) {
        psiFiles.add(psiFile);
      }
    }

    final List<AndroidDomElement> selectors = Lists.newArrayListWithCapacity(files.size());

    Class<? extends AndroidDomElement> selectorClass;

    if (stateList.getFolderType() == ResourceFolderType.COLOR) {
      selectorClass = ColorSelector.class;
    }
    else {
      selectorClass = DrawableSelector.class;
    }
    for (VirtualFile file : files) {
      final AndroidDomElement selector = AndroidUtils.loadDomElement(project, file, selectorClass);
      if (selector == null) {
        AndroidUtils.reportError(project, file.getName() + " is not a statelist file");
        return;
      }
      selectors.add(selector);
    }

    new WriteCommandAction.Simple(project, "Change State List", psiFiles.toArray(PsiFile.EMPTY_ARRAY)) {
      @Override
      protected void run() {
        for (AndroidDomElement selector : selectors) {
          XmlTag tag = selector.getXmlTag();
          for (XmlTag subtag : tag.getSubTags()) {
            subtag.delete();
          }
          for (StateListState state : stateList.getStates()) {
            XmlTag child = tag.createChildTag(TAG_ITEM, tag.getNamespace(), null, false);
            child = tag.addSubTag(child, false);

            Map<String, Boolean> attributes = state.getAttributes();
            for (String attributeName : attributes.keySet()) {
              child.setAttribute(attributeName, ANDROID_URI, attributes.get(attributeName).toString());
            }

            if (!StringUtil.isEmpty(state.getAlpha())) {
              child.setAttribute("alpha", ANDROID_URI, state.getAlpha());
            }

            if (selector instanceof ColorSelector) {
              child.setAttribute(ATTR_COLOR, ANDROID_URI, state.getValue());
            }
            else if (selector instanceof DrawableSelector) {
              child.setAttribute(ATTR_DRAWABLE, ANDROID_URI, state.getValue());
            }
          }
        }

        // The following is necessary since layoutlib will look on disk for the color state list file.
        // So as soon as a color state list is modified, the change needs to be saved on disk
        // for the correct values to be used in the theme editor preview.
        // TODO: Remove this once layoutlib can get color state lists from PSI instead of disk
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    }.execute();
  }


  /**
   * Ensures that the given namespace is imported in the given XML document.
   */
  @NotNull
  public static String ensureNamespaceImported(@NotNull XmlFile file, @NotNull String namespaceUri, @Nullable String suggestedPrefix) {
    final XmlTag rootTag = file.getRootTag();

    assert rootTag != null;
    final XmlElementFactory elementFactory = XmlElementFactory.getInstance(file.getProject());

    if (StringUtil.isEmpty(namespaceUri)) {
      // The style attribute has an empty namespaceUri:
      return "";
    }

    String prefix = rootTag.getPrefixByNamespace(namespaceUri);
    if (prefix != null) {
      return prefix;
    }

    ApplicationManager.getApplication().assertWriteAccessAllowed();

    if (suggestedPrefix != null) {
      prefix = suggestedPrefix;
    }
    else if (TOOLS_URI.equals(namespaceUri)) {
      prefix = TOOLS_PREFIX;
    }
    else if (ANDROID_URI.equals(namespaceUri)) {
      prefix = ANDROID_NS_NAME;
    }
    else if (AAPT_URI.equals(namespaceUri)) {
      prefix = AAPT_PREFIX;
    }
    else {
      prefix = APP_PREFIX;
    }
    if (rootTag.getAttribute(XMLNS_PREFIX + prefix) != null) {
      String base = prefix;
      for (int i = 2; ; i++) {
        prefix = base + Integer.toString(i);
        if (rootTag.getAttribute(XMLNS_PREFIX + prefix) == null) {
          break;
        }
      }
    }
    String name = XMLNS_PREFIX + prefix;
    final XmlAttribute xmlnsAttr = elementFactory.createXmlAttribute(name, namespaceUri);
    final XmlAttribute[] attributes = rootTag.getAttributes();
    XmlAttribute next = attributes.length > 0 ? attributes[0] : null;
    for (XmlAttribute attribute : attributes) {
      String attributeName = attribute.getName();
      if (!attributeName.startsWith(XMLNS_PREFIX) || attributeName.compareTo(name) > 0) {
        next = attribute;
        break;
      }
    }
    if (next != null) {
      rootTag.addBefore(xmlnsAttr, next);
    }
    else {
      rootTag.add(xmlnsAttr);
    }

    return prefix;
  }
}
