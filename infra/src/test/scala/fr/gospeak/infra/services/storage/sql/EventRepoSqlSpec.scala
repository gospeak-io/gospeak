package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import fr.gospeak.core.domain.Group
import fr.gospeak.infra.services.storage.sql.ContactRepoSqlSpec.{fields => contactFields, table => contactTable}
import fr.gospeak.infra.services.storage.sql.EventRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.PartnerRepoSqlSpec.{fields => partnerFields, table => partnerTable}
import fr.gospeak.infra.services.storage.sql.UserRepoSqlSpec.{fields => userFields, table => userTable}
import fr.gospeak.infra.services.storage.sql.VenueRepoSqlSpec.{fields => venueFields, table => venueTable}
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class EventRepoSqlSpec extends RepoSpec {
  describe("EventRepoSql") {
    it("should create and retrieve an event for a group") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      val (partner, venue) = createPartnerAndVenue(user, group).unsafeRunSync()
      val eventData = eventData1.copy(venue = eventData1.venue.map(_ => venue.id))
      eventRepo.list(group.id, params).unsafeRunSync().items shouldBe Seq()
      eventRepo.find(group.id, eventData.slug).unsafeRunSync() shouldBe None
      val event = eventRepo.create(group.id, eventData, user.id, now).unsafeRunSync()
      eventRepo.list(group.id, params).unsafeRunSync().items shouldBe Seq(event)
      eventRepo.find(group.id, eventData.slug).unsafeRunSync() shouldBe Some(event)
    }
    it("should fail to create an event when the group does not exists") {
      val user = userRepo.create(userData1, now).unsafeRunSync()
      an[Exception] should be thrownBy eventRepo.create(Group.Id.generate(), eventData1, user.id, now).unsafeRunSync()
    }
    it("should fail on duplicate slug for the same group") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      val (partner, venue) = createPartnerAndVenue(user, group).unsafeRunSync()
      val eventData = eventData1.copy(venue = eventData1.venue.map(_ => venue.id))
      eventRepo.create(group.id, eventData, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy eventRepo.create(group.id, eventData, user.id, now).unsafeRunSync()
    }
    describe("Queries") {
      it("should build insert") {
        val q = EventRepoSql.insert(event)
        check(q, s"INSERT INTO ${table.stripSuffix(" e")} (${mapFields(fields, _.stripPrefix("e."))}) VALUES (${mapFields(fields, _ => "?")})")
      }
      it("should build update") {
        val q = EventRepoSql.update(group.id, event.slug)(eventData1, user.id, now)
        check(q, s"UPDATE $table SET e.cfp_id=?, e.slug=?, e.name=?, e.start=?, e.max_attendee=?, e.description=?, e.venue=?, e.tags=?, e.meetupGroup=?, e.meetupEvent=?, e.updated=?, e.updated_by=? WHERE e.group_id=? AND e.slug=?")
      }
      it("should build updateCfp") {
        val q = EventRepoSql.updateCfp(group.id, event.slug)(cfp.id, user.id, now)
        check(q, s"UPDATE $table SET e.cfp_id=?, e.updated=?, e.updated_by=? WHERE e.group_id=? AND e.slug=?")
      }
      it("should build updateTalks") {
        val q = EventRepoSql.updateTalks(group.id, event.slug)(Seq(), user.id, now)
        check(q, s"UPDATE $table SET e.talks=?, e.updated=?, e.updated_by=? WHERE e.group_id=? AND e.slug=?")
      }
      it("should build updatePublished") {
        val q = EventRepoSql.updatePublished(group.id, event.slug)(user.id, now)
        check(q, s"UPDATE $table SET e.published=?, e.updated=?, e.updated_by=? WHERE e.group_id=? AND e.slug=?")
      }
      it("should build selectOne") {
        val q = EventRepoSql.selectOne(group.id, event.slug)
        check(q, s"SELECT $fields FROM $table WHERE e.group_id=? AND e.slug=?")
      }
      it("should build selectOnePublished") {
        val q = EventRepoSql.selectOnePublished(group.id, event.slug)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE e.group_id=? AND e.slug=? AND e.published IS NOT NULL")
      }
      it("should build selectPage") {
        val q = EventRepoSql.selectPage(group.id, params)
        check(q, s"SELECT $fields FROM $table WHERE e.group_id=? ORDER BY e.start IS NULL, e.start DESC OFFSET 0 LIMIT 20")
      }
      it("should build selectPagePublished") {
        val q = EventRepoSql.selectPagePublished(group.id, params)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE e.group_id=? AND e.published IS NOT NULL ORDER BY e.start IS NULL, e.start DESC OFFSET 0 LIMIT 20")
      }
      it("should build selectAll") {
        val q = EventRepoSql.selectAll(NonEmptyList.of(event.id))
        check(q, s"SELECT $fields FROM $table WHERE e.id IN (?) ")
      }
      it("should build selectAll for venue") {
        val q = EventRepoSql.selectAll(group.id, venue.id)
        check(q, s"SELECT $fields FROM $table WHERE e.group_id=? AND e.venue=? ")
      }
      it("should build selectAll for partner") {
        val q = EventRepoSql.selectAll(group.id, partner.id)
        check(q, s"SELECT $fieldsWithVenue FROM $tableWithVenue WHERE e.group_id=? AND v.partner_id=? ")
      }
      it("should build selectPageAfter") {
        val q = EventRepoSql.selectPageAfter(group.id, now, params)
        check(q, s"SELECT $fields FROM $table WHERE e.group_id=? AND e.start > ? ORDER BY e.start IS NULL, e.start DESC OFFSET 0 LIMIT 20")
      }
      it("should build selectTags") {
        val q = EventRepoSql.selectTags()
        check(q, s"SELECT e.tags FROM $table")
      }
      describe("rsvp") {
        it("should build insertRsvp") {
          val q = EventRepoSql.insertRsvp(rsvp)
          check(q, s"INSERT INTO ${rsvpTable.stripSuffix(" er")} (${mapFields(rsvpFields, _.stripPrefix("er."))}) VALUES (${mapFields(rsvpFields, _ => "?")})")
        }
        it("should build selectPageRsvps") {
          val q = EventRepoSql.selectPageRsvps(event.id, params)
          check(q, s"SELECT $rsvpFieldsWithUser FROM $rsvpTableWithUser WHERE er.event_id=? ORDER BY er.answered_at IS NULL, er.answered_at OFFSET 0 LIMIT 20")
        }
        it("should build selectOneRsvp") {
          val q = EventRepoSql.selectOneRsvp(event.id, user.id)
          check(q, s"SELECT $rsvpFieldsWithUser FROM $rsvpTableWithUser WHERE er.event_id=? AND er.user_id=?")
        }
      }
    }
  }
}

object EventRepoSqlSpec {

  import RepoSpec._

  val table = "events e"
  val fields: String = mapFields("id, group_id, cfp_id, slug, name, start, max_attendee, description, venue, talks, tags, published, meetupGroup, meetupEvent, created, created_by, updated, updated_by", "e." + _)

  private val tableWithVenue = s"$table LEFT OUTER JOIN $venueTable ON e.venue=v.id"
  private val fieldsWithVenue = s"$fields, $venueFields"

  private val tableFull = s"$tableWithVenue LEFT OUTER JOIN $partnerTable ON v.partner_id=pa.id LEFT OUTER JOIN $contactTable ON v.contact_id=ct.id"
  private val fieldsFull = s"$fieldsWithVenue, $partnerFields, $contactFields"

  private val rsvpTable = "event_rsvps er"
  private val rsvpFields = mapFields("event_id, user_id, answer, answered_at", "er." + _)
  private val rsvpTableWithUser = s"$rsvpTable INNER JOIN $userTable ON er.user_id=u.id"
  private val rsvpFieldsWithUser = s"${rsvpFields.replaceAll("er.user_id, ", "")}, $userFields"
}
