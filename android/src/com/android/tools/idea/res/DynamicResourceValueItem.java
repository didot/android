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
package com.android.tools.idea.res;

import com.android.annotations.NonNull;
import com.android.builder.model.ClassField;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.res2.SourcelessResourceItem;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link ResourceItem} for an item that is dynamically defined in a Gradle file. This needs a special class because (1) we can't rely on
 * the normal resource value parser to create resource values from XML, and (2) we need to implement getQualifiers since there is no source
 * file.
 */
public class DynamicResourceValueItem extends SourcelessResourceItem {
  public DynamicResourceValueItem(@Nullable String namespace,
                                  @NonNull ResourceType type,
                                  @NonNull ClassField field) {
    // Dynamic values are always in the "current module", so they don't live in a namespace.
    super(field.getName(), namespace, type, null, null);
    mResourceValue = new ResourceValue(ResourceUrl.create(namespace, type, field.getName()), field.getValue());
  }

  @NonNull
  @Override
  public String getQualifiers() {
    return "";
  }

  @NotNull
  public ResolveResult createResolveResult() {
    return new ResolveResult() {
      @Nullable
      @Override
      public PsiElement getElement() {
        // TODO: Try to find the item in the Gradle files
        return null;
      }

      @Override
      public boolean isValidResult() {
        return false;
      }
    };
  }
}
