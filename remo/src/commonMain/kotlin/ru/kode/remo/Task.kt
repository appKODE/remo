/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
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
public interface Task0<R> : Task<R> {
  public fun start(scheduled: StartScheduled = StartScheduled.Eagerly): Job
}

/**
 * Задача с одним входным параметром, выполняемая в рамках [ReactiveModel].
 * Функция [start] используется для запуска/повторного запуска данной задачи.
 * За ходом её выполнения можно следить используя поля объекта [jobFlow]
 */
public interface Task1<in P1, R> : Task<R> {
  public fun start(
    argument: P1,
    scheduled: StartScheduled = StartScheduled.Eagerly
  ): Job
}

/**
 * Задача с двумя входными параметрами , выполняемая в рамках [ReactiveModel].
 * Функция [start] используется для запуска/повторного запуска данной задачи.
 * За ходом её выполнения можно следить используя поля объекта [jobFlow]
 */
public interface Task2<in P1, in P2, R> : Task<R> {
  public fun start(
    argument1: P1,
    argument2: P2,
    scheduled: StartScheduled = StartScheduled.Eagerly
  ): Job
}

/**
 * Задача с тремя входными параметрами , выполняемая в рамках [ReactiveModel].
 * Функция [start] используется для запуска/повторного запуска данной задачи.
 * За ходом её выполнения можно следить используя поля объекта [jobFlow]
 */
public interface Task3<in P1, in P2, in P3, R> : Task<R> {
  public fun start(
    argument1: P1,
    argument2: P2,
    argument3: P3,
    scheduled: StartScheduled = StartScheduled.Eagerly
  ): Job
}
