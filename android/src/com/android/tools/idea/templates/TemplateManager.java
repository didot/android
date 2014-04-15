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

import com.android.tools.idea.actions.NewAndroidComponentAction;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import com.google.common.io.Files;
import com.intellij.ide.actions.NonEmptyActionGroup;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.templates.Template.TEMPLATE_XML_NAME;

/**
 * Handles locating templates and providing template metadata
 */
public class TemplateManager {
  private static final Logger LOG = Logger.getInstance("#" + TemplateManager.class.getName());

  /**
   * A directory relative to application home folder where we can find an extra template folder. This lets us ship more up-to-date
   * templates with the application instead of waiting for SDK updates.
   */
  private static final String BUNDLED_TEMPLATE_PATH = "/plugins/android/lib/templates";
  private static final String[] DEVELOPMENT_TEMPLATE_PATHS = {"/../../tools/base/templates", "/android/tools-base/templates"};

  public static final String CATEGORY_OTHER = "Other";
  private static final String ACTION_ID_PREFIX = "template.create.";
  private static final boolean USE_SDK_TEMPLATES = false;
  private static final Set<String> EXCLUDED_CATEGORIES = ImmutableSet.of("Application", "Applications");

  /**
   * Cache for {@link #getTemplate(File)}
   */
  private Map<File, TemplateMetadata> myTemplateMap;

  /** Table mapping (Category, Template Name) -> Template File */
  private Table<String, String, File> myCategoryTable;

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
    String homePath = FileUtil.toSystemIndependentName(PathManager.getHomePath());
    // Release build?
    VirtualFile root = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(homePath + BUNDLED_TEMPLATE_PATH));
    if (root == null) {
      // Development build?
      for (String path : DEVELOPMENT_TEMPLATE_PATHS) {
        root = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(homePath + path));

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

    // Fall back to SDK template root
    AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (sdkData != null) {
      File location = sdkData.getLocation();
      File folder = new File(location, FD_TOOLS + File.separator + FD_TEMPLATES);
      if (folder.isDirectory()) {
        return folder;
      }
    }

    return null;
  }

  /**
   * @return A list of root folders containing extra templates
   */
  @NotNull
  public static List<File> getExtraTemplateRootFolders() {
    List<File> folders = new ArrayList<File>();

    // Check in various locations in the SDK
    AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (sdkData != null) {
      File location = sdkData.getLocation();

      if (USE_SDK_TEMPLATES) {
        // Look in SDK/tools/templates
        File toolsTemplatesFolder = new File(location, FileUtil.join(FD_TOOLS, FD_TEMPLATES));
        if (toolsTemplatesFolder.isDirectory()) {
          File[] templateRoots = toolsTemplatesFolder.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
              return pathname.isDirectory();
            }
          });
          if (templateRoots != null) {
            Collections.addAll(folders, templateRoots);
          }
        }
      }

      // Look in SDK/extras/*
      File extras = new File(location, FD_EXTRAS);
      if (extras.isDirectory()) {
        for (File vendor : TemplateUtils.listFiles(extras)) {
          if (!vendor.isDirectory()) {
            continue;
          }
          for (File pkg : TemplateUtils.listFiles(vendor)) {
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
        for (File addOn : TemplateUtils.listFiles(addOns)) {
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
    String homePath = FileUtil.toSystemIndependentName(PathManager.getHomePath());
    // Release build?
    VirtualFile root = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(homePath + BUNDLED_TEMPLATE_PATH));
    if (root == null) {
      // Development build?
      for (String path : DEVELOPMENT_TEMPLATE_PATHS) {
        root = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(homePath + path));

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
    List<File> templates = new ArrayList<File>();
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
      for (File file : TemplateUtils.listFiles(new File(extra, folder))) {
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
      Collections.sort(templates, new Comparator<File>() {
        @Override
        public int compare(File file1, File file2) {
          return file1.getName().compareTo(file2.getName());
        }
      });
    }

    return templates;
  }

  @Nullable
  public ActionGroup getTemplateCreationMenu() {
    refreshDynamicTemplateMenu();
    return myTopGroup;
  }

  public void refreshDynamicTemplateMenu() {
    if (myTopGroup == null) {
      myTopGroup = new DefaultActionGroup("AndroidTemplateGroup", false);
    } else {
      myTopGroup.removeAll();
    }
    ActionManager am = ActionManager.getInstance();
    for (String category : getCategoryTable(true).rowKeySet()) {
      if (EXCLUDED_CATEGORIES.contains(category)) {
        continue;
      }
      // Create the menu group item
      NonEmptyActionGroup categoryGroup = new NonEmptyActionGroup() {
        @Override
        public void update(AnActionEvent e) {
          final Module module = LangDataKeys.MODULE.getData(e.getDataContext());
          final AndroidFacet facet = module != null ? AndroidFacet.getInstance(module) : null;
          Presentation presentation = e.getPresentation();
          presentation.setVisible(getChildrenCount() > 0 && facet != null && facet.isGradleProject());
        }
      };
      categoryGroup.setPopup(true);
      Presentation presentation = categoryGroup.getTemplatePresentation();
      presentation.setIcon(AndroidIcons.Android);
      presentation.setText(category);

      Map<String, File> categoryRow = myCategoryTable.row(category);
      for (String templateName : categoryRow.keySet()) {
        NewAndroidComponentAction templateAction = new NewAndroidComponentAction(category, templateName);
        String actionId = ACTION_ID_PREFIX + category + templateName;
        am.unregisterAction(actionId);
        am.registerAction(actionId, templateAction);
        categoryGroup.add(templateAction);
      }
      myTopGroup.add(categoryGroup);
    }
  }

  private Table<String, String, File> getCategoryTable(boolean forceReload) {
    if (myCategoryTable== null || forceReload) {
      myCategoryTable = TreeBasedTable.create();
      for (File categoryDirectory : TemplateUtils.listFiles(getTemplateRootFolder())) {
        for (File newTemplate : TemplateUtils.listFiles(categoryDirectory)) {
          addTemplateToTable(newTemplate);
        }
      }

      for (File rootDirectory : getExtraTemplateRootFolders()) {
        for (File newTemplate : TemplateUtils.listFiles(rootDirectory)) {
          addTemplateToTable(newTemplate);
        }
      }
    }

    return myCategoryTable;
  }

  private void addTemplateToTable(@NotNull File newTemplate) {
    TemplateMetadata newMetadata = getTemplate(newTemplate);
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
    TemplateMetadata template1 = getTemplate(file1);
    TemplateMetadata template2 = getTemplate(file2);

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
    return getCategoryTable(false).get(category, templateName);
  }

  @Nullable
  public TemplateMetadata getTemplate(@Nullable String category, @Nullable String templateName) {
    File templateDir = getTemplateFile(category, templateName);
    return templateDir != null ? getTemplate(templateDir) : null;
  }

  @Nullable
  public TemplateMetadata getTemplate(@NotNull File templateDir) {
    if (myTemplateMap != null) {
      TemplateMetadata metadata = myTemplateMap.get(templateDir);
      if (metadata != null) {
        return metadata;
      }
    }
    else {
      myTemplateMap = Maps.newHashMap();
    }

    try {
      File templateFile = new File(templateDir, TEMPLATE_XML_NAME);
      if (templateFile.isFile()) {
        String xml = Files.toString(templateFile, Charsets.UTF_8);
        Document doc = XmlUtils.parseDocumentSilently(xml, true);
        if (doc != null && doc.getDocumentElement() != null) {
          TemplateMetadata metadata = new TemplateMetadata(doc);
          myTemplateMap.put(templateDir, metadata);
          return metadata;
        }
      }
    }
    catch (IOException e) {
      LOG.warn(e);
    }

    return null;
  }

  /**
   * Do a sanity check to see if we have templates that look compatible, otherwise we get really strange problems. The existence
   * of a gradle wrapper in the templates directory is a good sign.
   * @return whether the templates pass the check or not
   */
  public static boolean templatesAreValid() {
    try {
      File templateRootFolder = getTemplateRootFolder();
      if (templateRootFolder == null) {
        return false;
      }
      return templateRootIsValid(templateRootFolder);
    }
    catch (Exception e) {
      return false;
    }
  }

  public static File getWrapperLocation(@NotNull File templateRootFolder) {
    return new File(templateRootFolder, FileUtil.join("gradle", "wrapper"));

  }

  public static boolean templateRootIsValid(@NotNull File templateRootFolder) {
    return new File(getWrapperLocation(templateRootFolder), "gradlew").exists();
  }
}
