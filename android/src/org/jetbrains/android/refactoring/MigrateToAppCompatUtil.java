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
package org.jetbrains.android.refactoring;

import com.android.annotations.NonNull;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.resources.ResourceType;
import com.android.tools.idea.lint.LintIdeClient;
import com.android.tools.idea.lint.LintIdeIssueRegistry;
import com.android.tools.idea.lint.LintIdeRequest;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ProjectResourceRepository;
import com.android.tools.lint.checks.AppCompatCustomViewDetector;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.TextFormat;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.SmartList;
import org.jetbrains.android.inspections.lint.ProblemData;
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.MethodMigrationEntry;
import org.jetbrains.android.refactoring.MigrateToAppCompatUsageInfo.ChangeCustomViewUsageInfo;
import org.jetbrains.android.refactoring.MigrateToAppCompatUsageInfo.ClassMigrationUsageInfo;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT;
import static com.android.SdkConstants.CLASS_ACTIVITY;
import static com.intellij.psi.util.PsiTreeUtil.getParentOfType;

class MigrateToAppCompatUtil {

  // Class known for its static members
  private MigrateToAppCompatUtil() {
  }

  static List<UsageInfo> findClassUsages(@NonNull Project project,
                                         @NonNull Module[] modules,
                                         @NonNull PsiMigration psiMigration,
                                         @NonNull String qName) {
    PsiClass aClass = findOrCreateClass(project, psiMigration, qName);
    if (aClass == null) {
      return Collections.emptyList();
    }
    List<UsageInfo> results = new SmartList<>();
    for (Module module : modules) {
      for (PsiReference usage : ReferencesSearch.search(aClass, GlobalSearchScope.moduleScope(module), false)) {
        results.add(new UsageInfo(usage));
      }
    }
    return results;
  }

  static Collection<PsiReference> findChangeMethodRefs(Project project, MethodMigrationEntry entry) {
    String psiClass = entry.myOldClassName;
    PsiClass psiLookupClass = JavaPsiFacade.getInstance(project).findClass(psiClass, GlobalSearchScope.allScope(project));
    assert psiLookupClass != null : psiClass + " not found";
    PsiMethod[] methods = psiLookupClass.findMethodsByName(entry.myOldMethodName, true);
    if (methods.length > 0) {
      List<PsiReference> refs = new ArrayList<>();
      for (PsiMethod method : methods) {
        RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(method);
        refs.addAll(processor.findReferences(methods[0], false));
      }
      return refs;
    }
    return Collections.emptyList();
  }

  // Code copied from MigrationUtil since it's not marked public
  static PsiClass findOrCreateClass(Project project, final PsiMigration migration, final String qName) {
    PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(qName, GlobalSearchScope.allScope(project));
    if (aClass == null) {
      aClass = WriteAction.compute(() -> migration.createClass(qName));
    }
    return aClass;
  }

  @NonNull
  static List<ChangeCustomViewUsageInfo> findCustomViewsUsages(@NonNull Project project, @NonNull Module[] modules) {
    PsiManager manager = PsiManager.getInstance(project);
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();

    Map<Issue, Map<File, List<ProblemData>>> issues = computeCustomViewIssuesMap(project, modules);
    Map<File, List<ProblemData>> fileListMap = issues.get(AppCompatCustomViewDetector.ISSUE);
    if (fileListMap == null) {
      return Collections.emptyList();
    }

    List<ChangeCustomViewUsageInfo> result = Lists.newArrayList();

    //noinspection ConstantConditions
    Map<PsiFile, List<ProblemData>> psiFileListMap = fileListMap.entrySet().stream()
      .filter(e -> fileSystem.findFileByIoFile(e.getKey()) != null)
      .collect(Collectors.toMap(
        e -> manager.findFile(fileSystem.findFileByIoFile(e.getKey())),
        Map.Entry::getValue));

    for (Map.Entry<PsiFile, List<ProblemData>> entry : psiFileListMap.entrySet()) {
      PsiFile psiFile = entry.getKey();

      if (!psiFile.isValid()) {
        continue;
      }
      List<ProblemData> problemDataList = entry.getValue();

      for (ProblemData problemData : problemDataList) {
        Integer start = problemData.getTextRange().getStartOffset();
        LintFix fix = problemData.getQuickfixData();
        if (!(fix instanceof LintFix.ReplaceString)) continue;
        LintFix.ReplaceString replaceFix = (LintFix.ReplaceString)fix;
        String suggestedSuperClass = replaceFix.replacement;
        PsiElement element = PsiTreeUtil.findElementOfClassAtOffset(psiFile, start, PsiElement.class, true);
        if (element != null) {
          result.add(new ChangeCustomViewUsageInfo(element, suggestedSuperClass));
        }
      }
    }
    return result;
  }

  /**
   * Run the {@link AppCompatCustomViewDetector} lint check to find all usages of Custom Views that need
   * to be migrated to their appCompat counterparts.
   *
   * @param project
   * @param modules
   * @return map of issues with the problemdata.
   */
  @NotNull
  static Map<Issue, Map<File, List<ProblemData>>> computeCustomViewIssuesMap(@NotNull Project project, @NotNull Module[] modules) {
    Map<Issue, Map<File, List<ProblemData>>> map = Maps.newHashMap();
    boolean detectorWasEnabled = AppCompatCustomViewDetector.ISSUE.isEnabledByDefault();
    AppCompatCustomViewDetector.ISSUE.setEnabledByDefault(true);
    AnalysisScope scope = new AnalysisScope(project);

    try {
      Set<Issue> issues = new HashSet<>(1);
      issues.add(AppCompatCustomViewDetector.ISSUE);
      LintIdeClient client = LintIdeClient.forBatch(project, map, scope, issues);
      LintRequest request = new LintIdeRequest(client, project, null, Arrays.asList(modules), false) {
        @NonNull
        @Override
        public com.android.tools.lint.detector.api.Project getMainProject(@NonNull com.android.tools.lint.detector.api.Project project) {
          com.android.tools.lint.detector.api.Project mainProject = super.getMainProject(project);
          return new com.android.tools.lint.detector.api.Project(mainProject.getClient(), mainProject.getDir(),
                                                                 mainProject.getReferenceDir()) {
            @Override
            public Boolean dependsOn(@NotNull String artifact) {
              // Make it look like the App already depends on AppCompat to get the warnings for custom views.
              if (APPCOMPAT_LIB_ARTIFACT.equals(artifact)) {
                return Boolean.TRUE;
              }
              return super.dependsOn(artifact);
            }
          };
        }
      };
      request.setScope(Scope.JAVA_FILE_SCOPE);
      new LintDriver(new LintIdeIssueRegistry(), client, request).analyze();
    }
    finally {
      AppCompatCustomViewDetector.ISSUE.setEnabledByDefault(detectorWasEnabled);
    }
    return map;
  }

  /**
   * Class Migrations such as replacing imports, changing extends require the following logic
   * to ensure that fully qualified names do not end up in the surce java files.
   *
   * First, collect all the changes necessary for a particular {@link PsiFile}.
   * Next, sort the changes to have the import replacements <strong>before</strong> any
   * of the other changes such as extends clauses.
   *
   * If this order is not followed, shortenClassReferences() does not work because it finds
   * an import with a similar shortName.
   *
   * @param psiMigration    The PsiMigration instance necessary for looking up or creating classes.
   * @param classMigrations The list of UsageInfo's to be migrated.
   */
  static void doClassMigrations(@NonNull PsiMigration psiMigration,
                                @NonNull List<ClassMigrationUsageInfo> classMigrations) {
    ArrayListMultimap<PsiFile, ClassMigrationUsageInfo> map = ArrayListMultimap.create();
    for (ClassMigrationUsageInfo migrationUsageInfo : classMigrations) {
      if (migrationUsageInfo.getElement() == null || migrationUsageInfo.getElement().getContainingFile() == null) {
        continue;
      }
      map.put(migrationUsageInfo.getElement().getContainingFile(), migrationUsageInfo);
    }

    for (PsiFile key : map.keySet()) {
      List<ClassMigrationUsageInfo> usageInfos = map.get(key);
      usageInfos.sort((a, b) -> {
        boolean aIsImport = getParentOfType(a.getElement(), PsiImportStatement.class) != null;
        boolean bIsImport = getParentOfType(b.getElement(), PsiImportStatement.class) != null;
        if (aIsImport && bIsImport) {
          return 0;
        }
        else if (aIsImport) {
          return -1;
        }
        else {
          return 1;
        }
      });
      for (ClassMigrationUsageInfo info : usageInfos) {
        info.applyChange(psiMigration);
      }
    }
  }

  /**
   * Get {@link XmlFile} instances of type {@link ResourceType} from the given
   * {@link AbstractResourceRepository} and {@link Project}.
   *
   * @param project      The project to use to get the PsiFile.
   * @param repository   The repository to be used for getting the items.
   * @param resourceType The resourceType to look up
   * @return A Set of XmlFile objects.
   */
  @NonNull
  static Set<XmlFile> getPsiFilesOfType(@NonNull Project project,
                                        @NonNull AbstractResourceRepository repository,
                                        @NonNull ResourceType resourceType) {

    Collection<String> itemsOfType = repository.getItemsOfType(resourceType);

    return itemsOfType.stream()
      .map(name -> repository.getResourceItem(resourceType, name))
      .flatMap(Collection::stream)
      .map(item -> LocalResourceRepository.getItemPsiFile(project, item))
      .filter(f -> f instanceof XmlFile)
      .map(XmlFile.class::cast)
      .collect(Collectors.toSet());
  }

  /**
   * Utility method for finding usages of any {@link AppCompatMigrationEntry.XmlElementMigration}.
   *
   * @param project the current project
   * @param modules The modules that should be looked at for this project.
   * @param operations A list of {@link AppCompatMigrationEntry.XmlElementMigration} instances that define
   *                   which tags/attributes and attribute values should be looked at.
   * @param resourceType The {@link ResourceType} such as LAYOUT, MENU that is used for fetching
   *                     the resources from the {@link ProjectResourceRepository}.
   * @return A list of UsageInfos that describe the changes to be migrated.
   */
  public static List<UsageInfo> findUsagesOfXmlElements(@NonNull Project project,
                                                        @NonNull Module[] modules,
                                                        @NonNull List<AppCompatMigrationEntry.XmlElementMigration> operations,
                                                        @NonNull ResourceType resourceType) {

    if (operations.isEmpty()) {
      return Collections.emptyList();
    }
    // Create a mapping between tagName => XmlElementMigration so we can simply lookup any xmlOperations
    // for a given tagName when visiting all the xml tags. (This is to prevent looking at each operation
    // while visiting every xml tag in a file)
    ArrayListMultimap<String, AppCompatMigrationEntry.XmlElementMigration> tag2XmlOperation = ArrayListMultimap.create();
    for (AppCompatMigrationEntry.XmlElementMigration operation : operations) {
      for (String tagName : operation.applicableTagNames()) {
        tag2XmlOperation.put(tagName, operation);
      }
    }

    List<UsageInfo> usageInfos = new ArrayList<>();
    for (Module module : modules) {
      ProjectResourceRepository projectResources = ProjectResourceRepository.getOrCreateInstance(module);
      if (projectResources == null) {
        continue;
      }
      Set<XmlFile> xmlFiles = getPsiFilesOfType(project, projectResources, resourceType);
      for (XmlFile file : xmlFiles) {
        file.accept(new XmlRecursiveElementVisitor() {
          @Override
          public void visitXmlTag(XmlTag tag) {
            super.visitXmlTag(tag);
            List<AppCompatMigrationEntry.XmlElementMigration> operations = tag2XmlOperation.get(tag.getName());
            if (operations != null) {
              for (AppCompatMigrationEntry.XmlElementMigration operation : operations) {
                UsageInfo usage = operation.apply(tag);
                if (usage != null) {
                  usageInfos.add(usage);
                }
              }
            }
          }
        });
      }
    }
    tag2XmlOperation.clear();
    return usageInfos;
  }

  /**
   * Prevent the issue where we show Usages only pointing to a migration for an import
   * especially for Activity and FragmentActivity.
   *
   * This happens because we exclude the two classes when used within a method parameter.
   * for e.g: onAttach(Activity activity) - should not be migrated.
   *
   * @param infos The usageInfos to process
   * @param filesNeedingActivityOrFragmentActivity files which were part of the exclude
   *                                               because they contained references to the specific
   *                                               classes in method parameters.
   */
  public static void removeUnneededUsages(@NonNull List<UsageInfo> infos) {

    ArrayListMultimap<PsiFile, ClassMigrationUsageInfo> map = ArrayListMultimap.create();
    for (UsageInfo usageInfo : infos) {
      if (!(usageInfo instanceof ClassMigrationUsageInfo)) {
        continue;
      }
      if (usageInfo.getElement() == null || usageInfo.getElement().getContainingFile() == null) {
        continue;
      }
      map.put(usageInfo.getElement().getContainingFile(), (ClassMigrationUsageInfo)usageInfo);
    }

    List<UsageInfo> toRemove = new SmartList<>();
    for (PsiFile file : map.keySet()) {
      List<ClassMigrationUsageInfo> usages = map.get(file);
      boolean excludeUsages = usages.stream()
        .allMatch(u -> {
          if (u.getElement() != null && u.getElement().getParent() instanceof PsiImportStatement) {
            String qname = ((PsiImportStatement)u.getElement().getParent()).getQualifiedName();
            if (qname != null &&
                (qname.equals(CLASS_ACTIVITY) || qname.equals(MigrateToAppCompatProcessor.CLASS_SUPPORT_FRAGMENT_ACTIVITY))) {
              return true;
            }
          }
          return false;
        });
      if (excludeUsages) {
        toRemove.addAll(usages);
      }
    }
    infos.removeAll(toRemove);
  }
}
