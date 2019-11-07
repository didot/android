/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.jetbrains.android.sdk;

import com.android.SdkConstants;
import com.android.internal.util.Preconditions;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.OptionalLibrary;
import com.android.sdklib.repository.targets.PlatformTarget;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link IAndroidTarget} to render using the layoutlib version and resources shipped with Android Studio.
 */
public class StudioEmbeddedRenderTarget implements IAndroidTarget {
  private static final Logger LOG = Logger.getInstance(StudioEmbeddedRenderTarget.class);
  private static final String ONLY_FOR_RENDERING_ERROR = "This target is only for rendering";

  // Possible paths of the embedded "layoutlib" directory.
  private static final String[] EMBEDDED_LAYOUTLIB_PATHS = {
    // Bundled path.
    "/plugins/android/lib/layoutlib/",
    // Development path.
    "/../../prebuilts/studio/layoutlib/",
    // IDEA path.
    "/community/android/tools-base/layoutlib/",
    // IDEA community path.
    "/android/tools-base/layoutlib/" // FIXME-ank: added (large) binaries to the path
  };
  // Possible paths of framework_res.jar relative to the "layoutlib" directory.
  private static final String[] EMBEDDED_FRAMEWORK_RES_JAR_PATHS = {
    // Bundled path.
    "data/framework_res.jar",
    // Path when running in IntelliJ.
    "../../../bazel-genfiles/tools/adt/idea/resources-aar/framework_res.jar",
  };
  // Path of framework_res.jar relative to the runfiles directory when running in Bazel.
  private static final String EMBEDDED_FRAMEWORK_RES_JAR_BAZEL_PATH = "tools/adt/idea/resources-aar/framework_res.jar";

  @Nullable private final String myBasePath;
  @Nullable private final String myFrameworkResPath;

  private static StudioEmbeddedRenderTarget ourStudioEmbeddedTarget;
  private static boolean ourDisableEmbeddedTargetForTesting = false;

  /**
   * Method that allows to disable the use of the embedded render target. Only for testing.
   *
   * @param value if true, the embedded layoutlib won't be used
   */
  @VisibleForTesting
  public static void setDisableEmbeddedTarget(boolean value) {
    assert ApplicationManager.getApplication().isUnitTestMode();

    ourDisableEmbeddedTargetForTesting = value;
  }

  /**
   * Returns a CompatibilityRenderTarget that will use StudioEmbeddedRenderTarget to do the rendering.
   */
  public static CompatibilityRenderTarget getCompatibilityTarget(@NotNull IAndroidTarget target) {
    if (ourDisableEmbeddedTargetForTesting) {
      return new CompatibilityRenderTarget(target, target.getVersion().getApiLevel(), target);
    }

    int api = target.getVersion().getApiLevel();

    if (target instanceof CompatibilityRenderTarget) {
      CompatibilityRenderTarget compatRenderTarget = (CompatibilityRenderTarget)target;
      target = compatRenderTarget.getRealTarget();
    }

    return new CompatibilityRenderTarget(getInstance(), api, target);
  }

  private static StudioEmbeddedRenderTarget getInstance() {
    if (ourStudioEmbeddedTarget == null) {
      ourStudioEmbeddedTarget = new StudioEmbeddedRenderTarget();
    }
    return ourStudioEmbeddedTarget;
  }

  private StudioEmbeddedRenderTarget() {
    myBasePath = getEmbeddedLayoutLibPath();
    myFrameworkResPath = getEmbeddedFrameworkResPath(myBasePath);
  }

  /**
   * Returns the URL for the embedded layoutlib distribution.
   */
  @Nullable
  private static String getEmbeddedLayoutLibPath() {
    String homePath = FileUtil.toSystemIndependentName(PathManager.getHomePath());

    StringBuilder notFoundPaths = new StringBuilder();
    for (String path : EMBEDDED_LAYOUTLIB_PATHS) {
      String jarPath = homePath + path;
      VirtualFile root = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(jarPath));

      if (root != null) {
        File rootFile = VfsUtilCore.virtualToIoFile(root);
        if (rootFile.exists() && rootFile.isDirectory()) {
          LOG.debug("Embedded layoutlib found at " + jarPath);
          return rootFile.getAbsolutePath() + File.separator;
        }
      }
      else {
        notFoundPaths.append(jarPath).append('\n');
      }
    }

    LOG.error("Unable to find embedded layoutlib in paths:\n" + notFoundPaths.toString());
    return null;
  }

  /**
   * Returns the URL for the embedded layoutlib distribution.
   * @param basePath
   */
  @Nullable
  private static String getEmbeddedFrameworkResPath(@Nullable String basePath) {
    if (basePath == null) {
      return null;
    }

    for (String relativePath : EMBEDDED_FRAMEWORK_RES_JAR_PATHS) {
      Path path = Paths.get(basePath, relativePath).normalize();
      if (Files.exists(path)) {
        return path.toString();
      }
    }
    Path path = Paths.get(EMBEDDED_FRAMEWORK_RES_JAR_BAZEL_PATH).toAbsolutePath().normalize();
    if (Files.exists(path)) {
      return path.toString();
    }
    return null;
  }

  @Override
  @NotNull
  public String getLocation() {
    Preconditions.checkState(myBasePath != null, "Embedded layoutlib not found");
    return myBasePath;
  }

  @Override
  public String getVendor() {
    return PlatformTarget.PLATFORM_VENDOR;
  }

  @Override
  @NotNull
  public AndroidVersion getVersion() {
    // This method will never be called if this is used as a delegate of CompatibilityRenderTarget
    throw new UnsupportedOperationException("This target can only be used as a CompatibilityRenderTarget delegate");
  }

  @Override
  public String getVersionName() {
    // This method will never be called if this is used as a delegate of CompatibilityRenderTarget
    throw new UnsupportedOperationException("This target can only be used as a CompatibilityRenderTarget delegate");
  }

  @Override
  public int getRevision() {
    return 1;
  }

  @Override
  public boolean isPlatform() {
    return true;
  }

  @Override
  public IAndroidTarget getParent() {
    return null;
  }

  @Override
  @NotNull
  public String getPath(int pathId) {
    // The prebuilt version of layoutlib only includes the layoutlib.jar and the resources.
    switch (pathId) {
      case DATA:
        return getLocation() + SdkConstants.OS_PLATFORM_DATA_FOLDER;
      case RESOURCES:
        Preconditions.checkState(myFrameworkResPath != null, "Embedded framework_res.jar not found");
        return myFrameworkResPath;
      case FONTS:
        return getLocation() + SdkConstants.OS_PLATFORM_FONTS_FOLDER;
      default:
        assert false : getClass().getSimpleName() + " does not support path of type " + pathId;
        return getLocation();
    }
  }

  @Override
  public BuildToolInfo getBuildToolInfo() {
    return null;
  }

  @Override
  @NotNull
  public List<String> getBootClasspath() {
    return ImmutableList.of(getPath(IAndroidTarget.ANDROID_JAR));
  }

  @Override
  public boolean hasRenderingLibrary() {
    return true;
  }

  /*
   * All the methods below are not used since this is a target that is only used to render previews in Studio.
   */

  @Override
  public String getName() {
    // This method is only used for non platform targets
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @Override
  public String getFullName() {
    return getName();
  }

  @Override
  public String getClasspathName() {
    return getName();
  }

  @Override
  public String getShortClasspathName() {
    return getName();
  }

  @Override
  public String getDescription() {
    return getName();
  }

  @Override
  @NotNull
  public List<OptionalLibrary> getOptionalLibraries() {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @Override
  @NotNull
  public List<OptionalLibrary> getAdditionalLibraries() {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @Override
  @NotNull
  public File[] getSkins() {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @Override
  @Nullable
  public File getDefaultSkin() {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @Override
  public String[] getPlatformLibraries() {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @Override
  public String getProperty(String name) {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @Override
  public Map<String, String> getProperties() {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @Override
  public boolean canRunOn(IAndroidTarget target) {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }

  @Override
  public String hashString() {
    return "studio-embedded-render-target";
  }

  @Override
  public int compareTo(IAndroidTarget o) {
    throw new UnsupportedOperationException(ONLY_FOR_RENDERING_ERROR);
  }
}
