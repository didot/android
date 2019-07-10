/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lang.androidSql.room

import com.android.support.AndroidxName
import com.android.tools.idea.kotlin.findAnnotation
import com.android.tools.idea.kotlin.findArgumentExpression
import com.android.tools.idea.kotlin.tryEvaluateConstant
import com.android.tools.idea.lang.androidSql.resolution.AndroidSqlColumn
import com.android.tools.idea.lang.androidSql.resolution.PRIMARY_KEY_NAMES
import com.android.tools.idea.lang.androidSql.resolution.PRIMARY_KEY_NAMES_FOR_FTS
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getScopeType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch.searchPsiClasses
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.asJava.elements.KtLightField
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtExpression

private val LOG = Logger.getInstance(RoomSchemaManager::class.java)

/** Utility for constructing a [RoomSchema] using IDE indices. */
class RoomSchemaManager(val module: Module, private val cachedValuesManager: CachedValuesManager) {
  companion object {
    fun getInstance(module: Module): RoomSchemaManager = ModuleServiceManager.getService(module, RoomSchemaManager::class.java)!!
  }

  private val schemas = ScopeType.values().associate { it to createCachedValue(it) }

  private fun createCachedValue(scope: ScopeType): CachedValue<RoomSchema> {
    return cachedValuesManager.createCachedValue {
      CachedValueProvider.Result(buildSchema(module, scope), PsiModificationTracker.MODIFICATION_COUNT)
    }
  }

  /**
   * Returns the [RoomSchema] visible from the given [PsiFile] or null if Room is not used in the project.
   *
   * The schema is cached in the file and recomputed after a change to java structure.
   *
   * @see PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT
   */
  fun getSchema(psiFile: PsiFile): RoomSchema? {
    val vFile = psiFile.originalFile.virtualFile ?: return null
    if (!module.moduleContentScope.contains(vFile)) return null

    val scopeType = module.getModuleSystem().getScopeType(vFile, module.project)
    return schemas[scopeType]!!.value
  }

  private val constantEvaluationHelper = JavaPsiFacade.getInstance(module.project).constantEvaluationHelper
  private val pointerManager = SmartPointerManager.getInstance(module.project)

  /** Builds the schema using IJ indexes. */
  private fun buildSchema(module: Module, scopeType: ScopeType): RoomSchema? {
    val scope = module.getModuleSystem().getResolveScope(scopeType)

    if (!isRoomPresentInScope(scope)) return null

    LOG.debug { "Recalculating Room schema for module ${module.name} for scope ${scopeType}" }

    val psiFacade = JavaPsiFacade.getInstance(module.project) ?: return null

    // Some of this logic is repeated in [RoomReferenceSearchExecutor], make sure to keep them in sync.
    val entities = processAnnotatedClasses(psiFacade, scope, RoomAnnotations.ENTITY) { createTable(it, RoomTable.Type.ENTITY) }
    val views = processAnnotatedClasses(psiFacade, scope, RoomAnnotations.DATABASE_VIEW) { createTable(it, RoomTable.Type.VIEW) }
    val databases = processAnnotatedClasses(psiFacade, scope, RoomAnnotations.DATABASE) { this.createDatabase(it, pointerManager) }
    val daos = processAnnotatedClasses(psiFacade, scope, RoomAnnotations.DAO) {
      Dao(pointerManager.createSmartPsiElementPointer(it))
    }

    return RoomSchema(databases, entities + views, daos)
  }

  /**
   * Finds classes annotated with the given annotation (both old and new names) and processes them using the supplied [processor] function,
   * gathering non-null results.
   */
  private fun <T : Any> processAnnotatedClasses(
    psiFacade: JavaPsiFacade,
    scope: GlobalSearchScope,
    annotation: AndroidxName,
    processor: (PsiClass) -> T?
  ): Set<T> {
    val result = HashSet<T>()
    annotation.bothNames { name ->
      psiFacade.findClass(name, scope)?.let { searchPsiClasses(it, scope).mapNotNullTo(result, processor) }
    }
    return result
  }

  private fun createTable(psiClass: PsiClass, type: RoomTable.Type): RoomTable? {
    val (tableName, tableNameElement) = getNameAndNameElement(
      psiClass,
      annotationName = when (type) {
        RoomTable.Type.ENTITY -> RoomAnnotations.ENTITY
        RoomTable.Type.VIEW -> RoomAnnotations.DATABASE_VIEW
      },
      annotationAttributeName = when (type) {
        RoomTable.Type.ENTITY -> "tableName"
        RoomTable.Type.VIEW -> "viewName"
      }
    ) ?: return null

    return RoomTable(
      pointerManager.createSmartPsiElementPointer(psiClass),
      type,
      tableName,
      pointerManager.createSmartPsiElementPointer(tableNameElement),
      createColumns(psiClass, tableName, type)
    )
  }

  private fun createColumns(psiClass: PsiClass, tableName: String, type: RoomTable.Type): Set<AndroidSqlColumn> {
    val columns = createColumnsFromFields(psiClass).toHashSet<AndroidSqlColumn>()
    val tableElement = pointerManager.createSmartPsiElementPointer(psiClass).element!!
    val primaryKeyElement = columns.find { it.isPrimaryKey }
    if (psiClass.annotations.any(::isFtsAnnotation)) {
      // In Fts tables we can use table name as a column in queries.
      columns.add(RoomFtsTableColumn(tableElement, tableName))
    }
    if (primaryKeyElement == null && type != RoomTable.Type.VIEW) {
      // ROWID can be referenced in queries using multiple special names [alternativePrimaryKeysNames], but doesn't need to be declared.
      // See [SQLite docs](https://sqlite.org/lang_createtable.html#rowid).
      columns.add(RoomRowidColumn(
        PsiElementForFakeColumn(tableElement), getPrimaryKeyNames(psiClass)))
    }

    return columns
  }

  private fun isFtsAnnotation(psiAnnotation: PsiAnnotation): Boolean {
    val qName = psiAnnotation.qualifiedName
    return when {
      RoomAnnotations.FTS3.isEquals(qName) -> true
      RoomAnnotations.FTS4.isEquals(qName) -> true
      else -> false
    }
  }

  private fun getPrimaryKeyNames(psiClass: PsiClass): Set<String> {
    return if (psiClass.annotations.any(::isFtsAnnotation)) PRIMARY_KEY_NAMES_FOR_FTS else PRIMARY_KEY_NAMES
  }

  private fun createColumnsFromFields(psiClass: PsiClass, namePrefix: String = ""): Sequence<RoomFieldColumn> {
    return psiClass.allFields
      .asSequence()
      .filterNot { it.modifierList?.hasModifierProperty(PsiModifier.STATIC) == true }
      .filterNot { it.modifierList?.findAnnotation(RoomAnnotations.IGNORE) != null }
      .flatMap { psiField ->
        val embeddedAnnotation = psiField.modifierList?.findAnnotation(RoomAnnotations.EMBEDDED)
        if (embeddedAnnotation != null) {
          createColumnsFromEmbeddedField(psiField, embeddedAnnotation, namePrefix)
        } else {
          val thisField = getNameAndNameElement(
            psiField,
            annotationName = RoomAnnotations.COLUMN_INFO,
            annotationAttributeName = "name"
          )
            ?.let { (columnName, columnNameElement) ->
              val isPrimaryKey = psiField.modifierList?.findAnnotation(RoomAnnotations.PRIMARY_KEY) != null

              RoomFieldColumn(
                pointerManager.createSmartPsiElementPointer(psiField),
                namePrefix + columnName,
                pointerManager.createSmartPsiElementPointer(columnNameElement),
                isPrimaryKey,
                if (isPrimaryKey) getPrimaryKeyNames(psiClass) else emptySet()
              )
            }

          if (thisField != null) sequenceOf(thisField) else emptySequence()
        }
      }
  }

  private fun createColumnsFromEmbeddedField(
    embeddedField: PsiField,
    embeddedAnnotation: PsiAnnotation,
    currentPrefix: String
  ): Sequence<RoomFieldColumn> {
    val newPrefix = embeddedAnnotation.findAttributeValue("prefix")
                      ?.let { constantEvaluationHelper.computeConstantExpression(it) }
                      ?.toString()
                    ?: ""

    val embeddedClass = PsiUtil.resolveClassInClassTypeOnly(embeddedField.type) ?: return emptySequence()

    return createColumnsFromFields(embeddedClass, currentPrefix + newPrefix)
  }

  private fun createDatabase(psiClass: PsiClass, pointerManager: SmartPointerManager): RoomDatabase? {
    val entitiesElementValue: HashSet<PsiClassPointer>? =
      psiClass.modifierList
        ?.findAnnotation(RoomAnnotations.DATABASE)
        ?.findDeclaredAttributeValue("tables")
        ?.let { it as? PsiArrayInitializerMemberValue }
        ?.initializers
        ?.mapNotNullTo(HashSet()) {
          val classObjectAccessExpression = it as? PsiClassObjectAccessExpression ?: return@mapNotNullTo null
          PsiUtil.resolveClassInClassTypeOnly(classObjectAccessExpression.operand.type)
            ?.let(pointerManager::createSmartPsiElementPointer)
        }

    return RoomDatabase(pointerManager.createSmartPsiElementPointer(psiClass),
                                                                    entitiesElementValue ?: emptySet())
  }

  private fun <T> getNameAndNameElement(
    element: T,
    annotationName: AndroidxName,
    annotationAttributeName: String
  ): Pair<String, PsiElement>?
    where T : PsiModifierListOwner,
          T : PsiNamedElement {
    // First look for the annotation that can override the name:
    return getAnnotationAndAnnotationName(element, annotationName, annotationAttributeName)
           // Fall back to the name used in code:
           ?: element.name?.let { it to element }
  }

  private fun KtLightField.getPropertyAnnotationExpression(
    annotationName: AndroidxName,
    annotationAttributeName: String
  ): KtExpression? {
    val annotationEntry = kotlinOrigin?.annotationEntries?.findAnnotation(annotationName) ?: return null
    // Property annotation it is annotation without target
    return if (annotationEntry.useSiteTarget == null) {
      annotationEntry.findArgumentExpression(annotationAttributeName)
    } else {
      null
    }
  }

  /**
   * Returns annotation PsiElement and override name from it
   *
   * if there is no correct annotation returns null
   */
  private fun <T> getAnnotationAndAnnotationName(
    element: T,
    annotationName: AndroidxName,
    annotationAttributeName: String
  ): Pair<String, PsiElement>?
    where T : PsiModifierListOwner,
          T : PsiNamedElement {
    var annotation: PsiElement? = element.modifierList
      ?.findAnnotation(annotationName)
      ?.findDeclaredAttributeValue(annotationAttributeName)
    var name: String? = annotation?.let { constantEvaluationHelper.computeConstantExpression(it)?.toString() }

    // There is special case for KtLightField when we have annotation without target (property annotation) e.g @ColumnInfo(name = 'override_name')
    // In that case element.modifierList.findAnnotation(annotationName) returns null because it searches only for annotation with FIELD target
    if (name == null && element is KtLightField) {
      val ktExpression = element.getPropertyAnnotationExpression(annotationName, annotationAttributeName)
      name = ktExpression?.let { tryEvaluateConstant(it) }
      if (name != null) annotation = ktExpression as PsiElement
    }

    return name?.let { it to annotation!! }
  }

  private inline fun AndroidxName.bothNames(f: (String) -> Unit) {
    f(oldName())
    f(newName())
  }

  fun List<KtAnnotationEntry>.findAnnotation(qualifiedName: AndroidxName): KtAnnotationEntry? {
    qualifiedName.bothNames { name ->
      val result = findAnnotation(name)
      if (result != null) {
        return result
      }
    }
    return null
  }

  fun PsiModifierList.findAnnotation(qualifiedName: AndroidxName): PsiAnnotation? {
    qualifiedName.bothNames { name ->
      val result = findAnnotation(name)
      if (result != null) {
        return result
      }
    }
    return null
  }
}
