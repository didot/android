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
package com.android.tools.idea.gradle.dsl.parser;

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.CompileOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorsDslElement;
import com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.android.tools.idea.gradle.dsl.parser.settings.ProjectPropertiesDslElement;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.psi.util.PsiTreeUtil.*;

/**
 * Generic parser to parse .gradle files.
 *
 * <p>It parses any general application statements or assigned statements in the .gradle file directly and stores them as key value pairs
 * in the {@link GradleBuildModel}. For every closure block section like {@code android{}}, it will create block elements like
 * {@link AndroidModel}. See {@link #getBlockElement(List, GradlePropertiesDslElement)} for all the block elements currently supported
 * by this parser.
 */
public final class GradleDslParser {
  public static boolean parse(@NotNull GroovyPsiElement psiElement, @NotNull GradleDslFile gradleDslFile) {
    if (psiElement instanceof GrMethodCallExpression) {
      return parse((GrMethodCallExpression)psiElement, (GradlePropertiesDslElement)gradleDslFile);
    }
    else if (psiElement instanceof GrAssignmentExpression) {
      return parse((GrAssignmentExpression)psiElement, (GradlePropertiesDslElement)gradleDslFile);
    }
    else if (psiElement instanceof GrApplicationStatement) {
      return parse((GrApplicationStatement)psiElement, (GradlePropertiesDslElement)gradleDslFile);
    }
    return false;
  }

  private static boolean parse(@NotNull GrMethodCallExpression expression, @NotNull GradlePropertiesDslElement dslElement) {
    GrReferenceExpression referenceExpression = findChildOfType(expression, GrReferenceExpression.class);
    if (referenceExpression == null) {
      return false;
    }

    String name = referenceExpression.getText();
    if (isEmpty(name)) {
      return false;
    }

    GradleDslExpression expressionElement = getExpressionElement(dslElement, expression, name, expression);
    if (expressionElement instanceof GradleDslMethodCall) {
      dslElement.addParsedElement(name, expressionElement);
      // This element is a method call with arguments. This element may also contain a closure along with it, but as of now we do not have
      // a use case to understand closure associated with a method call with arguments. So, just process the method arguments and return.
      // ex: compile("dependency") {}
      return true;
    }

    GrClosableBlock[] closureArguments = expression.getClosureArguments();
    if (closureArguments.length == 0) {
      return false;
    }

    // Now this element is pure block element, i.e a method call with no argument but just a closure argument. So, here just process the
    // closure and treat it as a block element.
    // ex: android {}
    GrClosableBlock closableBlock = closureArguments[0];
    List<GradlePropertiesDslElement> blockElements = Lists.newArrayList(); // The block elements this closure needs to be applied.

    if (dslElement instanceof GradleDslFile && name.equals("allprojects")) {
      // The "allprojects" closure needs to be applied to this project and all it's sub projects.
      blockElements.add(dslElement);
      // After applying the allprojects closure to this project, process it as subprojects section to also pass the same properties to
      // subprojects.
      name = "subprojects";
    }

    List<String> nameSegments = Splitter.on('.').splitToList(name);
    GradlePropertiesDslElement blockElement = getBlockElement(nameSegments, dslElement);
    if (blockElement != null) {
      blockElement.setPsiElement(closableBlock);
      blockElements.add(blockElement);
    }

    if (blockElements.isEmpty()) {
      return false;
    }
    for (GradlePropertiesDslElement element : blockElements) {
      parse(closableBlock, element);
    }
    return true;
  }

  private static void parse(@NotNull GrClosableBlock closure, @NotNull final GradlePropertiesDslElement blockElement) {
    closure.acceptChildren(new GroovyElementVisitor() {
      @Override
      public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
        parse(methodCallExpression, blockElement);
      }

      @Override
      public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
        parse(applicationStatement, blockElement);
      }

      @Override
      public void visitAssignmentExpression(GrAssignmentExpression expression) {
        parse(expression, blockElement);
      }
    });

  }

  private static boolean parse(@NotNull GrApplicationStatement statement, @NotNull GradlePropertiesDslElement blockElement) {
    GrReferenceExpression referenceExpression = getChildOfType(statement, GrReferenceExpression.class);
    if (referenceExpression == null) {
      return false;
    }

    GrCommandArgumentList argumentList = getNextSiblingOfType(referenceExpression, GrCommandArgumentList.class);
    if (argumentList == null) {
      return false;
    }

    GroovyPsiElement[] arguments = argumentList.getAllArguments();
    if (arguments.length == 0) {
      return false;
    }

    String name = referenceExpression.getText();
    if (isEmpty(name)) {
      return false;
    }

    List<String> nameSegments = Splitter.on('.').splitToList(name);
    if (nameSegments.size() > 1) {
      GradlePropertiesDslElement nestedElement = getBlockElement(nameSegments.subList(0, nameSegments.size() - 1), blockElement);
      if (nestedElement != null) {
        blockElement = nestedElement;
      }
      else {
        return false;
      }
    }

    String propertyName = nameSegments.get(nameSegments.size() - 1);
    GradleDslElement propertyElement = null;
    if (arguments.length == 1) {
      GroovyPsiElement element = arguments[0];
      if (element instanceof GrExpression) {
        propertyElement = getExpressionElement(blockElement, argumentList, propertyName, (GrExpression)element);
      }
      else if (element instanceof GrNamedArgument) {// ex: manifestPlaceholders activityLabel:"defaultName"
        propertyElement = getExpressionMap(blockElement, argumentList, propertyName, (GrNamedArgument)element);
      }
    }
    else {
      if (arguments[0] instanceof GrExpression) { // ex: proguardFiles 'proguard-android.txt', 'proguard-rules.pro'
        GrExpression[] expressions = new GrExpression[arguments.length];
        for (int i = 0; i < expressions.length; i++) {
          expressions[i] = (GrExpression)arguments[i];
        }
        propertyElement = getExpressionList(blockElement, argumentList, propertyName, expressions);
      }
      else if (arguments[0] instanceof GrNamedArgument) {
        // ex: manifestPlaceholders activityLabel1:"defaultName1", activityLabel2:"defaultName2"
        GrNamedArgument[] namedArguments = new GrNamedArgument[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
          namedArguments[i] = (GrNamedArgument)arguments[i];
        }
        propertyElement = getExpressionMap(blockElement, argumentList, propertyName, namedArguments);
      }
    }
    if (propertyElement == null) {
      return false;
    }

    blockElement.addParsedElement(propertyName, propertyElement);
    return true;
  }

  private static boolean parse(@NotNull GrAssignmentExpression assignment, @NotNull GradlePropertiesDslElement blockElement) {
    PsiElement operationToken = assignment.getOperationToken();
    if (!operationToken.getText().equals("=")) {
      return false; // TODO: Add support for other operators like +=.
    }

    GrExpression left = assignment.getLValue();
    String name = left.getText();
    if (isEmpty(name)) {
      return false;
    }

    List<String> nameSegments = Splitter.on('.').splitToList(name);
    if (nameSegments.size() > 1) {
      GradlePropertiesDslElement nestedElement = getBlockElement(nameSegments.subList(0, nameSegments.size() - 1), blockElement);
      if (nestedElement != null) {
        blockElement = nestedElement;
      }
      else {
        return false;
      }
    }

    String propertyName = nameSegments.get(nameSegments.size() - 1).trim();
    GrExpression right = assignment.getRValue();
    if (right == null) {
      return false;
    }

    GradleDslElement propertyElement;
    if (right instanceof GrListOrMap) {
      GrListOrMap listOrMap = (GrListOrMap)right;
      if (listOrMap.isMap()) { // ex: manifestPlaceholders = [activityLabel1:"defaultName1", activityLabel2:"defaultName2"]
        propertyElement = getExpressionMap(blockElement, listOrMap, propertyName, listOrMap.getNamedArguments());
      }
      else { // ex: proguardFiles = ['proguard-android.txt', 'proguard-rules.pro']
        propertyElement = getExpressionList(blockElement, listOrMap, propertyName, listOrMap.getInitializers());
      }
    }
    else {
      propertyElement = getExpressionElement(blockElement, assignment, propertyName, right);
    }

    if (propertyElement == null) {
      return false;
    }

    blockElement.setParsedElement(propertyName, propertyElement);
    return true;
  }

  @Nullable
  private static GradleDslExpression getExpressionElement(@NotNull GradleDslElement parentElement,
                                                          @NotNull GroovyPsiElement psiElement,
                                                          @NotNull String propertyName,
                                                          @NotNull GrExpression propertyExpression) {
    if (propertyExpression instanceof GrLiteral) { // ex: compileSdkVersion 23 or compileSdkVersion = "android-23"
      return new GradleDslLiteral(parentElement, psiElement, propertyName, (GrLiteral)propertyExpression);
    }

    if (propertyExpression instanceof GrReferenceExpression) { // ex: compileSdkVersion SDK_VERSION or sourceCompatibility = VERSION_1_5
      return new GradleDslReference(parentElement, psiElement, propertyName, (GrReferenceExpression)propertyExpression);
    }

    if (propertyExpression instanceof GrMethodCallExpression) { // ex: compile project("someProject")
      GrMethodCallExpression methodCall = (GrMethodCallExpression)propertyExpression;
      GrReferenceExpression callReferenceExpression = getChildOfType(methodCall, GrReferenceExpression.class);
      if (callReferenceExpression != null) {
        String referenceName = callReferenceExpression.getText();
        if (!isEmpty(referenceName)) {
          GrArgumentList argumentList = methodCall.getArgumentList();
          if (argumentList.getAllArguments().length > 0) {
            return getMethodCall(parentElement, methodCall, referenceName, argumentList);
          }
        }
      }
    }

    if (propertyExpression instanceof GrNewExpression) {
      GrNewExpression newExpression = (GrNewExpression)propertyExpression;
      GrCodeReferenceElement referenceElement = newExpression.getReferenceElement();
      if (referenceElement != null) {
        String objectName = referenceElement.getText();
        if (!isEmpty(objectName)) {
          GrArgumentList argumentList = newExpression.getArgumentList();
          if (argumentList != null) {
            if (argumentList.getAllArguments().length > 0) {
              return getNewExpression(parentElement, newExpression, objectName, argumentList);
            }
          }
        }
      }
    }
    return null;
  }

  @NotNull
  static GradleDslMethodCall getMethodCall(@NotNull GradleDslElement parentElement,
                                           @NotNull GrMethodCallExpression psiElement,
                                           @NotNull String propertyName,
                                           @NotNull GrArgumentList argumentList) {
    GradleDslMethodCall methodCall = new GradleDslMethodCall(parentElement, psiElement, propertyName);

    for (GrExpression expression : argumentList.getExpressionArguments()) {
      if (expression instanceof GrListOrMap) {
        GrListOrMap listOrMap = (GrListOrMap)expression;
        if (listOrMap.isMap()) {
          methodCall.addParsedExpressionMap(getExpressionMap(methodCall, expression, propertyName, listOrMap.getNamedArguments()));
        }
        else {
          for (GrExpression grExpression : listOrMap.getInitializers()) {
            GradleDslExpression dslExpression = getExpressionElement(methodCall, expression, propertyName, grExpression);
            if (dslExpression != null) {
              methodCall.addParsedExpression(dslExpression);
            }
          }
        }
      }
      else {
        GradleDslExpression dslExpression = getExpressionElement(methodCall, expression, propertyName, expression);
        if (dslExpression != null) {
          methodCall.addParsedExpression(dslExpression);
        }
      }
    }

    GrNamedArgument[] namedArguments = argumentList.getNamedArguments();
    if (namedArguments.length > 0) {
      methodCall.addParsedExpressionMap(getExpressionMap(methodCall, argumentList, propertyName, namedArguments));
    }

    return methodCall;
  }

  @NotNull
  static GradleDslNewExpression getNewExpression(@NotNull GradleDslElement parentElement,
                                                 @NotNull GrNewExpression psiElement,
                                                 @NotNull String propertyName,
                                                 @NotNull GrArgumentList argumentList) {
    GradleDslNewExpression newExpression = new GradleDslNewExpression(parentElement, psiElement, propertyName);

    for (GrExpression expression : argumentList.getExpressionArguments()) {
      if (expression instanceof GrListOrMap) {
        GrListOrMap listOrMap = (GrListOrMap)expression;
        if (!listOrMap.isMap()) {
          for (GrExpression grExpression : listOrMap.getInitializers()) {
            GradleDslExpression dslExpression = getExpressionElement(newExpression, expression, propertyName, grExpression);
            if (dslExpression != null) {
              newExpression.addParsedExpression(dslExpression);
            }
          }
        }
      }
      else {
        GradleDslExpression dslExpression = getExpressionElement(newExpression, expression, propertyName, expression);
        if (dslExpression != null) {
          newExpression.addParsedExpression(dslExpression);
        }
      }
    }

    return newExpression;
  }

  @NotNull
  private static GradleDslExpressionList getExpressionList(@NotNull GradleDslElement parentElement,
                                                           @NotNull GroovyPsiElement listPsiElement, // GrArgumentList or GrListOrMap
                                                           @NotNull String propertyName,
                                                           @NotNull GrExpression... propertyExpressions) {
    GradleDslExpressionList expressionList = new GradleDslExpressionList(parentElement, listPsiElement, propertyName);
    for (GrExpression expression : propertyExpressions) {
      GradleDslExpression expressionElement = getExpressionElement(expressionList, listPsiElement, propertyName, expression);
      if (expressionElement != null) {
        expressionList.addParsedExpression(expressionElement);
      }
    }
    return expressionList;
  }

  @NotNull
  private static GradleDslExpressionMap getExpressionMap(@NotNull GradleDslElement parentElement,
                                                         @NotNull GroovyPsiElement mapPsiElement, // GrArgumentList or GrListOrMap
                                                         @NotNull String propertyName,
                                                         @NotNull GrNamedArgument... namedArguments) {
    GradleDslExpressionMap expressionMap = new GradleDslExpressionMap(parentElement, mapPsiElement, propertyName);
    for (GrNamedArgument namedArgument : namedArguments) {
      String argName = namedArgument.getLabelName();
      if (!isEmpty(argName)) {
        GrExpression valueExpression = namedArgument.getExpression();
        if (valueExpression != null) {
          GradleDslElement valueElement = getExpressionElement(expressionMap, mapPsiElement, argName, valueExpression);
          if (valueElement != null) {
            expressionMap.setParsedElement(argName, valueElement);
          }
        }
      }
    }
    return expressionMap;
  }

  private static GradlePropertiesDslElement getBlockElement(@NotNull List<String> qualifiedName,
                                                            @NotNull GradlePropertiesDslElement parentElement) {
    GradlePropertiesDslElement resultElement = parentElement;
    for (String nestedElementName : qualifiedName) {
      nestedElementName = nestedElementName.trim();
      GradleDslElement element = resultElement.getPropertyElement(nestedElementName);
      if (element == null) {
        GradlePropertiesDslElement newElement;
        if (resultElement instanceof GradleDslFile || resultElement instanceof SubProjectsDslElement) {
          if (ExtDslElement.NAME.equals(nestedElementName)) {
            newElement = new ExtDslElement(resultElement);
          }
          else if (AndroidDslElement.NAME.equals(nestedElementName)) {
            newElement = new AndroidDslElement(resultElement);
          }
          else if (DependenciesDslElement.NAME.equals(nestedElementName)) {
            newElement = new DependenciesDslElement(resultElement);
          }
          else if (SubProjectsDslElement.NAME.equals(nestedElementName)) {
            newElement = new SubProjectsDslElement(resultElement);
          }
          else {
            String projectKey = ProjectPropertiesDslElement.getStandardProjectKey(nestedElementName);
            if (projectKey != null) {
              nestedElementName = projectKey;
              newElement = new ProjectPropertiesDslElement(resultElement, nestedElementName);
            }
            else {
              return null;
            }
          }
        }
        else if (resultElement instanceof AndroidDslElement) {
          if ("defaultConfig".equals(nestedElementName)) {
            newElement = new ProductFlavorDslElement(resultElement, nestedElementName);
          }
          else if (ProductFlavorsDslElement.NAME.equals(nestedElementName)) {
            newElement = new ProductFlavorsDslElement(resultElement);
          }
          else if (BaseCompileOptionsDslElement.NAME.equals(nestedElementName)) {
            newElement = new CompileOptionsDslElement(resultElement);
          }
          else {
            return null;
          }
        }
        else if (resultElement instanceof ProductFlavorsDslElement) {
          newElement = new ProductFlavorDslElement(resultElement, nestedElementName);
        }
        else if (resultElement instanceof ProductFlavorDslElement &&
                 ("manifestPlaceholders".equals(nestedElementName) || "testInstrumentationRunnerArguments".equals(nestedElementName))) {
          newElement = new GradleDslExpressionMap(resultElement, nestedElementName);
        }
        else {
          return null;
        }
        resultElement.setParsedElement(nestedElementName, newElement);
        resultElement = newElement;
      }
      else if (element instanceof GradlePropertiesDslElement) {
        resultElement = (GradlePropertiesDslElement)element;
      }
      else {
        return null;
      }
    }
    return resultElement;
  }
}
