// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.inspections.lint;

import static org.jetbrains.android.inspections.lint.AndroidLintInspectionBase.LINT_INSPECTION_PREFIX;

import com.android.ide.common.gradle.model.IdeLintOptions;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.editors.strings.StringsVirtualFile;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.lint.AndroidLintLintBaselineInspection;
import com.android.tools.idea.lint.LintIdeAnalytics;
import com.android.tools.idea.lint.LintIdeClient;
import com.android.tools.idea.lint.LintIdeIssueRegistry;
import com.android.tools.idea.lint.LintIdeProject;
import com.android.tools.idea.lint.LintIdeRequest;
import com.android.tools.lint.client.api.LintBaseline;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.Scope;
import com.google.wireless.android.sdk.stats.LintSession.AnalysisType;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class AndroidLintGlobalInspectionContext implements GlobalInspectionContextExtension<AndroidLintGlobalInspectionContext> {
  static final Key<AndroidLintGlobalInspectionContext> ID = Key.create("AndroidLintGlobalInspectionContext");
  private Map<Issue, Map<File, List<ProblemData>>> myResults;
  private LintBaseline myBaseline;
  private Issue myEnabledIssue;

  @NotNull
  @Override
  public Key<AndroidLintGlobalInspectionContext> getID() {
    return ID;
  }

  @Override
  public void performPreRunActivities(@NotNull List<Tools> globalTools, @NotNull List<Tools> localTools, @NotNull final GlobalInspectionContext context) {
    final Project project = context.getProject();

    // Running a single inspection that's not lint? If so don't run lint
    boolean runningSingleInspection = localTools.isEmpty() && globalTools.size() == 1;
    if (runningSingleInspection) {
      Tools tool = globalTools.get(0);
      if (!tool.getShortName().startsWith(LINT_INSPECTION_PREFIX)) {
        return;
      }
    }

    if (!ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      return;
    }

    Set<Issue> issues = AndroidLintExternalAnnotator.getIssuesFromInspections(project, null);
    if (issues.isEmpty()) {
      return;
    }

    long startTime = System.currentTimeMillis();

    // If running a single check by name, turn it on if it's off by default.
    if (runningSingleInspection) {
      Tools tool = globalTools.get(0);
      String id = tool.getShortName().substring(LINT_INSPECTION_PREFIX.length());
      Issue issue = new LintIdeIssueRegistry().getIssue(id);
      if (issue != null && !issue.isEnabledByDefault()) {
        issues = Collections.singleton(issue);
        issue.setEnabledByDefault(true);
        // And turn it back off again in cleanup
        myEnabledIssue = issue;
      }
    }

    final Map<Issue, Map<File, List<ProblemData>>> problemMap = new HashMap<>();
    AnalysisScope scope = context.getRefManager().getScope();
    if (scope == null) {
      scope = AndroidLintLintBaselineInspection.ourRerunScope;
      if (scope == null) {
        return;
      }
    }

    final LintIdeClient client = LintIdeClient.forBatch(project, problemMap, scope, issues);

    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      ProgressWrapper.unwrap(indicator).setText("Running Android Lint");
    }

    EnumSet<Scope> lintScope;
    //noinspection ConstantConditions
    if (!LintIdeProject.SUPPORT_CLASS_FILES) {
      lintScope = EnumSet.copyOf(Scope.ALL);
      // Can't run class file based checks
      lintScope.remove(Scope.CLASS_FILE);
      lintScope.remove(Scope.ALL_CLASS_FILES);
      lintScope.remove(Scope.JAVA_LIBRARIES);
    } else {
      lintScope = Scope.ALL;
    }

    List<VirtualFile> files = null;
    final List<Module> modules = new ArrayList<>();

    int scopeType = scope.getScopeType();
    switch (scopeType) {
      case AnalysisScope.MODULE: {
        SearchScope searchScope = ReadAction.compute(scope::toSearchScope);
        if (searchScope instanceof ModuleWithDependenciesScope) {
          ModuleWithDependenciesScope s = (ModuleWithDependenciesScope)searchScope;
          if (!s.isSearchInLibraries()) {
            modules.add(s.getModule());
          }
        }
        break;
      }
      case AnalysisScope.FILE:
      case AnalysisScope.VIRTUAL_FILES:
      case AnalysisScope.UNCOMMITTED_FILES: {
        files = new ArrayList<>();
        SearchScope searchScope = ReadAction.compute(scope::toSearchScope);
        if (searchScope instanceof LocalSearchScope) {
          final LocalSearchScope localSearchScope = (LocalSearchScope)searchScope;
          final PsiElement[] elements = localSearchScope.getScope();
          final List<VirtualFile> finalFiles = files;

          ApplicationManager.getApplication().runReadAction(() -> {
            for (PsiElement element : elements) {
              if (element instanceof PsiFile) { // should be the case since scope type is FILE
                Module module = ModuleUtilCore.findModuleForPsiElement(element);
                if (module != null && !modules.contains(module)) {
                  modules.add(module);
                }
                VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
                if (virtualFile != null) {
                  if (virtualFile instanceof StringsVirtualFile) {
                    StringsVirtualFile f = (StringsVirtualFile)virtualFile;
                    if (!modules.contains(f.getFacet().getModule())) {
                      modules.add(f.getFacet().getModule());
                    }
                  } else {
                    finalFiles.add(virtualFile);
                  }
                }
              }
            }
          });
        } else {
          final List<VirtualFile> finalList = files;
          scope.accept(new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
              VirtualFile virtualFile = file.getVirtualFile();
              if (virtualFile != null) {
                finalList.add(virtualFile);
              }
            }
          });
        }
        if (files.isEmpty()) {
          files = null;
        } else {
          // Lint will compute it lazily based on actual files in the request
          lintScope = null;
        }
        break;
      }
      case AnalysisScope.PROJECT: {
        modules.addAll(Arrays.asList(ModuleManager.getInstance(project).getModules()));
        break;
      }
      case AnalysisScope.CUSTOM:
      case AnalysisScope.MODULES:
      case AnalysisScope.DIRECTORY: {
        // Handled by the getNarrowedComplementaryScope case below
        break;
      }

      case AnalysisScope.INVALID:
        break;
      default:
        Logger.getInstance(this.getClass()).warn("Unexpected inspection scope " + scope + ", " + scopeType);
    }

    if (modules.isEmpty()) {
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        if (scope.containsModule(module)) {
          modules.add(module);
        }
      }

      if (modules.isEmpty() && files != null) {
        for (VirtualFile file : files) {
          Module module = ModuleUtilCore.findModuleForFile(file, project);
          if (module != null && !modules.contains(module)) {
            modules.add(module);
          }
        }
      }

      if (modules.isEmpty()) {
        AnalysisScope scopeRef = scope; // Need effectively final reference to permit capture by lambda.
        AnalysisScope narrowed = ReadAction.compute(() -> scopeRef.getNarrowedComplementaryScope(project));
        for (Module module : ModuleManager.getInstance(project).getModules()) {
          if (narrowed.containsModule(module)) {
            modules.add(module);
          }
        }
      }
    }

    LintRequest request = new LintIdeRequest(client, project, files, modules, false);
    request.setScope(lintScope);
    final LintDriver lint = new LintDriver(new LintIdeIssueRegistry(), client, request);

    // Baseline analysis?
    myBaseline = null;
    Module severityModule = null;
    for (Module module : modules) {
      AndroidModuleModel model = AndroidModuleModel.get(module);
      if (model != null) {
        GradleVersion version = model.getModelVersion();
        if (version != null && version.isAtLeast(2, 3, 0, "beta", 2, true)) {
          IdeLintOptions options = model.getAndroidProject().getLintOptions();
          try {
            if (options.getSeverityOverrides() != null) {
              severityModule = module;
            }

            File baselineFile = options.getBaselineFile();
            if (baselineFile != null && !AndroidLintLintBaselineInspection.ourSkipBaselineNextRun) {
              if (!baselineFile.isAbsolute()) {
                String path = module.getProject().getBasePath();
                if (path != null) {
                  baselineFile = new File(FileUtil.toSystemDependentName(path), baselineFile.getPath());
                }
              }
              myBaseline = new LintBaseline(client, baselineFile);
              lint.setBaseline(myBaseline);
              if (!baselineFile.isFile()) {
                myBaseline.setWriteOnClose(true);
              } else if (AndroidLintLintBaselineInspection.ourUpdateBaselineNextRun) {
                myBaseline.setRemoveFixed(true);
                myBaseline.setWriteOnClose(true);
              }

            }
          } catch (Throwable unsupported) {
            // During 2.3 development some builds may have this method, others may not
          }
        }
        break;
      }
    }

    lint.analyze();

    // Running all detectors? Then add dynamically registered detectors too.
    if (!runningSingleInspection) {
      List<Tools> tools = AndroidLintInspectionBase.getDynamicTools(project);
      if (tools != null) {
        for (Tools tool : tools) {
          // can't just call globalTools.contains(tool): ToolsImpl.equals does *not* check
          // tool identity, it just checks settings identity.
          String name = tool.getShortName();
          boolean found = false;
          for (Tools registered : globalTools) {
            if (registered.getShortName().equals(name)) {
              found = true;
              break;
            }
          }
          if (!found) {
            globalTools.add(tool);
          }
        }
      }
    }

    AndroidLintLintBaselineInspection.clearNextRunState();

    lint.setAnalysisStartTime(startTime);
    LintIdeAnalytics analytics = new LintIdeAnalytics(project);
    analytics.logSession(AnalysisType.IDE_BATCH, lint, severityModule, null, problemMap);

    myResults = problemMap;
  }

  @Nullable
  public Map<Issue, Map<File, List<ProblemData>>> getResults() {
    return myResults;
  }

  @Override
  public void performPostRunActivities(@NotNull List<InspectionToolWrapper> inspections, @NotNull final GlobalInspectionContext context) {
    if (myBaseline != null) {
      // Close the baseline; we need to hold a read lock such that line numbers can be computed from PSI file contents
      if (myBaseline.getWriteOnClose()) {
        ApplicationManager.getApplication().runReadAction(() -> myBaseline.close());
      }

      // If we wrote a baseline file, post a notification
      if (myBaseline.getWriteOnClose()) {
        String message;
        if (myBaseline.getRemoveFixed()) {
          message = String
            .format(Locale.US, "Updated baseline file %1$s<br>Removed %2$d issues<br>%3$s remaining", myBaseline.getFile().getName(),
                    myBaseline.getFixedCount(),
                    Lint.describeCounts(myBaseline.getFoundErrorCount(), myBaseline.getFoundWarningCount(), false,
                                        true));
        } else {
          message = String
            .format(Locale.US, "Created baseline file %1$s<br>%2$d issues will be filtered out", myBaseline.getFile().getName(),
                    myBaseline.getTotalCount());
        }
        new NotificationGroup("Wrote Baseline", NotificationDisplayType.BALLOON, true)
          .createNotification(message, NotificationType.INFORMATION)
          .notify(context.getProject());
      }
    }
  }

  @Override
  public void cleanup() {
    if (myEnabledIssue != null) {
      myEnabledIssue.setEnabledByDefault(false);
      myEnabledIssue = null;
    }
  }
}
