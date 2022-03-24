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
package com.android.tools.idea.concurrency

import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.utils.reflection.qualifiedName
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.application.ex.ApplicationUtil.CannotRunReadActionException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * [CoroutineDispatcher]s equivalent to executors defined in [AndroidExecutors].
 */
object AndroidDispatchers {
  /**
   * [CoroutineDispatcher] that dispatches to the UI thread with [ModalityState.defaultModalityState].
   *
   * @see AndroidExecutors.uiThreadExecutor
   */
  val uiThread: CoroutineDispatcher get() = uiThread(ModalityState.defaultModalityState())

  /**
   * Creates a [CoroutineDispatcher] that uses the UI thread with the given [ModalityState].
   *
   * @see AndroidExecutors.uiThreadExecutor
   */
  fun uiThread(modalityState: ModalityState): CoroutineDispatcher {
    return Executor { block -> AndroidExecutors.getInstance().uiThreadExecutor(modalityState, block) }.asCoroutineDispatcher()
  }

  /**
   * [CoroutineDispatcher] that dispatches to a background worker thread.
   *
   * @see AndroidExecutors.workerThreadExecutor
   */
  val workerThread: CoroutineDispatcher get() = AndroidExecutors.getInstance().workerThreadExecutor.asCoroutineDispatcher()

  /**
   * [CoroutineDispatcher] that dispatches to a disk IO thread. Please notice that the disk IO
   * thread pool is very limited and should not be used for anything except local disk IO.
   * For socket IO and inter-process communication please use [kotlinx.coroutines.Dispatchers.IO].
   *
   * @see AndroidExecutors.diskIoThreadExecutor
   */
  val diskIoThread: CoroutineDispatcher get() = AndroidExecutors.getInstance().diskIoThreadExecutor.asCoroutineDispatcher()
}

private val LOG: Logger get() = Logger.getInstance("CoroutinesUtils.kt")

/**
 * Exception handler similar to IDEA's default behavior (see [com.intellij.idea.StartupUtil.installExceptionHandler]) that additionally
 * logs the [CoroutineName] from context.
 */
val androidCoroutineExceptionHandler = CoroutineExceptionHandler { ctx, throwable ->
  if (throwable !is ProcessCanceledException) {
    val coroutineName = ctx[CoroutineName]?.name
    if (coroutineName != null) {
      LOG.error(coroutineName, throwable)
    }
    else {
      LOG.error(throwable)
    }
  }
}

/**
 * Creates a [Job] tied to the lifecycle of a [Disposable].
 */
@Suppress("FunctionName") // mirroring upstream API.
fun SupervisorJob(disposable: Disposable): Job {
  return SupervisorJob().also { job ->
    Disposer.register(
      disposable,
      Disposable {
        if (!job.isCancelled) {
          job.cancel(CancellationException("$disposable has been disposed."))
        }
      }
    )
  }
}

/**
 * Returns a [CoroutineScope] containing:
 *   - a [Job] tied to the [Disposable] lifecycle of this object
 *   - [AndroidDispatchers.workerThread]
 *   - a [CoroutineExceptionHandler] that logs unhandled exception at `ERROR` level.
 */
@Suppress("FunctionName") // Mirroring coroutines API, with many functions that look like constructors.
fun AndroidCoroutineScope(disposable: Disposable, context: CoroutineContext = EmptyCoroutineContext): CoroutineScope {
  return CoroutineScope(SupervisorJob(disposable) + AndroidDispatchers.workerThread + androidCoroutineExceptionHandler + context)
}

/**
 * Launches a new coroutine that will be bound to the given [ProgressIndicatorEx]. If the indicator is stopped, the coroutine will be
 * cancelled. If the coroutine finishes or is cancelled, the indicator will also be stopped.
 * This method also accepts an optional [CoroutineContext].
 */
fun CoroutineScope.launchWithProgress(
  progressIndicator: ProgressIndicatorEx,
  context: CoroutineContext = EmptyCoroutineContext,
  runnable: suspend CoroutineScope.() -> Unit): Job {
  if (!progressIndicator.isRunning) progressIndicator.start()

  // We create a new scope that we will cancel if the progressIndicator is stopped.
  val scope = createChildScope()

  /**
   * Checks if [progressIndicator] is cancelled and cancels the scope. Returns true as long as the scope is still active.
   */
  fun checkProgressIndicatorState(): Boolean {
    if (progressIndicator.isCanceled) {
      scope.cancel("User cancelled the refresh")
    }
    else if (!progressIndicator.isRunning) {
      scope.cancel("The progress indicator is not running")
    }

    return scope.isActive
  }

  scope.launch(workerThread) {
    while (checkProgressIndicatorState()) { delay(500) }
  }

  return scope.launch(context = scope.coroutineContext + context, block = runnable).apply {
    invokeOnCompletion {
      // The coroutine completed so, if needed, we stop the indicator.
      if (progressIndicator.isRunning) {
        progressIndicator.stop()
        progressIndicator.processFinish()
      }
    }
  }
}

/**
 * Mixin interface for IDE components aware of Android conventions for using Kotlin coroutines.
 *
 * To properly use coroutines, the component needs to meet these requirements:
 * - Be [Disposable]. Disposing the component cancels all coroutines created in the scope of this object.
 * - Implement [UserDataHolderEx], so the single [Job] tied to the [Disposable] lifecycle can be stored and reused. This can be done by
 *   delegating to a fresh instance of [UserDataHolderBase], e.g. `class Foo : UserDataHolder by UserDataHolderBase(), ...`
 *
 * Alternatively, an IDE component may use [AndroidCoroutineScope] to store the scope in an explicit field.
 */
interface AndroidCoroutinesAware : UserDataHolderEx, Disposable, CoroutineScope {

  companion object {
    private val CONTEXT: Key<CoroutineContext> = Key.create(::CONTEXT.qualifiedName)
  }

  /** @see AndroidCoroutineScope */
  override val coroutineContext: CoroutineContext
    get() {
      return getUserData(CONTEXT) ?: putUserDataIfAbsent(CONTEXT, AndroidCoroutineScope(this).coroutineContext)
    }
}

private val PROJECT_SCOPE: Key<CoroutineScope> = Key.create(::PROJECT_SCOPE.qualifiedName)

val Project.coroutineScope: CoroutineScope
  get() = getUserData(PROJECT_SCOPE) ?: (this as UserDataHolderEx).putUserDataIfAbsent(PROJECT_SCOPE, AndroidCoroutineScope(this))

/**
 * A coroutine-based launcher that ensures that at most one task is running at any point in time. It cancels the previous task if a new is
 * enqueued.
 */
class UniqueTaskCoroutineLauncher(private val coroutineScope: CoroutineScope, description: String) {
  // This mutex makes sure that the previous job is cancelled before a new one is started. This prevents several jobs to be executed at the
  // same time meaning that several tasks also cannot be executed at the same time and therefore we do not need a mutex on a task execution
  // itself.
  private val jobMutex = Mutex()

  private val taskDispatcher = AppExecutorUtil.createBoundedApplicationPoolExecutor(description, 1).asCoroutineDispatcher()

  private var taskJob: Job? = null

  /**
   * Returns a coroutine [Job] that wraps the task to be executed. If the task was overridden by the next task during scheduling the
   * returned [Job] is null.
   */
  suspend fun launch(task: suspend () -> Unit): Job? {
    taskJob?.cancel()
    var newJob: Job? = null
    coroutineScope.launch(taskDispatcher) {
      jobMutex.withLock {
        taskJob?.join()
        newJob = launch(taskDispatcher) {
          task()
        }
        taskJob = newJob
      }
    }.join()
    return newJob
  }
}

/**
 * Utility function for quickly creating a scope that is a child of the current scope. It can be optionally a supervisor scope.
 */
fun CoroutineScope.createChildScope(isSupervisor: Boolean = false): CoroutineScope = CoroutineScope(
  this.coroutineContext + if (isSupervisor) SupervisorJob(this.coroutineContext[Job]) else Job(this.coroutineContext[Job]))

/**
 * Immediately returns the completed result. If deferred is not complete for any reason, return null.
 */
suspend fun <T> Deferred<T>.getCompletedOrNull(): T? {
  if (isCompleted) {
    try {
      return this.await()
    }
    catch (t: Throwable) {
      return null
    }
  }
  return null
}

/**
 * Suspendable method that will suspend until the [project] is in smart mode and can get hold of the read lock. Once both conditions
 * are true, this method will execute [compute].
 * @see [com.intellij.openapi.application.smartReadAction]. This method is equivalent and will be replaced by it once is out of experimental.
 */
// TODO(b/190691270): Migrate to com.intellij.openapi.application.smartReadAction once is not experimental
suspend fun <T> runInSmartReadAction(project: Project, compute: Computable<T>): T = coroutineScope {
  val result = CompletableDeferred<T>()
  while (!result.isCompleted && isActive) {
    val waitingForSmart = CompletableDeferred<Boolean>()
    runReadAction {
      val dumbService = DumbService.getInstance(project)
      if (dumbService.isDumb) {
        dumbService.runWhenSmart {
          waitingForSmart.complete(true)
        }
        return@runReadAction
      }
      waitingForSmart.complete(true)
      result.complete(compute.compute())
      return@runReadAction
    }
    // We could not run in this loop, wait until we are in smart mode
    waitingForSmart.await()
  }
  return@coroutineScope result.await()
}

/**
 * Suspendable method that will suspend until it can get obtain the read lock. Once the read lock is obtained, it will execute [compute].
 * @see [com.intellij.openapi.application.readAction]. This method is equivalent and will be replaced by it once is out of experimental.
 */
// TODO(b/190691270): Migrate to com.intellij.openapi.application.readAction once is not experimental
suspend fun <T> runReadAction(compute: Computable<T>): T = coroutineScope {
  while (isActive) {
    try {
      return@coroutineScope ApplicationUtil.tryRunReadAction {
        return@tryRunReadAction compute.compute()
      }
    }
    catch (_: CannotRunReadActionException) {
      // Wait until the current write finishes
      val writeFinished = CompletableDeferred<Boolean>()
      ApplicationManager.getApplication().invokeLater { writeFinished.complete(true) }
      // This will suspend the coroutine until the write lock has finished
      writeFinished.await()
    }
  }
  throw CancellationException()
}

/**
 * Suspendable method that will suspend until the given [compute] can be executed in a write action in the UI thread.
 */
suspend fun <T> runWriteActionAndWait(compute: Computable<T>): T = coroutineScope {
  val result = CompletableDeferred<T>()
  ApplicationManager.getApplication().invokeLater {
    if (isActive) {
      WriteAction.run<Throwable> {
        if (isActive) result.complete(compute.compute())
      }
    }
  }
  return@coroutineScope result.await()
}

/**
 * Similar to [AndroidPsiUtils#getPsiFileSafely] but using a suspendable function.
 */
suspend fun getPsiFileSafely(project: Project, virtualFile: VirtualFile): PsiFile? = runReadAction {
  if (project.isDisposed) return@runReadAction null
  val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@runReadAction null
  return@runReadAction if (psiFile.isValid) psiFile else null
}

/**
 * Scope passed to the runnable in [disposableCallbackFlow].
 */
interface CallbackFlowWithDisposableScope<T>: CoroutineScope {
  /**
   * This disposable will be disposed if the [CoroutineScope] is cancelled or if the optional `parentDisposable` in
   * [disposableCallbackFlow] is disposed.
   */
  val disposable: Disposable

  /**
   * Equivalent to [kotlinx.coroutines.channels.ProducerScope.trySend].
   */
  fun trySend(e: T)
}

/**
 * Similar to [callbackFlow] but allows to use a [Disposable] as part of the callback.
 *
 * The [runnable] will be called with a [CallbackFlowWithDisposableScope] that contains a [Disposable]. The [Disposable] will be disposed if:
 *  - The [parentDisposable] is disposed, if not null.
 *  - The flow is closed.
 *
 * This allow for any callbacks to use that [Disposable] and dispose the listeners when the flow is not needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> disposableCallbackFlow(debugName: String, logger: Logger? = null, parentDisposable: Disposable? = null, runnable: CallbackFlowWithDisposableScope<T>.() -> Unit) = callbackFlow {
  logger?.debug("$debugName start")

  val disposable = parentDisposable?.let {
    // If there is a parent disposable, cancel the flow when it's disposed.
    Disposer.register(it) { cancel("parentDisposable was disposed") }
    Disposer.newDisposable(it, debugName)
  } ?: Disposer.newDisposable(debugName)

  val scope = object : CallbackFlowWithDisposableScope<T> {
    override val coroutineContext: CoroutineContext
      get() = this@callbackFlow.coroutineContext
    override val disposable: Disposable
      get() = disposable

    override fun trySend(e: T) {
      this@callbackFlow.trySend(e)
    }
  }

  scope.runnable()

  awaitClose {
    logger?.debug("$debugName shutdown")
    Disposer.dispose(disposable)
  }
}

/**
 * A [callbackFlow] that produces an element when the [project] moves into smart mode. The [onConnected] listener will be
 * called in the context of a worker thread.
 */
@VisibleForTesting
fun smartModeFlow(project: Project, parentDisposable: Disposable, logger: Logger?, onConnected: (() -> Unit)?): Flow<Unit> =
  disposableCallbackFlow("SmartModeFlow", logger, parentDisposable) {
  project.messageBus.connect(disposable).subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
    override fun exitDumbMode() {
      trySend(Unit)
    }
  })

  onConnected?.let { launch(workerThread) { it() } }
}

/**
 * A [callbackFlow] that produces an element when the [project] moves into smart mode.
 */
fun smartModeFlow(project: Project, parentDisposable: Disposable, logger: Logger? = null): Flow<Unit> =
  smartModeFlow(project, parentDisposable, logger, null)