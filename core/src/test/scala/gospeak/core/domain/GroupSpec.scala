package gospeak.core.domain

import gospeak.core.domain.Group.Settings.Action.Trigger.{OnEventCreated, OnEventPublish}
import org.scalatest.{FunSpec, Matchers}

class GroupSpec extends FunSpec with Matchers {
  describe("Group") {
    describe("Settings") {
      describe("Action") {
        describe("Trigger") {
          it("should return class name as value") {
            OnEventCreated.value shouldBe "OnEventCreated"
            OnEventPublish.value shouldBe "OnEventPublish"
          }
        }
      }
    }
  }
}
