// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android;

import com.android.tools.idea.projectsystem.ProjectSystemSyncUtil;
import com.android.tools.idea.templates.TemplateManager;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetManagerAdapter;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.android.compiler.AndroidAutogeneratorMode;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.compiler.AndroidPrecompileTask;
import org.jetbrains.android.compiler.ModuleSourceAutogenerating;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.compiler.AndroidResourceFilesListener;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Service
public final class AndroidProjectComponent implements Disposable {
  private final Project myProject;

  private static boolean ourDynamicTemplateMenuCreated;

  private AndroidProjectComponent(@NotNull Project project) {
    myProject = project;
  }

  private static AndroidProjectComponent getService(Project project){
    return ServiceManager.getService(project, AndroidProjectComponent.class);
  }

  public static class AndroidProjectStartupActivity implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
      final CompilerManager manager = CompilerManager.getInstance(project);
      manager.addBeforeTask(new AndroidPrecompileTask());

      if (ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
        getService(project).createAndroidSpecificComponents();
      }
      else {
        final MessageBusConnection connection = project.getMessageBus().connect();

        connection.subscribe(FacetManager.FACETS_TOPIC, new FacetManagerAdapter() {
          @Override
          public void facetAdded(@NotNull Facet facet) {
            if (facet instanceof AndroidFacet) {
              getService(project).createAndroidSpecificComponents();
              connection.disconnect();
            }
          }
        });
      }

      getService(project).registerTemplatesAutoRefresh();
    }
  }

  private void registerTemplatesAutoRefresh() {
    myProject.getMessageBus().connect(this).subscribe(ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC, result -> {
      if (result.isSuccessful()) {
        Runnable runnable = () -> {
          if (myProject.isDisposed() || !ProjectFacetManager.getInstance(myProject).hasFacets(AndroidFacet.ID)) return;
          TemplateManager.getInstance().refreshDynamicTemplateMenu(myProject);
        };
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          runnable.run();
        }
        else {
          ApplicationManager.getApplication().executeOnPooledThread(runnable);
        }
      }
    });
  }

  private void createAndroidSpecificComponents() {
    final AndroidResourceFilesListener listener = new AndroidResourceFilesListener(myProject);
    Disposer.register(this, listener);

    createDynamicTemplateMenu();

    // TODO: for external build systems, this alarm is unnecessary and should not be added
    createAlarmForAutogeneration();
  }

  @Override
  public void dispose() {

  }

  private static void createDynamicTemplateMenu() {
    if (ourDynamicTemplateMenuCreated) {
      return;
    }
    ourDynamicTemplateMenuCreated = true;

    new Task.Backgroundable(null, "Loading Dynamic Templates", false, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
      ActionGroup menu;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        menu = TemplateManager.getInstance().getTemplateCreationMenu(null);
      }

      @Override
      public void onFinished() {
        DefaultActionGroup newGroup = (DefaultActionGroup)ActionManager.getInstance().getAction("NewGroup");
        newGroup.addSeparator();
        if (menu != null) {
          newGroup.add(menu, new Constraints(Anchor.AFTER, "NewFromTemplate"));
        }
      }
    }.queue();
  }

  private void createAlarmForAutogeneration() {
    final Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    alarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (!myProject.isOpen()) {
          return; // When project is closing (but not disposed yet), runReadActionInSmartMode throws "Registering post-startup activity"
        }

        DumbService service = DumbService.getInstance(myProject);
        Map<AndroidFacet, Collection<AndroidAutogeneratorMode>> facetsToProcess = service.runReadActionInSmartMode(() -> checkGenerate());
        if (!facetsToProcess.isEmpty()) {
          generate(facetsToProcess);
        }
        if (!alarm.isDisposed()) {
          alarm.addRequest(this, 2000);
        }
      }
    }, 2000);
  }

  private Map<AndroidFacet, Collection<AndroidAutogeneratorMode>> checkGenerate() {
    if (myProject.isDisposed()) return Collections.emptyMap();

    final Map<AndroidFacet, Collection<AndroidAutogeneratorMode>> facetsToProcess = new HashMap<>();

    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet == null) {
        continue;
      }

      if (!ModuleSourceAutogenerating.requiresAutoSourceGeneration(facet)) {
        continue;
      }

      ModuleSourceAutogenerating autogenerator = ModuleSourceAutogenerating.getInstance(facet);
      assert autogenerator != null;

      final Set<AndroidAutogeneratorMode> modes = EnumSet.noneOf(AndroidAutogeneratorMode.class);
      for (AndroidAutogeneratorMode mode : AndroidAutogeneratorMode.values()) {
        if (autogenerator.cleanRegeneratingState(mode) || autogenerator.isGeneratedFileRemoved(mode)) {
          modes.add(mode);
        }
      }

      if (!modes.isEmpty()) {
        facetsToProcess.put(facet, modes);
      }
    }
    return facetsToProcess;
  }

  private void generate(final Map<AndroidFacet, Collection<AndroidAutogeneratorMode>> facetsToProcess) {
    ApplicationManager.getApplication().invokeAndWait(
      () -> AndroidCompileUtil.createGenModulesAndSourceRoots(myProject, facetsToProcess.keySet()));

    for (Map.Entry<AndroidFacet, Collection<AndroidAutogeneratorMode>> entry : facetsToProcess.entrySet()) {
      final AndroidFacet facet = entry.getKey();
      final Collection<AndroidAutogeneratorMode> modes = entry.getValue();

      for (AndroidAutogeneratorMode mode : modes) {
        AndroidCompileUtil.doGenerate(facet, mode);
      }
    }
  }
}
