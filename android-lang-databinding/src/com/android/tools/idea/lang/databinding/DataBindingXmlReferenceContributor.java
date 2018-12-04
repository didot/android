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
package com.android.tools.idea.lang.databinding;

import static com.android.tools.idea.lang.databinding.DataBindingLangUtil.JAVA_LANG;

import android.databinding.tool.reflection.Callable;
import android.databinding.tool.reflection.ModelClass;
import android.databinding.tool.reflection.ModelMethod;
import com.android.ide.common.resources.DataBindingResourceType;
import com.android.tools.idea.databinding.DataBindingUtil;
import com.android.tools.idea.lang.databinding.model.PsiModelClass;
import com.android.tools.idea.lang.databinding.model.PsiModelMethod;
import com.android.tools.idea.lang.databinding.psi.PsiDbCallExpr;
import com.android.tools.idea.lang.databinding.psi.PsiDbExpr;
import com.android.tools.idea.lang.databinding.psi.PsiDbExpressionList;
import com.android.tools.idea.lang.databinding.psi.PsiDbId;
import com.android.tools.idea.lang.databinding.psi.PsiDbRefExpr;
import com.android.tools.idea.res.DataBindingInfo;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.PsiDataBindingResourceItem;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * For references in DataBinding expressions. For references in {@code <data>} tag,
 * see {@link org.jetbrains.android.dom.converters.DataBindingVariableTypeConverter}.
 */
public class DataBindingXmlReferenceContributor extends PsiReferenceContributor {
  // TODO: Support generics
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDbId.class), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        PsiDbId id = (PsiDbId)element;
        String text = element.getText();
        if (text == null) {
          return PsiReference.EMPTY_ARRAY;
        }

        DataBindingInfo dataBindingInfo = null;
        Module module = ModuleUtilCore.findModuleForPsiElement(element);
        if (module != null) {
          AndroidFacet facet = AndroidFacet.getInstance(module);
          if (facet != null && DataBindingUtil.isDataBindingEnabled(facet)) {
            LocalResourceRepository moduleResources = ResourceRepositoryManager.getModuleResources(facet);
            PsiFile topLevelFile = InjectedLanguageManager.getInstance(element.getProject()).getTopLevelFile(element);
            if (topLevelFile != null) {
              if (topLevelFile.getFileType() == DbFileType.INSTANCE) {
                PsiElement fileContext = topLevelFile.getContext();
                if (fileContext != null) {
                  topLevelFile = fileContext.getContainingFile();
                }
              }
              String name = topLevelFile.getName();
              name = name.substring(0, name.lastIndexOf('.'));
              dataBindingInfo = moduleResources.getDataBindingInfoForLayout(name);
            }
          }
        }
        if (dataBindingInfo == null) {
          return PsiReference.EMPTY_ARRAY;
        }
        for (PsiDataBindingResourceItem variable : dataBindingInfo.getItems(DataBindingResourceType.VARIABLE).values()) {
          if (text.equals(variable.getName())) {
            XmlTag xmlTag = variable.getXmlTag();
            return toArray(new VariableDefinitionReference(id, xmlTag, variable, dataBindingInfo, module));
          }
        }
        for (PsiDataBindingResourceItem anImport : dataBindingInfo.getItems(DataBindingResourceType.IMPORT).values()) {
          if (text.equals(DataBindingUtil.getAlias(anImport))) {
            XmlTag xmlTag = anImport.getXmlTag();
            return toArray(new ImportDefinitionReference(id, xmlTag, anImport, module));
          }
        }
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(id.getProject());
        if (text.indexOf('.') < 0) {
          PsiClass langClass = javaPsiFacade.findClass(JAVA_LANG + text, GlobalSearchScope.moduleWithLibrariesScope(module));
          if (langClass != null) {
            return toArray(new ClassDefinitionReference(id, langClass));
          }
        }
        PsiPackage aPackage = javaPsiFacade.findPackage(text);
        if (aPackage != null) {
          return toArray(new PackageReference(id, aPackage));
        }
        return PsiReference.EMPTY_ARRAY;
      }
    });

    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDbRefExpr.class), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        PsiDbRefExpr refExpr = (PsiDbRefExpr)element;

        PsiDbExpr qualifierExpr = refExpr.getExpr();
        if (qualifierExpr == null) {
          PsiDbId id = refExpr.getId();
          return id.getReferences();
        }
        ResolvesToModelClass ref = resolveClassReference(qualifierExpr);
        PsiModelClass psiModelClass = resolveClassType(ref);
        if (psiModelClass == null) {
          PsiReference[] references = qualifierExpr.getReferences();

          if (references.length > 0) {
            String fieldText = refExpr.getId().getText();
            Module module = ModuleUtilCore.findModuleForPsiElement(element);
            if (module == null || StringUtil.isEmpty(fieldText)) {
              return PsiReference.EMPTY_ARRAY;
            }
            GlobalSearchScope scope = module.getModuleWithDependenciesAndLibrariesScope(false);
            for (PsiReference reference : references) {
              if (reference instanceof PackageReference) {
                PsiPackage aPackage = ((PackageReference)reference).resolve();
                if (aPackage != null) {
                  for (PsiPackage subPackage : aPackage.getSubPackages(scope)) {
                    String name = subPackage.getName();
                    //noinspection ConstantConditions as a subpackage's name can't be null.
                    if (name.endsWith(fieldText) &&
                        (name.length() == fieldText.length() || name.charAt(name.length() - fieldText.length() - 1) == '.')) {
                      return toArray(new PackageReference(element, subPackage));
                    }
                  }
                  PsiClass[] classes = aPackage.findClassByShortName(fieldText, scope);
                  if (classes.length > 0) {
                    PsiReference[] refs = new PsiReference[classes.length];
                    for (int i = 0; i < classes.length; i++) {
                      refs[i] = new ClassDefinitionReference(element, classes[i]);
                    }
                    return refs;
                  }
                }
              }
            }
          }
          return PsiReference.EMPTY_ARRAY;
        }
        PsiDbId fieldName = refExpr.getId();
        String fieldText = fieldName.getText();
        if (StringUtil.isEmpty(fieldText)) {
          return PsiReference.EMPTY_ARRAY;
        }
        // TODO: Search for methods with args also. The following only searches for methods with no args.
        // This results in attributes like 'android:onClick="@{variable.method}"' to be unresolved.
        Callable getterOrField = psiModelClass.findGetterOrField(fieldText, ref.isStatic());
        PsiClass psiClass = psiModelClass.getPsiClass();
        if (psiClass == null) {
          return PsiReference.EMPTY_ARRAY;
        }
        // TODO: If psiClass is ObservableField<Foo> or ObservablePrimitive, change it to Foo (by an implicit call to #get()).
        if (getterOrField != null) {
          if (getterOrField.type.equals(Callable.Type.METHOD)) {
            PsiMethod[] methodsByName = psiClass.findMethodsByName(getterOrField.name, true);
            if (methodsByName.length > 0) {
              return toArray(new PsiMethodReference(refExpr, methodsByName[0]));
            }
          } else if (getterOrField.type.equals(Callable.Type.FIELD)) {
            PsiField fieldsByName = psiClass.findFieldByName(getterOrField.name, true);
            if (fieldsByName != null) {
              return toArray(new PsiFieldReference(refExpr, fieldsByName));
            }
          }
        }

        // The field probably references an inner class.
        Module module = ModuleUtilCore.findModuleForPsiElement(element);
        if (module != null) {
          String innerName = psiClass.getQualifiedName() + '.' + fieldText;
          PsiClass clazz =
            JavaPsiFacade.getInstance(element.getProject()).findClass(innerName, module.getModuleWithDependenciesAndLibrariesScope(false));
          if (clazz != null) {
            return toArray(new ClassDefinitionReference(element, clazz));
          }
        }
        return PsiReference.EMPTY_ARRAY;
      }
    });

    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDbCallExpr.class), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        PsiDbCallExpr callExpr = (PsiDbCallExpr)element;
        PsiDbExpr receiverExpr = callExpr.getRefExpr().getExpr();
        if (receiverExpr == null) {
          return PsiReference.EMPTY_ARRAY;
        }
        PsiModelClass psiModelClass = resolveClassType(resolveClassReference(receiverExpr));
        if (psiModelClass == null) {
          return PsiReference.EMPTY_ARRAY;
        }
        // TODO we need to do this incrementally. e.g. find a method then a method which matches all params
        List<ModelClass> args = new ArrayList<>();
        boolean hasInvalidArg = false;
        PsiDbExpressionList expressionList = callExpr.getExpressionList();
        if (expressionList != null) {
          for (PsiDbExpr expr : expressionList.getExprList()) {
            ModelClass refClass = resolveClassType(resolveClassReference(expr));
            if (refClass == null) {
              hasInvalidArg = true;
              break;
            }
            args.add(refClass);
          }
        }

        if (!hasInvalidArg) {
          // todo check static
          ModelMethod method = psiModelClass.getMethod(callExpr.getRefExpr().getId().getText(), args, false, false);
          if (method instanceof PsiModelMethod) {
            return toArray(new PsiMethodReference(callExpr, ((PsiModelMethod)method).getPsiMethod()));
          }
        }
        List<ModelMethod> methods = psiModelClass.findMethods(callExpr.getRefExpr().getId().getText(), false);
        List<PsiReference> selected = new ArrayList<>();
        for (ModelMethod modelMethod : methods) {
          if (modelMethod instanceof PsiModelMethod) {
            selected.add(new PsiMethodReference(callExpr, ((PsiModelMethod)modelMethod).getPsiMethod()));
          }
        }
        return selected.toArray(PsiReference.EMPTY_ARRAY);
      }
    });
  }

  @Nullable
  private static PsiModelClass resolveClassType(@Nullable ResolvesToModelClass ref) {
    return ref == null ? null : ref.getResolvedType();
  }

  @Nullable
  private static ResolvesToModelClass resolveClassReference(@NotNull PsiDbExpr expr) {
    PsiReference[] references = expr.getReferences();
    for (PsiReference ref : references) {
      if (ref instanceof ResolvesToModelClass) {
        return (ResolvesToModelClass) ref;
      }
    }
    return null;
  }

  public interface ResolvesToModelClass {
    @Nullable PsiModelClass getResolvedType();
    boolean isStatic();
  }

  private static class PsiFieldReference extends DefinitionReference {

    private PsiFieldReference(@NotNull PsiDbRefExpr refExpr, @NotNull PsiField field) {
      super(refExpr, field, refExpr.getId().getTextRange().shiftRight(-refExpr.getStartOffsetInParent()));
    }

    @NotNull
    @Override
    public PsiModelClass getResolvedType() {
      return new PsiModelClass(((PsiField)myTarget).getType());
    }

    @Override
    public boolean isStatic() {
      PsiModifierList modifierList = ((PsiField)myTarget).getModifierList();
      return modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC);
    }
  }

  private static class PsiMethodReference extends DefinitionReference {

    private PsiMethodReference(@NotNull PsiDbCallExpr expr, @NotNull PsiMethod method) {
      super(expr, method, expr.getRefExpr().getId().getTextRange().shiftRight(-expr.getStartOffsetInParent()));
    }

    private PsiMethodReference(@NotNull PsiDbRefExpr expr, @NotNull PsiMethod method) {
      super(expr, method, expr.getId().getTextRange().shiftRight(-expr.getStartOffsetInParent()));
    }

    @Nullable
    @Override
    public PsiModelClass getResolvedType() {
      PsiType returnType = ((PsiMethod)myTarget).getReturnType();
      return returnType != null ? new PsiModelClass(returnType) : null;
    }

    @Override
    public boolean isStatic() {
      return false;
    }
  }

  private static class VariableDefinitionReference extends DefinitionReference {
    private final PsiModelClass myModelClass;

    private VariableDefinitionReference(@NotNull PsiElement element,
                                        @NotNull XmlTag resolveTo,
                                        @NotNull PsiDataBindingResourceItem variable,
                                        @NotNull DataBindingInfo dataBindingInfo,
                                        @NotNull Module module) {
      super(element, resolveTo);
      String type = DataBindingUtil.getQualifiedType(variable.getTypeDeclaration(), dataBindingInfo, false);
      PsiModelClass modelClass = null;
      if (type != null) {
        PsiClass psiType =
            JavaPsiFacade.getInstance(element.getProject()).findClass(type, module.getModuleWithDependenciesAndLibrariesScope(false));
        if (psiType != null) {
          modelClass = new PsiModelClass(PsiTypesUtil.getClassType(psiType));
        }
      }
      myModelClass = modelClass;
    }

    @Override
    @Nullable/*Unable to resolve type for the variable.*/
    public PsiModelClass getResolvedType() {
      return myModelClass;
    }

    @Override
    public boolean isStatic() {
      return false;
    }
  }

  private static class ImportDefinitionReference extends DefinitionReference {
    private final PsiModelClass myModelClass;

    private ImportDefinitionReference(@NotNull PsiElement element,
                                      @NotNull XmlTag resolveTo,
                                      @NotNull PsiDataBindingResourceItem variable,
                                      @NotNull Module module) {
      super(element, resolveTo);
      String type = variable.getTypeDeclaration();
      PsiModelClass modelClass = null;
      if (type != null) {
        PsiClass psiType =
          JavaPsiFacade.getInstance(element.getProject()).findClass(type, module.getModuleWithDependenciesAndLibrariesScope(false));
        if (psiType != null) {
          modelClass = new PsiModelClass(PsiTypesUtil.getClassType(psiType));
        }
      }
      myModelClass = modelClass;
    }

    @Override
    public PsiModelClass getResolvedType() {
      return myModelClass;
    }

    @Override
    public boolean isStatic() {
      return true;
    }
  }

  private static class ClassDefinitionReference extends DefinitionReference {

    private ClassDefinitionReference(@NotNull PsiElement element, @NotNull PsiClass resolveTo) {
      super(element, resolveTo);
    }

    @NotNull
    @Override
    public PsiModelClass getResolvedType() {
      return new PsiModelClass(PsiTypesUtil.getClassType((PsiClass)myTarget));
    }

    @Override
    public boolean isStatic() {
      return true;
    }
  }

  private static PsiReference[] toArray(PsiReference... ref) {
    return ref;
  }

  private static class PackageReference implements PsiReference {
    private final PsiElement myElement;
    private final PsiPackage myTarget;
    private final TextRange myTextRange;

    private PackageReference(@NotNull PsiElement element, @NotNull PsiPackage target) {
      myElement = element;
      myTarget = target;
      myTextRange = myElement.getTextRange().shiftRight(-myElement.getStartOffsetInParent());
    }

    @NotNull
    @Override
    public PsiElement getElement() {
      return myElement;
    }

    @NotNull
    @Override
    public TextRange getRangeInElement() {
      return myTextRange;
    }

    @Nullable
    @Override
    public PsiPackage resolve() {
      return myTarget;
    }

    @NotNull
    @Override
    public String getCanonicalText() {
      return myElement.getText();
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
      return null;
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      return null;
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
      return myElement.getManager().areElementsEquivalent(resolve(), element);
    }

    @Override
    public boolean isSoft() {
      return false;
    }
  }
}
