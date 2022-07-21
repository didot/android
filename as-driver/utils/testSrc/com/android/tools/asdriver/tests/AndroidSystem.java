/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.asdriver.tests;

import com.android.testutils.TestUtils;
import com.android.utils.PathUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.function.Consumer;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Represents a system running android development tools.
 * It has a display, environment variables, a file system etc.
 */
public class AndroidSystem implements AutoCloseable, TestRule {
  private final TestFileSystem fileSystem;
  private final HashMap<String, String> env;
  private final Display display;
  private final AndroidSdk sdk;
  private AndroidStudioInstallation install;
  private String emulator;

  private static boolean applied = false;

  private AndroidSystem(TestFileSystem fileSystem, Display display, AndroidSdk sdk) {
    this.fileSystem = fileSystem;
    this.display = display;
    this.sdk = sdk;
    this.env = new HashMap<>();
    this.install = null;
    this.emulator = null;
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        if (applied) {
          // This object can be used as a rule only once per execution to avoid multiple
          // integration tests on the same target. We only want a single test in a single
          // target so that integration tests are parallelized, since they tend to take
          // much longer time than unit tests.
          throw new IllegalStateException("There should only be one integration test per test execution.");
        }
        applied = true;
        try {
          base.evaluate();
        } finally {
          AndroidSystem.this.close();
        }
      }
    };
  }

  /**
   * Creates a standard system with a default temp folder
   * that contains a preinstalled version of android studio
   * from the distribution zips. The SDK is set up pointing
   * to the standard prebuilts one.
   */
  public static AndroidSystem standard() {
    try {
      AndroidSystem system = basic(Files.createTempDirectory("root"));

      system.install = AndroidStudioInstallation.fromZip(system.fileSystem);
      system.install.createFirstRunXml();
      system.install.createGeneralPropertiesXml();

      return system;
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Creates a system that contains only the sdk installed
   * and with a default temp folder.
   */
  public static AndroidSystem basic() {
    try {
      return basic(Files.createTempDirectory("root"));
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Creates a system that contains only the sdk installed.
   */
  public static AndroidSystem basic(Path root) throws IOException {
    TestFileSystem fileSystem = new TestFileSystem(root);
    AndroidSdk sdk = new AndroidSdk(TestUtils.resolveWorkspacePath(TestUtils.getRelativeSdk()));

    AndroidSystem system = new AndroidSystem(fileSystem, Display.createDefault(), sdk);

    sdk.install(system.env);

    return system;
  }

  /**
   * Assumes there is only one AndroidStudio installed in the system.
   * If there are multiple it will throw an exception.
   */
  public AndroidStudioInstallation getInstallation() {
    if (install == null) {
      throw new IllegalStateException("Android studio has not been installed on this system.");
    }
    return install;
  }

  public AndroidStudio runStudio(AndroidProject project) throws IOException, InterruptedException {
    AndroidStudioInstallation install = getInstallation();
    return install.run(display, env, project);
  }

  public void runStudio(AndroidProject project, Consumer<AndroidStudio> callback) throws Exception {
      try (AndroidStudio studio = runStudio(project)) {
        callback.accept(studio);
      }
  }

  public void installRepo(MavenRepo repo) throws Exception {
    AndroidStudioInstallation install = getInstallation();
    repo.install(fileSystem.getRoot(), install, env);
  }

  public Emulator runEmulator() throws IOException, InterruptedException {
    if (emulator == null) {
      emulator = "emu";
      Emulator.createEmulator(fileSystem, emulator, TestUtils.getWorkspaceRoot().resolve("../system_image_android-29_default_x86_64"));
    }
    return Emulator.start(fileSystem, sdk, display, emulator);
  }

  public void runEmulator(Consumer<Emulator> callback) throws IOException, InterruptedException {
    try (Emulator emulator = runEmulator()) {
      callback.accept(emulator);
    }
  }

  public Adb runAdb() throws IOException {
    return Adb.start(sdk, fileSystem.getHome());
  }

  public Adb runAdb(boolean startServer, String... args) throws IOException {
    return Adb.start(sdk, fileSystem.getHome(), startServer, args);
  }

  public void runAdb(Consumer<Adb> callback) throws IOException {
    try (Adb adb = runAdb()) {
      callback.accept(adb);
    }
  }

  public void setEnv(String name, String value) {
    env.put(name, value);
  }

  @Override
  public void close() throws Exception {
    display.close();
    PathUtils.deleteRecursivelyIfExists(fileSystem.getRoot());
  }
}
