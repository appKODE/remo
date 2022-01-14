package ru.kode.remo

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Предоставляет возможность наблюдения за процессом выполнения повторяемых задач [ReactiveModel]
 */
public interface JobFlow<R> {
  /**
   * Стрим состояний активности-неактивности
   */
  public val state: StateFlow<JobState>

  /**
   * Стрим результатов выполнения задачи. Если [replayLast] будет
   * установлен в `false`, то будут приходить только результаты выполнения _после_ подписки,
   * иначе будет сразу же отправлен последний успешный результат (если таковой был)
   */
  public fun results(replayLast: Boolean = true): Flow<Result<R, Throwable>>

  /**
   * Стрим успешных результатов успешного выполнения задачи. Если [replayLast] будет
   * установлен в `false`, то будут приходить только результаты выполнения _после_ подписки,
   * иначе будет сразу же отправлен последний успешный результат (если таковой был)
   *
   * Обратите внимание, что по умолчанию здесь [replayLast] установлен в `false`, потому что в подавляющем
   * большинстве случаев за успешным выполеннием следят уже после подписки, поэтому они
   * должны представлять собой "event", а не "state"
   */
  public fun successResults(replayLast: Boolean = false): Flow<R>

  /**
   * Стрим ошибок выполнения задачи. Если [replayLast] будет
   * установлен в `false`, то будут приходить только ошибки _после_ подписки,
   * иначе будет сразу же отправлен последняя ошибка (если таковая имелась).
   *
   * Обратите внимание, что по умолчанию здесь [replayLast] установлен в `false`, потому что в подавляющем
   * большинстве случаев ошибки должны представлять собой "event", а не "state"
   */
  public fun errors(replayLast: Boolean = false): Flow<Throwable>
}

public enum class JobState {
  Idle,
  Running
}
