/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.remo

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Контекст для запуска suspend-функций и наблюдения за ходом их выполнения.
 * Предназначен для использования в наследниках [ReactiveModel], клиентам рекомендуется отдавать [JobFlow]
 * (интерфейс, который данный класс имплементирует)
 *
 * @param name имя задачи, описательное
 * @param errorMapper Если указан, будет использоваться для маппинга ошибок перед
 * их emit-ом в стримы результатов/ошибок.
 */
public class WatchContext<R>(
  public val name: String = ReactiveModel.createWatchContextName(),
  public val errorMapper: ((Throwable) -> Throwable)? = null,
) : JobFlow<R> {
  private val _state = MutableStateFlow(JobState.Idle)
  private val _results = MutableSharedFlow<Result<R, Throwable>?>(replay = 1)

  @Suppress("TooGenericExceptionCaught") // intentionally catching all exceptions to wrap them in Result
  private suspend fun execute(body: suspend () -> R) {
    if (_state.value == JobState.Running) {
      error("$name is already executing some job")
    }
    _state.value = JobState.Running
    try {
      _results.emit(Ok(body()))
    } catch (e: Throwable) {
      if (e is CancellationException) throw e
      _results.emit(Err(errorMapper?.invoke(e) ?: e))
    } finally {
      _state.value = JobState.Idle
    }
  }

  public fun executeIn(scope: CoroutineScope, body: suspend () -> R): Job {
    return scope.launch(block = { execute(body) })
  }

  override val state: Flow<JobState> = _state

  override fun results(replayLast: Boolean): Flow<Result<R, Throwable>> {
    return if (!replayLast) {
      flow { _results.drop(_results.replayCache.size).collect { emit(it) } }
    } else {
      _results
    }.filterNotNull()
  }
}
