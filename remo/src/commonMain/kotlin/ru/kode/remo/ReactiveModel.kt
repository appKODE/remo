/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.remo

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlin.native.concurrent.ThreadLocal

/**
 * Данный класс предоставляет контекст для асинхронного запуска задач (jobs) с возможностью наблюдения за
 * процессом их выполнения: состоянием, успешными результатами, ошибками.
 *
 * Нацелен на использование в декларативно-реактивной манере в процессе реализации логики доменного уровня.
 *
 * Для подробного описания возможностей с примерами использования смотри файл
 * [README](https://git.appkode.ru/mobile-android/remo/-/blob/master/README.md).
 */
public open class ReactiveModel(
  /**
   * Dispatcher, который по умолчанию используется для выполнения Job/Task
   */
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
  /**
   * Если указан, будет использоваться для маппинга ошибок **всех тасков**
   * перед их emit-ом в стримы результатов/ошибок.
   *
   * Можно также указывать `errorMapper` для [Task] и [WatchContext] при их создании, они будут приоритетнее, чем
   * данный глобальный маппер
   */
  private val errorMapper: ((Throwable) -> Throwable)? = null,
) {
  private val _uncaughtExceptions = MutableSharedFlow<Throwable>(replay = 1, extraBufferCapacity = 10)

  /**
   * Поток неотловленных ошибок, которые произошли вне запуска Job/Task.
   * Например, при выполнении `scope.launch()`.
   *
   * При подписке будет испущена последняя полученная ошибка, если таковая имеется.
   *
   * Обратите внимание, что все ошибки в рамках запуска тасков или работы Job в [WatchContext] не попадут сюда,
   * они будут своевременно отловлены и запущены в [JobFlow.errors].
   *
   * Обычно стоит подписаться на этот поток и как минимум отображать в логах поступающие сюда ошибки.
   */
  public val uncaughtExceptions: Flow<Throwable> = _uncaughtExceptions
  private val handler = CoroutineExceptionHandler { _, e ->
    if (!_uncaughtExceptions.tryEmit(e)) {
      println("failed to emit an uncaught error, printing to stdout")
      e.printStackTrace()
    }
  }

  private var _scope: CoroutineScope? = null
  protected val scope: CoroutineScope
    get() {
      return _scope ?: error("scope not available. Possible reason: start() was not called")
    }
  protected val scopeSafe: CoroutineScope? get() = _scope

  /**
   * Выполняет старт и инициализацию. Возвращает `Job`, вызвав `cancel()` у которого можно завершить работу модели.
   * Если был передан [parentScope], то внутренний CoroutineScope модели станет дочерним
   * по отношению к [parentScope] и `parentScope.cancel()` соответственно завершит работу всей модели.
   */
  public fun start(parentScope: CoroutineScope? = null): Job {
    if (_scope?.isActive == true) {
      error("model \"${this::class.simpleName}\" is already started")
    }
    _scope = CoroutineScope(SupervisorJob(parentScope?.coroutineContext?.job) + handler + dispatcher)
    onPostStart()
    return _scope!!.coroutineContext[Job] ?: error("no job in model scope")
  }

  /**
   * Будет вызвана сразу после успешного [start] модели.
   * В этой функции уже можно использовать [scope] и стартовать корутины, job-ы.
   */
  protected open fun onPostStart(): Unit = Unit

  /**
   * Запускает [body] внутри [scope] модели
   */
  protected fun <R> WatchContext<R>.executeInModelScope(scheduled: StartScheduled, body: suspend () -> R): Job {
    return this.executeIn(scope, scheduled, body)
  }

  /**
   * Создаёт [Task] без входных параметров. При вызове [Task0.start], переданный [body] будет выполнен в [context]
   */
  protected fun <R> taskIn(
    context: WatchContext<R>,
    body: suspend () -> R,
  ): Task0<R> {
    return object : Task0<R> {
      override fun start(scheduled: StartScheduled): Job {
        return context.executeInModelScope(scheduled, body)
      }

      override val jobFlow: JobFlow<R> = context
    }
  }

  /**
   * Создаёт [Task] с одним входным параметром. При вызове [Task1.start], переданный [body] будет выполнен в [context]
   */
  protected fun <P1, R> taskIn(
    context: WatchContext<R>,
    body: suspend (P1) -> R,
  ): Task1<P1, R> {
    return Task1(
      jobFlow = context,
      start = { p1 ->
        context.executeInModelScope(scheduled = StartScheduled.Eagerly) { body(p1) }
      }
    )
  }

  /**
   * Создаёт [Task] с двумя входными параметрами. При вызове [Task2.start], переданный [body] будет выполнен в [context]
   */
  protected fun <P1, P2, R> taskIn(
    context: WatchContext<R>,
    body: suspend (P1, P2) -> R,
  ): Task2<P1, P2, R> {
    return Task2(
      start = { p1, p2 ->
        context.executeInModelScope(scheduled = StartScheduled.Eagerly) { body(p1, p2) }
      },
      jobFlow = context
    )
  }

  /**
   * Создаёт [Task] с тремя входными параметрами. При вызове [Task3.start], переданный [body] будет выполнен в [context]
   */
  protected fun <P1, P2, P3, R> taskIn(
    context: WatchContext<R>,
    body: suspend (P1, P2, P3) -> R,
  ): Task3<P1, P2, P3, R> {
    return Task3(
      start = { p1, p2, p3 ->
        context.executeInModelScope(scheduled = StartScheduled.Eagerly) { body(p1, p2, p3) }
      },
      jobFlow = context
    )
  }

  /**
   * Создаёт [Task] без входных параметров. При вызове [Task0.start], переданный [body] будет выполнен в [context].
   *
   * Это хелпер функция, являющаяся копией [taskIn] с 0-аргументами, добавленная
   * из-за синтаксического перфекционизма, потому что котлин не умеет различать overloads `() -> R` и `(P) -> R`.
   *
   * Варианты равнозначны:
   *
   * ```
   * val fetch = taskIn(WorkContext()) { ->
   * }
   *
   * val fetch = task<Unit>(WorkContext()) {
   * }
   *
   * val fetch = task0In(WorkContext()) {
   * }
   * ```
   */
  protected fun <R> task0In(
    context: WatchContext<R>,
    body: suspend () -> R,
  ): Task0<R> {
    return object : Task0<R> {
      override fun start(scheduled: StartScheduled): Job {
        return context.executeInModelScope(scheduled, body)
      }

      override val jobFlow: JobFlow<R> = context

    }
  }

  /**
   * Создаёт [Task] без входных параметров. При вызове [Task0.start], переданный [body] будет выполнен в
   * заранее созданном [WatchContext] с именем [name]
   *
   * @param name имя задачи, описательное
   * @param errorMapper Если указан, будет использоваться для маппинга ошибок перед
   * их emit-ом в стримы результатов/ошибок. Является более приоритетным, чем "глобальный" маппер, указанный в
   * конструкторе [ReactiveModel]
   * @param body выполняемое тело задачи
   */
  protected fun <R> task(
    name: String = createTaskName(),
    errorMapper: ((Throwable) -> Throwable)? = null,
    body: suspend () -> R,
  ): Task0<R> {
    return taskIn(WatchContext(name, errorMapper ?: this.errorMapper), body)
  }

  /**
   * Создаёт [Task] с одним входным параметром. При вызове [Task1.start], переданный [body] будет выполнен в
   * заранее созданном [WatchContext] с именем [name]
   *
   * @param name имя задачи, описательное
   * @param errorMapper Если указан, будет использоваться для маппинга ошибок перед
   * их emit-ом в стримы результатов/ошибок. Является более приоритетным, чем "глобальный" маппер, указанный в
   * конструкторе [ReactiveModel]
   * @param body выполняемое тело задачи
   */
  protected fun <P1, R> task(
    name: String = createTaskName(),
    errorMapper: ((Throwable) -> Throwable)? = null,
    body: suspend (P1) -> R,
  ): Task1<P1, R> {
    return taskIn(WatchContext(name, errorMapper ?: this.errorMapper)) { p1 -> body(p1) }
  }

  /**
   * Создаёт [Task] с двумя входными параметрами. При вызове [Task2.start], переданный [body] будет выполнен в
   * заранее созданном [WatchContext] с именем [name]
   *
   * @param name имя задачи, описательное
   * @param errorMapper Если указан, будет использоваться для маппинга ошибок перед
   * их emit-ом в стримы результатов/ошибок. Является более приоритетным, чем "глобальный" маппер, указанный в
   * конструкторе [ReactiveModel]
   * @param body выполняемое тело задачи
   */
  protected fun <P1, P2, R> task(
    name: String = createTaskName(),
    errorMapper: ((Throwable) -> Throwable)? = null,
    body: suspend (P1, P2) -> R,
  ): Task2<P1, P2, R> {
    return taskIn(WatchContext(name, errorMapper ?: this.errorMapper)) { p1, p2 -> body(p1, p2) }
  }

  /**
   * Создаёт [Task] с тремя входными параметрами. При вызове [Task2.start], переданный [body] будет выполнен в
   * заранее созданном [WatchContext] с именем [name]
   *
   * @param name имя задачи, описательное
   * @param errorMapper Если указан, будет использоваться для маппинга ошибок перед
   * их emit-ом в стримы результатов/ошибок. Является более приоритетным, чем "глобальный" маппер, указанный в
   * конструкторе [ReactiveModel]
   * @param body выполняемое тело задачи
   */
  protected fun <P1, P2, P3, R> task(
    name: String = createTaskName(),
    errorMapper: ((Throwable) -> Throwable)? = null,
    body: suspend (P1, P2, P3) -> R,
  ): Task3<P1, P2, P3, R> {
    return taskIn(WatchContext(name, errorMapper ?: this.errorMapper)) { p1, p2, p3 -> body(p1, p2, p3) }
  }

  /**
   * Создаёт [Task] без входных параметров. При вызове [Task0.start], переданный [body] будет выполнен в
   * заранее созданном [WatchContext] с именем [name]
   *
   * Это хелпер функция, являющаяся копией [task] с 0-аргументами, добавленная
   * из-за синтаксического перфекционизма, потому что котлин не умеет различать overloads `() -> R` и `(P) -> R`.
   *
   * Варианты равнозначны:
   *
   * ```
   * val fetch = task { ->
   * }
   *
   * val fetch = task<Unit> {
   * }
   *
   * val fetch = task0 {
   * }
   * ```
   *
   * @param name имя задачи, описательное
   * @param errorMapper Если указан, будет использоваться для маппинга ошибок перед
   * их emit-ом в стримы результатов/ошибок. Является более приоритетным, чем "глобальный" маппер, указанный в
   * конструкторе [ReactiveModel]
   * @param body выполняемое тело задачи
   */
  protected fun <R> task0(
    name: String = createTaskName(),
    errorMapper: ((Throwable) -> Throwable)? = null,
    body: suspend () -> R,
  ): Task0<R> {
    return taskIn(WatchContext(name, errorMapper ?: this.errorMapper), body)
  }

  public companion object {
    internal fun createTaskName(): String {
      return "task#$NEXT_TASK_ID".also { NEXT_TASK_ID += 1 }
    }

    internal fun createWatchContextName(): String {
      return "watch_context#$NEXT_WATCH_CONTEXT_ID".also { NEXT_WATCH_CONTEXT_ID += 1 }
    }
  }
}

@ThreadLocal
private var NEXT_TASK_ID: Long = 0
@ThreadLocal
private var NEXT_WATCH_CONTEXT_ID: Long = 0
