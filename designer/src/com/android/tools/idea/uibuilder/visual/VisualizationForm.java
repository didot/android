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
package com.android.tools.idea.uibuilder.visual;

import static com.android.tools.idea.uibuilder.graphics.NlConstants.DEFAULT_SCREEN_OFFSET_X;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.DEFAULT_SCREEN_OFFSET_Y;

import com.android.annotations.concurrency.UiThread;
import com.android.resources.ResourceFolderType;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.devices.Device;
import com.android.tools.adtui.common.AdtPrimaryPanel;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.adtui.workbench.WorkBench;
import com.android.tools.idea.common.error.IssuePanelSplitter;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.startup.ClearResourceCacheAfterFirstBuild;
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager;
import com.android.tools.idea.uibuilder.editor.NlPreviewForm;
import com.android.tools.idea.uibuilder.surface.GridSurfaceLayoutManager;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.SceneMode;
import com.android.tools.idea.util.SyncUtil;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.EmptyIcon;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Form of layout visualization which offers multiple previews for different devices in the same time. It provides a
 * convenient way to user to preview the layout in different devices.
 * <p>
 * This class is inspired by {@link NlPreviewForm}.<br>
 * Most of the codes are copied from {@link NlPreviewForm} instead of sharing, because {@link NlPreviewForm} is being
 * removed after we enable split editor.
 */
public class VisualizationForm implements Disposable {

  public static final String VISUALIZATION_DESIGN_SURFACE = "VisualizationFormDesignSurface";

  /**
   * horizontal gap between different previews
   */
  @SwingCoordinate private static final int HORIZONTAL_SCREEN_DELTA = 100;

  /**
   * vertical gap between different previews
   */
  @SwingCoordinate private static final int VERTICAL_SCREEN_DELTA = 60;

  /**
   * We predefined some pixel devices for now.
   */
  private static final List<String> DEVICES_TO_DISPLAY =
    ImmutableList.of("Pixel 3", "Pixel 3 XL", "Pixel 3a", "Pixel 3a XL", "Pixel 2", "Pixel 2 XL", "Pixel", "Pixel XL", "Pixel C");

  private final VisualizationManager myManager;
  private final Project myProject;
  private final NlDesignSurface mySurface;
  private final WorkBench<DesignSurface> myWorkBench;
  private final JPanel myRoot = new JPanel(new BorderLayout());
  private VirtualFile myFile;
  private boolean isActive = false;
  private JComponent myContentPanel;
  private JLabel myFileNameLabel;

  @Nullable private Runnable myCancelPreviousAddModelsRequestTask = null;

  @Nullable private List<NlModel> myModels = null;

  /**
   * Contains the editor that is currently being loaded.
   * Once the file is loaded, myPendingEditor will be null.
   */
  private FileEditor myPendingEditor;

  private FileEditor myEditor;
  /**
   * {@link CompletableFuture} of the next model load. This is kept so the load can be cancelled.
   */
  private AtomicBoolean myCancelPendingModelLoad = new AtomicBoolean(false);

  public VisualizationForm(@NotNull VisualizationManager manager) {
    myManager = manager;
    myProject = myManager.getProject();
    mySurface = NlDesignSurface.builder(myProject, myProject)
      .showModelNames()
      .setIsPreview(false)
      .setEditable(false)
      .setActionManagerProvider((surface) -> new VisualizationActionManager((NlDesignSurface) surface))
      .setLayoutManager(new GridSurfaceLayoutManager(DEFAULT_SCREEN_OFFSET_X,
                                                     DEFAULT_SCREEN_OFFSET_Y,
                                                     HORIZONTAL_SCREEN_DELTA,
                                                     VERTICAL_SCREEN_DELTA))
      .build();
    mySurface.setScreenMode(SceneMode.VISUALIZATION, false);
    Disposer.register(this, mySurface);
    mySurface.setCentered(true);
    mySurface.setName(VISUALIZATION_DESIGN_SURFACE);

    myWorkBench = new WorkBench<>(myProject, "Visualization", null, this);
    myWorkBench.setLoadingText(IdeBundle.message("common.text.loading"));
    myWorkBench.setToolContext(mySurface);
    myRoot.add(new IssuePanelSplitter(mySurface, myWorkBench));
  }

  private void createContentPanel() {
    myContentPanel = new JPanel(new BorderLayout());
    myFileNameLabel = new JLabel();
    // Create an action group and fill a dummy action for now. This makes the height of toolbar same as layout editor's.
    // TODO: Remove the dummy action when another action is added.
    ActionGroup group = new DefaultActionGroup(new AnAction(EmptyIcon.create(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width,
                                                                             ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height)) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(false);
        e.getPresentation().setVisible(true);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
      }
    });
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("VisualizationBar", group, true);

    JComponent fileNamePanel = new AdtPrimaryPanel(new BorderLayout());
    fileNamePanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, StudioColorsKt.getBorder()),
                                                             BorderFactory.createEmptyBorder(0, 6, 0, 0)));
    fileNamePanel.add(myFileNameLabel, BorderLayout.WEST);
    fileNamePanel.add(toolbar.getComponent(), BorderLayout.CENTER);

    myContentPanel.add(fileNamePanel, BorderLayout.NORTH);
    myContentPanel.add(mySurface, BorderLayout.CENTER);
  }

  private void setEditor(@Nullable FileEditor editor) {
    if (editor != myEditor) {
      myEditor = editor;
      mySurface.setFileEditorDelegate(editor);
    }
  }

  @SuppressWarnings("unused") // Used by Kotlin plugin
  @Nullable
  public JComponent getToolbarComponent() {
     return null;
  }

  @NotNull
  public JComponent getComponent() {
    return myRoot;
  }

  @Override
  public void dispose() {
    deactivate();
    if (myModels != null) {
      removeAndDisposeModels(myModels);
      myModels = null;
    }
  }

  private void removeAndDisposeModels(@NotNull List<NlModel> models) {
    for (NlModel model : models) {
      model.deactivate(this);
      mySurface.removeModel(model);
      Disposer.dispose(model);
    }
  }

  /**
   * Specifies the next editor the preview should be shown for.
   * The update of the preview may be delayed.
   *
   * @return true on success. False if the preview update is not possible (e.g. the file for the editor cannot be found).
   */
  public boolean setNextEditor(@NotNull FileEditor editor) {
    if (ResourceHelper.getFolderType(editor.getFile()) != ResourceFolderType.LAYOUT) {
      return false;
    }
    myPendingEditor = editor;
    myFile = editor.getFile();

    myCancelPendingModelLoad.set(true);

    if (isActive) {
      initPreviewForm();
    }

    return true;
  }

  private void initPreviewForm() {
    if (myContentPanel == null) {
      // First time: Make sure we have compiled the project at least once...
      ClearResourceCacheAfterFirstBuild.getInstance(myProject)
        .runWhenResourceCacheClean(this::initPreviewFormAfterInitialBuild, this::buildError);
    }
    else {
      // Subsequent times: Setup a Nele model in preparation for creating a preview image
      initNeleModel();
    }
  }

  private void initPreviewFormAfterInitialBuild() {
    myWorkBench.setLoadingText("Waiting for build to finish...");
    SyncUtil.runWhenSmartAndSyncedOnEdt(myProject, this, result -> {
      if (result.isSuccessful()) {
        initPreviewFormAfterBuildOnEventDispatchThread();
      }
      else {
        buildError();
        SyncUtil.listenUntilNextSync(myProject, this, ignore -> initPreviewFormAfterBuildOnEventDispatchThread());
      }
    });
  }

  // Build was either cancelled or there was an error
  private void buildError() {
    myWorkBench.loadingStopped("Preview is unavailable until after a successful project sync");
  }

  private void initPreviewFormAfterBuildOnEventDispatchThread() {
    if (Disposer.isDisposed(this)) {
      return;
    }
    if (myContentPanel == null) {
      createContentPanel();
      myWorkBench.init(myContentPanel, mySurface, ImmutableList.of());
    }
    initNeleModel();
  }

  private void initNeleModel() {
    myWorkBench.showLoading("Rendering Previews...");
    DumbService.getInstance(myProject).smartInvokeLater(() -> initNeleModelWhenSmart());
  }

  @UiThread
  private void initNeleModelWhenSmart() {
    setNoActiveModel();

    if (myCancelPreviousAddModelsRequestTask != null) {
      myCancelPreviousAddModelsRequestTask.run();
      myCancelPreviousAddModelsRequestTask = null;
    }

    if (myFile == null) {
      return;
    }
    PsiFile file = PsiManager.getInstance(myProject).findFile(myFile);
    AndroidFacet facet = file != null ? AndroidFacet.getInstance(file) : null;
    if (facet == null) {
      return;
    }

    myFileNameLabel.setText(file.getName());

    // isRequestCancelled allows us to cancel the ongoing computation if it is not needed anymore. There is no need to hold
    // to the Future since Future.cancel does not really interrupt the work.
    AtomicBoolean isRequestCancelled = new AtomicBoolean(false);
    myCancelPendingModelLoad = isRequestCancelled;
    // Asynchronously load the model and refresh the preview once it's ready
    CompletableFuture
      .supplyAsync(() -> {
        // Hide the content while adding the models.
        myWorkBench.hideContent();
        ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(facet);
        List<Device> devices = configurationManager.getDevices();
        Configuration defaultConfig = configurationManager.getConfiguration(file.getVirtualFile());

        List<Device> deviceList = new ArrayList<>();
        for (String name : DEVICES_TO_DISPLAY) {
          devices.stream().filter(device -> name.equals(device.getDisplayName())).findFirst().ifPresent(deviceList::add);
        }

        if (deviceList.isEmpty()) {
          myWorkBench.showLoading("No Device Found");
          return null;
        }

        List<NlModel> models = new ArrayList<>();
        VirtualFile virtualFile = file.getVirtualFile();
        Consumer<NlComponent> registrar = mySurface.getComponentRegistrar();

        for (Device d : deviceList) {
          Configuration config = defaultConfig.clone();
          config.setDevice(d, false);
          String label = d.getDisplayName();
          Dimension size = d.getScreenSize(ScreenOrientation.PORTRAIT);
          if (size != null) {
            label = label + " (" + size.width + " x " + size.height + ")";
          }
          models.add(NlModel.create(this, label, facet, virtualFile, config, registrar));
        }
        return models;
      }, AppExecutorUtil.getAppExecutorService()).thenAcceptAsync(models -> {
        if (models == null || isRequestCancelled.get()) {
          return;
        }

        if (myCancelPreviousAddModelsRequestTask != null) {
          myCancelPreviousAddModelsRequestTask.run();
        }

        AtomicBoolean isAddingModelCanceled = new AtomicBoolean(false);
        // We want to add model sequentially so we can interrupt them if needed.
        // When adding a model the render request is triggered. Stop adding remaining models avoids unnecessary render requests.
        CompletableFuture<Void> addModelFuture = CompletableFuture.completedFuture(null);
        for (NlModel model : models) {
          addModelFuture = addModelFuture.thenCompose(it -> {
            if (isAddingModelCanceled.get()) {
              return CompletableFuture.completedFuture(null);
            }
            else {
              return mySurface.addModel(model, false);
            }
          });
        }

        myCancelPreviousAddModelsRequestTask = () -> isAddingModelCanceled.set(true);

        addModelFuture.thenRunAsync(() -> {
          if (!isRequestCancelled.get() && !facet.isDisposed() && !isAddingModelCanceled.get()) {
            activeModels(models);
            mySurface.setScale(0.25 / mySurface.getScreenScalingFactor());
            myWorkBench.showContent();
          }
          else {
            removeAndDisposeModels(models);
          }
        }, EdtExecutorService.getInstance());
      }, EdtExecutorService.getInstance());
  }

  // A file editor was closed. If our editor no longer exists, cleanup our state.
  public void fileClosed(@NotNull FileEditorManager editorManager, @NotNull VirtualFile file) {
    if (myEditor != null && file.equals(myFile)) {
      if (ArrayUtil.find(editorManager.getAllEditors(file), myEditor) < 0) {
        setNoActiveModel();
      }
    }
    if (myPendingEditor != null && file.equals(myPendingEditor.getFile())) {
      if (ArrayUtil.find(editorManager.getAllEditors(file), myPendingEditor) < 0) {
        myPendingEditor = null;
      }
    }
  }

  private void setNoActiveModel() {
    myCancelPendingModelLoad.set(true);
    setEditor(null);

    myWorkBench.setFileEditor(null);

    if (myModels != null) {
      removeAndDisposeModels(myModels);
      myModels = null;
    }
  }

  private void activeModels(@NotNull List<NlModel> models) {
    myCancelPendingModelLoad.set(true);
    if (models.isEmpty()) {
      setEditor(null);
      myWorkBench.setFileEditor(null);
    }
    else {
      myFile = models.get(0).getVirtualFile();
      mySurface.zoomToFit();
      setEditor(myPendingEditor);
      myPendingEditor = null;

      for (NlModel model : models) {
        model.activate(this);
      }
      myWorkBench.setFileEditor(myEditor);
    }
    myModels = models;
  }

  @NotNull
  public NlDesignSurface getSurface() {
    return mySurface;
  }

  /**
   * Re-enables updates for this preview form. See {@link #deactivate()}
   */
  public void activate() {
    if (isActive) {
      return;
    }

    isActive = true;
    initPreviewForm();
    mySurface.activate();
    getAnalyticsManager().trackVisualizationToolWindow(true);
  }

  /**
   * Disables the updates for this preview form. Any changes to resources or the layout won't update
   * this preview until {@link #activate()} is called.
   */
  public void deactivate() {
    if (!isActive) {
      return;
    }

    myCancelPendingModelLoad.set(true);
    mySurface.deactivate();
    isActive = false;
    if (myContentPanel != null) {
      setNoActiveModel();
    }
    getAnalyticsManager().trackVisualizationToolWindow(false);
  }

  private NlAnalyticsManager getAnalyticsManager() {
    return mySurface.getAnalyticsManager();
  }

  @Nullable
  public final FileEditor getEditor() {
    return myEditor;
  }
}
