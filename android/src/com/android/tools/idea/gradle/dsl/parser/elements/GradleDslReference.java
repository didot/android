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
package com.android.tools.idea.gradle.dsl.parser.elements;

import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Represents a reference expression.
 */
public final class GradleDslReference extends GradleDslSettableExpression {
  public GradleDslReference(@NotNull GradleDslElement parent,
                            @NotNull PsiElement psiElement,
                            @NotNull GradleNameElement name,
                            @NotNull PsiElement reference) {
    super(parent, psiElement, name, reference);
  }

  public GradleDslReference(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, null, name, null);
  }

  @Override
  @NotNull
  public Collection<GradleDslElement> getChildren() {
    return ImmutableList.of();
  }

  @Nullable
  public String getReferenceText() {
    PsiElement element = getCurrentElement();
    return element != null ? element.getText() : null;
  }

  @Override
  @Nullable
  public Object getValue() {
    GradleDslLiteral valueLiteral = getValue(GradleDslLiteral.class);
    return valueLiteral != null ? valueLiteral.getValue() : getValue(String.class);
  }

  /**
   * Returns the same as getReferenceText if you need to get the unresolved version of what this
   * reference refers to then use getResolvedVariables and call getRawValue on the result.
   */
  @Override
  @Nullable
  public Object getUnresolvedValue() {
    return getReferenceText();
  }

  @Nullable
  public GradleReferenceInjection getReferenceInjection() {
    return myDependencies.isEmpty() ? null : myDependencies.get(0);
  }

  /**
   * Returns the value of type {@code clazz} when the reference expression is referring to an element with the value
   * of that type, or {@code null} otherwise.
   */
  @Override
  @Nullable
  public <T> T getValue(@NotNull Class<T> clazz) {
    String referenceText = getReferenceText();
    if (referenceText == null) {
      return null;
    }
    return resolveReference(referenceText, clazz);
  }

  @Override
  @Nullable
  public <T> T getUnresolvedValue(@NotNull Class<T> clazz) {
    Object value = getUnresolvedValue();
    if (value != null && clazz.isAssignableFrom(value.getClass())) {
      return clazz.cast(value);
    }
    return null;
  }

  @Override
  public void setValue(@NotNull Object value) {
    checkForValidValue(value);
    PsiElement element =
      ApplicationManager.getApplication().runReadAction((Computable<PsiElement>)() -> getDslFile().getParser().convertToPsiElement(value));
    setUnsavedValue(element);
    valueChanged();
  }

  @Override
  protected void apply() {
    getDslFile().getWriter().applyDslReference(this);
  }

  @Override
  @Nullable
  public PsiElement create() {
    return getDslFile().getWriter().createDslReference(this);
  }

  @Override
  public void delete() {
    getDslFile().getWriter().deleteDslReference(this);
  }

  @Override
  @NotNull
  protected List<GradleReferenceInjection> fetchDependencies(@Nullable PsiElement element) {
    if (element == null) {
      return ImmutableList.of();
    }

    GradleReferenceInjection injection = findInjection(element);
    return injection == null ? ImmutableList.of() : ImmutableList.of(injection);
  }

  @Nullable
  private GradleReferenceInjection findInjection(@NotNull PsiElement currentElement) {
    String text = currentElement.getText();
    if (text == null) {
      return null;
    }

    // Resolve our reference
    GradleDslElement element = resolveReference(text, true);
    return new GradleReferenceInjection(this, element, currentElement, text);
  }
}
