package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import fr.gospeak.core.domain.{Cfp, Proposal, Talk}
import fr.gospeak.infra.services.storage.sql.CfpRepoSqlSpec.{fields => cfpFields, table => cfpTable}
import fr.gospeak.infra.services.storage.sql.EventRepoSqlSpec.{fields => eventFields, table => eventTable}
import fr.gospeak.infra.services.storage.sql.GroupRepoSqlSpec.{fields => groupFields, table => groupTable}
import fr.gospeak.infra.services.storage.sql.ProposalRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.TalkRepoSqlSpec.{fields => talkFields, table => talkTable}
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class ProposalRepoSqlSpec extends RepoSpec {
  describe("ProposalRepoSql") {
    it("should create and retrieve a proposal for a group and talk") {
      val (user, group, cfp, talk) = createUserGroupCfpAndTalk().unsafeRunSync()
      proposalRepo.listFull(talk.id, params).unsafeRunSync().items shouldBe Seq()
      proposalRepo.listFull(cfp.id, params).unsafeRunSync().items shouldBe Seq()
      val proposal = proposalRepo.create(talk.id, cfp.id, proposalData1, speakers, user.id, now).unsafeRunSync()
      proposalRepo.listFull(talk.id, params).unsafeRunSync().items shouldBe Seq(Proposal.Full(proposal, cfp, group, talk, None))
      proposalRepo.listFull(cfp.id, params).unsafeRunSync().items shouldBe Seq(Proposal.Full(proposal, cfp, group, talk, None))
      proposalRepo.find(cfp.slug, proposal.id).unsafeRunSync() shouldBe Some(proposal)
    }
    it("should fail to create a proposal when talk does not exists") {
      val user = userRepo.create(userData1, now).unsafeRunSync()
      val group = groupRepo.create(groupData1, user.id, now).unsafeRunSync()
      val cfp = cfpRepo.create(group.id, cfpData1, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy proposalRepo.create(Talk.Id.generate(), cfp.id, proposalData1, speakers, user.id, now).unsafeRunSync()
    }
    it("should fail to create a proposal when cfp does not exists") {
      val user = userRepo.create(userData1, now).unsafeRunSync()
      val talk = talkRepo.create(talkData1, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy proposalRepo.create(talk.id, Cfp.Id.generate(), proposalData1, speakers, user.id, now).unsafeRunSync()
    }
    it("should fail on duplicate cfp and talk") {
      val (user, _, cfp, talk) = createUserGroupCfpAndTalk().unsafeRunSync()
      proposalRepo.create(talk.id, cfp.id, proposalData1, speakers, user.id, now).unsafeRunSync()
      an[Exception] should be thrownBy proposalRepo.create(talk.id, cfp.id, proposalData1, speakers, user.id, now).unsafeRunSync()
    }
    describe("Queries") {
      it("should build insert") {
        val q = ProposalRepoSql.insert(proposal)
        q.sql shouldBe s"INSERT INTO $table ($fields) VALUES (${mapFields(fields, _ => "?")})"
        check(q)
      }
      it("should build update for orga, group, cfp and proposal") {
        val q = ProposalRepoSql.update(user.id, group.slug, cfp.slug, proposal.id)(proposal.data, now)
        q.sql shouldBe s"UPDATE $table SET title=?, duration=?, description=?, slides=?, video=?, tags=?, updated=?, updated_by=? WHERE id=$whereGroupAndCfp"
        check(q)
      }
      it("should build update for speaker, talk and cfp") {
        val q = ProposalRepoSql.update(user.id, talk.slug, cfp.slug)(proposal.data, now)
        q.sql shouldBe s"UPDATE $table SET title=?, duration=?, description=?, slides=?, video=?, tags=?, updated=?, updated_by=? WHERE id=$whereCfpAndTalk"
        check(q)
      }
      it("should build updateStatus") {
        val q = ProposalRepoSql.updateStatus(cfp.slug, proposal.id)(proposal.status, None)
        q.sql shouldBe s"UPDATE $table SET status=?, event_id=? WHERE id=$whereCfp"
        check(q)
      }
      it("should build updateSlides by cfp and proposal") {
        val q = ProposalRepoSql.updateSlides(cfp.slug, proposal.id)(slides, user.id, now)
        q.sql shouldBe s"UPDATE $table SET slides=?, updated=?, updated_by=? WHERE id=$whereCfp"
        check(q)
      }
      it("should build updateSlides by speaker, talk and cfp") {
        val q = ProposalRepoSql.updateSlides(user.id, talk.slug, cfp.slug)(slides, user.id, now)
        q.sql shouldBe s"UPDATE $table SET slides=?, updated=?, updated_by=? WHERE id=$whereCfpAndTalk"
        check(q)
      }
      it("should build updateVideo by cfp and proposal") {
        val q = ProposalRepoSql.updateVideo(cfp.slug, proposal.id)(video, user.id, now)
        q.sql shouldBe s"UPDATE $table SET video=?, updated=?, updated_by=? WHERE id=$whereCfp"
        check(q)
      }
      it("should build updateVideo by speaker, talk and cfp") {
        val q = ProposalRepoSql.updateVideo(user.id, talk.slug, cfp.slug)(video, user.id, now)
        q.sql shouldBe s"UPDATE $table SET video=?, updated=?, updated_by=? WHERE id=$whereCfpAndTalk"
        check(q)
      }
      it("should build updateSpeakers by id") {
        val q = ProposalRepoSql.updateSpeakers(proposal.id)(proposal.speakers, user.id, now)
        q.sql shouldBe s"UPDATE $table SET speakers=?, updated=?, updated_by=? WHERE id=?"
        check(q)
      }
      it("should build selectOne for proposal id") {
        val q = ProposalRepoSql.selectOne(proposal.id)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE id=?"
        check(q)
      }
      it("should build selectOne for cfp and proposal id") {
        val q = ProposalRepoSql.selectOne(cfp.slug, proposal.id)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE id=$whereCfp"
        check(q)
      }
      it("should build selectOne for speaker, talk and cfp") {
        val q = ProposalRepoSql.selectOne(user.id, talk.slug, cfp.slug)
        q.sql shouldBe s"SELECT $fields FROM $table WHERE id=$whereCfpAndTalk"
        check(q)
      }
      it("should build selectOneFull for id") {
        val q = ProposalRepoSql.selectOneFull(proposal.id)
        q.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE p.id=?"
        check(q)
      }
      it("should build selectOneFull for talk, cfp and speaker") {
        val q = ProposalRepoSql.selectOneFull(talk.slug, cfp.slug, user.id)
        q.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE t.slug=? AND c.slug=? AND p.speakers LIKE ?"
        check(q)
      }
      it("should build selectOnePublicFull for id") {
        val q = ProposalRepoSql.selectOnePublicFull(group.id, proposal.id)
        q.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE c.group_id=? AND p.id=? AND e.published IS NOT NULL"
        check(q)
      }
      it("should build selectPage for a cfp and status") {
        val q = ProposalRepoSql.selectPage(cfp.id, Proposal.Status.Pending, params)
        q.query.sql shouldBe s"SELECT $fields FROM $table WHERE cfp_id=? AND status=? ORDER BY created IS NULL, created DESC OFFSET 0 LIMIT 20"
        q.count.sql shouldBe s"SELECT count(*) FROM $table WHERE cfp_id=? AND status=? "
        check(q.query)
        check(q.count)
      }
      it("should build selectPageFull for a talk") {
        val q = ProposalRepoSql.selectPageFull(talk.id, params)
        q.query.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE p.talk_id=? ORDER BY p.created IS NULL, p.created DESC OFFSET 0 LIMIT 20"
        q.count.sql shouldBe s"SELECT count(*) FROM $tableFull WHERE p.talk_id=? "
        check(q.query)
        check(q.count)
      }
      it("should build selectPageFull for a group and speaker") {
        val q = ProposalRepoSql.selectPageFull(group.id, user.id, params)
        q.query.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE c.group_id=? AND p.speakers LIKE ? ORDER BY p.created IS NULL, p.created DESC OFFSET 0 LIMIT 20"
        q.count.sql shouldBe s"SELECT count(*) FROM $tableFull WHERE c.group_id=? AND p.speakers LIKE ? "
        check(q.query)
        check(q.count)
      }
      it("should build selectPageFull for a speaker") {
        val q = ProposalRepoSql.selectPageFull(user.id, params)
        q.query.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE p.speakers LIKE ? ORDER BY p.created IS NULL, p.created DESC OFFSET 0 LIMIT 20"
        q.count.sql shouldBe s"SELECT count(*) FROM $tableFull WHERE p.speakers LIKE ? "
        check(q.query)
        check(q.count)
      }
      it("should build selectPagePublicFull for a speaker") {
        val q = ProposalRepoSql.selectPagePublicFull(user.id, params)
        q.query.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE p.speakers LIKE ? AND e.published IS NOT NULL ORDER BY p.created IS NULL, p.created DESC OFFSET 0 LIMIT 20"
        q.count.sql shouldBe s"SELECT count(*) FROM $tableFull WHERE p.speakers LIKE ? AND e.published IS NOT NULL "
        check(q.query)
        check(q.count)
      }
      it("should build selectPagePublicFull for a group") {
        val q = ProposalRepoSql.selectPagePublicFull(group.id, params)
        q.query.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE e.group_id=? AND e.published IS NOT NULL ORDER BY p.created IS NULL, p.created DESC OFFSET 0 LIMIT 20"
        q.count.sql shouldBe s"SELECT count(*) FROM $tableFull WHERE e.group_id=? AND e.published IS NOT NULL "
        check(q.query)
        check(q.count)
      }
      it("should build selectAll") {
        val q = ProposalRepoSql.selectAll(NonEmptyList.of(proposal.id))
        q.sql shouldBe s"SELECT $fields FROM $table WHERE id IN (?) "
        check(q)
      }
      it("should build selectAllPublicFull") {
        val q = ProposalRepoSql.selectAllPublicFull(NonEmptyList.of(proposal.id))
        q.sql shouldBe s"SELECT $fieldsFull FROM $tableFull WHERE p.id IN (?) AND e.published IS NOT NULL"
        check(q)
      }
      it("should build selectTags") {
        val q = ProposalRepoSql.selectTags()
        q.sql shouldBe s"SELECT tags FROM $table"
        check(q)
      }
    }
  }
}

object ProposalRepoSqlSpec {

  import RepoSpec._

  val table = "proposals"
  val fields = "id, talk_id, cfp_id, event_id, status, title, duration, description, speakers, slides, video, tags, created, created_by, updated, updated_by"

  private val whereCfp = s"(SELECT p.id FROM $table p INNER JOIN $cfpTable c ON p.cfp_id=c.id WHERE p.id=? AND c.slug=?)"
  private val whereCfpAndTalk = s"(SELECT p.id FROM $table p INNER JOIN cfps c ON p.cfp_id=c.id INNER JOIN talks t ON p.talk_id=t.id WHERE c.slug=? AND t.slug=? AND p.speakers LIKE ?)"
  private val whereGroupAndCfp = s"(SELECT p.id FROM $table p INNER JOIN cfps c ON p.cfp_id=c.id INNER JOIN groups g ON c.group_id=g.id WHERE p.id=? AND c.slug=? AND g.slug=? AND g.owners LIKE ?)"

  private val tableFull = s"$table p INNER JOIN $cfpTable c ON p.cfp_id=c.id INNER JOIN $groupTable g ON c.group_id=g.id INNER JOIN $talkTable t ON p.talk_id=t.id LEFT OUTER JOIN $eventTable e ON p.event_id=e.id"
  private val fieldsFull = s"${mapFields(fields, "p." + _)}, ${mapFields(cfpFields, "c." + _)}, ${mapFields(groupFields, "g." + _)}, ${mapFields(talkFields, "t." + _)}, ${mapFields(eventFields, "e." + _)}"
}
