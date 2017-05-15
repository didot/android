/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.refactoring.modularize;

import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ProjectResourceRepository;
import com.android.tools.idea.res.ResourceHelper;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.actionSystem.LangDataKeys.TARGET_MODULE;

public class AndroidModularizeHandler implements RefactoringActionHandler {

  private static final int RESOURCE_SET_INITIAL_SIZE = 100;

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    invoke(project, BaseRefactoringAction.getPsiElementArray(dataContext), dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    CodeAndResourcesReferenceCollector scanner = new CodeAndResourcesReferenceCollector(project);

    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ApplicationManager.getApplication().runReadAction(
        () -> scanner.accumulate(elements)), "Computing References", false, project);

    AndroidModularizeProcessor processor =
      new AndroidModularizeProcessor(project,
                                     elements,
                                     scanner.getClassReferences(),
                                     scanner.getResourceReferences(),
                                     scanner.getManifestReferences(),
                                     scanner.getReferenceGraph());

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      Module targetModule = TARGET_MODULE.getData(dataContext);
      if (targetModule != null) {
        processor.setTargetModule(targetModule);
      }
      processor.run();
    }
    else {
      List<Module> suitableModules = new ArrayList<>();
      // Only offer modules that have an Android facet, otherwise we don't know where to move resources.
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet != null) {
          if (!IdeaSourceProvider.getCurrentSourceProviders(facet).isEmpty() && !facet.getAllResourceDirectories().isEmpty()) {
            suitableModules.add(module);
          }
        }
      }
      for (PsiElement root : elements) {
        Module sourceModule = ModuleUtilCore.findModuleForPsiElement(root);
        if (sourceModule != null) {
          suitableModules.remove(sourceModule);
        }
      }

      AndroidModularizeDialog dialog = new AndroidModularizeDialog(project, suitableModules, processor);
      dialog.show();
    }
  }


  private static class CodeAndResourcesReferenceCollector {
    private final Project myProject;

    private final Set<PsiClass> myClassRefSet = new LinkedHashSet<>();
    private final Set<ResourceItem> myResourceRefSet = new LinkedHashSet<>(RESOURCE_SET_INITIAL_SIZE);
    private final Set<PsiElement> myManifestRefSet = new HashSet<>();
    private final Queue<PsiElement> myVisitQueue = new ArrayDeque<>();
    private final AndroidCodeAndResourcesGraph.Builder myGraphBuilder = new AndroidCodeAndResourcesGraph.Builder();

    public CodeAndResourcesReferenceCollector(@NotNull Project project) {
      myProject = project;
    }

    public void accumulate(PsiElement... roots) {
      myVisitQueue.clear();
      for (PsiElement element : roots) {
        PsiClass ownerClass =
          (element instanceof PsiClass) ? (PsiClass)element : PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (ownerClass != null && myClassRefSet.add(ownerClass)) {
          myVisitQueue.add(ownerClass);
          myGraphBuilder.addRoot(ownerClass);
        }
      }

      ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      int numVisited = 0;

      while (!myVisitQueue.isEmpty()) {
        PsiElement element = myVisitQueue.poll();
        numVisited++;

        final AndroidFacet facet = AndroidFacet.getInstance(element);
        if (facet == null) {
          continue;
        }

        if (indicator != null) {
          indicator.setText(String.format("Scanning definition %1$d of %2$d", numVisited, numVisited + myVisitQueue.size()));
          indicator.setFraction((double)numVisited / (numVisited + myVisitQueue.size()));
        }

        if (element instanceof PsiClass) {
          element.accept(new JavaReferenceVisitor(facet, element));

          // Check for manifest entries referencing this class (this applies to activities, content providers, etc).
          GlobalSearchScope manifestScope = GlobalSearchScope.filesScope(myProject, IdeaSourceProvider.getManifestFiles(facet));

          ReferencesSearch.search(element, manifestScope).forEach(reference -> {
            PsiElement tag = reference.getElement();
            tag = PsiTreeUtil.getParentOfType(tag, XmlTag.class);

            if (tag != null) {
              if (myManifestRefSet.add(tag)) {
                // Scan the tag because we might have references to other resources.
                myVisitQueue.offer(tag);
              }

              myGraphBuilder.markReference(element, tag);
            }
          });
        }
        else {
          element.accept(new XmlResourceReferenceVisitor(facet, element));
        }
      }
    }

    @NotNull
    public Set<PsiClass> getClassReferences() {
      return myClassRefSet;
    }

    @NotNull
    public Set<ResourceItem> getResourceReferences() {
      return myResourceRefSet;
    }

    @NotNull
    public Set<PsiElement> getManifestReferences() {
      return myManifestRefSet;
    }

    @NotNull
    public AndroidCodeAndResourcesGraph getReferenceGraph() {
      return myGraphBuilder.build();
    }

    @Nullable
    private PsiElement getResourceDefinition(ResourceItem resource) {
      PsiFile psiFile = LocalResourceRepository.getItemPsiFile(myProject, resource);
      if (psiFile == null) { // psiFile could be null if this is dynamically defined, so nothing to visit...
        return null;
      }

      if (ResourceHelper.getFolderType(psiFile) == ResourceFolderType.VALUES) {
        // This is just a value, so we'll just scan its corresponding XmlTag
        return LocalResourceRepository.getItemTag(myProject, resource);
      }
      return psiFile;
    }

    private class XmlResourceReferenceVisitor extends XmlRecursiveElementWalkingVisitor {
      private final AndroidFacet myFacet;
      private final PsiElement mySource;
      private final ProjectResourceRepository myResourceRepository;

      XmlResourceReferenceVisitor(@NotNull AndroidFacet facet, @NotNull PsiElement source) {
        myFacet = facet;
        mySource = source;
        myResourceRepository = ProjectResourceRepository.getOrCreateInstance(facet);
      }

      @Override
      public void visitXmlAttributeValue(XmlAttributeValue element) {
        processPotentialReference(element.getValue());
      }

      @Override
      public void visitXmlToken(XmlToken token) {
        processPotentialReference(token.getText());
      }

      private void processPotentialReference(String text) {
        ResourceUrl url = ResourceUrl.parse(text);
        if (url != null) {
          if (!url.framework && !url.create && url.type != ResourceType.ID) {
            List<ResourceItem> matches = myResourceRepository.getResourceItem(url.type, url.name);
            if (matches != null) {
              for (ResourceItem match : matches) {
                PsiElement target = getResourceDefinition(match);
                if (myResourceRefSet.add(match)) {
                  myVisitQueue.offer(target);
                }
                myGraphBuilder.markReference(mySource, target);
              }
            }
          }
        } else {
          // Perhaps this is a reference to a Java class
          PsiClass target = JavaPsiFacade.getInstance(myProject).findClass(
            text, GlobalSearchScope.moduleWithDependenciesScope(myFacet.getModule()));
          if (target != null) {
            if (myClassRefSet.add(target)) {
              myVisitQueue.offer(target);
            }
            myGraphBuilder.markReference(mySource, target);
          }
        }
      }
    }

    private class JavaReferenceVisitor extends JavaRecursiveElementWalkingVisitor {
      private final AndroidFacet myFacet;
      private final PsiElement mySource;
      private final ProjectResourceRepository myResourceRepository;

      JavaReferenceVisitor(@NotNull AndroidFacet facet, @NotNull PsiElement source) {
        myFacet = facet;
        mySource = source;
        myResourceRepository = ProjectResourceRepository.getOrCreateInstance(facet);
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        PsiElement element = expression.resolve();
        if (element instanceof PsiField) {
          AndroidPsiUtils.ResourceReferenceType referenceType = AndroidPsiUtils.getResourceReferenceType(expression);

          if (referenceType == AndroidPsiUtils.ResourceReferenceType.APP) {
            // This is a resource we might be able to move
            ResourceType type = AndroidPsiUtils.getResourceType(expression);
            if (type != null && type != ResourceType.ID) {
              String name = AndroidPsiUtils.getResourceName(expression);

              List<ResourceItem> matches = myResourceRepository.getResourceItem(type, name);
              if (matches != null) {
                for (ResourceItem match : matches) {
                  PsiElement target = getResourceDefinition(match);
                  if (myResourceRefSet.add(match)) {
                    myVisitQueue.offer(target);
                  }
                  myGraphBuilder.markReference(mySource, target);
                }
              }
            }
            return; // We had a resource match, no need to keep visiting children.
          }
        }
        super.visitReferenceExpression(expression);
      }

      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        PsiElement target = reference.advancedResolve(false).getElement();
        if (target instanceof PsiClass) {
          if (!(target instanceof PsiTypeParameter) && !(target instanceof SyntheticElement)) {
            VirtualFile source = target.getContainingFile().getVirtualFile();
            for (IdeaSourceProvider sourceProvider : IdeaSourceProvider.getCurrentSourceProviders(myFacet)) {
              if (sourceProvider.containsFile(source)) {
                // This is a local source file, therefore a candidate to be moved
                if (myClassRefSet.add((PsiClass)target)) {
                  myVisitQueue.add(target);
                }
                if (target != mySource) { // Don't add self-references
                  myGraphBuilder.markReference(mySource, target);
                }
                return; // We had a reference match, nothing further to do
              }
            }
          }
        }
      }
    }
  }
}
