package gospeak.infra.services.slack

import gospeak.core.services.slack.domain.{SlackCredentials, SlackToken}
import gospeak.libs.slack.SlackClient
import gospeak.libs.slack.domain.SlackError
import gospeak.infra.services.TemplateSrvImpl
import gospeak.libs.scala.domain.Crypted
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class SlackSrvImplSpec extends AnyFunSpec with Matchers {
  private val token = SlackToken(Crypted("..."))
  private val creds = SlackCredentials(token, "Gospeak test", None)
  private val client = new SlackClient
  private val srv = new SlackSrvImpl(client, new TemplateSrvImpl)

  describe("SlackSrvImpl") {
    describe("exec") {
      describe("PostMessage") {
        /* it("should") {
          val action = SlackAction.PostMessage(Template.Mustache("test1"), Template.Mustache("test"), createdChannelIfNotExist = true, inviteEverybody = true)
          srv.exec(creds, action, TemplateData.EventPublished()).unsafeRunSync()
        } */
      }
    }
    describe("format") {
      it("should format channel not found error") {
        val err = SlackError(ok = false, "channel_not_found", needed = None, provided = None)
        SlackSrvImpl.format(err) shouldBe "channel_not_found"
      }
      it("should format missing scope error") {
        val err = SlackError(ok = false, "missing_scope", needed = Some("channels:write"), provided = Some("identify,chat:write:bot"))
        SlackSrvImpl.format(err) shouldBe "missing_scope channels:write (provided: identify,chat:write:bot)"
      }
    }
  }
}
