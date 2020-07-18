package gospeak.core.domain

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import gospeak.core.domain.Group.Settings.Action.Trigger.{OnEventCreated, OnEventPublish}
import gospeak.core.domain.messages.Message
import gospeak.core.testingutils.BaseSpec
import gospeak.core.testingutils.Generators._
import gospeak.libs.scala.domain.Liquid

class GroupSpec extends BaseSpec with RandomDataGenerator {
  private val settings = random[Group.Settings]

  describe("Group") {
    describe("Settings") {
      it("should update templates") {
        val s1 = settings.addEventTemplate("tmpl", Liquid[Message.EventInfo]("aaa")).get
        s1.getEventTemplate("tmpl") shouldBe Some(Liquid[Message.EventInfo]("aaa"))
        val s2 = s1.updateEventTemplate("tmpl", "tmpl", Liquid[Message.EventInfo]("bbb")).get
        s2.getEventTemplate("tmpl") shouldBe Some(Liquid[Message.EventInfo]("bbb"))
        val s3 = s2.removeEventTemplate("tmpl").get
        s3.getEventTemplate("tmpl") shouldBe None
      }
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
