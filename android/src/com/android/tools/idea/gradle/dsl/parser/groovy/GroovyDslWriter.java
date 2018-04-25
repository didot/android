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

import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.parser.GradleDslWriter;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import static com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslUtil.*;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mASSIGN;

public class GroovyDslWriter implements GradleDslWriter {
  @Override
  public PsiElement moveDslElement(@NotNull GradleDslElement element) {
    // 1. Get the anchor where we need to move the element to.
    GradleDslElement anchorAfter = element.getAnchor();

    GroovyPsiElement psiElement = ensureGroovyPsi(element.getPsiElement());
    if (psiElement == null) {
      return null;
    }

    PsiElement parentPsiElement = getParentPsi(element);
    if (parentPsiElement == null) {
      return null;
    }

    PsiElement anchor = getPsiElementForAnchor(parentPsiElement, anchorAfter);

    // 2. Create a dummy element that we can move the element to.
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(parentPsiElement.getProject());
    PsiElement lineTerminator = factory.createLineTerminator(1);
    PsiElement toReplace = parentPsiElement.addAfter(lineTerminator, anchor);

    // 3. Find the element we need to actually replace. The psiElement we have may be a child of what we need.
    PsiElement e = element.getPsiElement();
    while (!(e.getParent() instanceof GroovyFile || e.getParent() instanceof GrClosableBlock)) {
      // Make sure e isn't going to be set to null.
      if (e.getParent() == null) {
        e = element.getPsiElement();
        break;
      }
      e = e.getParent();
    }

    // 4. Copy the old PsiElement tree.
    PsiElement treeCopy = e.copy();

    // 5. Replace what we need to replace.
    PsiElement newTree = toReplace.replace(treeCopy);

    // 6. Delete the original tree.
    e.delete();

    // 7. Set the new PsiElement. Note: The internal state of this element will have invalid elements. It is required to reparse the file
    // to obtain the correct elements.
    element.setPsiElement(newTree);

    return element.getPsiElement();
  }

  @Override
  public PsiElement createDslElement(@NotNull GradleDslElement element) {
    GradleDslElement anchorAfter = element.getAnchor();
    GroovyPsiElement psiElement = ensureGroovyPsi(element.getPsiElement());
    if (psiElement != null) {
      return psiElement;
    }

    // If the parent doesn't have a psi element, the anchor will be used to create the parent in getParentPsi.
    // In this case we want to be placed in the newly made parent so we ignore our anchor.
    if (needToCreateParent(element)) {
      anchorAfter = null;
    }
    PsiElement parentPsiElement = getParentPsi(element);
    if (parentPsiElement == null) {
      return null;
    }

    Project project = parentPsiElement.getProject();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

    if (isNewEmptyBlockElement(element)) {
      return null; // Avoid creation of an empty block statement.
    }

    String statementText = element.getFullName();
    if (element.isBlockElement()) {
      statementText += " {\n}\n";
    }
    else if (element.shouldUseAssignment()) {
      if (element.getElementType() == PropertyType.REGULAR) {
        statementText += " = 'abc'";
      }
      else if (element.getElementType() == PropertyType.VARIABLE) {
        statementText = "def " + statementText + " = 'abc'";
      }
    }
    else {
      statementText += "\"abc\", \"xyz\"";
    }
    GrStatement statement = factory.createStatementFromText(statementText);
    // TODO: Move these workarounds to a more sensible way of doing things.
    if (statement instanceof GrApplicationStatement) {
      // Workaround to create an application statement.
      ((GrApplicationStatement)statement).getArgumentList().delete();
    }
    else if (statement instanceof GrAssignmentExpression) {
      // Workaround to create an assignment statement
      GrAssignmentExpression assignment = (GrAssignmentExpression)statement;
      if (assignment.getRValue() != null) {
        assignment.getRValue().delete();
      }
    }
    else if (statement instanceof GrVariableDeclaration) {
      GrVariableDeclaration variableDeclaration = (GrVariableDeclaration)statement;
      for (GrVariable var : variableDeclaration.getVariables()) {
        if (var.getInitializerGroovy() != null) {
          var.getInitializerGroovy().delete();
          // The '=' gets deleted here, add it back.
          final ASTNode node = var.getNode();
          node.addLeaf(mASSIGN, "=", var.getLastChild().getNode().getTreeNext());
        }
      }
    }
    PsiElement lineTerminator = factory.createLineTerminator(1);
    PsiElement addedElement;
    PsiElement anchor = getPsiElementForAnchor(parentPsiElement, anchorAfter);

    if (parentPsiElement instanceof GroovyFile) {
      addedElement = parentPsiElement.addAfter(statement, anchor);
      parentPsiElement.addBefore(lineTerminator, addedElement);
    }
    else if (parentPsiElement instanceof GrClosableBlock) {
      addedElement = parentPsiElement.addAfter(statement, anchor);
      if (anchorAfter != null) {
        parentPsiElement.addBefore(lineTerminator, addedElement);
      } else {
        parentPsiElement.addAfter(lineTerminator, addedElement);
        GrClosableBlock parentBlock = (GrClosableBlock)parentPsiElement;
        if (parentBlock.getRBrace() != null && !hasNewLineBetween(parentBlock.getLBrace(), parentBlock.getRBrace())) {
          parentPsiElement.addBefore(lineTerminator, addedElement);
        }
      }
    }
    else {
      addedElement = parentPsiElement.addAfter(statement, anchor);
      parentPsiElement.addBefore(lineTerminator, addedElement);
    }
    if (element.isBlockElement()) {
      GrClosableBlock closableBlock = getClosableBlock(addedElement);
      if (closableBlock != null) {
        element.setPsiElement(closableBlock);
      }
    }
    else {
      if (addedElement instanceof GrApplicationStatement ||
          addedElement instanceof GrAssignmentExpression ||
          addedElement instanceof GrVariableDeclaration) {
        // This is for the workarounds above, this ensures that applyDslLiteral is called to actually add the value to
        // either the application or assignment statement.
        element.setPsiElement(addedElement);
      }
    }
    return element.getPsiElement();
  }

  @Override
  public void deleteDslElement(@NotNull GradleDslElement element) {
    PsiElement psiElement = element.getPsiElement();
    if (psiElement == null || !psiElement.isValid()) {
      return;
    }

    PsiElement parent = psiElement.getParent();
    psiElement.delete();

    maybeDeleteIfEmpty(parent, element);

    // Now we have deleted all empty PsiElements in the Psi tree, we also need to make sure
    // to clear any invalid PsiElements in the GradleDslElement tree otherwise we will
    // be prevented from recreating these elements.
    removePsiIfInvalid(element);
  }

  @Override
  public PsiElement createDslLiteral(@NotNull GradleDslLiteral literal) {
    GradleDslElement parent = literal.getParent();

    if (!(parent instanceof GradleDslExpressionMap)) {
      return createDslElement(literal);
    }

    return processMapElement(literal);
  }

  @Override
  public void applyDslLiteral(@NotNull GradleDslLiteral literal) {
    PsiElement psiElement = ensureGroovyPsi(literal.getPsiElement());
    if (psiElement == null) {
      return;
    }

    maybeUpdateName(literal);

    GrExpression newLiteral = extractUnsavedExpression(literal);
    if (newLiteral == null) {
      return;
    }
    PsiElement expression = ensureGroovyPsi(literal.getLastCommittedValue());
    if (expression != null) {
      PsiElement replace = expression.replace(newLiteral);
      if (replace instanceof GrLiteral) {
        literal.setExpression(replace);
      }
    }
    else {
      PsiElement added = createPsiElementInsideList(literal, psiElement, newLiteral);
      if (added instanceof GrLiteral) {
        literal.setExpression(added);
      }

      if (literal.getUnsavedConfigBlock() != null) {
        addConfigBlock(literal);
      }
    }

    literal.reset();
    literal.setModified(false);
  }

  @Override
  public void deleteDslLiteral(@NotNull GradleDslLiteral literal) {
    PsiElement expression = literal.getLastCommittedValue();
    if (expression == null) {
      return;
    }
    PsiElement parent = expression.getParent();
    expression.delete();
    maybeDeleteIfEmpty(parent, literal);
    removePsiIfInvalid(literal);
  }

  @Override
  public PsiElement createDslReference(@NotNull GradleDslReference reference) {
    GradleDslElement parent = reference.getParent();

    if (!(parent instanceof GradleDslExpressionMap)) {
      return createDslElement(reference);
    }

    return processMapElement(reference);
  }

  @Override
  public void applyDslReference(@NotNull GradleDslReference reference) {
    PsiElement psiElement = ensureGroovyPsi(reference.getPsiElement());
    if (psiElement == null) {
      return;
    }

    maybeUpdateName(reference);

    PsiElement newReference = extractUnsavedExpression(reference);
    if (newReference == null) {
      return;
    }

    PsiElement expression = ensureGroovyPsi(reference.getExpression());
    if (expression != null) {
      PsiElement replace = expression.replace(newReference);
      reference.setExpression(replace);
    }
    else {
      PsiElement added = createPsiElementInsideList(reference, psiElement, newReference);
      reference.setExpression(added);
    }

    reference.reset();
    reference.setModified(false);
  }

  @Override
  public void deleteDslReference(@NotNull GradleDslReference reference) {
    PsiElement expression = reference.getExpression();
    if (expression == null) {
      return;
    }
    PsiElement parent = expression.getParent();
    expression.delete();
    maybeDeleteIfEmpty(parent, reference);
    removePsiIfInvalid(reference);
  }

  @Override
  public PsiElement createDslMethodCall(@NotNull GradleDslMethodCall methodCall) {
    PsiElement psiElement = methodCall.getPsiElement();
    if (psiElement != null && psiElement.isValid()) {
      return psiElement;
    }

    if (methodCall.getParent() == null) {
      return null;
    }

    GradleDslElement anchorAfter = methodCall.getAnchor();

    // If the parent doesn't have a psi element, the anchor will be used to create the parent in getParentPsi.
    // In this case we want to be placed in the newly made parent so we ignore our anchor.
    if (needToCreateParent(methodCall)) {
      anchorAfter = null;
    }

    PsiElement parentPsiElement = methodCall.getParent().create();
    if (parentPsiElement == null) {
      return null;
    }

    PsiElement anchor = getPsiElementForAnchor(parentPsiElement, anchorAfter);

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(parentPsiElement.getProject());
    String statementText =
      (!methodCall.getFullName().isEmpty() ? methodCall.getFullName() + " " : "") + methodCall.getMethodName() + "()";
    GrStatement statement = factory.createStatementFromText(statementText);
    PsiElement addedElement = parentPsiElement.addAfter(statement, anchor);

    if (addedElement instanceof GrApplicationStatement) {
      GrExpression[] expressionArguments = ((GrApplicationStatement)addedElement).getArgumentList().getExpressionArguments();
      if (expressionArguments.length == 1 && expressionArguments[0] instanceof GrMethodCallExpression) {
        methodCall.setPsiElement(expressionArguments[0]);

        // Set the argument list element as well.
        if (expressionArguments[0] instanceof GrMethodCallExpression) {
          GrMethodCallExpression methodCallExpression = (GrMethodCallExpression)expressionArguments[0];
          methodCall.getArgumentsElement().setPsiElement(methodCallExpression.getArgumentList());
        }
        return methodCall.getPsiElement();
      }
    }

    if (addedElement instanceof GrMethodCallExpression) {
      methodCall.setPsiElement(addedElement);
      methodCall.getArgumentsElement().setPsiElement(((GrMethodCallExpression)addedElement).getArgumentList());
      return methodCall.getPsiElement();
    }

    return null;
  }

  @Override
  public void applyDslMethodCall(@NotNull GradleDslMethodCall element) {
    maybeUpdateName(element);
    element.getArgumentsElement().applyChanges();
  }

  @Override
  public PsiElement createDslExpressionList(@NotNull GradleDslExpressionList expressionList) {
    PsiElement psiElement = expressionList.getPsiElement();
    if (psiElement == null) {
      if (expressionList.getParent() instanceof GradleDslExpressionMap) {
        // This is a list in the map element and we need to create a named argument for it.
        return createNamedArgumentList(expressionList);
      }
      psiElement = createDslElement(expressionList);
    }
    else {
      return psiElement;
    }

    if (psiElement == null) {
      return null;
    }

    if (psiElement instanceof GrListOrMap) {
      return psiElement;
    }

    // We are assigning a list to a property.
    if (psiElement instanceof GrAssignmentExpression || psiElement instanceof GrVariableDeclaration) {
      GrExpression emptyMap = GroovyPsiElementFactory.getInstance(psiElement.getProject()).createExpressionFromText("[]");
      PsiElement element = psiElement.addAfter(emptyMap, psiElement.getLastChild());
      // Overwrite the PsiElement set by createDslElement() to cause the elements of the map to be put into the correct place.
      // e.g within the brackets. For example this will replace the PsiElement "prop1 = " with "[]".
      expressionList.setPsiElement(element);
      return expressionList.getPsiElement();
    }

    if (psiElement instanceof GrArgumentList) {
      if (expressionList.getExpressions().size() == 1 &&
          ((GrArgumentList)psiElement).getAllArguments().length == 1 &&
          !expressionList.isAppendToArgumentListWithOneElement()) {
        // Sometimes it's not possible to append to the arguments list with one item. eg. proguardFile "xyz".
        // Set the psiElement to null and create a new psiElement of an empty application statement.
        expressionList.setPsiElement(null);
        psiElement = createDslElement(expressionList);
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
        expressionList.setPsiElement(addedArgumentList);
        return addedArgumentList;
      }
    }

    return null;
  }

  @Override
  public void applyDslExpressionList(@NotNull GradleDslExpressionList expressionList) {
    maybeUpdateName(expressionList);
  }

  @Override
  public PsiElement createDslExpressionMap(@NotNull GradleDslExpressionMap expressionMap) {
    if (expressionMap.getPsiElement() != null) {
      return expressionMap.getPsiElement();
    }

    PsiElement psiElement;
    if (expressionMap.getElementType() == PropertyType.DERIVED && expressionMap.isLiteralMap()) {
      psiElement = createDerivedMap(expressionMap);
    }
    else {
      psiElement = createDslElement(expressionMap);
    }
    if (psiElement == null) {
      return null;
    }

    if (psiElement instanceof GrListOrMap || psiElement instanceof GrArgumentList || psiElement instanceof GrNamedArgument) {
      return psiElement;
    }

    // We are assigning a map to a property.
    if (psiElement instanceof GrAssignmentExpression || psiElement instanceof GrVariableDeclaration) {
      GrExpression emptyMap = GroovyPsiElementFactory.getInstance(psiElement.getProject()).createExpressionFromText("[:]");
      PsiElement element = psiElement.addAfter(emptyMap, psiElement.getLastChild());
      // Overwrite the PsiElement set by createDslElement() to cause the elements of the map to be put into the correct place.
      // e.g within the brackets. For example this will replace the PsiElement "prop1 = " with "[:]".
      expressionMap.setPsiElement(element);
      return element;
    }

    if (psiElement instanceof GrApplicationStatement) {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(psiElement.getProject());
      GrArgumentList argumentList = factory.createArgumentListFromText("xyz");
      argumentList.getFirstChild().delete(); // Workaround to get an empty argument list.
      PsiElement added = psiElement.addAfter(argumentList, psiElement.getLastChild());
      if (added instanceof GrArgumentList) {
        GrArgumentList addedArgumentList = (GrArgumentList)added;
        expressionMap.setPsiElement(addedArgumentList);
        return addedArgumentList;
      }
    }

    return null;
  }

  @Override
  public void applyDslExpressionMap(@NotNull GradleDslExpressionMap expressionMap) {
    maybeUpdateName(expressionMap);
  }
}
