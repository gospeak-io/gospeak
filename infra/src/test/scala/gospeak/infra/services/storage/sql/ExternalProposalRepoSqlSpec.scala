package gospeak.infra.services.storage.sql

import gospeak.core.domain._
import gospeak.infra.services.storage.sql.ExternalEventRepoSqlSpec.{fields => externalEventFields, table => externalEventTable}
import gospeak.infra.services.storage.sql.ExternalProposalRepoSql._
import gospeak.infra.services.storage.sql.ExternalProposalRepoSqlSpec._
import gospeak.infra.services.storage.sql.TalkRepoSqlSpec.{fields => talkFields, table => talkTable}
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.infra.services.storage.sql.testingutils.RepoSpec.mapFields

import scala.concurrent.duration._

class ExternalProposalRepoSqlSpec extends RepoSpec {
  describe("ExternalProposalRepoSql") {
    it("should handle crud operations") {
      val (user, ctx) = createUser().unsafeRunSync()
      val talk = talkRepo.create(talkData1)(ctx).unsafeRunSync()
      val event = externalEventRepo.create(externalEventData1)(ctx).unsafeRunSync()
      val proposalData = externalProposalData1.copy(status = Proposal.Status.Accepted)
      externalProposalRepo.listPublic(event.id, params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List()
      val proposal = externalProposalRepo.create(talk.id, event.id, proposalData, talk.speakers)(ctx).unsafeRunSync()
      externalProposalRepo.listPublic(event.id, params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(proposal)

      val data = externalProposalData2.copy(status = Proposal.Status.Accepted)
      externalProposalRepo.edit(proposal.id)(data)(ctx).unsafeRunSync()
      externalProposalRepo.listPublic(event.id, params)(ctx.userAwareCtx).unsafeRunSync().items.map(_.data) shouldBe List(data)

      externalProposalRepo.remove(proposal.id)(ctx).unsafeRunSync()
      externalProposalRepo.listPublic(event.id, params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List()
    }
    it("should perform specific updates") {
      val (user, ctx) = createUser().unsafeRunSync()
      val user2 = userRepo.create(userData2, now, None).unsafeRunSync()
      val talk = talkRepo.create(talkData1)(ctx).unsafeRunSync()
      val event = externalEventRepo.create(externalEventData1)(ctx).unsafeRunSync()
      val proposal = externalProposalRepo.create(talk.id, event.id, externalProposalData1, talk.speakers)(ctx).unsafeRunSync()
      externalProposalRepo.find(proposal.id).unsafeRunSync() shouldBe Some(proposal)

      val p1 = proposal.copy(status = Proposal.Status.Accepted)
      externalProposalRepo.editStatus(proposal.id, p1.status)(ctx).unsafeRunSync()
      externalProposalRepo.find(proposal.id).unsafeRunSync() shouldBe Some(p1)

      val p2 = p1.copy(slides = Some(urlSlides))
      externalProposalRepo.editSlides(proposal.id, p2.slides.get)(ctx).unsafeRunSync()
      externalProposalRepo.find(proposal.id).unsafeRunSync() shouldBe Some(p2)

      val p3 = p2.copy(video = Some(urlVideo))
      externalProposalRepo.editVideo(proposal.id, p3.video.get)(ctx).unsafeRunSync()
      externalProposalRepo.find(proposal.id).unsafeRunSync() shouldBe Some(p3)

      val p4 = p3.copy(speakers = p3.speakers :+ user2.id)
      an[Exception] should be thrownBy externalProposalRepo.addSpeaker(proposal.id, user.id)(ctx).unsafeRunSync()
      externalProposalRepo.addSpeaker(proposal.id, user2.id)(ctx).unsafeRunSync()
      externalProposalRepo.find(proposal.id).unsafeRunSync() shouldBe Some(p4)

      an[Exception] should be thrownBy externalProposalRepo.removeSpeaker(proposal.id, user.id)(ctx).unsafeRunSync()
      an[Exception] should be thrownBy externalProposalRepo.removeSpeaker(proposal.id, User.Id.generate())(ctx).unsafeRunSync()
      externalProposalRepo.removeSpeaker(proposal.id, user2.id)(ctx).unsafeRunSync()
      externalProposalRepo.find(proposal.id).unsafeRunSync() shouldBe Some(p3)
    }
    it("should select a page") {
      val (user, ctx) = createUser().unsafeRunSync()
      val talk1 = talkRepo.create(talkData1)(ctx).unsafeRunSync()
      val talk2 = talkRepo.create(talkData2)(ctx).unsafeRunSync()
      val event = externalEventRepo.create(externalEventData1)(ctx).unsafeRunSync()
      val proposal1 = externalProposalRepo.create(talk1.id, event.id, externalProposalData1.copy(status = Proposal.Status.Accepted, title = Talk.Title("aaa"), duration = 2.hours), talk1.speakers)(ctx.plusSeconds(10)).unsafeRunSync()
      val proposal2 = externalProposalRepo.create(talk2.id, event.id, externalProposalData2.copy(status = Proposal.Status.Accepted, title = Talk.Title("bbb"), duration = 1.hour), talk2.speakers)(ctx).unsafeRunSync()
      val commonProposal1 = CommonProposal(proposal1, talk1, event)
      val commonProposal2 = CommonProposal(proposal2, talk2, event)

      externalProposalRepo.listPublic(event.id, params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(proposal1, proposal2)
      externalProposalRepo.listPublic(event.id, params.page(2))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List()
      externalProposalRepo.listPublic(event.id, params.pageSize(5))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(proposal1, proposal2)
      externalProposalRepo.listPublic(event.id, params.search(proposal1.title.value))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(proposal1)
      externalProposalRepo.listPublic(event.id, params.orderBy("duration"))(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(proposal2, proposal1)

      externalProposalRepo.listCommon(params)(ctx).unsafeRunSync().items shouldBe List(commonProposal1, commonProposal2)
      externalProposalRepo.listCommon(params.page(2))(ctx).unsafeRunSync().items shouldBe List()
      externalProposalRepo.listCommon(params.pageSize(5))(ctx).unsafeRunSync().items shouldBe List(commonProposal1, commonProposal2)
      externalProposalRepo.listCommon(params.search(proposal1.title.value))(ctx).unsafeRunSync().items shouldBe List(commonProposal1)
      externalProposalRepo.listCommon(params.orderBy("duration"))(ctx).unsafeRunSync().items shouldBe List(commonProposal2, commonProposal1)
    }
    it("should be able to read correctly") {
      val (user, ctx) = createUser().unsafeRunSync()
      val talk = talkRepo.create(talkData1)(ctx).unsafeRunSync()
      val event = externalEventRepo.create(externalEventData1.copy(start = Some(nowLDT.plusDays(10))))(ctx).unsafeRunSync()
      val proposal = externalProposalRepo.create(talk.id, event.id, externalProposalData1.copy(status = Proposal.Status.Accepted), talk.speakers)(ctx).unsafeRunSync()
      val proposalFull = ExternalProposal.Full(proposal, talk, event)
      val commonProposal = CommonProposal(proposal, talk, event)

      externalProposalRepo.listAllPublicIds().unsafeRunSync() shouldBe List(event.id -> proposal.id)
      externalProposalRepo.listPublic(event.id, params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(proposal)
      externalProposalRepo.listCommon(talk.id, params)(ctx).unsafeRunSync().items shouldBe List(commonProposal)
      externalProposalRepo.listCommon(params)(ctx).unsafeRunSync().items shouldBe List(commonProposal)
      externalProposalRepo.listCurrentCommon(params)(ctx).unsafeRunSync().items shouldBe List(commonProposal)
      externalProposalRepo.listAllCommon(talk.id).unsafeRunSync() shouldBe List(commonProposal)
      externalProposalRepo.listAllCommon(user.id, Proposal.Status.Accepted).unsafeRunSync() shouldBe List(commonProposal)
      externalProposalRepo.listAllCommon(talk.id, Proposal.Status.Accepted).unsafeRunSync() shouldBe List(commonProposal)
      externalProposalRepo.find(proposal.id).unsafeRunSync() shouldBe Some(proposal)
      externalProposalRepo.findFull(proposal.id).unsafeRunSync() shouldBe Some(proposalFull)
      externalProposalRepo.listTags().unsafeRunSync() shouldBe proposal.tags
    }
    it("should check queries") {
      check(insert(externalProposal), s"INSERT INTO ${table.stripSuffix(" ep")} (${mapFields(fields, _.stripPrefix("ep."))}) VALUES (${mapFields(fields, _ => "?")})")
      check(update(externalProposal.id)(externalProposal.data, user.id, now), s"UPDATE $table SET status=?, title=?, duration=?, description=?, message=?, slides=?, video=?, url=?, tags=?, updated_at=?, updated_by=? WHERE ep.id=? AND ep.speakers LIKE ?")
      check(updateStatus(externalProposal.id)(Proposal.Status.Accepted, user.id), s"UPDATE $table SET status=? WHERE ep.id=? AND ep.speakers LIKE ?")
      check(updateSlides(externalProposal.id)(urlSlides, user.id, now), s"UPDATE $table SET slides=?, updated_at=?, updated_by=? WHERE ep.id=? AND ep.speakers LIKE ?")
      check(updateVideo(externalProposal.id)(urlVideo, user.id, now), s"UPDATE $table SET video=?, updated_at=?, updated_by=? WHERE ep.id=? AND ep.speakers LIKE ?")
      check(updateSpeakers(externalProposal.id)(talk.speakers, user.id, now), s"UPDATE $table SET speakers=?, updated_at=?, updated_by=? WHERE ep.id=? AND ep.speakers LIKE ?")
      check(delete(externalProposal.id, user.id), s"DELETE FROM $table WHERE ep.id=? AND ep.speakers LIKE ?")
      check(selectOne(externalProposal.id), s"SELECT $fields FROM $table WHERE ep.id=? $orderBy LIMIT 1")
      check(selectOneFull(externalProposal.id), s"SELECT $fieldsFull FROM $tableFull WHERE ep.id=? $orderBy LIMIT 1")
      check(selectAllPublicIds(), s"SELECT ep.event_id, ep.id FROM $table WHERE ep.status=? $orderBy")
      check(selectPage(externalEvent.id, Proposal.Status.Accepted, params), s"SELECT $fields FROM $table WHERE ep.event_id=? AND ep.status=? $orderBy LIMIT 20 OFFSET 0")
      unsafeCheck(selectPageCommon(talk.id, params), s"SELECT $commonFields FROM $commonTable WHERE p.talk_id=? $commonOrderBy LIMIT 20 OFFSET 0")
      unsafeCheck(selectPageCommon(params), s"SELECT $commonFields FROM $commonTable WHERE p.speakers LIKE ? $commonOrderBy LIMIT 20 OFFSET 0")
      unsafeCheck(selectPageCommonCurrent(params), s"SELECT $commonFields FROM $commonTable WHERE p.speakers LIKE ? AND ((p.status=?) OR (p.status=? AND (p.event_start > ? OR p.event_ext_start > ?)) OR (p.status=? AND p.updated_at > ?)) $commonOrderBy LIMIT 20 OFFSET 0")
      unsafeCheck(selectAllCommon(talk.id), s"SELECT $commonFields FROM $commonTable WHERE p.talk_id=? $commonOrderBy")
      unsafeCheck(selectAllCommon(user.id, Proposal.Status.Accepted), s"SELECT $commonFields FROM $commonTable WHERE p.speakers LIKE ? AND p.status=? $commonOrderBy")
      unsafeCheck(selectAllCommon(talk.id, Proposal.Status.Accepted), s"SELECT $commonFields FROM $commonTable WHERE p.talk_id=? AND p.status=? $commonOrderBy")
      unsafeCheck(selectTags(), s"SELECT ep.tags FROM $table $orderBy")
    }
  }
}

object ExternalProposalRepoSqlSpec {
  val table = "external_proposals ep"
  val fields: String = mapFields("id, talk_id, event_id, status, title, duration, description, message, speakers, slides, video, url, tags, created_at, created_by, updated_at, updated_by", "ep." + _)
  val orderBy = "ORDER BY ep.title IS NULL, ep.title, ep.created_at IS NULL, ep.created_at"

  val tableFull = s"$table INNER JOIN $talkTable ON ep.talk_id=t.id INNER JOIN $externalEventTable ON ep.event_id=ee.id"
  val fieldsFull = s"$fields, $talkFields, $externalEventFields"

  val commonTable: String = "(" +
    "(SELECT p.title, p.status, p.duration, p.speakers, p.slides, p.video, p.tags, t.id as talk_id, t.slug as talk_slug, t.duration as talk_duration, null as ext_id, null as event_ext_id, null as event_ext_name, null as event_ext_kind, null as event_ext_logo, null as event_ext_start, null as event_ext_url, null as event_ext_proposal_url, p.id as int_id, g.id as group_id, g.slug as group_slug, g.name as group_name, g.logo as group_logo, g.owners as group_owners, c.id as cfp_id, c.slug as cfp_slug, c.name as cfp_name, e.id as event_id, e.slug as event_slug, e.name as event_name, e.kind as event_kind, e.start as event_start, p.created_at, p.created_by, p.updated_at, p.updated_by FROM proposals p INNER JOIN talks t ON p.talk_id=t.id LEFT OUTER JOIN events e ON p.event_id=e.id INNER JOIN cfps c ON p.cfp_id=c.id INNER JOIN groups g ON c.group_id=g.id WHERE e.published IS NOT NULL) UNION " +
    "(SELECT ep.title, ep.status, ep.duration, ep.speakers, ep.slides, ep.video, ep.tags, t.id as talk_id, t.slug as talk_slug, t.duration as talk_duration, ep.id as ext_id, ee.id as event_ext_id, ee.name as event_ext_name, ee.kind as event_ext_kind, ee.logo as event_ext_logo, ee.start as event_ext_start, ee.url as event_ext_url, ep.url as event_ext_proposal_url, null as int_id, null as group_id, null as group_slug, null as group_name, null as group_logo, null as group_owners, null as cfp_id, null as cfp_slug, null as cfp_name, null as event_id, null as event_slug, null as event_name, null as event_kind, null as event_start, ep.created_at, ep.created_by, ep.updated_at, ep.updated_by FROM external_proposals ep INNER JOIN talks t ON ep.talk_id=t.id INNER JOIN external_events ee ON ep.event_id=ee.id)) p"
  val commonFields: String = mapFields("title, status, duration, speakers, slides, video, tags, talk_id, talk_slug, talk_duration, ext_id, event_ext_id, event_ext_name, event_ext_kind, event_ext_logo, event_ext_start, event_ext_url, event_ext_proposal_url, int_id, group_id, group_slug, group_name, group_logo, group_owners, cfp_id, cfp_slug, cfp_name, event_id, event_slug, event_name, event_kind, event_start, created_at, created_by, updated_at, updated_by", "p." + _)
  val commonOrderBy = "ORDER BY p.created_at IS NULL, p.created_at DESC"
}
