package ru.kode.remo

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Задача, выполняемая в рамках [ReactiveModel]
 */
public interface Task<R> {
  public val jobFlow: JobFlow<R>
}

/**
 * Задача без входных параметров, выполняемая в рамках [ReactiveModel].
 * Функция [start] используется для запуска/повторного запуска данной задачи.
 * За ходом её выполнения можно следить используя поля объекта [jobFlow]
 */
public class Task0<R>(
  override val jobFlow: JobFlow<R>,
  public val start: () -> Unit,
) : Task<R>

/**
 * Задача с одним входным параметром, выполняемая в рамках [ReactiveModel].
 * Функция [start] используется для запуска/повторного запуска данной задачи.
 * За ходом её выполнения можно следить используя поля объекта [jobFlow]
 */
public class Task1<in P1, R>(
  override val jobFlow: JobFlow<R>,
  public val start: (P1) -> Unit,
) : Task<R>

/**
 * Задача с двумя входными параметрами , выполняемая в рамках [ReactiveModel].
 * Функция [start] используется для запуска/повторного запуска данной задачи.
 * За ходом её выполнения можно следить используя поля объекта [jobFlow]
 */
public class Task2<in P1, in P2, R>(
  override val jobFlow: JobFlow<R>,
  public val start: (P1, P2) -> Unit,
) : Task<R>

/**
 * Задача с тремя входными параметрами , выполняемая в рамках [ReactiveModel].
 * Функция [start] используется для запуска/повторного запуска данной задачи.
 * За ходом её выполнения можно следить используя поля объекта [jobFlow]
 */
public class Task3<in P1, in P2, in P3, R>(
  override val jobFlow: JobFlow<R>,
  public val start: (P1, P2, P3) -> Unit,
) : Task<R>

/**
 * Запускает задачу и "suspend"-ится пока она не завершится успешно или с ошибкой, возвращает соответствующий [Result]
 */
public suspend fun <R> Task0<R>.startAndAwait(): Result<R, Throwable> {
  this.start()
  return combine(
    this.jobFlow.successResults(replayLast = false).onStart<R?> { emit(null) },
    this.jobFlow.errors(replayLast = false).onStart<Throwable?> { emit(null) },
    transform = { result, error -> result to error },
  ).firstResult()
}

/**
 * Запускает задачу и "suspend"-ится пока она не завершится успешно или с ошибкой, возвращает соответствующий [Result]
 */
public suspend fun <P, R> Task1<P, R>.startAndAwait(parameter: P): Result<R, Throwable> {
  this.start(parameter)
  return combine(
    this.jobFlow.successResults(replayLast = false).onStart<R?> { emit(null) },
    this.jobFlow.errors(replayLast = false).onStart<Throwable?> { emit(null) },
    transform = { result, error -> result to error },
  ).firstResult()
}

/**
 * Запускает задачу и "suspend"-ится пока она не завершится успешно или с ошибкой, возвращает соответствующий [Result]
 */
public suspend fun <P1, P2, R> Task2<P1, P2, R>.startAndAwait(parameter1: P1, parameter2: P2): Result<R, Throwable> {
  this.start(parameter1, parameter2)
  return combine(
    this.jobFlow.successResults(replayLast = false).onStart<R?> { emit(null) },
    this.jobFlow.errors(replayLast = false).onStart<Throwable?> { emit(null) },
    transform = { result, error -> result to error },
  ).firstResult()
}

/**
 * Запускает задачу и "suspend"-ится пока она не завершится успешно или с ошибкой, возвращает соответствующий [Result]
 */
public suspend fun <P1, P2, P3, R> Task3<P1, P2, P3, R>.startAndAwait(
  parameter1: P1,
  parameter2: P2,
  parameter3: P3,
): Result<R, Throwable> {
  this.start(parameter1, parameter2, parameter3)
  return combine(
    this.jobFlow.successResults(replayLast = false).onStart<R?> { emit(null) },
    this.jobFlow.errors(replayLast = false).onStart<Throwable?> { emit(null) },
    transform = { result, error -> result to error },
  ).firstResult()
}

private suspend fun <R> Flow<Pair<R?, Throwable?>>.firstResult(): Result<R, Throwable> {
  return this
    .filter { it.first != null || it.second != null }
    .map { (result, error) ->
      when {
        result != null -> Ok(result)
        error != null -> Err(error)
        else -> error("unexpected: both result and error are not null")
      }
    }
    .first()
}
