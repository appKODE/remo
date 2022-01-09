package ru.kode.remo

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Контекст для запуска suspend-функций и наблюдения за ходом их выполнения.
 * Предназначен для использования в наследниках [ReactiveModel], клиентам рекомендуется отдавать [JobFlow]
 * (интерфейс, который данный класс имплементирует)
 */
public class WatchContext<R>(public val name: String = ReactiveModel.createWatchContextName()) : JobFlow<R> {
  private val _state = MutableStateFlow(JobState.Idle)
  private val _results = MutableSharedFlow<Result<R, Throwable>?>(replay = 1)

  @Suppress("TooGenericExceptionCaught") // intentionally catching all exceptions to wrap them in Result
  public suspend fun execute(body: suspend () -> R) {
    if (_state.value == JobState.Running) {
      error("$name is already executing some job")
    }
    _state.value = JobState.Running
    try {
      _results.emit(Ok(body()))
    } catch (e: Throwable) {
      if (e is CancellationException) throw e
      _results.emit(Err(e))
    } finally {
      _state.value = JobState.Idle
    }
  }

  public fun executeIn(scope: CoroutineScope, body: suspend () -> R) {
    scope.launch(block = { execute(body) })
  }

  override val state: StateFlow<JobState> = _state

  override fun results(replayLast: Boolean): Flow<Result<R, Throwable>> {
    return _results.filterNotNull()
  }

  override fun successResults(replayLast: Boolean): Flow<R> {
    return _results
      .filter { it != null && it is Ok }
      .map { it?.get() ?: error("internal error: null result") }
      .let { if (replayLast) it else it.drop(1) }
  }

  override fun errors(replayLast: Boolean): Flow<Throwable> {
    return _results
      .filter { it != null && it is Err }
      .map { it?.getError() ?: error("internal error: null result") }
      .let { if (replayLast) it else it.drop(1) }
  }
}
