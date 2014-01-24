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

import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;

import java.io.File;
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

  /**
   * Cache for {@link #getTemplate()}
   */
  private Map<File, TemplateMetadata> myTemplateMap;

  private static TemplateManager ourInstance = new TemplateManager();

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
   * @return the root folder containing extra templates
   */
  @NotNull
  public static List<File> getExtraTemplateRootFolders() {
    List<File> folders = new ArrayList<File>();
    String location = null;
    if (location != null) {
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
    }

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
      File[] files = new File(extra, folder).listFiles();
      if (files != null) {
        for (File file : files) {
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
