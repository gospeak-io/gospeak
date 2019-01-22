package fr.gospeak.web.utils

import fr.gospeak.core.domain.{Event, Group, Proposal, Talk}
import fr.gospeak.web.utils.PathBindables._
import org.scalatest.{FunSpec, Matchers}

class PathBindablesSpec extends FunSpec with Matchers {
  describe("PathBindables") {
    describe("groupSlugPathBinder") {
      it("should bind & unbind") {
        groupSlugPathBinder.bind("key", "value") shouldBe Right(Group.Slug("value"))
        groupSlugPathBinder.unbind("key", Group.Slug("value")) shouldBe "value"
      }
    }
    describe("eventSlugPathBinder") {
      it("should bind & unbind") {
        eventSlugPathBinder.bind("key", "value") shouldBe Right(Event.Slug("value"))
        eventSlugPathBinder.unbind("key", Event.Slug("value")) shouldBe "value"
      }
    }
    describe("talkSlugPathBinder") {
      it("should bind & unbind") {
        talkSlugPathBinder.bind("key", "value") shouldBe Right(Talk.Slug("value"))
        talkSlugPathBinder.unbind("key", Talk.Slug("value")) shouldBe "value"
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
