package fr.gospeak.libs.scalautils

import cats.effect.IO
import fr.gospeak.libs.scalautils.MessageBusSpec._
import org.scalatest.{FunSpec, Matchers}

class MessageBusSpec extends FunSpec with Matchers {
  describe("MessageBus") {
    it("test") {
      import cats.implicits._
      val s = Seq(1, 2, 3).map(IO.pure)
      val r = s.toList.sequence
      println(s"hello: ${r.unsafeRunSync()}")
    }
    it("should be specialized for a message type") {
      val mb = create[MyEvents]()
      mb.publish(Event1("a"))
      // mb.publish(Message("a")) // should not compile
    }
    describe("subscribe") {
      it("should register a handler based on message type") {
        val mb = create[Any]()
        var tmp = ""
        mb.subscribe[Event1](e => IO {
          tmp = e.msg
        })
        mb.publish(Event1("a")).unsafeRunSync() shouldBe 1
        tmp shouldBe "a"
        mb.publish(Msg("b")).unsafeRunSync() shouldBe 0
        tmp shouldBe "a"
        mb.publish(Event1("c")).unsafeRunSync() shouldBe 1
        tmp shouldBe "c"
      }
      it("should work with parent types") {
        val mb = create[Any]()
        var tmp = ""
        mb.subscribe[MyEvents] {
          case e: Event1 => IO {
            tmp = e.msg
          }
          case e: Event2 => IO {
            tmp = e.count.toString
          }
        }
        mb.publish(Event1("a")).unsafeRunSync() shouldBe 1
        tmp shouldBe "a"
        mb.publish(Event2(2)).unsafeRunSync() shouldBe 1
        tmp shouldBe "2"
        mb.publish(Event1("c"): MyEvents).unsafeRunSync() shouldBe 1
        tmp shouldBe "c"
      }
    }
  }
}

object MessageBusSpec {

  sealed trait MyEvents

  case class Event1(msg: String) extends MyEvents

  case class Event2(count: Int) extends MyEvents

  case class Msg(info: String)

  def create[A](): MessageBus[A] = {
    new BasicMessageBus[A]
  }
}
