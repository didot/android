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
package com.android.tools.idea.gradle.dsl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Represents an element which consists a list of {@link LiteralElement}s.
 */
final class LiteralListElement extends GradleDslElement {
  @NotNull private final List<LiteralElement> myElements = Lists.newArrayList();
  @NotNull private final List<LiteralElement> myToBeAddedElements = Lists.newArrayList();
  @NotNull private final List<LiteralElement> myToBeRemovedElements = Lists.newArrayList();

  LiteralListElement(@Nullable GradleDslElement parent, @NotNull String name) {
    super(parent, null, name);
  }

  LiteralListElement(@NotNull GradleDslElement parent, @NotNull String name, @NotNull GrListOrMap list) {
    super(parent, list, name);
    assert !list.isMap();
    for (GrExpression exp : list.getInitializers()) {
      if (exp instanceof GrLiteral) {
        myElements.add(new LiteralElement(this, list, name, (GrLiteral)exp));
      }
    }
  }

  LiteralListElement(@NotNull GradleDslElement parent, @NotNull GroovyPsiElement psiElement, @NotNull String name, @NotNull GrLiteral... literals) {
    super(parent, psiElement, name);
    for (GrLiteral literal : literals) {
      myElements.add(new LiteralElement(this, psiElement, name, literal));
    }
  }

  void add(@NotNull GroovyPsiElement psiElement, @NotNull String name, @NotNull GrLiteral... literals) {
    setPsiElement(psiElement);
    for (GrLiteral literal : literals) {
      myElements.add(new LiteralElement(this, psiElement, name, literal));
    }
  }

  @NotNull
  String getName() {
    return myName;
  }

  @NotNull
  List<LiteralElement> getElements() {
    if (myToBeAddedElements.isEmpty() && myToBeRemovedElements.isEmpty()) {
      return ImmutableList.copyOf(myElements);
    }

    List<LiteralElement> result = Lists.newArrayList();
    result.addAll(myElements);
    result.addAll(myToBeAddedElements);
    for (LiteralElement element : myToBeRemovedElements) {
      result.remove(element);
    }
    return result;
  }

  void add(@NotNull LiteralElement... elements) {
    myToBeAddedElements.addAll(Arrays.asList(elements));
    setModified(true);
  }

  void add(@NotNull Object elementValue) {
    LiteralElement element = new LiteralElement(this, myName);
    element.setValue(elementValue);
    myToBeAddedElements.add(element);
  }

  void remove(@NotNull Object elementValue) {
    for (LiteralElement element : getElements()) {
      if (elementValue.equals(element.getValue())) {
        myToBeRemovedElements.add(element);
        setModified(true);
        return;
      }
    }
  }

  void replace(@NotNull Object oldElementValue, @NotNull Object newElementValue) {
    for (LiteralElement element : getElements()) {
      if (oldElementValue.equals(element.getValue())) {
        element.setValue(newElementValue);
        return;
      }
    }
  }

  /**
   * Returns the list of values of type {@code clazz}.
   *
   * <p>Returns an empty list when there are no elements of type {@code clazz}.
   */
  @NotNull
  <E> List<E> getValues(Class<E> clazz) {
    List<E> result = Lists.newArrayList();
    for (LiteralElement element : getElements()) {
      E value = element.getValue(clazz);
      if (value != null) {
        result.add(value);
      }
    }
    return result;
  }

  @Override
  @Nullable
  protected GroovyPsiElement create() {
    GroovyPsiElement psiElement = super.create();
    if (psiElement == null) {
      return null;
    }

    if (psiElement instanceof GrListOrMap) {
      return psiElement;
    }

    if (psiElement instanceof GrArgumentList) {
      if (((GrArgumentList)psiElement).getAllArguments().length == 1) {
        // Sometimes it's not possible to append to the arguments list with one item. eg. proguardFile "xyz".
        // Set the psiElement to null and create a new psiElement of an empty application statement.
        setPsiElement(null);
        psiElement = super.create();
      }
      else {
        return psiElement;
      }
    }

    if (psiElement instanceof GrApplicationStatement) {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(psiElement.getProject());
      GrArgumentList argumentList = factory.createArgumentListFromText("xyz");
      argumentList.getFirstChild().delete(); // Workaround to get an empty argument list.
      PsiElement added = psiElement.addAfter(argumentList, psiElement.getLastChild());
      if (added instanceof GrArgumentList) {
        GrArgumentList addedArgumentList = (GrArgumentList)added;
        setPsiElement(addedArgumentList);
        return addedArgumentList;
      }
    }

    return null;
  }

  @Override
  protected void apply() {
    GroovyPsiElement psiElement = create();
    if (psiElement != null) {
      for (LiteralElement element : myToBeAddedElements) {
        element.setPsiElement(psiElement);
        element.applyChanges();
        myElements.add(element);
      }
    }
    myToBeAddedElements.clear();

    for (LiteralElement element : myToBeRemovedElements) {
      if (myElements.remove(element)) {
        element.delete();
      }
    }
    myToBeRemovedElements.clear();

    for (LiteralElement element : myElements) {
      if (element.isModified()) {
        element.applyChanges();
      }
    }
  }

  @Override
  protected void reset() {
    myToBeAddedElements.clear();
    myToBeRemovedElements.clear();
    for (LiteralElement element : myElements) {
      if (element.isModified()) {
        element.resetState();
      }
    }
  }

  @Override
  @NotNull
  protected Collection<GradleDslElement> getChildren() {
    return ImmutableList.of();
  }
}
