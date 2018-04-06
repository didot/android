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

import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.model.values.GradleNotNullValueImpl;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents an element which consists a list of {@link GradleDslSimpleExpression}s.
 */
public final class GradleDslExpressionList extends GradleDslElementImpl {
  @NotNull private final List<GradleDslSimpleExpression> myExpressions = Lists.newArrayList();
  @NotNull private final List<GradleDslSimpleExpression> myUnsavedExpressions = Lists.newArrayList();

  private final boolean myAppendToArgumentListWithOneElement;

  // Is this GradleDslExpressionList being used as an actual list. This is used when creating the element to
  // work out whether we need to wrap this list in brackets. For example expression lists are used for literals lists
  // like "prop = ['value1', 'value2']" but can also be used for thing such as lint options "check 'check-id-1', 'check-id-2'"
  private boolean myIsLiteralList;

  public GradleDslExpressionList(@Nullable GradleDslElement parent, @NotNull GradleNameElement name, boolean isLiteralList) {
    super(parent, null, name);
    myAppendToArgumentListWithOneElement = false;
    myIsLiteralList = isLiteralList;
  }

  public GradleDslExpressionList(@NotNull GradleDslElement parent, @NotNull PsiElement psiElement,  boolean isLiteralList, @NotNull GradleNameElement name) {
    super(parent, psiElement, name);
    myAppendToArgumentListWithOneElement = false;
    myIsLiteralList = isLiteralList;
  }

  public GradleDslExpressionList(@NotNull GradleDslElement parent,
                                 @NotNull PsiElement psiElement,
                                 @NotNull GradleNameElement name,
                                 boolean appendToArgumentListWithOneElement) {
    super(parent, psiElement, name);
    myAppendToArgumentListWithOneElement = appendToArgumentListWithOneElement;
    myIsLiteralList = false;
  }

  public void addParsedExpression(@NotNull GradleDslSimpleExpression expression) {
    expression.myParent = this;
    myExpressions.add(expression);
    myUnsavedExpressions.add(expression);
  }

  public void addNewExpression(@NotNull GradleDslSimpleExpression expression, int index) {
    expression.myParent = this;
    myUnsavedExpressions.add(index, expression);
    setModified(true);
    updateDependenciesOnAddElement(expression);
  }

  @SuppressWarnings("SuspiciousMethodCalls") // We pass in a superclass instance to remove.
  public void removeElement(@NotNull GradleDslElement element) {
    if (myUnsavedExpressions.remove(element)) {
      setModified(true);
    }
    updateDependenciesOnRemoveElement(element);
  }

  public GradleDslSimpleExpression getElementAt(int index) {
    if (index < 0 || index > myUnsavedExpressions.size()) {
      return null;
    }
    return myUnsavedExpressions.get(index);
  }

  @SuppressWarnings("SuspiciousMethodCalls") // We pass in a superclass instance to remove.
  public int findIndexOf(@NotNull GradleDslElement element) {
    return myUnsavedExpressions.indexOf(element);
  }

  void addNewLiteral(@NotNull Object value) {
    GradleDslLiteral literal = new GradleDslLiteral(this, myName);
    literal.setValue(value);
    myUnsavedExpressions.add(literal);
    setModified(true);
  }

  void removeExpression(@NotNull Object value) {
    for (GradleDslSimpleExpression expression : myUnsavedExpressions) {
      if (value.equals(expression.getValue())) {
        myUnsavedExpressions.remove(expression);
        setModified(true);
        updateDependenciesOnRemoveElement(expression);
        return;
      }
    }
  }

  void replaceExpression(@NotNull Object oldValue, @NotNull Object newValue) {
    for (GradleDslSimpleExpression expression : myUnsavedExpressions) {
      if (oldValue.equals(expression.getValue())) {
        expression.setValue(newValue);
        return;
      }
    }
  }

  public void replaceExpression(@NotNull GradleDslSimpleExpression oldExpression, @NotNull GradleDslSimpleExpression newExpression) {
    assert oldExpression.getFullName().equals(newExpression.getFullName());
    int index = myUnsavedExpressions.indexOf(oldExpression);
    assert index != -1;
    myUnsavedExpressions.set(index, newExpression);
    updateDependenciesOnReplaceElement(oldExpression, newExpression);
  }

  @NotNull
  public List<GradleDslSimpleExpression> getExpressions() {
    List<GradleDslSimpleExpression> result = Lists.newArrayList();
    result.addAll(myUnsavedExpressions);
    return result;
  }

  public boolean isLiteralList() {
    return myIsLiteralList;
  }

  public boolean isAppendToArgumentListWithOneElement() {
    return myAppendToArgumentListWithOneElement;
  }

  /**
   * This method should <b>not</b> be called outside of the GradleDslWriter classes.
   * <p>
   * If you need to add expressions to this GradleDslExpressionList please use
   * {@link #addNewExpression(GradleDslSimpleExpression) addNewExpression} followed by a call to {@link #apply() apply}
   * to ensure the changes are written to the underlying file.
   */
  public void commitExpressions(@NotNull PsiElement psiElement) {
    for (GradleDslSimpleExpression expression : myUnsavedExpressions) {
      if (expression.getPsiElement() == null) {
        expression.setPsiElement(psiElement);
        expression.applyChanges();
      }
    }
    saveExpressions();
  }

  /**
   * Returns the list of values of type {@code clazz}.
   *
   * <p>Returns an empty list when there are no elements of type {@code clazz}.
   */
  @NotNull
  public <E> List<GradleNotNullValue<E>> getValues(Class<E> clazz) {
    List<GradleNotNullValue<E>> result = Lists.newArrayList();
    for (GradleDslSimpleExpression expression : getExpressions()) {
      if (expression instanceof GradleDslReference) {
        // See if the reference itself is pointing to a list.
        GradleDslExpressionList referenceList = expression.getValue(GradleDslExpressionList.class);
        if (referenceList != null) {
          result.addAll(referenceList.getValues(clazz));
          continue;
        }
      }
      E value = expression.getValue(clazz);
      if (value != null) {
        result.add(new GradleNotNullValueImpl<>(expression, value));
      }
    }
    return result;
  }

  @Override
  @Nullable
  public PsiElement create() {
    return getDslFile().getWriter().createDslExpressionList(this);
  }

  @Override
  protected void apply() {
    PsiElement psiElement = create();

    getDslFile().getWriter().applyDslExpressionList(this);

    for (GradleDslSimpleExpression expression : myExpressions) {
      if (!myUnsavedExpressions.contains(expression)) {
        expression.delete();
      }
    }

    if (psiElement != null) {
      for (int i = 0; i < myUnsavedExpressions.size(); i++) {
        GradleDslSimpleExpression expression = myUnsavedExpressions.get(i);
        if (expression.getPsiElement() == null) {
          // See GroovyDslUtil#shouldAddToListInternal for why this workaround is needed.
          if (i > 0) {
            expression.setPsiElement(myUnsavedExpressions.get(i - 1).getExpression());
          } else {
            expression.setPsiElement(psiElement);
          }
          expression.applyChanges();
          if (i > 0) {
            expression.setPsiElement(psiElement);
          }
        }
      }
    }
    saveExpressions();

    for (GradleDslSimpleExpression expression : myExpressions) {
      if (expression.isModified()) {
        expression.applyChanges();
      }
    }
  }

  @Override
  protected void reset() {
    myUnsavedExpressions.clear();
    myUnsavedExpressions.addAll(myExpressions);
    for (GradleDslSimpleExpression expression : myExpressions) {
      if (expression.isModified()) {
        expression.resetState();
      }
    }
  }

  @Override
  @NotNull
  public Collection<GradleDslElement> getChildren() {
    List<GradleDslSimpleExpression> expressions = getExpressions();
    List<GradleDslElement> children = new ArrayList<>(expressions.size());
    children.addAll(expressions);
    return children;
  }

  private void saveExpressions() {
    myExpressions.clear();
    myExpressions.addAll(myUnsavedExpressions);
  }

  @Override
  @NotNull
  public List<GradleReferenceInjection> getDependencies() {
    return myUnsavedExpressions.stream().map(GradleDslElement::getDependencies).flatMap(Collection::stream).collect(Collectors.toList());
  }
}
