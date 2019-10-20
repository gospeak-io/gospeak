package fr.gospeak.infra.libs.slack

import fr.gospeak.infra.libs.slack.SlackJson._
import fr.gospeak.infra.libs.slack.domain.{SlackChannel, SlackMessage, SlackTokenInfo, SlackUser}
import fr.gospeak.libs.scalautils.FileUtils
import io.circe.parser.decode
import org.scalatest.{FunSpec, Matchers}

class SlackJsonSpec extends FunSpec with Matchers {
  private val basePath = "src/test/resources/slack"

  describe("SlackJson") {
    it("should parse auth.test response") {
      decode[SlackTokenInfo](FileUtils.read(basePath + "/auth.test.json").get).toTry.get
    }
    it("should parse channels.create response") {
      decode[SlackChannel.Single](FileUtils.read(basePath + "/channels.create.json").get).toTry.get
    }
    it("should parse channels.invite response") {
      decode[SlackChannel.Single](FileUtils.read(basePath + "/channels.invite.json").get).toTry.get
    }
    it("should parse channels.list response") {
      val res = decode[SlackChannel.List](FileUtils.read(basePath + "/channels.list.json").get).toTry.get
      res.channels.length shouldBe 4
    }
    it("should parse users.list response") {
      val res = decode[SlackUser.List](FileUtils.read(basePath + "/users.list.json").get).toTry.get
      res.members.length shouldBe 1
    }
    it("should parse chat.postMessage response") {
      decode[SlackMessage.Posted](FileUtils.read(basePath + "/chat.postMessage.json").get).toTry.get
    }
  }
}
