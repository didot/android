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
package com.android.tools.idea.templates;

import static com.android.SdkConstants.FD_ADDONS;
import static com.android.SdkConstants.FD_EXTRAS;
import static com.android.SdkConstants.FD_GRADLE_WRAPPER;
import static com.android.SdkConstants.FD_TEMPLATES;
import static com.android.SdkConstants.FN_GRADLE_WRAPPER_UNIX;
import static com.android.tools.idea.flags.StudioFlags.COMPOSE_WIZARD_TEMPLATES;
import static com.android.tools.idea.npw.project.AndroidPackageUtils.getModuleTemplates;
import static com.android.tools.idea.npw.project.AndroidPackageUtils.getPackageForPath;
import static com.android.tools.idea.templates.Template.TEMPLATE_XML_NAME;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static java.util.stream.Collectors.toMap;

import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.Slow;
import com.android.prefs.AndroidLocation;
import com.android.repository.Revision;
import com.android.repository.io.FileOpUtils;
import com.android.tools.idea.actions.NewAndroidComponentAction;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.model.ProjectSyncInvoker;
import com.android.tools.idea.npw.model.RenderTemplateModel;
import com.android.tools.idea.npw.template.ChooseActivityTypeStep;
import com.android.tools.idea.npw.template.ChooseFragmentTypeStep;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.templates.TemplateMetadata.TemplateConstraint;
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.android.utils.XmlUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import com.intellij.ide.IdeView;
import com.intellij.ide.actions.NonEmptyActionGroup;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.templates.github.ZipUtil;
import icons.AndroidIcons;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;

/**
 * Handles locating templates and providing template metadata
 */
public class TemplateManager {
  private static final Logger LOG = Logger.getInstance(TemplateManager.class);

  /**
   * A directory relative to application home folder where we can find an extra template folder. This lets us ship more up-to-date
   * templates with the application instead of waiting for SDK updates.
   */
  private static final String BUNDLED_TEMPLATE_PATH = "/plugins/android/lib/templates";
  private static final String[] DEVELOPMENT_TEMPLATE_PATHS = {"/../../tools/base/templates", "/android/tools-base/templates", "/community/android/tools-base/templates"};
  private static final String EXPLODED_AAR_PATH = "build/intermediates/exploded-aar";

  public static final String CATEGORY_OTHER = "Other";
  public static final String CATEGORY_ACTIVITY = "Activity";
  public static final String CATEGORY_FRAGMENT = "Fragment";
  public static final String CATEGORY_AUTOMOTIVE = "Automotive";
  public static final String CATEGORY_COMPOSE = "Compose";
  private static final String ACTION_ID_PREFIX = "template.create.";
  private static final Set<String> EXCLUDED_CATEGORIES = ImmutableSet.of("Application", "Applications");
  public static final Set<String> EXCLUDED_TEMPLATES = ImmutableSet.of();
  private static final String TEMPLATE_ZIP_NAME = "templates.zip";

  /**
   * Cache for {@link #getTemplateMetadata(File)}
   */
  private Map<File, TemplateMetadata> myTemplateMap;

  /** Lock protecting access to {@link #myCategoryTable} */
  private final Object CATEGORY_TABLE_LOCK = new Object();

  /** Table mapping (Category, Template Name) -> Template File */
  @GuardedBy("CATEGORY_TABLE_LOCK")
  private Table<String, String, File> myCategoryTable;

  /**
   * Cache location for templates pulled from exploded-aars
   */
  private File myAarCache;

  private static TemplateManager ourInstance = new TemplateManager();
  private DefaultActionGroup myTopGroup;

  private TemplateManager() {
  }

  public static TemplateManager getInstance() {
    return ourInstance;
  }

  /**
   * @return the root folder containing templates
   */
  @Nullable
  public static File getTemplateRootFolder() {
    String homePath = toSystemIndependentName(PathManager.getHomePath());
    // Release build?
    VirtualFile root = LocalFileSystem.getInstance().findFileByPath(toSystemIndependentName(homePath + BUNDLED_TEMPLATE_PATH));
    if (root == null) {
      // Development build?
      for (String path : DEVELOPMENT_TEMPLATE_PATHS) {
        root = LocalFileSystem.getInstance().findFileByPath(toSystemIndependentName(homePath + path));

        if (root != null) {
          break;
        }
      }
    }
    if (root != null) {
      File rootFile = VfsUtilCore.virtualToIoFile(root);
      if (templateRootIsValid(rootFile)) {
        return rootFile;
      }
    }

    return null;
  }

  /**
   * @return A list of root folders containing extra templates
   */
  @NotNull
  public static List<File> getExtraTemplateRootFolders() {
    List<File> folders = getUserDefinedTemplateRootFolders();
    folders.addAll(getAuxTemplateRootFolders());
    return folders;
  }

  @NotNull
  private static List<File> getUserDefinedTemplateRootFolders() {
    List<File> folders = new ArrayList<>();

    String homeFolder = AndroidLocation.getFolderWithoutWrites();
    if (homeFolder != null) {
      // Look in $userhome/.android/templates
      File templatesFolder = new File(homeFolder, FD_TEMPLATES);
      if (templatesFolder.isDirectory()) {
        Collections.addAll(folders, templatesFolder);
      }
    }
    return folders;
  }

  @NotNull
  private static List<File> getAuxTemplateRootFolders() {
    List<File> folders = new ArrayList<>();

    // Check in various locations in the SDK
    AndroidSdkData sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk();
    if (sdkData != null) {
      File location = sdkData.getLocation();

      // Look in SDK/extras/*
      File extras = new File(location, FD_EXTRAS);
      if (extras.isDirectory()) {
        for (File vendor : listFiles(extras)) {
          if (!vendor.isDirectory()) {
            continue;
          }
          for (File pkg : listFiles(vendor)) {
            if (pkg.isDirectory()) {
              File folder = new File(pkg, FD_TEMPLATES);
              if (folder.isDirectory()) {
                folders.add(folder);
              }
            }
          }
        }

        // Legacy
        File folder = new File(extras, FD_TEMPLATES);
        if (folder.isDirectory()) {
          folders.add(folder);
        }
      }

      // Look in SDK/add-ons
      File addOns = new File(location, FD_ADDONS);
      if (addOns.isDirectory()) {
        for (File addOn : listFiles(addOns)) {
          if (!addOn.isDirectory()) {
            continue;
          }
          File folder = new File(addOn, FD_TEMPLATES);
          if (folder.isDirectory()) {
            folders.add(folder);
          }
        }
      }
    }

    // Look for source tree files
    String homePath = toSystemIndependentName(PathManager.getHomePath());
    // Release build?
    VirtualFile root = LocalFileSystem.getInstance().findFileByPath(toSystemIndependentName(homePath + BUNDLED_TEMPLATE_PATH));
    if (root == null) {
      // Development build?
      for (String path : DEVELOPMENT_TEMPLATE_PATHS) {
        root = LocalFileSystem.getInstance().findFileByPath(toSystemIndependentName(homePath + path));

        if (root != null) {
          break;
        }
      }
    }

    if (root == null) {
      // error message tailored for release build file layout
      LOG.error("Templates not found in: " + homePath + BUNDLED_TEMPLATE_PATH +
                " or " + homePath + Arrays.toString(DEVELOPMENT_TEMPLATE_PATHS));
    } else {
      File templateDir = new File(root.getCanonicalPath()).getAbsoluteFile();
      if (templateDir.isDirectory()) {
        folders.add(templateDir);
      }
    }
    return folders;
  }

  /**
   * Returns all the templates with the given prefix
   *
   * @param folder the folder prefix
   * @return the available templates
   */
  @NotNull
  public List<File> getTemplates(@NotNull String folder) {
    List<File> templates = new ArrayList<>();
    Map<String, File> templateNames = Maps.newHashMap();
    File root = getTemplateRootFolder();
    if (root != null) {
      File[] files = new File(root, folder).listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.isDirectory() && (new File(file, TEMPLATE_XML_NAME)).exists()) { // Avoid .DS_Store etc, & non Freemarker templates
            templates.add(file);
            templateNames.put(file.getName(), file);
          }
        }
      }
    }

    // Add in templates from extras/ as well.
    for (File extra : getExtraTemplateRootFolders()) {
      for (File file : listFiles(new File(extra, folder))) {
        if (file.isDirectory() && (new File(file, TEMPLATE_XML_NAME)).exists()) {
          File replaces = templateNames.get(file.getName());
          if (replaces != null) {
            int compare = compareTemplates(replaces, file);
            if (compare > 0) {
              int index = templates.indexOf(replaces);
              if (index != -1) {
                templates.set(index, file);
              }
              else {
                templates.add(file);
              }
            }
          }
          else {
            templates.add(file);
          }
        }
      }
    }

    // Sort by file name (not path as is File's default)
    if (templates.size() > 1) {
      Collections.sort(templates, Comparator.comparing(File::getName));
    }

    return templates;
  }
  @NotNull
  public List<File> getTemplateDirectoriesFromAars(@Nullable Project project) {
    List<File> templateDirectories = Lists.newArrayList();
    if (project != null && project.getBaseDir() != null) {
      if (myAarCache == null) {
        String prefix = project.getName();
        String suffix = "aar_cache";
        try {
          myAarCache = FileUtil.createTempDirectory(prefix, suffix);
        }
        catch (IOException e) {
          LOG.error(String.format("Problem trying to create temp directory with prefix: '%1$s' suffix: '%2$s' path: '%3$s'",
                                  prefix, suffix, FileUtil.getTempDirectory()), e);
          return templateDirectories;
        }
      }
      File aarRoot = new File(project.getBasePath(), FileUtil.toSystemDependentName(EXPLODED_AAR_PATH));
      if (aarRoot.isDirectory()) {
        for (File artifactPackage : listFiles(aarRoot)) {
          if (artifactPackage.isDirectory() && !artifactPackage.isHidden()) {
            for (File artifactName : listFiles(artifactPackage)) {
              if (artifactName.isDirectory() && !artifactName.isHidden()) {
                templateDirectories.addAll(getHighestVersionedTemplateRoot(artifactName));
              }
            }
          }
        }
      }
    }
    return templateDirectories;
  }

  @NotNull
  private List<File> getHighestVersionedTemplateRoot(@NotNull File artifactNameRoot) {
    List<File> templateDirectories = Lists.newArrayList();
    File highestVersionDir = null;
    Revision highestVersionNumber = null;
    for (File versionDir : listFiles(artifactNameRoot)) {
      if (!versionDir.isDirectory() || versionDir.isHidden()) {
        continue;
      }
      // Find the highest version of this AAR
      Revision revision;
      try {
        revision = Revision.parseRevision(versionDir.getName());
      } catch (NumberFormatException e) {
        // Revision was not parse-able, consider it to be the lowest version revision
        revision = Revision.NOT_SPECIFIED;
      }
      if (highestVersionNumber == null || revision.compareTo(highestVersionNumber) > 0) {
        highestVersionNumber = revision;
        highestVersionDir = versionDir;
      }
    }
    if (highestVersionDir != null) {
      String name = artifactNameRoot.getName() + "-" + highestVersionNumber.toString();
      File inflated = new File(myAarCache, name);
      if (!inflated.isDirectory()) {
        // Only unzip once
        File zipFile = new File(highestVersionDir, TEMPLATE_ZIP_NAME);
        if (zipFile.isFile()) {
          try {
            ZipUtil.unzip(null, inflated, zipFile, null, null, true);
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      }
      if (inflated.isDirectory()) {
        templateDirectories.add(inflated);
      }
    }
    return templateDirectories;
  }

  /**
   * @return a list of template files that declare the given category.
   */
  @NotNull
  public List<File> getTemplatesInCategory(@NotNull String category) {
    synchronized (CATEGORY_TABLE_LOCK) {
      Table<String, String, File> table = getCategoryTable();
      if (table.containsRow(category)) {
        return Lists.newArrayList(table.row(category).values());
      }
      else {
        return Lists.newArrayList();
      }
    }
  }

  @Slow
  @Nullable
  public ActionGroup getTemplateCreationMenu(@Nullable Project project) {
    refreshDynamicTemplateMenu(project);
    return myTopGroup;
  }

  @Slow
  public void refreshDynamicTemplateMenu(@Nullable Project project) {
    synchronized (CATEGORY_TABLE_LOCK) {
      if (myTopGroup == null) {
        myTopGroup = new DefaultActionGroup("AndroidTemplateGroup", false);
      } else {
        myTopGroup.removeAll();
      }
      myTopGroup.addSeparator();
      ActionManager am = ActionManager.getInstance();

      reloadCategoryTable(project); // Force reload

      for (final String category : getCategoryTable().rowKeySet()) {
        if (EXCLUDED_CATEGORIES.contains(category)) {
          continue;
        }
        // Create the menu group item
        NonEmptyActionGroup categoryGroup = new NonEmptyActionGroup() {
          @Override
          public void update(@NotNull AnActionEvent e) {
            updateAction(e, category, getChildrenCount() > 0, false);
          }
        };
        categoryGroup.setPopup(true);
        fillCategory(categoryGroup, category, am);
        myTopGroup.add(categoryGroup);
        setPresentation(category, categoryGroup);
      }
    }
  }

  private static void updateAction(AnActionEvent event, String text, boolean visible, boolean disableIfNotReady) {
    IdeView view = event.getData(LangDataKeys.IDE_VIEW);
    final Module module = event.getData(LangDataKeys.MODULE);
    final AndroidFacet facet = module != null ? AndroidFacet.getInstance(module) : null;
    Presentation presentation = event.getPresentation();
    boolean isProjectReady = facet != null && AndroidModel.get(facet) != null;
    presentation.setText(text + (isProjectReady ? "" : " (Project not ready)"));
    presentation.setVisible(visible && view != null && facet != null && AndroidModel.isRequired(facet));
    presentation.setEnabled(!disableIfNotReady || isProjectReady);
  }

  @GuardedBy("CATEGORY_TABLE_LOCK")
  private void fillCategory(NonEmptyActionGroup categoryGroup, final String category, ActionManager am) {
    Map<String, File> categoryRow = myCategoryTable.row(category);
    if (CATEGORY_ACTIVITY.equals(category)) {
      AnAction galleryAction = new AnAction() {
        @Override
        public void update(@NotNull AnActionEvent e) {
          updateAction(e, "Gallery...", true, true);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          showWizardDialog(e, CATEGORY_ACTIVITY,
                           AndroidBundle.message("android.wizard.activity.add", FormFactor.MOBILE.id),
                          "New Android Activity");
        }
      };
      categoryGroup.add(galleryAction);
      categoryGroup.addSeparator();
      setPresentation(category, galleryAction);
    }

    if (StudioFlags.NPW_SHOW_FRAGMENT_GALLERY.get() && category.equals(CATEGORY_FRAGMENT)) {
      AnAction fragmentGalleryAction = new AnAction() {
        @Override
        public void update(@NotNull AnActionEvent e) {
          updateAction(e, "Gallery...", true, true);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          showWizardDialog(e, CATEGORY_FRAGMENT,
                           AndroidBundle.message("android.wizard.fragment.add", FormFactor.MOBILE.id),
                           "New Android Fragment");
        }
      };
      categoryGroup.add(fragmentGalleryAction);
      categoryGroup.addSeparator();
      setPresentation(category, fragmentGalleryAction);
    }

    // Automotive category includes Car category templates. If a template is in both categories, use the automotive one.
    Map<String, String> templateCategoryMap = categoryRow.keySet().stream().collect(toMap(it -> it, it -> category));

    for (Map.Entry<String, String> templateNameAndCategory : templateCategoryMap.entrySet()) {
      String templateName = templateNameAndCategory.getKey();
      String templateCategory = templateNameAndCategory.getValue();
      if (EXCLUDED_TEMPLATES.contains(templateName)) {
        continue;
      }
      TemplateMetadata metadata = getTemplateMetadata(myCategoryTable.get(templateCategory, templateName));
      int minSdkVersion = metadata == null ? 0 : metadata.getMinSdk();
      int minBuildSdkApi = metadata == null ? 0 : metadata.getMinBuildApi();
      EnumSet<TemplateConstraint> templateConstraints = metadata == null ? EnumSet.noneOf(
        TemplateConstraint.class) : metadata.getConstraints();
      File templateFile = myCategoryTable.row(templateCategory).get(templateName);
      NewAndroidComponentAction templateAction = new NewAndroidComponentAction(
        category, templateName, minSdkVersion, minBuildSdkApi, templateConstraints, templateFile);
      String actionId = ACTION_ID_PREFIX + templateCategory + templateName;
      am.replaceAction(actionId, templateAction);
      categoryGroup.add(templateAction);
    }
  }

  private static void showWizardDialog(@NotNull AnActionEvent e, String category, String commandName, String dialogTitle) {
    ProjectSyncInvoker projectSyncInvoker = new ProjectSyncInvoker.DefaultProjectSyncInvoker();

    DataContext dataContext = e.getDataContext();
    Module module = LangDataKeys.MODULE.getData(dataContext);
    assert module != null;

    VirtualFile targetFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    assert targetFile != null;

    VirtualFile targetDirectory = targetFile;
    if (!targetDirectory.isDirectory()) {
      targetDirectory = targetFile.getParent();
      assert targetDirectory != null;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null && AndroidModel.get(facet) != null;

    List<NamedModuleTemplate> moduleTemplates = getModuleTemplates(facet, targetDirectory);
    assert (!moduleTemplates.isEmpty());

    String initialPackageSuggestion = getPackageForPath(facet, moduleTemplates, targetDirectory);
    Project project = facet.getModule().getProject();

    RenderTemplateModel renderModel = RenderTemplateModel.fromFacet(
      facet, initialPackageSuggestion, moduleTemplates.get(0),
      commandName, projectSyncInvoker, true);

    SkippableWizardStep chooseTypeStep;
    if (category.equals(CATEGORY_ACTIVITY)) {
      chooseTypeStep =
        new ChooseActivityTypeStep(renderModel, FormFactor.MOBILE, targetDirectory);
    } else if (category.equals(CATEGORY_FRAGMENT)) {
      chooseTypeStep =
        new ChooseFragmentTypeStep(renderModel, FormFactor.MOBILE, targetDirectory);
    } else {
      throw new RuntimeException("Invalid category name: " + category);
    }
    ModelWizard wizard = new ModelWizard.Builder().addStep(chooseTypeStep).build();

    new StudioWizardDialogBuilder(wizard, dialogTitle).build().show();
  }

  private static void setPresentation(String category, AnAction categoryGroup) {
    Presentation presentation = categoryGroup.getTemplatePresentation();
    presentation.setIcon(AndroidIcons.Android);
    presentation.setText(category);
  }

  @GuardedBy("CATEGORY_TABLE_LOCK")
  private Table<String, String, File> getCategoryTable() {
    if (myCategoryTable == null) {
      reloadCategoryTable(null);
    }

    return myCategoryTable;
  }

  @Slow
  @GuardedBy("CATEGORY_TABLE_LOCK")
  private void reloadCategoryTable(@Nullable Project project) {
    if (myTemplateMap != null) {
      myTemplateMap.clear();
    }
    myCategoryTable = TreeBasedTable.create();
    File templateRootFolder = getTemplateRootFolder();
    if (templateRootFolder != null) {
      for (File categoryDirectory : listFiles(templateRootFolder)) {
        for (File newTemplate : listFiles(categoryDirectory)) {
          addTemplateToTable(newTemplate, false);
        }
      }
    }

    for (File rootDirectory : getUserDefinedTemplateRootFolders()) {
      for (File categoryDirectory : listFiles(rootDirectory)) {
        for (File newTemplate : listFiles(categoryDirectory)) {
          addTemplateToTable(newTemplate, true);
        }
      }
    }

    for (File rootDirectory : getAuxTemplateRootFolders()) {
      for (File categoryDirectory : listFiles(rootDirectory)) {
        for (File newTemplate : listFiles(categoryDirectory)) {
          addTemplateToTable(newTemplate, false);
        }
      }
    }

    for (File aarDirectory : getTemplateDirectoriesFromAars(project)) {
      for (File newTemplate : listFiles(aarDirectory)) {
        addTemplateToTable(newTemplate, false);
      }
    }
  }

  @GuardedBy("CATEGORY_TABLE_LOCK")
  private void addTemplateToTable(@NotNull File newTemplate, boolean userDefinedTemplate) {
    TemplateMetadata newMetadata = getTemplateMetadata(newTemplate, userDefinedTemplate);
    if (newMetadata != null) {
      String title = newMetadata.getTitle();
      if (title == null || (newMetadata.getCategory() == null &&
                            myCategoryTable.columnKeySet().contains(title) &&
                            myCategoryTable.get(CATEGORY_OTHER, title) == null)) {
        // If this template is uncategorized, and we already have a template of this name that has a category,
        // that is NOT "Other," then ignore this new template since it's undoubtedly older.
        return;
      }
      String category = newMetadata.getCategory() != null ? newMetadata.getCategory() : CATEGORY_OTHER;
      if (CATEGORY_COMPOSE.equals(category) && !COMPOSE_WIZARD_TEMPLATES.get()) {
        return;
      }
      File existingTemplate = myCategoryTable.get(category, title);
      if (existingTemplate == null || compareTemplates(existingTemplate, newTemplate) > 0) {
        myCategoryTable.put(category, title, newTemplate);
      }
    }
  }

  /**
   * Compare two files, and return the one with the HIGHEST revision, and if
   * the same, most recently modified
   */
  private int compareTemplates(@NotNull File file1, @NotNull File file2) {
    TemplateMetadata template1 = getTemplateMetadata(file1);
    TemplateMetadata template2 = getTemplateMetadata(file2);

    if (template1 == null) {
      return 1;
    }
    else if (template2 == null) {
      return -1;
    }
    else {
      int delta = template2.getRevision() - template1.getRevision();
      if (delta == 0) {
        delta = (int)(file2.lastModified() - file1.lastModified());
      }
      return delta;
    }
  }

  @Nullable
  public File getTemplateFile(@Nullable String category, @Nullable String templateName) {
    synchronized (CATEGORY_TABLE_LOCK) {
      return getCategoryTable().get(category, templateName);
    }
  }

  /**
   * Given a root path, parse the target template.xml file found there and return the Android data
   * contained within. This data will be cached and reused on subsequent requests.
   *
   * @return The Android metadata contained in the template.xml file, or {@code null} if there was
   * any problem collecting it, such as a parse failure or invalid path, etc.
   */
  @Nullable
  public TemplateMetadata getTemplateMetadata(@NotNull File templateRoot) {
    return getTemplateMetadata(templateRoot, false);
  }

  @Nullable
  private TemplateMetadata getTemplateMetadata(@NotNull File templateRoot, boolean userDefinedTemplate) {
    if (myTemplateMap != null) {
      TemplateMetadata metadata = myTemplateMap.get(templateRoot);
      if (metadata != null) {
        return metadata;
      }
    }
    else {
      myTemplateMap = Maps.newHashMap();
    }

    try {
      File templateFile = new File(templateRoot, TEMPLATE_XML_NAME);
      if (templateFile.isFile()) {
        Document doc = XmlUtils.parseUtfXmlFile(templateFile, true);
        if (doc != null && doc.getDocumentElement() != null) {
          TemplateMetadata metadata = new TemplateMetadata(doc);
          myTemplateMap.put(templateRoot, metadata);
          return metadata;
        }
      }
    }
    catch (Exception e) {
      if (userDefinedTemplate) {
        LOG.warn(e);
      }
    }

    return null;
  }

  public static File getWrapperLocation(@NotNull File templateRootFolder) {
    return new File(templateRootFolder, FD_GRADLE_WRAPPER);

  }

  public static boolean templateRootIsValid(@NotNull File templateRootFolder) {
    return new File(getWrapperLocation(templateRootFolder), FN_GRADLE_WRAPPER_UNIX).exists();
  }

  private static File[] listFiles(@NotNull File root) {
    return FileOpUtils.create().listFiles(root);
  }
}
