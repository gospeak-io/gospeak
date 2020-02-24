package gospeak.web.utils

import gospeak.core.domain._
import gospeak.web.utils.PathBindables._
import org.scalatest.{FunSpec, Matchers}

class PathBindablesSpec extends FunSpec with Matchers {
  describe("PathBindables") {
    it("should bind & unbind a Group.Slug") {
      val slug = Group.Slug.from("value").right.get
      groupSlugPathBinder.bind("key", "value") shouldBe Right(slug)
      groupSlugPathBinder.unbind("key", slug) shouldBe "value"
    }
    it("should bind & unbind a Event.Slug") {
      val slug = Event.Slug.from("value").right.get
      eventSlugPathBinder.bind("key", "value") shouldBe Right(slug)
      eventSlugPathBinder.unbind("key", slug) shouldBe "value"
    }
    it("should bind & unbind a Talk.Slug") {
      val slug = Talk.Slug.from("value").right.get
      talkSlugPathBinder.bind("key", "value") shouldBe Right(slug)
      talkSlugPathBinder.unbind("key", slug) shouldBe "value"
    }
    it("should bind & unbind a Talk.Status") {
      Talk.Status.all.foreach { status =>
        talkStatusPathBinder.bind("key", status.toString) shouldBe Right(status)
        talkStatusPathBinder.unbind("key", status) shouldBe status.toString
      }
    }
    it("should bind & unbind a Cfp.Slug") {
      val slug = Cfp.Slug.from("value").right.get
      cfpSlugPathBinder.bind("key", "value") shouldBe Right(slug)
      cfpSlugPathBinder.unbind("key", slug) shouldBe "value"
    }
    it("should bind & unbind a Proposal.Id") {
      val id = Proposal.Id.generate()
      proposalIdPathBinder.bind("key", id.value) shouldBe Right(id)
      proposalIdPathBinder.unbind("key", id) shouldBe id.value

      proposalIdPathBinder.bind("key", "invalid") shouldBe a[Left[_, _]]
    }
    it("should bind & unbind a User.Slug") {
      val slug = User.Slug.from("value").right.get
      userSlugPathBinder.bind("key", "value") shouldBe Right(slug)
      userSlugPathBinder.unbind("key", slug) shouldBe "value"
    }
  }
}
