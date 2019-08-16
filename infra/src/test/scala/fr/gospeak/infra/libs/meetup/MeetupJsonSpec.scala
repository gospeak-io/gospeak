package fr.gospeak.infra.libs.meetup

import fr.gospeak.infra.libs.meetup.MeetupJson._
import fr.gospeak.infra.libs.meetup.domain._
import fr.gospeak.libs.scalautils.FileUtils
import io.circe.parser.decode
import org.scalatest.{FunSpec, Matchers}

class MeetupJsonSpec extends FunSpec with Matchers {
  private val basePath = "infra/src/test/resources/meetup"

  it("should parse access token response") {
    decode[MeetupToken](FileUtils.read(basePath + "/accessToken.json").get).toTry.get
  }
  it("should parse user response") {
    decode[MeetupUser](FileUtils.read(basePath + "/user.json").get).toTry.get
  }
  it("should parse group response") {
    decode[MeetupGroup](FileUtils.read(basePath + "/group.json").get).toTry.get
  }
  it("should parse event response") {
    decode[MeetupEvent](FileUtils.read(basePath + "/event.json").get).toTry.get
    decode[Seq[MeetupEvent]](FileUtils.read(basePath + "/events.json").get).toTry.get
  }
  it("should parse venues response") {
    decode[Seq[MeetupVenue]](FileUtils.read(basePath + "/venues.json").get).toTry.get
  }
  it("should parse error responses") {
    decode[MeetupError.NotAuthorized](FileUtils.read(basePath + "/errors/notAuthorized.json").get).toTry.get
    decode[MeetupError.Multi](FileUtils.read(basePath + "/errors/multi.json").get).toTry.get
    decode[MeetupError](FileUtils.read(basePath + "/errors/badAuth.json").get).toTry.get
  }
}
