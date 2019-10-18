package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import fr.gospeak.core.domain.{Group, Talk}
import fr.gospeak.infra.services.storage.sql.CfpRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class CfpRepoSqlSpec extends RepoSpec {
  describe("CfpRepoSql") {
    it("should create and retrieve a cfp for a group") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      val talkId = Talk.Id.generate()
      cfpRepo.find(cfpData1.slug).unsafeRunSync() shouldBe None
      cfpRepo.list(group.id, params).unsafeRunSync().items shouldBe Seq()
      cfpRepo.availableFor(talkId, params).unsafeRunSync().items shouldBe Seq()
      val cfp = cfpRepo.create(group.id, cfpData1, user.id, now).unsafeRunSync()
      cfpRepo.find(cfp.id).unsafeRunSync().get shouldBe cfp
      cfpRepo.find(cfpData1.slug).unsafeRunSync().get shouldBe cfp
      cfpRepo.list(group.id, params).unsafeRunSync().items shouldBe Seq(cfp)
      cfpRepo.availableFor(talkId, params).unsafeRunSync().items shouldBe Seq(cfp)
    }
    it("should fail to create a cfp when the group does not exists") {
      val user = userRepo.create(userData1, now).unsafeRunSync()
      an[Exception] should be thrownBy cfpRepo.create(Group.Id.generate(), cfpData1, user.id, now).unsafeRunSync()
    }
    it("should fail to create two cfp for a group") {
      val (user, group) = createUserAndGroup().unsafeRunSync()
      cfpRepo.create(group.id, cfpData1, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy cfpRepo.create(group.id, cfpData1, user.id, now).unsafeRunSync()
    }
    it("should fail on duplicate slug") {
      val (user, group1) = createUserAndGroup().unsafeRunSync()
      val group2 = groupRepo.create(groupData2, user.id, now).unsafeRunSync()
      cfpRepo.create(group1.id, cfpData1, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy cfpRepo.create(group2.id, cfpData1, user.id, now).unsafeRunSync()
    }
    describe("Queries") {
      it("should build insert") {
        val q = CfpRepoSql.insert(cfp)
        q.sql shouldBe s"INSERT INTO $table ($fields) VALUES (${mapFields(fields, _ => "?")})"
        check(q)
      }
      it("should build update") {
        val q = CfpRepoSql.update(group.id, cfp.slug)(cfpData1, user.id, now)
        q.sql shouldBe s"UPDATE $table SET slug=?, name=?, begin=?, close=?, description=?, tags=?, updated=?, updated_by=? WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build selectOne for cfp id") {
        val q = CfpRepoSql.selectOne(cfp.id)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE id=?"
        check(q)
      }
      it("should build selectOne for cfp slug") {
        val q = CfpRepoSql.selectOne(cfp.slug)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE slug=?"
        check(q)
      }
      it("should build selectOne for group id and cfp slug") {
        val q = CfpRepoSql.selectOne(group.id, cfp.slug)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE group_id=? AND slug=?"
        check(q)
      }
      it("should build selectOne for event id") {
        val q = CfpRepoSql.selectOne(event.id)
        q.sql shouldBe s"SELECT ${mapFields(fields, "c." + _)} FROM $table c INNER JOIN events e ON e.cfp_id=c.id WHERE e.id=?"
        check(q)
      }
      it("should build selectOne for cfp slug id and date") {
        val q = CfpRepoSql.selectOne(cfp.slug, now)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE (begin IS NULL OR begin < ?) AND (close IS NULL OR close > ?) AND slug=?"
        check(q)
      }
      it("should build selectPage for a group") {
        val q = CfpRepoSql.selectPage(group.id, params)
        q.query.sql shouldBe s"SELECT $fields FROM $table WHERE group_id=? ORDER BY close IS NULL, close DESC, name IS NULL, name OFFSET 0 LIMIT 20"
        q.count.sql shouldBe s"SELECT count(*) FROM $table WHERE group_id=? "
        check(q.query)
        check(q.count)
      }
      it("should build selectPage for a talk") {
        val q = CfpRepoSql.selectPage(talk.id, params)
        q.query.sql shouldBe s"SELECT $fields FROM $table WHERE id NOT IN (SELECT cfp_id FROM proposals WHERE talk_id=?) ORDER BY close IS NULL, close DESC, name IS NULL, name OFFSET 0 LIMIT 20"
        q.count.sql shouldBe s"SELECT count(*) FROM $table WHERE id NOT IN (SELECT cfp_id FROM proposals WHERE talk_id=?) "
        check(q.query)
        check(q.count)
      }
      it("should build selectPage for a date") {
        val q = CfpRepoSql.selectPage(now, params)
        q.query.sql shouldBe s"SELECT $fields FROM $table WHERE (begin IS NULL OR begin < ?) AND (close IS NULL OR close > ?) ORDER BY close IS NULL, close DESC, name IS NULL, name OFFSET 0 LIMIT 20"
        q.count.sql shouldBe s"SELECT count(*) FROM $table WHERE (begin IS NULL OR begin < ?) AND (close IS NULL OR close > ?) "
        check(q.query)
        check(q.count)
      }
      it("should build selectAll for group id") {
        val q = CfpRepoSql.selectAll(group.id)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE group_id=?"
        check(q)
      }
      it("should build selectAll for cfp ids") {
        val q = CfpRepoSql.selectAll(NonEmptyList.of(cfp.id, cfp.id, cfp.id))
        q.sql shouldBe s"SELECT $fields FROM $table WHERE id IN (?, ?, ?) "
        check(q)
      }
      it("should build selectAll for group and date") {
        val q = CfpRepoSql.selectAll(group.id, now)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE (begin IS NULL OR begin < ?) AND (close IS NULL OR close > ?) AND group_id=?"
        check(q)
      }
      it("should build selectTags") {
        val q = CfpRepoSql.selectTags()
        q.sql shouldBe s"SELECT tags FROM $table"
        check(q)
      }
    }
  }
}

object CfpRepoSqlSpec {
  val table = "cfps"
  val fields = "id, group_id, slug, name, begin, close, description, tags, created, created_by, updated, updated_by"
}
