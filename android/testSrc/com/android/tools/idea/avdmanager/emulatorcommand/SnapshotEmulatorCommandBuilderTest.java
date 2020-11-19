/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.avdmanager.emulatorcommand;

import static org.junit.Assert.assertEquals;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdWizardUtils;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.intellij.execution.configurations.GeneralCommandLine;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class SnapshotEmulatorCommandBuilderTest {
  @Test
  public void addSnapshotParameters() {
    // Arrange
    Path emulator = Jimfs.newFileSystem(Configuration.unix()).getPath("/home/user/Android/Sdk/emulator/emulator");

    AvdInfo avd = Mockito.mock(AvdInfo.class);

    Mockito.when(avd.getProperty(AvdWizardUtils.CHOSEN_SNAPSHOT_FILE)).thenReturn("snap_2020-11-10_13-18-17");
    Mockito.when(avd.getName()).thenReturn("Pixel_4_API_30");

    EmulatorCommandBuilder builder = new SnapshotEmulatorCommandBuilder(emulator, avd)
      .setEmulatorSupportsSnapshots(true);

    // Act
    GeneralCommandLine command = builder.build();

    // Assert
    assertEquals("/home/user/Android/Sdk/emulator/emulator -snapshot snap_2020-11-10_13-18-17 -no-snapshot-save -avd Pixel_4_API_30",
                 command.getCommandLineString());
  }
}
