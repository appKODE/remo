package ru.kode.remo

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Данный класс предоставляет контекст для асинхронного запуска задач (jobs) с возможностью наблюдения за
 * процессом их выполнения: состоянием, успешными результатами, ошибками.
 *
 * Нацелен на использование в декларативно-реактивной манере в процессе реализации логики доменного уровня.
 *
 * ## Общее описание архитектурного подхода
 *
 * Доменная логика реализуется с помощью выполнения набора задач (job) в асинхронном окружении (coroutines).
 * Каждая job рассматривается как повторяемая (например `fetchUsers`) а также "наблюдаемая" — к примеру UI-слой
 * хочет получать ошибки, результаты выполнения, изменения состояния активности job. В условиях декларативного подхода
 * это реализуется с помощью "реактивности", то есть, при использовании `kotlinx.coroutines`, с помощью `Flow`.
 *
 * Ниже для обзора приведён пример частичной реализации фичи.
 * Далее будут разобраны используемые в примере функции и указаны более интересные/гибкие вариации их использования.
 *
 * ```kotlin
 * class UserListModel : ReactiveModel {
 *   val fetch = task { sortConfiguration: SortConfiguration ->
 *     userNetworkApi.fetch(sortConfiguration)
 *   }
 *
 *   val uploadPhoto = task { photo ->
 *     userNetworkApi.uploadPhoto(photo)
 *   }
 *
 *   fun deleteLocalPhoto(userId: UserId) {
 *     scope.launch {
 *       repository.deletePhotoFile(userId)
 *     }
 *   }
 * }
 *
 * fun main(val model: UserListModel) {
 *   launch {
 *     model.fetch.jobFlow.state.collect { println("state: $it" }
 *     model.fetch.jobFlow.errors().collect { println("error: $it" }
 *     model.fetch.jobFlow.results().collect { println("result: $it" }
 *   }
 *   launch {
 *     model.fetch.start(SortConfiguration.Ascending)
 *   }.join()
 *   model.dispose()
 * }
 * ```
 *
 * ## Работа с `Task`, `WatchContext` и `JobFlow`
 *
 * В [ReactiveModel] используется "jobs", которые представлены `suspend`-функциями, а также [WatchContext], которые
 * предоставляют функционал запуска job-ов в специальном скоупе, который следит за процессом их выполнения, и рапортует
 * подписчикам об изменении хода выполнения через [WatchContext.state], [WatchContext.results], [WatchContext.errors].
 * Каждое из этих свойств представляет собой [kotlinx.coroutines.flow.Flow].
 *
 * Класс [WatchContext] предназначен для использования внутри [ReactiveModel], для наблюдателей из внешнего мира
 * рекомендуется возвращать [JobFlow], который "прячет" внутренности [WatchContext] и оставляет клиенту
 * только необходимые свойства: `state`, `results`, `errors`.
 *
 * Наконец, для удобства клиентов, есть структура [Task], которая упаковывает вместе функцию старта job-а, а также
 * [WatchContext] для наблюдения за ходом выполнения.
 *
 * Рассмотрим самый простой пример, когда у нас есть одна самодостаточная операция.
 *
 * ```kotlin
 * class UserListModel : ReactiveModel {
 *
 *   val fetch = task { sortConfiguration: SortConfiguration ->
 *     userNetworkApi.fetch(sortConfiguration)
 *   }
 *
 * }
 * ```
 *
 * Здесь хелпер-функция `task` создаёт и конфигурирует объект [Task], при вызове функции `start` которого, переданная
 * suspend-лямбда будет запущена в рамках нового [WatchContext] и будет в нём наблюдаться.
 *
 * К сведению: код выше равносилен такому
 *
 * ```kotlin
 *   val fetch = taskIn(WatchContext()) { sortConfiguration: SortConfiguration ->
 *     userNetworkApi.fetch(sortConfiguration)
 *   }
 * ```
 *
 * ### Использование одного `WatchContext` для разных job
 *
 * В примере с `UserListModel` выше, каждому таску соответствовал один [WatchContext]. Но бывает логика посложнее.
 *
 * Например, представим, что функционал отображения списка пользователей содержит операции
 *
 * - изначального получения пользователей
 * - изменения сортировки
 * - удаления пользователя
 *
 * При этом **все** эти операции отсылают разные запросы на сервер, но требования таковы, что для клиента данной фичи
 * это всё относится к одному "контексту": обновлению списка пользователей. Например, это выражается в показе одного
 * и того же лоадера в UI.
 *
 * В этом случае можно завести один "контекст" и запускать в нём все три job-а. Клиент будет получать уведомления
 * о процессе работы любого из них через _один_ объект [JobFlow]:
 *
 * ```kotlin
 * class UserListModel : ReactiveModel {
 *   // клиент использует данный объект для наблюдения
 *   // за состоянием выполнения job-ов в этом контексте
 *   val fetchJobFlow: JobFlow<Unit> = WatchContext()
 *
 *   fun fetch() {
 *     fetchJobFlow.executeInModelScope {
 *       userNetworkApi.fetch()
 *     }
 *   }
 *
 *   fun setSortConfiguration(configuration: SortConfiguration) {
 *     fetchJobFlow.executeInModelScope {
 *       userNetworkApi.fetchWithSortConfiguration(configuration)
 *     }
 *   }
 *
 *   fun deleteUser() {
 *     fetchJobFlow.executeInModelScope {
 *       userNetworkApi.deleteUser()
 *     }
 *   }
 * }
 * ```
 *
 * ## Работа со state
 *
 * Базовый вариант [ReactiveModel] не диктует никаких обязательств по работе с внутренним стейтом. Однако, как правило,
 * его удобно реализовывать через `MutableStateFlow`, например:
 *
 * ```kotlin
 * class LoginModel : ReactiveModel {
 *
 *   private data class State(val otpId: String? = null)
 *   private val stateFlow = MutableStateFlow(State())
 *
 *   val login = task { ->
 *     val otpId = loginApi.login()
 *     stateFlow.update { state -> state.copy(otpId = otpId) }
 *   }
 *
 *   val otpIdChanges: Flow<OtpId> = stateFlow.map { it.otpId }.filterNotNull()
 * }
 * ```
 *
 * ## Работа с `CoroutineScope`
 *
 * Каждый наследник [ReactiveModel] может запускать корутины в scope модели:
 *
 * ```kotlin
 * class LoginModel : ReactiveModel {
 *   fun deleteAuthToken() {
 *     scope.launch { authRepository.deleteTokens() }
 *   }
 * }
 * ```
 *
 * ## Запуск задач друг за другом
 *
 * Если нужно организовать последовательный запуск нескольких задач, можно сделать это просто вызывая `start`/`execute`,
 * например:
 *
 * ```kotlin
 *   val fetchProfile = task { repository.fetch() }
 *
 *   val login = task {
 *     val result = repository.login()
 *     fetchProfile.start(result)
 *   }
 *  ```
 *
 * или, если используются [WatchContext]:
 *
 * ```kotlin
 *   val fetchProfileContext = WatchContext()
 *
 *   val login = task {
 *     val result = repository.login()
 *     fetchProfileContext.executeInModelScope { fetchProfileInternal(result) }
 *   }
 *  ```
 *
 * ## Постановка в очередь
 *
 * Бывают ситуации, когда одну и ту же задачу в рамках одного [WatchContext] нужно ставить в очередь, если какая-то
 * сейчас уже выполняется:
 *
 * ```kotlin
 * val updatePushPreferenceContext = WatchContext()
 *
 * fun updatePushPreference(config: Config) {
 *   updatePushPreferenceContext.executeInModelScope { repository.update(config) }
 * }
 *
 * // in UI
 *
 * // due to fast user clicks
 * model.updatePushPreference(Config.Enabled)
 * model.updatePushPreference(Config.Disabled)
 * model.updatePushPreference(Config.Enabled)
 * ```
 *
 * **TODO** эта фича пока не реализована. Скорее всего, будет некий флаг enableQueueing при отсылке job-ы в контекст
 *
 * ## Lifecycle
 *
 * У каждой [ReactiveModel] есть жизненный цикл. Он начинается с создания модели, которое инициализирует
 * внутренний `CoroutineScope`. Чтобы корректно завершить работу модели и закрыть этот scope, освободить остальные
 * ресурсы, необходимо вызывать метод [ReactiveModel.dispose].
 *
 * ## Использование в не-декларативном окружении
 *
 * При необходимости использования данного класса в не-декларативных условиях, можно воспользоваться suspend-функцией
 * `startAndAwait()`, которая выполнит таск и дождется его результата.
 *
 * ```kotlin
 * fun main() {
 *   launch {
 *     val result = loginModel.login.startAndAwait()
 *     println("result is $result")
 *   }
 * }
 * ```
 */
public open class ReactiveModel(
  /**
   * Dispatcher, который по умолчанию используется для выполнения Job/Task
   */
  dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
  private val handler = CoroutineExceptionHandler { _, e -> e.printStackTrace() }
  protected val scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob() + handler)

  /**
   * Завершает работу и освобождает все ресурсы, отменяет все запущенные job-ы
   */
  public fun dispose() {
    scope.cancel()
  }

  /**
   * Запускает [body] внутри [scope] модели
   */
  protected fun <R> WatchContext<R>.executeInModelScope(body: suspend () -> R) {
    this.executeIn(scope, body)
  }

  /**
   * Создаёт [Task] без входных параметров. При вызове [Task0.start], переданный [body] будет выполнен в [context]
   */
  protected fun <R> taskIn(
    context: WatchContext<R>,
    body: suspend () -> R,
  ): Task0<R> {
    return Task0(
      jobFlow = context,
      start = { context.executeInModelScope(body) }
    )
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
        context.executeInModelScope { body(p1) }
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
        context.executeInModelScope { body(p1, p2) }
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
        context.executeInModelScope { body(p1, p2, p3) }
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
    return Task0(
      jobFlow = context,
      start = { context.executeInModelScope(body) }
    )
  }

  /**
   * Создаёт [Task] без входных параметров. При вызове [Task0.start], переданный [body] будет выполнен в
   * заранее созданном [WatchContext] с именем [name]
   */
  protected fun <R> task(
    name: String = createTaskName(),
    body: suspend () -> R,
  ): Task0<R> {
    return taskIn(WatchContext(name), body)
  }

  /**
   * Создаёт [Task] с одним входным параметром. При вызове [Task1.start], переданный [body] будет выполнен в
   * заранее созданном [WatchContext] с именем [name]
   */
  protected fun <P1, R> task(
    name: String = createTaskName(),
    body: suspend (P1) -> R,
  ): Task1<P1, R> {
    return taskIn(WatchContext(name)) { p1 -> body(p1) }
  }

  /**
   * Создаёт [Task] с двумя входными параметрами. При вызове [Task2.start], переданный [body] будет выполнен в
   * заранее созданном [WatchContext] с именем [name]
   */
  protected fun <P1, P2, R> task(
    name: String = createTaskName(),
    body: suspend (P1, P2) -> R,
  ): Task2<P1, P2, R> {
    return taskIn(WatchContext(name)) { p1, p2 -> body(p1, p2) }
  }

  /**
   * Создаёт [Task] с тремя входными параметрами. При вызове [Task2.start], переданный [body] будет выполнен в
   * заранее созданном [WatchContext] с именем [name]
   */
  protected fun <P1, P2, P3, R> task(
    name: String = createTaskName(),
    body: suspend (P1, P2, P3) -> R,
  ): Task3<P1, P2, P3, R> {
    return taskIn(WatchContext(name)) { p1, p2, p3 -> body(p1, p2, p3) }
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
   */
  protected fun <R> task0(
    name: String = createTaskName(),
    body: suspend () -> R,
  ): Task0<R> {
    return taskIn(WatchContext(name), body)
  }

  public companion object {
    private var NEXT_TASK_ID: Long = 0
    private var NEXT_WATCH_CONTEXT_ID: Long = 0

    internal fun createTaskName(): String {
      return "task#$NEXT_TASK_ID".also { NEXT_TASK_ID += 1 }
    }

    internal fun createWatchContextName(): String {
      return "watch_context#$NEXT_WATCH_CONTEXT_ID".also { NEXT_WATCH_CONTEXT_ID += 1 }
    }
  }
}
