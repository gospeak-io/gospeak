package fr.gospeak.web.utils

import fr.gospeak.core.domain._
import fr.gospeak.web.utils.PathBindables._
import org.scalatest.{FunSpec, Matchers}

class PathBindablesSpec extends FunSpec with Matchers {
  describe("PathBindables") {
    describe("groupSlugPathBinder") {
      it("should bind & unbind") {
        val slug = Group.Slug.from("value").get
        groupSlugPathBinder.bind("key", "value") shouldBe Right(slug)
        groupSlugPathBinder.unbind("key", slug) shouldBe "value"
      }
    }
    describe("eventSlugPathBinder") {
      it("should bind & unbind") {
        val slug = Event.Slug.from("value").get
        eventSlugPathBinder.bind("key", "value") shouldBe Right(slug)
        eventSlugPathBinder.unbind("key", slug) shouldBe "value"
      }
    }
    describe("talkSlugPathBinder") {
      it("should bind & unbind") {
        val slug = Talk.Slug.from("value").get
        talkSlugPathBinder.bind("key", "value") shouldBe Right(slug)
        talkSlugPathBinder.unbind("key", slug) shouldBe "value"
      }
    }
    describe("talkStatusPathBinder") {
      it("should bind & unbind") {
        Talk.Status.all.foreach { status =>
          talkStatusPathBinder.bind("key", status.toString) shouldBe Right(status)
          talkStatusPathBinder.unbind("key", status) shouldBe status.toString
        }
      }
    }
    describe("cfpSlugPathBinder") {
      it("should bind & unbind") {
        val slug = Cfp.Slug.from("value").get
        cfpSlugPathBinder.bind("key", "value") shouldBe Right(slug)
        cfpSlugPathBinder.unbind("key", slug) shouldBe "value"
      }
    }
    describe("proposalIdPathBinder") {
      it("should bind & unbind") {
        val id = Proposal.Id.generate()
        proposalIdPathBinder.bind("key", id.value) shouldBe Right(id)
        proposalIdPathBinder.unbind("key", id) shouldBe id.value
      }
      it("should fail on invalid format") {
        proposalIdPathBinder.bind("key", "invalid") shouldBe a[Left[_, _]]
      }
    }
  }
}
