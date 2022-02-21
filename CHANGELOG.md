# Changelog

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
