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
        check(q, s"INSERT INTO ${table.stripSuffix(" p")} (${mapFields(fields, _.stripPrefix("p."))}) VALUES (${mapFields(fields, _ => "?")})")
      }
      it("should build update for orga, group, cfp and proposal") {
        val q = ProposalRepoSql.update(user.id, group.slug, cfp.slug, proposal.id)(proposal.data, now)
        check(q, s"UPDATE $table SET title=?, duration=?, description=?, slides=?, video=?, tags=?, updated=?, updated_by=? WHERE p.id=$whereGroupAndCfp")
      }
      it("should build update for speaker, talk and cfp") {
        val q = ProposalRepoSql.update(user.id, talk.slug, cfp.slug)(proposal.data, now)
        check(q, s"UPDATE $table SET title=?, duration=?, description=?, slides=?, video=?, tags=?, updated=?, updated_by=? WHERE p.id=$whereCfpAndTalk")
      }
      it("should build updateStatus") {
        val q = ProposalRepoSql.updateStatus(cfp.slug, proposal.id)(proposal.status, None)
        check(q, s"UPDATE $table SET status=?, event_id=? WHERE p.id=$whereCfp")
      }
      it("should build updateSlides by cfp and proposal") {
        val q = ProposalRepoSql.updateSlides(cfp.slug, proposal.id)(slides, user.id, now)
        check(q, s"UPDATE $table SET slides=?, updated=?, updated_by=? WHERE p.id=$whereCfp")
      }
      it("should build updateSlides by speaker, talk and cfp") {
        val q = ProposalRepoSql.updateSlides(user.id, talk.slug, cfp.slug)(slides, user.id, now)
        check(q, s"UPDATE $table SET slides=?, updated=?, updated_by=? WHERE p.id=$whereCfpAndTalk")
      }
      it("should build updateVideo by cfp and proposal") {
        val q = ProposalRepoSql.updateVideo(cfp.slug, proposal.id)(video, user.id, now)
        check(q, s"UPDATE $table SET video=?, updated=?, updated_by=? WHERE p.id=$whereCfp")
      }
      it("should build updateVideo by speaker, talk and cfp") {
        val q = ProposalRepoSql.updateVideo(user.id, talk.slug, cfp.slug)(video, user.id, now)
        check(q, s"UPDATE $table SET video=?, updated=?, updated_by=? WHERE p.id=$whereCfpAndTalk")
      }
      it("should build updateSpeakers by id") {
        val q = ProposalRepoSql.updateSpeakers(proposal.id)(proposal.speakers, user.id, now)
        check(q, s"UPDATE $table SET speakers=?, updated=?, updated_by=? WHERE p.id=?")
      }
      it("should build selectOne for proposal id") {
        val q = ProposalRepoSql.selectOne(proposal.id)
        check(q, s"SELECT $fields FROM $table WHERE p.id=? $orderBy")
      }
      it("should build selectOne for cfp and proposal id") {
        val q = ProposalRepoSql.selectOne(cfp.slug, proposal.id)
        check(q, s"SELECT $fields FROM $table WHERE p.id=$whereCfp $orderBy")
      }
      it("should build selectOne for speaker, talk and cfp") {
        val q = ProposalRepoSql.selectOne(user.id, talk.slug, cfp.slug)
        check(q, s"SELECT $fields FROM $table WHERE p.id=$whereCfpAndTalk $orderBy")
      }
      it("should build selectOneFull for id") {
        val q = ProposalRepoSql.selectOneFull(proposal.id)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE p.id=? $orderBy")
      }
      it("should build selectOneFull for talk, cfp and speaker") {
        val q = ProposalRepoSql.selectOneFull(talk.slug, cfp.slug, user.id)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE t.slug=? AND c.slug=? AND p.speakers LIKE ? $orderBy")
      }
      it("should build selectOnePublicFull for id") {
        val q = ProposalRepoSql.selectOnePublicFull(group.id, proposal.id)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE c.group_id=? AND p.id=? AND e.published IS NOT NULL $orderBy")
      }
      it("should build selectPage for a cfp and status") {
        val q = ProposalRepoSql.selectPage(cfp.id, Proposal.Status.Pending, params)
        check(q, s"SELECT $fields FROM $table WHERE p.cfp_id=? AND p.status=? $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectPageFull for a cfp") {
        val q = ProposalRepoSql.selectPageFull(cfp.id, params)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE p.cfp_id=? $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectPageFull for a group") {
        val q = ProposalRepoSql.selectPageFull(group.id, params)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE c.group_id=? $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectPageFull for a group and speaker") {
        val q = ProposalRepoSql.selectPageFull(group.id, user.id, params)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE c.group_id=? AND p.speakers LIKE ? $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectPageFull for a talk") {
        val q = ProposalRepoSql.selectPageFull(talk.id, params)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE p.talk_id=? $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectPageFull for a speaker") {
        val q = ProposalRepoSql.selectPageFull(user.id, params)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE p.speakers LIKE ? $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectPagePublicFull for a speaker") {
        val q = ProposalRepoSql.selectPagePublicFull(user.id, params)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE p.speakers LIKE ? AND e.published IS NOT NULL $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectPagePublicFull for a group") {
        val q = ProposalRepoSql.selectPagePublicFull(group.id, params)
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE e.group_id=? AND e.published IS NOT NULL $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectAll") {
        val q = ProposalRepoSql.selectAll(NonEmptyList.of(proposal.id))
        check(q, s"SELECT $fields FROM $table WHERE id IN (?)  $orderBy")
      }
      it("should build selectAllPublicFull") {
        val q = ProposalRepoSql.selectAllPublicFull(NonEmptyList.of(proposal.id))
        check(q, s"SELECT $fieldsFull FROM $tableFull WHERE p.id IN (?) AND e.published IS NOT NULL $orderBy")
      }
      it("should build selectTags") {
        val q = ProposalRepoSql.selectTags()
        check(q, s"SELECT p.tags FROM $table")
      }
    }
  }
}

object ProposalRepoSqlSpec {

  import RepoSpec._

  val table = "proposals p"
  val fields: String = mapFields("id, talk_id, cfp_id, event_id, status, title, duration, description, speakers, slides, video, tags, created, created_by, updated, updated_by", "p." + _)
  val orderBy = "ORDER BY p.created IS NULL, p.created DESC"

  private val whereCfp = s"(SELECT p.id FROM $table INNER JOIN $cfpTable ON p.cfp_id=c.id WHERE p.id=? AND c.slug=?)"
  private val whereCfpAndTalk = s"(SELECT p.id FROM $table INNER JOIN $cfpTable ON p.cfp_id=c.id INNER JOIN $talkTable ON p.talk_id=t.id WHERE c.slug=? AND t.slug=? AND p.speakers LIKE ?)"
  private val whereGroupAndCfp = s"(SELECT p.id FROM $table INNER JOIN $cfpTable ON p.cfp_id=c.id INNER JOIN $groupTable ON c.group_id=g.id WHERE p.id=? AND c.slug=? AND g.slug=? AND g.owners LIKE ?)"

  private val tableFull = s"$table INNER JOIN $cfpTable ON p.cfp_id=c.id INNER JOIN $groupTable ON c.group_id=g.id INNER JOIN $talkTable ON p.talk_id=t.id LEFT OUTER JOIN $eventTable ON p.event_id=e.id"
  private val fieldsFull = s"$fields, $cfpFields, $groupFields, $talkFields, $eventFields"
}
