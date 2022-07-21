/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.remo

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

/**
 * Предоставляет возможность наблюдения за процессом выполнения повторяемых задач [ReactiveModel]
 */
public interface JobFlow<R> {
  /**
   * Стрим состояний активности-неактивности. При подписке всегда отправляет последнее состояние.
   */
  public val state: Flow<JobState>

  /**
   * Стрим результатов выполнения задачи. Если [replayLast] будет
   * установлен в `false`, то будут приходить только результаты выполнения _после_ подписки,
   * иначе будет сразу же отправлен последний успешный результат (если таковой был)
   */
  public fun results(replayLast: Boolean = true): Flow<Result<R, Throwable>>
}

/**
 * Стрим успешных результатов успешного выполнения задачи. Если [replayLast] будет
 * установлен в `false`, то будут приходить только результаты выполнения _после_ подписки,
 * иначе будет сразу же отправлен последний успешный результат (если таковой был)
 *
 * Обратите внимание, что по умолчанию здесь [replayLast] установлен в `false`, потому что в подавляющем
 * большинстве случаев за успешным выполеннием следят уже после подписки, поэтому они
 * должны представлять собой "event", а не "state"
 */
public fun <R> JobFlow<R>.successResults(replayLast: Boolean = false): Flow<R> {
  return results(replayLast)
    .filterIsInstance<Ok<R>>()
    .map { it.get() ?: error("internal error: null result") }
}

/**
 * Стрим ошибок выполнения задачи. Если [replayLast] будет
 * установлен в `false`, то будут приходить только ошибки _после_ подписки,
 * иначе будет сразу же отправлен последняя ошибка (если таковая имелась).
 *
 * Обратите внимание, что по умолчанию здесь [replayLast] установлен в `false`, потому что в подавляющем
 * большинстве случаев ошибки должны представлять собой "event", а не "state"
 */
public fun <R> JobFlow<R>.errors(replayLast: Boolean = false): Flow<Throwable> {
  return results(replayLast)
    .filterIsInstance<Err<Throwable>>()
    .map { it.getError() ?: error("internal error: null result") }
}

public fun <R1, R2> JobFlow<R1>.mapSuccessResults(transform: suspend (R1) -> R2): JobFlow<R2> {
  return object : JobFlow<R2> {
    override val state: Flow<JobState> = this@mapSuccessResults.state

    override fun results(replayLast: Boolean): Flow<Result<R2, Throwable>> {
      return this@mapSuccessResults.results(replayLast).map {
        when (it) {
          is Ok -> Ok(transform(it.value))
          is Err -> it
        }
      }
    }
  }
}

public fun <R> JobFlow<R>.mapErrors(transform: suspend (Throwable) -> Throwable): JobFlow<R> {
  return object : JobFlow<R> {
    override val state: Flow<JobState> = this@mapErrors.state

    override fun results(replayLast: Boolean): Flow<Result<R, Throwable>> {
      return this@mapErrors.results(replayLast).map {
        when (it) {
          is Ok -> it
          is Err -> Err(transform(it.error))
        }
      }
    }
  }
}

public enum class JobState {
  Idle,
  Running
}
