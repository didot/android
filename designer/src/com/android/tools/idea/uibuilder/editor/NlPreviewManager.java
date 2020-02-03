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

import com.android.tools.idea.common.editor.DesignToolsSplitEditor;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.res.ResourceNotificationManager;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.visual.VisualizationManager;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import icons.StudioIcons;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.Arrays;
import javax.swing.JComponent;
import javax.swing.LayoutFocusTraversalPolicy;
import org.jetbrains.android.uipreview.AndroidEditorSettings;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages a shared UI Preview window on the right side of the source editor which shows a preview
 * of the corresponding Android resource content on the left.
 * <p>
 * Based on the earlier {@link AndroidLayoutPreviewToolWindowManager} but updated to use
 * (a) the {@link ResourceNotificationManager} for update tracking, and (b) the
 * {@link NlDesignSurface} for layout rendering and direct manipulation editing.
 */
public class NlPreviewManager implements ProjectComponent {
  private final MergingUpdateQueue myToolWindowUpdateQueue;

  private final Project myProject;

  private NlPreviewForm myToolWindowForm;
  private ToolWindow myToolWindow;
  private boolean myToolWindowReady = false;
  private boolean myToolWindowDisposed = false;

  @VisibleForTesting
  private int myUpdateCount;

  public NlPreviewManager(@NotNull Project project) {
    myProject = project;

    myToolWindowUpdateQueue = new MergingUpdateQueue("android.layout.preview", 100, true, null, project);

    final MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileEditorManagerListener());
  }

  @Override
  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(() -> {
      myToolWindowReady = true;
      processFileEditorChange(getActiveLayoutXmlEditor(null));
    });
  }

  public boolean isWindowVisible() {
    return myToolWindow != null && myToolWindow.isVisible();
  }

  protected boolean isUseInteractiveSelector() {
    return true;
  }

  protected String getToolWindowId() {
    return AndroidBundle.message("android.layout.preview.tool.window.title");
  }

  @NotNull
  protected NlPreviewForm createPreviewForm() {
    return new NlPreviewForm(this);
  }

  protected void initToolWindow() {
    myToolWindowForm = createPreviewForm();
    final String toolWindowId = getToolWindowId();
    myToolWindow =
      ToolWindowManager.getInstance(myProject).registerToolWindow(toolWindowId, false, ToolWindowAnchor.RIGHT, myProject, true);
    myToolWindow.setIcon(StudioIcons.Shell.ToolWindows.ANDROID_PREVIEW);

    // Do not give focus to the preview when first opened:
    myToolWindow.getComponent().setFocusTraversalPolicy(new NoDefaultFocusTraversalPolicy());

    myProject.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
      @Override
      public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
        final ToolWindow window = toolWindowManager.getToolWindow(toolWindowId);
        if (window != null && window.isAvailable()) {
          final boolean visible = window.isVisible();
          AndroidEditorSettings.getInstance().getGlobalState().setVisible(visible);

          if (myToolWindowForm != null) {
            if (visible) {
              myToolWindowForm.activate();
            }
            else {
              myToolWindowForm.deactivate();
            }
          }
        }
      }
    });

    final JComponent contentPanel = myToolWindowForm.getComponent();
    final ContentManager contentManager = myToolWindow.getContentManager();
    contentManager.addDataProvider(dataId -> {
      if (LangDataKeys.MODULE.is(dataId) || LangDataKeys.IDE_VIEW.is(dataId) || CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
        TextEditor textEditor = myToolWindowForm.getEditor();
        if (textEditor != null) {
          JComponent component = textEditor.getEditor().getContentComponent();
          DataContext context = DataManager.getInstance().getDataContext(component);
          return context.getData(dataId);
        }
      }
      return null;
    });

    @SuppressWarnings("ConstantConditions") final Content content = contentManager.getFactory().createContent(contentPanel, null, false);
    content.setDisposer(myToolWindowForm);
    content.setCloseable(false);
    content.setPreferredFocusableComponent(contentPanel);
    contentManager.addContent(content);
    contentManager.setSelectedContent(content, true);
    myToolWindowForm.setUseInteractiveSelector(isUseInteractiveSelector());
    if (isWindowVisible()) {
      myToolWindowForm.activate();
    }
  }

  @Override
  public void projectClosed() {
    if (myToolWindowForm != null) {
      Disposer.dispose(myToolWindowForm);
      myToolWindowForm = null;
      myToolWindow = null;
      myToolWindowDisposed = true;
    }
  }

  @Override
  @NotNull
  @NonNls
  public String getComponentName() {
    return "NlPreviewManager";
  }

  @VisibleForTesting
  public int getUpdateCount() {
    return myUpdateCount;
  }

  @VisibleForTesting
  public boolean isPreviewVisible() {
    return myToolWindow != null && myToolWindow.isVisible();
  }

  /**
   * Whether we've seen an open file editor yet
   */
  private boolean mySeenEditor;

  /**
   * The most recently opened file editor that was not showing (while {@link #mySeenEditor} was false)
   */
  private JComponent myPendingShowComponent;

  /**
   * A listener on {@link #myPendingShowComponent} which listens for the most recently opened file editor to start showing
   */
  private HierarchyListener myHierarchyListener;

  private boolean myRenderImmediately;

  private void processFileEditorChange(@Nullable final TextEditor newEditor) {
    if (myPendingShowComponent != null) {
      myPendingShowComponent.removeHierarchyListener(myHierarchyListener);
      myPendingShowComponent = null;
    }

    myToolWindowUpdateQueue.cancelAllUpdates();
    myToolWindowUpdateQueue.queue(new Update("update") {
      @Override
      public void run() {
        myUpdateCount++;
        if (!myToolWindowReady || myToolWindowDisposed) {
          return;
        }
        myRenderImmediately = false;

        final Editor activeEditor = newEditor != null ? newEditor.getEditor() : null;

        if (myToolWindow == null) {
          if (activeEditor == null) {
            return;
          }
          else if (!activeEditor.getComponent().isShowing()) {
            // When the IDE starts, it opens all the previously open editors, one
            // after the other. This means that this method gets called, and for
            // each layout editor that is on top, it opens up the preview window
            // and starts a render, even if the topmost editor is not a layout
            // editor file. However, unlike a normal tab switch performed by the
            // user, we can detect the startup scenario by ignoring editors that
            // are not actually showing, so if editor tabs aren't showing, we ignore
            // them.
            //
            // However, it's possible for the last editor to come up and not be
            // marked showing yet. That means that the XML editor comes up and
            // you have to give it focus before the layout preview kicks in.
            // The reason this happens is that the last event we receive is when
            // the file is opened (but the editor is not yet showing).
            // To deal with this, the following code adds a hierarchy listener,
            // which is notified when the component associated with this editor
            // is actually shown. We need to remove those listeners as soon
            // as we switch to a different editor (which at startup happens rapidly
            // for each successive restored editor tab). And we only do this
            // at startup (recorded by the mySeenEditor field; this is startup
            // per project frame.)
            if (!mySeenEditor) {
              myPendingShowComponent = activeEditor.getComponent();
              if (myHierarchyListener == null) {
                myHierarchyListener = hierarchyEvent -> {
                  if ((hierarchyEvent.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                    if (hierarchyEvent.getComponent() == myPendingShowComponent && myPendingShowComponent.isShowing()) {
                      myPendingShowComponent.removeHierarchyListener(myHierarchyListener);
                      mySeenEditor = true;
                      myPendingShowComponent = null;
                      processFileEditorChange(getActiveLayoutXmlEditor(null));
                    }
                  }
                };
              }
              myPendingShowComponent.addHierarchyListener(myHierarchyListener);
            }

            return;
          }
          mySeenEditor = true;
          initToolWindow();
        }

        final AndroidEditorSettings settings = AndroidEditorSettings.getInstance();
        final boolean hideForNonLayoutFiles = settings.getGlobalState().isHideForNonLayoutFiles();

        if (activeEditor == null) {
          myToolWindow.setAvailable(!hideForNonLayoutFiles, null);
          return;
        }

        if (!myToolWindowForm.setNextEditor(newEditor)) {
          myToolWindow.setAvailable(!hideForNonLayoutFiles, null);
          return;
        }

        myToolWindow.setAvailable(true, null);
        // If user is using Visualization Tool, don't force switch to Preview.
        final boolean visible = AndroidEditorSettings.getInstance().getGlobalState().isVisible()
                                && !VisualizationManager.getInstance(myProject).isWindowVisible();
        if (visible && !myToolWindow.isVisible()) {
          Runnable restoreFocus = null;
          if (myToolWindow.getType() == ToolWindowType.WINDOWED) {
            // Ugly hack: Fix for b/68148499
            // We never want the preview to take focus when the content of the preview changes because of a file change.
            // Even when the preview is restored after being closed (move from Java file to an XML file).
            // There is no way to show the tool window without also taking the focus.
            // This hack is a workaround that sets the focus back to editor.
            // Note, that this may be wrong in certain circumstances, but should be OK for most scenarios.
            restoreFocus = () -> IdeFocusManager.getInstance(myProject).doWhenFocusSettlesDown(() -> restoreFocusToEditor(newEditor));
          }
          // Clear out the render result for the previous file, such that it doesn't briefly show between the time the
          // tool window is shown and the time the render has completed
          myToolWindowForm.clearRenderResult();
          myToolWindow.activate(restoreFocus, false, false);
        }
      }
    });
  }

  private static void restoreFocusToEditor(@NotNull TextEditor newEditor) {
    ApplicationManager.getApplication().invokeLater(() -> newEditor.getEditor().getContentComponent().requestFocus());
  }

  /**
   * Find an active editor for the specified file, or just the first active editor if file is null.
   */
  @Nullable
  private TextEditor getActiveLayoutXmlEditor(@Nullable PsiFile file) {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      return ApplicationManager.getApplication().runReadAction((Computable<TextEditor>)() -> getActiveLayoutXmlEditor(file));
    }
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return (TextEditor)Arrays.stream(FileEditorManager.getInstance(myProject).getSelectedEditors())
      .filter(editor -> editor instanceof TextEditor && isApplicableEditor((TextEditor)editor, file))
      .findFirst()
      .orElse(null);
  }

  @SuppressWarnings("WeakerAccess") // This method needs to be public as it's used by the Anko DSL preview
  public boolean isApplicableEditor(@NotNull TextEditor textEditor, @Nullable PsiFile file) {
    if (textEditor instanceof DesignToolsSplitEditor) {
      // Split-editor is not applicable for the preview panel.
      return false;
    }

    final Document document = textEditor.getEditor().getDocument();
    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);

    if (file != null && !file.equals(psiFile)) {
      return false;
    }

    // In theory, we should just check
    //   LayoutDomFileDescription.isLayoutFile((XmlFile)psiFile);
    // here, but there are problems where files don't show up with layout preview
    // at startup, presumably because the resource directories haven't been properly
    // initialized yet.
    return isInResourceFolder(psiFile);
  }

  @Nullable
  protected XmlFile getBoundXmlFile(@Nullable PsiFile file) {
    return (XmlFile) file;
  }

  @Nullable
  protected ToolWindow getToolWindow() {
    return myToolWindow;
  }

  @NotNull
  public NlPreviewForm getPreviewForm() {
    if (myToolWindow == null) {
      initToolWindow();
    }
    return myToolWindowForm;
  }

  private static boolean isInResourceFolder(@Nullable PsiFile psiFile) {
    if (psiFile instanceof XmlFile) {
      return RenderService.canRender(psiFile);
    }
    return false;
  }

  public static NlPreviewManager getInstance(Project project) {
    return project.getComponent(NlPreviewManager.class);
  }

  /**
   * Manually notify the manager that an editor is about to be shown; typically done right after
   * switching to a file to show an update as soon as possible. This is used when we know
   * the editor is about to be shown (because we've requested it). We don't have a way to
   * add a listener which is called after the requested file has been opened, so instead we
   * simply anticipate the change by calling this method first; the subsequent file open will
   * then become a no-op since the file doesn't change.
   */
  public void notifyFileShown(@NotNull TextEditor editor, boolean renderImmediately) {
    // Don't delete: should be invoked from ConfigurationAction#pickedBetterMatch when we can access designer code from there
    // (or when ConfigurationAction moves here)
    if (renderImmediately) {
      myRenderImmediately = true;
    }
    processFileEditorChange(editor);
    if (renderImmediately) {
      myToolWindowUpdateQueue.sendFlush();
    }
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  private class MyFileEditorManagerListener implements FileEditorManagerListener {
    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      if (!file.isValid()) {
        return;
      }

      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
      processFileEditorChange(getActiveLayoutXmlEditor(psiFile));
    }

    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      if (myToolWindowForm != null) {
        // Remove stale references from the preview form. See b/80084773
        myToolWindowForm.fileClosed(source, file);
      }
      // When using "Close All" action, the selectionChanged event is not triggered.
      // Thus we have to handle this case here.
      // In other cases, do not respond to fileClosed events since this has led to problems
      // with the preview window in the past. See b/64199946 and b/64288544
      if (!source.hasOpenFiles()) {
        ApplicationManager.getApplication().invokeLater(() -> {
          processFileEditorChange(null);
        }, myProject.getDisposed());
      }
    }

    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      final FileEditor newEditor = event.getNewEditor();
      TextEditor layoutXmlEditor = null;
      if (newEditor instanceof TextEditor) {
        TextEditor textEditor = (TextEditor)newEditor;
        if (isApplicableEditor(textEditor, null)) {
          layoutXmlEditor = textEditor;
        }
      }
      processFileEditorChange(layoutXmlEditor);
    }
  }

  /**
   * A {@link LayoutFocusTraversalPolicy} without a default focusable component.
   *
   * When a tool window is created a list of commands are supplied and executed.
   * One of these commands are
   *   {@link RequestFocusInToolWindowCommand}
   * which starts a timer and for the next 10 secs will attempt to set focus to
   * the component returned by {@link #getDefaultComponent}.
   *
   * This causes a problem if the user tries to open the palette within the
   * first 10 seconds the preview is opened. The palette is designed to auto
   * close when it looses focus. Thus a user may see the palette close
   * immediately after opening it.
   *
   * When the preview is opened we probably should keep the focus in the text
   * editor. There doesn't seem to be a way to avoid the request focus command
   * to run for the tool window, but we can trick the focus command into
   * believing that there is no component to give focus to.
   */
  private static class NoDefaultFocusTraversalPolicy extends LayoutFocusTraversalPolicy {

    @Override
    @Nullable
    public Component getDefaultComponent(@Nullable Container aContainer) {
      super.getDefaultComponent(aContainer);
      return null;
    }
  }
}
