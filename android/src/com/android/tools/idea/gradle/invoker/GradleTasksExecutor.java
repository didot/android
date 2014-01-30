/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.invoker;

import com.android.tools.idea.gradle.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.invoker.console.view.GradleConsoleToolWindowFactory;
import com.android.tools.idea.gradle.invoker.console.view.GradleConsoleView;
import com.android.tools.idea.gradle.output.GradleMessage;
import com.android.tools.idea.gradle.output.parser.GradleErrorOutputParser;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.sdk.DefaultSdks;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.impl.CompilerErrorTreeView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.ide.errorTreeView.impl.ErrorTreeViewConfiguration;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbModeAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.pom.Navigatable;
import com.intellij.ui.AppIcon;
import com.intellij.ui.content.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.MessageCategory;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.gradle.util.GradleBuilds.OFFLINE_MODE_OPTION;
import static com.android.tools.idea.gradle.util.GradleBuilds.PARALLEL_BUILD_OPTION;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleInvocationJvmArgs;

/**
 * Invokes Gradle tasks as a IDEA task in the background.
 */
class GradleTasksExecutor extends Task.Backgroundable {
  private static final ExternalSystemTaskNotificationListener GRADLE_LISTENER = new ExternalSystemTaskNotificationListenerAdapter() {
  };

  private static final long ONE_MINUTE_MS = 60L /*sec*/ * 1000L /*millisec*/;

  private static final Logger LOG = Logger.getInstance(GradleInvoker.class);

  @NonNls private static final String CONTENT_NAME = "Gradle tasks";
  @NonNls private static final String APP_ICON_ID = "compiler";

  private static final Key<Key<?>> CONTENT_ID_KEY = Key.create("CONTENT_ID");
  private static final int BUFFER_SIZE = 2048;

  private static final String GRADLE_RUNNING_MSG_TITLE = "Gradle Running";
  private static final String STOPPING_GRADLE_MSG_TITLE = "Stopping Gradle";

  @NotNull private final Key<Key<?>> myContentIdKey = CONTENT_ID_KEY;
  @NotNull private final Key<Key<?>> myContentId = Key.create("compile_content");

  @NotNull private final Object myMessageViewLock = new Object();
  @Nullable private NewErrorTreeViewPanel myErrorTreeView;

  @NotNull private final GradleExecutionHelper myHelper = new GradleExecutionHelper();
  @NotNull private final List<String> myGradleTasks;
  @NotNull private final GradleInvoker.AfterGradleInvocationTask[] myAfterGradleInvocationTasks;

  private volatile int myErrorCount;
  private volatile int myWarningCount;

  @NotNull private volatile ProgressIndicator myIndicator = new EmptyProgressIndicator();

  private volatile boolean myMessageViewWasPrepared;
  private volatile boolean myMessagesAutoActivated;

  private CloseListener myCloseListener;

  GradleTasksExecutor(@NotNull Project project,
                      @NotNull List<String> gradleTasks,
                      @NotNull GradleInvoker.AfterGradleInvocationTask[] afterGradleInvocationTasks) {
    super(project, String.format("Gradle: Executing Tasks %1$s", gradleTasks.toString()), false /* Gradle does not support cancellation of task execution */);
    myGradleTasks = gradleTasks;
    myAfterGradleInvocationTasks = afterGradleInvocationTasks;
  }

  @Override
  public String getProcessId() {
    return "GradleTaskInvocation";
  }

  @Override
  public DumbModeAction getDumbModeAction() {
    return DumbModeAction.WAIT;
  }

  @Override
  @Nullable
  public NotificationInfo getNotificationInfo() {
    return new NotificationInfo(myErrorCount > 0 ? "Gradle Invocation (errors)" : "Gradle Invocation (success)",
                                "Gradle Invocation Finished", myErrorCount + " Errors, " + myWarningCount + " Warnings", true);
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    myIndicator = indicator;

    ProjectManager projectManager = ProjectManager.getInstance();
    Project project = getNotNullProject();
    myCloseListener = new CloseListener();
    projectManager.addProjectManagerListener(project, myCloseListener);

    Semaphore semaphore = ((CompilerManagerImpl)CompilerManager.getInstance(project)).getCompilationSemaphore();
    boolean acquired = false;
    try {
      try {
        while (!acquired) {
          acquired = semaphore.tryAcquire(300, TimeUnit.MILLISECONDS);
          if (indicator.isCanceled()) {
            // Give up obtaining the semaphore, let compile work begin in order to stop gracefully on cancel event.
            break;
          }
        }
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      if (!isHeadless()) {
        addIndicatorDelegate();
      }
      invokeGradleTasks();
    }
    finally {
      try {
        indicator.stop();
        projectManager.removeProjectManagerListener(project, myCloseListener);
      }
      finally {
        if (acquired) {
          semaphore.release();
        }
      }
    }
  }

  private void addIndicatorDelegate() {
    if (myIndicator instanceof ProgressIndicatorEx) {
      ProgressIndicatorEx indicator = (ProgressIndicatorEx)myIndicator;
      indicator.addStateDelegate(new ProgressIndicatorStateDelegate());
    }
  }

  private void closeView() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        synchronized (myMessageViewLock) {
          if (myErrorTreeView != null && !getNotNullProject().isDisposed()) {
            addStatisticsMessage(CompilerBundle.message("statistics.error.count", myErrorCount));
            addStatisticsMessage(CompilerBundle.message("statistics.warnings.count", myWarningCount));
            addMessage(new GradleMessage(GradleMessage.Kind.INFO, "See complete output in console"), new OpenGradleConsole());
            myErrorTreeView.selectFirstMessage();
          }
        }
      }

      private void addStatisticsMessage(@NotNull String text) {
        addMessage(new GradleMessage(GradleMessage.Kind.STATISTICS, text), null);
      }
    }, ModalityState.NON_MODAL);
  }

  private void invokeGradleTasks() {
    final Project project = getNotNullProject();

    final GradleExecutionSettings executionSettings = GradleUtil.getGradleExecutionSettings(project);
    final String projectPath = project.getBasePath();

    Function<ProjectConnection, Void> executeTasksFunction = new Function<ProjectConnection, Void>() {
      @Override
      public Void fun(ProjectConnection connection) {
        final Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();

        GradleConsoleView consoleView = GradleConsoleView.getInstance(project);
        consoleView.clear();

        addMessage(new GradleMessage(GradleMessage.Kind.INFO, "Gradle tasks " + myGradleTasks), null);

        ExternalSystemTaskId id = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project);
        List<String> extraJvmArgs = getGradleInvocationJvmArgs(new File(projectPath));

        String executingTasksText =
          "Executing tasks: " + myGradleTasks + SystemProperties.getLineSeparator() + SystemProperties.getLineSeparator();
        consoleView.print(executingTasksText, ConsoleViewContentType.NORMAL_OUTPUT);

        ByteArrayOutputStream stdout = new ConsoleAwareOutputStream(consoleView, ConsoleViewContentType.NORMAL_OUTPUT);
        ByteArrayOutputStream stderr = new ConsoleAwareOutputStream(consoleView, ConsoleViewContentType.ERROR_OUTPUT);

        List<GradleMessage> compilerMessages = Collections.emptyList();

        try {
          AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
          List<String> commandLineOptions = Lists.newArrayList(buildConfiguration.getCommandLineOptions());

          CompilerWorkspaceConfiguration compilerConfiguration = CompilerWorkspaceConfiguration.getInstance(project);
          if (compilerConfiguration.PARALLEL_COMPILATION && !commandLineOptions.contains(PARALLEL_BUILD_OPTION)) {
            LOG.info("Using 'parallel' build option");
            commandLineOptions.add(PARALLEL_BUILD_OPTION);
          }

          BuildLauncher launcher = connection.newBuild();
          GradleExecutionHelper.prepare(launcher, id, executionSettings, GRADLE_LISTENER, extraJvmArgs, commandLineOptions, connection);

          File javaHome = DefaultSdks.getDefaultJavaHome();
          if (javaHome != null) {
            launcher.setJavaHome(javaHome);
          }
          launcher.forTasks(ArrayUtil.toStringArray(myGradleTasks));
          launcher.setStandardOutput(stdout);
          launcher.setStandardError(stderr);
          launcher.run();
        }
        catch (BuildException e) {
          compilerMessages = handleBuildException(e, stderr.toString());
        }
        finally {
          Closeables.closeQuietly(stdout);
          Closeables.closeQuietly(stderr);

          stopwatch.stop();

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              notifyGradleInvocationCompleted(stopwatch.elapsedMillis());
            }
          });

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              showMessages();
            }
          });

          GradleInvocationResult result = new GradleInvocationResult(myGradleTasks, compilerMessages);
          for (GradleInvoker.AfterGradleInvocationTask task : myAfterGradleInvocationTasks) {
            task.execute(result);
          }
        }
        return null;
      }
    };

    try {
      myHelper.execute(projectPath, executionSettings, executeTasksFunction);
    }
    catch (ExternalSystemException e) {
      if (myIndicator.isCanceled()) {
        LOG.info("Failed to complete Gradle execution. Project may be closing or already closed.", e);
      }
      else {
        Messages.showErrorDialog(myProject, "Failed to complete Gradle execution.\n\nCause:\n" + e.getMessage(), GRADLE_RUNNING_MSG_TITLE);
      }
    }
  }

  /**
   * Something went wrong while invoking Gradle. Since we cannot distinguish an execution error from compilation errors easily, we first try
   * to show, in the "Problems" view, compilation errors by parsing the error output. If no errors are found, we show the stack trace in the
   * "Problems" view. The idea is that we need to somehow inform the user that something went wrong.
   */
  private List<GradleMessage> handleBuildException(BuildException e, String stdErr) {
    List<GradleMessage> compilerMessages = new GradleErrorOutputParser().parseErrorOutput(stdErr, false);
    if (!compilerMessages.isEmpty()) {
      for (GradleMessage msg : compilerMessages) {
        addMessage(msg, null);
      }
      return compilerMessages;
    }
    // There are no error messages to present. Show some feedback indicating that something went wrong.
    if (!stdErr.isEmpty()) {
      // Show the contents of stderr as a compiler error.
      GradleMessage msg = new GradleMessage(GradleMessage.Kind.ERROR, stdErr);
      compilerMessages.add(msg);
      addMessage(msg, null);
    }
    else {
      // Since we have nothing else to show, just print the stack trace of the caught exception.
      ByteArrayOutputStream out = new ByteArrayOutputStream(BUFFER_SIZE);
      try {
        //noinspection IOResourceOpenedButNotSafelyClosed
        e.printStackTrace(new PrintStream(out));
        String message = "Internal error:" + SystemProperties.getLineSeparator() + out.toString();
        GradleMessage msg = new GradleMessage(GradleMessage.Kind.ERROR, message);
        compilerMessages.add(msg);
        addMessage(msg, null);
      }
      finally {
        Closeables.closeQuietly(out);
      }
    }
    return compilerMessages;
  }

  private void addMessage(@NotNull final GradleMessage message, @Nullable final Navigatable navigatable) {
    prepareMessageView();
    switch (message.getKind()) {
      case WARNING:
        myWarningCount++;
        break;
      case ERROR:
        myErrorCount++;
      default:
        // do nothing.
    }
    if (ApplicationManager.getApplication().isDispatchThread()) {
      openMessageView();
      add(message, navigatable);
    }
    else {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          if (!getNotNullProject().isDisposed()) {
            openMessageView();
            add(message, navigatable);
          }
        }
      }, ModalityState.NON_MODAL);
    }
  }

  private void prepareMessageView() {
    if (!myIndicator.isRunning() || myMessageViewWasPrepared) {
      return;
    }
    myMessageViewWasPrepared = true;
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (!getNotNullProject().isDisposed()) {
          synchronized (myMessageViewLock) {
            // Clear messages from the previous compilation
            if (myErrorTreeView == null) {
              // If message view != null, the contents has already been cleared.
              removeAllContents(null);
            }
          }
        }
      }
    });
  }

  private void removeAllContents(@Nullable Content toKeep) {
    MessageView messageView = getMessageView();
    Content[] contents = messageView.getContentManager().getContents();
    for (Content content : contents) {
      if (content.isPinned() || content == toKeep) {
        continue;
      }
      if (content.getUserData(myContentIdKey) != null) { // the content was added by me
        messageView.getContentManager().removeContent(content, true);
      }
    }
  }

  private void openMessageView() {
    if (myIndicator.isCanceled()) {
      return;
    }

    Project project = getNotNullProject();
    JComponent component;
    synchronized (myMessageViewLock) {
      if (myErrorTreeView != null) {
        return;
      }
      //noinspection ConstantConditions
      myErrorTreeView = new CompilerErrorTreeView(project, null) {
        @Override
        protected void fillRightToolbarGroup(DefaultActionGroup group) {
          super.fillRightToolbarGroup(group);
          group.add(new AnAction("Show Console Output", null, AndroidIcons.GradleConsole) {
            @Override
            public void actionPerformed(AnActionEvent e) {
              activateGradleConsole();
            }

            @Override
            public void update(AnActionEvent e) {
              e.getPresentation().setEnabledAndVisible(true);
            }
          });
        }
      };

      myErrorTreeView.setProcessController(new NewErrorTreeViewPanel.ProcessController() {
        @Override
        public void stopProcess() {
        }

        @Override
        public boolean isProcessStopped() {
          return !myIndicator.isRunning();
        }
      });
      component = myErrorTreeView.getComponent();
    }

    Content content = ContentFactory.SERVICE.getInstance().createContent(component, CONTENT_NAME, true);
    content.putUserData(myContentIdKey, myContentId);

    MessageView messageView = getMessageView();
    messageView.getContentManager().addContent(content);

    myCloseListener.setContent(messageView.getContentManager(), content);

    removeAllContents(content);
    messageView.getContentManager().setSelectedContent(content);
  }

  private void activateGradleConsole() {
    ToolWindow window = getToolWindowManager().getToolWindow(GradleConsoleToolWindowFactory.ID);
    if (window != null) {
      window.activate(null, false);
    }
  }

  private void add(@NotNull GradleMessage message, @Nullable Navigatable navigatable) {
    synchronized (myMessageViewLock) {
      if (myErrorTreeView != null && !getNotNullProject().isDisposed()) {
        GradleMessage.Kind messageKind = message.getKind();
        int type = translateMessageKind(messageKind);
        String[] text = getTextOf(message);
        if (navigatable == null) {
          VirtualFile file = findFileFrom(message);
          myErrorTreeView.addMessage(type, text, file, message.getLineNumber() - 1, message.getColumn() - 1, null);
        }
        else {
          myErrorTreeView.addMessage(type, text, null, navigatable, null, null, null);
        }

        boolean autoActivate =
          !myMessagesAutoActivated && (type == MessageCategory.ERROR || (type == MessageCategory.WARNING && !shouldHideWarnings()));
        if (autoActivate) {
          myMessagesAutoActivated = true;
          activateMessageView();
        }
      }
    }
  }

  @NotNull
  private static String[] getTextOf(@NotNull GradleMessage message) {
    String text = message.getText();
    if (text.indexOf('\n') == -1) {
      return new String[]{text};
    }
    List<String> lines = Lists.newArrayList(Splitter.on('\n').split(text));
    return lines.toArray(new String[lines.size()]);
  }

  @Nullable
  private static VirtualFile findFileFrom(@NotNull GradleMessage message) {
    String sourcePath = message.getSourcePath();
    if (Strings.isNullOrEmpty(sourcePath)) {
      return null;
    }
    return VfsUtil.findFileByIoFile(new File(sourcePath), true);
  }

  private boolean shouldHideWarnings() {
    Project project = getNotNullProject();
    return ErrorTreeViewConfiguration.getInstance(project).isHideWarnings();
  }

  private static int translateMessageKind(@NotNull GradleMessage.Kind kind) {
    switch (kind) {
      case INFO:
        return MessageCategory.INFORMATION;
      case WARNING:
        return MessageCategory.WARNING;
      case ERROR:
        return MessageCategory.ERROR;
      case STATISTICS:
        return MessageCategory.STATISTICS;
      default:
        LOG.info("Unknown message kind: " + kind);
        return 0;
    }
  }

  private void notifyGradleInvocationCompleted(long durationMillis) {
    Project project = getNotNullProject();
    if (!project.isDisposed()) {
      String statusMsg = createStatusMessage(durationMillis);
      MessageType messageType = myErrorCount > 0 ? MessageType.ERROR : myWarningCount > 0 ? MessageType.WARNING : MessageType.INFO;
      if (durationMillis > ONE_MINUTE_MS) {
        getToolWindowManager().notifyByBalloon(ToolWindowId.MESSAGES_WINDOW, messageType, statusMsg);
      }
      CompilerManager.NOTIFICATION_GROUP.createNotification(statusMsg, messageType).notify(myProject);
    }
  }

  @NotNull
  private String createStatusMessage(long durationMillis) {
    String message = "Gradle invocation completed successfully";
    if (myErrorCount > 0) {
      if (myWarningCount > 0) {
        message += String.format(" with %d error(s) and %d warning(s)", myErrorCount, myWarningCount);
      }
      else {
        message += String.format(" with %d error(s)", myErrorCount);
      }
    }
    else if (myWarningCount > 0) {
      message += String.format(" with %d warnings(s)", myWarningCount);
    }
    message = message + " in " + StringUtil.formatDuration(durationMillis);
    return message;
  }

  @NotNull
  private ToolWindowManager getToolWindowManager() {
    return ToolWindowManager.getInstance(getNotNullProject());
  }

  private void showMessages() {
    synchronized (myMessageViewLock) {
      if (myErrorTreeView != null && !getNotNullProject().isDisposed()) {
        MessageView messageView = getMessageView();
        Content[] contents = messageView.getContentManager().getContents();
        for (Content content : contents) {
          if (content.getUserData(myContentIdKey) != null) {
            messageView.getContentManager().setSelectedContent(content);
            return;
          }
        }
      }
    }
  }

  @NotNull
  private MessageView getMessageView() {
    return MessageView.SERVICE.getInstance(getNotNullProject());
  }

  @NotNull
  private Project getNotNullProject() {
    assert myProject != null;
    return myProject;
  }

  private void activateMessageView() {
    synchronized (myMessageViewLock) {
      if (myErrorTreeView != null) {
        ToolWindow window = getToolWindowManager().getToolWindow(ToolWindowId.MESSAGES_WINDOW);
        if (window != null) {
          window.activate(null, false);
        }
      }
    }
  }

  private void cancel() {
    if (!myIndicator.isCanceled()) {
      try {
        GradleUtil.stopAllGradleDaemons(false);
      }
      catch (FileNotFoundException e) {
        Messages.showErrorDialog(myProject, e.getMessage(), STOPPING_GRADLE_MSG_TITLE);
      }
      catch (IOException e) {
        String errMsg = "Failed to stop Gradle daemons. Cause: " + e.getMessage();
        Messages.showErrorDialog(myProject, errMsg, STOPPING_GRADLE_MSG_TITLE);
      }
      myIndicator.cancel();
    }
  }

  private class CloseListener extends ContentManagerAdapter implements ProjectManagerListener {
    @NotNull private ContentManager myContentManager;
    @Nullable private Content myContent;

    private boolean myIsApplicationExitingOrProjectClosing;
    private boolean myUserAcceptedCancel;

    @Override
    public void projectOpened(Project project) {
    }

    @Override
    public boolean canCloseProject(Project project) {
      if (!project.equals(myProject)) {
        return true;
      }
      if (shouldPromptUser()) {
        myUserAcceptedCancel = askUserToCancelGradleExecution();
        if (!myUserAcceptedCancel) {
          return false; // veto closing
        }
        cancel();
        return true;
      }
      return !myIndicator.isRunning();
    }

    @Override
    public void projectClosed(Project project) {
      if (project.equals(myProject) && myContent != null) {
        myContentManager.removeContent(myContent, true);
      }
    }

    @Override
    public void projectClosing(Project project) {
      if (project.equals(myProject)) {
        myIsApplicationExitingOrProjectClosing = true;
      }
    }

    void setContent(@NotNull ContentManager contentManager, @Nullable Content content) {
      myContent = content;
      myContentManager = contentManager;
      contentManager.addContentManagerListener(this);
    }

    @Override
    public void contentRemoved(ContentManagerEvent event) {
      if (event.getContent() == myContent) {
        synchronized (myMessageViewLock) {
          Project project = getNotNullProject();
          if (myErrorTreeView != null && !project.isDisposed()) {
            myErrorTreeView.dispose();
            myErrorTreeView = null;
            if (myIndicator.isRunning()) {
              cancel();
            }
            AppIcon appIcon = AppIcon.getInstance();
            if (appIcon.hideProgress(project, APP_ICON_ID)) {
              //noinspection ConstantConditions
              appIcon.setErrorBadge(project, null);
            }
          }
        }
        myContentManager.removeContentManagerListener(this);
        myContent.release();
        myContent = null;
      }
    }

    @Override
    public void contentRemoveQuery(ContentManagerEvent event) {
      if (event.getContent() == myContent && !myIndicator.isCanceled() && shouldPromptUser()) {
        myUserAcceptedCancel = askUserToCancelGradleExecution();
        if (!myUserAcceptedCancel) {
          event.consume(); // veto closing
        }
      }
    }

    private boolean shouldPromptUser() {
      return !myUserAcceptedCancel && !myIsApplicationExitingOrProjectClosing && myIndicator.isRunning();
    }

    private boolean askUserToCancelGradleExecution() {
      ProjectManager projectManager = ProjectManager.getInstance();
      List<String> projectsBeingBuilt = Lists.newArrayList();
      for (Project project : projectManager.getOpenProjects()) {
        if (project.getBasePath().equals(getNotNullProject().getBasePath())) {
          continue;
        }
        BuildMode buildMode = BuildSettings.getInstance(project).getBuildMode();
        if (buildMode != null) {
          projectsBeingBuilt.add("'" + project.getName() + "'");
        }
      }

      StringBuilder msgBuilder = new StringBuilder();
      msgBuilder.append("Gradle is running. Proceed with Project closing?\n\n")
        .append("If you click \"Yes\" Android Studio will stop all the Gradle daemons currently running on your machine.\n")
        .append("Any project builds, either from the command line or in other IDE instances, will stop.");

      if (!projectsBeingBuilt.isEmpty()) {
        msgBuilder.append("\n\nCurrently these projects may be currently being built: ").append(projectsBeingBuilt);
      }

      String msg = msgBuilder.toString();

      int result = Messages.showYesNoDialog(myProject, msg, GRADLE_RUNNING_MSG_TITLE, Messages.getQuestionIcon());
      return result == Messages.YES;
    }
  }

  private class ProgressIndicatorStateDelegate extends ProgressIndicatorBase {
    @Override
    public void cancel() {
      super.cancel();
      closeView();
      stopAppIconProgress();
    }

    @Override
    public void stop() {
      super.stop();
      if (!isCanceled()) {
        closeView();
      }
      stopAppIconProgress();
    }

    private void stopAppIconProgress() {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          AppIcon appIcon = AppIcon.getInstance();
          Project project = getNotNullProject();
          if (appIcon.hideProgress(project, APP_ICON_ID)) {
            if (myErrorCount > 0) {
              appIcon.setErrorBadge(project, String.valueOf(myErrorCount));
              appIcon.requestAttention(project, true);
            }
            else {
              appIcon.setOkBadge(project, true);
              appIcon.requestAttention(project, false);
            }
          }
        }
      });
    }

    @Override
    protected void onProgressChange() {
      prepareMessageView();
    }
  }

  private static class ConsoleAwareOutputStream extends ByteArrayOutputStream {
    @NotNull private final GradleConsoleView myConsoleView;
    @NotNull private final ConsoleViewContentType myContentType;

    private volatile boolean myClosed;

    ConsoleAwareOutputStream(@NotNull GradleConsoleView consoleView, @NotNull ConsoleViewContentType contentType) {
      super(BUFFER_SIZE);
      myConsoleView = consoleView;
      myContentType = contentType;
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
      if (!myClosed) {
        // If we keep writing to the console after closing it we get NPEs pointing to native code, at least on MacOS.
        String text = new String(b, off, len);
        myConsoleView.print(text, myContentType);
        super.write(b, off, len);
      }
    }

    @Override
    public void close() throws IOException {
      myClosed = true;
      super.close();
    }
  }

  private class OpenGradleConsole implements Navigatable {
    @Override
    public void navigate(boolean requestFocus) {
      activateGradleConsole();
    }

    @Override
    public boolean canNavigate() {
      return true;
    }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }
  }
}
