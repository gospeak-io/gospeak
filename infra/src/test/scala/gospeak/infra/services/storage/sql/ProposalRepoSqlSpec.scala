package gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import gospeak.core.domain._
import gospeak.infra.services.storage.sql.CfpRepoSqlSpec.{fields => cfpFields, table => cfpTable}
import gospeak.infra.services.storage.sql.CommentRepoSqlSpec.{table => commentTable}
import gospeak.infra.services.storage.sql.ContactRepoSqlSpec.{fields => contactFields, table => contactTable}
import gospeak.infra.services.storage.sql.EventRepoSqlSpec.{fields => eventFields, table => eventTable}
import gospeak.infra.services.storage.sql.GroupRepoSqlSpec.{fields => groupFields, table => groupTable}
import gospeak.infra.services.storage.sql.PartnerRepoSqlSpec.{fields => partnerFields, table => partnerTable}
import gospeak.infra.services.storage.sql.ProposalRepoSql._
import gospeak.infra.services.storage.sql.ProposalRepoSqlSpec._
import gospeak.infra.services.storage.sql.TalkRepoSqlSpec.{fields => talkFields, table => talkTable}
import gospeak.infra.services.storage.sql.UserRepoSqlSpec.{fields => userFields, table => userTable}
import gospeak.infra.services.storage.sql.VenueRepoSqlSpec.{fields => venueFields, table => venueTable}
import gospeak.infra.services.storage.sql.testingutils.RepoSpec
import gospeak.libs.scala.domain.Tag

class ProposalRepoSqlSpec extends RepoSpec {
  describe("ProposalRepoSql") {
    it("should handle crud operations") {
      val (user, group, cfp, talk, ctx) = createCfpAndTalk().unsafeRunSync()
      proposalRepo.listFull(cfp.slug, params)(ctx).unsafeRunSync().items shouldBe List()
      val proposal = proposalRepo.create(talk.id, cfp.id, proposalData1, NonEmptyList.of(user.id))(ctx).unsafeRunSync()
      proposal.data shouldBe proposalData1
      val proposalFull = Proposal.Full(proposal, cfp, group, talk, None, None, 0L, None, 0L, None, 0L, 0L, 0L, None)
      proposalRepo.listFull(cfp.slug, params)(ctx).unsafeRunSync().items shouldBe List(proposalFull)

      proposalRepo.edit(talk.slug, cfp.slug, proposalData2)(ctx).unsafeRunSync()
      proposalRepo.listFull(cfp.slug, params)(ctx).unsafeRunSync().items.map(_.data) shouldBe List(proposalData2)
      // no delete...

      val rating = Proposal.Rating(proposal.id, Proposal.Rating.Grade.Like, now, user.id)
      proposalRepo.listRatings(cfp.slug)(ctx).unsafeRunSync() shouldBe List()
      proposalRepo.rate(cfp.slug, proposal.id, rating.grade)(ctx).unsafeRunSync()
      proposalRepo.listRatings(cfp.slug)(ctx).unsafeRunSync() shouldBe List(rating)

      proposalRepo.rate(cfp.slug, proposal.id, Proposal.Rating.Grade.Dislike)(ctx).unsafeRunSync()
      proposalRepo.listRatings(cfp.slug)(ctx).unsafeRunSync() shouldBe List(rating.copy(grade = Proposal.Rating.Grade.Dislike))
      // no delete...
    }
    it("should fail to create a proposal when talk or cfp does not exists") {
      val (user, group, cfp, talk, ctx) = createCfpAndTalk().unsafeRunSync()
      an[Exception] should be thrownBy proposalRepo.create(Talk.Id.generate(), cfp.id, proposalData1, speakers)(ctx).unsafeRunSync()
      an[Exception] should be thrownBy proposalRepo.create(talk.id, Cfp.Id.generate(), proposalData1, speakers)(ctx).unsafeRunSync()
    }
    it("should fail on duplicate cfp and talk") {
      val (user, _, cfp, talk, ctx) = createCfpAndTalk().unsafeRunSync()
      proposalRepo.create(talk.id, cfp.id, proposalData1, speakers)(ctx).unsafeRunSync()
      an[Exception] should be thrownBy proposalRepo.create(talk.id, cfp.id, proposalData1, speakers)(ctx).unsafeRunSync()
    }
    it("should perform specific updates") {
      val (user, group, cfp, talk, ctx) = createCfpAndTalk().unsafeRunSync()
      val (partner, venue, contact) = createPartnerAndVenue()(ctx).unsafeRunSync()
      val user2 = userRepo.create(userData2, now, None).unsafeRunSync()
      val eventData = eventData1.copy(venue = eventData1.venue.map(_ => venue.id))
      val event = eventRepo.create(eventData)(ctx).unsafeRunSync()
      val proposal = proposalRepo.create(talk.id, cfp.id, proposalData1, NonEmptyList.of(user.id))(ctx).unsafeRunSync()

      val p1 = proposal.copy(title = Talk.Title("aaaaaa"), orgaTags = List(Tag("tag")))
      proposalRepo.edit(cfp.slug, proposal.id, p1.dataOrga)(ctx).unsafeRunSync()
      proposalRepo.find(proposal.id).unsafeRunSync() shouldBe Some(p1)

      val p2 = p1.copy(slides = Some(urlSlides))
      proposalRepo.editSlides(cfp.slug, proposal.id, urlSlides)(ctx).unsafeRunSync()
      proposalRepo.find(proposal.id).unsafeRunSync() shouldBe Some(p2)

      val p3 = p2.copy(slides = Some(urlSlides2))
      proposalRepo.editSlides(talk.slug, cfp.slug, urlSlides2)(ctx).unsafeRunSync()
      proposalRepo.find(proposal.id).unsafeRunSync() shouldBe Some(p3)

      val p4 = p3.copy(video = Some(urlVideo))
      proposalRepo.editVideo(cfp.slug, proposal.id, urlVideo)(ctx).unsafeRunSync()
      proposalRepo.find(proposal.id).unsafeRunSync() shouldBe Some(p4)

      val p5 = p4.copy(video = Some(urlVideo2))
      proposalRepo.editVideo(talk.slug, cfp.slug, urlVideo2)(ctx).unsafeRunSync()
      proposalRepo.find(proposal.id).unsafeRunSync() shouldBe Some(p5)

      val p6 = p5.copy(orgaTags = List(Tag("t"), Tag("u")))
      proposalRepo.editOrgaTags(cfp.slug, proposal.id, p6.orgaTags)(ctx).unsafeRunSync()
      proposalRepo.find(proposal.id).unsafeRunSync() shouldBe Some(p6)

      val p7 = p6.copy(speakers = NonEmptyList.of(user.id, user2.id))
      proposalRepo.addSpeaker(proposal.id)(user2.id, user.id, now).unsafeRunSync()
      proposalRepo.find(proposal.id).unsafeRunSync() shouldBe Some(p7)
      an[Exception] should be thrownBy proposalRepo.addSpeaker(proposal.id)(user2.id, user.id, now).unsafeRunSync()

      an[Exception] should be thrownBy proposalRepo.removeSpeaker(talk.slug, cfp.slug, user.id)(ctx).unsafeRunSync()
      an[Exception] should be thrownBy proposalRepo.removeSpeaker(talk.slug, cfp.slug, User.Id.generate())(ctx).unsafeRunSync()
      proposalRepo.removeSpeaker(talk.slug, cfp.slug, user2.id)(ctx).unsafeRunSync()
      proposalRepo.find(proposal.id).unsafeRunSync() shouldBe Some(p6)

      proposalRepo.addSpeaker(proposal.id)(user2.id, user.id, now).unsafeRunSync()
      proposalRepo.removeSpeaker(cfp.slug, proposal.id, user2.id)(ctx).unsafeRunSync()
      proposalRepo.find(proposal.id).unsafeRunSync() shouldBe Some(p6)

      proposalRepo.accept(cfp.slug, proposal.id, event.id).unsafeRunSync()
      proposalRepo.find(proposal.id).unsafeRunSync().map(_.status) shouldBe Some(Proposal.Status.Accepted)

      proposalRepo.cancel(cfp.slug, proposal.id, event.id).unsafeRunSync()
      proposalRepo.find(proposal.id).unsafeRunSync().map(_.status) shouldBe Some(Proposal.Status.Pending)

      proposalRepo.reject(cfp.slug, proposal.id).unsafeRunSync()
      proposalRepo.find(proposal.id).unsafeRunSync().map(_.status) shouldBe Some(Proposal.Status.Declined)

      proposalRepo.cancelReject(cfp.slug, proposal.id).unsafeRunSync()
      proposalRepo.find(proposal.id).unsafeRunSync().map(_.status) shouldBe Some(Proposal.Status.Pending)

      proposalRepo.reject(cfp.slug, proposal.id, user.id, now).unsafeRunSync()
      proposalRepo.find(proposal.id).unsafeRunSync().map(_.status) shouldBe Some(Proposal.Status.Declined)
    }
    it("should select a page") {
      val (user, group, cfp, talk, ctx) = createCfpAndTalk().unsafeRunSync()
      val talk2 = talkRepo.create(talkData2)(ctx).unsafeRunSync()
      val (partner, venue, contact) = createPartnerAndVenue()(ctx).unsafeRunSync()
      val eventCreated = eventRepo.create(eventData1.copy(venue = eventData1.venue.map(_ => venue.id)))(ctx).unsafeRunSync()
      eventRepo.publish(eventCreated.slug)(ctx).unsafeRunSync()
      val event = eventCreated.copy(published = Some(ctx.now))

      val data1 = proposalData1.copy(title = Talk.Title("bbbbbb"), slides = Some(urlSlides), video = Some(urlVideo), tags = List(Tag("fp"), Tag("oop")))
      val proposalCreated = proposalRepo.create(talk.id, cfp.id, data1, NonEmptyList.of(user.id))(ctx).unsafeRunSync()
      val proposal = proposalCreated.copy(status = Proposal.Status.Accepted, event = Some(event.id), orgaTags = List(Tag("great")))
      proposalRepo.edit(cfp.slug, proposalCreated.id, proposalCreated.dataOrga.copy(orgaTags = proposal.orgaTags))(ctx).unsafeRunSync()
      proposalRepo.accept(cfp.slug, proposalCreated.id, event.id)(ctx).unsafeRunSync()
      val rating = Proposal.Rating(proposal.id, Proposal.Rating.Grade.Like, ctx.now, user.id)
      proposalRepo.rate(cfp.slug, proposal.id, rating.grade)(ctx).unsafeRunSync()
      commentRepo.addComment(proposal.id, commentData1.copy(answers = None))(ctx).unsafeRunSync()
      val proposalFull = Proposal.Full(proposal, cfp, group, talk, Some(event), event.venue.map(_ => Venue.Full(venue, partner, contact)), 1, Some(ctx.now), 0, None, 1, 1, 0, Some(rating.grade))

      val data2 = proposalData2.copy(title = Talk.Title("aaaaaa"), slides = None, video = None)
      val proposalCreated2 = proposalRepo.create(talk2.id, cfp.id, data2, NonEmptyList.of(user.id))(ctx).unsafeRunSync()
      proposalRepo.accept(cfp.slug, proposalCreated2.id, event.id)(ctx).unsafeRunSync()
      val proposal2 = proposalCreated2.copy(status = Proposal.Status.Accepted, event = Some(event.id))
      val proposalFull2 = Proposal.Full(proposal2, cfp, group, talk2, Some(event), event.venue.map(_ => Venue.Full(venue, partner, contact)), 0, None, 0, None, 0, 0, 0, None)

      proposalRepo.listFull(cfp.slug, params)(ctx).unsafeRunSync().items shouldBe List(proposalFull, proposalFull2)
      proposalRepo.listFull(cfp.slug, params.page(2))(ctx).unsafeRunSync().items shouldBe List()
      proposalRepo.listFull(cfp.slug, params.pageSize(5))(ctx).unsafeRunSync().items shouldBe List(proposalFull, proposalFull2)
      proposalRepo.listFull(cfp.slug, params.search(proposal.title.value))(ctx).unsafeRunSync().items shouldBe List(proposalFull)
      proposalRepo.listFull(cfp.slug, params.orderBy("title"))(ctx).unsafeRunSync().items shouldBe List(proposalFull2, proposalFull)
      proposalRepo.listFull(cfp.slug, params.filters("status" -> "accepted"))(ctx).unsafeRunSync().items shouldBe List(proposalFull, proposalFull2)
      proposalRepo.listFull(cfp.slug, params.filters("slides" -> "true"))(ctx).unsafeRunSync().items shouldBe List(proposalFull)
      proposalRepo.listFull(cfp.slug, params.filters("video" -> "true"))(ctx).unsafeRunSync().items shouldBe List(proposalFull)
      proposalRepo.listFull(cfp.slug, params.filters("comment" -> "true"))(ctx).unsafeRunSync().items shouldBe List(proposalFull)
      proposalRepo.listFull(cfp.slug, params.filters("tags" -> "fp"))(ctx).unsafeRunSync().items shouldBe List(proposalFull)
      proposalRepo.listFull(cfp.slug, params.filters("orga-tags" -> "great"))(ctx).unsafeRunSync().items shouldBe List(proposalFull)
      proposalRepo.listFull(cfp.slug, params.filters("status" -> "accepted", "slides" -> "true"))(ctx).unsafeRunSync().items shouldBe List(proposalFull)
    }
    it("should be able to read correctly") {
      val (user, group, cfp, talk, ctx) = createCfpAndTalk().unsafeRunSync()
      val (partner, venue, contact) = createPartnerAndVenue()(ctx).unsafeRunSync()
      val eventCreated = eventRepo.create(eventData1.copy(venue = eventData1.venue.map(_ => venue.id)))(ctx).unsafeRunSync()
      eventRepo.publish(eventCreated.slug)(ctx).unsafeRunSync()
      val event = eventCreated.copy(published = Some(now))
      val proposalCreated = proposalRepo.create(talk.id, cfp.id, proposalData1, NonEmptyList.of(user.id))(ctx).unsafeRunSync()
      proposalRepo.accept(cfp.slug, proposalCreated.id, event.id)(ctx).unsafeRunSync()
      val proposal = proposalCreated.copy(status = Proposal.Status.Accepted, event = Some(event.id))
      val rating = Proposal.Rating(proposal.id, Proposal.Rating.Grade.Like, now, user.id)
      val ratingFull = Proposal.Rating.Full(rating, user, proposal)
      proposalRepo.rate(cfp.slug, proposal.id, rating.grade)(ctx).unsafeRunSync()
      val proposalFull = Proposal.Full(proposal, cfp, group, talk, Some(event), event.venue.map(_ => Venue.Full(venue, partner, contact)), 0, None, 0, None, 1, 1, 0, Some(rating.grade))

      proposalRepo.find(proposal.id).unsafeRunSync() shouldBe Some(proposal)
      proposalRepo.find(cfp.slug, proposal.id).unsafeRunSync() shouldBe Some(proposal)
      proposalRepo.find(talk.slug, cfp.slug)(ctx).unsafeRunSync() shouldBe Some(proposal)
      proposalRepo.findFull(cfp.slug, proposal.id)(ctx).unsafeRunSync() shouldBe Some(proposalFull)
      proposalRepo.findFull(proposal.id)(ctx).unsafeRunSync() shouldBe Some(proposalFull)
      proposalRepo.findFull(talk.slug, cfp.slug)(ctx).unsafeRunSync() shouldBe Some(proposalFull)
      proposalRepo.findPublicFull(group.id, proposal.id)(ctx.userAwareCtx).unsafeRunSync() shouldBe Some(proposalFull)
      // proposalRepo.listFull(params)(ctx: UserCtx).unsafeRunSync().items shouldBe List(proposalFull)
      // proposalRepo.listFull(params)(ctx: OrgaCtx).unsafeRunSync().items shouldBe List(proposalFull)
      proposalRepo.listFull(cfp.slug, params)(ctx).unsafeRunSync().items shouldBe List(proposalFull)
      proposalRepo.listFull(cfp.slug, Proposal.Status.Accepted, params)(ctx).unsafeRunSync().items shouldBe List(proposalFull)
      proposalRepo.listFull(user.id, params)(ctx).unsafeRunSync().items shouldBe List(proposalFull)
      proposalRepo.listFull(talk.id, params)(ctx).unsafeRunSync().items shouldBe List(proposalFull)
      proposalRepo.listAllPublicIds()(ctx.userAwareCtx).unsafeRunSync() shouldBe List(group.id -> proposal.id)
      proposalRepo.listAllPublicFull(user.id)(ctx.userAwareCtx).unsafeRunSync() shouldBe List(proposalFull)
      proposalRepo.listPublicFull(group.id, params)(ctx.userAwareCtx).unsafeRunSync().items shouldBe List(proposalFull)
      proposalRepo.list(List(proposal.id)).unsafeRunSync() shouldBe List(proposal)
      proposalRepo.listFull(List(proposal.id))(ctx.userAwareCtx).unsafeRunSync() shouldBe List(proposalFull)
      proposalRepo.listPublic(List(proposal.id)).unsafeRunSync() shouldBe List(proposal)
      proposalRepo.listPublicFull(List(proposal.id))(ctx.userAwareCtx).unsafeRunSync() shouldBe List(proposalFull)
      proposalRepo.listTags().unsafeRunSync() shouldBe proposal.tags.distinct
      proposalRepo.listOrgaTags()(ctx).unsafeRunSync() shouldBe proposal.orgaTags
      proposalRepo.listRatings(proposal.id).unsafeRunSync() shouldBe List(ratingFull)
      proposalRepo.listRatings(cfp.slug)(ctx).unsafeRunSync() shouldBe List(rating)
      proposalRepo.listRatings(List(proposal.id))(ctx).unsafeRunSync() shouldBe List(rating)
    }
    it("should compute score and comments") {
      val (user, group, cfp, partner, venue, contact, event, talk, proposal, ctx) = createProposal().unsafeRunSync()
      val c1 = commentRepo.addComment(proposal.id, commentData1.copy(answers = None))(ctx).unsafeRunSync()
      val c2 = commentRepo.addComment(proposal.id, commentData2.copy(answers = None))(ctx).unsafeRunSync()
      val c3 = commentRepo.addOrgaComment(proposal.id, commentData3.copy(answers = None))(ctx).unsafeRunSync()
      proposalRepo.rate(cfp.slug, proposal.id, Proposal.Rating.Grade.Like)(ctx).unsafeRunSync()

      val proposalFull = proposalRepo.findFull(proposal.id)(ctx).unsafeRunSync().get

      proposalFull.proposal shouldBe proposal
      proposalFull.cfp shouldBe cfp
      proposalFull.group shouldBe group
      proposalFull.talk shouldBe talk
      proposalFull.event shouldBe Some(event)
      proposalFull.venue shouldBe Some(Venue.Full(venue, partner, contact))
      proposalFull.userGrade shouldBe Some(Proposal.Rating.Grade.Like)
      proposalFull.speakerCommentCount shouldBe 2
      proposalFull.speakerLastComment shouldBe Some(c2.createdAt)
      proposalFull.orgaCommentCount shouldBe 1
      proposalFull.orgaLastComment shouldBe Some(c3.createdAt)
      proposalFull.score shouldBe 1
      proposalFull.likes shouldBe 1
      proposalFull.dislikes shouldBe 0
    }
    it("should compute comment counts") {
      val (user, group, cfp, partner, venue, contact, event, talk, proposal, ctx) = createProposal().unsafeRunSync()
      eventRepo.publish(event.slug)(ctx).unsafeRunSync()
      commentRepo.addComment(proposal.id, commentData1.copy(answers = None))(ctx).unsafeRunSync()
      commentRepo.addOrgaComment(proposal.id, commentData2.copy(answers = None))(ctx).unsafeRunSync()
      commentRepo.addOrgaComment(proposal.id, commentData3.copy(answers = None))(ctx).unsafeRunSync()
      val p = proposalRepo.findPublicFull(group.id, proposal.id)(ctx.userAwareCtx).unsafeRunSync().get
      p.speakerCommentCount shouldBe 1
      p.orgaCommentCount shouldBe 2
    }
    it("should check queries") {
      check(insert(proposal), s"INSERT INTO ${table.stripSuffix(" p")} (${mapFields(fields, _.stripPrefix("p."))}) VALUES (${mapFields(fields, _ => "?")})")
      check(update(user.id, group.slug, cfp.slug, proposal.id)(proposal.dataOrga, now), s"UPDATE $table SET title=?, duration=?, description=?, slides=?, video=?, tags=?, orga_tags=?, updated_at=?, updated_by=? WHERE p.id=$whereGroupAndCfp")
      check(update(user.id, talk.slug, cfp.slug)(proposal.data, now), s"UPDATE $table SET title=?, duration=?, description=?, message=?, slides=?, video=?, tags=?, updated_at=?, updated_by=? WHERE p.id=$whereCfpAndTalk")
      check(updateStatus(cfp.slug, proposal.id)(proposal.status, None), s"UPDATE $table SET status=?, event_id=? WHERE p.id=$whereCfp")
      check(updateSlides(cfp.slug, proposal.id)(urlSlides, user.id, now), s"UPDATE $table SET slides=?, updated_at=?, updated_by=? WHERE p.id=$whereCfp")
      check(updateSlides(user.id, talk.slug, cfp.slug)(urlSlides, user.id, now), s"UPDATE $table SET slides=?, updated_at=?, updated_by=? WHERE p.id=$whereCfpAndTalk")
      check(updateVideo(cfp.slug, proposal.id)(urlVideo, user.id, now), s"UPDATE $table SET video=?, updated_at=?, updated_by=? WHERE p.id=$whereCfp")
      check(updateVideo(user.id, talk.slug, cfp.slug)(urlVideo, user.id, now), s"UPDATE $table SET video=?, updated_at=?, updated_by=? WHERE p.id=$whereCfpAndTalk")
      check(updateOrgaTags(cfp.slug, proposal.id)(List(tag), user.id, now), s"UPDATE $table SET orga_tags=? WHERE p.id=$whereCfp")
      check(updateSpeakers(proposal.id)(proposal.speakers, user.id, now), s"UPDATE $table SET speakers=?, updated_at=?, updated_by=? WHERE p.id=?")
      check(selectOne(proposal.id), s"SELECT $fields FROM $table WHERE p.id=? $orderBy")
      check(selectOne(cfp.slug, proposal.id), s"SELECT $fields FROM $table WHERE p.id=$whereCfp $orderBy")
      check(selectOne(user.id, talk.slug, cfp.slug), s"SELECT $fields FROM $table WHERE p.id=$whereCfpAndTalk $orderBy")
      check(selectOneFull(cfp.slug, proposal.id), s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE p.id=$whereCfp GROUP BY $fieldsFull $orderByFull")
      check(selectOneFull(proposal.id), s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE p.id=? GROUP BY $fieldsFull $orderByFull")
      check(selectOneFull(talk.slug, cfp.slug), s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE t.slug=? AND c.slug=? AND p.speakers LIKE ? GROUP BY $fieldsFull $orderByFull")
      check(selectOnePublicFull(group.id, proposal.id), s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE c.group_id=? AND p.id=? AND e.published IS NOT NULL GROUP BY $fieldsFull $orderByFull")
      check(selectPageFullSpeaker(params), s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE p.speakers LIKE ? GROUP BY $fieldsFull $orderByFull LIMIT 20 OFFSET 0")
      check(selectPageFull(params), s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE c.group_id=? GROUP BY $fieldsFull $orderByFull LIMIT 20 OFFSET 0")
      check(selectPageFull(cfp.slug, params), s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE c.slug=? GROUP BY $fieldsFull $orderByFull LIMIT 20 OFFSET 0")
      check(selectPageFull(cfp.slug, Proposal.Status.Pending, params), s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE c.slug=? AND p.status=? GROUP BY $fieldsFull $orderByFull LIMIT 20 OFFSET 0")
      check(selectPageFull(user.id, params), s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE c.group_id=? AND p.speakers LIKE ? GROUP BY $fieldsFull $orderByFull LIMIT 20 OFFSET 0")
      check(selectPageFull(talk.id, params), s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE p.talk_id=? GROUP BY $fieldsFull $orderByFull LIMIT 20 OFFSET 0")
      check(selectAllPublicIds(), s"SELECT e.group_id, p.id FROM $tableWithEvent WHERE e.published IS NOT NULL $orderBy")
      check(selectAllFullPublic(user.id), s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE p.speakers LIKE ? AND e.published IS NOT NULL GROUP BY $fieldsFull ORDER BY p.created_at IS NULL, p.created_at DESC")
      check(selectPageFullPublic(group.id, params), s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE e.group_id=? AND e.published IS NOT NULL GROUP BY $fieldsFull $orderByFull LIMIT 20 OFFSET 0")
      check(selectAll(NonEmptyList.of(proposal.id)), s"SELECT $fields FROM $table WHERE p.id IN (?) $orderBy")
      check(selectAllFull(NonEmptyList.of(proposal.id)), s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE p.id IN (?) GROUP BY $fieldsFull $orderByFull")
      check(selectAllPublic(NonEmptyList.of(proposal.id)), s"SELECT $fields FROM $tableWithEvent WHERE p.id IN (?) AND e.published IS NOT NULL $orderBy")
      check(selectAllFullPublic(NonEmptyList.of(proposal.id)), s"SELECT $fieldsFull, $fieldsFullAgg, $fieldsFullCustom FROM $tableFull WHERE p.id IN (?) AND e.published IS NOT NULL GROUP BY $fieldsFull $orderByFull")
      check(selectTags(), s"SELECT p.tags FROM $table $orderBy")
      check(selectOrgaTags(group.id), s"SELECT p.orga_tags FROM $tableWithCfp WHERE c.group_id=? $orderBy")

      check(insert(rating), s"INSERT INTO ${ratingTable.stripSuffix(" pr")} (${mapFields(ratingFields, _.stripPrefix("pr."))}) VALUES (${mapFields(ratingFields, _ => "?")})")
      check(update(rating), s"UPDATE $ratingTable SET grade=?, created_at=? WHERE pr.proposal_id=? AND pr.created_by=?")
      check(selectOneRating(proposal.id, user.id), s"SELECT $ratingFields FROM $ratingTable WHERE pr.proposal_id=? AND pr.created_by=? $ratingOrderBy")
      check(selectAllRatings(proposal.id), s"SELECT $ratingFieldsFull FROM $ratingTableFull WHERE pr.proposal_id=? $ratingOrderBy")
      check(selectAllRatings(cfp.slug, user.id), s"SELECT $ratingFields FROM $ratingTable " +
        s"INNER JOIN $table ON pr.proposal_id=p.id " +
        s"INNER JOIN $cfpTable ON p.cfp_id=c.id " +
        s"WHERE c.slug=? AND pr.created_by=? $ratingOrderBy")
      check(selectAllRatings(user.id, NonEmptyList.of(proposal.id)), s"SELECT $ratingFields FROM $ratingTable WHERE pr.proposal_id IN (?) AND pr.created_by=? $ratingOrderBy")
    }
  }
}

object ProposalRepoSqlSpec {

  import RepoSpec._

  val table = "proposals p"
  val fields: String = mapFields("id, talk_id, cfp_id, event_id, status, title, duration, description, message, speakers, slides, video, tags, orga_tags, created_at, created_by, updated_at, updated_by", "p." + _)
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
    s"LEFT OUTER JOIN ${commentTable.replace(" co", " sco")} ON p.id=sco.proposal_id AND sco.kind=? " +
    s"LEFT OUTER JOIN ${commentTable.replace(" co", " oco")} ON p.id=oco.proposal_id AND oco.kind=?"
  private val fieldsFull = s"$fields, $cfpFields, $groupFields, $talkFields, $eventFields, $venueFields, $partnerFields, $contactFields"
  private val fieldsFullAgg = "COALESCE(COUNT(DISTINCT sco.id), 0) as speakerCommentCount, MAX(sco.created_at) as speakerLastComment, COALESCE(COUNT(DISTINCT oco.id), 0) as orgaCommentCount, MAX(oco.created_at) as orgaLastComment"
  private val fieldsFullCustom = "(SELECT COALESCE(SUM(grade), 0) FROM proposal_ratings pr WHERE pr.proposal_id=p.id) as score, (SELECT COUNT(grade) FROM proposal_ratings pr WHERE pr.proposal_id=p.id AND pr.grade=?) as likes, (SELECT COUNT(grade) FROM proposal_ratings pr WHERE pr.proposal_id=p.id AND pr.grade=?) as dislikes, (SELECT pr.grade FROM proposal_ratings pr WHERE pr.created_by=? AND pr.proposal_id=p.id) as user_grade"
  private val orderByFull = "ORDER BY (SELECT COALESCE(SUM(grade), 0) FROM proposal_ratings pr WHERE pr.proposal_id=p.id) IS NULL, (SELECT COALESCE(SUM(grade), 0) FROM proposal_ratings pr WHERE pr.proposal_id=p.id) DESC, p.created_at IS NULL, p.created_at DESC"

  private val ratingTableFull = s"$ratingTable INNER JOIN $userTable ON pr.created_by=u.id INNER JOIN $table ON pr.proposal_id=p.id"
  private val ratingFieldsFull = s"$ratingFields, $userFields, $fields"
}
