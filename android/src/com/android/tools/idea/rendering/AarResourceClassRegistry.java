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
package com.android.tools.idea.rendering;

import com.android.io.FileWrapper;
import com.android.tools.idea.gradle.project.GradleBuildListener;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.xml.AndroidManifest;
import com.google.common.collect.Maps;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.android.uipreview.ModuleClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.DOT_AAR;
import static com.android.tools.idea.gradle.compiler.PostProjectBuildTasksExecutor.GRADLE_BUILD_TOPIC;
import static org.jetbrains.android.facet.ResourceFolderManager.EXPLODED_AAR;

/**
 * A registry for class lookup of resource classes (R classes) in AAR libraries.
 */
public class AarResourceClassRegistry implements ProjectComponent {

  private final Map<String,AarResourceClassGenerator> myGeneratorMap = Maps.newHashMap();
  private final Project myProject;
  private GradleBuildListener myBuildCompleteListener;

  @SuppressWarnings("WeakerAccess")  // Accessed via reflection.
  public AarResourceClassRegistry(Project project) {
    myProject = project;
  }

  public void addLibrary(AppResourceRepository appResources, File aarDir) {
    String path = aarDir.getPath();
    if (path.endsWith(DOT_AAR) || path.contains(EXPLODED_AAR)) {
      FileResourceRepository repository = appResources.findRepositoryFor(aarDir);
      if (repository != null) {
        String pkg = getAarPackage(aarDir);
        if (pkg != null) {
          AarResourceClassGenerator generator = AarResourceClassGenerator.create(appResources, repository);
          myGeneratorMap.put(pkg, generator);
        }
      }
    }
  }

  @Nullable
  private static String getAarPackage(@NotNull File aarDir) {
    File manifest = new File(aarDir, ANDROID_MANIFEST_XML);
    if (manifest.exists()) {
      try {
        // TODO: Come up with something more efficient! A pull parser can do this quickly
        return AndroidManifest.getPackage(new FileWrapper(manifest));
      }
      catch (Exception e) {
        // No go
        return null;
      }
    }

    return null;
  }

  /** Looks up a class definition for the given name, if possible */
  @Nullable
  public byte[] findClassDefinition(@NotNull String name) {
    int index = name.lastIndexOf('.');
    if (index != -1 && name.charAt(index + 1) == 'R' && (index == name.length() - 2 || name.charAt(index + 2) == '$') && index > 1) {
      String pkg = name.substring(0, index);
      AarResourceClassGenerator generator = myGeneratorMap.get(pkg);
      if (generator != null) {
        registerSyncListenerIfNecessary();
        return generator.generate(name);
      }
    }
    return null;
  }

  /**
   * There's a bug in the ModuleClassLoader's cache implementation, which results in crashes during preview rendering. The workaround is
   * to clear the cache on each build. This registers a build complete listener to trigger the cache refresh.
   */
  private void registerSyncListenerIfNecessary() {
    if (myBuildCompleteListener != null) {
      return;
    }
    myBuildCompleteListener = new GradleBuildListener() {
      @Override
      public void buildFinished(@NotNull Project builtProject, @Nullable BuildMode mode) {
        if (mode == null || builtProject != myProject) {
          return;
        }
        switch (mode) {
          case CLEAN:
          case ASSEMBLE:
          case COMPILE_JAVA:
            ModuleClassLoader.clearCache();
          case REBUILD:
          case SOURCE_GEN:
          case ASSEMBLE_TRANSLATE:
        }
      }
    };
    MessageBusConnection connection = myProject.getMessageBus().connect(myProject);
    connection.subscribe(GRADLE_BUILD_TOPIC, myBuildCompleteListener);
  }

  public static AarResourceClassRegistry get(Project project) {
    return project.getComponent(AarResourceClassRegistry.class);
  }

  // ProjectComponent methods.

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return AarResourceClassRegistry.class.getName();
  }
}