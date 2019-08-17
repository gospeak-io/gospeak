package fr.gospeak.infra.libs.meetup

import java.time.Instant
import java.time.temporal.ChronoUnit

import fr.gospeak.infra.libs.meetup.MeetupClient.Conf
import fr.gospeak.infra.libs.meetup.domain.{MeetupEvent, MeetupToken}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.Secret
import org.scalatest.{FunSpec, Matchers}

class MeetupClientSpec extends FunSpec with Matchers {
  private val redirectUri = "http://localhost:9000/u/groups/ht-paris/settings/meetup-oauth2"
  private val localConf = Conf("...", Secret("..."), "http://localhost:9000")
  private val client = new MeetupClient(localConf)
  private val code = "..."
  private implicit val accessToken: MeetupToken.Access = MeetupToken.Access("...")
  private val refreshToken = "..."
  private val groupId = "HumanTalks-Paris"
  private val eventId = "262874292"
  private val venueId = 25982234L
  private val userId = 14321102L

  ignore("MeetupClient") {
    ignore("should request an access token") {
      val token = client.requestAccessToken(redirectUri, code).unsafeRunSync()
      println(s"token: $token")
      token shouldBe a[Right[_, _]]
    }
    it("should get logged user details") {
      client.getLoggedUser()(MeetupToken.Access("aaa")).unsafeRunSync() shouldBe a[Left[_, _]]
      val user = client.getLoggedUser().unsafeRunSync().get
      println(s"user: $user")
    }
    it("should get group details") {
      client.getGroup("aaa").unsafeRunSync() shouldBe a[Left[_, _]]
      val group = client.getGroup(groupId).unsafeRunSync().get
      println(s"group: $group")
      group.urlname shouldBe groupId
    }
    it("should get event list") {
      client.getEvents("aaa").unsafeRunSync() shouldBe a[Left[_, _]]
      val events = client.getEvents(groupId).unsafeRunSync().get
      println(s"events: $events")
      events should not be empty
    }
    it("should create an event") {
      val created = client.createEvent(groupId, MeetupEvent.Create(
        name = "Test event",
        description =
          """A <b>nice event</b>, just to <i>test</i> publishing from <a href="https://github.com/loicknuchel/gospeak" target="_blank">gospeak</a>.
            |
            |Hoping description supports lists:
            |- item 1
            |- item 2
            |- item 3
            |
            |https://blog.humancoders.com/wp-content/uploads/2012/11/tumblr_md28i6bE1X1qcyigs.png
            |""".stripMargin,
        time = Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli,
        event_hosts = Some(s"$userId"))).unsafeRunSync()
      println(s"status: ${created.status}")
      println(s"created: ${created.body}")
      println(s"headers: ${created.headers.map { case (key, value) => s"$key=$value" }.mkString("\n")}")
      // {"errors":[{"code":"authentication_error","message":"Authenticated member required"}]}
      created.status shouldBe 201
    }
    it("should get event details") {
      client.getEvent(groupId, "aaa").unsafeRunSync() shouldBe a[Left[_, _]]
      val event = client.getEvent(groupId, eventId).unsafeRunSync().get
      println(s"event: $event")
      event.id shouldBe eventId
    }
    it("should get venue list") {
      client.getVenues("aaa").unsafeRunSync() shouldBe a[Left[_, _]]
      val venues = client.getVenues(groupId).unsafeRunSync().get
      println(s"venues: $venues")
      venues should not be empty
    }
  }
}
