/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import static com.android.tools.idea.gradle.util.EmbeddedDistributionPaths.doFindAndroidStudioLocalMavenRepoPaths;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.util.StudioPathManager;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * Tests for {@link EmbeddedDistributionPaths}.
 */
public class EmbeddedDistributionPathsTest {

  @Test
  public void testFindAndroidStudioLocalMavenRepoPaths() {
    List<File> expectedRepo = Arrays.asList(new File(StudioPathManager.resolveDevPath("out/repo")),
                                            new File(StudioPathManager.resolveDevPath("out/studio/repo")),
                                            new File(StudioPathManager.resolveDevPath("prebuilts/tools/common/m2/repository")),
                                            new File(StudioPathManager.resolveDevPath("../maven/repo")),
                                            new File(System.getProperty("java.io.tmpdir"), "offline-maven-repo"));
    expectedRepo = expectedRepo.stream().filter(File::isDirectory).collect(Collectors.toList());
    // Invoke the method to test.
    List<File> paths = doFindAndroidStudioLocalMavenRepoPaths();
    assertThat(paths).hasSize(expectedRepo.size());
    assertThat(paths).containsExactlyElementsIn(expectedRepo);
  }
}
