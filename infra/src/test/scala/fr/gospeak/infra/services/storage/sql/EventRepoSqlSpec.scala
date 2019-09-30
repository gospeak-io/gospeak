package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import fr.gospeak.core.domain.Group
import fr.gospeak.infra.services.storage.sql.ContactRepoSqlSpec.{fields => contactFields, table => contactTable}
import fr.gospeak.infra.services.storage.sql.EventRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.PartnerRepoSqlSpec.{fields => partnerFields, table => partnerTable}
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
        q.sql shouldBe s"INSERT INTO $table ($fields) VALUES (${mapFields(fields, _ => "?")})"
        check(q)
      }
      it("should build update") {
        val q = EventRepoSql.update(group.id, event.slug)(eventData1, user.id, now)
        q.sql shouldBe s"UPDATE $table SET cfp_id=?, slug=?, name=?, start=?, description=?, venue=?, tags=?, meetupGroup=?, meetupEvent=?, updated=?, updated_by=? WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build updateCfp") {
        val q = EventRepoSql.updateCfp(group.id, event.slug)(cfp.id, user.id, now)
        q.sql shouldBe s"UPDATE $table SET cfp_id=?, updated=?, updated_by=? WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build updateTalks") {
        val q = EventRepoSql.updateTalks(group.id, event.slug)(Seq(), user.id, now)
        q.sql shouldBe s"UPDATE $table SET talks=?, updated=?, updated_by=? WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build selectOne") {
        val q = EventRepoSql.selectOne(group.id, event.slug)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build selectOnePublished") {
        val q = EventRepoSql.selectOnePublished(group.id, event.slug)
        q.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE e.group_id=? AND e.slug=? AND e.published IS NOT NULL"
        check(q)
      }
      it("should build selectPage") {
        val (s, c) = EventRepoSql.selectPage(group.id, params)
        s.sql shouldBe s"SELECT $fields FROM $table WHERE group_id=? ORDER BY start IS NULL, start DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe s"SELECT count(*) FROM $table WHERE group_id=? "
        check(s)
        check(c)
      }
      it("should build selectPagePublished") {
        val (s, c) = EventRepoSql.selectPagePublished(group.id, params)
        s.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE e.group_id=? AND e.published IS NOT NULL ORDER BY e.start IS NULL, e.start DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe s"SELECT count(*) FROM $tableFull WHERE e.group_id=? AND e.published IS NOT NULL "
        check(s)
        check(c)
      }
      it("should build selectAll for venue") {
        val q = EventRepoSql.selectAll(group.id, venue.id)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE group_id=? AND venue=? "
        check(q)
      }
      it("should build selectAll for partner") {
        val q = EventRepoSql.selectAll(group.id, partner.id)
        q.sql shouldBe s"SELECT $fieldsWithVenue FROM $tableWithVenue WHERE e.group_id=? AND v.partner_id=? "
        check(q)
      }
      it("should build selectAll") {
        val q = EventRepoSql.selectAll(NonEmptyList.of(event.id))
        q.sql shouldBe s"SELECT $fields FROM $table WHERE id IN (?) "
        check(q)
      }
      it("should build selectAllAfter") {
        val (s, c) = EventRepoSql.selectAllAfter(group.id, now, params)
        s.sql shouldBe s"SELECT $fields FROM $table WHERE group_id=? AND start > ? ORDER BY start IS NULL, start DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe s"SELECT count(*) FROM $table WHERE group_id=? AND start > ? "
        check(s)
        check(c)
      }
    }
  }
}

object EventRepoSqlSpec {

  import RepoSpec._

  val table = "events"
  val fields = "id, group_id, cfp_id, slug, name, start, description, venue, talks, tags, published, meetupGroup, meetupEvent, created, created_by, updated, updated_by"

  private val tableWithVenue = s"$table e LEFT OUTER JOIN venues v ON e.venue=v.id"
  private val fieldsWithVenue = s"${mapFields(fields, "e." + _)}, ${mapFields(venueFields, "v." + _)}"

  private val tableFull = s"$table e LEFT OUTER JOIN $venueTable v ON e.venue=v.id LEFT OUTER JOIN $partnerTable p ON v.partner_id=p.id LEFT OUTER JOIN $contactTable c ON v.contact_id=c.id"
  private val fieldsFull = s"${mapFields(fields, "e." + _)}, ${mapFields(venueFields, "v." + _)}, ${mapFields(partnerFields, "p." + _)}, ${mapFields(contactFields, "c." + _)}"
}
