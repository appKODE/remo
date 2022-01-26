# Changelog

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
