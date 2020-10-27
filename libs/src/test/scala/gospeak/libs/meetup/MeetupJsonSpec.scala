package gospeak.libs.meetup

import gospeak.libs.meetup.MeetupJson._
import gospeak.libs.meetup.domain._
import gospeak.libs.scala.FileUtils
import gospeak.libs.testingutils.BaseSpec
import io.circe.parser.decode

class MeetupJsonSpec extends BaseSpec {
  private val basePath = FileUtils.adaptLocalPath("libs/src/test/resources/meetup")

  it("should parse access token response") {
    decode[MeetupToken](FileUtils.read(basePath + "/accessToken.json").get).toTry.get
  }
  it("should parse user response") {
    decode[MeetupUser.Alt](FileUtils.read(basePath + "/user.json").get).toTry.get
  }
  it("should parse groups response") {
    decode[List[MeetupGroup]](FileUtils.read(basePath + "/groups.json").get).toTry.get
  }
  it("should parse group response") {
    decode[MeetupGroup](FileUtils.read(basePath + "/group.json").get).toTry.get
  }
  it("should parse event response") {
    decode[MeetupEvent](FileUtils.read(basePath + "/event.json").get).toTry.get
    decode[List[MeetupEvent]](FileUtils.read(basePath + "/events.json").get).toTry.get
    decode[MeetupEvent](FileUtils.read(basePath + "/eventCreated.json").get).toTry.get
  }
  it("should parse attendees response") {
    decode[List[MeetupAttendee]](FileUtils.read(basePath + "/attendees.json").get).toTry.get
  }
  it("should parse venues response") {
    decode[List[MeetupVenue]](FileUtils.read(basePath + "/venues.json").get).toTry.get
  }
  it("should parse locations response") {
    decode[List[MeetupLocation]](FileUtils.read(basePath + "/locations.json").get).toTry.get
  }
  it("should parse error responses") {
    decode[MeetupError.NotAuthorized](FileUtils.read(basePath + "/errors/not_authorized.json").get).toTry.get
    decode[MeetupError](FileUtils.read(basePath + "/errors/invalid_request.json").get).toTry.get
    decode[MeetupError.Multi](FileUtils.read(basePath + "/errors/authentication_error.json").get).toTry.get
    decode[MeetupError.Multi](FileUtils.read(basePath + "/errors/group_error.json").get).toTry.get
    decode[MeetupError.Multi](FileUtils.read(basePath + "/errors/name_error.json").get).toTry.get
    decode[MeetupError.Multi](FileUtils.read(basePath + "/errors/scope_error.json").get).toTry.get
    decode[MeetupError.Multi](FileUtils.read(basePath + "/errors/multi.json").get).toTry.get
  }
}
