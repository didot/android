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
import com.android.tools.idea.gradle.dsl.parser.elements.*
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.getBlockElement
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.text.StringUtil.unquoteString
import com.intellij.psi.PsiElement
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.kotlin.resolve.constants.evaluate.parseBoolean
import org.jetbrains.kotlin.resolve.constants.evaluate.parseNumericLiteral
import java.math.BigDecimal

/**
 * Parser for .gradle.kt files. This method produces a [GradleDslElement] tree.
 */
class KotlinDslParser(val psiFile : KtFile, val dslFile : GradleDslFile): KtVisitor<Unit, GradlePropertiesDslElement>(), GradleDslParser {

  //
  // Methods for GradleDslParser
  //
  override fun parse() {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    psiFile.script?.blockExpression?.statements?.map {
      if (it is KtScriptInitializer) it.body else it
    }?.requireNoNulls()?.forEach {
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
      is KtArrayAccessExpression -> {
        if (resolve) {
          val property = extraPropertyReferenceName(literal)
          val gradleDslElement = context.resolveReference(property ?: literal.text, true)
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
            return BigDecimal(parsedNumber.toString())
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
    // Currently, createBlock returns a block with two empty lines, we should keep only one new line.
    val firstStatementOrEmpty = block.firstChild.nextSibling
    if (firstStatementOrEmpty.text == "\n\n") {
      firstStatementOrEmpty.delete()
      block.addAfter(factory.createNewLine(), block.firstChild)
    }
    excludes.forEach {
      val group = if (FakeArtifactElement.shouldInterpolate(it.group)) iStr(it.group ?: "\"\"") else "\"${it.group}\""
      val name = if (FakeArtifactElement.shouldInterpolate(it.name)) iStr(it.name) else "\"${it.name}\""
      val text = "exclude(mapOf(\"group\" to $group, \"module\" to $name))"
      val expression = factory.createExpressionIfPossible(text)
      if (expression != null) {
        block.addBefore(expression, block.lastChild)
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

  // Check if this is a block with a methodCall as name, and get the block in such case. Ex: getByName("release") -> the release block.
  private fun methodCallBlock(expression: KtCallExpression, parent: GradlePropertiesDslElement): GradlePropertiesDslElement? {
    val arguments = expression.valueArgumentList?.arguments ?: return null
    if (arguments.size != 1) return null
    // TODO(xof): we should handle injections / resolving here:
    //  buildTypes.getByName("$foo") { ... }
    //  buildTypes.getByName(foo) { ... }
    val argument = arguments[0].getArgumentExpression()
    when (argument) {
      is KtStringTemplateExpression -> {
        if (argument.hasInterpolation()) return null
        val blockName = unquoteString((argument.entries[0] as KtLiteralStringTemplateEntry).text)
        val blockElement = dslFile.getBlockElement(listOf(blockName), parent) ?: return null
        if (blockElement is AbstractFlavorTypeDslElement) {
          // TODO(xof): this way of keeping track of how we got hold of the block (which method name) only works once
          blockElement.setMethodName(expression.name())
        }
        return blockElement
      }
      else -> return null
    }
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
      val blockElements = ArrayList<GradlePropertiesDslElement>()
      // If the block is allprojects, we need to apply the closure to the project and to all its subprojetcs.
      if (parent is GradleDslFile && referenceName == "allprojects") {
        // The block has to be applied to the project.
        blockElements.add(parent)
        // Then the block should be applied to subprojects.
        referenceName = "subprojects"
      }
      val blockElement = methodCallBlock(expression, parent) ?: dslFile.getBlockElement(listOf(referenceName), parent) ?: return
      val argumentsBlock = expression.lambdaArguments.getOrNull(0)?.getLambdaExpression()?.bodyExpression

      blockElement.setPsiElement(argumentsBlock)
      blockElements.add(blockElement)
      blockElements.forEach { block ->
        // Visit the children of this element, with the current block set as parent.
        expression.lambdaArguments.getOrNull(0)?.getLambdaExpression()?.bodyExpression?.statements?.requireNoNulls()?.forEach {
          it.accept(this, block)
        }
      }
    }
    else {
      // Get args and block.
      val argumentsList = expression.valueArgumentList
      val argumentsBlock = expression.lambdaArguments.getOrNull(0)?.getLambdaExpression()?.bodyExpression
      val referenceExpression = expression.referenceExpression()
      val name =
        if (referenceExpression != null) GradleNameElement.from(referenceExpression) else GradleNameElement.create(referenceName)
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

  override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression, parent: GradlePropertiesDslElement) {

    fun parentBlockFromReceiver(receiver: KtExpression): GradlePropertiesDslElement? {
      var current = parent
      receiver.accept(object : KtTreeVisitorVoid() {
        // TODO(xof): need to handle android.getByName("buildTypes").release { ... }
        override fun visitReferenceExpression(expression: KtReferenceExpression) {
          when (expression) {
            is KtNameReferenceExpression -> {
              current = dslFile.getBlockElement(listOf(expression.text), current) ?: return
            }
            else -> Unit
          }
        }
        override fun visitCallExpression(expression: KtCallExpression) {
          current = methodCallBlock(expression, current) ?: return
        }
      }, null)
      return current
    }

    // android.buildTypes.release { minify_enabled true }
    val receiver = expression.receiverExpression
    val selector = expression.selectorExpression
    when (selector) {
      is KtCallExpression -> {
        val parentBlock = parentBlockFromReceiver(receiver)
        if (parentBlock == null) {
          super.visitDotQualifiedExpression(expression, parent)
          return
        }
        visitCallExpression(selector, parentBlock)
      }
      else -> super.visitDotQualifiedExpression(expression, parent)
    }
  }

  override fun visitBinaryExpression(expression: KtBinaryExpression, parent: GradlePropertiesDslElement) {
    var parentBlock = parent
    val name: GradleNameElement
    // Check the expression is valid.
    if (expression.operationToken != KtTokens.EQ) return
    val left = expression.left ?: return
    val right = expression.right ?: return
    when (val property = extraPropertyReferenceName(left)) {
      is String -> {
        // we have something of the form extra["literalString"] = init
        parentBlock = getBlockElement(listOf("ext"), parent, null) ?: return
        name = GradleNameElement.create(property)
      }
      else -> {
        name = GradleNameElement.from(left)
        if (name.isEmpty) return
        if (name.isQualified) {
          val nestedElement = getBlockElement(name.qualifyingParts(), parent, null) ?: return
          parentBlock = nestedElement
        }
      }
    }
    val propertyElement = createExpressionElement(parentBlock, expression, name, right) ?: return
    propertyElement.setUseAssignment(true)
    propertyElement.setElementType(REGULAR)

    parentBlock.setParsedElement(propertyElement)
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
      // This Property is assigning a value to a property, so we need to set the UseAssignment to true.
      propertyElement.setUseAssignment(true)
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
      return getExpressionMap(parentElement, psiElement, name, argumentsList.arguments)
    }
    else if (methodName == "listOf") {
      return getExpressionList(parentElement, psiElement, name, argumentsList.arguments, false)
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
        val argumentsName = (arguments[0].getArgumentExpression() as KtCallExpression).name() ?: return null
        if (isFirstCall) {
          val argumentsList = argumentExpression.valueArgumentList
          return if (argumentsList != null) getCallExpression(
            parentElement, argumentExpression, name, argumentsList, argumentsName, false) else null
        }
        return getMethodCall(
          parentElement,
          psiElement,
          // isNamed() checks if getArgumentName() is not null, so using !! here is safe (unless the implementation changes).
          if (arguments[0].isNamed()) GradleNameElement.create(arguments[0].getArgumentName()!!.text) else name,
          methodName)
      }
      if (isFirstCall && !arguments[0].isNamed()) {
        return getExpressionElement(
          parentElement,
          arguments[0],
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
    val arguments = (psiElement as KtCallExpression).valueArgumentList ?: return methodCall
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
      // Filter map elements that either have a left or right element null. This filter makes using !! safe in the next mapNotNull fun.
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
      argumentExpression,
      // isNamed() checks if getArgumentName() is not null, so using !! here is safe (unless the implementation changes).
      if (argumentExpression.isNamed())
        GradleNameElement.create(argumentExpression.getArgumentName()!!.text) else GradleNameElement.empty(),
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
        val expressionName = expression.name() ?: return null
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
