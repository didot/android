/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.android.designer.designSurface;

import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.sdk.SdkVersionInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.*;
import com.android.tools.idea.rendering.*;
import com.google.common.primitives.Ints;
import com.intellij.android.designer.componentTree.AndroidTreeDecorator;
import com.intellij.android.designer.inspection.ErrorAnalyzer;
import com.intellij.android.designer.model.*;
import com.intellij.designer.DesignerEditor;
import com.intellij.designer.DesignerToolWindowManager;
import com.intellij.designer.actions.DesignerActionPanel;
import com.intellij.designer.componentTree.TreeComponentDecorator;
import com.intellij.designer.designSurface.*;
import com.intellij.designer.designSurface.tools.ComponentCreationFactory;
import com.intellij.designer.designSurface.tools.ComponentPasteFactory;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadVisualComponent;
import com.intellij.designer.model.WrapInProvider;
import com.intellij.designer.palette.DefaultPaletteItem;
import com.intellij.designer.palette.PaletteGroup;
import com.intellij.designer.palette.PaletteItem;
import com.intellij.designer.palette.PaletteToolWindowManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.Alarm;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.refactoring.AndroidExtractAsIncludeAction;
import org.jetbrains.android.refactoring.AndroidExtractStyleAction;
import org.jetbrains.android.refactoring.AndroidInlineIncludeAction;
import org.jetbrains.android.refactoring.AndroidInlineStyleReferenceAction;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.uipreview.AndroidLayoutPreviewPanel;
import org.jetbrains.android.uipreview.RenderingException;
import org.jetbrains.android.util.AndroidSdkNotConfiguredException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.android.tools.idea.configurations.ConfigurationListener.MASK_RENDERING;
import static com.android.tools.idea.rendering.RenderErrorPanel.SIZE_ERROR_PANEL_DYNAMICALLY;
import static com.intellij.designer.designSurface.ZoomType.FIT_INTO;

/**
 * @author Alexander Lobas
 */
public final class AndroidDesignerEditorPanel extends DesignerEditorPanel implements RenderContext {
  private static final int DEFAULT_HORIZONTAL_MARGIN = 30;
  private static final int DEFAULT_VERTICAL_MARGIN = 20;
  private static final Integer LAYER_ERRORS = LAYER_INPLACE_EDITING + 150; // Must be an Integer, not an int; see JLayeredPane.addImpl

  private final TreeComponentDecorator myTreeDecorator = new AndroidTreeDecorator();
  private final XmlFile myXmlFile;
  private final ExternalPSIChangeListener myPsiChangeListener;
  private final Alarm mySessionAlarm = new Alarm();
  private final MergingUpdateQueue mySessionQueue;
  private final AndroidDesignerEditorPanel.LayoutConfigurationListener myConfigListener;
  private volatile RenderSession mySession;
  private volatile long mySessionId;
  private final Lock myRendererLock = new ReentrantLock();
  private WrapInProvider myWrapInProvider;
  private boolean myZoomRequested = true;
  private RootView myRootView;
  private boolean myShowingRoot;
  @NotNull
  private Configuration myConfiguration;
  private int myConfigurationDirty;
  private boolean myActive;

  /** Zoom level (1 = 100%). TODO: Persist this setting across IDE sessions (on a per file basis) */
  private double myZoom = 1;

  public AndroidDesignerEditorPanel(@NotNull DesignerEditor editor,
                                    @NotNull Project project,
                                    @NotNull Module module,
                                    @NotNull VirtualFile file) {
    super(editor, project, module, file);

    showProgress("Loading configuration...");

    AndroidFacet facet = AndroidFacet.getInstance(getModule());
    assert facet != null;
    myConfiguration = facet.getConfigurationManager().get(file);
    myConfigListener = new LayoutConfigurationListener();
    myConfiguration.addListener(myConfigListener);

    mySessionQueue = ViewsMetaManager.getInstance(project).getSessionQueue();
    myXmlFile = (XmlFile)ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
      @Override
      @Nullable
      public PsiFile compute() {
        return PsiManager.getInstance(getProject()).findFile(myFile);
      }
    });
    myPsiChangeListener = new ExternalPSIChangeListener(this, myXmlFile, 100, new Runnable() {
      @Override
      public void run() {
        reparseFile();
      }
    });

    addActions();

    myActive = true;
    myPsiChangeListener.setInitialize();
    myPsiChangeListener.activate();
    myPsiChangeListener.addRequest();
  }

  private void addActions() {
    addConfigurationActions();
    myActionPanel.getPopupGroup().addSeparator();
    myActionPanel.getPopupGroup().add(buildRefactorActionGroup());
    addGotoDeclarationAction();
  }

  private void addGotoDeclarationAction() {
    AnAction gotoDeclaration = new AnAction("Go To Declaration") {
      @Override
      public void update(AnActionEvent e) {
        EditableArea area = e.getData(EditableArea.DATA_KEY);
        e.getPresentation().setEnabled(area != null && area.getSelection().size() == 1);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        EditableArea area = e.getData(EditableArea.DATA_KEY);
        RadViewComponent component = (RadViewComponent)area.getSelection().get(0);
        PsiNavigateUtil.navigate(component.getTag());
      }
    };
    myActionPanel.registerAction(gotoDeclaration, IdeActions.ACTION_GOTO_DECLARATION);
    myActionPanel.getPopupGroup().add(gotoDeclaration);
  }

  private void addConfigurationActions() {
    DefaultActionGroup designerActionGroup = getActionPanel().getActionGroup();
    ActionGroup group = ConfigurationToolBar.createActions(this);
    designerActionGroup.add(group);
  }

  @Override
  protected DesignerActionPanel createActionPanel() {
    return new AndroidDesignerActionPanel(this, myGlassLayer);
  }

  @Override
  protected CaptionPanel createCaptionPanel(boolean horizontal) {
    // No borders; not necessary since we have a different designer background than the caption area
    return new CaptionPanel(this, horizontal, false);
  }

  @Override
  protected JScrollPane createScrollPane(@NotNull JLayeredPane content) {
    // No background color
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(content);
    scrollPane.setBackground(null);
    return scrollPane;
  }

  @NotNull
  private static ActionGroup buildRefactorActionGroup() {
    final DefaultActionGroup group = new DefaultActionGroup("_Refactor", true);
    final ActionManager manager = ActionManager.getInstance();

    AnAction action = manager.getAction(AndroidExtractStyleAction.ACTION_ID);
    group.add(new AndroidRefactoringActionWrapper("_Extract Style...", action));

    action = manager.getAction(AndroidInlineStyleReferenceAction.ACTION_ID);
    group.add(new AndroidRefactoringActionWrapper("_Inline Style...", action));

    action = manager.getAction(AndroidExtractAsIncludeAction.ACTION_ID);
    group.add(new AndroidRefactoringActionWrapper("E_xtract Layout...", action));

    action = manager.getAction(AndroidInlineIncludeAction.ACTION_ID);
    group.add(new AndroidRefactoringActionWrapper("I_nline Layout...", action));
    return group;
  }

  private void reparseFile() {
    try {
      storeState();
      showDesignerCard();

      parseFile(new Runnable() {
        @Override
        public void run() {
          showDesignerCard();
          myLayeredPane.revalidate();
          restoreState();
        }
      });
    }
    catch (RuntimeException e) {
      myPsiChangeListener.clear();
      showError("Parsing error", e.getCause() == null ? e : e.getCause());
    }
  }

  private void parseFile(final Runnable runnable) {
    final ModelParser parser = new ModelParser(getProject(), myXmlFile);

    createRenderer(new MyThrowable(), new ThrowableConsumer<RenderResult, Throwable>() {
      @Override
      public void consume(RenderResult result) throws Throwable {
        RenderSession session = result.getSession();
        if (session == null) {
          return;
        }

        if (!session.getResult().isSuccess()) {
          // This image may not have been fully rendered before some error caused
          // the render to abort, but a partial render is better. However, if the render
          // was due to some configuration change, we don't want to replace the image
          // since all the mouse regions and model setup will no longer match the pixels.
          if (myRootView != null && myRootView.getImage() != null && session.getImage() != null &&
            session.getImage().getWidth() == myRootView.getImage().getWidth() &&
            session.getImage().getHeight() == myRootView.getImage().getHeight()) {
            myRootView.setImage(session.getImage(), session.isAlphaChannelImage());
            myRootView.repaint();
          }
          return;
        }

        boolean insertPanel = !myShowingRoot;
        if (myRootView == null) {
          myRootView = new RootView(AndroidDesignerEditorPanel.this, 0, 0, session.getImage(), session.isAlphaChannelImage());
          insertPanel = true;
        }
        else {
          myRootView.setImage(session.getImage(), session.isAlphaChannelImage());
          myRootView.updateSize();
        }
        try {
          parser.updateRootComponent(myConfiguration.getFullConfig(), session, myRootView);
        }
        catch (Throwable e) {
          myRootComponent = parser.getRootComponent();
          throw e;
        }
        RadViewComponent newRootComponent = parser.getRootComponent();

        newRootComponent.setClientProperty(ModelParser.XML_FILE_KEY, myXmlFile);
        newRootComponent.setClientProperty(ModelParser.MODULE_KEY, AndroidDesignerEditorPanel.this);
        newRootComponent.setClientProperty(TreeComponentDecorator.KEY, myTreeDecorator);

        IAndroidTarget target = myConfiguration.getTarget();
        assert target != null; // otherwise, rendering would not have succeeded
        PropertyParser propertyParser = new PropertyParser(getModule(), target);
        newRootComponent.setClientProperty(PropertyParser.KEY, propertyParser);
        propertyParser.loadRecursive(newRootComponent);

        boolean firstRender = myRootComponent == null;

        myRootComponent = newRootComponent;

        // Start out selecting the root layout rather than the device item; this will
        // show relevant layout actions immediately, will cause the component tree to
        // be properly expanded, etc
        if (firstRender) {
          RadViewComponent rootComponent = getLayoutRoot();
          if (rootComponent != null) {
            mySurfaceArea.setSelection(Collections.<RadComponent>singletonList(rootComponent));
          }
        }

        if (insertPanel) {
          // Use a custom layout manager which adjusts the margins/padding around the designer canvas
          // dynamically; it will try to use DEFAULT_HORIZONTAL_MARGIN * DEFAULT_VERTICAL_MARGIN, but
          // if there is not enough room, it will split the margins evenly in each dimension until
          // there is no room available without scrollbars.
          JPanel rootPanel = new JPanel(new LayoutManager() {
            @Override
            public void addLayoutComponent(String s, Component component) {
            }

            @Override
            public void removeLayoutComponent(Component component) {
            }

            @Override
            public Dimension preferredLayoutSize(Container container) {
              return new Dimension(0, 0);
            }

            @Override
            public Dimension minimumLayoutSize(Container container) {
              return new Dimension(0, 0);
            }

            @Override
            public void layoutContainer(Container container) {
              myRootView.updateBounds(false);
              int x = Math.max(2, Math.min(DEFAULT_HORIZONTAL_MARGIN, (container.getWidth() - myRootView.getWidth()) / 2));
              int y = Math.max(2, Math.min(DEFAULT_VERTICAL_MARGIN, (container.getHeight() - myRootView.getHeight()) / 2));
              myRootView.setLocation(x, y);
            }
          });

          rootPanel.setBackground(AndroidLayoutPreviewPanel.DESIGNER_BACKGROUND_COLOR);
          rootPanel.setOpaque(true);
          rootPanel.add(myRootView);
          myLayeredPane.add(rootPanel, LAYER_COMPONENT);
          myShowingRoot = true;
        }
        autoZoom();

        loadInspections(new EmptyProgressIndicator());
        updateInspections();

        runnable.run();
      }
    });
  }

  private void createRenderer(final MyThrowable throwable,
                              final ThrowableConsumer<RenderResult, Throwable> runnable) {
    disposeRenderer();

    // TODO: Get rid of this when the project resources are read directly from (possibly unsaved) PSI elements
    ApplicationManager.getApplication().saveAll();

    mySessionAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (mySession == null) {
          showProgress("Initializing Rendering Library...");
        }
      }
    }, 500);

    final long sessionId = ++mySessionId;

    mySessionQueue.queue(new Update("render") {
      private void cancel() {
        mySessionAlarm.cancelAllRequests();
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (!isProjectClosed()) {
              hideProgress();
            }
          }
        });
      }

      @Override
      public void run() {
        try {
          final Module module = getModule();
          AndroidPlatform platform = AndroidPlatform.getInstance(module);
          if (platform == null) {
            throw new AndroidSdkNotConfiguredException();
          }
          AndroidFacet facet = AndroidFacet.getInstance(module);
          if (facet == null) {
            throw new RenderingException();
          }

          if (myConfiguration.getTarget() == null || myConfiguration.getTheme() == null) {
            throw new RenderingException();
          }

          if (sessionId != mySessionId) {
            cancel();
            return;
          }

          final RenderLogger logger = new RenderLogger(myFile.getName(), module);
          final RenderResult renderResult;
          RenderContext renderContext = AndroidDesignerEditorPanel.this;
          if (myRendererLock.tryLock()) {
            try {
              RenderService service = RenderService.create(facet, module, myXmlFile, myConfiguration, logger, renderContext);
              if (service != null) {
                renderResult = service.render();
                service.dispose();
              } else {
                renderResult = new RenderResult(null, null, myXmlFile, logger);
              }
            }
            finally {
              myRendererLock.unlock();
            }
          }
          else {
            cancel();
            return;
          }

          if (sessionId != mySessionId) {
            cancel();
            return;
          }

          if (renderResult == null) {
            throw new RenderingException();
          }

          mySessionAlarm.cancelAllRequests();

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              try {
                if (!isProjectClosed()) {
                  hideProgress();
                  if (sessionId == mySessionId) {
                    runnable.consume(renderResult);
                    updateErrors(renderResult);
                  }
                }
              }
              catch (Throwable e) {
                myPsiChangeListener.clear();
                showError("Parsing error", throwable.wrap(e));
              }
            }
          });
        }
        catch (final Throwable e) {
          myPsiChangeListener.clear();
          mySessionAlarm.cancelAllRequests();

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              myPsiChangeListener.clear();
              showError("Render error", throwable.wrap(e));
            }
          });
        }
      }
    });
  }

  public int getDpi() {
    return myConfiguration.getDensity().getDpiValue();
  }

  private MyRenderPanelWrapper myErrorPanelWrapper;

  private void updateErrors(@NotNull RenderResult result) {
    RenderLogger logger = result.getLogger();
    if (logger == null || !logger.hasProblems()) {
      if (myErrorPanelWrapper == null) {
        return;
      }
      myLayeredPane.remove(myErrorPanelWrapper);
      myErrorPanelWrapper = null;
      myLayeredPane.repaint();
    } else {
      if (myErrorPanelWrapper == null) {
        myErrorPanelWrapper = new MyRenderPanelWrapper(new RenderErrorPanel());
      }
      myErrorPanelWrapper.getErrorPanel().showErrors(result);
      myLayeredPane.add(myErrorPanelWrapper, LAYER_ERRORS);
      myLayeredPane.repaint();
    }
  }

  private void disposeRenderer() {
    if (mySession != null) {
      mySession.dispose();
      mySession = null;
    }
  }

  private void updateRenderer(final boolean updateProperties) {
    createRenderer(new MyThrowable(), new ThrowableConsumer<RenderResult, Throwable>() {
      @Override
      public void consume(RenderResult result) throws Throwable {
        RenderSession session = result.getSession();
        if (session == null) {
          return;
        }
        RadViewComponent rootComponent = (RadViewComponent)myRootComponent;
        RootView rootView = (RootView)rootComponent.getNativeComponent();
        rootView.setImage(session.getImage(), session.isAlphaChannelImage());
        ModelParser.updateRootComponent(myConfiguration.getFullConfig(), rootComponent, session, rootView);

        autoZoom();

        myLayeredPane.revalidate();
        myHorizontalCaption.update();
        myVerticalCaption.update();

        DesignerToolWindowManager.getInstance(getProject()).refresh(updateProperties);
      }
    });
  }

  /**
   * Auto fits the scene, if requested. This will be the case the first time
   * the layout is opened, and after orientation or device changes.
   */
  private synchronized void autoZoom() {
    if (myZoomRequested) {
      myZoomRequested = false;
      zoom(ZoomType.FIT_INTO);
    }
  }

  private synchronized void requestZoomFit() {
    myZoomRequested = true;
  }

  private void removeNativeRoot() {
    if (myRootComponent != null) {
      Component component = ((RadVisualComponent)myRootComponent).getNativeComponent();
      if (component != null) {
        myLayeredPane.remove(component.getParent());
        myShowingRoot = false;
      }
    }
  }

  @Override
  protected void configureError(@NotNull ErrorInfo info) {
    // Error messages for the user (broken custom views, missing resources, etc) are already
    // trapped during rendering and shown in the error panel. These errors are internal errors
    // in the layout editor and should instead be redirected to the log.
    info.myShowMessage = false;
    info.myShowLog = true;

    Throwable renderCreator = null;
    if (info.myThrowable instanceof MyThrowable) {
      renderCreator = info.myThrowable;
      info.myThrowable = ((MyThrowable)info.myThrowable).original;
    }

    StringBuilder builder = new StringBuilder();

    builder.append("ActiveTool: ").append(myToolProvider.getActiveTool());
    builder.append("\nSDK: ");

    try {
      AndroidPlatform platform = AndroidPlatform.getInstance(getModule());
      IAndroidTarget target = platform.getTarget();
      builder.append(target.getFullName()).append(" - ").append(target.getVersion());
    }
    catch (Throwable e) {
      builder.append("<unknown>");
    }

    if (renderCreator != null) {
      builder.append("\nCreateRendererStack:\n");
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      renderCreator.printStackTrace(new PrintStream(stream));
      builder.append(stream.toString());
    }

    if (info.myThrowable instanceof IndexOutOfBoundsException && myRootComponent != null && mySession != null) {
      builder.append("\n-------- RadTree --------\n");
      ModelParser.printTree(builder, myRootComponent, 0);
      builder.append("\n-------- ViewTree(").append(mySession.getRootViews().size()).append(") --------\n");
      for (ViewInfo viewInfo : mySession.getRootViews()) {
        ModelParser.printTree(builder, viewInfo, 0);
      }
    }

    info.myMessage = builder.toString();
  }

  @Override
  protected void showErrorPage(ErrorInfo info) {
    myPsiChangeListener.clear();
    mySessionAlarm.cancelAllRequests();
    removeNativeRoot();
    super.showErrorPage(info);
  }

  @Override
  public void activate() {
    myActive = true;
    myPsiChangeListener.activate();

    if (myPsiChangeListener.isUpdateRenderer() || ((myConfigurationDirty & MASK_RENDERING) != 0)) {
      updateRenderer(true);
    }
    myConfigurationDirty = 0;
  }

  @Override
  public void deactivate() {
    myActive = false;
    myPsiChangeListener.deactivate();
  }

  public void buildProject() {
    if (myPsiChangeListener.ensureUpdateRenderer() && myRootComponent != null) {
      updateRenderer(true);
    }
  }

  @Override
  public void dispose() {
    myPsiChangeListener.dispose();
    myConfiguration.removeListener(myConfigListener);
    super.dispose();

    disposeRenderer();
  }

  @Override
  @Nullable
  protected Module findModule(Project project, VirtualFile file) {
    Module module = super.findModule(project, file);
    if (module == null) {
      module = ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
        @Nullable
        @Override
        public Module compute() {
          return ModuleUtilCore.findModuleForPsiElement(myXmlFile);
        }
      });
    }
    return module;
  }

  @Override
  public String getPlatformTarget() {
    return "android";
  }

  @Override
  public TreeComponentDecorator getTreeDecorator() {
    return myTreeDecorator;
  }

  @Override
  public WrapInProvider getWrapInProvider() {
    if (myWrapInProvider == null) {
      myWrapInProvider = new AndroidWrapInProvider(getProject());
    }
    return myWrapInProvider;
  }

  @Override
  protected ComponentDecorator getRootSelectionDecorator() {
    return EmptyComponentDecorator.INSTANCE;
  }

  @Override
  public List<PaletteGroup> getPaletteGroups() {
    return ViewsMetaManager.getInstance(getProject()).getPaletteGroups();
  }

  @NotNull
  @Override
  public String getVersionLabel(@Nullable String version) {
    if (StringUtil.isEmpty(version)) {
      return "";
    }

    // Android versions are recorded as API integers
    Integer api = Ints.tryParse(version);
    assert api != null : version;
    int since = api.intValue();
    if (since <= 1) {
      return "";
    }

    String name = SdkVersionInfo.getAndroidName(since);

    if (name == null) {
      name = String.format("API %1$d", since);
    }

    return name;
  }

  @Override
  public boolean isDeprecated(@NotNull String deprecatedIn) {
    IAndroidTarget target = myConfiguration.getTarget();
    if (target == null) {
      return super.isDeprecated(deprecatedIn);
    }

    if (StringUtil.isEmpty(deprecatedIn)) {
      return false;
    }

    Integer api = Ints.tryParse(deprecatedIn);
    assert api != null : deprecatedIn;
    return api.intValue() <= target.getVersion().getApiLevel();
  }

  @Override
  @NotNull
  protected ComponentCreationFactory createCreationFactory(final PaletteItem paletteItem) {
    return new ComponentCreationFactory() {
      @NotNull
      @Override
      public RadComponent create() throws Exception {
        RadViewComponent component = ModelParser.createComponent(null, paletteItem.getMetaModel());
        component.setInitialPaletteItem(paletteItem);
        if (component instanceof IConfigurableComponent) {
          ((IConfigurableComponent)component).configure(myRootComponent);
        }
        return component;
      }
    };
  }

  @Override
  public ComponentPasteFactory createPasteFactory(String xmlComponents) {
    return new AndroidPasteFactory(getModule(), myConfiguration.getTarget(), xmlComponents);
  }

  private void updatePalette(IAndroidTarget target) {
    try {
      for (PaletteGroup group : getPaletteGroups()) {
        for (PaletteItem item : group.getItems()) {
          String version = item.getVersion();
          if (version != null) {
            Integer api = Ints.tryParse(version);
            assert api != null : version;
            DefaultPaletteItem paletteItem = (DefaultPaletteItem)item;
            paletteItem.setEnabled(api.intValue() <= target.getVersion().getApiLevel());
          }
        }
      }

      PaletteItem item = getActivePaletteItem();
      if (item != null && !item.isEnabled()) {
        activatePaletteItem(null);
      }

      PaletteToolWindowManager.getInstance(getProject()).refresh();
    }
    catch (Throwable e) {
    }
  }

  @Override
  public String getEditorText() {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return myXmlFile.getText();
      }
    });
  }

  @Override
  protected boolean execute(ThrowableRunnable<Exception> operation, boolean updateProperties) {
    if (!ReadonlyStatusHandler.ensureFilesWritable(getProject(), myFile)) {
      return false;
    }
    try {
      myPsiChangeListener.stop();
      operation.run();
      updateRenderer(updateProperties);
      return true;
    }
    catch (Throwable e) {
      showError("Execute command", e);
      return false;
    }
    finally {
      myPsiChangeListener.start();
    }
  }

  @Override
  protected void executeWithReparse(ThrowableRunnable<Exception> operation) {
    if (!ReadonlyStatusHandler.ensureFilesWritable(getProject(), myFile)) {
      return;
    }
    try {
      myPsiChangeListener.stop();
      operation.run();
      myPsiChangeListener.start();
      reparseFile();
    }
    catch (Throwable e) {
      showError("Execute command", e);
      myPsiChangeListener.start();
    }
  }

  @Override
  protected void execute(List<EditOperation> operations) {
    if (!ReadonlyStatusHandler.ensureFilesWritable(getProject(), myFile)) {
      return;
    }
    try {
      myPsiChangeListener.stop();
      for (EditOperation operation : operations) {
        operation.execute();
      }
      updateRenderer(true);
    }
    catch (Throwable e) {
      showError("Execute command", e);
    }
    finally {
      myPsiChangeListener.start();
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Inspections
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public void loadInspections(ProgressIndicator progress) {
    if (myRootComponent != null) {
      ErrorAnalyzer.load(getProject(), myXmlFile, myRootComponent, progress);
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private static class MyThrowable extends Throwable {
    public Throwable original;

    public MyThrowable wrap(Throwable original) {
      this.original = original;
      return this;
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Zooming
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private static final double ZOOM_FACTOR = 1.2;

  @Override
  public boolean isZoomSupported() {
    return true;
  }

  /** Sets the zoom level. Note that this should be 1, not 100 (percent), for an image at its actual size */
  public void setZoom(double zoom) {
    if (zoom != myZoom) {
      myZoom = zoom;
      normalizeScale();
      viewZoomed();
      mySurfaceArea.scrollToSelection();
      repaint();
    }
  }

  private void normalizeScale() {
    // Some operations are faster if the zoom is EXACTLY 1.0 rather than ALMOST 1.0.
    // (This is because there is a fast-path when image copying and the scale is 1.0;
    // in that case it does not have to do any scaling).
    //
    // If you zoom out 10 times and then back in 10 times, small rounding errors mean
    // that you end up with a scale=1.0000000000000004. In the cases, when you get close
    // to 1.0, just make the zoom an exact 1.0.
    if (Math.abs(myZoom - 1.0) < 0.01) {
      myZoom = 1.0;
    }
  }

  /** Returns the current zoom level. Note that this is 1, not 100 (percent) for an image at its actual size */
  @Override
  public double getZoom() {
    return myZoom;
  }

  /** Zooms the designer view */
  @Override
  public void zoom(@NotNull ZoomType type) {
    switch (type) {
      case IN:
        setZoom(myZoom * ZOOM_FACTOR);
        break;
      case OUT:
        setZoom(myZoom / ZOOM_FACTOR);
        break;
      case ACTUAL:
        setZoom(1);
        break;
      case FIT_INTO:
      case FIT: {
        Dimension sceneSize = myRootComponent.getBounds().getSize();
        Dimension screenSize = getDesignerViewSize();
        if (screenSize != null && screenSize.width > 0 && screenSize.height > 0) {
          int sceneWidth = sceneSize.width;
          int sceneHeight = sceneSize.height;
          if (sceneWidth > 0 && sceneHeight > 0) {
            int viewWidth = screenSize.width;
            int viewHeight = screenSize.height;

            // Reduce the margins if necessary
            int hDelta = viewWidth - sceneWidth;
            int xMargin = 0;
            if (hDelta > 2 * DEFAULT_HORIZONTAL_MARGIN) {
              xMargin = DEFAULT_HORIZONTAL_MARGIN;
            } else if (hDelta > 0) {
              xMargin = hDelta / 2;
            }

            int vDelta = viewHeight - sceneHeight;
            int yMargin = 0;
            if (vDelta > 2 * DEFAULT_VERTICAL_MARGIN) {
              yMargin = DEFAULT_VERTICAL_MARGIN;
            } else if (vDelta > 0) {
              yMargin = vDelta / 2;
            }

            double hScale = (viewWidth - 2 * xMargin) / (double) sceneWidth;
            double vScale = (viewHeight - 2 * yMargin) / (double) sceneHeight;

            double scale = Math.min(hScale, vScale);

            if (type == FIT_INTO) {
              scale = Math.min(1.0, scale);
            }

            setZoom(scale);
          }
        }
        break;
      }
      case SCREEN:
      default:
        throw new UnsupportedOperationException("Not yet implemented: " + type);
    }
  }

  @NotNull
  private Dimension getDesignerViewSize() {
    Dimension size = myScrollPane.getSize();
    size.width -= 2;
    size.height -= 2;

    RootView rootView = getRootView();
    if (rootView != null) {
      if (rootView.getShowDropShadow()) {
        size.width -= ShadowPainter.SHADOW_SIZE;
        size.height -= ShadowPainter.SHADOW_SIZE;
      }
    }

    return size;
  }

  @Override
  @NotNull
  protected Dimension getSceneSize(@NotNull Component target) {
    int width = 0;
    int height = 0;

    if (myRootComponent != null) {
      Rectangle bounds = myRootComponent.getBounds(target);
      width = Math.max(width, (int)bounds.getMaxX());
      height = Math.max(height, (int)bounds.getMaxY());

      width += 1;
      height += 1;

      return new Dimension(width, height);
    }

    return super.getSceneSize(target);
  }

  @Override
  protected void viewZoomed() {
    RootView rootView = getRootView();
    if (rootView != null) {
      rootView.updateSize();
    }
    revalidate();
    super.viewZoomed();
  }


  @Nullable
  private RootView getRootView() {
    if (myRootComponent instanceof RadViewComponent) {
      Component nativeComponent = ((RadViewComponent)myRootComponent).getNativeComponent();
      if (nativeComponent instanceof RootView) {
        return (RootView)nativeComponent;
      }
    }
    return null;
  }

  @Nullable
  private RadViewComponent getLayoutRoot() {
    if (myRootComponent != null && myRootComponent.getChildren().size() == 1) {
      RadComponent component = myRootComponent.getChildren().get(0);
      if (component.isBackground() && component instanceof RadViewComponent) {
        return (RadViewComponent)component;
      }
    }

    return null;
  }

  @Override
  protected RadComponent findTarget(int x, int y, @Nullable ComponentTargetFilter filter) {
    RadComponent target = super.findTarget(x, y, filter);

    // If you click/drag outside the root, select the root
    if (target == null) {
      target = getLayoutRoot();
      if (target != null && filter != null && filter.preFilter(myRootComponent)) {
        filter.resultFilter(target);
      }
    }

    return target;
  }

  /**
   * Layered pane which shows the rendered image, as well as (if applicable) an error message panel on top of the rendering
   * near the bottom
   */
  private static class MyRenderPanelWrapper extends JPanel {
    private final RenderErrorPanel myErrorPanel;
    private int myErrorPanelHeight = -1;

    public MyRenderPanelWrapper(@NotNull RenderErrorPanel errorPanel) {
      super(new BorderLayout());
      myErrorPanel = errorPanel;
      setBackground(null);
      setOpaque(false);
      add(errorPanel);
    }

    private RenderErrorPanel getErrorPanel() {
      return myErrorPanel;
    }

    @Override
    public void doLayout() {
      super.doLayout();
      positionErrorPanel();
    }

    private void positionErrorPanel() {
      int height = getHeight();
      int width = getWidth();
      int size;
      if (SIZE_ERROR_PANEL_DYNAMICALLY) {
        if (myErrorPanelHeight == -1) {
          // Make the layout take up to 3/4ths of the height, and at least 1/4th, but
          // anywhere in between based on what the actual text requires
          size = height * 3 / 4;
          int preferredHeight = myErrorPanel.getPreferredHeight(width) + 8;
          if (preferredHeight < size) {
            size = Math.max(preferredHeight, Math.min(height / 4, size));
            myErrorPanelHeight = size;
          }
        } else {
          size = myErrorPanelHeight;
        }
      } else {
        size = height / 2;
      }

      myErrorPanel.setSize(width, size);
      myErrorPanel.setLocation(0, height - size);
    }
  }

  private void saveState() {
    ConfigurationStateManager stateManager = ConfigurationStateManager.get(getProject());
    ConfigurationProjectState projectState = stateManager.getProjectState();
    projectState.saveState(myConfiguration);

    ConfigurationFileState fileState = new ConfigurationFileState();
    fileState.saveState(myConfiguration);
    stateManager.setConfigurationState(myFile, fileState);
  }

  // ---- Implements RenderContext ----

  @NotNull
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  @Override
  public void requestRender() {
    updateRenderer(false);
  }

  @NotNull
  public UsageType getType() {
    return UsageType.LAYOUT_EDITOR;
  }

  @NotNull
  @Override
  public XmlFile getXmlFile() {
    return myXmlFile;
  }

  @Nullable
  @Override
  public VirtualFile getVirtualFile() {
    return myFile;
  }

  private class LayoutConfigurationListener implements ConfigurationListener {
    @Override
    public boolean changed(int flags) {
      if (isProjectClosed()) {
        return true;
      }

      if (myActive) {
        if ((flags & CFG_DEVICE_STATE) != 0) {
          requestZoomFit();
        }

        updateRenderer(false);

        if ((flags & CFG_TARGET) != 0) {
          IAndroidTarget target = myConfiguration.getTarget();
          if (target != null) {
            updatePalette(target);
          }
        }

        saveState();
      } else {
        myConfigurationDirty |= flags;
      }

      return true;
    }
  }
}
