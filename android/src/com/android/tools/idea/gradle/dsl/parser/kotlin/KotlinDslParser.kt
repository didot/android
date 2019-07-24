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
package com.android.tools.idea.gradle.dsl.parser.kotlin

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.iStr
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.VARIABLE
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.model.notifications.NotificationTypeReference
import com.android.tools.idea.gradle.dsl.model.notifications.NotificationTypeReference.INVALID_EXPRESSION
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection
import com.android.tools.idea.gradle.dsl.parser.android.AbstractFlavorTypeDslElement
import com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSettableExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslUnknownElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.getBlockElement
import com.google.common.collect.Lists
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.text.StringUtil.unquoteString
import com.intellij.psi.PsiElement
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtScriptInitializer
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.kotlin.resolve.constants.evaluate.parseBoolean
import org.jetbrains.kotlin.resolve.constants.evaluate.parseNumericLiteral

/**
 * Parser for .gradle.kt files. This method produces a [GradleDslElement] tree.
 */
class KotlinDslParser(val psiFile : KtFile, val dslFile : GradleDslFile): KtVisitor<Unit, GradlePropertiesDslElement>(), GradleDslParser {

  //
  // Methods for GradleDslParser
  //
  override fun parse() {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    psiFile.script!!.blockExpression.statements.map {
      if (it is KtScriptInitializer) it.body else it
    }.requireNoNulls().forEach {
      it.accept(this, dslFile)
    }
  }

  override fun convertToPsiElement(literal: Any): PsiElement? {
    return try {
      createLiteral(dslFile, literal)
    }
    catch (e : IncorrectOperationException) {
      dslFile.context.getNotificationForType(dslFile, INVALID_EXPRESSION).addError(e)
      null
    }
  }

  override fun setUpForNewValue(context: GradleDslLiteral, newValue: PsiElement?) {
    val isReference = newValue is KtNameReferenceExpression || newValue is KtDotQualifiedExpression ||
                      newValue is KtClassLiteralExpression || newValue is KtArrayAccessExpression
    context.isReference = isReference
  }

  override fun extractValue(context: GradleDslSimpleExpression, literal: PsiElement, resolve: Boolean): Any? {
    when (literal) {
      // Ex: KotlinCompilerVersion
      is KtNameReferenceExpression -> {
        if (resolve) {
          val gradleDslElement = context.resolveReference(literal.text, true)
          // Only get the value if the element is a GradleDslSimpleExpression.
          if (gradleDslElement is GradleDslSimpleExpression) {
            return gradleDslElement.value
          }
        }
        return unquoteString(literal.text)
      }
      // For String and constant literals. Ex : Integers, single-quoted Strings.
      is KtStringTemplateExpression, is KtStringTemplateEntry -> {
        if (!resolve || context.hasCycle()) {
          return unquoteString(literal.text)
        }
        val injections = context.resolvedVariables
        return GradleReferenceInjection.injectAll(literal, injections)
      }
      is KtConstantExpression -> {
        return when (literal.node.elementType) {
          KtNodeTypes.INTEGER_CONSTANT-> {
            val numericalValue = parseNumericLiteral(literal.text, literal.node.elementType)
            if (numericalValue is Long && (numericalValue > Integer.MAX_VALUE || numericalValue < Integer.MIN_VALUE)) return numericalValue
            // TODO: Add support to byte and short if needed.
            return (numericalValue as Number).toInt()
          }
          KtNodeTypes.FLOAT_CONSTANT -> {
            val parsedNumber = parseNumericLiteral(literal.text, literal.node.elementType)
            if (parsedNumber is Float) return parsedNumber.toBigDecimal()
            return (parsedNumber as Double).toBigDecimal()
          }
          KtNodeTypes.BOOLEAN_CONSTANT -> parseBoolean(literal.text)
          else -> unquoteString(literal.text)
        }
      }
      else -> return ReferenceTo(literal.text)
    }
  }

  override fun convertToExcludesBlock(excludes: List<ArtifactDependencySpec>): PsiElement? {
    val factory = KtPsiFactory(dslFile.project)
    val block = factory.createBlock("")
    excludes.forEach {
      val group = if (FakeArtifactElement.shouldInterpolate(it.group)) iStr(it.group ?: "''") else "'$it.group'"
      val name = if (FakeArtifactElement.shouldInterpolate(it.name)) iStr(it.name) else "'$it.name'"
      val text = "exclude(group = $group, module = $name)"
      val expression = factory.createExpressionIfPossible(text)
      if (expression != null) {
        block.addAfter(expression, block.lastChild)
        block.addBefore(factory.createNewLine(), block.lastChild)
      }
    }
    return block
  }

  override fun shouldInterpolate(elementToCheck: GradleDslElement): Boolean {
    return when (elementToCheck) {
      is GradleDslSettableExpression -> elementToCheck.currentElement is KtStringTemplateExpression
      is GradleDslSimpleExpression -> elementToCheck.expression is KtStringTemplateExpression
      else -> elementToCheck.psiElement is KtStringTemplateExpression
    }
  }

  override fun getResolvedInjections(context: GradleDslSimpleExpression, psiElement: PsiElement): List<GradleReferenceInjection> {
    return findInjections(context, psiElement, false)
  }

  override fun getInjections(context: GradleDslSimpleExpression, psiElement: PsiElement): List<GradleReferenceInjection> {
    return findInjections(context, psiElement, true)
  }

  override fun getBlockElement(nameParts: List<String>,
                               parentElement: GradlePropertiesDslElement,
                               nameElement: GradleNameElement?): GradlePropertiesDslElement? {
    return dslFile.getBlockElement(nameParts, parentElement, nameElement)
  }

  //
  // Methods to perform the parsing on the KtFile
  //
  override fun visitCallExpression(expression: KtCallExpression, parent: GradlePropertiesDslElement) {
    // If the call expression has no name, we don't know how to handle it.
    var referenceName = expression.name() ?: return
    // If expression is a pure block element and not an expression.
    if (expression.isBlockElement()) {
      // We might need to apply the block to multiple DslElements.
      val blockElements = Lists.newArrayList<GradlePropertiesDslElement>()
      // If the block is allprojects, we need to apply the closure to the project and to all its subprojetcs.
      if (parent is GradleDslFile && referenceName == "allprojects") {
        // The block has to be applied to the project.
        blockElements.add(parent)
        // Then the block should be applied to subprojects.
        referenceName = "subprojects"
      }
      val blockElement : GradlePropertiesDslElement
      // Check if this is a block with a methodCall as name, and get its correct name in such case. Ex: getByName("release") -> release.
      if (expression.valueArgumentList != null && (expression.valueArgumentList as KtValueArgumentList).arguments.size == 1) {
        val blockName =
          (((expression.valueArgumentList as KtValueArgumentList).arguments[0].getArgumentExpression()
            as KtStringTemplateExpression).entries[0] as KtLiteralStringTemplateEntry).text
        blockElement = dslFile.getBlockElement(listOf(blockName), parent) ?: return
        if (blockElement is AbstractFlavorTypeDslElement) {
          blockElement.setMethodName(referenceName)
        }
      }
      else {
        blockElement = dslFile.getBlockElement(listOf(referenceName), parent) ?: return
      }
      // Get the block psi element of expression.
      val argumentsBlock = expression.lambdaArguments.getOrNull(0)?.getLambdaExpression()?.bodyExpression

      blockElement.setPsiElement(argumentsBlock)
      blockElements.add(blockElement)
      blockElements.forEach { block ->
        // Visit the children of this element, with the current block set as parent.
        expression.lambdaArguments[0]!!.getLambdaExpression()?.bodyExpression?.statements?.requireNoNulls()?.forEach {
          it.accept(this, block)
        }
      }
    }
    else {
      // Get args and block.
      val argumentsList = expression.valueArgumentList
      val argumentsBlock = expression.lambdaArguments.getOrNull(0)?.getLambdaExpression()?.bodyExpression
      val name = GradleNameElement.from(expression.referenceExpression()!!)
      if (argumentsList != null) {
        val callExpression =
          getCallExpression(parent, expression, name, argumentsList, referenceName, true) ?: return
        if (argumentsBlock != null) {
          callExpression.setParsedClosureElement(getClosureBlock(callExpression, argumentsBlock, name))
        }

        callExpression.elementType = REGULAR
        parent.addParsedElement(callExpression)
      }

    }
  }

  override fun visitBinaryExpression(expression: KtBinaryExpression, parent: GradlePropertiesDslElement) {
    var parentBlock = parent
    // Check the expression is valid.
    if (expression.operationToken != KtTokens.EQ) return
    val left = expression.left ?: return
    val right = expression.right ?: return
    val name = GradleNameElement.from(left)
    if (name.isEmpty) return
    if (name.isQualified) {
      val nestedElement = getBlockElement(name.qualifyingParts(), parent, null) ?: return
      parentBlock = nestedElement
    }
    val propertyElement = createExpressionElement(parentBlock, expression, name, right) ?: return
    propertyElement.setUseAssignment(true)
    propertyElement.setElementType(REGULAR)

    parent.setParsedElement(propertyElement)
  }

  override fun visitProperty(expression: KtProperty, parent: GradlePropertiesDslElement) {
    val identifier = expression.nameIdentifier ?: return
    val delegate = expression.delegate
    if (delegate == null) {
      // handling standard KtProperties ( val foo = ...) to support variables
      val initializer = expression.initializer ?: return

      // if we've got this far, we have a variable declaration/initialization of the form "val foo = bar"

      val name = GradleNameElement.from(identifier)
      val propertyElement = createExpressionElement(parent, expression, name, initializer) ?: return
      propertyElement.elementType = VARIABLE
      parent.setParsedElement(propertyElement)
    }
    else {
      // handling delegated KtProperties ( val foo by ... ) to support Gradle extra properties.
      val callExpression = delegate.expression as? KtCallExpression ?: return

      val referenceExpression = callExpression.referenceExpression() ?: return
      // TODO(xof): it's more complicated than this, of course; kotlinscript can express gradle properties on other
      //  projects' extra blocks, using "val foo by rootProject.extra(42)".  The Kotlinscript Psi tree is the wrong way round
      //  for us to detect it easily: (rootProject dot (extra [42])) rather than ((rootProject dot extra) [42]) so more work
      //  is needed here.
      if (referenceExpression.text != "extra") return
      val arguments = callExpression.valueArgumentList?.arguments ?: return
      if (arguments.size != 1) return
      val initializer = arguments[0].getArgumentExpression() ?: return

      // If we've got this far, we have an extra property declaration/initialization of the form "val foo by extra(bar)".

      val ext = getBlockElement(listOf("ext"), parent, null) ?: return
      val name = GradleNameElement.from(identifier) // TODO(xof): error checking: empty/qualified/etc
      val propertyElement = createExpressionElement(ext, expression, name, initializer) ?: return
      propertyElement.elementType = REGULAR
      ext.setParsedElement(propertyElement)
    }
  }

  private fun getCallExpression(
    parentElement : GradleDslElement,
    psiElement : PsiElement,
    name : GradleNameElement,
    argumentsList : KtValueArgumentList,
    methodName : String,
    isFirstCall : Boolean
  ) : GradleDslExpression? {
    if (methodName == "mapOf") {
      return getExpressionMap(parentElement, argumentsList, name, argumentsList.arguments)
    }
    else if (methodName == "listOf") {
      return getExpressionList(parentElement, argumentsList, name, argumentsList.arguments, false)
    }
    else if (methodName == "kotlin") {
      // If the method has one argument, we should check if it's declared under a dependency block in order to resolve it to a dependency,
      // or to a plugin otherwise.
      if (argumentsList.arguments.size == 1) {
        val nameExpression = argumentsList.arguments[0].getArgumentExpression() ?: return null
        if (parentElement.fullName == "plugins") {
          val pluginName = "kotlin-${unquoteString(nameExpression.text)}"
          val literalExpression = GradleDslLiteral(parentElement, psiElement, name, psiElement, false)
          literalExpression.setValue(pluginName)
          return literalExpression
        }
        else {
          val dependencyName = "org.jetbrains.kotlin:kotlin-${unquoteString(nameExpression.text)}"
          val dependencyLiteral = GradleDslLiteral(parentElement, psiElement, name, psiElement, false)
          dependencyLiteral.setValue(dependencyName)
          return dependencyLiteral
        }
      }
      // If the method has two arguments then it should automatically resolve to a dependency.
      else if (argumentsList.arguments.size == 2) {
        // arguments are not null and each argument will have an non null argumentExpression as well, so using !! in this case is safe.
        val moduleName = argumentsList.arguments[0].getArgumentExpression() ?: return null
        val version = argumentsList.arguments[1].getArgumentExpression() ?: return null
        val dependencyName = "org.jetbrains.kotlin:kotlin-${unquoteString(moduleName.text)}:${unquoteString(version.text)}"
        val dependencyLiteral = GradleDslLiteral(parentElement, psiElement, name, psiElement, false)
        dependencyLiteral.setValue(dependencyName)
        return dependencyLiteral
      }
    }

    // If the CallExpression has one argument only that is a callExpression, we skip the current CallExpression.
    val arguments = argumentsList.arguments
    if (arguments.size != 1) {
      return getMethodCall(parentElement, psiElement, name, methodName)
    }
    else {
      val argumentExpression = arguments[0].getArgumentExpression()
      if (argumentExpression is KtCallExpression) {
        val argumentsName = (arguments[0].getArgumentExpression() as KtCallExpression).name()!!
        if (isFirstCall) {
          return getCallExpression(
            parentElement, argumentExpression, name, argumentExpression.valueArgumentList!!, argumentsName, false)
        }
        return getMethodCall(
          parentElement,
          psiElement,
          if (arguments[0].isNamed()) GradleNameElement.create(arguments[0].getArgumentName()!!.text) else name,
          methodName)
      }
      if (isFirstCall && !arguments[0].isNamed()) {
        return getExpressionElement(
          parentElement,
          arguments[0].getArgumentExpression() as PsiElement,
          name,
          arguments[0].getArgumentExpression() as KtElement)
      }
      return getMethodCall(parentElement, psiElement, name, methodName)
    }
  }

  private fun getMethodCall(parent : GradleDslElement,
                            psiElement: PsiElement,
                            name : GradleNameElement,
                            methodName: String) : GradleDslMethodCall {

    val methodCall = GradleDslMethodCall(parent, psiElement, name, methodName, false)
    val arguments = (psiElement as KtCallExpression).valueArgumentList!!
    val argumentList = getExpressionList(methodCall, arguments, name, arguments.arguments, false)
    methodCall.setParsedArgumentList(argumentList)
    return methodCall
  }

  private fun getExpressionMap(parentElement : GradleDslElement,
                               mapPsiElement: PsiElement,
                               propertyName : GradleNameElement,
                               arguments : List<KtElement>) : GradleDslExpressionMap {
    val expressionMap = GradleDslExpressionMap(parentElement, mapPsiElement, propertyName, false)
    arguments.map {
      arg -> (arg as KtValueArgument).getArgumentExpression()
    }.filter {
      expression -> expression is KtBinaryExpression && expression.operationReference.getReferencedName() == "to" &&
                    expression.left != null && expression.right != null
    }.mapNotNull {
      expression -> createExpressionElement(
      expressionMap, mapPsiElement, GradleNameElement.from((expression as KtBinaryExpression).left!!), expression.right!!)
    }.forEach(expressionMap::addParsedElement)

    return expressionMap
  }

  private fun getExpressionList(parentElement : GradleDslElement,
                                listPsiElement : PsiElement,
                                propertyName : GradleNameElement,
                                valueArguments : List<KtElement>,
                                isLiteral : Boolean) : GradleDslExpressionList {
    val expressionList = GradleDslExpressionList(parentElement, listPsiElement, propertyName, isLiteral)
    valueArguments.map {
      expression -> expression as KtValueArgument
    }.filter {
      arg -> arg.getArgumentExpression() != null
    }.mapNotNull {
      argumentExpression -> createExpressionElement(
      expressionList,
      argumentExpression.getArgumentExpression()!!,
      if (argumentExpression.isNamed()) GradleNameElement.create(argumentExpression.getArgumentName()!!.text) else GradleNameElement.empty(),
      argumentExpression.getArgumentExpression() as KtExpression)
    }.forEach {
      if (it is GradleDslClosure) {
        parentElement.setParsedClosureElement(it)
      }
      else {
        expressionList.addParsedExpression(it)
      }
    }
    return expressionList
  }

  private fun createExpressionElement(parent : GradleDslElement,
                                      psiElement : PsiElement,
                                      name: GradleNameElement,
                                      expression : KtExpression) : GradleDslExpression? {
    // Parse all the ValueArgument types.
    when (expression) {
      is KtValueArgumentList -> return getExpressionList(parent, expression, name, expression.arguments, true)
      is KtCallExpression -> {
        // Ex: implementation(kotlin("stdlib-jdk7")).
        val expressionName = expression.name()!!
        val arguments = expression.valueArgumentList ?: return null
        return getCallExpression(parent, expression, name, arguments, expressionName, false)
      }
      is KtParenthesizedExpression -> return createExpressionElement(parent, psiElement, name, expression.expression ?: expression)
      else -> return getExpressionElement(parent, psiElement, name, expression)
    }
  }

  private fun getExpressionElement(parentElement : GradleDslElement,
                                   psiElement : PsiElement,
                                   propertyName: GradleNameElement,
                                   propertyExpression : KtElement) : GradleDslExpression {
    return when (propertyExpression) {
      // Ex: versionName = 1.0. isQualified = false.
      is KtStringTemplateExpression, is KtConstantExpression -> GradleDslLiteral(
        parentElement, psiElement, propertyName, propertyExpression, false)
      // Ex: compileSdkVersion(SDK_VERSION).
      is KtNameReferenceExpression -> GradleDslLiteral(parentElement, psiElement, propertyName, propertyExpression, true)
      // Ex: KotlinCompilerVersion.VERSION.
      is KtDotQualifiedExpression -> GradleDslLiteral(parentElement, psiElement, propertyName, propertyExpression, true)
      // Ex: Delete::class.
      is KtClassLiteralExpression -> GradleDslLiteral(
        parentElement, psiElement, propertyName, propertyExpression.receiverExpression as  PsiElement, true)
      // Ex: extra["COMPILE_SDK_VERSION"]
      is KtArrayAccessExpression -> GradleDslLiteral(
        parentElement, psiElement, propertyName, propertyExpression, true)
      else -> {
        // The expression is not supported.
        parentElement.notification(NotificationTypeReference.INCOMPLETE_PARSING).addUnknownElement(propertyExpression)
        return GradleDslUnknownElement(parentElement, propertyExpression, propertyName)
      }
    }
  }

  private fun getClosureBlock(
    parentElement: GradleDslElement, closableBlock : KtBlockExpression, propertyName: GradleNameElement) : GradleDslClosure {
    val closureElement = GradleDslClosure(parentElement, closableBlock, propertyName)
    closableBlock.statements?.requireNoNulls()?.forEach {
      it.accept(this, closureElement)
    }
    return closureElement
  }

}