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
package com.android.tools.idea.gradle.dsl.parser.groovy;

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl;
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.android.*;
import com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild.CMakeDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild.NdkBuildDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.ExternalNativeBuildOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.NdkOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.VectorDrawablesOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.externalNativeBuild.CMakeOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.externalNativeBuild.NdkBuildOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceFileDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.splits.AbiDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.splits.DensityDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.splits.LanguageDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.testOptions.UnitTestsDslElement;
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement;
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement;
import com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependencyConfigurationDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.repositories.FlatDirRepositoryDslElement;
import com.android.tools.idea.gradle.dsl.parser.repositories.MavenCredentialsDslElement;
import com.android.tools.idea.gradle.dsl.parser.repositories.MavenRepositoryDslElement;
import com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement;
import com.android.tools.idea.gradle.dsl.parser.settings.ProjectPropertiesDslElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.VARIABLE;
import static com.android.tools.idea.gradle.dsl.model.notifications.NotificationTypeReference.INCOMPLETE_PARSING;
import static com.android.tools.idea.gradle.dsl.parser.android.AaptOptionsDslElement.AAPT_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.AdbOptionsDslElement.ADB_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement.ANDROID_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.BuildTypesDslElement.BUILD_TYPES_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.DataBindingDslElement.DATA_BINDING_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.DexOptionsDslElement.DEX_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.ExternalNativeBuildDslElement.EXTERNAL_NATIVE_BUILD_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.LintOptionsDslElement.LINT_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.PackagingOptionsDslElement.PACKAGING_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorsDslElement.PRODUCT_FLAVORS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.SigningConfigsDslElement.SIGNING_CONFIGS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.SourceSetsDslElement.SOURCE_SETS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.SplitsDslElement.SPLITS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.TestOptionsDslElement.TEST_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild.CMakeDslElement.CMAKE_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild.NdkBuildDslElement.NDK_BUILD_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.productFlavors.NdkOptionsDslElement.NDK_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.productFlavors.VectorDrawablesOptionsDslElement.VECTOR_DRAWABLES_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.splits.AbiDslElement.ABI_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.splits.DensityDslElement.DENSITY_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.splits.LanguageDslElement.LANGUAGE_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.testOptions.UnitTestsDslElement.UNIT_TESTS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement.APPLY_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement.BUILDSCRIPT_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement.SUBPROJECTS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement.DEPENDENCIES_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement.COMPILE_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement.EXT_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslUtil.ensureUnquotedText;
import static com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslUtil.findInjections;
import static com.android.tools.idea.gradle.dsl.parser.repositories.FlatDirRepositoryDslElement.FLAT_DIR_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.repositories.MavenCredentialsDslElement.CREDENTIALS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.repositories.MavenRepositoryDslElement.JCENTER_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.repositories.MavenRepositoryDslElement.MAVEN_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement.REPOSITORIES_BLOCK_NAME;
import static com.intellij.psi.util.PsiTreeUtil.*;


/**
 * Generic parser to parse .gradle files.
 *
 * <p>It parses any general application statements or assigned statements in the .gradle file directly and stores them as key value pairs
 * in the {@link GradleBuildModelImpl}. For every closure block section like {@code android{}}, it will create block elements like
 * {@link AndroidModelImpl}. See {@link #getBlockElement(List, GradlePropertiesDslElement)} for all the block elements currently supported
 * by this parser.
 */
public class GroovyDslParser implements GradleDslParser {
  @NotNull private final GroovyFile myPsiFile;
  @NotNull private final GradleDslFile myDslFile;

  public GroovyDslParser(@NotNull GroovyFile file, @NotNull GradleDslFile dslFile) {
    myPsiFile = file;
    myDslFile = dslFile;
  }

  @Override
  public void parse() {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    myPsiFile.acceptChildren(new GroovyPsiElementVisitor(new GroovyElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull GrMethodCallExpression e) {
        process(e);
      }

      @Override
      public void visitAssignmentExpression(@NotNull GrAssignmentExpression e) {
        process(e);
      }

      @Override
      public void visitApplicationStatement(@NotNull GrApplicationStatement e) {
        process(e);
      }

      @Override
      public void visitVariableDeclaration(@NotNull GrVariableDeclaration e) {
        process(e);
      }

      void process(GroovyPsiElement e) {
        parse(e, myDslFile);
      }
    }));
  }

  @Override
  @Nullable
  public PsiElement convertToPsiElement(@NotNull Object literal) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return GroovyDslUtil.createLiteral(myDslFile, literal);
  }

  @Override
  @Nullable
  public Object extractValue(@NotNull GradleDslExpression context, @NotNull PsiElement literal, boolean resolve) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    if (!(literal instanceof GrLiteral)) {
      return null;
    }

    // If this literal has a value then return is, this will be the case for none-string values.
    Object value = ((GrLiteral)literal).getValue();
    if (value != null) {
      return value;
    }

    // Everything left should be a string
    if (!(literal instanceof GrString)) {
      return null;
    }

    // If we shouldn't resolve the value then just return the text.
    if (!resolve) {
      return ensureUnquotedText(literal.getText());
    }

    // Check that we are not resolving into a cycle, if we are just return the unresolved text.
    if (context.hasCycle()) {
      return ensureUnquotedText(literal.getText());
    }

    // Otherwise resolve the value and then return the resolved text.
    Collection<GradleReferenceInjection> injections = context.getResolvedVariables();
    return ensureUnquotedText(GradleReferenceInjection.injectAll(literal, injections));
  }

  @Override
  @NotNull
  public PsiElement convertToExcludesBlock(@NotNull List<ArtifactDependencySpec> excludes) {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myDslFile.getProject());
      GrClosableBlock block = factory.createClosureFromText("{\n}");
      for (ArtifactDependencySpec spec : excludes) {
        String text = String.format("exclude group: '%s', module: '%s'", spec.getGroup(), spec.getName());
        block.addBefore(factory.createStatementFromText(text), block.getLastChild());
        PsiElement lineTerminator = factory.createLineTerminator(1);
        block.addBefore(lineTerminator, block.getLastChild());
      }
      return block;
  }

  @Override
  public boolean shouldInterpolate(@NotNull GradleDslElement elementToCheck) {
    // Get the correct psiElement to check.
    PsiElement element;
    if (elementToCheck instanceof GradleDslSettableExpression) {
      element = ((GradleDslSettableExpression)elementToCheck).getCurrentElement();
    }
    else if (elementToCheck instanceof GradleDslExpression) {
      element = ((GradleDslExpression)elementToCheck).getExpression();
    }
    else {
      element = elementToCheck.getPsiElement();
    }

    return element instanceof GrString;
  }

  @Override
  @NotNull
  public List<GradleReferenceInjection> getResolvedInjections(@NotNull GradleDslExpression context, @NotNull PsiElement psiElement) {
    return findInjections(context, psiElement, false);
  }

  @NotNull
  @Override
  public List<GradleReferenceInjection> getInjections(@NotNull GradleDslExpression context, @NotNull PsiElement psiElement) {
    return findInjections(context, psiElement, true);
  }

  private void parse(@NotNull PsiElement psiElement, @NotNull GradleDslFile gradleDslFile) {
    boolean success = false;
    if (psiElement instanceof GrMethodCallExpression) {
      success = parse((GrMethodCallExpression)psiElement, (GradlePropertiesDslElement)gradleDslFile);
    }
    else if (psiElement instanceof GrAssignmentExpression) {
      success = parse((GrAssignmentExpression)psiElement, (GradlePropertiesDslElement)gradleDslFile);
    }
    else if (psiElement instanceof GrApplicationStatement) {
      success = parse((GrApplicationStatement)psiElement, (GradlePropertiesDslElement)gradleDslFile);
    }
    else if (psiElement instanceof GrVariableDeclaration) {
      success = parse((GrVariableDeclaration)psiElement, (GradlePropertiesDslElement)gradleDslFile);
    }
    if (!success) {
      gradleDslFile.notification(INCOMPLETE_PARSING).addUnknownElement(psiElement);
    }
  }

  private boolean parse(@NotNull GrMethodCallExpression expression, @NotNull GradlePropertiesDslElement dslElement) {
    GrReferenceExpression referenceExpression = findChildOfType(expression, GrReferenceExpression.class);
    if (referenceExpression == null) {
      return false;
    }

    GradleNameElement name = GradleNameElement.from(referenceExpression);
    if (name.isEmpty()) {
      return false;
    }

    if (name.isQualified()) {
      dslElement = getBlockElement(name.qualifyingParts(), dslElement);
    }

    if (dslElement == null) {
      return false;
    }

    GrClosableBlock[] closureArguments = expression.getClosureArguments();
    GrArgumentList argumentList = expression.getArgumentList();
    if (argumentList.getAllArguments().length > 0) {
      // This element is a method call with arguments and an optional closure associated with it.
      // ex: compile("dependency") {}
      GradleDslExpression methodCall = getMethodCall(dslElement, expression, name, argumentList, name.fullName());
      if (closureArguments.length > 0) {
        methodCall.setParsedClosureElement(getClosureElement(methodCall, closureArguments[0], name));
      }
      methodCall.setElementType(REGULAR);
      dslElement.addParsedElement(methodCall);
      return true;
    }

    if (argumentList.getAllArguments().length == 0 && closureArguments.length == 0) {
      // This element is a pure method call, i.e a method call with no arguments and no closure arguments.
      // ex: jcenter()
      GradleDslMethodCall methodCall =
        new GradleDslMethodCall(dslElement, expression, name, expression.getArgumentList(), name.fullName());
      methodCall.setElementType(REGULAR);
      dslElement.addParsedElement(methodCall);
      return true;
    }

    // Now this element is pure block element, i.e a method call with no argument but just a closure argument. So, here just process the
    // closure and treat it as a block element.
    // ex: android {}
    GrClosableBlock closableBlock = closureArguments[0];
    List<GradlePropertiesDslElement> blockElements = Lists.newArrayList(); // The block elements this closure needs to be applied.

    if (dslElement instanceof GradleDslFile && name.name().equals("allprojects")) {
      // The "allprojects" closure needs to be applied to this project and all it's sub projects.
      blockElements.add(dslElement);
      // After applying the allprojects closure to this project, process it as subprojects section to also pass the same properties to
      // subprojects.
      name = GradleNameElement.create("subprojects");
    }

    GradlePropertiesDslElement blockElement = getBlockElement(ImmutableList.of(name.name()), dslElement);
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

  private void parse(@NotNull GrClosableBlock closure, @NotNull final GradlePropertiesDslElement blockElement) {
    closure.acceptChildren(new GroovyElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull GrMethodCallExpression methodCallExpression) {
        parse(methodCallExpression, blockElement);
      }

      @Override
      public void visitApplicationStatement(@NotNull GrApplicationStatement applicationStatement) {
        parse(applicationStatement, blockElement);
      }

      @Override
      public void visitAssignmentExpression(@NotNull GrAssignmentExpression expression) {
        parse(expression, blockElement);
      }

      @Override
      public void visitVariableDeclaration(@NotNull GrVariableDeclaration variableDeclaration) {
        parse(variableDeclaration, blockElement);
      }
    });
  }

  private boolean parse(@NotNull GrApplicationStatement statement, @NotNull GradlePropertiesDslElement blockElement) {
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

    GradleNameElement name = GradleNameElement.from(referenceExpression);
    if (name.isEmpty()) {
      return false;
    }

    if (name.isQualified()) {
      GradlePropertiesDslElement nestedElement = getBlockElement(name.qualifyingParts(), blockElement);
      if (nestedElement != null) {
        blockElement = nestedElement;
      }
      else {
        return false;
      }
    }

    GradleNameElement propertyName = GradleNameElement.from(referenceExpression);
    // TODO: This code highly restricts the arguments allowed in an application statement. Fix this.
    GradleDslElement propertyElement = null;
    if (arguments[0] instanceof GrExpression) { // ex: proguardFiles 'proguard-android.txt', 'proguard-rules.pro'
      List<GrExpression> expressions = new ArrayList<>(arguments.length);
      for (GroovyPsiElement element : arguments) {
        // We need to make sure all of these are GrExpressions, there can be multiple types.
        // We currently can't handle different argument types.
        if (element instanceof GrExpression && !(element instanceof GrClosableBlock)) {
          expressions.add((GrExpression)element);
        }
      }
      if (expressions.size() == 1) {
        propertyElement = getExpressionElement(blockElement, argumentList, propertyName, expressions.get(0));
      }
      else {
        propertyElement = getExpressionList(blockElement, argumentList, propertyName, expressions, false);
      }
    }
    else if (arguments[0] instanceof GrNamedArgument) {
      // ex: manifestPlaceholders activityLabel1:"defaultName1", activityLabel2:"defaultName2"
      List<GrNamedArgument> namedArguments = new ArrayList<>(arguments.length);
      for (GroovyPsiElement element : arguments) {
        // We need to make sure all of these are GrNamedArgument, there can be multiple types.
        // We currently can't handle different argument types.
        if (element instanceof GrNamedArgument && !(element instanceof GrClosableBlock)) {
          namedArguments.add((GrNamedArgument)element);
        }
      }
      propertyElement = getExpressionMap(blockElement, argumentList, propertyName, namedArguments, false);
    }
    if (propertyElement == null) {
      return false;
    }

    GroovyPsiElement lastArgument = arguments[arguments.length - 1];
    if (lastArgument instanceof GrClosableBlock) {
      propertyElement.setParsedClosureElement(getClosureElement(propertyElement, (GrClosableBlock)lastArgument, propertyName));
    }

    propertyElement.setElementType(REGULAR);
    blockElement.addParsedElement(propertyElement);
    return true;
  }

  @Nullable
  private GradleDslElement createExpressionElement(@NotNull GradleDslElement parent,
                                                   @NotNull GroovyPsiElement psiElement,
                                                   @NotNull GradleNameElement name,
                                                   @Nullable GrExpression expression) {
    if (expression == null) {
      return null;
    }

    GradleDslElement propertyElement;
    if (expression instanceof GrListOrMap) {
      GrListOrMap listOrMap = (GrListOrMap)expression;
      if (listOrMap.isMap()) { // ex: manifestPlaceholders = [activityLabel1:"defaultName1", activityLabel2:"defaultName2"]
        propertyElement = getExpressionMap(parent, listOrMap, name, Arrays.asList(listOrMap.getNamedArguments()), true);
      }
      else { // ex: proguardFiles = ['proguard-android.txt', 'proguard-rules.pro']
        propertyElement = getExpressionList(parent, listOrMap, name, Arrays.asList(listOrMap.getInitializers()), true);
      }
    }
    else {
      propertyElement = getExpressionElement(parent, psiElement, name, expression);
    }

    return propertyElement;
  }

  private boolean parse(@NotNull GrVariableDeclaration declaration, @NotNull GradlePropertiesDslElement blockElement) {
    if (declaration.getVariables().length == 0) {
      return false;
    }

    for (GrVariable variable : declaration.getVariables()) {
      if (variable == null) {
        return false;
      }

      GradleNameElement name = GradleNameElement.from(variable);
      GradleDslElement variableElement =
        createExpressionElement(blockElement, declaration, name, variable.getInitializerGroovy());
      if (variableElement == null) {
        return false;
      }

      variableElement.setElementType(VARIABLE);
      blockElement.setParsedElement(variableElement);
    }
    return true;
  }

  private boolean parse(@NotNull GrAssignmentExpression assignment, @NotNull GradlePropertiesDslElement blockElement) {
    PsiElement operationToken = assignment.getOperationToken();
    if (!operationToken.getText().equals("=")) {
      return false; // TODO: Add support for other operators like +=.
    }

    GrExpression left = assignment.getLValue();
    GradleNameElement name = GradleNameElement.from(left);
    if (name.isEmpty()) {
      return false;
    }

    if (name.isQualified()) {
      GradlePropertiesDslElement nestedElement = getBlockElement(name.qualifyingParts(), blockElement);
      if (nestedElement != null) {
        blockElement = nestedElement;
      }
      else {
        return false;
      }
    }

    GrExpression right = assignment.getRValue();
    if (right == null) {
      return false;
    }

    GradleDslElement propertyElement = createExpressionElement(blockElement, assignment, name, right);
    if (propertyElement == null) {
      return false;
    }
    propertyElement.setUseAssignment(true);
    propertyElement.setElementType(REGULAR);

    blockElement.setParsedElement(propertyElement);
    return true;
  }

  @NotNull
  private GradleDslExpression getExpressionElement(@NotNull GradleDslElement parentElement,
                                                   @NotNull GroovyPsiElement psiElement,
                                                   @NotNull GradleNameElement propertyName,
                                                   @NotNull GrExpression propertyExpression) {
    if (propertyExpression instanceof GrLiteral) { // ex: compileSdkVersion 23 or compileSdkVersion = "android-23"
      return new GradleDslLiteral(parentElement, psiElement, propertyName, propertyExpression);
    }

    if (propertyExpression instanceof GrReferenceExpression) { // ex: compileSdkVersion SDK_VERSION or sourceCompatibility = VERSION_1_5
      return new GradleDslReference(parentElement, psiElement, propertyName, propertyExpression);
    }

    if (propertyExpression instanceof GrMethodCallExpression) { // ex: compile project("someProject")
      GrMethodCallExpression methodCall = (GrMethodCallExpression)propertyExpression;
      GrReferenceExpression callReferenceExpression = getChildOfType(methodCall, GrReferenceExpression.class);
      if (callReferenceExpression != null) {
        String methodName = callReferenceExpression.getText();
        if (!methodName.isEmpty()) {
          GrArgumentList argumentList = methodCall.getArgumentList();
          if (argumentList.getAllArguments().length > 0) {
            return getMethodCall(parentElement, methodCall, propertyName, argumentList, methodName);
          }
          else {
            return new GradleDslMethodCall(parentElement, propertyExpression, propertyName, methodCall.getArgumentList(), methodName);
          }
        }
      }
    }

    if (propertyExpression instanceof GrIndexProperty) {
      return new GradleDslReference(parentElement, psiElement, propertyName, propertyExpression);
    }

    if (propertyExpression instanceof GrNewExpression) {
      GrNewExpression newExpression = (GrNewExpression)propertyExpression;
      GrCodeReferenceElement referenceElement = newExpression.getReferenceElement();
      if (referenceElement != null) {
        GradleNameElement objectName = GradleNameElement.from(referenceElement);
        if (!objectName.isEmpty()) {
          GrArgumentList argumentList = newExpression.getArgumentList();
          if (argumentList != null) {
            if (argumentList.getAllArguments().length > 0) {
              return getNewExpression(parentElement, newExpression, propertyName, argumentList, objectName);
            }
          }
        }
      }
    }

    // We have no idea what it is.
    parentElement.notification(INCOMPLETE_PARSING).addUnknownElement(propertyExpression);
    return new GradleDslUnknownElement(parentElement, propertyExpression, propertyName);
  }

  @NotNull
  private GradleDslMethodCall getMethodCall(@NotNull GradleDslElement parentElement,
                                            @NotNull GrMethodCallExpression psiElement,
                                            @NotNull GradleNameElement propertyName,
                                            @NotNull GrArgumentList argumentList,
                                            @NotNull String methodName) {
    GradleDslMethodCall methodCall = new GradleDslMethodCall(parentElement, psiElement, propertyName, argumentList, methodName);

    for (GrExpression expression : argumentList.getExpressionArguments()) {
      if (expression instanceof GrListOrMap) {
        GrListOrMap listOrMap = (GrListOrMap)expression;
        if (listOrMap.isMap()) {
          methodCall
            .addParsedExpressionMap(
              getExpressionMap(methodCall, expression, propertyName, Arrays.asList(listOrMap.getNamedArguments()), false));
        }
        else {
          for (GrExpression grExpression : listOrMap.getInitializers()) {
            GradleDslExpression dslExpression = getExpressionElement(methodCall, expression, propertyName, grExpression);
            methodCall.addParsedExpression(dslExpression);
          }
        }
      }
      else if (expression instanceof GrClosableBlock) {
        methodCall.setParsedClosureElement(getClosureElement(methodCall, (GrClosableBlock)expression, propertyName));
      }
      else {
        GradleDslExpression dslExpression = getExpressionElement(methodCall, expression, propertyName, expression);
        methodCall.addParsedExpression(dslExpression);
      }
    }

    GrNamedArgument[] namedArguments = argumentList.getNamedArguments();
    if (namedArguments.length > 0) {
      methodCall.addParsedExpressionMap(
        getExpressionMap(methodCall, argumentList, propertyName, Arrays.asList(namedArguments), false));
    }

    return methodCall;
  }

  @NotNull
  private GradleDslNewExpression getNewExpression(@NotNull GradleDslElement parentElement,
                                                  @NotNull GrNewExpression psiElement,
                                                  @NotNull GradleNameElement propertyName,
                                                  @NotNull GrArgumentList argumentList,
                                                  @NotNull GradleNameElement objectName) {
    GradleDslNewExpression newExpression = new GradleDslNewExpression(parentElement, psiElement, propertyName, objectName);

    for (GrExpression expression : argumentList.getExpressionArguments()) {
      if (expression instanceof GrListOrMap) {
        GrListOrMap listOrMap = (GrListOrMap)expression;
        if (!listOrMap.isMap()) {
          for (GrExpression grExpression : listOrMap.getInitializers()) {
            GradleDslExpression dslExpression = getExpressionElement(newExpression, expression, propertyName, grExpression);
            newExpression.addParsedExpression(dslExpression);
          }
        }
      }
      else {
        GradleDslExpression dslExpression = getExpressionElement(newExpression, expression, propertyName, expression);
        newExpression.addParsedExpression(dslExpression);
      }
    }

    return newExpression;
  }

  @NotNull
  private GradleDslExpressionList getExpressionList(@NotNull GradleDslElement parentElement,
                                                    @NotNull GroovyPsiElement listPsiElement, // GrArgumentList or GrListOrMap
                                                    @NotNull GradleNameElement propertyName,
                                                    @NotNull List<GrExpression> propertyExpressions,
                                                    boolean isLiteral) {
    GradleDslExpressionList expressionList = new GradleDslExpressionList(parentElement, listPsiElement, isLiteral, propertyName);
    for (GrExpression expression : propertyExpressions) {
      GradleDslExpression expressionElement = getExpressionElement(expressionList, expression, propertyName, expression);
      expressionList.addParsedExpression(expressionElement);
    }
    return expressionList;
  }

  @NotNull
  private GradleDslExpressionMap getExpressionMap(@NotNull GradleDslElement parentElement,
                                                  @NotNull GroovyPsiElement mapPsiElement, // GrArgumentList or GrListOrMap
                                                  @NotNull GradleNameElement propertyName,
                                                  @NotNull List<GrNamedArgument> namedArguments,
                                                  boolean isLiteralMap) {
    GradleDslExpressionMap expressionMap = new GradleDslExpressionMap(parentElement, mapPsiElement, propertyName, isLiteralMap);
    for (GrNamedArgument namedArgument : namedArguments) {
      GrArgumentLabel nameLabel = namedArgument.getLabel();
      if (nameLabel == null) {
        continue;
      }
      GradleNameElement argName = GradleNameElement.from(nameLabel.getNameElement());
      if (argName.isEmpty()) {
        continue;
      }
      GrExpression valueExpression = namedArgument.getExpression();
      if (valueExpression == null) {
        continue;
      }
      GradleDslElement valueElement = getExpressionElement(expressionMap, mapPsiElement, argName, valueExpression);
      if (valueElement instanceof GradleDslUnknownElement && valueExpression instanceof GrListOrMap) {
        GrListOrMap listOrMap = (GrListOrMap)valueExpression;
        if (listOrMap.isMap()) {
          valueElement = getExpressionMap(expressionMap, listOrMap, argName, Arrays.asList(listOrMap.getNamedArguments()), true);
        }
        else { // ex: flatDir name: "libs", dirs: ["libs1", "libs2"]
          valueElement = getExpressionList(expressionMap, listOrMap, argName, Arrays.asList(listOrMap.getInitializers()), true);
        }
      }
      expressionMap.setParsedElement(valueElement);
    }
    return expressionMap;
  }

  @NotNull
  private GradleDslClosure getClosureElement(@NotNull GradleDslElement parentElement,
                                             @NotNull GrClosableBlock closableBlock,
                                             @NotNull GradleNameElement propertyName) {
    GradleDslClosure closureElement;
    if (parentElement.getParent() instanceof DependenciesDslElement) {
      closureElement = new DependencyConfigurationDslElement(parentElement, closableBlock, propertyName);
    }
    else {
      closureElement = new GradleDslClosure(parentElement, closableBlock, propertyName);
    }
    parse(closableBlock, closureElement);
    return closureElement;
  }

  @Override
  @Nullable
  public GradlePropertiesDslElement getBlockElement(@NotNull List<String> nameParts,
                                                    @NotNull GradlePropertiesDslElement parentElement) {
    GradlePropertiesDslElement resultElement = parentElement;
    for (String nestedElementName : nameParts) {
      nestedElementName = nestedElementName.trim();
      // We don't require PsiElement for backing block elements.
      GradleNameElement elementName = GradleNameElement.fake(nestedElementName);
      GradleDslElement element = resultElement.getPropertyElement(nestedElementName);
      if (element == null) {
        GradlePropertiesDslElement newElement;
        if (resultElement instanceof GradleDslFile || resultElement instanceof SubProjectsDslElement) {
          if (APPLY_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new ApplyDslElement(resultElement);
          }
          else if (EXT_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new ExtDslElement(resultElement);
          }
          else if (ANDROID_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new AndroidDslElement(resultElement);
          }
          else if (DEPENDENCIES_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new DependenciesDslElement(resultElement);
          }
          else if (SUBPROJECTS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new SubProjectsDslElement(resultElement);
          }
          else if (BUILDSCRIPT_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new BuildScriptDslElement(resultElement);
          }
          else if (REPOSITORIES_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new RepositoriesDslElement(resultElement);
          }
          else {
            String projectKey = ProjectPropertiesDslElement.getStandardProjectKey(nestedElementName);
            if (projectKey != null) {
              elementName = GradleNameElement.fake(projectKey);
              newElement = new ProjectPropertiesDslElement(resultElement, elementName);
            }
            else {
              return null;
            }
          }
        }
        else if (resultElement instanceof BuildScriptDslElement) {
          if (DEPENDENCIES_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new DependenciesDslElement(resultElement);
          }
          else if (REPOSITORIES_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new RepositoriesDslElement(resultElement);
          }
          else {
            return null;
          }
        }
        else if (resultElement instanceof RepositoriesDslElement) {
          if (MAVEN_BLOCK_NAME.equals(nestedElementName) || JCENTER_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new MavenRepositoryDslElement(resultElement, elementName);
          }
          else if (FLAT_DIR_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new FlatDirRepositoryDslElement(resultElement);
          }
          else {
            return null;
          }
        }
        else if (resultElement instanceof MavenRepositoryDslElement) {
          if (CREDENTIALS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new MavenCredentialsDslElement(resultElement);
          }
          else {
            return null;
          }
        }
        else if (resultElement instanceof AndroidDslElement) {
          if ("defaultConfig".equals(nestedElementName)) {
            newElement = new ProductFlavorDslElement(resultElement, elementName);
          }
          else if (PRODUCT_FLAVORS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new ProductFlavorsDslElement(resultElement);
          }
          else if (BUILD_TYPES_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new BuildTypesDslElement(resultElement);
          }
          else if (COMPILE_OPTIONS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new CompileOptionsDslElement(resultElement);
          }
          else if (EXTERNAL_NATIVE_BUILD_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new ExternalNativeBuildDslElement(resultElement);
          }
          else if (SIGNING_CONFIGS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new SigningConfigsDslElement(resultElement);
          }
          else if (SOURCE_SETS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new SourceSetsDslElement(resultElement);
          }
          else if (AAPT_OPTIONS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new AaptOptionsDslElement(resultElement);
          }
          else if (ADB_OPTIONS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new AdbOptionsDslElement(resultElement);
          }
          else if (DATA_BINDING_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new DataBindingDslElement(resultElement);
          }
          else if (DEX_OPTIONS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new DexOptionsDslElement(resultElement);
          }
          else if (LINT_OPTIONS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new LintOptionsDslElement(resultElement);
          }
          else if (PACKAGING_OPTIONS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new PackagingOptionsDslElement(resultElement);
          }
          else if (SPLITS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new SplitsDslElement(resultElement);
          }
          else if (TEST_OPTIONS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new TestOptionsDslElement(resultElement);
          }
          else {
            return null;
          }
        }
        else if (resultElement instanceof ExternalNativeBuildDslElement) {
          if (CMAKE_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new CMakeDslElement(resultElement);
          }
          else if (NDK_BUILD_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new NdkBuildDslElement(resultElement);
          }
          else {
            return null;
          }
        }
        else if (resultElement instanceof ProductFlavorsDslElement) {
          newElement = new ProductFlavorDslElement(resultElement, elementName);
        }
        else if (resultElement instanceof ProductFlavorDslElement) {
          if ("manifestPlaceholders".equals(nestedElementName) || "testInstrumentationRunnerArguments".equals(nestedElementName)) {
            newElement = new GradleDslExpressionMap(resultElement, elementName);
          }
          else if (EXTERNAL_NATIVE_BUILD_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new ExternalNativeBuildOptionsDslElement(resultElement);
          }
          else if (NDK_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new NdkOptionsDslElement(resultElement);
          }
          else if (VECTOR_DRAWABLES_OPTIONS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new VectorDrawablesOptionsDslElement(resultElement);
          }
          else {
            return null;
          }
        }
        else if (resultElement instanceof BuildTypesDslElement) {
          newElement = new BuildTypeDslElement(resultElement, elementName);
        }
        else if (resultElement instanceof BuildTypeDslElement && "manifestPlaceholders".equals(nestedElementName)) {
          newElement = new GradleDslExpressionMap(resultElement, elementName);
        }
        else if (resultElement instanceof SigningConfigsDslElement) {
          newElement = new SigningConfigDslElement(resultElement, elementName);
        }
        else if (resultElement instanceof SourceSetsDslElement) {
          newElement = new SourceSetDslElement(resultElement, elementName);
        }
        else if (resultElement instanceof SourceSetDslElement) {
          if ("manifest".equals(nestedElementName)) {
            newElement = new SourceFileDslElement(resultElement, elementName);
          }
          else {
            newElement = new SourceDirectoryDslElement(resultElement, elementName);
          }
        }
        else if (resultElement instanceof ExternalNativeBuildOptionsDslElement) {
          if (CMAKE_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new CMakeOptionsDslElement(resultElement);
          }
          else if (NDK_BUILD_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new NdkBuildOptionsDslElement(resultElement);
          }
          else {
            return null;
          }
        }
        else if (resultElement instanceof SplitsDslElement) {
          if (ABI_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new AbiDslElement(resultElement);
          }
          else if (DENSITY_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new DensityDslElement(resultElement);
          }
          else if (LANGUAGE_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new LanguageDslElement(resultElement);
          }
          else {
            return null;
          }
        }
        else if (resultElement instanceof TestOptionsDslElement) {
          if (UNIT_TESTS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new UnitTestsDslElement(resultElement);
          }
          else {
            return null;
          }
        }
        else {
          return null;
        }
        resultElement.setParsedElement(newElement);
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
