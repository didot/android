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
package com.android.tools.idea.sdk.install.patch;

import com.android.repository.api.ProgressIndicator;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;
import com.android.testutils.InMemoryFileSystemUtilsKt;
import java.awt.Component;
import java.io.File;
import junit.framework.TestCase;

/**
 * Tests for {@link PatchInstallerFactory}.
 */
public class PatchInstallerTest extends TestCase {
  private static MockFileOp ourFileOp;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ourFileOp = new MockFileOp();
  }

  public void testRunInstaller() {
    FakeProgressIndicator progress = new FakeProgressIndicator(true);
    ourFileOp.recordExistingFile("/sdk/pkg/sourceFile",
                           "the source to which the diff will be applied");
    ourFileOp.recordExistingFile("/patchfile", "the patch contents");
    PatchRunner runner = new PatchRunner(ourFileOp.toPath("dummy"), FakeRunner.class, FakeUIBase.class, FakeUI.class, FakeGenerator.class);
    boolean result = runner.run(new File(InMemoryFileSystemUtilsKt.getPlatformSpecificPath("/sdk/pkg")),
                                new File(InMemoryFileSystemUtilsKt.getPlatformSpecificPath("/patchfile")), progress);
    progress.assertNoErrorsOrWarnings();
    assertTrue(result);
    assertTrue(FakeRunner.ourDidRun);
  }

  private static class FakeRunner {
    public static boolean ourDidRun;
    private static boolean ourLoggerInitted;

    public static void initLogger() {
      ourLoggerInitted = true;
    }

    @SuppressWarnings("unused") // invoked by reflection
    public static boolean doInstall(String patchPath, FakeUIBase ui, String sourcePath) {
      assertEquals(ourFileOp.getPlatformSpecificPath("/patchfile"), patchPath);
      assertTrue(ourFileOp.exists(new File(sourcePath, "sourceFile")));
      assertTrue(ui instanceof FakeUI);
      ourDidRun = true;
      return ourLoggerInitted;
    }
  }

  private static class FakeUIBase {}

  private static class FakeGenerator {}

  private static class FakeUI extends FakeUIBase {
    FakeUI(Component c, ProgressIndicator progress) {}
  }
}
