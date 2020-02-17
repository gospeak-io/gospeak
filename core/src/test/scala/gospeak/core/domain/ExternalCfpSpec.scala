package gospeak.core.domain

import java.time.LocalDateTime

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import gospeak.core.testingutils.Generators._
import org.scalatest.{FunSpec, Matchers}

class ExternalCfpSpec extends FunSpec with Matchers with RandomDataGenerator {
  private val now = LocalDateTime.now()
  private val yesterday = now.minusDays(1)
  private val tomorrow = now.plusDays(1)
  private val lastWeek = now.minusDays(7)
  private val nextWeek = now.plusDays(7)
  private val cfp = random[ExternalCfp]

  describe("ExternalCfp") {
    it("should be future when begin is in the future") {
      cfp.copy(begin = None, close = None).isFuture(now) shouldBe false
      cfp.copy(begin = Some(nextWeek), close = None).isFuture(now) shouldBe true
      cfp.copy(begin = Some(lastWeek), close = None).isFuture(now) shouldBe false
    }
    it("should be past when close is in the past") {
      cfp.copy(begin = None, close = None).isPast(now) shouldBe false
      cfp.copy(begin = None, close = Some(nextWeek)).isPast(now) shouldBe false
      cfp.copy(begin = None, close = Some(lastWeek)).isPast(now) shouldBe true
    }
    it("should be active when after begin, if defined and before close, if defined") {
      cfp.copy(begin = None, close = None).isActive(now) shouldBe true
      cfp.copy(begin = Some(nextWeek), close = None).isActive(now) shouldBe false
      cfp.copy(begin = Some(lastWeek), close = None).isActive(now) shouldBe true
      cfp.copy(begin = None, close = Some(nextWeek)).isActive(now) shouldBe true
      cfp.copy(begin = None, close = Some(lastWeek)).isActive(now) shouldBe false
      cfp.copy(begin = Some(lastWeek), close = Some(yesterday)).isActive(now) shouldBe false
      cfp.copy(begin = Some(lastWeek), close = Some(nextWeek)).isActive(now) shouldBe true
      cfp.copy(begin = Some(tomorrow), close = Some(nextWeek)).isActive(now) shouldBe false
    }
  }
}
