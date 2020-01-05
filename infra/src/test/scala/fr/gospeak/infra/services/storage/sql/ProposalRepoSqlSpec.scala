package fr.gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import fr.gospeak.core.domain.utils.{FakeCtx, OrgaCtx, UserAwareCtx}
import fr.gospeak.core.domain.{Cfp, Proposal, Talk}
import fr.gospeak.infra.services.storage.sql.CfpRepoSqlSpec.{fields => cfpFields, table => cfpTable}
import fr.gospeak.infra.services.storage.sql.ContactRepoSqlSpec.{fields => contactFields, table => contactTable}
import fr.gospeak.infra.services.storage.sql.EventRepoSqlSpec.{fields => eventFields, table => eventTable}
import fr.gospeak.infra.services.storage.sql.GroupRepoSqlSpec.{fields => groupFields, table => groupTable}
import fr.gospeak.infra.services.storage.sql.PartnerRepoSqlSpec.{fields => partnerFields, table => partnerTable}
import fr.gospeak.infra.services.storage.sql.ProposalRepoSqlSpec._
import fr.gospeak.infra.services.storage.sql.TalkRepoSqlSpec.{fields => talkFields, table => talkTable}
import fr.gospeak.infra.services.storage.sql.UserRepoSqlSpec.{fields => userFields, table => userTable}
import fr.gospeak.infra.services.storage.sql.VenueRepoSqlSpec.{fields => venueFields, table => venueTable}
import fr.gospeak.infra.services.storage.sql.testingutils.RepoSpec

class ProposalRepoSqlSpec extends RepoSpec {
  implicit private val orgaCtx: OrgaCtx = FakeCtx(now, user, group)
  implicit private val userAwareCtx: UserAwareCtx = FakeCtx(now, Some(user))

  describe("ProposalRepoSql") {
    it("should create and retrieve a proposal for a group and talk") {
      val (user, group, cfp, talk) = createUserGroupCfpAndTalk().unsafeRunSync()
      val ctx = FakeCtx(now, user)
      proposalRepo.listFull(talk.id, params).unsafeRunSync().items shouldBe Seq()
      proposalRepo.listFull(cfp.slug, params).unsafeRunSync().items shouldBe Seq()
      val proposal = proposalRepo.create(talk.id, cfp.id, proposalData1, speakers)(ctx).unsafeRunSync()
      proposalRepo.listFull(talk.id, params).unsafeRunSync().items shouldBe Seq(Proposal.Full(proposal, cfp, group, talk, None, None, 0L, 0L, 0L, None))
      proposalRepo.listFull(cfp.slug, params).unsafeRunSync().items shouldBe Seq(Proposal.Full(proposal, cfp, group, talk, None, None, 0L, 0L, 0L, None))
      proposalRepo.find(cfp.slug, proposal.id).unsafeRunSync() shouldBe Some(proposal)
    }
    it("should fail to create a proposal when talk does not exists") {
      val user = userRepo.create(userData1, now, None).unsafeRunSync()
      val group = groupRepo.create(groupData1)(FakeCtx(now, user)).unsafeRunSync()
      val ctx = FakeCtx(now, user, group)
      val cfp = cfpRepo.create(cfpData1)(ctx).unsafeRunSync()
      an[Exception] should be thrownBy proposalRepo.create(Talk.Id.generate(), cfp.id, proposalData1, speakers)(ctx).unsafeRunSync()
    }
    it("should fail to create a proposal when cfp does not exists") {
      val user = userRepo.create(userData1, now, None).unsafeRunSync()
      val ctx = FakeCtx(now, user)
      val talk = talkRepo.create(talkData1)(ctx).unsafeRunSync()
      an[Exception] should be thrownBy proposalRepo.create(talk.id, Cfp.Id.generate(), proposalData1, speakers)(ctx).unsafeRunSync()
    }
    it("should fail on duplicate cfp and talk") {
      val (user, _, cfp, talk) = createUserGroupCfpAndTalk().unsafeRunSync()
      val ctx = FakeCtx(now, user)
      proposalRepo.create(talk.id, cfp.id, proposalData1, speakers)(ctx).unsafeRunSync()
      an[Exception] should be thrownBy proposalRepo.create(talk.id, cfp.id, proposalData1, speakers)(ctx).unsafeRunSync()
    }
    describe("Queries") {
      it("should build insert") {
        val q = ProposalRepoSql.insert(proposal)
        check(q, s"INSERT INTO ${table.stripSuffix(" p")} (${mapFields(fields, _.stripPrefix("p."))}) VALUES (${mapFields(fields, _ => "?")})")
      }
      it("should build update for orga, group, cfp and proposal") {
        val q = ProposalRepoSql.update(user.id, group.slug, cfp.slug, proposal.id)(proposal.dataOrga, now)
        check(q, s"UPDATE $table SET title=?, duration=?, description=?, slides=?, video=?, tags=?, orga_tags=?, updated_at=?, updated_by=? WHERE p.id=$whereGroupAndCfp")
      }
      it("should build update for speaker, talk and cfp") {
        val q = ProposalRepoSql.update(user.id, talk.slug, cfp.slug)(proposal.data, now)
        check(q, s"UPDATE $table SET title=?, duration=?, description=?, slides=?, video=?, tags=?, updated_at=?, updated_by=? WHERE p.id=$whereCfpAndTalk")
      }
      it("should build updateStatus") {
        val q = ProposalRepoSql.updateStatus(cfp.slug, proposal.id)(proposal.status, None)
        check(q, s"UPDATE $table SET status=?, event_id=? WHERE p.id=$whereCfp")
      }
      it("should build updateSlides by cfp and proposal") {
        val q = ProposalRepoSql.updateSlides(cfp.slug, proposal.id)(slides, user.id, now)
        check(q, s"UPDATE $table SET slides=?, updated_at=?, updated_by=? WHERE p.id=$whereCfp")
      }
      it("should build updateSlides by speaker, talk and cfp") {
        val q = ProposalRepoSql.updateSlides(user.id, talk.slug, cfp.slug)(slides, user.id, now)
        check(q, s"UPDATE $table SET slides=?, updated_at=?, updated_by=? WHERE p.id=$whereCfpAndTalk")
      }
      it("should build updateVideo by cfp and proposal") {
        val q = ProposalRepoSql.updateVideo(cfp.slug, proposal.id)(video, user.id, now)
        check(q, s"UPDATE $table SET video=?, updated_at=?, updated_by=? WHERE p.id=$whereCfp")
      }
      it("should build updateVideo by speaker, talk and cfp") {
        val q = ProposalRepoSql.updateVideo(user.id, talk.slug, cfp.slug)(video, user.id, now)
        check(q, s"UPDATE $table SET video=?, updated_at=?, updated_by=? WHERE p.id=$whereCfpAndTalk")
      }
      it("should build updateSpeakers by id") {
        val q = ProposalRepoSql.updateSpeakers(proposal.id)(proposal.speakers, user.id, now)
        check(q, s"UPDATE $table SET speakers=?, updated_at=?, updated_by=? WHERE p.id=?")
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
      it("should build selectOneFull for cfp and proposal id") {
        val q = ProposalRepoSql.selectOneFull(cfp.slug, proposal.id)
        check(q, s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE p.id=$whereCfp GROUP BY $fieldsFull $orderByFull")
      }
      it("should build selectOneFull for id") {
        val q = ProposalRepoSql.selectOneFull(proposal.id)
        check(q, s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE p.id=? GROUP BY $fieldsFull $orderByFull")
      }
      it("should build selectOneFull for talk, cfp and speaker") {
        val q = ProposalRepoSql.selectOneFull(talk.slug, cfp.slug)
        check(q, s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE t.slug=? AND c.slug=? AND p.speakers LIKE ? GROUP BY $fieldsFull $orderByFull")
      }
      it("should build selectOnePublicFull for id") {
        val q = ProposalRepoSql.selectOnePublicFull(group.id, proposal.id)
        check(q, s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE c.group_id=? AND p.id=? AND e.published IS NOT NULL GROUP BY $fieldsFull $orderByFull")
      }
      it("should build selectPage for a cfp and status") {
        val q = ProposalRepoSql.selectPage(cfp.id, Proposal.Status.Pending, params)
        check(q, s"SELECT $fields FROM $table WHERE p.cfp_id=? AND p.status=? $orderBy LIMIT 20 OFFSET 0")
      }
      it("should build selectPageFull for a cfp") {
        val q = ProposalRepoSql.selectPageFull(cfp.slug, params)
        check(q, s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE c.slug=? GROUP BY $fieldsFull $orderByFull LIMIT 20 OFFSET 0")
      }
      it("should build selectPageFull for a cfp and status") {
        val q = ProposalRepoSql.selectPageFull(cfp.slug, Proposal.Status.Pending, params)
        check(q, s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE c.slug=? AND p.status=? GROUP BY $fieldsFull $orderByFull LIMIT 20 OFFSET 0")
      }
      it("should build selectPageFull for a group") {
        val q = ProposalRepoSql.selectPageFull(params)
        check(q, s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE c.group_id=? GROUP BY $fieldsFull $orderByFull LIMIT 20 OFFSET 0")
      }
      it("should build selectPageFull for a group and speaker") {
        val q = ProposalRepoSql.selectPageFull(user.id, params)
        check(q, s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE c.group_id=? AND p.speakers LIKE ? GROUP BY $fieldsFull $orderByFull LIMIT 20 OFFSET 0")
      }
      it("should build selectPageFull for a talk") {
        val q = ProposalRepoSql.selectPageFull(talk.id, params)
        check(q, s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE p.talk_id=? GROUP BY $fieldsFull $orderByFull LIMIT 20 OFFSET 0")
      }
      it("should build selectSpeakerPageFull for a speaker") {
        val q = ProposalRepoSql.selectSpeakerPageFull(params)
        check(q, s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE p.speakers LIKE ? GROUP BY $fieldsFull $orderByFull LIMIT 20 OFFSET 0")
      }
      it("should build selectPagePublicFull for a speaker") {
        val q = ProposalRepoSql.selectPagePublicFull(user.id, params)
        check(q, s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE p.speakers LIKE ? AND e.published IS NOT NULL GROUP BY $fieldsFull $orderByFull LIMIT 20 OFFSET 0")
      }
      it("should build selectPagePublicFull for a group") {
        val q = ProposalRepoSql.selectPagePublicFull(group.id, params)
        check(q, s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE e.group_id=? AND e.published IS NOT NULL GROUP BY $fieldsFull $orderByFull LIMIT 20 OFFSET 0")
      }
      it("should build selectAll") {
        val q = ProposalRepoSql.selectAll(NonEmptyList.of(proposal.id))
        check(q, s"SELECT $fields FROM $table WHERE id IN (?)  $orderBy")
      }
      it("should build selectAllPublic") {
        val q = ProposalRepoSql.selectAllPublic(NonEmptyList.of(proposal.id))
        check(q, s"SELECT $fields FROM $tableWithEvent WHERE p.id IN (?) AND e.published IS NOT NULL $orderBy")
      }
      it("should build selectTags") {
        val q = ProposalRepoSql.selectTags()
        check(q, s"SELECT p.tags FROM $table")
      }
      it("should build selectOrgaTags") {
        val q = ProposalRepoSql.selectOrgaTags(group.id)
        check(q, s"SELECT p.orga_tags FROM $tableWithCfp WHERE c.group_id=?")
      }
      it("should build insert Rating") {
        val q = ProposalRepoSql.insert(rating)
        check(q, s"INSERT INTO ${ratingTable.stripSuffix(" pr")} (${mapFields(ratingFields, _.stripPrefix("pr."))}) VALUES (${mapFields(ratingFields, _ => "?")})")
      }
      it("should build update Rating") {
        val q = ProposalRepoSql.update(rating)
        check(q, s"UPDATE $ratingTable SET grade=?, created_at=?  WHERE pr.proposal_id=? AND pr.created_by=?")
      }
      it("should build selectOneRating") {
        val q = ProposalRepoSql.selectOneRating(proposal.id, user.id)
        check(q, s"SELECT $ratingFields FROM $ratingTable WHERE pr.proposal_id=? AND pr.created_by=? $ratingOrderBy")
      }
      it("should build selectAllRatings for a proposal") {
        val q = ProposalRepoSql.selectAllRatings(proposal.id)
        check(q, s"SELECT $ratingFieldsFull FROM $ratingTableFull WHERE pr.proposal_id=? $ratingOrderBy")
      }
      it("should build selectAllRatings for an orga in a cfp") {
        val q = ProposalRepoSql.selectAllRatings(cfp.slug, user.id)
        check(q, s"SELECT $ratingFields FROM $ratingTable " +
          s"INNER JOIN $table ON pr.proposal_id=p.id " +
          s"INNER JOIN $cfpTable ON p.cfp_id=c.id " +
          s"WHERE c.slug=? AND pr.created_by=? $ratingOrderBy")
      }
      it("should build selectAllRatings for an orga in a list") {
        val q = ProposalRepoSql.selectAllRatings(user.id, NonEmptyList.of(proposal.id))
        check(q, s"SELECT $ratingFields FROM $ratingTable WHERE pr.proposal_id IN (?) AND pr.created_by=? $ratingOrderBy")
      }
    }
  }
}

object ProposalRepoSqlSpec {

  import RepoSpec._

  val table = "proposals p"
  val fields: String = mapFields("id, talk_id, cfp_id, event_id, status, title, duration, description, speakers, slides, video, tags, orga_tags, created_at, created_by, updated_at, updated_by", "p." + _)
  val orderBy = "ORDER BY p.created_at IS NULL, p.created_at DESC"

  private val whereCfp = s"(SELECT p.id FROM $table INNER JOIN $cfpTable ON p.cfp_id=c.id WHERE p.id=? AND c.slug=?)"
  private val whereCfpAndTalk = s"(SELECT p.id FROM $table INNER JOIN $cfpTable ON p.cfp_id=c.id INNER JOIN $talkTable ON p.talk_id=t.id WHERE c.slug=? AND t.slug=? AND p.speakers LIKE ?)"
  private val whereGroupAndCfp = s"(SELECT p.id FROM $table INNER JOIN $cfpTable ON p.cfp_id=c.id INNER JOIN $groupTable ON c.group_id=g.id WHERE p.id=? AND c.slug=? AND g.slug=? AND g.owners LIKE ?)"

  private val ratingTable = "proposal_ratings pr"
  private val ratingFields = mapFields("proposal_id, grade, created_at, created_by", "pr." + _)
  private val ratingOrderBy = "ORDER BY pr.created_at IS NULL, pr.created_at"

  private val tableWithEvent = s"$table LEFT OUTER JOIN $eventTable ON p.event_id=e.id"
  private val tableWithCfp = s"$table INNER JOIN $cfpTable ON p.cfp_id=c.id"

  private val tableFull = s"$table INNER JOIN $cfpTable ON p.cfp_id=c.id " +
    s"INNER JOIN $groupTable ON c.group_id=g.id " +
    s"INNER JOIN $talkTable ON p.talk_id=t.id " +
    s"LEFT OUTER JOIN $eventTable ON p.event_id=e.id " +
    s"LEFT OUTER JOIN $venueTable ON e.venue=v.id " +
    s"LEFT OUTER JOIN $partnerTable ON v.partner_id=pa.id " +
    s"LEFT OUTER JOIN $contactTable ON v.contact_id=ct.id " +
    s"LEFT OUTER JOIN $ratingTable ON p.id=pr.proposal_id"
  private val fieldsFull = s"$fields, $cfpFields, $groupFields, $talkFields, $eventFields, $venueFields, $partnerFields, $contactFields"
  private val fieldsFullAgg = "COALESCE(SUM(pr.grade), 0) as score, COALESCE((COUNT(pr.grade) + SUM(pr.grade)) / 2, 0) as likes, COALESCE((COUNT(pr.grade) - SUM(pr.grade)) / 2, 0) as dislikes"
  private val fieldsFullCustom = "(SELECT grade from proposal_ratings WHERE created_by=? AND proposal_id=p.id) as user_grade"
  private val orderByFull = "ORDER BY COALESCE(SUM(pr.grade), 0) IS NULL, COALESCE(SUM(pr.grade), 0) DESC, COALESCE(COUNT(pr.grade), 0) IS NULL, COALESCE(COUNT(pr.grade), 0) DESC, p.created_at IS NULL, p.created_at DESC"

  private val ratingTableFull = s"$ratingTable INNER JOIN $userTable ON pr.created_by=u.id"
  private val ratingFieldsFull = s"$ratingFields, $userFields"
}
