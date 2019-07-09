/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.databinding.psiclass;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.ide.common.resources.ResourcesUtil.stripPrefixFromId;
import static com.android.tools.idea.databinding.ViewBindingUtil.getViewBindingClassName;
import static com.android.tools.idea.res.binding.BindingLayoutInfo.LayoutType.DATA_BINDING_LAYOUT;

import com.android.SdkConstants;
import com.android.ide.common.resources.DataBindingResourceType;
import com.android.tools.idea.databinding.DataBindingMode;
import com.android.tools.idea.databinding.DataBindingUtil;
import com.android.tools.idea.databinding.ModuleDataBinding;
import com.android.tools.idea.databinding.cache.ResourceCacheValueProvider;
import com.android.tools.idea.databinding.index.BindingXmlIndex;
import com.android.tools.idea.databinding.index.ViewIdInfo;
import com.android.tools.idea.res.binding.BindingLayoutInfo;
import com.android.tools.idea.res.binding.DefaultBindingLayoutInfo;
import com.android.tools.idea.res.binding.MergedBindingLayoutInfo;
import com.android.tools.idea.res.binding.PsiDataBindingResourceItem;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.XmlRecursiveElementWalkingVisitor;
import com.intellij.psi.impl.light.LightField;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.indexing.FileBasedIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * In-memory PSI for classes generated from a layout file (or a list of related layout files from
 * different configurations)
 *
 * See also: https://developer.android.com/topic/libraries/data-binding/expressions#binding_data
 */
public class LightBindingClass extends AndroidLightClassBase {
  private static final int STATIC_METHOD_COUNT = 6;
  private BindingLayoutInfo myInfo;
  private CachedValue<PsiMethod[]> myPsiMethodsCache;
  private CachedValue<PsiField[]> myPsiFieldsCache;

  private PsiReferenceList myExtendsList;
  private PsiClassType[] myExtendsListTypes;
  private final AndroidFacet myFacet;
  private PsiFile myVirtualPsiFile;
  private final DataBindingMode myMode;
  private final Object myLock = new Object();

  public LightBindingClass(AndroidFacet facet, @NotNull PsiManager psiManager, @NotNull BindingLayoutInfo info) {
    super(psiManager, ImmutableSet.of(PsiModifier.PUBLIC, PsiModifier.FINAL));
    myInfo = info;
    myFacet = facet;
    // TODO we should create a virtual one not use the XML.
    myVirtualPsiFile = info.getPsiFile();
    myMode = ModuleDataBinding.getInstance(facet).getDataBindingMode();

    CachedValuesManager cachedValuesManager = CachedValuesManager.getManager(info.getProject());

    myPsiMethodsCache =
      cachedValuesManager.createCachedValue(new ResourceCacheValueProvider<PsiMethod[]>(facet, myLock) {
        @Override
        protected PsiMethod[] doCompute() {
          Map<String, PsiDataBindingResourceItem> variables = myInfo.getItems(DataBindingResourceType.VARIABLE);
          // Generate getter if this is merged or does not have an alternative layout in another configuration.
          List<PsiMethod> methods = new ArrayList<>(variables.size() * 2 + STATIC_METHOD_COUNT);
          // If this is merged, we override all setters (even if we don't use that variable.
          BindingLayoutInfo mergedInfo = myInfo.getMergedInfo();
          if (mergedInfo == null) {
            for (PsiDataBindingResourceItem variable : variables.values()) {
              createVariableMethods(variable, methods, true);
            }
            PsiElementFactory factory = PsiElementFactory.SERVICE.getInstance(myInfo.getProject());
            createStaticMethods(factory.createType(LightBindingClass.this), methods);
          } else {
            for (PsiDataBindingResourceItem variable : mergedInfo.getItems(DataBindingResourceType.VARIABLE).values()) {
              // Just the setters to be overriding super class abstract setters.
              createVariableMethods(variable, methods, false);
            }
          }
          // Create a private constructor.
          PsiMethod constructor = createConstructor();
          methods.add(constructor);
          return methods.toArray(PsiMethod.EMPTY_ARRAY);
        }

        @Override
        protected PsiMethod[] defaultValue() {
          return PsiMethod.EMPTY_ARRAY;
        }
      }, false);

    myPsiFieldsCache = cachedValuesManager
      .createCachedValue(() -> CachedValueProvider.Result.create(computeFields(), PsiModificationTracker.MODIFICATION_COUNT));

    setModuleInfo(facet.getModule(), false);
  }

  private PsiField[] computeFields() {
    if (myInfo.getMergedInfo() != null) {
      // fields are generated in the base class.
      return PsiField.EMPTY_ARRAY;
    }

    List<DefaultBindingLayoutInfo> infoList = myInfo.isMerged()
                                              ? ((MergedBindingLayoutInfo)myInfo).getInfos()
                                              : Lists.newArrayList((DefaultBindingLayoutInfo)myInfo);
    return infoList.stream()
      .flatMap(bindingInfo -> FileBasedIndex.getInstance()
        .getValues(BindingXmlIndex.NAME, BindingXmlIndex.getKeyForFile(bindingInfo.getPsiFile().getVirtualFile()),
                   GlobalSearchScope.fileScope(bindingInfo.getPsiFile())).stream())
      .flatMap(layoutInfo -> layoutInfo.getViewIds().stream())
      .map(idInfo -> createPsiField(idInfo))
      .toArray(PsiField[]::new);
  }

  /**
   * Creates a private no-argument constructor.
   */
  @NotNull
  private PsiMethod createConstructor() {
    LightMethodBuilder constructor = new LightMethodBuilder(this, JavaLanguage.INSTANCE);
    constructor.setConstructor(true);
    constructor.addModifier("private");
    return constructor;
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return myInfo.getQualifiedName();
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @NotNull
  @Override
  public PsiField[] getFields() {
    return myPsiFieldsCache.getValue();
  }

  @NotNull
  @Override
  public PsiField[] getAllFields() {
    return getFields();
  }

  @NotNull
  @Override
  public PsiMethod[] getMethods() {
    return myPsiMethodsCache.getValue();
  }

  @Override
  public PsiClass getSuperClass() {
    BindingLayoutInfo mergedInfo = myInfo.getMergedInfo();
    String superClassName;
    if (mergedInfo == null) {
      if (DATA_BINDING_LAYOUT == myInfo.getLayoutType()) {
        superClassName = myMode.viewDataBinding;
      } else {
        superClassName = getViewBindingClassName(getProject());
      }
    } else {
      superClassName = mergedInfo.getQualifiedName();
    }
    return JavaPsiFacade.getInstance(myInfo.getProject())
        .findClass(superClassName, myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(false));
  }

  @Override
  public PsiReferenceList getExtendsList() {
    if (myExtendsList == null) {
      PsiElementFactory factory = PsiElementFactory.SERVICE.getInstance(myInfo.getProject());
      PsiJavaCodeReferenceElement referenceElementByType = factory.createReferenceElementByType(getExtendsListTypes()[0]);
      myExtendsList = factory.createReferenceList(new PsiJavaCodeReferenceElement[]{referenceElementByType});
    }
    return myExtendsList;
  }

  @NotNull
  @Override
  public PsiClassType[] getSuperTypes() {
    return getExtendsListTypes();
  }

  @NotNull
  @Override
  public PsiClassType[] getExtendsListTypes() {
    if (myExtendsListTypes == null) {
      BindingLayoutInfo mergedInfo = myInfo.getMergedInfo();
      String superClassName;
      if (mergedInfo == null) {
        if (DATA_BINDING_LAYOUT == myInfo.getLayoutType()) {
          superClassName = myMode.viewDataBinding;
        } else {
          superClassName = getViewBindingClassName(getProject());
        }
      } else {
        superClassName = mergedInfo.getQualifiedName();
      }
      myExtendsListTypes = new PsiClassType[]{
        PsiType.getTypeByName(superClassName, myInfo.getProject(),
                              myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(false))};
    }
    return myExtendsListTypes;
  }


  @NotNull
  @Override
  public PsiMethod[] getAllMethods() {
    return getMethods();
  }

  @NotNull
  @Override
  public PsiMethod[] findMethodsByName(@NonNls String name, boolean checkBases) {
    List<PsiMethod> matched = null;
    for (PsiMethod method : getMethods()) {
      if (name.equals(method.getName())) {
        if (matched == null) {
          matched = new ArrayList<>();
        }
        matched.add(method);
      }
    }
    return matched == null ? PsiMethod.EMPTY_ARRAY : matched.toArray(PsiMethod.EMPTY_ARRAY);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    boolean continueProcessing = super.processDeclarations(processor, state, lastParent, place);
    if (!continueProcessing) {
      return false;
    }
    Map<String, PsiDataBindingResourceItem> imports = myInfo.getItems(DataBindingResourceType.IMPORT);
    if (imports.isEmpty()) {
      return true;
    }
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    if (classHint != null && classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
      NameHint nameHint = processor.getHint(NameHint.KEY);
      String name = nameHint != null ? nameHint.getName(state) : null;
      for (PsiDataBindingResourceItem imp : imports.values()) {
        String alias = imp.getExtra(SdkConstants.ATTR_ALIAS);
        if (alias != null) {
          continue; // Aliases are pre-resolved in replaceImportAliases.
        }
        String qName = imp.getExtra(SdkConstants.ATTR_TYPE);
        if (qName == null) {
          continue;
        }

        if (name != null && !qName.endsWith("" + name)) {
          continue;
        }

        Module module = myInfo.getModule();
        if (module == null) {
          return true; // this should not really happen but just to be safe
        }
        PsiClass aClass = JavaPsiFacade.getInstance(myManager.getProject()).findClass(qName, module
          .getModuleWithDependenciesAndLibrariesScope(true));
        if (aClass != null) {
          if (!processor.execute(aClass, state)) {
            // found it!
            return false;
          }
        }
      }
    }
    return true;
  }

  private void createVariableMethods(@NotNull PsiDataBindingResourceItem item, @NotNull List<PsiMethod> outPsiMethods, boolean addGetter) {
    PsiManager psiManager = getManager();

    String typeName = item.getExtra(SdkConstants.ATTR_TYPE);
    String variableType = DataBindingUtil.getQualifiedType(typeName, myInfo, true);
    if (variableType == null) {
      return;
    }
    PsiType type = DataBindingUtil.parsePsiType(variableType, myFacet, this);
    if (type == null) {
      return;
    }

    String javaName = DataBindingUtil.convertToJavaFieldName(item.getName());
    String capitalizedName = StringUtil.capitalize(javaName);
    LightMethodBuilder setter = createPublicMethod("set" + capitalizedName, PsiType.VOID);
    setter.addParameter(javaName, type);
    if (myInfo.isMerged()) {
      setter.addModifier("abstract");
    }
    outPsiMethods.add(new LightDataBindingMethod(item.getXmlTag(), psiManager, setter, this, JavaLanguage.INSTANCE));

    if (addGetter) {
      LightMethodBuilder getter = createPublicMethod("get" + capitalizedName, type);
      outPsiMethods.add(new LightDataBindingMethod(item.getXmlTag(), psiManager, getter, this, JavaLanguage.INSTANCE));
    }
  }

  private void createStaticMethods(@NotNull PsiClassType ownerType, @NotNull List<PsiMethod> outPsiMethods) {
    Project project = myInfo.getProject();
    Module module = myFacet.getModule();
    PsiClassType viewGroupType =
        PsiType.getTypeByName(SdkConstants.CLASS_VIEWGROUP, project, module.getModuleWithDependenciesAndLibrariesScope(true));
    PsiClassType layoutInflaterType =
        PsiType.getTypeByName(SdkConstants.CLASS_LAYOUT_INFLATER, project, module.getModuleWithDependenciesAndLibrariesScope(true));
    PsiClassType dataBindingComponent =
        PsiType.getJavaLangObject(getManager(), module.getModuleWithDependenciesAndLibrariesScope(true));
    PsiClassType viewType =
        PsiType.getTypeByName(SdkConstants.CLASS_VIEW, project, module.getModuleWithDependenciesAndLibrariesScope(true));

    DeprecatableLightMethodBuilder inflate4Arg = createPublicStaticMethod("inflate", ownerType);
    inflate4Arg.addParameter("inflater", layoutInflaterType);
    inflate4Arg.addParameter("root", viewGroupType);
    inflate4Arg.addParameter("attachToRoot", PsiType.BOOLEAN);
    inflate4Arg.addParameter("bindingComponent", dataBindingComponent);
    // methods receiving DataBindingComponent are deprecated. see: b/116541301
    inflate4Arg.setDeprecated(true);

    LightMethodBuilder inflate3Arg = createPublicStaticMethod("inflate", ownerType);
    inflate3Arg.addParameter("inflater", layoutInflaterType);
    inflate3Arg.addParameter("root", viewGroupType);
    inflate3Arg.addParameter("attachToRoot", PsiType.BOOLEAN);

    DeprecatableLightMethodBuilder inflate2Arg = createPublicStaticMethod("inflate", ownerType);
    inflate2Arg.addParameter("inflater", layoutInflaterType);
    inflate2Arg.addParameter("bindingComponent", dataBindingComponent);
    // methods receiving DataBindingComponent are deprecated. see: b/116541301
    inflate2Arg.setDeprecated(true);

    LightMethodBuilder inflate1Arg = createPublicStaticMethod("inflate", ownerType);
    inflate1Arg.addParameter("inflater", layoutInflaterType);

    LightMethodBuilder bind = createPublicStaticMethod("bind", ownerType);
    bind.addParameter("view", viewType);

    DeprecatableLightMethodBuilder bindWithComponent = createPublicStaticMethod("bind", ownerType);
    bindWithComponent.addParameter("view", viewType);
    bindWithComponent.addParameter("bindingComponent", dataBindingComponent);
    // methods receiving DataBindingComponent are deprecated. see: b/116541301
    bindWithComponent.setDeprecated(true);

    PsiManager psiManager = getManager();
    PsiMethod[] methods = new PsiMethod[]{inflate1Arg, inflate2Arg, inflate3Arg, inflate4Arg, bind, bindWithComponent};
    for (PsiMethod method : methods) {
      outPsiMethods.add(new LightDataBindingMethod(myInfo.getPsiFile(), psiManager, method, this, JavaLanguage.INSTANCE));
    }
  }

  @NotNull
  private DeprecatableLightMethodBuilder createPublicStaticMethod(@NotNull String name, @NotNull PsiType returnType) {
    DeprecatableLightMethodBuilder method = createPublicMethod(name, returnType);
    method.addModifier("static");
    return method;
  }

  @NotNull
  private DeprecatableLightMethodBuilder createPublicMethod(@NotNull String name, @NotNull PsiType returnType) {
    DeprecatableLightMethodBuilder method = new DeprecatableLightMethodBuilder(getManager(), JavaLanguage.INSTANCE, name);
    method.setContainingClass(this);
    method.setMethodReturnType(returnType);
    method.addModifier("public");
    return method;
  }

  @Nullable
  private PsiField createPsiField(@NotNull ViewIdInfo idInfo) {
    String name = DataBindingUtil.convertToJavaFieldName(idInfo.getId());
    PsiType type = DataBindingUtil.resolveViewPsiType(idInfo, myFacet);
    if (type == null) {
      return null;
    }
    LightFieldBuilder field = new LightFieldBuilder(PsiManager.getInstance(myInfo.getProject()), name, type);
    field.setModifiers("public", "final");
    return new LightDataBindingField(idInfo, getManager(), field, this);
  }

  @Override
  public boolean isInterface() {
    return false;
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myInfo.getNavigationElement();
  }

  @Override
  public String getName() {
    return myInfo.getClassName();
  }

  @Nullable
  @Override
  public PsiFile getContainingFile() {
    return myVirtualPsiFile;
  }

  @Override
  public boolean isValid() {
    // it is always valid. Not having this valid creates IDE errors because it is not always resolved instantly
    return true;
  }

  /**
   * The light method class that represents the generated data binding methods for a layout file.
   */
  public static class LightDataBindingMethod extends LightMethod {
    private PsiElement myNavigationElement;

    public LightDataBindingMethod(@NotNull PsiElement navigationElement,
                                  @NotNull PsiManager manager,
                                  @NotNull PsiMethod method,
                                  @NotNull PsiClass containingClass,
                                  @NotNull Language language) {
      super(manager, method, containingClass, language);
      myNavigationElement = navigationElement;
    }

    @Override
    public TextRange getTextRange() {
      return TextRange.EMPTY_RANGE;
    }

    @NotNull
    @Override
    public PsiElement getNavigationElement() {
      return myNavigationElement;
    }

    @Override
    public PsiIdentifier getNameIdentifier() {
      return new LightIdentifier(getManager(), getName());
    }
  }

  /**
   * The light field class that represents the generated view fields for a layout file.
   */
  public static class LightDataBindingField extends LightField {
    private final ViewIdInfo myViewIdInfo;

    private final CachedValue<XmlTag> tagCache = CachedValuesManager.getManager(getProject())
      .createCachedValue(() -> CachedValueProvider.Result.create(computeTag(), PsiModificationTracker.MODIFICATION_COUNT));

    public LightDataBindingField(@NotNull ViewIdInfo viewIdInfo,
                                 @NotNull PsiManager manager,
                                 @NotNull PsiField field,
                                 @NotNull PsiClass containingClass) {
      super(manager, field, containingClass);
      myViewIdInfo = viewIdInfo;
    }

    @Nullable
    private XmlTag computeTag() {
      final Ref<XmlTag> resultTag = new Ref<>();
      if (getContainingFile() != null) {
        getContainingFile().accept(new XmlRecursiveElementWalkingVisitor() {
          @Override
          public void visitXmlTag(XmlTag tag) {
            super.visitXmlTag(tag);
            String idValue = tag.getAttributeValue(ATTR_ID, ANDROID_URI);
            if (idValue != null && myViewIdInfo.getId().equals(stripPrefixFromId(idValue))) {
              resultTag.set(tag);
              stopWalking();
            }
          }
        });
      }
      return resultTag.get();
    }

    @Override
    @Nullable
    public PsiFile getContainingFile() {
      PsiClass containingClass = super.getContainingClass();
      return containingClass == null ? null : containingClass.getContainingFile();
    }

    @Override
    public TextRange getTextRange() {
      return TextRange.EMPTY_RANGE;
    }

    @Override
    @NotNull
    public PsiElement getNavigationElement() {
      return tagCache.getValue();
    }

    @Override
    @NotNull
    public PsiElement setName(@NotNull String name) {
      // This method is called by rename refactoring and has to succeed in order for the refactoring to succeed.
      // There no need to change the name since once the refactoring is complete, this object will be replaced
      // by a new one reflecting the changed source code.
      return this;
    }
  }
}
