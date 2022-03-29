package ru.kode.remo

import kotlinx.coroutines.Job

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
  public val start: () -> Job,
) : Task<R>

/**
 * Задача с одним входным параметром, выполняемая в рамках [ReactiveModel].
 * Функция [start] используется для запуска/повторного запуска данной задачи.
 * За ходом её выполнения можно следить используя поля объекта [jobFlow]
 */
public class Task1<in P1, R>(
  override val jobFlow: JobFlow<R>,
  public val start: (P1) -> Job,
) : Task<R>

/**
 * Задача с двумя входными параметрами , выполняемая в рамках [ReactiveModel].
 * Функция [start] используется для запуска/повторного запуска данной задачи.
 * За ходом её выполнения можно следить используя поля объекта [jobFlow]
 */
public class Task2<in P1, in P2, R>(
  override val jobFlow: JobFlow<R>,
  public val start: (P1, P2) -> Job,
) : Task<R>

/**
 * Задача с тремя входными параметрами , выполняемая в рамках [ReactiveModel].
 * Функция [start] используется для запуска/повторного запуска данной задачи.
 * За ходом её выполнения можно следить используя поля объекта [jobFlow]
 */
public class Task3<in P1, in P2, in P3, R>(
  override val jobFlow: JobFlow<R>,
  public val start: (P1, P2, P3) -> Job,
) : Task<R>
