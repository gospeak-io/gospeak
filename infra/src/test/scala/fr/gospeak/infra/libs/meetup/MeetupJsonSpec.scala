package fr.gospeak.infra.libs.meetup

import fr.gospeak.libs.scalautils.FileUtils
import org.scalatest.{FunSpec, Matchers}
import io.circe.parser.decode
import fr.gospeak.infra.libs.meetup.MeetupJson._
import fr.gospeak.infra.libs.meetup.domain.MeetupGroup

class MeetupJsonSpec extends FunSpec with Matchers {
  private val basePath = "infra/src/test/resources/meetup"

  it("should parse group response") {
    decode[MeetupGroup](FileUtils.read(basePath + "/group.json").get).toTry.get
  }
}
