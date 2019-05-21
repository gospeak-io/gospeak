package fr.gospeak.infra.libs.meetup

import fr.gospeak.infra.libs.meetup.domain.MeetupKey
import org.scalatest.{FunSpec, Matchers}

class MeetupClientSpec extends FunSpec with Matchers {
  private val apiKey = MeetupKey("...")
  private val client = new MeetupClient

  ignore("MeetupClient") {
    it("should get info") {
      val res = client.dashboard(apiKey).unsafeRunSync()
      println(s"res: $res")
    }
    it("should get group info") {
      val res = client.group("HumanTalks-Paris").unsafeRunSync()
      println(s"res: $res")
    }
    it("should get group events") {
      val res = client.events("HumanTalks-Paris").unsafeRunSync()
      println(s"res: $res")
    }
  }
}
