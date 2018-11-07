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
package com.android.tools.idea.instantapp.provision;

import com.android.annotations.NonNull;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.tools.idea.instantapp.InstantAppSdks;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.testFramework.MapDataContext;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.StringReader;
import java.nio.charset.Charset;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ProvisionBeforeRunTaskProvider}.
 */
public class ProvisionBeforeRunTaskProviderTest extends AndroidTestCase {
  private AndroidRunConfigurationBase myRunConfiguration;
  private IdeComponents myIdeComponents;
  private InstantAppSdks myInstantAppSdks;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ConfigurationFactory configurationFactory = AndroidRunConfigurationType.getInstance().getFactory();
    AndroidRunConfigurationBase actualRunConfiguration = new AndroidRunConfiguration(getProject(), configurationFactory);
    myRunConfiguration = spy(actualRunConfiguration);
    myIdeComponents = new IdeComponents(getProject());
    myInstantAppSdks = myIdeComponents.mockService(InstantAppSdks.class);
    initMocks(this);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIdeComponents.restore();
    }
    finally {
      super.tearDown();
    }
  }

  public void testTaskNotCreatedIfSdkNotDefined() {
    when(myInstantAppSdks.isInstantAppSdkEnabled()).thenReturn(false);
    assertNull(new ProvisionBeforeRunTaskProvider().createTask(myRunConfiguration));
  }

  public void testTaskCreatedIfModuleNull() {
    when(myInstantAppSdks.isInstantAppSdkEnabled()).thenReturn(true);
    JavaRunConfigurationModule runConfigurationModule = mock(JavaRunConfigurationModule.class);
    when(runConfigurationModule.getModule()).thenReturn(null);
    when(myRunConfiguration.getConfigurationModule()).thenReturn(runConfigurationModule);
    assertNotNull(new ProvisionBeforeRunTaskProvider().createTask(myRunConfiguration));
  }

  public void testProvisionSkippedWhenNotInstantApp() {
    assertTrue(new ProvisionBeforeRunTaskProvider() {
      @Override
      boolean isInstantAppContext(@NotNull AndroidRunConfigurationBase runConfiguration) {
        return false;
      }
    }.executeTask(new MapDataContext(), myRunConfiguration, new ExecutionEnvironment(), new ProvisionBeforeRunTaskProvider.ProvisionBeforeRunTask(getProject())));
  }

  public void testTaskReadExternalXmlWithNoTimestamp() throws Exception {
    Element element = createElementFromString("<option name=\"com.android.instantApps.provision.BeforeRunTask\" enabled=\"true\" clearCache=\"false\" clearProvisionedDevices=\"true\" />");

    ProvisionBeforeRunTaskProvider.ProvisionBeforeRunTask task = new ProvisionBeforeRunTaskProvider.ProvisionBeforeRunTask(getProject());
    task.readExternal(element);
    assertFalse(task.isClearCache());
    assertTrue(task.isClearProvisionedDevices());
    assertEquals(0, task.getTimestamp());
  }

  public void testWriteAndReadExternal() {
    IDevice device1 = mock(IDevice.class);
    IDevice device2 = mock(IDevice.class);
    when(device1.getSerialNumber()).thenReturn("device1");
    when(device1.getAvdName()).thenReturn(null);
    when(device2.getSerialNumber()).thenReturn("device2");
    when(device2.getAvdName()).thenReturn("avd2");

    ProvisionBeforeRunTaskProvider.ProvisionBeforeRunTask task1 = new ProvisionBeforeRunTaskProvider.ProvisionBeforeRunTask(getProject()) {
      @Override
      boolean isPackageInstalled(@NonNull IDevice device, @NonNull String pkgName) {
        return true;
      }
    };

    task1.setClearCache(false);
    task1.setClearProvisionedDevices(true);
    task1.addProvisionedDevice(device1);

    Element element = new Element("option");
    task1.writeExternal(element);

    ProvisionBeforeRunTaskProvider.ProvisionBeforeRunTask task2 = new ProvisionBeforeRunTaskProvider.ProvisionBeforeRunTask(getProject()) {
      @Override
      boolean isPackageInstalled(@NonNull IDevice device, @NonNull String pkgName) {
        return true;
      }
    };
    task2.readExternal(element);

    assertFalse(task2.isClearCache());
    assertTrue(task2.isClearProvisionedDevices());
    assertTrue(task2.isProvisioned(device1));
    assertFalse(task2.isProvisioned(device2));
  }

  public void testShouldProvisionWhenSupervisorNotInstalled() {
    shouldProvisionWhenPackageNotInstalled("com.google.android.instantapps.supervisor");
  }

  public void testShouldProvisionWhenDevManNotInstalled() {
    shouldProvisionWhenPackageNotInstalled("com.google.android.instantapps.devman");
  }

  public void testIsInstalledPackage() throws Throwable {
    IDevice device = mock(IDevice.class);
    when(device.getSerialNumber()).thenReturn("device");
    when(device.getAvdName()).thenReturn(null);
    doAnswer(
      invocation -> {
        IShellOutputReceiver receiver = invocation.getArgument(1);
        byte[] output = ("path/to/package").getBytes(Charset.defaultCharset());
        receiver.addOutput(output, 0, output.length);
        receiver.flush();
        return null;
      })
      .when(device)
      .executeShellCommand(eq("pm path installed.package"), notNull());

    ProvisionBeforeRunTaskProvider.ProvisionBeforeRunTask task = new ProvisionBeforeRunTaskProvider.ProvisionBeforeRunTask(getProject());

    assertTrue(task.isPackageInstalled(device, "installed.package"));
    assertFalse(task.isPackageInstalled(device, "otherPackage"));
  }

  @NotNull
  private static Element createElementFromString(@NotNull String xmlString) throws Exception {
    Document document = new SAXBuilder().build(new StringReader(xmlString));
    return document.getRootElement();
  }

  private void shouldProvisionWhenPackageNotInstalled(@NotNull String testPkgName) {
    IDevice device = mock(IDevice.class);
    when(device.getSerialNumber()).thenReturn("device");
    when(device.getAvdName()).thenReturn(null);

    ProvisionBeforeRunTaskProvider.ProvisionBeforeRunTask task = new ProvisionBeforeRunTaskProvider.ProvisionBeforeRunTask(getProject()) {
      @Override
      boolean isPackageInstalled(@NonNull IDevice device, @NonNull String pkgName) {
        return pkgName.compareTo(testPkgName) != 0;
      }
    };

    task.addProvisionedDevice(device);

    assertFalse(task.isProvisioned(device));
  }
}
