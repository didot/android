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
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.google.common.base.Objects;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a reference to another property or variable.
 */
public final class ReferenceTo {
  @NotNull private final String fullyQualifiedName;  // The internal fully qualified name of the DSL reference.
  @NotNull private final GradlePropertyModel propertyModel;
  @NotNull private final GradleDslElement scope;

  /**
   * Create a reference to a {@link GradlePropertyModel}
   * @param model the model we want to refer to.
   * @throws IllegalArgumentException if the model isn't of a valid existing property.
   */
  public ReferenceTo(@NotNull GradlePropertyModel model) {
    // TODO(xof): this constructor should probably not exist, or at least not be exported from the Dsl module.  (It's needed internally
    //  but should probably not be used in any external usage of the Dsl system.
    this(model, model);
  }

  /**
   * Create a reference to a {@link GradlePropertyModel}.
   * @param model the model we want to refer to.
   * @param context the context in which we want to refer to the model.
   * @throws IllegalArgumentException if the model isn't of a valid existing property.
   */
  public ReferenceTo(@NotNull GradlePropertyModel model, @NotNull GradlePropertyModel context) {
    if (model.getRawElement() != null) {
      fullyQualifiedName = model.getFullyQualifiedName();
      propertyModel = model;
      scope = context.getRawPropertyHolder();
    }
    else {
      throw new IllegalArgumentException("Invalid model property: please check the property exists.");
    }
  }

  /**
   * Create a reference to a {@link SigningConfigModel}.
   * In this method the reference is set to a model, so we are dealing with internal names.
   * @param model the signingConfigModel we are trying to refer to.
   */
  public ReferenceTo(@NotNull SigningConfigModel model) {
    fullyQualifiedName = GradleNameElement.escape(model.name());
    propertyModel = GradlePropertyModelBuilder.createModelFromDslElement(model.getDslElement());
    scope = model.getDslElement().getParent().getParent();
  }

  /**
   * Create a reference to a dsl element given its name.
   * Please only consider using this function if you cannot fetch the {@link GradlePropertyModel} or the {@link SigningConfigModel} of the
   * element you want to refer to, as this function only guarantees a correct result if the {@param referredElementName} is in the expected
   * syntax.
   * @param referredElementName the name of the dslElement we are trying to set a reference to. This name should be the canonical name in
   *                            the external build language.
   * @param propertyContext the context where we are setting the reference. This is very important to determine the scoping of the lookup
   *                   in the dsl tree.
   * @return a referenceTo referring to a {@link GradlePropertyModel} if found, or null.
   */
  @Nullable
  public static ReferenceTo createReferenceFromText(@NotNull String referredElementName, @NotNull GradlePropertyModel propertyContext) {
    GradlePropertyModel referenceModel =
      GradlePropertyModelBuilder.getModelFromExternalText(referredElementName, propertyContext.getRawPropertyHolder());
    if (referenceModel == null) {
      return null;
    }
    return new ReferenceTo(referenceModel, propertyContext);
  }

  @NotNull
  public GradleDslElement getReferredElement() {
    return propertyModel.getRawElement();
  }

  /**
   * Get the reference fully qualified dsl name.
   * @return the reference text.
   */
  @NotNull
  public String getFullyQualifiedName() {
    return fullyQualifiedName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ReferenceTo text = (ReferenceTo)o;
    return Objects.equal(fullyQualifiedName, text.fullyQualifiedName);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(fullyQualifiedName);
  }

  @Override
  @NotNull
  public String toString() {
    List<String> scopeNameParts = GradleNameElement.split(scope.getQualifiedName());
    List<String> resultParts = GradleNameElement.split(propertyModel.getFullyQualifiedName());
    int r = 0;
    int s = 0;
    if ("buildscript".equals(scopeNameParts.get(s))) s++;
    if ("buildscript".equals(resultParts.get(r))) r++;
    if ("ext".equals(scopeNameParts.get(s))) s++;
    if ("ext".equals(resultParts.get(r))) r++;
    if (r >= resultParts.size()) return GradleNameElement.join(resultParts);
    while (r < resultParts.size() && s < scopeNameParts.size() && java.util.Objects.equals(resultParts.get(r), scopeNameParts.get(s))) {
      r++;
      s++;
    }
    return GradleNameElement.join(resultParts.subList(r, resultParts.size()));
  }
}
