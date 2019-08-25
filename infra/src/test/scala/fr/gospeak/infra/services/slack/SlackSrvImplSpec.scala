package fr.gospeak.infra.services.slack

import fr.gospeak.core.services.slack.domain.{SlackCredentials, SlackToken}
import fr.gospeak.infra.libs.slack.SlackClient
import fr.gospeak.infra.libs.slack.domain.SlackError
import fr.gospeak.infra.services.TemplateSrv
import fr.gospeak.libs.scalautils.domain.Crypted
import org.scalatest.{FunSpec, Matchers}

class SlackSrvImplSpec extends FunSpec with Matchers {
  private val token = SlackToken(Crypted("..."))
  private val creds = SlackCredentials(token, "Gospeak test", None)
  private val client = new SlackClient
  private val srv = new SlackSrvImpl(client, new TemplateSrv)

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
