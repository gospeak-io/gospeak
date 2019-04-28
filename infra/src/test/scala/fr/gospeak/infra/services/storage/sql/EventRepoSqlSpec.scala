package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import fr.gospeak.core.domain.Group
import fr.gospeak.infra.services.storage.sql.EventRepoSql._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class EventRepoSqlSpec extends RepoSpec {
  private val fields = "id, group_id, cfp_id, slug, name, start, description, venue, talks, tags, created, created_by, updated, updated_by"

  describe("EventRepoSql") {
    it("should create and retrieve an event for a group") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      eventRepo.list(group.id, page).unsafeRunSync().items shouldBe Seq()
      eventRepo.find(group.id, eventData1.slug).unsafeRunSync() shouldBe None
      val event = eventRepo.create(group.id, eventData1, user.id, now).unsafeRunSync()
      eventRepo.list(group.id, page).unsafeRunSync().items shouldBe Seq(event)
      eventRepo.find(group.id, eventData1.slug).unsafeRunSync() shouldBe Some(event)
    }
    it("should fail to create an event when the group does not exists") {
      val user = userRepo.create(userData1, now).unsafeRunSync()
      an[Exception] should be thrownBy eventRepo.create(Group.Id.generate(), eventData1, user.id, now).unsafeRunSync()
    }
    it("should fail on duplicate slug for the same group") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      eventRepo.create(group.id, eventData1, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy eventRepo.create(group.id, eventData1, user.id, now).unsafeRunSync()
    }
    describe("Queries") {
      it("should build insert") {
        val q = insert(event)
        q.sql shouldBe s"INSERT INTO events ($fields) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
      it("should build update") {
        val q = update(group.id, event.slug)(eventData1, user.id, now)
        q.sql shouldBe "UPDATE events SET cfp_id=?, slug=?, name=?, start=?, tags=?, updated=?, updated_by=? WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build updateCfp") {
        val q = updateCfp(group.id, event.slug)(cfp.id, user.id, now)
        q.sql shouldBe "UPDATE events SET cfp_id=?, updated=?, updated_by=? WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build updateTalks") {
        val q = updateTalks(group.id, event.slug)(Seq(), user.id, now)
        q.sql shouldBe "UPDATE events SET talks=?, updated=?, updated_by=? WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build selectOne") {
        val q = selectOne(group.id, event.slug)
        q.sql shouldBe s"SELECT $fields FROM events WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build selectPage") {
        val (s, c) = selectPage(group.id, params)
        s.sql shouldBe s"SELECT $fields FROM events WHERE group_id=? ORDER BY start IS NULL, start DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM events WHERE group_id=? "
        check(s)
        check(c)
      }
      it("should build selectAll") {
        val q = selectAll(NonEmptyList.of(event.id))
        q.sql shouldBe s"SELECT $fields FROM events WHERE id IN (?) "
        check(q)
      }
      it("should build selectAllAfter") {
        val (s, c) = selectAllAfter(group.id, now, params)
        s.sql shouldBe s"SELECT $fields FROM events WHERE group_id=? AND start > ? ORDER BY start IS NULL, start DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM events WHERE group_id=? AND start > ? "
        check(s)
        check(c)
      }
    }
  }
}
