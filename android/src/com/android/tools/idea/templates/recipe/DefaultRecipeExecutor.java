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
package com.android.tools.idea.templates.recipe;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.templates.*;
import com.android.tools.idea.templates.FreemarkerUtils.TemplatePostProcessor;
import com.android.tools.idea.templates.FreemarkerUtils.TemplateProcessingException;
import com.android.tools.idea.templates.FreemarkerUtils.TemplateUserVisibleException;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.util.Projects.isBuildWithGradle;
import static com.android.tools.idea.templates.FreemarkerUtils.processFreemarkerTemplate;
import static com.android.tools.idea.templates.TemplateUtils.*;

/**
 * Executor support for recipe instructions.
 */
final class DefaultRecipeExecutor implements RecipeExecutor {

  /**
   * The settings.gradle lives at project root and points gradle at the build files for individual modules in their subdirectories
   */
  private static final String GRADLE_PROJECT_SETTINGS_FILE = "settings.gradle";

  private final FindReferencesRecipeExecutor myReferences;
  private final RenderingContext myContext;
  private final RecipeIO myIO;
  private final ReadonlyStatusHandler myReadonlyStatusHandler;
  private boolean myNeedsGradleSync;

  public DefaultRecipeExecutor(@NotNull RenderingContext context, boolean dryRun) {
    myReferences = new FindReferencesRecipeExecutor(context);
    myContext = context;
    myIO = dryRun ? new DryRunRecipeIO() : new RecipeIO();
    myReadonlyStatusHandler = ReadonlyStatusHandler.getInstance(context.getProject());
  }

  /**
   * Add a library dependency into the project.
   */
  @Override
  public void addDependency(@NotNull String mavenUrl) {
    myReferences.addDependency(mavenUrl);
    //noinspection unchecked
    List<String> dependencyList = (List<String>)getParamMap().get(TemplateMetadata.ATTR_DEPENDENCIES_LIST);
    dependencyList.add(mavenUrl);
  }

  @Override
  public void addFilesToOpen(@NotNull File file) {
    myReferences.addFilesToOpen(file);
  }

  private void addWarning(@NotNull String warning) {
    myContext.getWarnings().add(warning);
  }

  @NotNull
  private Map<String, Object> getParamMap() {
    return myContext.getParamMap();
  }

  @NotNull
  Configuration getFreemarker() {
    return myContext.getFreemarkerConfiguration();
  }

  /**
   * Copies the given source file into the given destination file (where the
   * source is allowed to be a directory, in which case the whole directory is
   * copied recursively)
   */
  @Override
  public void copy(@NotNull File from, @NotNull File to) {
    try {
      myReferences.copy(from, to);
      copyTemplateResource(from, to);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Instantiates the given template file into the given output file (running the freemarker
   * engine over it)
   */
  @Override
  public void instantiate(@NotNull File from, @NotNull File to) throws TemplateProcessingException {
    try {
      myReferences.instantiate(from, to);

      // For now, treat extension-less files as directories... this isn't quite right
      // so I should refine this! Maybe with a unique attribute in the template file?
      boolean isDirectory = from.getName().indexOf('.') == -1;
      if (isDirectory) {
        // It's a directory
        copyTemplateResource(from, to);
      }
      else {
        File targetFile = getTargetFile(to);
        String content = processFreemarkerTemplate(myContext, from, null);
        if (targetFile.exists()) {
          if (!compareTextFile(myContext.getProject(), targetFile, content)) {
            addFileAlreadyExistWarning(targetFile);
          }
        }
        else {
          myIO.writeFile(this, content, targetFile);
        }
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Merges the given source file into the given destination file (or it just copies it over if
   * the destination file does not exist).
   * <p/>
   * Only XML and Gradle files are currently supported.
   */
  @Override
  public void merge(@NotNull File from, @NotNull File to) throws TemplateProcessingException {
    try {
      myReferences.merge(from, to);

      String targetText = null;

      to = getTargetFile(to);
      if (!(hasExtension(to, DOT_XML) || hasExtension(to, DOT_GRADLE))) {
        throw new RuntimeException("Only XML or Gradle files can be merged at this point: " + to);
      }

      if (to.exists()) {
        if (myContext.getProject().isInitialized()) {
          VirtualFile toFile = VfsUtil.findFileByIoFile(to, true);
          final ReadonlyStatusHandler.OperationStatus status = myReadonlyStatusHandler.ensureFilesWritable(toFile);
          if (status.hasReadonlyFiles()) {
            myContext.getTargetFiles().remove(to);
            throw new TemplateUserVisibleException(String.format("Attempt to update file that is readonly: %1$s", to.getAbsolutePath()));
          }
        }
        targetText = Files.toString(to, Charsets.UTF_8);
      }

      if (targetText == null) {
        // The target file doesn't exist: don't merge, just copy
        boolean instantiate = hasExtension(from, DOT_FTL);
        if (instantiate) {
          instantiate(from, to);
        }
        else {
          copyTemplateResource(from, to);
        }
        return;
      }

      String sourceText;
      if (hasExtension(from, DOT_FTL)) {
        // Perform template substitution of the template prior to merging
        sourceText = processFreemarkerTemplate(myContext, from, null);
      }
      else {
        from = myContext.getLoader().getSourceFile(from);
        sourceText = readTextFile(from);
        if (sourceText == null) {
          return;
        }
      }

      String contents;
      if (to.getName().equals(GRADLE_PROJECT_SETTINGS_FILE)) {
        contents = RecipeMergeUtils.mergeGradleSettingsFile(sourceText, targetText);
        myNeedsGradleSync = true;
      }
      else if (to.getName().equals(SdkConstants.FN_BUILD_GRADLE)) {
        String compileSdkVersion = (String)getParamMap().get(TemplateMetadata.ATTR_BUILD_API_STRING);
        contents = GradleFileMerger.mergeGradleFiles(sourceText, targetText, myContext.getProject(), compileSdkVersion);
        myNeedsGradleSync = true;
      }
      else if (hasExtension(to, DOT_XML)) {
        contents = RecipeMergeUtils.mergeXml(myContext, sourceText, targetText, to);
      }
      else {
        throw new RuntimeException("Only XML or Gradle settings files can be merged at this point: " + to);
      }

      myIO.writeFile(this, contents, to);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    catch (TemplateException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Create a directory at the specified location (if not already present). This will also create
   * any parent directories that don't exist, as well.
   */
  @Override
  public void mkDir(@NotNull File at) {
    try {
      myIO.mkDir(getTargetFile(at));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String processTemplate(@NotNull File recipe, @NotNull TemplatePostProcessor processor) throws TemplateProcessingException {
    return FreemarkerUtils.processFreemarkerTemplate(myContext, recipe, processor);
  }

  /**
   * Update the project's gradle build file and sync, if necessary. This should only be called
   * once and after all dependencies are already added.
   */
  @Override
  public void updateAndSyncGradle() {
    // Handle dependencies
    if (getParamMap().containsKey(TemplateMetadata.ATTR_DEPENDENCIES_LIST)) {
      Object maybeDependencyList = getParamMap().get(TemplateMetadata.ATTR_DEPENDENCIES_LIST);
      if (maybeDependencyList instanceof List) {
        //noinspection unchecked
        List<String> dependencyList = (List<String>)maybeDependencyList;
        if (!dependencyList.isEmpty()) {
          try {
            mergeDependenciesIntoGradle();
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      }
    }
    Project project = myContext.getProject();
    if (myNeedsGradleSync &&
        myContext.performGradleSync() &&
        !project.isDefault() &&
        isBuildWithGradle(project)) {
      myIO.requestGradleSync(project);
    }
  }

  /**
   * Returns the absolute path to the file which will get written to.
   */
  @NotNull
  private File getTargetFile(@NotNull File file) throws IOException {
    if (file.isAbsolute()) {
      return file;
    }
    return new File(myContext.getOutputRoot(), file.getPath());
  }

  /**
   * Merge the URLs from our gradle template into the target module's build.gradle file
   */
  private void mergeDependenciesIntoGradle() throws Exception {
    File gradleBuildFile = GradleUtil.getGradleBuildFilePath(myContext.getModuleRoot());

    File templateRootFolder = TemplateManager.getTemplateRootFolder();
    assert templateRootFolder != null;

    String templateRoot = templateRootFolder.getPath();
    File gradleTemplate = new File(templateRoot, FileUtil.join("gradle", "utils", "dependencies.gradle.ftl"));
    String contents = processFreemarkerTemplate(myContext, gradleTemplate, null);
    String destinationContents = null;
    if (gradleBuildFile.exists()) {
      destinationContents = readTextFile(gradleBuildFile);
    }
    if (destinationContents == null) {
      destinationContents = "";
    }
    String compileSdkVersion = (String)getParamMap().get(TemplateMetadata.ATTR_BUILD_API_STRING);
    String result = GradleFileMerger.mergeGradleFiles(contents, destinationContents, myContext.getProject(), compileSdkVersion);
    myIO.writeFile(this, result, gradleBuildFile);
    myNeedsGradleSync = true;
  }

  /**
   * VfsUtil#copyDirectory messes up the undo stack, most likely by trying to
   * create a directory even if it already exists. This is an undo-friendly
   * replacement.
   */
  private void copyDirectory(@NotNull final VirtualFile src, @NotNull final File dest) throws IOException {
    VfsUtilCore.visitChildrenRecursively(src, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        try {
          return copyFile(file, src, dest);
        }
        catch (IOException e) {
          throw new VisitorException(e);
        }
      }
    }, IOException.class);
  }

  private void copyTemplateResource(@NotNull File from, @NotNull File to) throws IOException {
    from = myContext.getLoader().getSourceFile(from);
    to = getTargetFile(to);

    VirtualFile sourceFile = VfsUtil.findFileByIoFile(from, true);
    assert sourceFile != null : from;
    sourceFile.refresh(false, false);
    File destPath = (from.isDirectory() ? to : to.getParentFile());
    if (from.isDirectory()) {
      copyDirectory(sourceFile, destPath);
    }
    else {
      Document document = FileDocumentManager.getInstance().getDocument(sourceFile);
      if (document != null) {
        myIO.writeFile(this, document.getText(), to);
      }
      else {
        myIO.copyFile(this, sourceFile, destPath, to.getName());
      }
    }
  }

  private boolean copyFile(VirtualFile file, VirtualFile src, File destinationFile) throws IOException {
    String relativePath = VfsUtilCore.getRelativePath(file, src, File.separatorChar);
    if (relativePath == null) {
      throw new RuntimeException(String.format("%1$s is not a child of %2$s", file.getPath(), src));
    }
    if (file.isDirectory()) {
      myIO.mkDir(new File(destinationFile, relativePath));
    }
    else {
      File target = new File(destinationFile, relativePath);
      if (target.exists()) {
        if (!compareFile(myContext.getProject(), file, target)) {
          addFileAlreadyExistWarning(target);
        }
      }
      else {
        myIO.copyFile(this, file, target);
      }
    }
    return true;
  }

  private void addFileAlreadyExistWarning(@NotNull File targetFile) {
    addWarning(String.format("The following file could not be created since it already exists: %1$s", targetFile.getName()));
  }

  private static class RecipeIO {
    public void writeFile(@NotNull Object requestor, @Nullable String contents, @NotNull File to) throws IOException {
      checkedCreateDirectoryIfMissing(to.getParentFile());
      writeTextFile(this, contents, to);
    }

    public void copyFile(Object requestor, @NotNull VirtualFile file, @NotNull File toFile) throws IOException {
      VirtualFile toDir = checkedCreateDirectoryIfMissing(toFile.getParentFile());
      VfsUtilCore.copyFile(this, file, toDir);
    }

    public void copyFile(Object requestor, @NotNull VirtualFile file, @NotNull File toFileDir, @NotNull String newName)
      throws IOException {
      VirtualFile toDir = checkedCreateDirectoryIfMissing(toFileDir);
      VfsUtilCore.copyFile(requestor, file, toDir, newName);
    }

    public void mkDir(@NotNull File directory) throws IOException {
      checkedCreateDirectoryIfMissing(directory);
    }

    public void requestGradleSync(@NotNull Project project) {
      GradleProjectImporter.getInstance().requestProjectSync(project, null);
    }
  }

  private static class DryRunRecipeIO extends RecipeIO {
    @Override
    public void writeFile(@NotNull Object requestor, @Nullable String contents, @NotNull File to) throws IOException {
      checkDirectoryIsWriteable(to.getParentFile());
    }

    @Override
    public void copyFile(Object requestor, @NotNull VirtualFile file, @NotNull File toFile) throws IOException {
      checkDirectoryIsWriteable(toFile.getParentFile());
    }

    @Override
    public void copyFile(Object requestor, @NotNull VirtualFile file, @NotNull File toFileDir, @Nullable String newName)
      throws IOException {
      checkDirectoryIsWriteable(toFileDir);
    }

    @Override
    public void mkDir(@NotNull File directory) throws IOException {
      checkDirectoryIsWriteable(directory);
    }

    @Override
    public void requestGradleSync(@NotNull Project project) {
    }
  }
}
