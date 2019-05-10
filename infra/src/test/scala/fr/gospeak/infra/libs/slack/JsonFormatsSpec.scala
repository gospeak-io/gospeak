package fr.gospeak.infra.libs.slack

import fr.gospeak.infra.libs.slack.JsonFormats._
import fr.gospeak.infra.libs.slack.domain.{SlackChannel, SlackMessage, SlackTokenInfo, SlackUser}
import io.circe.parser.decode
import org.scalatest.{FunSpec, Matchers}

import scala.io.Source

class JsonFormatsSpec extends FunSpec with Matchers {
  private val basePath = "infra/src/test/resources/slack"

  it("should parse auth.test response") {
    decode[SlackTokenInfo](read("auth.test.json")).toTry.get
  }
  it("should parse channels.create response") {
    decode[SlackChannel.Single](read("channels.create.json")).toTry.get
  }
  it("should parse channels.invite response") {
    decode[SlackChannel.Single](read("channels.invite.json")).toTry.get
  }
  it("should parse channels.list response") {
    val res = decode[SlackChannel.List](read("channels.list.json")).toTry.get
    res.channels.length shouldBe 4
  }
  it("should parse users.list response") {
    val res = decode[SlackUser.List](read("users.list.json")).toTry.get
    res.members.length shouldBe 1
  }
  it("should parse chat.postMessage response") {
    decode[SlackMessage.Posted](read("chat.postMessage.json")).toTry.get
  }

  private def read(file: String): String = {
    val src = Source.fromFile(basePath + "/" + file)
    try {
      src.mkString
    } finally {
      src.close()
    }
  }
}
