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
import com.android.tools.adtui.common.AdtPrimaryPanel;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.adtui.workbench.WorkBench;
import com.android.tools.editor.ActionToolbarUtil;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.startup.ClearResourceCacheAfterFirstBuild;
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager;
import com.android.tools.idea.uibuilder.editor.NlPreviewForm;
import com.android.tools.idea.uibuilder.surface.GridSurfaceLayoutManager;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.SceneMode;
import com.android.tools.idea.util.SyncUtil;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
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
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.DefaultFocusTraversalPolicy;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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
public class VisualizationForm implements Disposable, ConfigurationSetListener {

  public static final String VISUALIZATION_DESIGN_SURFACE = "VisualizationFormDesignSurface";

  /**
   * horizontal gap between different previews
   */
  @SwingCoordinate private static final int HORIZONTAL_SCREEN_DELTA = 100;

  /**
   * vertical gap between different previews
   */
  @SwingCoordinate private static final int VERTICAL_SCREEN_DELTA = 48;

  private final Project myProject;
  private final NlDesignSurface mySurface;
  private final WorkBench<DesignSurface> myWorkBench;
  private final JPanel myRoot = new JPanel(new BorderLayout());
  private VirtualFile myFile;
  private boolean isActive = false;
  private JComponent myContentPanel;
  private JComponent myActionToolbarPanel;
  private JLabel myFileNameLabel;

  /**
   * The mouse listener in all visible component in visualization tool to make visualization tool can grab the focus.
   * TODO(b/142469546): Remove this once the interaction of visualization tool is defined.
   */
  private final MouseListener myClickToFocusWindowListener;

  @Nullable private Runnable myCancelPreviousAddModelsRequestTask = null;

  @Nullable private List<NlModel> myModels = null;

  /**
   * Contains the editor that is currently being loaded.
   * Once the file is loaded, myPendingEditor will be null.
   */
  private FileEditor myPendingEditor;

  private FileEditor myEditor;

  @NotNull private ConfigurationSet myCurrentConfigurationSet = ConfigurationSet.PIXEL_DEVICES;
  private VisualizationModelsProvider myCurrentModelsProvider = myCurrentConfigurationSet.getModelsProviderCreator().invoke(this);

  /**
   * {@link CompletableFuture} of the next model load. This is kept so the load can be cancelled.
   */
  private AtomicBoolean myCancelPendingModelLoad = new AtomicBoolean(false);

  public VisualizationForm(@NotNull Project project) {
    myProject = project;
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
    updateScreenMode();
    Disposer.register(this, mySurface);
    mySurface.setCentered(true);
    mySurface.setName(VISUALIZATION_DESIGN_SURFACE);

    myClickToFocusWindowListener = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        if (event.getID() == MouseEvent.MOUSE_PRESSED) {
          mySurface.getLayeredPane().requestFocusInWindow();
        }
      }
    };

    // TODO(b/142469546): Remove this once the interaction of visualization tool is defined.
    // The interaction of mySurface is disabled because mySurface is not editable so its InteractionManager is not listening any
    // mouse and keyboard events. Here we add a mouse listener to focus visualization tool when clicking on previews area.
    mySurface.getLayeredPane().addMouseListener(myClickToFocusWindowListener);

    myWorkBench = new WorkBench<>(myProject, "Visualization", null, this);
    myWorkBench.setLoadingText("Loading...");
    myWorkBench.setToolContext(mySurface);

    myRoot.add(createToolbarPanel(), BorderLayout.NORTH);
    myRoot.add(myWorkBench, BorderLayout.CENTER);
    myRoot.setFocusCycleRoot(true);
    myRoot.setFocusTraversalPolicy(new VisualizationTraversalPolicy(mySurface));
  }

  private void updateScreenMode() {
    switch (myCurrentConfigurationSet) {
      case COLOR_BLIND_MODE:
        mySurface.setScreenMode(SceneMode.COLOR_BLIND_MODE, false);
        break;
      default:
        mySurface.setScreenMode(SceneMode.VISUALIZATION, false);
        break;
    }
  }

  @NotNull
  private JComponent createToolbarPanel() {
    myFileNameLabel = new JLabel();
    myActionToolbarPanel = new AdtPrimaryPanel(new BorderLayout());
    myActionToolbarPanel.addMouseListener(myClickToFocusWindowListener);

    JComponent toolbarRootPanel = new AdtPrimaryPanel(new BorderLayout());
    toolbarRootPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, StudioColorsKt.getBorder()),
                                                              BorderFactory.createEmptyBorder(0, 6, 0, 0)));

    toolbarRootPanel.add(myFileNameLabel, BorderLayout.WEST);
    toolbarRootPanel.add(myActionToolbarPanel, BorderLayout.CENTER);
    toolbarRootPanel.addMouseListener(myClickToFocusWindowListener);

    updateActionToolbar();
    return toolbarRootPanel;
  }

  private void updateActionToolbar() {
    myActionToolbarPanel.removeAll();
    DefaultActionGroup group = new DefaultActionGroup(new ConfigurationSetMenuAction(this, myCurrentConfigurationSet));
    if (myFile != null) {
      PsiFile file = PsiManager.getInstance(myProject).findFile(myFile);
      AndroidFacet facet = file != null ? AndroidFacet.getInstance(file) : null;
      if (facet != null) {
        ActionGroup configurationActions = myCurrentModelsProvider.createActions(file, facet);
        group.addAll(configurationActions);
      }
    }
    // Use ActionPlaces.EDITOR_TOOLBAR as place to update the ui when appearance is changed.
    // In IJ's implementation, only the actions in ActionPlaces.EDITOR_TOOLBAR toolbar will be tweaked when ui is changed.
    // See com.intellij.openapi.actionSystem.impl.ActionToolbarImpl.tweakActionComponentUI()
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, group, true);
    actionToolbar.updateActionsImmediately();
    ActionToolbarUtil.makeToolbarNavigable(actionToolbar);
    myActionToolbarPanel.add(actionToolbar.getComponent(), BorderLayout.CENTER);
  }

  private void createContentPanel() {
    myContentPanel = new JPanel(new BorderLayout());
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
      mySurface.zoomToFit();
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
    myWorkBench.loadingStopped("Previews are unavailable until after a successful project sync");
  }

  private void initPreviewFormAfterBuildOnEventDispatchThread() {
    if (Disposer.isDisposed(this)) {
      return;
    }
    if (myContentPanel == null) {
      createContentPanel();
      myWorkBench.init(myContentPanel, mySurface, ImmutableList.of(), false);
      // The toolbar is in the root panel which contains myWorkBench. To traverse to toolbar we need to traverse out from myWorkBench.
      myWorkBench.setFocusCycleRoot(false);
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
        List<NlModel> models = myCurrentModelsProvider.createNlModels(this, file, facet);
        if (models.isEmpty()) {
          myWorkBench.showLoading("No Device Found");
          return null;
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
              return mySurface.addModel(model);
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
    if (myEditor == null) {
      setNoActiveModel();
    }
    else if (file.equals(myFile)) {
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

  @Override
  public void onSelectedConfigurationSetChanged(@NotNull ConfigurationSet newConfigurationSet) {
    if (myCurrentConfigurationSet != newConfigurationSet) {
      myCurrentConfigurationSet = newConfigurationSet;
      myCurrentModelsProvider = newConfigurationSet.getModelsProviderCreator().invoke(this);
      refresh();
    }
  }

  @Override
  public void onCurrentConfigurationSetUpdated() {
    refresh();
  }

  /**
   * Refresh the previews. This recreates the {@link NlModel}s from the current {@link ConfigurationSet}.
   */
  private void refresh() {
    updateScreenMode();
    updateActionToolbar();
    // Dispose old models and create new models with new configuration set.
    initNeleModel();
  }

  private NlAnalyticsManager getAnalyticsManager() {
    return mySurface.getAnalyticsManager();
  }

  @Nullable
  public final FileEditor getEditor() {
    return myEditor;
  }

  private static class VisualizationTraversalPolicy extends DefaultFocusTraversalPolicy {
    @NotNull private DesignSurface mySurface;

    private VisualizationTraversalPolicy(@NotNull DesignSurface surface) {
      mySurface = surface;
    }

    @Override
    public Component getDefaultComponent(Container aContainer) {
      return mySurface.getLayeredPane();
    }
  }
}
