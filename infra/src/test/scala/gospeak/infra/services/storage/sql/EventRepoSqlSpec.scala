package gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import gospeak.core.domain.Event
import gospeak.core.domain.messages.Message
import gospeak.core.domain.utils.FakeCtx
import gospeak.infra.services.storage.sql.CfpRepoSqlSpec.{fields => cfpFields, table => cfpTable}
import gospeak.infra.services.storage.sql.ContactRepoSqlSpec.{fields => contactFields, table => contactTable}
import gospeak.infra.services.storage.sql.EventRepoSqlSpec._
import gospeak.infra.services.storage.sql.GroupRepoSqlSpec.{memberTable, fields => groupFields, table => groupTable}
import gospeak.infra.services.storage.sql.PartnerRepoSqlSpec.{fields => partnerFields, table => partnerTable}
import gospeak.infra.services.storage.sql.UserRepoSqlSpec.{fields => userFields, table => userTable}
import gospeak.infra.services.storage.sql.VenueRepoSqlSpec.{fields => venueFields, table => venueTable}
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.libs.scala.domain.LiquidMarkdown

class EventRepoSqlSpec extends RepoSpec {
  describe("EventRepoSql") {
    it("should create and retrieve an event for a group") {
      val (user, group, ctx) = createUserAndGroup().unsafeRunSync()
      val (partner, venue, contact) = createPartnerAndVenue(user, group)(ctx).unsafeRunSync()
      val eventData = eventData1.copy(venue = eventData1.venue.map(_ => venue.id))
      eventRepo.list(params)(ctx).unsafeRunSync().items shouldBe Seq()
      eventRepo.find(eventData.slug)(ctx).unsafeRunSync() shouldBe None
      val event = eventRepo.create(eventData)(ctx).unsafeRunSync()
      eventRepo.list(params)(ctx).unsafeRunSync().items shouldBe Seq(event)
      eventRepo.find(eventData.slug)(ctx).unsafeRunSync() shouldBe Some(event)
    }
    it("should fail to create an event when the group does not exists") {
      val user = userRepo.create(userData1, now, None).unsafeRunSync()
      val ctx = FakeCtx(now, user, group)
      an[Exception] should be thrownBy eventRepo.create(eventData1)(ctx).unsafeRunSync()
    }
    it("should fail on duplicate slug for the same group") {
      val (user, group, ctx) = createUserAndGroup().unsafeRunSync()
      val (partner, venue, contact) = createPartnerAndVenue(user, group)(ctx).unsafeRunSync()
      val eventData = eventData1.copy(venue = eventData1.venue.map(_ => venue.id))
      eventRepo.create(eventData)(ctx).unsafeRunSync()
      an[Exception] should be thrownBy eventRepo.create(eventData)(ctx).unsafeRunSync()
    }
    describe("Queries") {
      it("should build insert") {
        val q = EventRepoSql.insert(event)
        check(q, s"INSERT INTO ${table.stripSuffix(" e")} (${mapFields(fields, _.stripPrefix("e."))}) VALUES (${mapFields(fields, _ => "?")})")
      }
      it("should build update") {
        val q = EventRepoSql.update(group.id, event.slug)(eventData1, user.id, now)
        check(q, s"UPDATE $table SET cfp_id=?, slug=?, name=?, kind=?, start=?, max_attendee=?, allow_rsvp=?, description=?, venue=?, tags=?, meetupGroup=?, meetupEvent=?, updated_at=?, updated_by=? WHERE e.group_id=? AND e.slug=?")
      }
      it("should build updateDescription") {
        val q = EventRepoSql.updateDescription(event.id)(LiquidMarkdown[Message.EventInfo](""))
        check(q, s"UPDATE $table SET description=? WHERE e.id=?")
      }
      it("should build updateNotes") {
        val q = EventRepoSql.updateNotes(group.id, event.slug)("notes", user.id, now)
        check(q, s"UPDATE $table SET orga_notes=?, orga_notes_updated_at=?, orga_notes_updated_by=? WHERE e.group_id=? AND e.slug=?")
      }
      it("should build updateCfp") {
        val q = EventRepoSql.updateCfp(group.id, event.slug)(cfp.id, user.id, now)
        check(q, s"UPDATE $table SET cfp_id=?, updated_at=?, updated_by=? WHERE e.group_id=? AND e.slug=?")
      }
      it("should build updateTalks") {
        val q = EventRepoSql.updateTalks(group.id, event.slug)(Seq(), user.id, now)
        check(q, s"UPDATE $table SET talks=?, updated_at=?, updated_by=? WHERE e.group_id=? AND e.slug=?")
      }
      it("should build updatePublished") {
        val q = EventRepoSql.updatePublished(group.id, event.slug)(user.id, now)
        check(q, s"UPDATE $table SET published=?, updated_at=?, updated_by=? WHERE e.group_id=? AND e.slug=?")
      }
      it("should build selectOne by id") {
        val q = EventRepoSql.selectOne(event.id)
        check(q, s"SELECT $fields FROM $table WHERE e.id=? $orderBy LIMIT 1")
      }
      it("should build selectOne by group and slug") {
        val q = EventRepoSql.selectOne(group.id, event.slug)
        check(q, s"SELECT $fields FROM $table WHERE e.group_id=? AND e.slug=? $orderBy LIMIT 1")
      }
      it("should build selectOneFull with group id") {
        val q = EventRepoSql.selectOneFull(group.id, event.slug)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE e.group_id=? AND e.slug=? $orderBy LIMIT 1")
      }
      it("should build selectOneFull with group slug") {
        val q = EventRepoSql.selectOneFull(group.slug, event.slug)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE g.slug=? AND e.slug=? AND (e.published IS NOT NULL OR g.owners LIKE ?) $orderBy LIMIT 1")
      }
      it("should build selectOnePublished") {
        val q = EventRepoSql.selectOnePublished(group.id, event.slug)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE e.group_id=? AND e.slug=? AND e.published IS NOT NULL $orderBy LIMIT 1")
      }
      it("should build selectPage") {
        val q = EventRepoSql.selectPage(params)
        check(q, s"SELECT $fields FROM $table WHERE e.group_id=? $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectPageFull") {
        val q = EventRepoSql.selectPageFull(params)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE e.group_id=? $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectAllPublishedSlugs") {
        val q = EventRepoSql.selectAllPublishedSlugs()
        check(q, s"SELECT e.group_id, e.slug FROM $table WHERE e.published IS NOT NULL $orderBy")
      }
      it("should build selectPagePublished") {
        val q = EventRepoSql.selectPagePublished(group.id, params)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE e.group_id=? AND e.published IS NOT NULL $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectAllFromGroups") {
        val q = EventRepoSql.selectAllFromGroups(NonEmptyList.of(group.id))(adminCtx)
        check(q, s"SELECT $fields FROM $table WHERE e.group_id IN (?)  $orderBy")
      }
      it("should build selectAll") {
        val q = EventRepoSql.selectAll(NonEmptyList.of(event.id))
        check(q, s"SELECT $fields FROM $table WHERE e.id IN (?)  $orderBy")
      }
      it("should build selectAll for venue") {
        val q = EventRepoSql.selectAll(group.id, venue.id)
        check(q, s"SELECT $fields FROM $table WHERE e.group_id=? AND e.venue=? $orderBy")
      }
      it("should build selectAll for partner") {
        val q = EventRepoSql.selectAll(group.id, partner.id)
        check(q, s"SELECT $fieldsWithVenue FROM $tableWithVenue WHERE e.group_id=? AND v.partner_id=? $orderBy")
      }
      it("should build selectPageAfterFull") {
        val q = EventRepoSql.selectPageAfterFull(params)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE e.group_id=? AND e.start > ? $orderBy LIMIT 20 OFFSET 0", checkCount = false)
      }
      it("should build selectPageIncoming") {
        val q = EventRepoSql.selectPageIncoming(params)
        check(q, s"SELECT $fieldsFullWithMemberAndRsvp FROM $tableFullWithMemberAndRsvp WHERE e.start > ? AND e.published IS NOT NULL AND gm.user_id=? $orderBy LIMIT 20 OFFSET 0", checkCount = false)
      }
      it("should build selectTags") {
        val q = EventRepoSql.selectTags()
        check(q, s"SELECT e.tags FROM $table $orderBy")
      }
      describe("rsvp") {
        it("should build countRsvp") {
          val q = EventRepoSql.countRsvp(event.id, rsvp.answer)
          check(q, s"SELECT COUNT(*) FROM $rsvpTable WHERE er.event_id=? AND er.answer=? GROUP BY er.event_id, er.answer ORDER BY er.event_id IS NULL, er.event_id LIMIT 1")
        }
        it("should build insertRsvp") {
          val q = EventRepoSql.insertRsvp(rsvp)
          check(q, s"INSERT INTO ${rsvpTable.stripSuffix(" er")} (${mapFields(rsvpFields, _.stripPrefix("er."))}) VALUES (${mapFields(rsvpFields, _ => "?")})")
        }
        it("should build updateRsvp") {
          val q = EventRepoSql.updateRsvp(event.id, user.id, rsvp.answer, now)
          check(q, s"UPDATE $rsvpTable SET answer=?, answered_at=? WHERE er.event_id=? AND er.user_id=?")
        }
        it("should build selectAllRsvp") {
          val q = EventRepoSql.selectAllRsvp(event.id)
          check(q, s"SELECT $rsvpFieldsWithUser FROM $rsvpTableWithUser WHERE er.event_id=? $rsvpOrderBy")
        }
        it("should build selectAllRsvp with answers") {
          val q = EventRepoSql.selectAllRsvp(event.id, NonEmptyList.of(rsvp.answer))
          check(q, s"SELECT $rsvpFieldsWithUser FROM $rsvpTableWithUser WHERE er.event_id=? AND er.answer IN (?)  $rsvpOrderBy")
        }
        it("should build selectOneRsvp") {
          val q = EventRepoSql.selectOneRsvp(event.id, user.id)
          check(q, s"SELECT $rsvpFieldsWithUser FROM $rsvpTableWithUser WHERE er.event_id=? AND er.user_id=? $rsvpOrderBy")
        }
        it("should build selectFirstRsvp") {
          val q = EventRepoSql.selectFirstRsvp(event.id, Event.Rsvp.Answer.Wait)
          check(q, s"SELECT $rsvpFieldsWithUser FROM $rsvpTableWithUser WHERE er.event_id=? AND er.answer=? ORDER BY er.answered_at IS NULL, er.answered_at LIMIT 1")
        }
      }
    }
  }
}

object EventRepoSqlSpec {

  import RepoSpec._

  val table = "events e"
  val fields: String = mapFields("id, group_id, cfp_id, slug, name, kind, start, max_attendee, allow_rsvp, description, orga_notes, orga_notes_updated_at, orga_notes_updated_by, venue, talks, tags, published, meetupGroup, meetupEvent, created_at, created_by, updated_at, updated_by", "e." + _)
  val orderBy = "ORDER BY e.start IS NULL, e.start DESC"

  private val tableWithVenue = s"$table LEFT OUTER JOIN $venueTable ON e.venue=v.id"
  private val fieldsWithVenue = s"$fields, $venueFields"

  private val tableFull = s"$tableWithVenue LEFT OUTER JOIN $partnerTable ON v.partner_id=pa.id LEFT OUTER JOIN $contactTable ON v.contact_id=ct.id LEFT OUTER JOIN $cfpTable ON e.cfp_id=c.id INNER JOIN $groupTable ON e.group_id=g.id"
  private val fieldsFull = s"$fieldsWithVenue, $partnerFields, $contactFields, $cfpFields, $groupFields"

  private val rsvpTable = "event_rsvps er"
  private val rsvpFields = mapFields("event_id, user_id, answer, answered_at", "er." + _)
  private val rsvpOrderBy = "ORDER BY er.answered_at IS NULL, er.answered_at"
  private val rsvpTableWithUser = s"$rsvpTable INNER JOIN $userTable ON er.user_id=u.id"
  private val rsvpFieldsWithUser = s"${rsvpFields.replaceAll("er.user_id, ", "")}, $userFields"

  private val tableFullWithMemberAndRsvp = s"$tableFull INNER JOIN $memberTable ON g.id=gm.group_id LEFT OUTER JOIN $rsvpTable ON e.id=er.event_id AND gm.user_id=er.user_id LEFT OUTER JOIN $userTable ON er.user_id=u.id"
  private val fieldsFullWithMemberAndRsvp = s"$fieldsFull, $rsvpFieldsWithUser"
}
