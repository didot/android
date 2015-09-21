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
package org.jetbrains.android.run;

import com.android.ddmlib.IDevice;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A target chooser for picking cloud matrix test deploy targets.
 */
public class CloudMatrixTargetChooser implements TargetChooser {

  private final int myMatrixConfigurationId;
  @NotNull private final String myCloudProjectId;

  public CloudMatrixTargetChooser(int matrixConfigurationId, @NotNull String cloudProjectId) {
    myMatrixConfigurationId = matrixConfigurationId;
    myCloudProjectId = cloudProjectId;
  }

  @Nullable
  @Override
  public CloudMatrixTarget getTarget() {
    return new CloudMatrixTarget(myMatrixConfigurationId, myCloudProjectId);
  }

  @Override
  public boolean matchesDevice(@NotNull IDevice device) {
    return false;
  }
}
