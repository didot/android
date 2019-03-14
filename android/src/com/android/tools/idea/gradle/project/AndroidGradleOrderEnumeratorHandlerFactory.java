package com.android.tools.idea.gradle.project;

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.BaseArtifact;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.io.FilePaths;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderRootType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.execution.GradleOrderEnumeratorHandler;
import org.jetbrains.plugins.gradle.execution.GradleOrderEnumeratorHandler.FactoryImpl;
import org.jetbrains.plugins.gradle.model.ExtIdeaCompilerOutput;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.intellij.openapi.util.io.FileUtil.join;

/**
 * {@link AndroidGradleOrderEnumeratorHandlerFactory} was introduced to make
 * order entries enumeration of Android modules consistent with the Android gradle importer.
 * Android gradle importer uses first-level dependencies for android modules,
 * and each module has to "export" their dependencies to make them visible to dependent modules.
 * <p>
 * Non-android gradle modules don't have such restriction (there will always be fully resolved dependency graph as a flat list)
 * and should not be affected by the recursive enumeration.
 * Which can lead to unexpected runtime classpath and performance degradation.
 *
 * @author Vladislav.Soroka
 */
public class AndroidGradleOrderEnumeratorHandlerFactory extends FactoryImpl {
  @NotNull private static final String CLASSES_FOLDER_NAME = "classes";
  @NotNull private static final String RESOURCES_FOLDER_NAME = "resources";
  @NotNull private static final String MAIN_FOLDER_NAME = "main";
  @NotNull private static final String TEST_FOLDER_NAME = "test";
  @NotNull private static final String KOTLIN_FOLDER_NAME = "kotlin";

  @Override
  public boolean isApplicable(@NotNull Module module) {
    return IdeInfo.getInstance().isAndroidStudio() || AndroidModuleModel.get(module) != null;
  }

  @NotNull
  @Override
  public GradleOrderEnumeratorHandler createHandler(@NotNull Module module) {
    return new GradleOrderEnumeratorHandler(module) {
      @Override
      public boolean shouldAddRuntimeDependenciesToTestCompilationClasspath() {
        return false;
      }

      @Override
      public boolean shouldIncludeTestsFromDependentModulesToTestClasspath() {
        return true;
      }

      @Override
      public boolean shouldProcessDependenciesRecursively() {
        return true;
      }

      @Override
      public boolean addCustomModuleRoots(@NotNull OrderRootType type,
                                          @NotNull ModuleRootModel rootModel,
                                          @NotNull Collection<String> result,
                                          boolean includeProduction,
                                          boolean includeTests) {
        if (!type.equals(OrderRootType.CLASSES)) {
          return false;
        }

        AndroidModuleModel androidModel = AndroidModuleModel.get(rootModel.getModule());
        if (androidModel != null) {
          result.addAll(getAndroidCompilerOutputFolders(androidModel, includeProduction, includeTests));
          super.addCustomModuleRoots(type, rootModel, result, includeProduction, includeTests);
          return true;
        }

        JavaModuleModel javaModel = JavaModuleModel.get(rootModel.getModule());
        if (javaModel != null) {
          result.addAll(getJavaAndKotlinCompilerOutputFolders(javaModel, includeProduction, includeTests));
          super.addCustomModuleRoots(type, rootModel, result, includeProduction, includeTests);
          return true;
        }

        return super.addCustomModuleRoots(type, rootModel, result, includeProduction, includeTests);
      }
    };
  }

  @NotNull
  private static List<String> getAndroidCompilerOutputFolders(@NotNull AndroidModuleModel androidModel,
                                                                    boolean includeProduction,
                                                                    boolean includeTests) {
    List<String> toAdd = new LinkedList<>();
    // The test artifact must be added to the classpath before the main artifact, this is so that tests pick up the correct classes
    // if multiple definitions of the same class exist in both the test and the main artifact.
    if (includeTests) {
      if (androidModel.getSelectedVariant().getUnitTestArtifact() != null) {
        addFoldersFromArtifact(androidModel.getSelectedVariant().getUnitTestArtifact(), toAdd);
      }
      if (androidModel.getSelectedVariant().getAndroidTestArtifact() != null) {
        addFoldersFromArtifact(androidModel.getSelectedVariant().getAndroidTestArtifact(), toAdd);
      }
    }
    if (includeProduction) {
      addFoldersFromArtifact(androidModel.getSelectedVariant().getMainArtifact(), toAdd);
    }
    return toAdd;
  }

  private static void addFoldersFromArtifact(@NotNull BaseArtifact artifact, @NotNull List<String> toAdd) {
    toAdd.add(pathToIdeaUrl(artifact.getClassesFolder()));
    artifact.getAdditionalClassesFolders().stream()
            .filter(Objects::nonNull)
            .map(FilePaths::pathToIdeaUrl)
            .forEach(toAdd::add);
    toAdd.add(pathToIdeaUrl(artifact.getJavaResourcesFolder()));
    if (artifact instanceof AndroidArtifact) {
      ((AndroidArtifact)artifact).getGeneratedResourceFolders().stream()
                                 .filter(Objects::nonNull)
                                 .map(FilePaths::pathToIdeaUrl)
                                 .forEach(toAdd::add);
    }
  }

  @NotNull
  private static Collection<String> getJavaAndKotlinCompilerOutputFolders(@NotNull JavaModuleModel javaModel,
                                                                 boolean includeProduction,
                                                                 boolean includeTests) {
    Collection<String> toAdd = new LinkedList<>();
    File mainClassesFolderPath = null;
    File mainResourcesFolderPath = null;
    File testClassesFolderPath = null;
    File testResourcesFolderPath = null;
    File mainKotlinClassesFolderPath = null;
    File testKotlinClassesFolderPath = null;

    ExtIdeaCompilerOutput compilerOutput = javaModel.getCompilerOutput();
    if (compilerOutput != null) {
      mainClassesFolderPath = compilerOutput.getMainClassesDir();
      mainResourcesFolderPath = compilerOutput.getMainResourcesDir();
      testClassesFolderPath = compilerOutput.getTestClassesDir();
      testResourcesFolderPath = compilerOutput.getTestResourcesDir();
    }

    File buildFolderPath = javaModel.getBuildFolderPath();
    if (javaModel.isBuildable()) {
      if (mainClassesFolderPath == null) {
        // Guess default output folder
        mainClassesFolderPath = new File(buildFolderPath, join(CLASSES_FOLDER_NAME, MAIN_FOLDER_NAME));
      }
      if (mainResourcesFolderPath == null) {
        // Guess default output folder
        mainResourcesFolderPath = new File(buildFolderPath, join(RESOURCES_FOLDER_NAME, MAIN_FOLDER_NAME));
      }
      if (testClassesFolderPath == null) {
        // Guess default output folder
        testClassesFolderPath = new File(buildFolderPath, join(CLASSES_FOLDER_NAME, TEST_FOLDER_NAME));
      }
      if (testResourcesFolderPath == null) {
        // Guess default output folder
        testResourcesFolderPath = new File(buildFolderPath, join(RESOURCES_FOLDER_NAME, TEST_FOLDER_NAME));
      }
    }

    // For Kotlin models it is possible that the javaModel#isBuildable returns false since no javaCompile task exists.
    // As a result we always need to try and look for Kotlin output folder.
    if (buildFolderPath != null) {
      // We try to guess Kotlin output folders (Gradle default), since we cannot obtain that from Kotlin model for now.
      File kotlinClasses = buildFolderPath.toPath().resolve(CLASSES_FOLDER_NAME).resolve(KOTLIN_FOLDER_NAME).toFile();
      // The test artifact must be added to the classpath before the main artifact, this is so that tests pick up the correct classes
      // is multiple definitions of the same class existed in both the test and the main artifact.
      if (includeTests) {
        testKotlinClassesFolderPath = new File(kotlinClasses, TEST_FOLDER_NAME);
      }
      if (includeProduction) {
        mainKotlinClassesFolderPath = new File(kotlinClasses, MAIN_FOLDER_NAME);
      }
    }

    // The test artifact must be added to the classpath before the main artifact, this is so that tests pick up the correct classes
    // is multiple definitions of the same class existed in both the test and the main artifact.
    if (includeTests) {
      if (testClassesFolderPath != null) {
        toAdd.add(pathToIdeaUrl(testClassesFolderPath));
      }
      if (testKotlinClassesFolderPath != null) {
        toAdd.add(pathToIdeaUrl(testKotlinClassesFolderPath));
      }
      if (testResourcesFolderPath != null) {
        toAdd.add(pathToIdeaUrl(testResourcesFolderPath));
      }
    }

    if (includeProduction) {
      if (mainClassesFolderPath != null) {
        toAdd.add(pathToIdeaUrl(mainClassesFolderPath));
      }
      if (mainKotlinClassesFolderPath != null) {
        toAdd.add(pathToIdeaUrl(mainKotlinClassesFolderPath));
      }
      if (mainResourcesFolderPath != null) {
        toAdd.add(pathToIdeaUrl(mainResourcesFolderPath));
      }
    }

    return toAdd;
  }
}
