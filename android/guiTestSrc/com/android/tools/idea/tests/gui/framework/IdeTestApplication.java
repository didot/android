/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework;

import com.intellij.ide.BootstrapClassLoaderUtil;
import com.intellij.ide.WindowsCommandLineProcessor;
import com.intellij.idea.Main;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

import static com.intellij.openapi.util.io.FileUtil.ensureExists;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static org.fest.reflect.core.Reflection.staticMethod;

public class IdeTestApplication implements Disposable {
  private static final Logger LOG = Logger.getInstance(IdeTestApplication.class);

  private static IdeTestApplication ourInstance;

  @NotNull private final UrlClassLoader myIdeClassLoader;

  @NotNull
  public static synchronized LoadResult getInstance() throws Exception {
    System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, "AndroidStudio");
    System.setProperty(PathManager.PROPERTY_CONFIG_PATH, getConfigDirPath().getPath());

    boolean firstTimeLoaded = false;

    if (ourInstance == null) {
      firstTimeLoaded = true;
      new IdeTestApplication();
    }

    return new LoadResult(ourInstance, firstTimeLoaded);
  }

  private static File getConfigDirPath() throws IOException {
    String homeDirPath = toSystemDependentName(PathManager.getHomePath());
    assert !homeDirPath.isEmpty();
    File configDirPath = new File(homeDirPath, FileUtil.join("androidStudio", "gui-tests", "config"));
    ensureExists(configDirPath);
    return configDirPath;
  }

  private IdeTestApplication() throws Exception {
    String[] args = new String[0];

    LOG.assertTrue(ourInstance == null, "Only one instance allowed.");
    ourInstance = this;

    pluginManagerStart(args);
    mainMain();

    myIdeClassLoader = BootstrapClassLoaderUtil.initClassLoader(true);
    WindowsCommandLineProcessor.ourMirrorClass = Class.forName(WindowsCommandLineProcessor.class.getName(), true, myIdeClassLoader);

    Class<?> clazz = Class.forName("com.intellij.ide.plugins.PluginManager", true, myIdeClassLoader);
    staticMethod("start").withParameterTypes(String.class, String.class, String[].class)
                         .in(clazz)
                         .invoke("com.intellij.idea.MainImpl", "start", args);
  }

  private static void pluginManagerStart(@NotNull String[] args) {
    // Duplicates what PluginManager#start does.
    Main.setFlags(args);
    UIUtil.initDefaultLAF();
  }

  private static void mainMain() {
    // Duplicates what Main#main does.
    staticMethod("installPatch").in(Main.class).invoke();
  }

  @NotNull
  public UrlClassLoader getIdeClassLoader() {
    return myIdeClassLoader;
  }

  @Override
  public void dispose() {
    disposeInstance();
  }

  private static synchronized void disposeInstance() {
    if (ourInstance == null) {
      return;
    }
    final Application applicationEx = ApplicationManager.getApplication();
    if (applicationEx != null) {
      new WriteAction() {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          Disposer.dispose(applicationEx);
        }
      }.execute();
    }
    ourInstance = null;
  }

  public static class LoadResult {
    @NotNull private final IdeTestApplication myApp;
    private final boolean myFirstTimeLoaded;

    public LoadResult(@NotNull IdeTestApplication app, boolean firstTimeLoaded) {
      myApp = app;
      myFirstTimeLoaded = firstTimeLoaded;
    }

    @NotNull
    public IdeTestApplication getApp() {
      return myApp;
    }

    public boolean isFirstTimeLoaded() {
      return myFirstTimeLoaded;
    }
  }
}

