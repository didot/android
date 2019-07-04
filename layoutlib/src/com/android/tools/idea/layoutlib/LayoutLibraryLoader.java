/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package com.android.tools.idea.layoutlib;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.Bridge;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.tools.idea.io.BufferingFileWrapper;
import com.android.utils.ILogger;
import com.android.utils.SdkUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import com.intellij.util.lang.UrlClassLoader;
import java.net.MalformedURLException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Map;

/**
 * Loads a {@link LayoutLibrary}
 */
public class LayoutLibraryLoader {
  protected static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.LayoutLibraryLoader");

  private static final Map<IAndroidTarget, LayoutLibrary> ourLibraryCache =
    ContainerUtil.createWeakKeySoftValueMap();
  private static ClassLoader ourNativeClassLoader;

  private LayoutLibraryLoader() {
  }

  @Nullable
  private static LayoutLibrary loadImpl(@NotNull IAndroidTarget target,
                                        @NotNull Map<String, Map<String, Integer>> enumMap,
                                        boolean loadNative) throws RenderingException {
    final String fontFolderPath = FileUtil.toSystemIndependentName((target.getPath(IAndroidTarget.FONTS)));
    final VirtualFile fontFolder = LocalFileSystem.getInstance().findFileByPath(fontFolderPath);
    if (fontFolder == null || !fontFolder.isDirectory()) {
      throw new RenderingException(
        LayoutlibBundle.message("android.directory.cannot.be.found.error", FileUtil.toSystemDependentName(fontFolderPath)));
    }

    final String platformFolderPath = target.isPlatform() ? target.getLocation() : target.getParent().getLocation();
    final File platformFolder = new File(platformFolderPath);
    if (!platformFolder.isDirectory()) {
      throw new RenderingException(
        LayoutlibBundle.message("android.directory.cannot.be.found.error", FileUtil.toSystemDependentName(platformFolderPath)));
    }

    final File buildProp = new File(platformFolder, SdkConstants.FN_BUILD_PROP);
    if (!buildProp.isFile()) {
      throw new RenderingException(
        LayoutlibBundle.message("android.file.not.exist.error", FileUtil.toSystemDependentName(buildProp.getPath())));
    }

    if (!SystemInfo.isJavaVersionAtLeast(8, 0, 0) && target.getVersion().getFeatureLevel() >= 24) {
      // From N, we require to be running in Java 8
      throw new UnsupportedJavaRuntimeException(LayoutlibBundle.message("android.layout.preview.unsupported.jdk",
                                                                        SdkVersionInfo.getCodeName(target.getVersion().getFeatureLevel())));
    }

    LayoutLibrary library;
    final ILogger logger = new LogWrapper(LOG);
    final Map<String, String> buildPropMap = ProjectProperties.parsePropertyFile(new BufferingFileWrapper(buildProp), logger);
    final LayoutLog layoutLog = new LayoutLogWrapper(LOG);

    if (loadNative) {
      try {
        String dataPath = FileUtil.toSystemIndependentName(target.getPath(IAndroidTarget.DATA));
        if (ourNativeClassLoader == null) {
          URL nativeLayoutlibUrl = SdkUtils.fileToUrl(new File(dataPath + "layoutlib_native.jar"));
          ourNativeClassLoader =
            UrlClassLoader.build().urls(nativeLayoutlibUrl).parent(new LayoutlibNativeClassLoader(nativeLayoutlibUrl)).get();
        }
        ClassLoader classLoader = new LayoutlibClassLoader(ourNativeClassLoader);

        // Load the bridge from native layoutlib
        Class<?> clazz = classLoader.loadClass("com.android.layoutlib.bridge.Bridge");
        if (clazz != null) {
          Constructor<?> constructor = clazz.getConstructor();
          if (constructor != null) {
            Object bridgeObject = constructor.newInstance();
            if (bridgeObject instanceof Bridge) {
              Bridge bridge = (Bridge)bridgeObject;
              library = LayoutLibrary.load(bridge, classLoader);
              if (library.init(buildPropMap, new File(fontFolder.getPath()), getNativeLibraryPath(dataPath),
                               dataPath + "icu/", enumMap, layoutLog)) {
                return library;
              }
              else {
                LOG.warn("Loading the native layoutlib failed, using the default one instead");
              }
            }
          }
        }
      }
      catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException | MalformedURLException e) {
        LOG.warn("Exception when loading the native layoutlib", e);
      }
    }

    // We instantiate the local Bridge implementation and pass it to the LayoutLibrary instance
    library = LayoutLibrary.load(new com.android.layoutlib.bridge.Bridge(), new LayoutlibClassLoader(LayoutLibraryLoader.class.getClassLoader()));
    if (library.init(buildPropMap, new File(fontFolder.getPath()), null, null, enumMap, layoutLog)) {
      return library;
    }
    return null;
  }

  @NotNull
  private static String getNativeLibraryPath(@NotNull String dataPath) {
    return dataPath + getPlatformName() + (SystemInfo.is64Bit ? "/lib64/" : "/lib/");
  }

  @NotNull
  private static String getPlatformName() {
    if (SystemInfo.isWindows) return "win";
    else if (SystemInfo.isMac) return "mac";
    else if (SystemInfo.isLinux) return "linux";
    else return "";
  }

  /**
   * Loads the standard version or the native version of layoutlib depending on the value of loadNative.
   * If loading the native version, this will dynamically load the layoutlib jar and all its associated
   * native libraries.
   * Returns null if it fails to initialize layoutlib.
   */
  @Nullable
  public static synchronized LayoutLibrary load(@NotNull IAndroidTarget target,
                                                @NotNull Map<String, Map<String, Integer>> enumMap,
                                                boolean loadNative) throws RenderingException {
    LayoutLibrary library = ourLibraryCache.get(target);
    if (library == null || library.isDisposed()) {
      library = loadImpl(target, enumMap, loadNative);

      if (library != null) {
        ourLibraryCache.put(target, library);
      }
    }

    return library;
  }
}
