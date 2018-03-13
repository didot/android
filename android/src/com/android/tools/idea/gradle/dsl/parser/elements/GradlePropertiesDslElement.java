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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.values.GradleNotNullValueImpl;
import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValueImpl;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Base class for {@link GradleDslElement}s that represent a closure block or a map element. It provides the functionality to store the
 * data as key value pairs and convenient methods to access the data.
 *
 * TODO: Rename this class to something different as this will be conflicting with GradlePropertiesModel
 */
public abstract class GradlePropertiesDslElement extends GradleDslElement {
  @NotNull private final static Predicate<ElementList.ElementItem> VARIABLE_FILTER =
    e -> e.myElement.getElementType() == PropertyType.VARIABLE;
  // This filter currently gives us everything that is not a variable.
  @NotNull private final static Predicate<ElementList.ElementItem> PROPERTY_FILTER = VARIABLE_FILTER.negate();
  @NotNull private final static Predicate<ElementList.ElementItem> ANY_FILTER = e -> true;

  @NotNull private final ElementList myProperties = new ElementList();

  protected GradlePropertiesDslElement(@Nullable GradleDslElement parent,
                                       @Nullable PsiElement psiElement,
                                       @NotNull GradleNameElement name) {
    super(parent, psiElement, name);
  }

  /**
   * Adds the given {@code property}. All additions to {@code myProperties} should be made via this function to
   * ensure that {@code myVariables} is also updated.
   *
   * @param element the {@code GradleDslElement} for the property.
   */
  private void addPropertyInternal(@NotNull GradleDslElement element, @NotNull ElementState state) {
    myProperties.addElement(element, state);
  }

  private void addPropertyInternal(int index, @NotNull GradleDslElement element, @NotNull ElementState state) {
    myProperties.addElementAtIndex(element, state, index);
  }

  private void addAppliedProperty(@NotNull GradleDslElement element) {
    element.myHolders.add(this);
    addPropertyInternal(element, ElementState.APPLIED);
  }

  private void removePropertyInternal(@NotNull String property) {
    myProperties.removeAll(e -> e.myElement.getName().equals(property));
  }

  /**
   * Removes the property by the given element. Returns the OLD ElementItem that was used to
   * store the element.
   */
  private ElementState removePropertyInternal(@NotNull GradleDslElement element) {
    return myProperties.remove(element);
  }

  private void hidePropertyInternal(@NotNull String property) {
    myProperties.hideAll(e -> e.myElement.getName().equals(property));
  }

  public void addAppliedModelProperties(@NotNull GradleDslFile file) {
    // Here we need to merge the properties into from the applied file into this element.
    mergePropertiesFrom(file);
  }

  private void mergePropertiesFrom(@NotNull GradlePropertiesDslElement other) {
    Map<String, GradleDslElement> ourProperties = getPropertyElements();
    for (Map.Entry<String, GradleDslElement> entry : other.getPropertyElements().entrySet()) {
      if (ourProperties.containsKey(entry.getKey())) {
        GradleDslElement newProperty = entry.getValue();
        GradleDslElement existingProperty = getElementWhere(entry.getKey(), PROPERTY_FILTER);
        // If they are both block elements, merge them.
        if (newProperty instanceof GradleDslBlockElement && existingProperty instanceof GradleDslBlockElement) {
          ((GradlePropertiesDslElement)existingProperty).mergePropertiesFrom((GradlePropertiesDslElement)newProperty);
          continue;
        }
      }
      // Otherwise just add the new property.
      addAppliedProperty(entry.getValue());
    }
  }

  /**
   * Sets or replaces the given {@code property} value with the give {@code element}.
   *
   * <p>This method should be used when the given {@code property} is defined using an assigned statement.
   */
  public void setParsedElement(@NotNull GradleDslElement element) {
    element.myParent = this;
    addPropertyInternal(element, ElementState.EXISTING);
  }

  /**
   * Sets or replaces the given {@code property} value with the given {@code element}.
   *
   * <p>This method should be used when the given {@code property} is defined using an application statement. As the application statements
   * can have different meanings like append vs replace for list elements, the sub classes can override this method to do the right thing
   * for any given property.
   */
  public void addParsedElement(@NotNull GradleDslElement element) {
    element.myParent = this;
    addPropertyInternal(element, ElementState.EXISTING);
  }

  /**
   * Sets or replaces the given {@code property} value with the given {@code element}.
   *
   * <p>This method should be used when the given {@code property} would reset the effect of the other property. Ex: {@code reset()} method
   * in android.splits.abi block will reset the effect of the previously defined {@code includes} element.
   */
  protected void addParsedResettingElement(@NotNull GradleDslElement element, @NotNull String propertyToReset) {
    element.myParent = this;
    addPropertyInternal(element, ElementState.EXISTING);
    hidePropertyInternal(propertyToReset);
  }

  protected void addAsParsedDslExpressionList(GradleDslExpression expression) {
    PsiElement psiElement = expression.getPsiElement();
    if (psiElement == null) {
      return;
    }
    // Only elements which are added as expression list are the ones which supports both single argument and multiple arguments
    // (ex: flavorDimensions in android block). To support that, we create an expression list where appending to the arguments list is
    // supported even when there is only one element in it. This does not work in many other places like proguardFile elements where
    // only one argument is supported and for this cases we use addToParsedExpressionList method.
    GradleDslExpressionList literalList =
      new GradleDslExpressionList(this, psiElement, GradleNameElement.create(expression.getName()), true);
    if (expression instanceof GradleDslMethodCall) {
      // Make sure the psi is set to the argument list instead of the whole method call.
      literalList.setPsiElement(((GradleDslMethodCall)expression).getArgumentListPsiElement());
      for (GradleDslElement element : ((GradleDslMethodCall)expression).getArguments()) {
        if (element instanceof GradleDslExpression) {
          literalList.addParsedExpression((GradleDslExpression)element);
        }
      }
    }
    else {
      literalList.addParsedExpression(expression);
    }
    addPropertyInternal(literalList, ElementState.EXISTING);
  }

  public void addToParsedExpressionList(@NotNull String property, @NotNull GradleDslElement element) {
    PsiElement psiElement = element.getPsiElement();
    if (psiElement == null) {
      return;
    }

    GradleDslExpressionList gradleDslExpressionList = getPropertyElement(property, GradleDslExpressionList.class);
    if (gradleDslExpressionList == null) {
      gradleDslExpressionList = new GradleDslExpressionList(this, psiElement, GradleNameElement.create(property), false);
      addPropertyInternal(gradleDslExpressionList, ElementState.EXISTING);
    }
    else {
      gradleDslExpressionList.setPsiElement(psiElement);
    }

    if (element instanceof GradleDslExpression) {
      gradleDslExpressionList.addParsedExpression((GradleDslExpression)element);
    }
    else if (element instanceof GradleDslExpressionList) {
      List<GradleDslExpression> gradleExpressions = ((GradleDslExpressionList)element).getExpressions();
      for (GradleDslExpression expression : gradleExpressions) {
        gradleDslExpressionList.addParsedExpression(expression);
      }
    }
  }

  @NotNull
  public Set<String> getProperties() {
    return getPropertyElements().keySet();
  }

  /**
   * Note: This function does NOT guarantee that only elements belonging to properties are returned, since this class is also used
   * for maps it is also possible for the resulting elements to be of {@link PropertyType#DERIVED}.
   */
  @NotNull
  public Map<String, GradleDslElement> getPropertyElements() {
    return getElementsWhere(PROPERTY_FILTER);
  }

  @NotNull
  public Map<String, GradleDslElement> getVariableElements() {
    return getElementsWhere(VARIABLE_FILTER);
  }

  @NotNull
  public Map<String, GradleDslElement> getElements() {
    return getElementsWhere(ANY_FILTER);
  }

  @NotNull
  private Map<String, GradleDslElement> getElementsWhere(@NotNull Predicate<ElementList.ElementItem> predicate) {
    Map<String, GradleDslElement> results = new LinkedHashMap<>();
    List<GradleDslElement> elements = myProperties.getElementsWhere(predicate);
    for (GradleDslElement element : elements) {
      if (element != null) {
        results.put(element.getName(), element);
      }
    }
    return results;
  }

  private GradleDslElement getElementWhere(@NotNull String name, @NotNull Predicate<ElementList.ElementItem> predicate) {
    return getElementsWhere(predicate).get(name);
  }

  /**
   * Method to check whether a given property string is nested. A property counts as nested if it has move than one component
   * seperated dots ('.').
   */
  private static boolean isPropertyNested(@NotNull String property) {
    return property.contains(".");
  }

  @Nullable
  public GradleDslElement getVariableElement(@NotNull String property) {
    if (!isPropertyNested(property)) {
      return getElementWhere(property, VARIABLE_FILTER);
    }
    return searchForNestedProperty(property, GradlePropertiesDslElement::getVariableElement);
  }

  /**
   * Returns the {@link GradleDslElement} corresponding to the given {@code property}, or {@code null} if the given {@code property}
   * does not exist in this element.
   */
  @Nullable
  public GradleDslElement getPropertyElement(@NotNull String property) {
    if (!isPropertyNested(property)) {
      return getElementWhere(property, PROPERTY_FILTER);
    }
    return searchForNestedProperty(property, GradlePropertiesDslElement::getPropertyElement);
  }

  @Nullable
  public GradleDslElement getElement(@NotNull String property) {
    if (!isPropertyNested(property)) {
      return getElementWhere(property, ANY_FILTER);
    }
    return searchForNestedProperty(property, GradlePropertiesDslElement::getElement);
  }

  /**
   * Searches for a nested {@code property}.
   */
  @Nullable
  private GradleDslElement searchForNestedProperty(@NotNull String property,
                                                   @NotNull BiFunction<GradlePropertiesDslElement, String, GradleDslElement> func) {
    List<String> propertyNameSegments = Splitter.on('.').splitToList(property);
    GradlePropertiesDslElement nestedElement = this;
    for (int i = 0; i < propertyNameSegments.size() - 1; i++) {
      GradleDslElement element = nestedElement.getElement(propertyNameSegments.get(i).trim());
      if (element instanceof GradlePropertiesDslElement) {
        nestedElement = (GradlePropertiesDslElement)element;
      }
      else {
        return null;
      }
    }
    return func.apply(nestedElement, propertyNameSegments.get(propertyNameSegments.size() - 1));
  }

  /**
   * Returns the dsl element of the given {@code property} of the type {@code clazz}, or {@code null} if the given {@code property}
   * does not exist in this element.
   */
  @Nullable
  public <T extends GradleDslElement> T getPropertyElement(@NotNull String property, @NotNull Class<T> clazz) {
    GradleDslElement propertyElement = getPropertyElement(property);
    return clazz.isInstance(propertyElement) ? clazz.cast(propertyElement) : null;
  }

  @Nullable
  public <T extends GradleDslElement> T getPropertyElement(@NotNull List<String> properties, @NotNull Class<T> clazz) {
    GradleDslElement propertyElement = myProperties.getElementWhere(e -> properties.contains(e.myElement.getName()));
    return clazz.isInstance(propertyElement) ? clazz.cast(propertyElement) : null;
  }

  @NotNull
  public <T extends GradleDslElement> List<T> getPropertyElements(@NotNull Class<T> clazz) {
    Collection<GradleDslElement> propertyElements = getPropertyElements().values();
    return propertyElements.stream().filter(e -> clazz.isAssignableFrom(e.getClass())).map(e -> clazz.cast(e)).collect(Collectors.toList());
  }

  private static <T> GradleNullableValue<T> createAndWrapDslValue(@Nullable GradleDslElement element, @NotNull Class<T> clazz) {
    if (element == null) {
      return new GradleNullableValueImpl<>(null, null);
    }

    T resultValue = null;
    if (clazz.isInstance(element)) {
      resultValue = clazz.cast(element);
    }
    else if (element instanceof GradleDslExpression) {
      resultValue = ((GradleDslExpression)element).getValue(clazz);
    }

    if (resultValue != null) {
      return new GradleNotNullValueImpl<>(element, resultValue);
    }
    else {
      return new GradleNullableValueImpl<>(element, null);
    }
  }

  /**
   * Returns the literal value of the given {@code property} of the type {@code clazz} along with the variable resolution history.
   *
   * <p>The returned {@link GradleNullableValueImpl} may contain a {@code null} value when either the given {@code property} does not exists in
   * this element or the given {@code property} value is not of the type {@code clazz}.
   */
  @NotNull
  public <T> GradleNullableValue<T> getLiteralProperty(@NotNull String property, @NotNull Class<T> clazz) {
    Preconditions.checkArgument(clazz == String.class || clazz == Integer.class || clazz == Boolean.class);

    return createAndWrapDslValue(getPropertyElement(property), clazz);
  }

  /**
   * Adds the given element to the to-be added elements list, which are applied when {@link #apply()} method is invoked
   * or discarded when the {@lik #resetState()} method is invoked.
   */
  @NotNull
  public GradleDslElement setNewElement(@NotNull GradleDslElement newElement) {
    newElement.myParent = this;
    addPropertyInternal(newElement, ElementState.TO_BE_ADDED);
    setModified(true);
    return newElement;
  }

  @VisibleForTesting
  public void addNewElementAt(int index, @NotNull GradleDslElement newElement) {
    newElement.myParent = this;
    addPropertyInternal(index, newElement, ElementState.TO_BE_ADDED);
    setModified(true);
  }

  @NotNull
  public GradleDslElement replaceElement(@NotNull GradleDslElement oldElement, @NotNull GradleDslElement newElement) {
    List<GradlePropertiesDslElement> holders = new ArrayList<>();
    holders.add(this);
    holders.addAll(oldElement.myHolders);
    for (GradlePropertiesDslElement holder : holders) {
      ElementState state = holder.removePropertyInternal(oldElement);
      if (state != null) {
        if (state == ElementState.APPLIED) {
          holder.addPropertyInternal(newElement, ElementState.APPLIED);
        }
        else {
          setNewElement(newElement);
        }
      }
    }
    return newElement;
  }

  @NotNull
  public GradleDslElement setNewLiteral(@NotNull String property, @NotNull Object value) {
    return setNewLiteralImpl(property, value);
  }

  @NotNull
  private GradleDslElement setNewLiteralImpl(@NotNull String property, @NotNull Object value) {
    GradleDslLiteral literalElement = getPropertyElement(property, GradleDslLiteral.class);
    if (literalElement == null) {
      literalElement = new GradleDslLiteral(this, GradleNameElement.create(property));
      addPropertyInternal(literalElement, ElementState.TO_BE_ADDED);
    }
    literalElement.setValue(value);
    return literalElement;
  }

  @NotNull
  public GradlePropertiesDslElement addToNewLiteralList(@NotNull String property, @NotNull String value) {
    return addToNewLiteralListImpl(property, value);
  }

  @NotNull
  private GradlePropertiesDslElement addToNewLiteralListImpl(@NotNull String property, @NotNull Object value) {
    GradleDslExpressionList gradleDslExpressionList = getPropertyElement(property, GradleDslExpressionList.class);
    if (gradleDslExpressionList == null) {
      gradleDslExpressionList = new GradleDslExpressionList(this, GradleNameElement.create(property), false);
      addPropertyInternal(gradleDslExpressionList, ElementState.TO_BE_ADDED);
    }
    gradleDslExpressionList.addNewLiteral(value);
    return this;
  }

  @NotNull
  public GradlePropertiesDslElement removeFromExpressionList(@NotNull String property, @NotNull String value) {
    return removeFromExpressionListImpl(property, value);
  }

  @NotNull
  private GradlePropertiesDslElement removeFromExpressionListImpl(@NotNull String property, @NotNull Object value) {
    GradleDslExpressionList gradleDslExpressionList = getPropertyElement(property, GradleDslExpressionList.class);
    if (gradleDslExpressionList != null) {
      gradleDslExpressionList.removeExpression(value);
    }
    return this;
  }

  @NotNull
  public GradlePropertiesDslElement replaceInExpressionList(@NotNull String property, @NotNull String oldValue, @NotNull String newValue) {
    return replaceInExpressionListImpl(property, oldValue, newValue);
  }

  @NotNull
  private GradlePropertiesDslElement replaceInExpressionListImpl(@NotNull String property,
                                                                 @NotNull Object oldValue,
                                                                 @NotNull Object newValue) {
    GradleDslExpressionList gradleDslExpressionList = getPropertyElement(property, GradleDslExpressionList.class);
    if (gradleDslExpressionList != null) {
      gradleDslExpressionList.replaceExpression(oldValue, newValue);
    }
    return this;
  }

  /**
   * Marks the given {@code property} for removal.
   *
   * <p>The actual property will be removed from Gradle file when {@link #apply()} method is invoked.
   *
   * <p>The property will be un-marked for removal when {@link #reset()} method is invoked.
   */
  public GradlePropertiesDslElement removeProperty(@NotNull String property) {
    removePropertyInternal(property);
    setModified(true);
    return this;
  }

  public GradlePropertiesDslElement removeProperty(@NotNull GradleDslElement element) {
    removePropertyInternal(element);
    setModified(true);
    return this;
  }

  /**
   * Returns the list of values of type {@code clazz} when the given {@code property} corresponds to a {@link GradleDslExpressionList}.
   *
   * <p>Returns {@code null} when either the given {@code property} does not exists in this element or does not corresponds to a
   * {@link GradleDslExpressionList}.
   *
   * <p>Returns an empty list when the given {@code property} exists in this element and corresponds to a {@link GradleDslExpressionList}, but either
   * that list is empty or does not contain any element of type {@code clazz}.
   */
  @Nullable
  public <E> List<GradleNotNullValue<E>> getListProperty(@NotNull String property, @NotNull Class<E> clazz) {
    GradleDslExpressionList gradleDslExpressionList = getPropertyElement(property, GradleDslExpressionList.class);
    if (gradleDslExpressionList != null) {
      return gradleDslExpressionList.getValues(clazz);
    }
    return null;
  }

  @Override
  @Nullable
  public GradleDslElement requestAnchor(@NotNull GradleDslElement element) {
    // We need to find the element before `element` in my properties. The last one that has a psiElement, has the same name scheme as
    // the given element (to ensure that they should be placed in the same block) and much either have a state of TO_BE_ADDED or EXISTING.
    GradleDslElement lastElement = null;
    for (ElementList.ElementItem item : myProperties.myElements) {
      if (item.myElement == element) {
        return lastElement;
      }

      if (item.myElementState == ElementState.TO_BE_ADDED || item.myElementState == ElementState.EXISTING &&
          item.myElement.getNameElement().qualifyingParts().equals(element.getNameElement().qualifyingParts())) {
        // GradleDslElementLists do not have a PsiElement, as such we need to ask them where they should be placed.
        if (item.myElement instanceof GradleDslElementList) {
          lastElement = item.myElement.requestAnchor(element);
        } else {
          lastElement = item.myElement;
        }
      }
    }

    // The element is not in this list, we can't provide an anchor.
    return null;
  }

  @Override
  @NotNull
  public Collection<GradleDslElement> getChildren() {
    return getPropertyElements().values();
  }

  @Override
  public List<GradleDslElement> getContainedElements(boolean includeProperties) {
    List<GradleDslElement> result = new ArrayList<>();
    if (includeProperties) {
      result.addAll(getElementsWhere(e -> e.myElementState != ElementState.APPLIED).values());
    }
    else {
      result.addAll(getVariableElements().values());
    }

    List<GradlePropertiesDslElement> holders = getPropertyElements(GradlePropertiesDslElement.class);
    holders.forEach(e -> result.addAll(e.getContainedElements(includeProperties)));
    return result;
  }

  @Override
  protected void apply() {
    myProperties.removeElements(GradleDslElement::delete);
    myProperties.createElements((e) -> e.create() != null);
    myProperties.applyElements(e -> {
      if (e.isModified()) {
        e.apply();
      }
    });
  }

  @Override
  protected void reset() {
    myProperties.reset();
  }

  protected void clear() {
    myProperties.clear();
  }

  /**
   * Class to deal with retrieving the correct property for a given context. It manages whether
   * or not variable types should be returned along with coordinating a number of properties
   * with the same name.
   */
  private static class ElementList {
    /**
     * Wrapper to add state to each element.
     */
    private static class ElementItem {
      @NotNull private GradleDslElement myElement;
      @NotNull private ElementState myElementState;

      private ElementItem(@NotNull GradleDslElement element, @NotNull ElementState state) {
        myElement = element;
        myElementState = state;
      }
    }

    @NotNull private final List<ElementItem> myElements;

    private ElementList() {
      myElements = new ArrayList<>();
    }

    @NotNull
    private List<GradleDslElement> getElementsWhere(@NotNull Predicate<ElementItem> predicate) {
      return myElements.stream().filter(e -> e.myElementState != ElementState.TO_BE_REMOVED && e.myElementState != ElementState.HIDDEN)
        .filter(predicate).map(e -> e.myElement).collect(Collectors.toList());
    }

    @Nullable
    private GradleDslElement getElementWhere(@NotNull Predicate<ElementItem> predicate) {
      // We reduce to get the last element stored, this will be the one we want as it was added last and therefore must appear
      // later on in the file.
      return myElements.stream().filter(e -> e.myElementState != ElementState.TO_BE_REMOVED && e.myElementState != ElementState.HIDDEN)
        .filter(predicate).map(e -> e.myElement).reduce((first, second) -> second).orElse(null);
    }

    private void addElement(@NotNull GradleDslElement newElement, @NotNull ElementState state) {
      myElements.add(new ElementItem(newElement, state));
    }

    private void addElementAtIndex(@NotNull GradleDslElement newElement, @NotNull ElementState state, int index) {
      myElements.add(getRealIndex(index, newElement), new ElementItem(newElement, state));
    }

    /**
     * Converts a given index to a real index that can correctly place elements in myElements. This ignores all elements that should be
     * removed or have been applied.
     */
    private int getRealIndex(int index, @NotNull GradleDslElement element) {
      // If the index is less than zero then clamp it to zero
      if (index < 0) {
        return 0;
      }

      // Work out the real index
      for (int i = 0; i < myElements.size(); i++) {
        if (index == 0) {
          return i;
        }
        ElementItem item = myElements.get(i);
        if (item.myElementState == ElementState.TO_BE_ADDED ||
          item.myElementState == ElementState.EXISTING) {
          // Make sure we are only counting elements with the same qualified name.
          if (element.getNameElement().qualifyingParts().equals(item.myElement.getNameElement().qualifyingParts())) {
            index--;
          }
        }
      }
      return myElements.size();
    }

    @Nullable
    private ElementState remove(@NotNull GradleDslElement element) {
      ElementItem item = myElements.stream().filter(e -> element == e.myElement).findFirst().orElse(null);
      if (item == null) {
        return null;
      }
      ElementState oldState = item.myElementState;
      item.myElementState = ElementState.TO_BE_REMOVED;
      return oldState;
    }

    private void removeAll(@NotNull Predicate<ElementItem> filter) {
      myElements.stream().filter(filter).forEach(e -> e.myElementState = ElementState.TO_BE_REMOVED);
    }

    private void hideAll(@NotNull Predicate<ElementItem> filter) {
      myElements.stream().filter(filter).forEach(e -> e.myElementState = ElementState.HIDDEN);
    }

    private boolean isEmpty() {
      return myElements.isEmpty();
    }

    private void reset() {
      for (Iterator<ElementItem> i = myElements.iterator(); i.hasNext(); ) {
        ElementItem item = i.next();
        item.myElement.reset();
        if (item.myElementState == ElementState.TO_BE_REMOVED) {
          item.myElementState = ElementState.EXISTING;
        }
        if (item.myElementState == ElementState.TO_BE_ADDED) {
          i.remove();
        }
      }
    }

    /**
     * Runs {@code removeFunc} across all of the elements with {@link ElementState#TO_BE_REMOVED} stored in this list.
     * Once {@code removeFunc} has been run, the element is removed from the list.
     */
    private void removeElements(@NotNull Consumer<GradleDslElement> removeFunc) {
      for (Iterator<ElementItem> i = myElements.iterator(); i.hasNext(); ) {
        ElementItem item = i.next();
        if (item.myElementState == ElementState.TO_BE_REMOVED) {
          removeFunc.accept(item.myElement);
          i.remove();
        }
      }
    }

    /**
     * Runs {@code addFunc} across all of the elements with {@link ElementState#TO_BE_ADDED} stored in this list.
     * If {@code addFunc} returns true then the state is changed to {@link ElementState#EXISTING} else the element
     * is removed.
     */
    private void createElements(@NotNull Predicate<GradleDslElement> addFunc) {
      for (Iterator<ElementItem> i = myElements.iterator(); i.hasNext(); ) {
        ElementItem item = i.next();
        if (item.myElementState == ElementState.TO_BE_ADDED) {
          if (addFunc.test(item.myElement)) {
            item.myElementState = ElementState.EXISTING;
          }
          else {
            i.remove();
          }
        }
      }
    }

    /**
     * Runs {@code func} across all of the elements stored in this list.
     */
    private void applyElements(@NotNull Consumer<GradleDslElement> func) {
      myElements.stream().filter(e -> e.myElementState != ElementState.APPLIED).map(e -> e.myElement).forEach(func);
    }

    /**
     * Clears ALL element in this element list. This clears the whole list without affecting state. If you actually want to remove
     * elements from the file use {@link #removeAll(Predicate)}.
     */
    private void clear() {
      myElements.clear();
    }
  }
}
