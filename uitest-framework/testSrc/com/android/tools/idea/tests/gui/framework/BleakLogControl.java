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
package com.android.tools.idea.tests.gui.framework;

import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.BleakKt;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.IOException;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class BleakLogControl extends TestWatcher {

  @Override
  public void starting(Description description) {
    try {
      FileUtil.ensureExists(new File(GuiTests.getGuiTestRootDirPath(), "bleak"));
      BleakKt.setCurrentLogFile(new File(GuiTests.getGuiTestRootDirPath(),
                                         "bleak/" + description.getClassName() + "." + description.getMethodName()));
    } catch (IOException e) {
      BleakKt.setCurrentLogFile(null);
    }
  }
}
