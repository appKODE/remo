package ru.kode.remo

import app.cash.turbine.test
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReactiveModelTest : ShouldSpec({
  var model: ReactiveModel? = null

  afterEach {
    model?.dispose()
  }

  should("not conflate results") {
    val sut = object : ReactiveModel() {
      val foo = task { -> 33 }
    }.also { model = it }

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
    }.also { model = it }

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
    }.also { model = it }

    sut.foo.start(33)
    sut.foo.jobFlow.results().first() // await result
    sut.foo.start(44)

    // new subscriber
    sut.foo.jobFlow.results().test {
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
    }.also { model = it }

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
    }.also { model = it }

    sut.uncaughtExceptions.test {
      sut.produceError1()
      awaitItem()
      sut.produceError2()
      awaitItem()
      cancelAndIgnoreRemainingEvents()
    }

    sut.uncaughtExceptions.first().message shouldBe "error2"
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
