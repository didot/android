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
package com.android.tools.idea.startup;

import static com.android.tools.idea.io.FilePaths.toSystemDependentPath;
import static com.android.tools.idea.startup.Actions.hideAction;
import static com.android.tools.idea.startup.Actions.moveAction;
import static com.android.tools.idea.startup.Actions.replaceAction;
import static com.intellij.openapi.actionSystem.Anchor.AFTER;
import static org.jetbrains.android.sdk.AndroidSdkUtils.DEFAULT_JDK_NAME;
import static org.jetbrains.android.sdk.AndroidSdkUtils.createNewAndroidPlatform;

import com.android.prefs.AndroidLocation;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.idea.actions.AndroidActionGroupRemover;
import com.android.tools.idea.actions.AndroidImportModuleAction;
import com.android.tools.idea.actions.AndroidImportProjectAction;
import com.android.tools.idea.actions.AndroidNewModuleAction;
import com.android.tools.idea.actions.AndroidNewModuleInGroupAction;
import com.android.tools.idea.actions.AndroidNewProjectAction;
import com.android.tools.idea.actions.AndroidOpenFileAction;
import com.android.tools.idea.actions.CreateLibraryFromFilesAction;
import com.android.tools.idea.deploy.DeployActionsInitializer;
import com.android.tools.idea.gradle.actions.AndroidTemplateProjectSettingsGroup;
import com.android.tools.idea.gradle.actions.AndroidTemplateProjectStructureAction;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.ui.GuiTestingService;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.wizard.AndroidStudioWelcomeScreenProvider;
import com.android.utils.Pair;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.projectView.actions.MarkRootGroup;
import com.intellij.ide.projectView.impl.MoveModuleToGroupTopLevel;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Constraints;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Performs Gradle-specific IDE initialization
 */
public class GradleSpecificInitializer implements Runnable {

  private static final Logger LOG = Logger.getInstance(GradleSpecificInitializer.class);

  // Id for TemplateProjectSettingsGroup
  @NotNull public static final String TEMPLATE_PROJECT_SETTINGS_GROUP_ID = "TemplateProjectSettingsGroup";

  @Override
  public void run() {
    setUpNewProjectActions();
    DeployActionsInitializer.installActions();
    setUpWelcomeScreenActions();
    replaceProjectPopupActions();
    // Replace "TemplateProjectSettingsGroup" to cause "Find Action" menu use AndroidTemplateProjectSettingsGroup (b/37141013)
    replaceAction(TEMPLATE_PROJECT_SETTINGS_GROUP_ID, new AndroidTemplateProjectSettingsGroup());
    setUpGradleViewToolbarActions();
    checkInstallPath();

    ActionManager actionManager = ActionManager.getInstance();
    // "Configure Plugins..." Not sure why it's called StartupWizard.
    AnAction pluginAction = actionManager.getAction("StartupWizard");
    // Never applicable in the context of android studio, so just set to invisible.
    if (pluginAction != null) {
      pluginAction.getTemplatePresentation().setVisible(false);
    }

    if (AndroidSdkUtils.isAndroidSdkManagerEnabled()) {
      try {
        // Setup JDK and Android SDK if necessary
        setupSdks();
        checkAndroidSdkHome();
      }
      catch (Exception e) {
        LOG.error("Unexpected error while setting up SDKs: ", e);
      }
      checkAndSetAndroidSdkSources();
    }
  }

  /**
   * Gradle has an issue when the studio path contains ! (http://b.android.com/184588)
   */
  private static void checkInstallPath() {
    if (PathManager.getHomePath().contains("!")) {
      final Application app = ApplicationManager.getApplication();

      app.getMessageBus().connect(app).subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
        @Override
        public void appStarting(Project project) {
          app.invokeLater(() -> {
            String message = String.format("%1$s must not be installed in a path containing '!' or Gradle sync will fail!",
                                           ApplicationNamesInfo.getInstance().getProductName());
            Notification notification = getNotificationGroup().createNotification(message, NotificationType.ERROR);
            notification.setImportant(true);
            Notifications.Bus.notify(notification);
          });
        }
      });
    }
  }

  private static void setUpGradleViewToolbarActions() {
    hideAction("ExternalSystem.RefreshAllProjects");
    hideAction("ExternalSystem.SelectProjectDataToImport");
  }

  private static void setUpNewProjectActions() {
    // Unregister IntelliJ's version of the project actions and manually register our own.
    replaceAction("OpenFile", new AndroidOpenFileAction());
    replaceAction("NewProject", new AndroidNewProjectAction());
    replaceAction("NewModule", new AndroidNewModuleAction());
    replaceAction("NewModuleInGroup", new AndroidNewModuleInGroupAction());
    replaceAction("ImportProject", new AndroidImportProjectAction());
    replaceAction("CreateLibraryFromFile", new CreateLibraryFromFilesAction());
    replaceAction("ImportModule", new AndroidImportModuleAction());

    hideAction(IdeActions.ACTION_GENERATE_ANT_BUILD);
    hideAction("AddFrameworkSupport");
    hideAction("BuildArtifact");
    hideAction("RunTargetAction");
  }

  private static void setUpWelcomeScreenActions() {
    // Force the new "flat" welcome screen.
    System.setProperty("ide.new.welcome.screen.force", "true");

    // Update the Welcome Screen actions
    replaceAction("WelcomeScreen.OpenProject", new AndroidOpenFileAction("Open an existing Android Studio project"));
    replaceAction("WelcomeScreen.CreateNewProject", new AndroidNewProjectAction("Start a new Android Studio project"));
    replaceAction("WelcomeScreen.ImportProject", new AndroidImportProjectAction("Import project (Gradle, Eclipse ADT, etc.)"));
    replaceAction("WelcomeScreen.Configure.ProjectStructure", new AndroidTemplateProjectStructureAction("Default Project Structure..."));
    replaceAction("TemplateProjectStructure", new AndroidTemplateProjectStructureAction("Default Project Structure..."));

    moveAction("WelcomeScreen.ImportProject", "WelcomeScreen.QuickStart.IDEA",
               "WelcomeScreen.QuickStart", new Constraints(AFTER, "WelcomeScreen.GetFromVcs"));

    ActionManager actionManager = ActionManager.getInstance();
    AnAction getFromVcsAction = actionManager.getAction("WelcomeScreen.GetFromVcs");
    if (getFromVcsAction != null) {
      getFromVcsAction.getTemplatePresentation().setText("Check out Project from Version Control");
    }
  }

  private static void replaceProjectPopupActions() {
    Deque<Pair<DefaultActionGroup, AnAction>> stack = new ArrayDeque<>();
    stack.add(Pair.of(null, ActionManager.getInstance().getAction("ProjectViewPopupMenu")));
    while (!stack.isEmpty()) {
      Pair<DefaultActionGroup, AnAction> entry = stack.pop();
      DefaultActionGroup parent = entry.getFirst();
      AnAction action = entry.getSecond();
      if (action instanceof DefaultActionGroup) {
        DefaultActionGroup actionGroup = (DefaultActionGroup)action;
        for (AnAction child : actionGroup.getChildActionsOrStubs()) {
          stack.push(Pair.of(actionGroup, child));
        }
      }

      if (action instanceof MoveModuleToGroupTopLevel) {
        parent.remove(action);
        parent.add(new AndroidActionGroupRemover((ActionGroup)action, "Move Module to Group"),
                   new Constraints(AFTER, "OpenModuleSettings"));
      }
      else if (action instanceof MarkRootGroup) {
        parent.remove(action);
        parent.add(new AndroidActionGroupRemover((ActionGroup)action, "Mark Directory As"),
                   new Constraints(AFTER, "OpenModuleSettings"));
      }
    }
  }

  private static void notifyInvalidSdk() {
    String key = "android.invalid.sdk.message";
    String message = AndroidBundle.message(key);

    NotificationListener listener = new NotificationListener.Adapter() {
      @Override
      protected void hyperlinkActivated(@NotNull Notification notification,
                                        @NotNull HyperlinkEvent e) {
        SdkQuickfixUtils.showAndroidSdkManager();
        notification.expire();
      }
    };
    addStartupWarning(message, listener);
  }

  private static void addStartupWarning(@NotNull final String message, @Nullable final NotificationListener listener) {
    final Application app = ApplicationManager.getApplication();

    app.getMessageBus().connect(app).subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appStarting(Project project) {
        app.invokeLater(() -> {
          Notification notification =
            getNotificationGroup().createNotification("SDK Validation", message, NotificationType.WARNING, listener);
          notification.setImportant(true);
          Notifications.Bus.notify(notification);
        });
      }
    });
  }

  private static NotificationGroup getNotificationGroup() {
    // Use the system health settings by default
    NotificationGroup group = NotificationGroup.findRegisteredGroup("System Health");
    if (group == null) {
      // This shouldn't happen
      group = new NotificationGroup("Gradle Initializer", NotificationDisplayType.STICKY_BALLOON, true);
    }
    return group;
  }

  private static void setupSdks() {
    try {
      repairDuplicateAndroidSdks(); // TODO(b/143326468): Remove in Studio 4.2.
    }
    catch (Throwable e) {
      LOG.error("Failed to remove duplicate Android SDKs", e);
    }

    IdeSdks ideSdks = IdeSdks.getInstance();
    File androidHome = ideSdks.getAndroidSdkPath();

    if (androidHome != null) {
      Validator.Result result = PathValidator.forAndroidSdkLocation().validate(androidHome);
      Validator.Severity severity = result.getSeverity();

      if (severity == Validator.Severity.ERROR) {
        notifyInvalidSdk();
      }

      // Do not prompt user to select SDK path (we have one already.) Instead, check SDK compatibility when a project is opened.
      return;
    }

    Sdk sdk = findFirstAndroidSdk();
    if (sdk != null) {
      String sdkHomePath = sdk.getHomePath();
      assert sdkHomePath != null;
      ideSdks.createAndroidSdkPerAndroidTarget(toSystemDependentPath(sdkHomePath));
      return;
    }

    // Called in a 'invokeLater' block, otherwise file chooser will hang forever.
    ApplicationManager.getApplication().invokeLater(() -> {
      File androidSdkPath = getAndroidSdkPath();
      if (androidSdkPath == null) {
        return;
      }

      FirstRunWizardMode wizardMode = AndroidStudioWelcomeScreenProvider.getWizardMode();
      // Only show "Select SDK" dialog if the "First Run" wizard is not displayed.
      boolean promptSdkSelection = wizardMode == null;

      Sdk sdk1 = createNewAndroidPlatform(androidSdkPath.getPath(), promptSdkSelection);
      if (sdk1 != null) {
        // Rename the SDK to fit our default naming convention.
        String sdkNamePrefix = AndroidSdks.SDK_NAME_PREFIX;
        if (sdk1.getName().startsWith(sdkNamePrefix)) {
          SdkModificator sdkModificator = sdk1.getSdkModificator();
          sdkModificator.setName(sdkNamePrefix + sdk1.getName().substring(sdkNamePrefix.length()));
          sdkModificator.commitChanges();

          // Rename the JDK that goes along with this SDK.
          AndroidSdkAdditionalData additionalData = AndroidSdks.getInstance().getAndroidSdkAdditionalData(sdk1);
          if (additionalData != null) {
            Sdk jdk = additionalData.getJavaSdk();
            if (jdk != null) {
              sdkModificator = jdk.getSdkModificator();
              sdkModificator.setName(DEFAULT_JDK_NAME);
              sdkModificator.commitChanges();
            }
          }

          // Fill out any missing build APIs for this new SDK.
          ideSdks.createAndroidSdkPerAndroidTarget(androidSdkPath);
        }
      }
    });
  }

  /**
   * Removes duplicate Android SDKs that could be created due to b/142005646.
   */
  private static void repairDuplicateAndroidSdks() {
    ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
    Sdk[] sdks = jdkTable.getAllJdks();
    if (sdks.length <= 1) {
      return;
    }

    Multimap<List<String>, Sdk> androidSdksByClasses = ArrayListMultimap.create();
    for (Sdk sdk : sdks) {
      if (sdk.getSdkType().getName().equals(AndroidSdkType.SDK_NAME)) {
        RootProvider rootProvider = sdk.getRootProvider();
        String[] urls = rootProvider.getUrls(OrderRootType.CLASSES);
        androidSdksByClasses.put(Arrays.asList(urls), sdk);
      }
    }
    if (androidSdksByClasses.size() == androidSdksByClasses.keySet().size()) {
      return; // No duplicates to remove.
    }

    for (List<String> classes : androidSdksByClasses.keySet()) {
      Collection<Sdk> duplicateSdks = androidSdksByClasses.get(classes);
      if (duplicateSdks.size() > 1) {
        ApplicationManager.getApplication().runWriteAction(() -> {
          boolean firstSkipped = false;
          for (Sdk sdk : duplicateSdks) {
            if (firstSkipped) {
              jdkTable.removeJdk(sdk);
            }
            firstSkipped = true;
          }
        });
      }
    }
  }

  private static void checkAndroidSdkHome() {
    try {
      AndroidLocation.checkAndroidSdkHome();
    }
    catch (AndroidLocation.AndroidLocationException e) {
      addStartupWarning(e.getMessage(), null);
    }
  }

  @Nullable
  private static Sdk findFirstAndroidSdk() {
    List<Sdk> sdks = AndroidSdks.getInstance().getAllAndroidSdks();
    if (!sdks.isEmpty()) {
      return sdks.get(0);
    }
    return null;
  }

  @Nullable
  private static File getAndroidSdkPath() {
    return AndroidSdkInitializer.findOrGetAndroidSdkPath();
  }

  private static void checkAndSetAndroidSdkSources() {
    for (Sdk sdk : AndroidSdks.getInstance().getAllAndroidSdks()) {
      checkAndSetSources(sdk);
    }
  }

  private static void checkAndSetSources(@NotNull Sdk sdk) {
    VirtualFile[] storedSources = sdk.getRootProvider().getFiles(OrderRootType.SOURCES);
    if (storedSources.length > 0) {
      return;
    }

    AndroidPlatform platform = AndroidPlatform.getInstance(sdk);
    if (platform != null) {
      SdkModificator sdkModificator = sdk.getSdkModificator();
      IAndroidTarget target = platform.getTarget();
      AndroidSdks.getInstance().findAndSetPlatformSources(target, sdkModificator);
      sdkModificator.commitChanges();
    }
  }
}
