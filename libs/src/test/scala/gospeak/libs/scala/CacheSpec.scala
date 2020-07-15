package gospeak.libs.scala

import cats.effect.IO
import gospeak.libs.testingutils.BaseSpec

class CacheSpec extends BaseSpec {
  describe("Cache") {
    describe("memoize") {
      it("should compute value once") {
        var call = 0
        val mem = Cache.memoize((n: Int) => {
          call += 1
          n.toString
        })
        mem(1) shouldBe "1"
        call shouldBe 1
        mem(2) shouldBe "2"
        call shouldBe 2
        mem(1) shouldBe "1"
        call shouldBe 2
      }
    }
    describe("memoizeIO") {
      it("should compute value once") {
        var call = 0
        val mem = Cache.memoizeIO((n: Int) => {
          call += 1
          IO.pure(n.toString)
        })
        mem(1).unsafeRunSync() shouldBe "1"
        call shouldBe 1
        mem(2).unsafeRunSync() shouldBe "2"
        call shouldBe 2
        mem(1).unsafeRunSync() shouldBe "1"
        call shouldBe 2
      }
    }
  }
}
