# ReactiveModel

Класс `ReactiveModel` предоставляет контекст для асинхронного запуска задач (jobs) с возможностью наблюдения за
процессом их выполнения: состоянием, успешными результатами, ошибками.

Нацелен на использование в декларативно-реактивной манере в процессе реализации логики доменного уровня.

## Общее описание архитектурного подхода

Доменная логика реализуется с помощью выполнения набора задач (job) в асинхронном окружении (coroutines).
Каждая job рассматривается как повторяемая (например `fetchUsers`) а также "наблюдаемая" — к примеру UI-слой
хочет получать ошибки, результаты выполнения, изменения состояния активности job. В условиях декларативного подхода
это реализуется с помощью "реактивности", то есть, при использовании `kotlinx.coroutines`, с помощью `Flow`.

Ниже для обзора приведён пример частичной реализации фичи.
Далее будут разобраны используемые в примере функции и указаны более интересные/гибкие вариации их использования.

```kotlin
class UserListModel : ReactiveModel {
  val fetch = task { sortConfiguration: SortConfiguration ->
    userNetworkApi.fetch(sortConfiguration)
  }

  val uploadPhoto = task { photo ->
    userNetworkApi.uploadPhoto(photo)
  }

  fun deleteLocalPhoto(userId: UserId) {
    scope.launch {
      repository.deletePhotoFile(userId)
    }
  }
}

fun main(val model: UserListModel) {
  model.start()
  coroutineScope {
    launch {
      model.fetch.jobFlow.state.collect { println("state: $it") }
    }
    launch {
      model.fetch.jobFlow.errors().collect { println("error: $it") }
    }
    launch {
      model.fetch.jobFlow.results().collect { println("result: $it") }
    }
    launch {
      model.fetch.start(SortConfiguration.Ascending)
    }
  }
  model.dispose()
}
```

## Работа с `Task`, `WatchContext` и `JobFlow`

В `ReactiveModel` используется "jobs", которые представлены `suspend`-функциями, а также `WatchContext`, которые
предоставляют функционал запуска job-ов в специальном скоупе, который следит за процессом их выполнения, и рапортует
подписчикам об изменении хода выполнения через `WatchContext.state`, `WatchContext.successResults`, `WatchContext.errors`.
Каждое из этих свойств представляет собой `kotlinx.coroutines.flow.Flow`.

Класс `WatchContext` предназначен для использования внутри `ReactiveModel`, для наблюдателей из внешнего мира
рекомендуется возвращать `JobFlow`, который "прячет" внутренности `WatchContext` и оставляет клиенту
только необходимые свойства: `state`, `results`, `errors`.

Наконец, для удобства клиентов, есть структура `Task`, которая упаковывает вместе функцию старта job-а, а также
`WatchContext` для наблюдения за ходом выполнения.

Рассмотрим самый простой пример, когда у нас есть одна самодостаточная операция.

```kotlin
class UserListModel : ReactiveModel {

  val fetch = task { sortConfiguration: SortConfiguration ->
    userNetworkApi.fetch(sortConfiguration)
  }

}
```

Здесь хелпер-функция `task` создаёт и конфигурирует объект `Task`, при вызове функции `start` которого, переданная
suspend-лямбда будет запущена в рамках нового `WatchContext` и будет в нём наблюдаться.

К сведению: код выше равносилен такому

```kotlin
  val fetch = taskIn(WatchContext()) { sortConfiguration: SortConfiguration ->
    userNetworkApi.fetch(sortConfiguration)
  }
```

### Использование одного `WatchContext` для разных job

В примере с `UserListModel` выше, каждому таску соответствовал один `WatchContext`. Но бывает логика посложнее.

Например, представим, что функционал отображения списка пользователей содержит операции

- изначального получения пользователей
- изменения сортировки
- удаления пользователя

При этом **все** эти операции отсылают разные запросы на сервер, но требования таковы, что для клиента данной фичи
это всё относится к одному "контексту": обновлению списка пользователей. Например, это выражается в показе одного
и того же лоадера в UI.

В этом случае можно завести один "контекст" и запускать в нём все три job-а. Клиент будет получать уведомления
о процессе работы любого из них через _один_ объект `JobFlow`:

```kotlin
class UserListModel : ReactiveModel {
  // клиент использует данный объект для наблюдения
  // за состоянием выполнения job-ов в этом контексте
  val fetchJobFlow: JobFlow<Unit> = WatchContext()

  fun fetch() {
    fetchJobFlow.executeInModelScope {
      userNetworkApi.fetch()
    }
  }

  fun setSortConfiguration(configuration: SortConfiguration) {
    fetchJobFlow.executeInModelScope {
      userNetworkApi.fetchWithSortConfiguration(configuration)
    }
  }

  fun deleteUser() {
    fetchJobFlow.executeInModelScope {
      userNetworkApi.deleteUser()
    }
  }
}
```

## Работа со state

Базовый вариант `ReactiveModel` не диктует никаких обязательств по работе с внутренним стейтом. Однако, как правило,
его удобно реализовывать через `MutableStateFlow`, например:

```kotlin
class LoginModel : ReactiveModel {

  private data class State(val otpId: String? = null)
  private val stateFlow = MutableStateFlow(State())

  val login = task { ->
    val otpId = loginApi.login()
    stateFlow.update { state -> state.copy(otpId = otpId) }
  }

  val otpIdChanges: Flow<OtpId> = stateFlow.map { it.otpId }.filterNotNull()
}
```

## Работа с `CoroutineScope`

Каждый наследник `ReactiveModel` может запускать корутины в scope модели:

```kotlin
class LoginModel : ReactiveModel {
  fun deleteAuthToken() {
    scope.launch { authRepository.deleteTokens() }
  }
}
```

## Запуск задач друг за другом

Если нужно организовать последовательный запуск нескольких задач, можно сделать это просто вызывая `start`/`execute`,
например:

```kotlin
  val fetchProfile = task { repository.fetch() }

  val login = task {
    val result = repository.login()
    fetchProfile.start(result)
  }
 ```

или, если используются `WatchContext`:

```kotlin
  val fetchProfileContext = WatchContext()

  val login = task {
    val result = repository.login()
    fetchProfileContext.executeInModelScope { fetchProfileInternal(result) }
  }
 ```

## Постановка в очередь

Бывают ситуации, когда одну и ту же задачу в рамках одного `WatchContext` нужно ставить в очередь, если какая-то
сейчас уже выполняется:

```kotlin
val updatePushPreferenceContext = WatchContext()

fun updatePushPreference(config: Config) {
  updatePushPreferenceContext.executeInModelScope { repository.update(config) }
}

// in UI

// due to fast user clicks
model.updatePushPreference(Config.Enabled)
model.updatePushPreference(Config.Disabled)
model.updatePushPreference(Config.Enabled)
```

**TODO** эта фича пока не реализована. Скорее всего, будет некий флаг enableQueueing при отсылке job-ы в контекст

## Lifecycle

У каждой `ReactiveModel` есть жизненный цикл. Он начинается с вызова функции `start`, которая возвращает `Job`,
вызвав `cancel()` у которого можно завершить работу модели. Либо, если был передан `parentScope`, можно вызывать
`cancel` у него.
