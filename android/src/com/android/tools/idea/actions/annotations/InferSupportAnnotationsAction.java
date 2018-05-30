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
package com.android.tools.idea.actions.annotations;

import com.android.SdkConstants;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.templates.RepositoryUrlManager;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.COMPILE;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.util.text.StringUtil.pluralize;

/**
 * Analyze support annotations
 */
public class InferSupportAnnotationsAction extends BaseAnalysisAction {
  /**
   * Whether this feature is enabled or not during development
   */
  static final boolean ENABLED = Boolean.valueOf(System.getProperty("studio.infer.annotations"));

  /**
   * Number of times we pass through the project files
   */
  static final int MAX_PASSES = 3;

  @NonNls private static final String INFER_SUPPORT_ANNOTATIONS = "Infer Support Annotations";
  private static final int MAX_ANNOTATIONS_WITHOUT_PREVIEW = 5;

  public InferSupportAnnotationsAction() {
    super("Infer Support Annotations", INFER_SUPPORT_ANNOTATIONS);
    if (!ENABLED) {
      getTemplatePresentation().setVisible(false);
    }
  }

  private static final String ADD_DEPENDENCY = "Add Support Dependency";
  private static final int MIN_SDK_WITH_NULLABLE = 19;

  @Override
  public void update(AnActionEvent event) {
    if (!ENABLED) {
      return;
    }
    super.update(event);
    Project project = event.getProject();
    if (project == null || !GradleProjectInfo.getInstance(project).isBuildWithGradle()) {
      Presentation presentation = event.getPresentation();
      presentation.setEnabled(false);
    }
  }

  @Override
  protected void analyze(@NotNull Project project, @NotNull AnalysisScope scope) {
    if (!GradleProjectInfo.getInstance(project).isBuildWithGradle()) {
      return;
    }
    int[] fileCount = new int[]{0};
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    UsageInfo[] usageInfos = findUsages(project, scope, fileCount[0]);
    if (usageInfos == null) return;

    Map<Module, PsiFile> modules = findModulesFromUsage(usageInfos);

    if (!checkModules(project, scope, modules)) {
      return;
    }

    if (usageInfos.length < MAX_ANNOTATIONS_WITHOUT_PREVIEW) {
      ApplicationManager.getApplication().invokeLater(applyRunnable(project, () -> usageInfos));
    }
    else {
      showUsageView(project, usageInfos, scope);
    }
  }

  private static Map<Module, PsiFile> findModulesFromUsage(UsageInfo[] infos) {
    // We need 1 file from each module that requires changes (the file may be overwritten below):
    Map<Module, PsiFile> modules = new HashMap<>();

    for (UsageInfo info : infos) {
      PsiElement element = info.getElement();
      assert element != null;
      Module module = ModuleUtilCore.findModuleForPsiElement(element);
      if (module == null) {
        continue;
      }
      PsiFile file = element.getContainingFile();
      modules.put(module, file);
    }
    return modules;
  }

  private static UsageInfo[] findUsages(@NotNull Project project,
                                        @NotNull AnalysisScope scope,
                                        int fileCount) {
    InferSupportAnnotations inferrer = new InferSupportAnnotations(false, project);
    PsiManager psiManager = PsiManager.getInstance(project);
    Runnable searchForUsages = () -> scope.accept(new PsiElementVisitor() {
      int myFileCount = 0;

      @Override
      public void visitFile(PsiFile file) {
        myFileCount++;
        VirtualFile virtualFile = file.getVirtualFile();
        FileViewProvider viewProvider = psiManager.findViewProvider(virtualFile);
        Document document = viewProvider == null ? null : viewProvider.getDocument();
        if (document == null || virtualFile.getFileType().isBinary()) return; //do not inspect binary files
        ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        if (progressIndicator != null) {
          progressIndicator.setText2(ProjectUtil.calcRelativeToProjectPath(virtualFile, project));
          progressIndicator.setFraction(((double)myFileCount) / (MAX_PASSES * fileCount));
        }
        if (file instanceof PsiJavaFile) {
          inferrer.collect(file);
        }
      }
    });

    /*
      Collect these files and visit repeatedly. Consider this
      scenario, where I visit files A, B, C in alphabetical order.
      Let's say a method in A unconditionally calls a method in B
      calls a method in C. In file C I discover that the method
      requires permission P. At this point it's too late for me to
      therefore conclude that the method in B also requires it. If I
      make a whole separate pass again, I could now add that
      constraint. But only after that second pass can I infer that
      the method in A also requires it. In short, I need to keep
      passing through all files until I make no more progress. It
      would be much more efficient to handle this with a global call
      graph such that as soon as I make an inference I can flow it
      backwards.
     */
    Runnable multipass = () -> {
      for (int i = 0; i < MAX_PASSES; i++) {
        searchForUsages.run();
      }
    };

    if (ApplicationManager.getApplication().isDispatchThread()) {
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(multipass, INFER_SUPPORT_ANNOTATIONS, true, project)) {
        return null;
      }
    }
    else {
      multipass.run();
    }

    List<UsageInfo> usages = new ArrayList<>();
    inferrer.collect(usages, scope);
    return usages.toArray(UsageInfo.EMPTY_ARRAY);
  }

  // For Android we need to check SDK version and possibly update the gradle project file
  protected boolean checkModules(@NotNull Project project,
                                 @NotNull AnalysisScope scope,
                                 @NotNull Map<Module, PsiFile> modules) {
    Set<Module> modulesWithoutAnnotations = new HashSet<>();
    Set<Module> modulesWithLowVersion = new HashSet<>();
    for (Module module : modules.keySet()) {
      AndroidModuleInfo info = AndroidModuleInfo.getInstance(module);
      if (info != null && info.getBuildSdkVersion() != null && info.getBuildSdkVersion().getFeatureLevel() < MIN_SDK_WITH_NULLABLE) {
        modulesWithLowVersion.add(module);
      }
      GradleBuildModel buildModel = GradleBuildModel.get(module);
      if (buildModel == null) {
        Logger.getInstance(InferSupportAnnotationsAction.class)
          .warn("Unable to find Gradle build model for module " + module.getModuleFilePath());
        continue;
      }
      boolean dependencyFound = false;
      DependenciesModel dependenciesModel = buildModel.dependencies();
      if (dependenciesModel != null) {
        String configurationName =
          GradleUtil.mapConfigurationName(COMPILE, GradleUtil.getAndroidGradleModelVersionInUse(module), false);
        for (ArtifactDependencyModel dependency : dependenciesModel.artifacts(configurationName)) {
          String notation = dependency.compactNotation();
          if (notation.startsWith(SdkConstants.APPCOMPAT_LIB_ARTIFACT) ||
              notation.startsWith(SdkConstants.SUPPORT_LIB_ARTIFACT) ||
              notation.startsWith(SdkConstants.ANNOTATIONS_LIB_ARTIFACT)) {
            dependencyFound = true;
            break;
          }
        }
      }
      if (!dependencyFound) {
        modulesWithoutAnnotations.add(module);
      }
    }

    if (!modulesWithLowVersion.isEmpty()) {
      Messages.showErrorDialog(
        project,
        String.format("Infer Support Annotations requires the project sdk level be set to %1$d or greater.", MIN_SDK_WITH_NULLABLE),
        "Infer Support Annotations");
      return false;
    }
    if (modulesWithoutAnnotations.isEmpty()) {
      return true;
    }
    String moduleNames = StringUtil.join(modulesWithoutAnnotations, Module::getName, ", ");
    int count = modulesWithoutAnnotations.size();
    String message = String.format("The %1$s %2$s %3$sn't refer to the existing '%4$s' library with Android nullity annotations. \n\n" +
                                   "Would you like to add the %5$s now?",
                                   pluralize("module", count),
                                   moduleNames,
                                   count > 1 ? "do" : "does",
                                   GoogleMavenArtifactId.SUPPORT_ANNOTATIONS.getMavenArtifactId(),
                                   pluralize("dependency", count));
    if (Messages.showOkCancelDialog(project, message, "Infer Nullity Annotations", Messages.getErrorIcon()) == Messages.OK) {
      LocalHistoryAction action = LocalHistory.getInstance().startAction(ADD_DEPENDENCY);
      try {
        new WriteCommandAction(project, ADD_DEPENDENCY) {
          @Override
          protected void run(@NotNull Result result) throws Throwable {
            RepositoryUrlManager manager = RepositoryUrlManager.get();
            GoogleMavenArtifactId annotation = StudioFlags.NELE_USE_ANDROIDX_DEFAULT.get() ?
                                               GoogleMavenArtifactId.ANDROIDX_SUPPORT_ANNOTATIONS :
                                               GoogleMavenArtifactId.SUPPORT_ANNOTATIONS;
            String annotationsLibraryCoordinate = manager.getArtifactStringCoordinate(annotation, true);
            for (Module module : modulesWithoutAnnotations) {
              addDependency(module, annotationsLibraryCoordinate);
            }

            syncAndRestartAnalysis(project, scope);
          }
        }.execute();
      }
      finally {
        action.finish();
      }
    }
    return false;
  }

  private void syncAndRestartAnalysis(@NotNull Project project, @NotNull AnalysisScope scope) {
    assert ApplicationManager.getApplication().isDispatchThread();

    ListenableFuture<ProjectSystemSyncManager.SyncResult> syncResult = ProjectSystemUtil.getProjectSystem(project)
      .getSyncManager().syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED, false);

    Futures.addCallback(syncResult, new FutureCallback<ProjectSystemSyncManager.SyncResult>() {
      @Override
      public void onSuccess(@Nullable ProjectSystemSyncManager.SyncResult syncResult) {
        if (syncResult != null && syncResult.isSuccessful()) {
          restartAnalysis(project, scope);
        }
      }

      @Override
      public void onFailure(@Nullable Throwable t) {
        throw new RuntimeException(t);
      }
    });
  }

  private static Runnable applyRunnable(Project project, Computable<UsageInfo[]> computable) {
    return () -> {
      LocalHistoryAction action = LocalHistory.getInstance().startAction(INFER_SUPPORT_ANNOTATIONS);
      try {
        new WriteCommandAction(project, INFER_SUPPORT_ANNOTATIONS) {
          @Override
          protected void run(@NotNull Result result) throws Throwable {
            UsageInfo[] infos = computable.compute();
            if (infos.length > 0) {

              Set<PsiElement> elements = new LinkedHashSet<>();
              for (UsageInfo info : infos) {
                PsiElement element = info.getElement();
                if (element != null) {
                  PsiFile containingFile = element.getContainingFile();
                  // Skip results in .class files; these are typically from extracted AAR files
                  VirtualFile virtualFile = containingFile.getVirtualFile();
                  if (virtualFile.getFileType().isBinary()) {
                    continue;
                  }

                  ContainerUtil.addIfNotNull(elements, containingFile);
                }
              }
              if (!FileModificationService.getInstance().preparePsiElementsForWrite(elements)) return;

              SequentialModalProgressTask progressTask = new SequentialModalProgressTask(project, INFER_SUPPORT_ANNOTATIONS, false);
              progressTask.setMinIterationTime(200);
              progressTask.setTask(new AnnotateTask(project, progressTask, infos));
              ProgressManager.getInstance().run(progressTask);
            }
            else {
              InferSupportAnnotations.nothingFoundMessage(project);
            }
          }
        }.execute();
      }
      finally {
        action.finish();
      }
    };
  }

  private void restartAnalysis(Project project, AnalysisScope scope) {
    ApplicationManager.getApplication().invokeLater(() -> analyze(project, scope));
  }

  private static void showUsageView(@NotNull Project project, UsageInfo[] usageInfos, @NotNull AnalysisScope scope) {
    UsageTarget[] targets = UsageTarget.EMPTY_ARRAY;
    Ref<Usage[]> convertUsagesRef = new Ref<>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ApplicationManager.getApplication().runReadAction(() -> convertUsagesRef.set(UsageInfo2UsageAdapter.convert(usageInfos))),
      "Preprocess Usages", true, project)) {
      return;
    }

    if (convertUsagesRef.isNull()) return;
    Usage[] usages = convertUsagesRef.get();

    UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setTabText("Infer Nullity Preview");
    presentation.setShowReadOnlyStatusAsRed(true);
    presentation.setShowCancelButton(true);
    presentation.setUsagesString(RefactoringBundle.message("usageView.usagesText"));

    UsageView usageView =
      UsageViewManager.getInstance(project).showUsages(targets, usages, presentation, rerunFactory(project, scope));

    Runnable refactoringRunnable = applyRunnable(project, () -> {
      Set<UsageInfo> infos = UsageViewUtil.getNotExcludedUsageInfos(usageView);
      return infos.toArray(UsageInfo.EMPTY_ARRAY);
    });

    String canNotMakeString =
      "Cannot perform operation.\nThere were changes in code after usages have been found.\nPlease perform operation search again.";

    usageView.addPerformOperationAction(refactoringRunnable, INFER_SUPPORT_ANNOTATIONS, canNotMakeString, INFER_SUPPORT_ANNOTATIONS, false);
  }

  @NotNull
  private static Factory<UsageSearcher> rerunFactory(@NotNull Project project, @NotNull AnalysisScope scope) {
    return () -> new UsageInfoSearcherAdapter() {
      @NotNull
      @Override
      protected UsageInfo[] findUsages() {
        return ObjectUtils.notNull(InferSupportAnnotationsAction.findUsages(project, scope, scope.getFileCount()),UsageInfo.EMPTY_ARRAY);
      }

      @Override
      public void generate(@NotNull Processor<Usage> processor) {
        processUsages(processor, project);
      }
    };
  }

  private static void addDependency(@NotNull Module module, @Nullable String libraryCoordinate) {
    if (isNotEmpty(libraryCoordinate)) {
      ModuleRootModificationUtil.updateModel(module, model -> {
        GradleBuildModel buildModel = GradleBuildModel.get(module);
        if (buildModel != null) {
          String name = GradleUtil.mapConfigurationName(COMPILE, GradleUtil.getAndroidGradleModelVersionInUse(module), false);
          buildModel.dependencies().addArtifact(name, libraryCoordinate);
          buildModel.applyChanges();
        }
      });
    }
  }

  /* Android nullable annotations do not support annotations on local variables. */
  @Override
  protected JComponent getAdditionalActionSettings(Project project, BaseAnalysisActionDialog dialog) {
    if (!GradleProjectInfo.getInstance(project).isBuildWithGradle()) {
      return super.getAdditionalActionSettings(project, dialog);
    }
    return null;
  }

  private static class AnnotateTask implements SequentialTask {
    private final Project myProject;
    private UsageInfo[] myInfos;
    private final SequentialModalProgressTask myTask;
    private int myCount = 0;
    private final int myTotal;

    public AnnotateTask(Project project, SequentialModalProgressTask progressTask, UsageInfo[] infos) {
      myProject = project;
      myInfos = infos;
      myTask = progressTask;
      myTotal = infos.length;
    }

    @Override
    public void prepare() {
    }

    @Override
    public boolean isDone() {
      return myCount > myTotal - 1;
    }

    @Override
    public boolean iteration() {
      ProgressIndicator indicator = myTask.getIndicator();
      if (indicator != null) {
        indicator.setFraction(((double)myCount) / myTotal);
      }

      InferSupportAnnotations.apply(myProject, myInfos[myCount++]);

      boolean done = isDone();

      if (isDone()) {
        try {
          showReport();
        }
        catch (Throwable ignore) {
        }
      }
      return done;
    }

    @Override
    public void stop() {
    }

    public void showReport() {
      if (InferSupportAnnotations.CREATE_INFERENCE_REPORT) {
        String report = InferSupportAnnotations.generateReport(myInfos);
        String fileName = "Annotation Inference Report";
        ScratchFileService.Option option = ScratchFileService.Option.create_new_always;
        VirtualFile f = ScratchRootType.getInstance().createScratchFile(myProject, fileName, StdLanguages.TEXT, report, option);
        if (f != null) {
          FileEditorManager.getInstance(myProject).openFile(f, true);
        }
      }
    }
  }
}
