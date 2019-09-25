/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.api.ext;

import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;

/**
 * Represents a reference to another property or variable.
 */
public final class ReferenceTo extends RawText {
  @NotNull private static final String SIGNING_CONFIGS = "signingConfigs";

  public ReferenceTo(@NotNull String text) {
    super(text);
  }

  public ReferenceTo(@NotNull GradlePropertyModel model) {
    super(model.getFullyQualifiedName());
  }

  public ReferenceTo(@NotNull SigningConfigModel model, boolean isGroovy) {
    super(SIGNING_CONFIGS + "." + (isGroovy ? model.name() : "getByName(\"" + model.name() + "\")"));
  }

  public static ReferenceTo createForSigningConfig(@NotNull String signingConfigName, boolean isGroovy) {
    String signingConfigRefValue = SIGNING_CONFIGS + "." + (isGroovy ? signingConfigName : "getByName(\"" + signingConfigName + "\")");
    return new ReferenceTo(signingConfigRefValue);
  }
}
