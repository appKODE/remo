package ru.kode.remo

import app.cash.turbine.test
import com.github.michaelbull.result.Ok
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@ExperimentalTime
class ReactiveModelTest : ShouldSpec({
  var testScope = CoroutineScope(Dispatchers.Default)

  beforeEach {
    testScope.cancel()
    testScope = CoroutineScope(Dispatchers.Default)
  }

  should("not conflate results") {
    val sut = object : ReactiveModel() {
      val foo = task { -> 33 }
    }.also { it.start(testScope) }

    sut.foo.jobFlow.successResults(replayLast = true).test {
      sut.foo.start()
      awaitItem() shouldBe 33
      sut.foo.start()
      awaitItem() shouldBe 33
      cancelAndIgnoreRemainingEvents()
    }
  }

  should("not conflate errors") {
    val sut = object : ReactiveModel() {
      val foo = task<Unit> { throw ExceptionWithEquals("i am an error") }
    }.also { it.start(testScope) }

    sut.foo.jobFlow.errors(replayLast = true).test {
      sut.foo.start()
      awaitItem().message shouldBe "i am an error"
      sut.foo.start()
      awaitItem().message shouldBe "i am an error"
      cancelAndIgnoreRemainingEvents()
    }
  }

  should("late subscriber receives the last result") {
    val sut = object : ReactiveModel() {
      val foo = task { i: Int -> i }
    }.also { it.start(testScope) }

    sut.foo.start(33)
    sut.foo.jobFlow.successResults(replayLast = true).first() // await result
    sut.foo.jobFlow.state.filter { it == JobState.Idle }.first()
    sut.foo.start(44)
    sut.foo.jobFlow.successResults(replayLast = true).first() // await result
    sut.foo.jobFlow.state.filter { it == JobState.Idle }.first()

    // new subscriber
    sut.foo.jobFlow.successResults(replayLast = true).test {
      awaitItem() shouldBe 44
      cancelAndConsumeRemainingEvents()
    }
  }

  should("emit an uncaught error") {
    val sut = object : ReactiveModel() {
      fun produceError1() {
        scope.launch {
          throw RuntimeException("error1")
        }
      }
      fun produceError2() {
        scope.launch {
          throw RuntimeException("error2")
        }
      }
    }.also { it.start(testScope) }

    sut.uncaughtExceptions.test {
      sut.produceError1()
      awaitItem().message shouldBe "error1"
      sut.produceError2()
      awaitItem().message shouldBe "error2"
      cancelAndIgnoreRemainingEvents()
    }
  }

  should("emit last uncaught error on subscription") {
    val sut = object : ReactiveModel() {
      fun produceError1() {
        scope.launch {
          throw RuntimeException("error1")
        }
      }
      fun produceError2() {
        scope.launch {
          throw RuntimeException("error2")
        }
      }
    }.also { it.start(testScope) }

    sut.uncaughtExceptions.test {
      sut.produceError1()
      awaitItem()
      sut.produceError2()
      awaitItem()
      cancelAndIgnoreRemainingEvents()
    }

    sut.uncaughtExceptions.first().message shouldBe "error2"
  }

  should("emit result to existing subscribers when replayLast is false") {
    val sut = object : ReactiveModel() {
      val task = task { ->
        "hello"
      }
    }.also { it.start(testScope) }

    sut.task.jobFlow.results(replayLast = false).test {
      sut.task.start()
      awaitItem() shouldBe Ok("hello")
    }
  }

  should("emit success to existing subscribers when replayLast is false") {
    val sut = object : ReactiveModel() {
      val task = task { ->
        "hello"
      }
    }.also { it.start(testScope) }

    sut.task.jobFlow.successResults(replayLast = false).test {
      sut.task.start()
      awaitItem() shouldBe "hello"
    }
  }

  should("emit error to existing subscribers when replayLast is false") {
    val sut = object : ReactiveModel() {
      val task = task { ->
        throw RuntimeException("hello")
      }
    }.also { it.start(testScope) }

    sut.task.jobFlow.errors(replayLast = false).test {
      sut.task.start()
      awaitItem().message shouldBe "hello"
    }
  }

  should("emit error to when replayLast is false and emit after construct before subscribe") {
    val sut = object : ReactiveModel() {
      val task = task { ->
        throw RuntimeException("hello")
      }
    }.also { it.start(testScope) }

    val flow = sut.task.jobFlow.errors(replayLast = false)

    sut.task.start()
    sut.task.jobFlow.results().first() // await result
    sut.task.jobFlow.state.filter { it == JobState.Idle }

    flow.test {
      this.expectNoEvents()
    }
  }

  should("cancel model when parentScope is cancelled") {
    val sut = object : ReactiveModel() {
      val task = task { ->
        delay(10_000)
      }
    }
    val job = sut.start(testScope)

    sut.task.start()

    testScope.cancel()
    eventually(duration = 3.seconds) {
      testScope.isActive shouldBe false
      job.isActive shouldBe false
    }
  }

  should("use global custom exception mapper") {
    val mapper = { e: Throwable -> if (e is CancellationException) throw e else RuntimeException(e.message + "adj") }
    val sut = object : ReactiveModel(errorMapper = mapper) {
      val task = task { ->
        error("hello")
      }
    }.also { it.start(testScope) }

    sut.task.jobFlow.errors(replayLast = false).test {
      sut.task.start()
      awaitItem().message shouldBe "helloadj"
    }
  }

  should("allow local error mapper override the global one") {
    val mapper = { e: Throwable -> if (e is CancellationException) throw e else RuntimeException(e.message + "adj") }
    val sut = object : ReactiveModel(errorMapper = mapper) {
      val task = task(errorMapper = { RuntimeException(it.message + "jda") }) { ->
        error("hello")
      }
    }.also { it.start(testScope) }

    sut.task.jobFlow.errors(replayLast = false).test {
      sut.task.start()
      awaitItem().message shouldBe "hellojda"
    }
  }

  should("cancel task on Job cancel") {
    val sut = object : ReactiveModel() {
      val flow = MutableStateFlow(0)
      val task = task { ->
        while (true) {
          flow.update { it + 1 }
          delay(100)
          yield()
        }
      }
    }.also { it.start(testScope) }

    sut.flow.test {
      val job = sut.task.start()

      awaitItem() shouldBe 0
      awaitItem() shouldBe 1
      awaitItem() shouldBe 2
      awaitItem() shouldBe 3
      job.cancelAndJoin()
      delay(200)
      expectNoEvents()
    }
  }

  should("subscribe eagerly when requested") {
    val sut = object : ReactiveModel() {
      val flow = MutableStateFlow(0)
      val task = task { ->
        flow.update { it + 1 }
      }
    }.also { it.start(testScope) }

    sut.task.start(scheduled = StartScheduled.Eagerly)
    delay(200)

    // subscribing without replay should emit nothing,
    // but subscribing with replay should produce result which was computed almost immediately after
    // start() and prior to any subscriptions

    val result = withTimeoutOrNull(300) {
      sut.task.jobFlow.results(replayLast = false).first()
    }
    result.shouldBeNull()
    sut.task.jobFlow.results(replayLast = true).test {
      awaitItem() shouldBe Ok(Unit)
    }
  }

  should("subscribe lazily when requested with min results subscribers") {
    val sut = object : ReactiveModel() {
      val flow = MutableStateFlow(0)
      val task = task { ->
        flow.update { it + 1 }
      }
    }.also { it.start(testScope) }

    sut.task.start(scheduled = StartScheduled.Lazily(minResultsSubscribers = 1, minStateSubscribers = 0))
    delay(200)

    // subscribing without replay should still work because execution will start lazily,
    // only after results() subscriber is registered
    sut.task.jobFlow.results(replayLast = false).test {
      awaitItem() shouldBe Ok(Unit)
    }
  }

  should("subscribe lazily when requested with min state subscribers") {
    val sut = object : ReactiveModel() {
      val flow = MutableStateFlow(0)
      val task = task { ->
        flow.update { it + 1 }
      }
    }.also { it.start(testScope) }

    sut.task.start(scheduled = StartScheduled.Lazily(minResultsSubscribers = 0, minStateSubscribers = 1))
    delay(200)

    // subscribing without replay should still work because execution will start lazily,
    // only after state() subscriber is registered
    sut.task.jobFlow.state.test {
      awaitItem() shouldBe JobState.Idle
      awaitItem() shouldBe JobState.Running
      awaitItem() shouldBe JobState.Idle
    }
  }
})

private class ExceptionWithEquals(val msg: String) : Throwable(msg) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class != other::class) return false

    other as ExceptionWithEquals

    if (msg != other.msg) return false

    return true
  }

  override fun hashCode(): Int {
    return msg.hashCode()
  }
}
