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
package com.android.tools.idea.run;

import com.android.build.OutputFile;
import com.android.builder.model.Variant;
import com.android.ddmlib.IDevice;
import com.android.ide.common.build.GenericBuiltArtifacts;
import com.android.ide.common.build.GenericBuiltArtifactsSplitOutputMatcher;
import com.android.ide.common.build.SplitOutputMatcher;
import com.google.common.base.Joiner;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Find the best output for the selected device and variant when multiple splits are available.
 */
class BestOutputFinder {
  @NotNull
  File findBestOutput(@NotNull Variant variant, @NotNull IDevice device, @NotNull List<? extends OutputFile> outputs)
    throws ApkProvisionException {
    if (outputs.isEmpty()) {
      throw new ApkProvisionException("No outputs for the main artifact of variant: " + variant.getDisplayName());
    }
    return doFindBestOutput(variant, device, outputs, null);
  }

  @NotNull
  File findBestOutput(@NotNull Variant variant, @NotNull IDevice device, @NotNull GenericBuiltArtifacts builtArtifact)
    throws ApkProvisionException {
    return doFindBestOutput(variant, device, null, builtArtifact);
  }

  @NotNull
  private static File doFindBestOutput(@NotNull Variant variant,
                                       @NotNull IDevice device,
                                       @Nullable List<? extends OutputFile> outputs,
                                       @Nullable GenericBuiltArtifacts builtArtifact)
    throws ApkProvisionException {
    Set<String> variantAbiFilters = variant.getMainArtifact().getAbiFilters();
    int density = device.getDensity();
    List<String> abis = device.getAbis();
    List<File> apkFiles = new ArrayList<>();
    int outputCount = 0;
    if (outputs != null) {
      apkFiles =
        ContainerUtil.map(SplitOutputMatcher.computeBestOutput(outputs, variantAbiFilters, density, abis), OutputFile::getOutputFile);
      outputCount = outputs.size();
    }
    if (builtArtifact != null) {
      apkFiles = GenericBuiltArtifactsSplitOutputMatcher.INSTANCE.computeBestOutput(builtArtifact, variantAbiFilters, density, abis);
      outputCount = builtArtifact.getElements().size();
    }
    if (apkFiles.isEmpty()) {
      String message = AndroidBundle.message("deployment.failed.splitapk.nomatch",
                                             variant.getDisplayName(),
                                             outputCount,
                                             density,
                                             Joiner.on(", ").join(abis));
      throw new ApkProvisionException(message);
    }
    // Install apk (note that variant.getOutputFile() will point to a .aar in the case of a library).
    return apkFiles.get(0);
  }
}
