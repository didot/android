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
package com.android.tools.idea.memorysettings;

import static com.android.tools.idea.memorysettings.GradlePropertiesUtil.getGradleDaemonXmx;
import static com.android.tools.idea.memorysettings.GradlePropertiesUtil.getKotlinDaemonXmx;
import static com.android.tools.idea.memorysettings.GradlePropertiesUtil.setDaemonXmx;

import com.android.tools.idea.gradle.util.GradleProperties;
import com.intellij.testFramework.IdeaTestCase;
import java.io.File;

public class GradlePropertiesUtilTest extends IdeaTestCase {

  public void testHasJvmArgs() throws Exception {
    File propertiesFilePath = createTempFile("gradle.properties", "org.gradle.jvmargs=-Xms800M");
    GradleProperties properties = new GradleProperties(propertiesFilePath);
    assertTrue(GradlePropertiesUtil.hasJvmArgs(properties));

    propertiesFilePath = createTempFile("gradle.properties", "");
    properties = new GradleProperties(propertiesFilePath);
    assertFalse(GradlePropertiesUtil.hasJvmArgs(properties));

    propertiesFilePath = createTempFile("gradle.properties", "kotlin.code.style=official");
    properties = new GradleProperties(propertiesFilePath);
    assertFalse(GradlePropertiesUtil.hasJvmArgs(properties));
  }

  public void testNoDaemonXmx() throws Exception {
    checkXmx("", MemorySettingsUtil.NO_XMX_IN_VM_ARGS, MemorySettingsUtil.NO_XMX_IN_VM_ARGS);
    checkXmx("org.gradle.jvmargs=", MemorySettingsUtil.NO_XMX_IN_VM_ARGS, MemorySettingsUtil.NO_XMX_IN_VM_ARGS);
    checkXmx("# org.gradle.jvmargs=-Xmx1024M", MemorySettingsUtil.NO_XMX_IN_VM_ARGS, MemorySettingsUtil.NO_XMX_IN_VM_ARGS);
    checkXmx("org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xmx1G\"", MemorySettingsUtil.NO_XMX_IN_VM_ARGS, 1024);
    checkXmx("org.gradle.jvmargs=-Xmx2048m", 2048, MemorySettingsUtil.NO_XMX_IN_VM_ARGS);
  }

  private void checkXmx(String text, int expectedGradleXmx, int expectedKotlinXmx) throws Exception {
    File propertiesFilePath = createTempFile("gradle.properties", text);
    GradleProperties properties = new GradleProperties(propertiesFilePath);
    assertEquals(expectedGradleXmx, getGradleDaemonXmx(properties));
    assertEquals(expectedKotlinXmx, getKotlinDaemonXmx(properties));
  }

  public void testGetDaemonXmx() throws Exception {
    checkXmx("org.gradle.jvmargs=-Xmx", -1, -1);
    checkXmx("org.gradle.jvmargs=-XmxT", -1, -1);
    checkXmx("org.gradle.jvmargs=-Xmx1a", -1, -1);
    checkXmx("org.gradle.jvmargs=-Xmx1T", 1024 * 1024, -1);
    checkXmx("org.gradle.jvmargs=-Xmx2t", 2 * 1024 * 1024, -1);
    checkXmx("org.gradle.jvmargs=-Xmx1G", 1024, -1);
    checkXmx("org.gradle.jvmargs=-Xmx4g", 4 * 1024, -1);
    checkXmx("org.gradle.jvmargs=-Xmx1M", 1, -1);
    checkXmx("org.gradle.jvmargs=-Xmx10m", 10, -1);
    checkXmx("org.gradle.jvmargs=-Xmx1024K", 1, -1);
    checkXmx("org.gradle.jvmargs=-Xmx2048k", 2, -1);

    checkXmx("org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xmx\"", -1, -1);
    checkXmx("org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-XmxT\"", -1, -1);
    checkXmx("org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xmx1a\"", -1, -1);
    checkXmx("org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xms1G\"", -1, -1);
    checkXmx("org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xmx1T\"", -1, 1024 * 1024);
    checkXmx("org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xmx2G,-Xms1G\"", -1, 2048);
    checkXmx("org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xmx1024m,-Xmx2G\"", -1, 2048);

    checkXmx("org.gradle.jvmargs=-Xms1G -Xmx4G -Dkotlin.daemon.jvm.options=\"-Xms1G\"", 4096, -1);
    checkXmx("org.gradle.jvmargs=-Xmx1G -Xmx4G -Dkotlin.daemon.jvm.options=\"-Xmx2G\"", 4096, 2048);
    checkXmx("org.gradle.jvmargs=-Xmx4G -Dkotlin.daemon.jvm.options=\"-Xms1G,-Xmx2G\"", 4096, 2048);
    checkXmx("org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xms1G,-Xmx2G\" -Xmx4G", 4096, 2048);
    checkXmx("org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xmx1G,-Xmx2G\" -Xmx3G -Xmx4G", 4096, 2048);
  }

  public void testSetDaemonXmx() throws Exception {
    checkXmxNewValue("", 10, -1);
    checkXmxNewValue("#org.gradle.jvmargs=-Xms1280m", 10, -1);
    checkXmxNewValue("org.gradle.jvmargs=-Xms1280m", 1024, -1);
    checkXmxNewValue("org.gradle.jvmargs=-Xmx1280m", 2048, -1);

    checkXmxNewValue("", -1, 1024);
    checkXmxNewValue("#org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xms1G\"", -1, 20);
    checkXmxNewValue("org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xms1G\"", -1, 1024);
    checkXmxNewValue("org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xmx1G\"", -1, 2048);
    checkXmxNewValue("org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xmx1G,-Xms512m\"", -1, 2048);
    checkXmxNewValue("org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xmx512m,-Xmx1G\"", -1, 2048);

    checkXmxNewValue("", 10, 20);
    checkXmxNewValue("#org.gradle.jvmargs=-Xms1280m", 10, 20);
    checkXmxNewValue("#org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xms1G\"", 10, 20);
    checkXmxNewValue("org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xms1G\"", 512, 2048);
    checkXmxNewValue("org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xmx1G\"", 512, 2048);
    checkXmxNewValue("org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xmx1G,-Xms512m\"", 512, 2048);
    checkXmxNewValue("org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xmx512m,-Xmx1G\"", 512, 2048);
    checkXmxNewValue("org.gradle.jvmargs=-Xmx1G -Dkotlin.daemon.jvm.options=\"-Xmx1G,-Xms512m\"", 512, 2048);
    checkXmxNewValue("org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xmx512m,-Xmx1G\" -Xmx1G", 512, 2048);
  }

  private void checkXmxNewValue(String text, int newGradleXmx, int newKotlinXmx) throws Exception {
    File propertiesFilePath = createTempFile("gradle.properties", text);
    GradleProperties properties = new GradleProperties(propertiesFilePath);
    setDaemonXmx(properties, newGradleXmx, newKotlinXmx);
    assertEquals(newGradleXmx, getGradleDaemonXmx(properties));
    assertEquals(newKotlinXmx, getKotlinDaemonXmx(properties));
  }

  public void testJvmArgsAfterSetDaemonXmx() throws Exception {
    assertEquals("-Xmx1024M", setDaemonXmx("", 1024, -1));
    assertEquals("-Xms512m -Xmx1024M", setDaemonXmx("-Xms512m", 1024, -1));
    assertEquals("-Xmx1024M", setDaemonXmx("-Xmx512m", 1024, -1));
    assertEquals("-Xms512m -Xmx2048M",
                 setDaemonXmx("-Xms512m -Xmx1G", 2048, -1));
    assertEquals("-Xms512m -Xmx768m -Xmx2048M",
                 setDaemonXmx("-Xms512m -Xmx768m -Xmx1G", 2048, -1));

    assertEquals("-Dkotlin.daemon.jvm.options=\"-Xmx1024M\"",
                 setDaemonXmx("", -1, 1024));
    assertEquals("-Dkotlin.daemon.jvm.options=\"-Xms512m,-Xmx1024M\"",
                 setDaemonXmx("-Dkotlin.daemon.jvm.options=\"-Xms512m\"", -1, 1024));
    assertEquals("-Dkotlin.daemon.jvm.options=\"-Xmx1024M\"",
                 setDaemonXmx("-Dkotlin.daemon.jvm.options=\"-Xmx512m\"", -1, 1024));
    assertEquals("-Dkotlin.daemon.jvm.options=\"-Xms512m,-Xmx2048M\"",
                 setDaemonXmx("-Dkotlin.daemon.jvm.options=\"-Xms512m,-Xmx1G\"", -1, 2048));
    assertEquals("-Dkotlin.daemon.jvm.options=\"-Xms512m,-Xmx768m,-Xmx2048M\"",
                 setDaemonXmx("-Dkotlin.daemon.jvm.options=\"-Xms512m,-Xmx768m,-Xmx1G\"", -1, 2048));

    assertEquals("-Xmx512M -Dkotlin.daemon.jvm.options=\"-Xmx1024M\"",
                 setDaemonXmx("", 512, 1024));
    assertEquals("-Dkotlin.daemon.jvm.options=\"-Xms512m,-Xmx1024M\" -Xmx512M",
                 setDaemonXmx("-Dkotlin.daemon.jvm.options=\"-Xms512m\"", 512, 1024));
    assertEquals("-Dkotlin.daemon.jvm.options=\"-Xmx1024M\" -Xmx512M",
                 setDaemonXmx("-Dkotlin.daemon.jvm.options=\"-Xmx512m\"", 512, 1024));
    assertEquals("-Dkotlin.daemon.jvm.options=\"-Xms512m,-Xmx2048M\" -Xmx1024M",
                 setDaemonXmx("-Dkotlin.daemon.jvm.options=\"-Xms512m,-Xmx1G\"", 1024, 2048));
    assertEquals("-Xmx1024M -Dkotlin.daemon.jvm.options=\"-Xms512m,-Xmx2048M\"",
                 setDaemonXmx("-Xmx512M -Dkotlin.daemon.jvm.options=\"-Xms512m,-Xmx1G\"", 1024, 2048));
    assertEquals("-Xms512m -Xmx1024M -Dkotlin.daemon.jvm.options=\"-Xms512m,-Xmx768m,-Xmx2048M\"",
                 setDaemonXmx("-Xms512m -Xmx768m -Dkotlin.daemon.jvm.options=\"-Xms512m,-Xmx768m,-Xmx1G\"", 1024, 2048));
  }
}
