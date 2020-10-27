package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.syntax.string._
import gospeak.core.domain._
import gospeak.core.domain.utils.{OrgaCtx, UserAwareCtx, UserCtx}
import gospeak.core.services.storage.ProposalRepo
import gospeak.infra.services.storage.sql.ProposalRepoSql._
import gospeak.infra.services.storage.sql.database.Tables._
import gospeak.infra.services.storage.sql.database.tables.{PROPOSALS, PROPOSAL_RATINGS}
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.StringUtils
import gospeak.libs.scala.domain._
import gospeak.libs.sql.dsl._

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
  private val PROPOSALS_WITH_CFPS = PROPOSALS.joinOn(_.CFP_ID)
  private val PROPOSALS_WITH_EVENTS = PROPOSALS.joinOn(_.EVENT_ID).dropFields(EVENTS.getFields)
  val FILTERS = List(
    Table.Filter.Enum.fromValues("status", "Status", PROPOSALS.STATUS, Proposal.Status.all.map(s => (StringUtils.slugify(s.value), s.value, s))),
    Table.Filter.Bool.fromNullable("slides", "With slides", PROPOSALS.SLIDES),
    Table.Filter.Bool.fromNullable("video", "With video", PROPOSALS.VIDEO),
    Table.Filter.Bool.fromCountExpr("comment", "With comments", AggField("COALESCE(COUNT(DISTINCT sco.id), 0) + COALESCE(COUNT(DISTINCT oco.id), 0)")),
    Table.Filter.Value.fromField("tags", "With tag", PROPOSALS.TAGS),
    Table.Filter.Value.fromField("orga-tags", "With orga tag", PROPOSALS.ORGA_TAGS))
  val SORTS = List(
    Table.Sort("score", Field(PROPOSAL_RATINGS.select.fields(AggField("COALESCE(SUM(grade), 0)")).where(_.PROPOSAL_ID is PROPOSALS.ID, unsafe = true).orderBy().one[Long], "score").desc, PROPOSALS.CREATED_AT.desc),
    Table.Sort("title", TableField("LOWER(p.title)").asc),
    Table.Sort("comment", "last comment", TableField("MAX(GREATEST(sco.created_at, oco.created_at))").desc),
    Table.Sort("created", PROPOSALS.CREATED_AT.desc),
    Table.Sort("updated", PROPOSALS.UPDATED_AT.desc))
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

  private def PROPOSALS_FULL(user: Option[User]): Table.JoinTable = PROPOSALS_FULL_BASE.addFields(
    user.map(u => AggField(PROPOSAL_RATINGS.select.withFields(_.GRADE).where(pr => pr.CREATED_BY.is(u.id) and pr.PROPOSAL_ID.is(PROPOSALS.ID), unsafe = true).orderBy().one[Long], "user_grade")).getOrElse(AggField("null", "user_grade")))

  private def PROPOSALS_FULL(user: User): Table.JoinTable = PROPOSALS_FULL(Some(user))

  private val PROPOSAL_RATINGS_FULL = PROPOSAL_RATINGS.joinOn(_.CREATED_BY).joinOn(PROPOSAL_RATINGS.PROPOSAL_ID)
  private val PROPOSAL_RATINGS_WITH_PROPOSALS_AND_CFPS = PROPOSAL_RATINGS
    .joinOn(_.PROPOSAL_ID).dropFields(PROPOSALS.getFields)
    .joinOn(PROPOSALS.CFP_ID).dropFields(CFPS.getFields)

  private[sql] def insert(e: Proposal): Query.Insert[PROPOSALS] =
  // PROPOSALS.insert.values(e.id, e.talk, e.cfp, e.event, e.status, e.title, e.duration, e.description, e.message, e.speakers, e.slides, e.video, e.tags, e.orgaTags, e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    PROPOSALS.insert.values(fr0"${e.id}, ${e.talk}, ${e.cfp}, ${e.event}, ${e.status}, ${e.title}, ${e.duration}, ${e.description}, ${e.message}, ${e.speakers}, ${e.slides}, ${e.video}, ${e.tags}, ${e.orgaTags}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}")

  private[sql] def update(orga: User.Id, group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id)(data: Proposal.DataOrga, now: Instant): Query.Update[PROPOSALS] =
    PROPOSALS.update.set(_.TITLE, data.title).set(_.DURATION, data.duration).set(_.DESCRIPTION, data.description).set(_.SLIDES, data.slides).set(_.VIDEO, data.video).set(_.TAGS, data.tags).set(_.ORGA_TAGS, data.orgaTags).set(_.UPDATED_AT, now).set(_.UPDATED_BY, orga).where(where(orga, group, cfp, proposal))

  private[sql] def update(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug)(data: Proposal.Data, now: Instant): Query.Update[PROPOSALS] =
    PROPOSALS.update.set(_.TITLE, data.title).set(_.DURATION, data.duration).set(_.DESCRIPTION, data.description).set(_.MESSAGE, data.message).set(_.SLIDES, data.slides).set(_.VIDEO, data.video).set(_.TAGS, data.tags).set(_.UPDATED_AT, now).set(_.UPDATED_BY, speaker).where(where(speaker, talk, cfp))

  private[sql] def updateStatus(cfp: Cfp.Slug, id: Proposal.Id)(status: Proposal.Status, event: Option[Event.Id]): Query.Update[PROPOSALS] =
    PROPOSALS.update.set(_.STATUS, status).set(_.EVENT_ID, event).where(where(cfp, id))

  private[sql] def updateSlides(cfp: Cfp.Slug, id: Proposal.Id)(slides: Url.Slides, by: User.Id, now: Instant): Query.Update[PROPOSALS] =
    PROPOSALS.update.set(_.SLIDES, slides).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(cfp, id))

  private[sql] def updateSlides(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug)(slides: Url.Slides, by: User.Id, now: Instant): Query.Update[PROPOSALS] =
    PROPOSALS.update.set(_.SLIDES, slides).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(speaker, talk, cfp))

  private[sql] def updateVideo(cfp: Cfp.Slug, id: Proposal.Id)(video: Url.Video, by: User.Id, now: Instant): Query.Update[PROPOSALS] =
    PROPOSALS.update.set(_.VIDEO, video).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(cfp, id))

  private[sql] def updateVideo(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug)(video: Url.Video, by: User.Id, now: Instant): Query.Update[PROPOSALS] =
    PROPOSALS.update.set(_.VIDEO, video).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(speaker, talk, cfp))

  private[sql] def updateOrgaTags(cfp: Cfp.Slug, id: Proposal.Id)(orgaTags: List[Tag], by: User.Id, now: Instant): Query.Update[PROPOSALS] =
    PROPOSALS.update.set(_.ORGA_TAGS, orgaTags).where(where(cfp, id))

  private[sql] def updateSpeakers(id: Proposal.Id)(speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): Query.Update[PROPOSALS] =
    PROPOSALS.update.set(_.SPEAKERS, speakers).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(id))

  private[sql] def selectOne(id: Proposal.Id): Query.Select.Optional[Proposal] =
    PROPOSALS.select.where(where(id)).option[Proposal]

  private[sql] def selectOne(cfp: Cfp.Slug, id: Proposal.Id): Query.Select.Optional[Proposal] =
    PROPOSALS.select.where(where(cfp, id)).option[Proposal]

  private[sql] def selectOne(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug): Query.Select.Optional[Proposal] =
    PROPOSALS.select.where(where(speaker, talk, cfp)).option[Proposal]

  private[sql] def selectOneFull(cfp: Cfp.Slug, id: Proposal.Id)(implicit ctx: OrgaCtx): Query.Select.Optional[Proposal.Full] =
    PROPOSALS_FULL(ctx.user).select.where(where(cfp, id)).option[Proposal.Full]

  private[sql] def selectOneFull(id: Proposal.Id)(implicit ctx: UserCtx): Query.Select.Optional[Proposal.Full] =
    PROPOSALS_FULL(ctx.user).select.where(where(id)).option[Proposal.Full]

  private[sql] def selectOneFull(talk: Talk.Slug, cfp: Cfp.Slug)(implicit ctx: UserCtx): Query.Select.Optional[Proposal.Full] =
    PROPOSALS_FULL(ctx.user).select.where(TALKS.SLUG.is(talk) and CFPS.SLUG.is(cfp) and PROPOSALS.SPEAKERS.like("%" + ctx.user.id.value + "%")).option[Proposal.Full]

  private[sql] def selectOnePublicFull(group: Group.Id, id: Proposal.Id)(implicit ctx: UserAwareCtx): Query.Select.Optional[Proposal.Full] =
    PROPOSALS_FULL(ctx.user).select.where(CFPS.GROUP_ID.is(group) and PROPOSALS.ID.is(id) and EVENTS.PUBLISHED.notNull).option[Proposal.Full]

  private[sql] def selectPageFullSpeaker(params: Page.Params)(implicit ctx: UserCtx): Query.Select.Paginated[Proposal.Full] =
    PROPOSALS_FULL(ctx.user).select.where(PROPOSALS.SPEAKERS.like("%" + ctx.user.id.value + "%")).page[Proposal.Full](params, ctx.toDb)

  private[sql] def selectPageFull(params: Page.Params)(implicit ctx: OrgaCtx): Query.Select.Paginated[Proposal.Full] =
    PROPOSALS_FULL(ctx.user).select.where(CFPS.GROUP_ID is ctx.group.id).page[Proposal.Full](params, ctx.toDb)

  private[sql] def selectPageFull(cfp: Cfp.Slug, params: Page.Params)(implicit ctx: OrgaCtx): Query.Select.Paginated[Proposal.Full] =
    PROPOSALS_FULL(ctx.user).select.where(CFPS.SLUG is cfp).page[Proposal.Full](params, ctx.toDb)

  private[sql] def selectPageFull(cfp: Cfp.Slug, status: Proposal.Status, params: Page.Params)(implicit ctx: OrgaCtx): Query.Select.Paginated[Proposal.Full] =
    PROPOSALS_FULL(ctx.user).select.where(CFPS.SLUG.is(cfp) and PROPOSALS.STATUS.is(status)).page[Proposal.Full](params, ctx.toDb)

  private[sql] def selectPageFull(speaker: User.Id, params: Page.Params)(implicit ctx: OrgaCtx): Query.Select.Paginated[Proposal.Full] =
    PROPOSALS_FULL(ctx.user).select.where(CFPS.GROUP_ID.is(ctx.group.id) and PROPOSALS.SPEAKERS.like("%" + speaker.value + "%")).page[Proposal.Full](params, ctx.toDb)

  private[sql] def selectPageFull(talk: Talk.Id, params: Page.Params)(implicit ctx: UserCtx): Query.Select.Paginated[Proposal.Full] =
    PROPOSALS_FULL(ctx.user).select.where(PROPOSALS.TALK_ID is talk).page[Proposal.Full](params, ctx.toDb)

  private[sql] def selectAllPublicIds()(implicit ctx: UserAwareCtx): Query.Select.All[(Group.Id, Proposal.Id)] =
    PROPOSALS_WITH_EVENTS.select.fields(EVENTS.GROUP_ID, PROPOSALS.ID).where(EVENTS.PUBLISHED.notNull).all[(Group.Id, Proposal.Id)]

  private[sql] def selectAllFullPublic(speaker: User.Id)(implicit ctx: UserAwareCtx): Query.Select.All[Proposal.Full] =
    PROPOSALS_FULL(ctx.user).select.where(PROPOSALS.SPEAKERS.like("%" + speaker.value + "%") and EVENTS.PUBLISHED.notNull).orderBy(PROPOSALS.CREATED_AT.desc).all[Proposal.Full]

  private[sql] def selectPageFullPublic(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): Query.Select.Paginated[Proposal.Full] =
    PROPOSALS_FULL(ctx.user).select.where(EVENTS.GROUP_ID.is(group) and EVENTS.PUBLISHED.notNull).page[Proposal.Full](params, ctx.toDb)

  private[sql] def selectAll(ids: NonEmptyList[Proposal.Id]): Query.Select.All[Proposal] =
    PROPOSALS.select.where(_.ID in ids).all[Proposal]

  private[sql] def selectAllFull(ids: NonEmptyList[Proposal.Id])(implicit ctx: UserAwareCtx): Query.Select.All[Proposal.Full] =
    PROPOSALS_FULL(ctx.user).select.where(PROPOSALS.ID in ids).all[Proposal.Full]

  private[sql] def selectAllPublic(ids: NonEmptyList[Proposal.Id]): Query.Select.All[Proposal] =
    PROPOSALS_WITH_EVENTS.select.where(PROPOSALS.ID.in(ids) and EVENTS.PUBLISHED.notNull).all[Proposal]

  private[sql] def selectAllFullPublic(ids: NonEmptyList[Proposal.Id])(implicit ctx: UserAwareCtx): Query.Select.All[Proposal.Full] =
    PROPOSALS_FULL(ctx.user).select.where(PROPOSALS.ID.in(ids) and EVENTS.PUBLISHED.notNull).all[Proposal.Full]

  private[sql] def selectTags(): Query.Select.All[List[Tag]] =
    PROPOSALS.select.withFields(_.TAGS).all[List[Tag]]

  private[sql] def selectOrgaTags(group: Group.Id): Query.Select.All[List[Tag]] =
    PROPOSALS_WITH_CFPS.select.fields(PROPOSALS.ORGA_TAGS).where(CFPS.GROUP_ID is group).all[List[Tag]]

  private[sql] def insert(e: Proposal.Rating): Query.Insert[PROPOSAL_RATINGS] =
    PROPOSAL_RATINGS.insert.values(e.proposal, e.grade, e.createdAt, e.createdBy)

  private[sql] def update(e: Proposal.Rating): Query.Update[PROPOSAL_RATINGS] =
    PROPOSAL_RATINGS.update.set(_.GRADE, e.grade).set(_.CREATED_AT, e.createdAt).where(pr => pr.PROPOSAL_ID.is(e.proposal) and pr.CREATED_BY.is(e.createdBy))

  private[sql] def selectOneRating(id: Proposal.Id, user: User.Id): Query.Select.Optional[Proposal.Rating] =
    PROPOSAL_RATINGS.select.where(pr => pr.PROPOSAL_ID.is(id) and pr.CREATED_BY.is(user)).option[Proposal.Rating]

  private[sql] def selectAllRatings(id: Proposal.Id): Query.Select.All[Proposal.Rating.Full] =
    PROPOSAL_RATINGS_FULL.select.where(PROPOSAL_RATINGS.PROPOSAL_ID is id).all[Proposal.Rating.Full]

  private[sql] def selectAllRatings(cfp: Cfp.Slug, user: User.Id): Query.Select.All[Proposal.Rating] =
    PROPOSAL_RATINGS_WITH_PROPOSALS_AND_CFPS.select.where(CFPS.SLUG.is(cfp) and PROPOSAL_RATINGS.CREATED_BY.is(user)).all[Proposal.Rating]

  private[sql] def selectAllRatings(user: User.Id, proposals: NonEmptyList[Proposal.Id]): Query.Select.All[Proposal.Rating] =
    PROPOSAL_RATINGS.select.where(pr => pr.PROPOSAL_ID.in(proposals) and pr.CREATED_BY.is(user)).all[Proposal.Rating]

  private def where(id: Proposal.Id): Cond = PROPOSALS.ID is id

  private def where(cfp: Cfp.Slug, id: Proposal.Id): Cond = PROPOSALS.ID.is {
    PROPOSALS.joinOn(PROPOSALS.CFP_ID)
      .select.fields(PROPOSALS.ID)
      .where(PROPOSALS.ID.is(id) and CFPS.SLUG.is(cfp))
      .orderBy().one[Proposal.Id]
  }

  private def where(orga: User.Id, group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Cond = PROPOSALS.ID.is {
    PROPOSALS.joinOn(_.CFP_ID).joinOn(CFPS.GROUP_ID)
      .select.fields(PROPOSALS.ID)
      .where(PROPOSALS.ID.is(proposal) and CFPS.SLUG.is(cfp) and GROUPS.SLUG.is(group) and GROUPS.OWNERS.like("%" + orga.value + "%"))
      .orderBy().one[Proposal.Id]
  }

  private def where(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug): Cond = PROPOSALS.ID.is {
    PROPOSALS.joinOn(PROPOSALS.CFP_ID).joinOn(PROPOSALS.TALK_ID)
      .select.fields(PROPOSALS.ID)
      .where(CFPS.SLUG.is(cfp) and TALKS.SLUG.is(talk) and PROPOSALS.SPEAKERS.like("%" + speaker.value + "%"))
      .orderBy().one[Proposal.Id]
  }
}
