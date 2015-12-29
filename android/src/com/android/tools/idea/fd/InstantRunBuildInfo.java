/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.fd;

import com.android.builder.model.InstantRun;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.utils.XmlUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;

/**
 * {@link InstantRunBuildInfo} models the build-info.xml file that is generated by an instant-run aware Gradle build.
 */
public class InstantRunBuildInfo {
  private static final String ATTR_BUILD_ID = "build-id";

  private static final String ATTR_VERIFIER_STATUS = "verifier";

  // Note: The verifier status can be any number of values (See InstantRunVerifierStatus enum in gradle).
  // Currently, the only contract between gradle and the IDE is that the value is set to COMPATIBLE if the build can be hotswapped
  private static final String VALUE_VERIFIER_STATUS_COMPATIBLE = "COMPATIBLE";

  @NotNull private final Element myRoot;

  public InstantRunBuildInfo(@NotNull Element root) {
    myRoot = root;
  }

  @NotNull
  public String getBuildId() {
    return myRoot.getAttribute(ATTR_BUILD_ID);
  }

  @NotNull
  public String getVerifierStatus() {
    return myRoot.getAttribute(ATTR_VERIFIER_STATUS);
  }

  public boolean canHotswap() {
    String verifierStatus = getVerifierStatus();
    return StringUtil.isEmpty(verifierStatus) // not populated if there were no changes in versions <= 2.0.0-alpha3
           || VALUE_VERIFIER_STATUS_COMPATIBLE.equals(verifierStatus);
  }

  @Nullable
  public static InstantRunBuildInfo get(@NotNull AndroidGradleModel model) {
    File buildInfo = getLocalBuildInfoFile(model);
    if (!buildInfo.exists()) {
      return null;
    }

    String xml;
    try {
      xml = Files.toString(buildInfo, Charsets.UTF_8);
    }
    catch (IOException e) {
      return null;
    }

    return getInstantRunBuildInfo(xml);
  }

  @VisibleForTesting
  @Nullable
  static InstantRunBuildInfo getInstantRunBuildInfo(@NotNull String xml) {
    Document doc = XmlUtils.parseDocumentSilently(xml, false);
    if (doc == null) {
      return null;
    }

    return new InstantRunBuildInfo(doc.getDocumentElement());
  }

  @NotNull
  private static File getLocalBuildInfoFile(@NotNull AndroidGradleModel model) {
    InstantRun instantRun = model.getSelectedVariant().getMainArtifact().getInstantRun();

    File file = instantRun.getInfoFile();
    if (!file.exists()) {
      // Temporary hack workaround; model is passing the wrong value! See InstantRunAnchorTask.java
      file = new File(instantRun.getRestartDexFile().getParentFile(), "build-info.xml");
    }

    return file;
  }
}
