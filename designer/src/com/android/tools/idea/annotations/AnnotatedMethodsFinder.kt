/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.annotations

import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.getPsiFileSafely
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.isRejected
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds if [vFile] in [project] has methods annotated with any of the given [annotations] FQCN or the given [shortAnnotationName].
 */
private fun hasAnnotatedMethodsUncached(project: Project, vFile: VirtualFile,
                                        annotations: Set<String>,
                                        shortAnnotationName: String): Boolean = runReadAction {
  // This method can not call any methods that require smart mode.
  fun isFullNameAnnotation(annotation: KtAnnotationEntry) =
    // We use text() to avoid obtaining the FQN as that requires smart mode
    annotations.any { annotationFqn ->
      // In brackets annotations don't start with '@', but typical annotations do. Normalize them by removing it
      annotation.text.removePrefix("@").startsWith(annotationFqn)
    }

  val psiFile = PsiManager.getInstance(project).findFile(vFile)
  // Look into the imports first to avoid resolving the class name into all methods.
  val hasAnnotationImport = PsiTreeUtil.findChildrenOfType(psiFile, KtImportDirective::class.java)
    .any { annotations.contains(it.importedFqName?.asString()) }

  return@runReadAction if (hasAnnotationImport) {
    PsiTreeUtil.findChildrenOfType(psiFile, KtAnnotationEntry::class.java)
      .any {
        it.shortName?.asString() == shortAnnotationName || isFullNameAnnotation(it)
      }
  }
  else {
    // The annotation is not imported so check if the method is using full name import.
    PsiTreeUtil.findChildrenOfType(psiFile, KtAnnotationEntry::class.java)
      .any(::isFullNameAnnotation)
  }
}

/**
 * A mapping to keep track of the cache [Key]s for the annotation combinations. Each [Key] instance is unique by implementation and [Key]
 * declares [hashCode] and [equals] methods as final. Thus, this mapping allows to reuse the same [Key] instance for the same [localKey].
 */
@VisibleForTesting
object CacheKeysManager {
  private val annotationCacheKeys = ConcurrentHashMap<Any, Key<out CachedValue<out Any>>>()

  fun <T : Any> getKey(localKey: Any): Key<CachedValue<T>> {
    return annotationCacheKeys.getOrPut(localKey) { Key<CachedValue<T>>(localKey.toString()) } as Key<CachedValue<T>>
  }

  @VisibleForTesting
  fun map() = annotationCacheKeys
}

data class HasMethodsKey(val annotations: Set<String>, val shortAnnotationName: String)

fun <T> CachedValuesManager.getCachedValue(dataHolder: UserDataHolder, key: Key<CachedValue<T>>, provider: CachedValueProvider<T>): T =
  this.getCachedValue(dataHolder, key, provider, false)


/**
 * Finds if [vFile] in [project] has methods annotated with any of the given [annotations] FQCN or the given [shortAnnotationName].
 * Utilizes caching.
 */
fun hasAnnotatedMethods(project: Project, vFile: VirtualFile, annotations: Set<String>, shortAnnotationName: String): Boolean {
  val psiFile = AndroidPsiUtils.getPsiFileSafely(project, vFile) ?: return false
  return CachedValuesManager.getManager(project).getCachedValue(
    psiFile,
    CacheKeysManager.getKey(HasMethodsKey(annotations, shortAnnotationName))) {
    CachedValueProvider.Result.createSingleDependency(
      hasAnnotatedMethodsUncached(project, vFile, annotations, shortAnnotationName),
      psiFile
    )
  }
}

/**
 * Finds all the [KtAnnotationEntry] in [vFile] in [project] with [shortAnnotationName] as name.
 */
fun findAnnotations(project: Project, vFile: VirtualFile, shortAnnotationName: String): Collection<KtAnnotationEntry> {
  if (DumbService.isDumb(project)) {
    Logger.getInstance(AnnotatedMethodsFinder::class.java)
      .debug("findAnnotations for @$shortAnnotationName called while indexing. No annotations will be found")
    return emptyList()
  }

  val psiFile = AndroidPsiUtils.getPsiFileSafely(project, vFile) ?: return emptyList()
  return CachedValuesManager.getManager(project).getCachedValue(
    psiFile,
    CacheKeysManager.getKey(shortAnnotationName)) {
    val kotlinAnnotations: Sequence<PsiElement> = ReadAction.compute<Sequence<PsiElement>, Throwable> {
      KotlinAnnotationsIndex.getInstance().get(shortAnnotationName, project, GlobalSearchScope.fileScope(project, vFile)).asSequence()
    }

    val annotations = kotlinAnnotations
      .filterIsInstance<KtAnnotationEntry>()
      .toList()

    CachedValueProvider.Result.create(annotations.distinct(), psiFile)
  }
}

/**
 * A [ModificationTracker] that tracks a [Promise] and can be used as a dependency for [CachedValuesManager.getCachedValue].
 * If the promise is rejected or fails, this will update the count, invalidating the cache (so it's not stored).
 */
private class PromiseModificationTracker(private val promise: Promise<*>) : ModificationTracker {
  private var modificationCount = 0L
  override fun getModificationCount(): Long = when {
    promise.isRejected -> ++modificationCount // The promise failed so we ensure it is not cached
    else -> modificationCount
  }
}

fun UMethod?.isAnnotatedWith(annotations: Set<String>) = runReadAction {
  this?.uAnnotations?.any { annotation -> annotations.contains(annotation.qualifiedName) } ?: false
}

/**
 * Returns the [UMethod] annotated by this [UAnnotation], or null if it is not annotating
 * a method, or if the method is not also annotated with [annotations].
 */
fun UAnnotation.getContainingUMethodAnnotatedWith(annotations: Set<String>): UMethod? = runReadAction {
  val uMethod = getContainingUMethod() ?: javaPsi?.parentOfType<PsiMethod>()?.toUElement(UMethod::class.java)
  if (uMethod.isAnnotatedWith(annotations)) uMethod else null
}

/**
 * Returns a [CachedValueProvider] that provides values of type [T] from the methods annotated with [annotations] and [shortAnnotationName]
 * from [vFile] of [project]. Technically, this function could just return a collection of methods, but [toValues] might be slow
 * to calculate so caching the values rather than methods is more useful.
 */
private fun <T> findAnnotatedMethodsCachedValues(
  project: Project,
  vFile: VirtualFile,
  annotations: Set<String>,
  shortAnnotationName: String,
  toValues: (methods: List<UMethod>) -> Sequence<T>
): CachedValueProvider<CompletableDeferred<Collection<T>>> =
  CachedValueProvider {
    // This Deferred should not be needed, the promise could be returned directly. However, it seems there is a compiler issue that
    // causes the findAnnotatedMethodsValues to fail when using the "dist" build (not from source).
    // Using the deferred seems to avoid the problem. b/222843951.
    val deferred = CompletableDeferred<Collection<T>>()
    val promise = ReadAction
      .nonBlocking(Callable<Collection<T>> {
        val uMethods = findAnnotations(project, vFile, shortAnnotationName)
          .mapNotNull { (it.psiOrParent.toUElementOfType() as? UAnnotation)?.getContainingUMethodAnnotatedWith(annotations) }
          .distinct() // avoid looking more than once per method

        toValues(uMethods).toList()
      })
      .inSmartMode(project)
      .coalesceBy(project, vFile)
      .submit(AppExecutorUtil.getAppExecutorService())
      .onSuccess {
        deferred.complete(it)
      }
      .onError {
        deferred.completeExceptionally(it)
      }

    val kotlinJavaModificationTracker = PsiModificationTracker.SERVICE.getInstance(project).forLanguages { lang ->
      lang.`is`(KotlinLanguage.INSTANCE) || lang.`is`(JavaLanguage.INSTANCE)
    }
    CachedValueProvider.Result.create(deferred, kotlinJavaModificationTracker, PromiseModificationTracker(promise))
  }

/**
 * Maximum number of times to retries [findAnnotatedMethodsCachedValues] if failed.
 */
private const val MAX_NON_BLOCKING_ACTION_RETRIES = 3

data class CachedValuesKey<T>(val annotations: Set<String>,
                              val shortAnnotationName: String,
                              val toValues: (methods: List<UMethod>) -> Sequence<T>)

/**
 * Finds all the values calculated by [toValues] associated with the methods annotated with [annotations] and [shortAnnotationName] from
 * [vFile] in [project].
 */
suspend fun <T> findAnnotatedMethodsValues(
  project: Project,
  vFile: VirtualFile,
  annotations: Set<String>,
  shortAnnotationName: String,
  toValues: (methods: List<UMethod>) -> Sequence<T>): Collection<T> {
  val psiFile = getPsiFileSafely(project, vFile) ?: return emptyList()
  // This method will try to obtain the result MAX_NON_BLOCKING_ACTION_RETRIES, waiting 10 milliseconds more on every retry.
  // findMethodsCachedValues uses a non blocking read action so this allows for the action to be cancelled. The action will be
  // retried again. If MAX_NON_BLOCKING_ACTION_RETRIES retries are done, this method will return an empty list of elements.
  return withContext(AndroidDispatchers.workerThread) {
    var retries = MAX_NON_BLOCKING_ACTION_RETRIES
    var result: Collection<T>? = null
    while (result == null && retries > 0) {
      retries--
      val promiseResult = runReadAction {
        CachedValuesManager.getManager(project).getCachedValue(
          psiFile,
          CacheKeysManager.getKey(CachedValuesKey(annotations, shortAnnotationName, toValues)),
          findAnnotatedMethodsCachedValues(project, vFile, annotations, shortAnnotationName, toValues))
      }
      try {
        result = promiseResult.await()
      } catch (_: Throwable) {
        delay((MAX_NON_BLOCKING_ACTION_RETRIES - retries) * 10L)
      }
    }
    result ?: emptyList()
  }
}

private object AnnotatedMethodsFinder
