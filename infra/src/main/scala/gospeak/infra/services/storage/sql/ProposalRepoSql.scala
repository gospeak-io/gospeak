package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.syntax.string._
import doobie.util.fragment.Fragment
import gospeak.core.domain._
import gospeak.core.domain.utils.{BasicCtx, OrgaCtx, UserAwareCtx, UserCtx}
import gospeak.core.services.storage.ProposalRepo
import gospeak.infra.services.storage.sql.ProposalRepoSql._
import gospeak.infra.services.storage.sql.database.Tables._
import gospeak.infra.services.storage.sql.database.tables.{PROPOSALS, PROPOSAL_RATINGS}
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain._
import gospeak.libs.sql.doobie.{CustomField, DbCtx, Field, Table}
import gospeak.libs.sql.dsl
import gospeak.libs.sql.dsl.{AggField, Cond, NullField, Query}

class ProposalRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ProposalRepo {
  override def create(talk: Talk.Id, cfp: Cfp.Id, data: Proposal.Data, speakers: NonEmptyList[User.Id])(implicit ctx: UserCtx): IO[Proposal] = {
    val proposal = Proposal(talk, cfp, None, data, Proposal.Status.Pending, speakers, ctx.info)
    insert(proposal).run(xa).map(_ => proposal)
  }

  override def edit(cfp: Cfp.Slug, proposal: Proposal.Id, data: Proposal.DataOrga)(implicit ctx: OrgaCtx): IO[Unit] =
    update(ctx.user.id, ctx.group.slug, cfp, proposal)(data, ctx.now).run(xa)

  override def edit(talk: Talk.Slug, cfp: Cfp.Slug, data: Proposal.Data)(implicit ctx: UserCtx): IO[Unit] =
    update(ctx.user.id, talk, cfp)(data, ctx.now).run(xa)

  override def editSlides(cfp: Cfp.Slug, id: Proposal.Id, slides: Url.Slides)(implicit ctx: OrgaCtx): IO[Unit] =
    updateSlides(cfp, id)(slides, ctx.user.id, ctx.now).run(xa)

  override def editSlides(talk: Talk.Slug, cfp: Cfp.Slug, slides: Url.Slides)(implicit ctx: UserCtx): IO[Unit] =
    updateSlides(ctx.user.id, talk, cfp)(slides, ctx.user.id, ctx.now).run(xa)

  override def editVideo(cfp: Cfp.Slug, id: Proposal.Id, video: Url.Video)(implicit ctx: OrgaCtx): IO[Unit] =
    updateVideo(cfp, id)(video, ctx.user.id, ctx.now).run(xa)

  override def editVideo(talk: Talk.Slug, cfp: Cfp.Slug, video: Url.Video)(implicit ctx: UserCtx): IO[Unit] =
    updateVideo(ctx.user.id, talk, cfp)(video, ctx.user.id, ctx.now).run(xa)

  override def editOrgaTags(cfp: Cfp.Slug, id: Proposal.Id, orgaTags: List[Tag])(implicit ctx: OrgaCtx): IO[Unit] =
    updateOrgaTags(cfp, id)(orgaTags, ctx.user.id, ctx.now).run(xa)

  override def addSpeaker(proposal: Proposal.Id)(speaker: User.Id, by: User.Id, now: Instant): IO[Unit] =
    find(proposal).flatMap {
      case Some(proposalElt) =>
        if (proposalElt.speakers.toList.contains(speaker)) {
          IO.raiseError(new IllegalArgumentException("speaker already added"))
        } else {
          updateSpeakers(proposalElt.id)(proposalElt.speakers.append(speaker), by, now).run(xa)
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable talk"))
    }

  override def removeSpeaker(talk: Talk.Slug, cfp: Cfp.Slug, speaker: User.Id)(implicit ctx: UserCtx): IO[Unit] =
    removeSpeaker(find(talk, cfp))(speaker, ctx.user.id, ctx.now)

  override def removeSpeaker(cfp: Cfp.Slug, id: Proposal.Id, speaker: User.Id)(implicit ctx: OrgaCtx): IO[Unit] =
    removeSpeaker(find(cfp, id))(speaker, ctx.user.id, ctx.now)

  private def removeSpeaker(proposalIO: IO[Option[Proposal]])(speaker: User.Id, by: User.Id, now: Instant): IO[Unit] =
    proposalIO.flatMap {
      case Some(proposal) =>
        if (proposal.info.createdBy == speaker) {
          IO.raiseError(new IllegalArgumentException("proposal creator can't be removed"))
        } else if (proposal.speakers.toList.contains(speaker)) {
          proposal.speakers.filter(_ != speaker).toNel.map { speakers =>
            updateSpeakers(proposal.id)(speakers, by, now).run(xa)
          }.getOrElse {
            IO.raiseError(new IllegalArgumentException("last speaker can't be removed"))
          }
        } else {
          IO.raiseError(new IllegalArgumentException("user is not a speaker"))
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable proposal"))
    }

  override def accept(cfp: Cfp.Slug, id: Proposal.Id, event: Event.Id)(implicit ctx: OrgaCtx): IO[Unit] =
    updateStatus(cfp, id)(Proposal.Status.Accepted, Some(event)).run(xa) // FIXME track user & date

  override def cancel(cfp: Cfp.Slug, id: Proposal.Id, event: Event.Id)(implicit ctx: OrgaCtx): IO[Unit] =
    updateStatus(cfp, id)(Proposal.Status.Pending, None).run(xa) // FIXME track user & date + check event id was set

  override def reject(cfp: Cfp.Slug, id: Proposal.Id, by: User.Id, now: Instant): IO[Unit] =
    updateStatus(cfp, id)(Proposal.Status.Declined, None).run(xa) // FIXME track user & date

  override def reject(cfp: Cfp.Slug, id: Proposal.Id)(implicit ctx: OrgaCtx): IO[Unit] =
    updateStatus(cfp, id)(Proposal.Status.Declined, None).run(xa) // FIXME track user & date

  override def cancelReject(cfp: Cfp.Slug, id: Proposal.Id)(implicit ctx: OrgaCtx): IO[Unit] =
    updateStatus(cfp, id)(Proposal.Status.Pending, None).run(xa) // FIXME track user & date

  override def rate(cfp: Cfp.Slug, id: Proposal.Id, grade: Proposal.Rating.Grade)(implicit ctx: OrgaCtx): IO[Unit] =
    selectOneRating(id, ctx.user.id).run(xa).flatMap {
      case Some(_) => update(Proposal.Rating(id, grade, ctx.now, ctx.user.id)).run(xa)
      case None => insert(Proposal.Rating(id, grade, ctx.now, ctx.user.id)).run(xa)
    }

  override def find(proposal: Proposal.Id): IO[Option[Proposal]] = selectOne(proposal).run(xa)

  override def find(cfp: Cfp.Slug, id: Proposal.Id): IO[Option[Proposal]] = selectOne(cfp, id).run(xa)

  override def find(talk: Talk.Slug, cfp: Cfp.Slug)(implicit ctx: UserCtx): IO[Option[Proposal]] = selectOne(ctx.user.id, talk, cfp).run(xa)

  override def findFull(cfp: Cfp.Slug, id: Proposal.Id)(implicit ctx: OrgaCtx): IO[Option[Proposal.Full]] = selectOneFull(cfp, id).run(xa)

  override def findFull(proposal: Proposal.Id)(implicit ctx: UserCtx): IO[Option[Proposal.Full]] = selectOneFull(proposal).run(xa)

  override def findFull(talk: Talk.Slug, cfp: Cfp.Slug)(implicit ctx: UserCtx): IO[Option[Proposal.Full]] = selectOneFull(talk, cfp).run(xa)

  override def findPublicFull(group: Group.Id, proposal: Proposal.Id)(implicit ctx: UserAwareCtx): IO[Option[Proposal.Full]] = selectOnePublicFull(group, proposal).run(xa)

  override def listFull(params: Page.Params)(implicit ctx: UserCtx): IO[Page[Proposal.Full]] = selectPageFullSpeaker(params).run(xa)

  override def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Proposal.Full]] = selectPageFull(params).run(xa)

  override def listFull(cfp: Cfp.Slug, params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Proposal.Full]] = selectPageFull(cfp, params).run(xa)

  override def listFull(cfp: Cfp.Slug, status: Proposal.Status, params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Proposal.Full]] = selectPageFull(cfp, status, params).run(xa)

  override def listFull(speaker: User.Id, params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Proposal.Full]] = selectPageFull(speaker, params).run(xa)

  override def listFull(talk: Talk.Id, params: Page.Params)(implicit ctx: UserCtx): IO[Page[Proposal.Full]] = selectPageFull(talk, params).run(xa)

  override def listAllPublicIds()(implicit ctx: UserAwareCtx): IO[List[(Group.Id, Proposal.Id)]] = selectAllPublicIds().run(xa)

  override def listAllPublicFull(speaker: User.Id)(implicit ctx: UserAwareCtx): IO[List[Proposal.Full]] = selectAllFullPublic(speaker).run(xa)

  override def listPublicFull(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Proposal.Full]] = selectPageFullPublic(group, params).run(xa)

  override def list(ids: List[Proposal.Id]): IO[List[Proposal]] = runNel(selectAll, ids)

  override def listFull(ids: List[Proposal.Id])(implicit ctx: UserAwareCtx): IO[List[Proposal.Full]] = runNel(selectAllFull, ids)

  override def listPublic(ids: List[Proposal.Id]): IO[List[Proposal]] = runNel(selectAllPublic, ids)

  override def listPublicFull(ids: List[Proposal.Id])(implicit ctx: UserAwareCtx): IO[List[Proposal.Full]] = runNel[Proposal.Id, Proposal.Full](selectAllFullPublic(_), ids)

  override def listTags(): IO[List[Tag]] = selectTags().run(xa).map(_.flatten.distinct)

  override def listOrgaTags()(implicit ctx: OrgaCtx): IO[List[Tag]] = selectOrgaTags(ctx.group.id).run(xa).map(_.flatten.distinct)

  override def listRatings(id: Proposal.Id): IO[List[Proposal.Rating.Full]] = selectAllRatings(id).run(xa)

  override def listRatings(cfp: Cfp.Slug)(implicit ctx: OrgaCtx): IO[List[Proposal.Rating]] = selectAllRatings(cfp, ctx.user.id).run(xa)

  override def listRatings(proposals: List[Proposal.Id])(implicit ctx: OrgaCtx): IO[List[Proposal.Rating]] =
    proposals.toNel.map(selectAllRatings(ctx.user.id, _).run(xa)).getOrElse(IO.pure(List()))
}

object ProposalRepoSql {
  private val _ = proposalIdMeta // for intellij not remove DoobieMappings import
  private val table = Tables.proposals
  private val ratingTable = Tables.proposalRatings
  private val tableWithCfp = table
    .join(Tables.cfps, _.cfp_id -> _.id).get
  private val tableWithEvent = table
    .joinOpt(Tables.events, _.event_id -> _.id).get.dropFields(_.prefix == Tables.events.prefix)
  private val filters: List[Table.Filter] = List(
    Table.Filter.Enum.fromEnum("status", "Status", "p.status", Proposal.Status.all.map(s => s.value.toLowerCase -> s.value)),
    Table.Filter.Bool.fromNullable("slides", "With slides", "p.slides"),
    Table.Filter.Bool.fromNullable("video", "With video", "p.video"),
    Table.Filter.Bool.fromCountExpr("comment", "With comments", "COALESCE(COUNT(DISTINCT sco.id), 0) + COALESCE(COUNT(DISTINCT oco.id), 0)"),
    Table.Filter.Value.fromField("tags", "With tag", "p.tags"),
    Table.Filter.Value.fromField("orga-tags", "With orga tag", "p.orga_tags"))
  private val sorts: NonEmptyList[Table.Sort] = NonEmptyList.of(
    Table.Sort("score", Field("-(SELECT COALESCE(SUM(grade), 0) FROM proposal_ratings pr WHERE pr.proposal_id=p.id)", ""), Field("-created_at", "p")),
    Table.Sort("title", Field("LOWER(p.title)", "")),
    Table.Sort("comment", "last comment", Field("-MAX(GREATEST(sco.created_at, oco.created_at))", "")),
    Table.Sort("created", Field("-created_at", "p")),
    Table.Sort("updated", Field("-updated_at", "p")))
  private val tableFullBase: Table = table
    .join(Tables.cfps, _.cfp_id -> _.id).get
    .join(Tables.groups.dropFields(_.name.startsWith("location_")), _.group_id("c") -> _.id).get
    .join(Tables.talks, _.talk_id("p") -> _.id).get
    .joinOpt(Tables.events, _.event_id("p") -> _.id).get
    .joinOpt(Tables.venues.dropFields(_.name.startsWith("address_")), _.venue("e") -> _.id).get
    .joinOpt(Tables.partners, _.partner_id("v") -> _.id).get
    .joinOpt(Tables.contacts, _.contact_id("v") -> _.id).get
    .joinOpt(Tables.comments.setPrefix("sco"), fr0"sco.kind=${Comment.Kind.Proposal: Comment.Kind}", _.id("p") -> _.proposal_id).get.dropFields(_.prefix == "sco")
    .joinOpt(Tables.comments.setPrefix("oco"), fr0"oco.kind=${Comment.Kind.ProposalOrga: Comment.Kind}", _.id("p") -> _.proposal_id).get.dropFields(_.prefix == "oco")
    .aggregate("COALESCE(COUNT(DISTINCT sco.id), 0)", "speakerCommentCount")
    .aggregate("MAX(sco.created_at)", "speakerLastComment")
    .aggregate("COALESCE(COUNT(DISTINCT oco.id), 0)", "orgaCommentCount")
    .aggregate("MAX(oco.created_at)", "orgaLastComment")
    .addField(CustomField(fr0"(SELECT COALESCE(SUM(grade), 0) FROM proposal_ratings pr WHERE pr.proposal_id=p.id)", "score"))
    .addField(CustomField(fr0"(SELECT COUNT(grade) FROM proposal_ratings pr WHERE pr.proposal_id=p.id AND pr.grade=${Proposal.Rating.Grade.Like: Proposal.Rating.Grade})", "likes"))
    .addField(CustomField(fr0"(SELECT COUNT(grade) FROM proposal_ratings pr WHERE pr.proposal_id=p.id AND pr.grade=${Proposal.Rating.Grade.Dislike: Proposal.Rating.Grade})", "dislikes"))
    .filters(filters)
    .setSorts(sorts)

  private def tableFull(user: Option[User]): Table = tableFullBase.addField(CustomField(
    user.map(u => fr0"(SELECT pr.grade FROM proposal_ratings pr WHERE pr.created_by=${u.id} AND pr.proposal_id=p.id)").getOrElse(fr0"null"), "user_grade"))

  private def tableFull(user: User): Table = tableFull(Some(user))

  private val ratingTableFull = ratingTable
    .join(Tables.users, _.created_by -> _.id).get
    .join(Tables.proposals, _.proposal_id -> _.id).get
  private val ratingTableWithProposalCfp = ratingTable
    .join(table, _.proposal_id -> _.id).get
    .join(Tables.cfps, _.cfp_id -> _.id).get
    .dropFields(_.prefix == table.prefix)
    .dropFields(_.prefix == Tables.cfps.prefix)
  private val PROPOSALS_WITH_CFPS = PROPOSALS.joinOn(_.CFP_ID)
  private val PROPOSALS_WITH_EVENTS = PROPOSALS.joinOn(_.EVENT_ID).dropFields(EVENTS.getFields)
  val FILTERS = List(
    dsl.Table.Filter.Enum.fromValues("status", "Status", PROPOSALS.STATUS, Proposal.Status.all.map(s => s.value.toLowerCase -> s)),
    dsl.Table.Filter.Bool.fromNullable("slides", "With slides", PROPOSALS.SLIDES),
    dsl.Table.Filter.Bool.fromNullable("video", "With video", PROPOSALS.VIDEO),
    dsl.Table.Filter.Bool.fromCountExpr("comment", "With comments", AggField("COALESCE(COUNT(DISTINCT sco.id), 0) + COALESCE(COUNT(DISTINCT oco.id), 0)")),
    dsl.Table.Filter.Value.fromField("tags", "With tag", PROPOSALS.TAGS),
    dsl.Table.Filter.Value.fromField("orga-tags", "With orga tag", PROPOSALS.ORGA_TAGS))
  val SORTS = List(
    dsl.Table.Sort("score", dsl.Field(PROPOSAL_RATINGS.select.fields(AggField("COALESCE(SUM(grade), 0)")).where(_.PROPOSAL_ID is PROPOSALS.ID, unsafe = true).orderBy().one[Long], "score").desc, PROPOSALS.CREATED_AT.desc),
    dsl.Table.Sort("title", dsl.TableField("LOWER(p.title)").asc),
    dsl.Table.Sort("comment", "last comment", dsl.TableField("MAX(GREATEST(sco.created_at, oco.created_at))").desc),
    dsl.Table.Sort("created", PROPOSALS.CREATED_AT.desc),
    dsl.Table.Sort("updated", PROPOSALS.UPDATED_AT.desc))
  private val PROPOSALS_FULL_BASE = PROPOSALS
    .joinOn(_.CFP_ID)
    .joinOn(CFPS.GROUP_ID).dropFields(_.name.startsWith("location_"))
    .joinOn(PROPOSALS.TALK_ID)
    .joinOn(PROPOSALS.EVENT_ID)
    .joinOn(EVENTS.VENUE).dropFields(_.name.startsWith("address_"))
    .joinOn(VENUES.PARTNER_ID, _.LeftOuter)
    .joinOn(VENUES.CONTACT_ID)
    .join(COMMENTS.alias("sco"), _.LeftOuter).on(c => PROPOSALS.ID.is(c.PROPOSAL_ID) and c.KIND.is(Comment.Kind.Proposal)).dropFields(f => COMMENTS.has(f))
    .join(COMMENTS.alias("oco"), _.LeftOuter).on(c => PROPOSALS.ID.is(c.PROPOSAL_ID) and c.KIND.is(Comment.Kind.ProposalOrga)).dropFields(f => COMMENTS.has(f))
    .addFields(
      AggField("COALESCE(COUNT(DISTINCT sco.id), 0)").as("speakerCommentCount"),
      AggField("MAX(sco.created_at)").as("speakerLastComment"),
      AggField("COALESCE(COUNT(DISTINCT oco.id), 0)").as("orgaCommentCount"),
      AggField("MAX(oco.created_at)").as("orgaLastComment"),
      AggField(PROPOSAL_RATINGS.select.fields(AggField("COALESCE(SUM(grade), 0)")).where(_.PROPOSAL_ID is PROPOSALS.ID, unsafe = true).orderBy().one[Long], "score"),
      AggField(PROPOSAL_RATINGS.select.fields(AggField("COUNT(grade)")).where(pr => pr.PROPOSAL_ID.is(PROPOSALS.ID) and pr.GRADE.is(Proposal.Rating.Grade.Like), unsafe = true).orderBy().one[Long], "likes"),
      AggField(PROPOSAL_RATINGS.select.fields(AggField("COUNT(grade)")).where(pr => pr.PROPOSAL_ID.is(PROPOSALS.ID) and pr.GRADE.is(Proposal.Rating.Grade.Dislike), unsafe = true).orderBy().one[Long], "dislikes"))
    .filters(FILTERS)
    .sorts(SORTS)

  private def PROPOSALS_FULL(user: Option[User]): dsl.Table.JoinTable = PROPOSALS_FULL_BASE.addFields(
    user.map(u => AggField(PROPOSAL_RATINGS.select.withFields(_.GRADE).where(pr => pr.CREATED_BY.is(u.id) and pr.PROPOSAL_ID.is(PROPOSALS.ID), unsafe = true).orderBy().one[Long], "user_grade")).getOrElse(NullField("user_grade")))

  private def PROPOSALS_FULL(user: User): dsl.Table.JoinTable = PROPOSALS_FULL(Some(user))

  private val PROPOSAL_RATINGS_FULL = PROPOSAL_RATINGS.joinOn(_.CREATED_BY).joinOn(PROPOSAL_RATINGS.PROPOSAL_ID)
  private val PROPOSAL_RATINGS_WITH_PROPOSALS_AND_CFPS = PROPOSAL_RATINGS
    .joinOn(_.PROPOSAL_ID).dropFields(PROPOSALS.getFields)
    .joinOn(PROPOSALS.CFP_ID).dropFields(CFPS.getFields)

  private[sql] def insert(e: Proposal): Query.Insert[PROPOSALS] = {
    val values = fr0"${e.id}, ${e.talk}, ${e.cfp}, ${e.event}, ${e.status}, ${e.title}, ${e.duration}, ${e.description}, ${e.message}, ${e.speakers}, ${e.slides}, ${e.video}, ${e.tags}, ${e.orgaTags}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    val q1 = table.insert[Proposal](e, _ => values)
    // val q2 = PROPOSALS.insert.values(e.id, e.talk, e.cfp, e.event, e.status, e.title, e.duration, e.description, e.message, e.speakers, e.slides, e.video, e.tags, e.orgaTags, e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    val q2 = PROPOSALS.insert.values(values)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def update(orga: User.Id, group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id)(data: Proposal.DataOrga, now: Instant): Query.Update[PROPOSALS] = {
    val fields = fr0"title=${data.title}, duration=${data.duration}, description=${data.description}, slides=${data.slides}, video=${data.video}, tags=${data.tags}, orga_tags=${data.orgaTags}, updated_at=$now, updated_by=$orga"
    val q1 = table.update(fields).where(where(orga, group, cfp, proposal))
    val q2 = PROPOSALS.update.set(_.TITLE, data.title).set(_.DURATION, data.duration).set(_.DESCRIPTION, data.description).set(_.SLIDES, data.slides).set(_.VIDEO, data.video).set(_.TAGS, data.tags).set(_.ORGA_TAGS, data.orgaTags).set(_.UPDATED_AT, now).set(_.UPDATED_BY, orga).where(where2(orga, group, cfp, proposal))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def update(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug)(data: Proposal.Data, now: Instant): Query.Update[PROPOSALS] = {
    val fields = fr0"title=${data.title}, duration=${data.duration}, description=${data.description}, message=${data.message}, slides=${data.slides}, video=${data.video}, tags=${data.tags}, updated_at=$now, updated_by=$speaker"
    val q1 = table.update(fields).where(where(speaker, talk, cfp))
    val q2 = PROPOSALS.update.set(_.TITLE, data.title).set(_.DURATION, data.duration).set(_.DESCRIPTION, data.description).set(_.MESSAGE, data.message).set(_.SLIDES, data.slides).set(_.VIDEO, data.video).set(_.TAGS, data.tags).set(_.UPDATED_AT, now).set(_.UPDATED_BY, speaker).where(where2(speaker, talk, cfp))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def updateStatus(cfp: Cfp.Slug, id: Proposal.Id)(status: Proposal.Status, event: Option[Event.Id]): Query.Update[PROPOSALS] = {
    val q1 = table.update(fr0"status=$status, event_id=$event").where(where(cfp, id))
    val q2 = PROPOSALS.update.set(_.STATUS, status).set(_.EVENT_ID, event).where(where2(cfp, id))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def updateSlides(cfp: Cfp.Slug, id: Proposal.Id)(slides: Url.Slides, by: User.Id, now: Instant): Query.Update[PROPOSALS] = {
    val q1 = table.update(fr0"slides=$slides, updated_at=$now, updated_by=$by").where(where(cfp, id))
    val q2 = PROPOSALS.update.set(_.SLIDES, slides).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where2(cfp, id))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def updateSlides(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug)(slides: Url.Slides, by: User.Id, now: Instant): Query.Update[PROPOSALS] = {
    val q1 = table.update(fr0"slides=$slides, updated_at=$now, updated_by=$by").where(where(speaker, talk, cfp))
    val q2 = PROPOSALS.update.set(_.SLIDES, slides).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where2(speaker, talk, cfp))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def updateVideo(cfp: Cfp.Slug, id: Proposal.Id)(video: Url.Video, by: User.Id, now: Instant): Query.Update[PROPOSALS] = {
    val q1 = table.update(fr0"video=$video, updated_at=$now, updated_by=$by").where(where(cfp, id))
    val q2 = PROPOSALS.update.set(_.VIDEO, video).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where2(cfp, id))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def updateVideo(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug)(video: Url.Video, by: User.Id, now: Instant): Query.Update[PROPOSALS] = {
    val q1 = table.update(fr0"video=$video, updated_at=$now, updated_by=$by").where(where(speaker, talk, cfp))
    val q2 = PROPOSALS.update.set(_.VIDEO, video).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where2(speaker, talk, cfp))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def updateOrgaTags(cfp: Cfp.Slug, id: Proposal.Id)(orgaTags: List[Tag], by: User.Id, now: Instant): Query.Update[PROPOSALS] = {
    val q1 = table.update(fr0"orga_tags=$orgaTags").where(where(cfp, id))
    val q2 = PROPOSALS.update.set(_.ORGA_TAGS, orgaTags).where(where2(cfp, id))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def updateSpeakers(id: Proposal.Id)(speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): Query.Update[PROPOSALS] = {
    val q1 = table.update(fr0"speakers=$speakers, updated_at=$now, updated_by=$by").where(where(id))
    val q2 = PROPOSALS.update.set(_.SPEAKERS, speakers).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where2(id))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectOne(id: Proposal.Id): Query.Select.Optional[Proposal] = {
    val q1 = table.select[Proposal].where(where(id))
    val q2 = PROPOSALS.select.where(where2(id)).option[Proposal]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectOne(cfp: Cfp.Slug, id: Proposal.Id): Query.Select.Optional[Proposal] = {
    val q1 = table.select[Proposal].where(where(cfp, id))
    val q2 = PROPOSALS.select.where(where2(cfp, id)).option[Proposal]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectOne(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug): Query.Select.Optional[Proposal] = {
    val q1 = table.select[Proposal].where(where(speaker, talk, cfp))
    val q2 = PROPOSALS.select.where(where2(speaker, talk, cfp)).option[Proposal]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectOneFull(cfp: Cfp.Slug, id: Proposal.Id)(implicit ctx: OrgaCtx): Query.Select.Optional[Proposal.Full] = {
    val q1 = tableFull(ctx.user).select[Proposal.Full].where(where(cfp, id))
    val q2 = PROPOSALS_FULL(ctx.user).select.where(where2(cfp, id)).option[Proposal.Full]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectOneFull(id: Proposal.Id)(implicit ctx: UserCtx): Query.Select.Optional[Proposal.Full] = {
    val q1 = tableFull(ctx.user).select[Proposal.Full].where(where(id))
    val q2 = PROPOSALS_FULL(ctx.user).select.where(where2(id)).option[Proposal.Full]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectOneFull(talk: Talk.Slug, cfp: Cfp.Slug)(implicit ctx: UserCtx): Query.Select.Optional[Proposal.Full] = {
    val q1 = tableFull(ctx.user).select[Proposal.Full].where(fr0"t.slug=$talk AND c.slug=$cfp AND p.speakers LIKE ${"%" + ctx.user.id.value + "%"}")
    val q2 = PROPOSALS_FULL(ctx.user).select.where(TALKS.SLUG.is(talk) and CFPS.SLUG.is(cfp) and PROPOSALS.SPEAKERS.like("%" + ctx.user.id.value + "%")).option[Proposal.Full]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectOnePublicFull(group: Group.Id, id: Proposal.Id)(implicit ctx: UserAwareCtx): Query.Select.Optional[Proposal.Full] = {
    val q1 = tableFull(ctx.user).select[Proposal.Full].where(fr0"c.group_id=$group AND p.id=$id AND e.published IS NOT NULL")
    val q2 = PROPOSALS_FULL(ctx.user).select.where(CFPS.GROUP_ID.is(group) and PROPOSALS.ID.is(id) and EVENTS.PUBLISHED.notNull).option[Proposal.Full]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectPageFullSpeaker(params: Page.Params)(implicit ctx: UserCtx): Query.Select.Paginated[Proposal.Full] = {
    val q1 = tableFull(ctx.user).selectPage[Proposal.Full](params, adapt(ctx)).where(fr0"p.speakers LIKE ${"%" + ctx.user.id.value + "%"}")
    val q2 = PROPOSALS_FULL(ctx.user).select.where(PROPOSALS.SPEAKERS.like("%" + ctx.user.id.value + "%")).page[Proposal.Full](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectPageFull(params: Page.Params)(implicit ctx: OrgaCtx): Query.Select.Paginated[Proposal.Full] = {
    val q1 = tableFull(ctx.user).selectPage[Proposal.Full](params, adapt(ctx)).where(fr0"c.group_id=${ctx.group.id}")
    val q2 = PROPOSALS_FULL(ctx.user).select.where(CFPS.GROUP_ID is ctx.group.id).page[Proposal.Full](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectPageFull(cfp: Cfp.Slug, params: Page.Params)(implicit ctx: OrgaCtx): Query.Select.Paginated[Proposal.Full] = {
    val q1 = tableFull(ctx.user).selectPage[Proposal.Full](params, adapt(ctx)).where(fr0"c.slug=$cfp")
    val q2 = PROPOSALS_FULL(ctx.user).select.where(CFPS.SLUG is cfp).page[Proposal.Full](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectPageFull(cfp: Cfp.Slug, status: Proposal.Status, params: Page.Params)(implicit ctx: OrgaCtx): Query.Select.Paginated[Proposal.Full] = {
    val q1 = tableFull(ctx.user).selectPage[Proposal.Full](params, adapt(ctx)).where(fr0"c.slug=$cfp AND p.status=$status")
    val q2 = PROPOSALS_FULL(ctx.user).select.where(CFPS.SLUG.is(cfp) and PROPOSALS.STATUS.is(status)).page[Proposal.Full](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectPageFull(speaker: User.Id, params: Page.Params)(implicit ctx: OrgaCtx): Query.Select.Paginated[Proposal.Full] = {
    val q1 = tableFull(ctx.user).selectPage[Proposal.Full](params, adapt(ctx)).where(fr0"c.group_id=${ctx.group.id} AND p.speakers LIKE ${"%" + speaker.value + "%"}")
    val q2 = PROPOSALS_FULL(ctx.user).select.where(CFPS.GROUP_ID.is(ctx.group.id) and PROPOSALS.SPEAKERS.like("%" + speaker.value + "%")).page[Proposal.Full](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectPageFull(talk: Talk.Id, params: Page.Params)(implicit ctx: UserCtx): Query.Select.Paginated[Proposal.Full] = {
    val q1 = tableFull(ctx.user).selectPage[Proposal.Full](params, adapt(ctx)).where(fr0"p.talk_id=$talk")
    val q2 = PROPOSALS_FULL(ctx.user).select.where(PROPOSALS.TALK_ID is talk).page[Proposal.Full](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectAllPublicIds()(implicit ctx: UserAwareCtx): Query.Select.All[(Group.Id, Proposal.Id)] = {
    val q1 = tableWithEvent.select[(Group.Id, Proposal.Id)].fields(Field("group_id", "e"), Field("id", "p")).where(fr0"e.published IS NOT NULL")
    val q2 = PROPOSALS_WITH_EVENTS.select.fields(EVENTS.GROUP_ID, PROPOSALS.ID).where(EVENTS.PUBLISHED.notNull).all[(Group.Id, Proposal.Id)]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectAllFullPublic(speaker: User.Id)(implicit ctx: UserAwareCtx): Query.Select.All[Proposal.Full] = {
    val q1 = tableFull(ctx.user).select[Proposal.Full].where(fr0"p.speakers LIKE ${"%" + speaker.value + "%"} AND e.published IS NOT NULL").sort(Table.Sort("created", Field("-created_at", "p")))
    val q2 = PROPOSALS_FULL(ctx.user).select.where(PROPOSALS.SPEAKERS.like("%" + speaker.value + "%") and EVENTS.PUBLISHED.notNull).orderBy(PROPOSALS.CREATED_AT.desc).all[Proposal.Full]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectPageFullPublic(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): Query.Select.Paginated[Proposal.Full] = {
    val q1 = tableFull(ctx.user).selectPage[Proposal.Full](params, adapt(ctx)).where(fr0"e.group_id=$group AND e.published IS NOT NULL")
    val q2 = PROPOSALS_FULL(ctx.user).select.where(EVENTS.GROUP_ID.is(group) and EVENTS.PUBLISHED.notNull).page[Proposal.Full](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectAll(ids: NonEmptyList[Proposal.Id]): Query.Select.All[Proposal] = {
    val q1 = table.select[Proposal].where(Fragments.in(fr"p.id", ids))
    val q2 = PROPOSALS.select.where(_.ID in ids).all[Proposal]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectAllFull(ids: NonEmptyList[Proposal.Id])(implicit ctx: UserAwareCtx): Query.Select.All[Proposal.Full] = {
    val q1 = tableFull(ctx.user).select[Proposal.Full].where(Fragments.in(fr"p.id", ids))
    val q2 = PROPOSALS_FULL(ctx.user).select.where(PROPOSALS.ID in ids).all[Proposal.Full]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectAllPublic(ids: NonEmptyList[Proposal.Id]): Query.Select.All[Proposal] = {
    val q1 = tableWithEvent.select[Proposal].where(Fragments.in(fr"p.id", ids) ++ fr0"AND e.published IS NOT NULL")
    val q2 = PROPOSALS_WITH_EVENTS.select.where(PROPOSALS.ID.in(ids) and EVENTS.PUBLISHED.notNull).all[Proposal]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectAllFullPublic(ids: NonEmptyList[Proposal.Id])(implicit ctx: UserAwareCtx): Query.Select.All[Proposal.Full] = {
    val q1 = tableFull(ctx.user).select[Proposal.Full].where(Fragments.in(fr"p.id", ids) ++ fr0"AND e.published IS NOT NULL")
    val q2 = PROPOSALS_FULL(ctx.user).select.where(PROPOSALS.ID.in(ids) and EVENTS.PUBLISHED.notNull).all[Proposal.Full]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectTags(): Query.Select.All[List[Tag]] = {
    val q1 = table.select[List[Tag]].fields(Field("tags", "p"))
    val q2 = PROPOSALS.select.withFields(_.TAGS).all[List[Tag]]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectOrgaTags(group: Group.Id): Query.Select.All[List[Tag]] = {
    val q1 = tableWithCfp.select[List[Tag]].fields(Field("orga_tags", "p")).where(fr0"c.group_id=$group")
    val q2 = PROPOSALS_WITH_CFPS.select.fields(PROPOSALS.ORGA_TAGS).where(CFPS.GROUP_ID is group).all[List[Tag]]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def insert(e: Proposal.Rating): Query.Insert[PROPOSAL_RATINGS] = {
    val q1 = ratingTable.insert[Proposal.Rating](e, _ => fr0"${e.proposal}, ${e.grade}, ${e.createdAt}, ${e.createdBy}")
    val q2 = PROPOSAL_RATINGS.insert.values(e.proposal, e.grade, e.createdAt, e.createdBy)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def update(e: Proposal.Rating): Query.Update[PROPOSAL_RATINGS] = {
    val q1 = ratingTable.update(fr"grade=${e.grade}, created_at=${e.createdAt}").where(fr0"pr.proposal_id=${e.proposal} AND pr.created_by=${e.createdBy}")
    val q2 = PROPOSAL_RATINGS.update.set(_.GRADE, e.grade).set(_.CREATED_AT, e.createdAt).where(pr => pr.PROPOSAL_ID.is(e.proposal) and pr.CREATED_BY.is(e.createdBy))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectOneRating(id: Proposal.Id, user: User.Id): Query.Select.Optional[Proposal.Rating] = {
    val q1 = ratingTable.select[Proposal.Rating].where(fr0"pr.proposal_id=$id AND pr.created_by=$user")
    val q2 = PROPOSAL_RATINGS.select.where(pr => pr.PROPOSAL_ID.is(id) and pr.CREATED_BY.is(user)).option[Proposal.Rating]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectAllRatings(id: Proposal.Id): Query.Select.All[Proposal.Rating.Full] = {
    val q1 = ratingTableFull.select[Proposal.Rating.Full].where(fr0"pr.proposal_id=$id")
    val q2 = PROPOSAL_RATINGS_FULL.select.where(PROPOSAL_RATINGS.PROPOSAL_ID is id).all[Proposal.Rating.Full]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectAllRatings(cfp: Cfp.Slug, user: User.Id): Query.Select.All[Proposal.Rating] = {
    val q1 = ratingTableWithProposalCfp.select[Proposal.Rating].where(fr0"c.slug=$cfp AND pr.created_by=$user")
    val q2 = PROPOSAL_RATINGS_WITH_PROPOSALS_AND_CFPS.select.where(CFPS.SLUG.is(cfp) and PROPOSAL_RATINGS.CREATED_BY.is(user)).all[Proposal.Rating]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private[sql] def selectAllRatings(user: User.Id, proposals: NonEmptyList[Proposal.Id]): Query.Select.All[Proposal.Rating] = {
    val q1 = ratingTable.select[Proposal.Rating].where(Fragments.in(fr"pr.proposal_id", proposals) ++ fr0"AND pr.created_by=$user")
    val q2 = PROPOSAL_RATINGS.select.where(pr => pr.PROPOSAL_ID.in(proposals) and pr.CREATED_BY.is(user)).all[Proposal.Rating]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q2
  }

  private def where(id: Proposal.Id): Fragment = fr0"p.id=$id"

  private def where2(id: Proposal.Id): Cond = PROPOSALS.ID is id

  private def where(cfp: Cfp.Slug, id: Proposal.Id): Fragment =
    fr0"p.id=(SELECT p.id FROM " ++ table.value ++
      fr0" INNER JOIN " ++ Tables.cfps.value ++ fr0" ON p.cfp_id=c.id" ++
      fr0" WHERE p.id=$id AND c.slug=$cfp" ++ fr0")"

  private def where2(cfp: Cfp.Slug, id: Proposal.Id): Cond = PROPOSALS.ID.is {
    PROPOSALS.joinOn(PROPOSALS.CFP_ID)
      .select.fields(PROPOSALS.ID)
      .where(PROPOSALS.ID.is(id) and CFPS.SLUG.is(cfp))
      .orderBy().one[Proposal.Id]
  }

  private def where(orga: User.Id, group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Fragment =
    fr0"p.id=(SELECT p.id FROM " ++ table.value ++
      fr0" INNER JOIN " ++ Tables.cfps.value ++ fr0" ON p.cfp_id=c.id" ++
      fr0" INNER JOIN " ++ Tables.groups.value ++ fr0" ON c.group_id=g.id" ++
      fr0" WHERE p.id=$proposal AND c.slug=$cfp AND g.slug=$group AND g.owners LIKE ${"%" + orga.value + "%"}" ++ fr0")"

  private def where2(orga: User.Id, group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Cond = PROPOSALS.ID.is {
    PROPOSALS.joinOn(_.CFP_ID).joinOn(CFPS.GROUP_ID)
      .select.fields(PROPOSALS.ID)
      .where(PROPOSALS.ID.is(proposal) and CFPS.SLUG.is(cfp) and GROUPS.SLUG.is(group) and GROUPS.OWNERS.like("%" + orga.value + "%"))
      .orderBy().one[Proposal.Id]
  }

  private def where(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug): Fragment =
    fr0"p.id=(SELECT p.id FROM " ++ table.value ++
      fr0" INNER JOIN " ++ Tables.cfps.value ++ fr0" ON p.cfp_id=c.id" ++
      fr0" INNER JOIN " ++ Tables.talks.value ++ fr0" ON p.talk_id=t.id" ++
      fr0" WHERE c.slug=$cfp AND t.slug=$talk AND p.speakers LIKE ${"%" + speaker.value + "%"}" ++ fr0")"

  private def where2(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug): Cond = PROPOSALS.ID.is {
    PROPOSALS.joinOn(PROPOSALS.CFP_ID).joinOn(PROPOSALS.TALK_ID)
      .select.fields(PROPOSALS.ID)
      .where(CFPS.SLUG.is(cfp) and TALKS.SLUG.is(talk) and PROPOSALS.SPEAKERS.like("%" + speaker.value + "%"))
      .orderBy().one[Proposal.Id]
  }

  private def adapt(ctx: BasicCtx): DbCtx = DbCtx(ctx.now)
}
