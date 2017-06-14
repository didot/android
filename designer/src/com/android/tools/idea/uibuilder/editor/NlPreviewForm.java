/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.editor;

import com.android.tools.adtui.workbench.AutoHide;
import com.android.tools.adtui.workbench.Side;
import com.android.tools.adtui.workbench.Split;
import com.android.tools.adtui.workbench.WorkBench;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.startup.DelayedInitialization;
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.SelectionModel;
import com.android.tools.idea.uibuilder.palette.NlPaletteDefinition;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public class NlPreviewForm implements Disposable, CaretListener {

  public static final String PREVIEW_DESIGN_SURFACE = "NlPreviewFormDesignSurface";

  private final NlPreviewManager myManager;
  private final Project myProject;
  private final NlDesignSurface mySurface;
  private final WorkBench<DesignSurface> myWorkBench;
  private final MergingUpdateQueue myRenderingQueue =
    new MergingUpdateQueue("android.layout.preview.caret", 250/*ms*/, true, null, this, null, Alarm.ThreadToUse.SWING_THREAD);
  private boolean myUseInteractiveSelector = true;
  private boolean myIgnoreListener;
  private RenderResult myRenderResult;
  private XmlFile myFile;
  private boolean isActive = true;
  private ActionsToolbar myActionsToolbar;
  private JComponent myContentPanel;
  private final AnimationToolbar myAnimationToolbar;

  private NlModel myModel;

  /**
   * Contains the file that is currently being loaded (it might take a while to get a preview rendered).
   * Once the file is loaded, myPendingFile will be null.
   */
  private Pending myPendingFile;
  private TextEditor myEditor;
  private CaretModel myCaretModel;
  private NlDesignSurface.ScreenMode myScreenMode;

  public NlPreviewForm(NlPreviewManager manager) {
    myManager = manager;
    myProject = myManager.getProject();
    mySurface = new NlDesignSurface(myProject, true, this);
    mySurface.setCentered(true);
    mySurface.setScreenMode(NlDesignSurface.ScreenMode.SCREEN_ONLY, false);
    mySurface.setName(PREVIEW_DESIGN_SURFACE);

    myRenderingQueue.setRestartTimerOnAdd(true);

    if (StudioFlags.NELE_ANIMATIONS_PREVIEW.get()) {
      myAnimationToolbar = new AnimationToolbar(this, (timeMs) -> {
        ScreenView screenView = mySurface.getCurrentSceneView();
        NlModel model = screenView != null ? screenView.getModel() : null;
        if (model != null) {
          screenView.getSceneManager().setElapsedFrameTimeMs(timeMs);
          screenView.getSceneManager().requestRender();
        }
      }, 16);
    }
    else {
      myAnimationToolbar = null;
    }

    myWorkBench = new WorkBench<>(myProject, "Preview", null);
    myWorkBench.setLoadingText("Waiting for build to finish...");

    Disposer.register(this, myWorkBench);
  }

  private void createContentPanel() {
    myContentPanel = new JPanel(new BorderLayout());
    myContentPanel.add(mySurface, BorderLayout.CENTER);

    if (myAnimationToolbar != null) {
      myContentPanel.add(myAnimationToolbar, BorderLayout.SOUTH);
    }
  }

  private void setEditor(@Nullable TextEditor editor) {
    if (editor != myEditor) {
      myEditor = editor;
      mySurface.setFileEditorDelegate(editor);

      if (myCaretModel != null) {
        myCaretModel.removeCaretListener(this);
        myCaretModel = null;
      }

      if (editor != null) {
        myCaretModel = myEditor.getEditor().getCaretModel();
        myCaretModel.addCaretListener(this);
      }
    }
  }

  private void updateCaret() {
    if (myCaretModel != null && !myIgnoreListener && myUseInteractiveSelector) {
      ScreenView screenView = mySurface.getCurrentSceneView();
      if (screenView != null) {
        int offset = myCaretModel.getOffset();
        if (offset != -1) {
          List<NlComponent> views = screenView.getModel().findByOffset(offset);
          if (views == null || views.isEmpty()) {
            views = screenView.getModel().getComponents();
          }
          try {
            myIgnoreListener = true;
            SelectionModel selectionModel = screenView.getSelectionModel();
            selectionModel.setSelection(views);
            myRenderingQueue.queue(new Update("Preview update") {
              @Override
              public void run() {
                mySurface.repaint();
              }

              @Override
              public boolean canEat(Update update) {
                return true;
              }
            });
          }
          finally {
            myIgnoreListener = false;
          }
        }
      }
    }
  }

  @SuppressWarnings("unused") // Used by Kotlin plugin
  @Nullable
  public JComponent getToolbarComponent() {
    return myActionsToolbar.getToolbarComponent();
  }

  @Nullable
  public XmlFile getFile() {
    return myFile;
  }

  @NotNull
  public JComponent getComponent() {
    return myWorkBench;
  }

  @Override
  public void dispose() {
    deactivate();
    disposeActionsToolbar();

    if (myModel != null) {
      Disposer.dispose(myModel);
      myModel = null;
    }
  }

  public void setUseInteractiveSelector(boolean useInteractiveSelector) {
    this.myUseInteractiveSelector = useInteractiveSelector;
  }

  private class Pending implements ModelListener, Runnable {
    public final XmlFile file;
    public final NlModel model;
    public boolean valid = true;

    public Pending(XmlFile file, NlModel model) {
      this.file = file;
      this.model = model;
      model.addListener(this);
      ScreenView view = mySurface.getCurrentSceneView();
      if (view != null) {
        view.getSceneManager().requestRender();
      }
    }

    @Override
    public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
      // do nothing
    }

    @Override
    public void modelRendered(@NotNull NlModel model) {
      model.removeListener(this);
      if (valid) {
        valid = false;
        ApplicationManager.getApplication().invokeLater(this, model.getProject().getDisposed());
      }
    }

    public void invalidate() {
      valid = false;
    }

    @Override
    public void run() {
      // This method applies the given pending update to the UI thread; this must be done from a read thread
      ApplicationManager.getApplication().assertIsDispatchThread();
      setActiveModel(model);
    }
  }

  public void setFile(@Nullable PsiFile file) {
    if (myAnimationToolbar != null) {
      myAnimationToolbar.stop();
    }

    XmlFile xmlFile = myManager.getBoundXmlFile(file);
    if (xmlFile == myFile) {
      return;
    }
    myFile = xmlFile;

    if (myPendingFile != null) {
      myPendingFile.invalidate();
      // Set the model to null so the progressbar is displayed
      // TODO: find another way to decide that the progress indicator should be shown, so that the design surface model can be non-null
      // mySurface.setModel(null);
    }

    if (myContentPanel == null) {  // First time: Make sure we have compiled the project at least once...
      DelayedInitialization.getInstance(myProject).runAfterBuild(this::initPreviewForm, this::buildError);
    }
    else {
      initNeleModel();
    }
  }

  private void initPreviewForm() {
    UIUtil.invokeLaterIfNeeded(this::initPreviewFormOnEventDispatchThread);
  }

  // Build was either cancelled or there was an error
  private void buildError() {
    myWorkBench.loadingStopped("Preview is unavailable until a successful build");
  }

  private void initPreviewFormOnEventDispatchThread() {
    if (Disposer.isDisposed(this)) {
      return;
    }
    if (myContentPanel == null) {
      createContentPanel();
      myWorkBench.init(myContentPanel, mySurface,
                       Collections.singletonList(new NlPaletteDefinition(myProject, Side.LEFT, Split.TOP, AutoHide.AUTO_HIDE)));
    }
    initNeleModel();
  }

  private void initNeleModel() {
    XmlFile xmlFile = myFile;
    AndroidFacet facet = xmlFile != null ? AndroidFacet.getInstance(xmlFile) : null;
    if (!isActive || facet == null || xmlFile.getVirtualFile() == null) {
      myPendingFile = null;
      setActiveModel(null);
    }
    else {
      if (myModel != null) {
        Disposer.dispose(myModel);
      }
      myModel = NlModel.create(mySurface, null, facet, xmlFile);

      mySurface.setModel(myModel);
      myPendingFile = new Pending(xmlFile, myModel);
    }
  }

  public void setActiveModel(@Nullable NlModel model) {
    myPendingFile = null;
    ScreenView currentScreenView = mySurface.getCurrentSceneView();
    if (currentScreenView != null) {
      NlModel oldModel = currentScreenView.getModel();
      if (model != oldModel) {
        oldModel.deactivate();
        Disposer.dispose(oldModel);
      }
    }

    if (model == null) {
      setEditor(null);
      disposeActionsToolbar();

      myWorkBench.setToolContext(null);
    }
    else {
      myFile = model.getFile();
      if (!mySurface.isCanvasResizing()) {
        // If we are resizing, keep the zoom level constant
        // only if the zoom was previously set to FIT
        mySurface.zoomToFit();
      }
      else {
        mySurface.updateScrolledAreaSize();
      }
      setEditor(myManager.getActiveLayoutXmlEditor(myFile));
      model.activate();
      myWorkBench.setToolContext(mySurface);
      myWorkBench.setFileEditor(myEditor);

      disposeActionsToolbar();

      myActionsToolbar = new ActionsToolbar(mySurface);
      myActionsToolbar.setModel(model);

      myContentPanel.add(myActionsToolbar.getToolbarComponent(), BorderLayout.NORTH);

      if (!model.getType().isSupportedByDesigner()) {
        myScreenMode = mySurface.getScreenMode();
        mySurface.setScreenMode(NlDesignSurface.ScreenMode.SCREEN_ONLY, false);
        myWorkBench.setMinimizePanelsVisible(false);
      }
      else if (myScreenMode != null && mySurface.getScreenMode() == NlDesignSurface.ScreenMode.SCREEN_ONLY) {
        mySurface.setScreenMode(myScreenMode, false);
        myWorkBench.setMinimizePanelsVisible(true);
      }
    }
  }

  private void disposeActionsToolbar() {
    if (myActionsToolbar == null) {
      return;
    }

    myContentPanel.remove(myActionsToolbar.getToolbarComponent());

    Disposer.dispose(myActionsToolbar);
    myActionsToolbar = null;
  }

  @Nullable
  public RenderResult getRenderResult() {
    return myRenderResult;
  }

  public void setRenderResult(@NotNull RenderResult renderResult) {
    myRenderResult = renderResult;
  }

  @NotNull
  public NlDesignSurface getSurface() {
    return mySurface;
  }

  // ---- Implements CaretListener ----

  @Override
  public void caretPositionChanged(CaretEvent e) {
    if (!myIgnoreListener) {
      updateCaret();
      // TODO: implement
      //ActionBarHandler.showMenu(false, myContext, true);
    }
  }

  @Override
  public void caretAdded(CaretEvent e) {

  }

  @Override
  public void caretRemoved(CaretEvent e) {

  }

  /**
   * Re-enables updates for this preview form. See {@link #deactivate()}
   */
  public void activate() {
    if (isActive) {
      return;
    }

    isActive = true;
    if (myContentPanel != null) {
      initNeleModel();
    }
  }

  /**
   * Disables the updates for this preview form. Any changes to resources or the layout won't update
   * this preview until {@link #activate()} is called.
   */
  public void deactivate() {
    if (!isActive) {
      return;
    }

    isActive = false;
    if (myContentPanel != null) {
      initNeleModel();
    }
  }
}
