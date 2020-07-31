package gospeak.libs.meetup

import java.time.Instant
import java.time.temporal.ChronoUnit

import gospeak.libs.http.HttpClientImpl
import gospeak.libs.meetup.domain.{MeetupEvent, MeetupToken, MeetupVenue}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Geo, Secret}
import gospeak.libs.testingutils.BaseSpec

class MeetupClientSpec extends BaseSpec {
  private val redirectUri = "http://localhost:9000/u/groups/ht-paris/settings/meetup-oauth2"
  private val localConf = MeetupClient.Conf("...", Secret("..."))
  private val client = new MeetupClient(localConf, "http://localhost:9000", new HttpClientImpl, false)
  private val code = "..."
  private implicit val accessToken: MeetupToken.Access = MeetupToken.Access("...")
  private val refreshToken = "..."
  private val groupId = "HumanTalks-Paris"
  private val eventId = 262874292L
  private val venueId = 25982234L
  private val userId = 14321102L

  ignore("MeetupClient") {
    describe("auth") {
      it("should request an access token") {
        val token = client.requestAccessToken(redirectUri, code).unsafeRunSync()
        println(s"token: $token")
        token shouldBe a[Right[_, _]]
      }
    }
    describe("user") {
      it("should get logged user details") {
        val user = client.getLoggedUser().unsafeRunSync().get
        println(s"user: $user")
      }
      it("should get user details") {
        val user = client.getUser(userId).unsafeRunSync().get
        println(s"user: $user")
      }
      it("should get group users") {
        val users = client.getOrgas(groupId).unsafeRunSync().get
        println(s"users (${users.length}):\n${users.mkString("\n")}")
      }
      it("should get group user") {
        val user = client.getMember(groupId, userId).unsafeRunSync().get
        println(s"user: $user")
      }
    }
    describe("group") {
      it("should get group details") {
        val group = client.getGroup(groupId).unsafeRunSync().get
        println(s"group: $group")
        group.urlname shouldBe groupId
      }
    }
    describe("event") {
      it("should get event list") {
        val events = client.getEvents(groupId).unsafeRunSync().get
        println(s"events: $events")
        events should not be empty
      }
      it("should create, read, update and delete an event") {
        val event = MeetupEvent.Create(
          name = "Event name 3",
          description = "desc",
          time = Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli,
          event_hosts = Some(s"$userId"))
        val created = client.createEvent(groupId, event).unsafeRunSync().get
        println(s"created: $created")
        val read = client.getEvent(groupId, created.id).unsafeRunSync().get
        read shouldBe created
        val updated = client.updateEvent(groupId, created.id, event.copy(description = "aaa")).unsafeRunSync().get
        println(s"updated: $updated")
        client.deleteEvent(groupId, created.id).unsafeRunSync().get
      }
      it("should get event details") {
        val event = client.getEvent(groupId, eventId).unsafeRunSync().get
        println(s"event: $event")
        event.id shouldBe eventId
      }
      it("should get attendees") {
        val attendees = client.getEventAttendees(groupId, eventId).unsafeRunSync().get
        println(s"attendees (${attendees.length}):\n${attendees.mkString("\n")}")
      }
    }
    describe("venue") {
      it("should get venue list") {
        val venues = client.getVenues(groupId).unsafeRunSync().get
        println(s"venues (${venues.length}):\n${venues.mkString("\n")}")
        venues should not be empty
      }
      it("should create, read, update and delete a venue") {
        val venue = MeetupVenue.Create(
          name = "Test venue",
          address_1 = "119 rue des Pyrénées",
          city = "Paris",
          state = None,
          country = "fr",
          localized_country_name = "France",
          lat = 48.87651,
          lon = 2.310031,
          repinned = false,
          visibility = "public")
        val created = client.createVenue(groupId, venue).unsafeRunSync().get
        println(s"created: $created") // 26607477
        val read = client.getVenue(groupId, created.id).unsafeRunSync().get
        read shouldBe created
        val updated = client.updateVenue(groupId, created.id, venue.copy(name = "Test venue 2")).unsafeRunSync().get
        println(s"updated: $updated")
        client.deleteVenue(groupId, created.id).unsafeRunSync().get
      }
    }
    describe("locations") {
      it("should fetch locations") {
        val locations = client.getLocations(Geo(48.8716827, 2.3070390000000316)).unsafeRunSync().get
        println(s"locations (${locations.length}):\n${locations.mkString("\n")}")
      }
    }
  }
}
