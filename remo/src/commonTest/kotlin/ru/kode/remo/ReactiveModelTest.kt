package ru.kode.remo

import app.cash.turbine.test
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first

class ReactiveModelTest : ShouldSpec({
  var job: Job? = null

  afterEach {
    job?.cancelAndJoin()
  }

  should("throw when calling start() after started") {
    val sut = object : ReactiveModel() {}
    job = sut.start()

    val exception = shouldThrowUnit<IllegalStateException> {
      sut.start()
    }
    exception.message shouldContain "already started"
  }

  should("not conflate results") {
    val sut = object : ReactiveModel() {
      val foo = task { -> 33 }
    }.apply { job = start() }

    sut.foo.jobFlow.results().test {
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
    }.apply { job = start() }

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
    }.apply { job = start() }

    sut.foo.start(33)
    sut.foo.jobFlow.results().first() // await result
    sut.foo.start(44)

    // new subscriber
    sut.foo.jobFlow.results().test {
      awaitItem() shouldBe 44
      cancelAndConsumeRemainingEvents()
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
