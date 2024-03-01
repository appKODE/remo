/*
 * Copyright 2022 KODE LLC. Use of this source code is governed by the MIT license.
 */
package ru.kode.remo

import kotlinx.coroutines.Job

/**
 * Task which gets executed in scope of a [ReactiveModel]
 */
public interface Task<R> {
  public val jobFlow: JobFlow<R>
}

/**
 * Task without input parameters which gets executed in scope of a [ReactiveModel].
 *
 * Use [start] method to start or restart the task.
 * A [jobFlow] property could be used to observe the task completion state and results.
 */
public interface Task0<R> : Task<R> {
  /**
   * Starts the task
   *
   * @param scheduled determines the scheduling strategy of the task. Default is [StartScheduled.Eagerly]
   * @param queueingStrategy determines the queueing strategy of the task. Default is [QueueingStrategy.Disallow]
   */
  public fun start(
    scheduled: StartScheduled = StartScheduled.Eagerly,
    queueingStrategy: QueueingStrategy = QueueingStrategy.Disallow
  ): Job
}

/**
 * Task with a single input parameter which gets executed in scope of a [ReactiveModel].
 *
 * Use [start] method to start or restart the task.
 * A [jobFlow] property could be used to observe the task completion state and results.
 */
public interface Task1<in P1, R> : Task<R> {
  /**
   * Starts the task
   *
   * @param scheduled determines the scheduling strategy of the task. Default is [StartScheduled.Eagerly]
   * @param queueingStrategy determines the queueing strategy of the task. Default is [QueueingStrategy.Disallow]
   */
  public fun start(
    argument: P1,
    scheduled: StartScheduled = StartScheduled.Eagerly,
    queueingStrategy: QueueingStrategy = QueueingStrategy.Disallow
  ): Job
}

/**
 * Task with two input parameters which gets executed in scope of a [ReactiveModel].
 *
 * Use [start] method to start or restart the task.
 * A [jobFlow] property could be used to observe the task completion state and results.
 */
public interface Task2<in P1, in P2, R> : Task<R> {
  /**
   * Starts the task
   *
   * @param scheduled determines the scheduling strategy of the task. Default is [StartScheduled.Eagerly]
   * @param queueingStrategy determines the queueing strategy of the task. Default is [QueueingStrategy.Disallow]
   */
  public fun start(
    argument1: P1,
    argument2: P2,
    scheduled: StartScheduled = StartScheduled.Eagerly,
    queueingStrategy: QueueingStrategy = QueueingStrategy.Disallow
  ): Job
}

/**
 * Task with three input parameters which gets executed in scope of a [ReactiveModel].
 *
 * Use [start] method to start or restart the task.
 * A [jobFlow] property could be used to observe the task completion state and results.
 */
public interface Task3<in P1, in P2, in P3, R> : Task<R> {
  /**
   * Starts the task
   *
   * @param scheduled determines the scheduling strategy of the task. Default is [StartScheduled.Eagerly]
   * @param queueingStrategy determines the queueing strategy of the task. Default is [QueueingStrategy.Disallow]
   */
  public fun start(
    argument1: P1,
    argument2: P2,
    argument3: P3,
    scheduled: StartScheduled = StartScheduled.Eagerly,
    queueingStrategy: QueueingStrategy = QueueingStrategy.Disallow,
  ): Job
}
