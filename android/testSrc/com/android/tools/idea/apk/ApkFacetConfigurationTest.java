/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.apk;

import com.android.tools.idea.apk.debugging.NativeLibrary;
import com.android.tools.idea.apk.debugging.SetupIssue;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link ApkFacetConfiguration}.
 */
public class ApkFacetConfigurationTest {
  private ApkFacetConfiguration myConfiguration;

  @Before
  public void setUp()  {
    myConfiguration = new ApkFacetConfiguration();
  }

  @Test
  public void getDebugSymbolFolderPaths() throws Exception {
    NativeLibrary library1 = new NativeLibrary("x.c");
    library1.debuggableFilePath = "/a/x.c";

    NativeLibrary library2 = new NativeLibrary("x.h");
    library2.debuggableFilePath = "/a/x.h";

    NativeLibrary library3 = new NativeLibrary("y.c");
    library3.debuggableFilePath = "/a/b/y.c";

    myConfiguration.NATIVE_LIBRARIES.add(library1);
    myConfiguration.NATIVE_LIBRARIES.add(library2);
    myConfiguration.NATIVE_LIBRARIES.add(library3);

    Collection<String> paths = myConfiguration.getDebugSymbolFolderPaths();
    assertThat(paths).containsExactly("/a");
  }

  @Test
  public void getLibrariesWithoutDebugSymbolsWithEmptyLibraries() {
    List<NativeLibrary> libraries = myConfiguration.getLibrariesWithoutDebugSymbols();
    assertThat(libraries).isEmpty();
  }

  @Test
  public void getLibrariesWithoutDebugSymbolsWithLibrariesMissingDebugSymbols() {
    NativeLibrary library1 = new NativeLibrary("x.c");
    library1.hasDebugSymbols = true;

    NativeLibrary library2 = new NativeLibrary("x.h");
    NativeLibrary library3 = new NativeLibrary("y.c");

    myConfiguration.NATIVE_LIBRARIES.add(library1);
    myConfiguration.NATIVE_LIBRARIES.add(library2);
    myConfiguration.NATIVE_LIBRARIES.add(library3);

    List<NativeLibrary> libraries = myConfiguration.getLibrariesWithoutDebugSymbols();
    assertThat(libraries).containsAllOf(library2, library3);
  }

  @Test
  public void getLibrariesWithoutDebugSymbolsWithLibrariesHavingDebugSymbols() {
    NativeLibrary library1 = new NativeLibrary("x.c");
    library1.hasDebugSymbols = true;

    NativeLibrary library2 = new NativeLibrary("x.h");
    library2.hasDebugSymbols = true;

    NativeLibrary library3 = new NativeLibrary("y.c");
    library3.hasDebugSymbols = true;

    myConfiguration.NATIVE_LIBRARIES.add(library1);
    myConfiguration.NATIVE_LIBRARIES.add(library2);
    myConfiguration.NATIVE_LIBRARIES.add(library3);

    List<NativeLibrary> libraries = myConfiguration.getLibrariesWithoutDebugSymbols();
    assertThat(libraries).isEmpty();
  }

  @Test
  public void removeIssues() {
    SetupIssue issue1 = new SetupIssue();
    issue1.category = "category1";

    SetupIssue issue2 = new SetupIssue();
    issue2.category = "category1";

    SetupIssue issue3 = new SetupIssue();
    issue3.category = "category2";

    myConfiguration.SETUP_ISSUES.add(issue1);
    myConfiguration.SETUP_ISSUES.add(issue2);
    myConfiguration.SETUP_ISSUES.add(issue3);

    myConfiguration.removeIssues("category1");

    assertThat(myConfiguration.SETUP_ISSUES).containsExactly(issue3);
  }

  @Test
  public void getSymbolFolderPathMappings() {
    NativeLibrary library1 = new NativeLibrary("x.c");
    library1.pathMappings.put("a1.so", "b1.so");

    NativeLibrary library2 = new NativeLibrary("x.c");
    library2.pathMappings.put("a2.so", "b2.so");

    myConfiguration.NATIVE_LIBRARIES.add(library1);
    myConfiguration.NATIVE_LIBRARIES.add(library2);

    Map<String, String> mappings = myConfiguration.getSymbolFolderPathMappings();
    assertThat(mappings).hasSize(2);
    assertThat(mappings).containsEntry("a1.so", "b1.so");
    assertThat(mappings).containsEntry("a2.so", "b2.so");
  }
}