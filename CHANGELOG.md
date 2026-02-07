# Changelog

## 1.3.0 - 2026-02-07
* Update dependencies: kotlin (2.2.21), dokka (2.1.0), kotlinResult (2.1.0), turbine (1.2.1)
* Migrate to VanniktechMavenPublish plugin for publications
* Update Gradle to 9.3.0

## 1.2.0 - 2024-03-01
* Add `QueueingStrategy` parameter which lets the user configure behaviour of the `ReactiveModel` when `WatchContext` is already executing a task. The only available option (apart from throwing) for now is the ability to skip the new task
* Improve documentation
* Remove `task0`. It wasn't used and is actually a syntactic sugar

## 1.1.1 - 2022-11-27
* Исправлена ошибка с использованием `scheduled`-параметра в Task1/Task2

## 1.1.0 - 2022-10-11
* Добавлена возможность стартовать Task/JobFlow "лениво": после появления подписчиков на `Flow` результатов и/или состояния jobFlow:

```kotlin
model.fetchUsers.start(
  scheduled = StartScheduled.Lazily(minResultsSubscribers = 1, minStateSubscribers = 0)
)
delay(2000)
// this will correctly print results while with `StartScheduled.Eagerly` 
// they will be emitted early and won't be ever replayed
model.fetchUsers.results(replayLast = false).collect { println(it) }
```

## 1.0.8 - 2022-03-29
* Добавлена возможность отменять Task

## 1.0.7 - 2022-02-21
* Добавлены targets для M1: iosSimulatorArm64, macosArm64, macosX64

## 1.0.6 - 2022-02-07
* Убрана функция `Task.startAndAwait`. В ней был race condition, из-за которого она работала некорректно.
  С учётом того, что она нигде не испльзовалась, безопасно её убрать. Если понадобится в будущем, надо 
  будет продумать иной подход
* `JobFlow.state` теперь возвращает `Flow` вместо `StateFlow`. Последний накладывал излишние ограничения
  на имплементации.

## 1.0.5 - 2022-01-21
* Функция `mapResults` переименована в `mapSuccessResults`, в соответствии с тем что она делает
* Лямбда-аргументы `mapSuccessResults` и `mapErrors` получили модификатор `suspend`

## 1.0.4 - 2022-01-21
* `successResults`/`errorResults` теперь имеют умолчальное значение для `replayLast` (было забыто)

## 1.0.3 - 2022-01-18

* `successResults` по умолчанию теперь имеют `replayLast=false`
* Поддержка маппинга ошибок: в `ReactiveModel` и в конструкторах тасков/job-ов
* Добавлены `JobFlow.mapResults()`, `JobFlow.mapErrors()`

## 1.0.2 - 2022-01-13

* Добавлен метод `start` с поддержкой `parentScope`
* Исправлен баг с эмиттом ошибок при `replayLast=false`
