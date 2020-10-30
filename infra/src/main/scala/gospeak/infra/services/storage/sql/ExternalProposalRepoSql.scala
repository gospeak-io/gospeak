package gospeak.infra.services.storage.sql

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime}

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.syntax.string._
import fr.loicknuchel.safeql.{Cond, Query}
import gospeak.core.domain._
import gospeak.core.domain.utils.{Info, UserAwareCtx, UserCtx}
import gospeak.core.services.storage.ExternalProposalRepo
import gospeak.infra.services.storage.sql.ExternalProposalRepoSql._
import gospeak.infra.services.storage.sql.database.Tables._
import gospeak.infra.services.storage.sql.database.tables.EXTERNAL_PROPOSALS
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericQuery._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.TimeUtils
import gospeak.libs.scala.domain._

class ExternalProposalRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ExternalProposalRepo {
  override def create(talk: Talk.Id, event: ExternalEvent.Id, data: ExternalProposal.Data, speakers: NonEmptyList[User.Id])(implicit ctx: UserCtx): IO[ExternalProposal] = {
    val proposal = ExternalProposal(data, talk, event, speakers, Info(ctx.user.id, ctx.now))
    insert(proposal).run(xa).map(_ => proposal)
  }

  override def edit(id: ExternalProposal.Id)(data: ExternalProposal.Data)(implicit ctx: UserCtx): IO[Unit] = update(id)(data, ctx.user.id, ctx.now).run(xa)

  override def editStatus(id: ExternalProposal.Id, status: Proposal.Status)(implicit ctx: UserCtx): IO[Unit] = updateStatus(id)(status, ctx.user.id).run(xa)

  override def editSlides(id: ExternalProposal.Id, slides: Url.Slides)(implicit ctx: UserCtx): IO[Unit] = updateSlides(id)(slides, ctx.user.id, ctx.now).run(xa)

  override def editVideo(id: ExternalProposal.Id, video: Url.Video)(implicit ctx: UserCtx): IO[Unit] = updateVideo(id)(video, ctx.user.id, ctx.now).run(xa)

  override def addSpeaker(id: ExternalProposal.Id, speaker: User.Id)(implicit ctx: UserCtx): IO[Unit] = addSpeaker(id, speaker, ctx.user.id, ctx.now)

  override def addSpeaker(id: ExternalProposal.Id, speaker: User.Id, by: User.Id, now: Instant): IO[Unit] =
    find(id).flatMap {
      case Some(externalProposal) =>
        if (externalProposal.speakers.toList.contains(speaker)) {
          IO.raiseError(new IllegalArgumentException(s"Speaker ${speaker.value} already added"))
        } else {
          updateSpeakers(externalProposal.id)(externalProposal.speakers.append(speaker), by, now).run(xa)
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable talk"))
    }

  override def removeSpeaker(id: ExternalProposal.Id, speaker: User.Id)(implicit ctx: UserCtx): IO[Unit] =
    find(id).flatMap {
      case Some(externalProposal) =>
        if (externalProposal.info.createdBy == speaker) {
          IO.raiseError(new IllegalArgumentException("talk creator can't be removed"))
        } else if (externalProposal.speakers.toList.contains(speaker)) {
          externalProposal.speakers.filter(_ != speaker).toNel.map { speakers =>
            updateSpeakers(id)(speakers, ctx.user.id, ctx.now).run(xa)
          }.getOrElse {
            IO.raiseError(new IllegalArgumentException("last speaker can't be removed"))
          }
        } else {
          IO.raiseError(new IllegalArgumentException("user is not a speaker"))
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable talk"))
    }

  override def remove(id: ExternalProposal.Id)(implicit ctx: UserCtx): IO[Unit] = delete(id, ctx.user.id).run(xa)

  override def listAllPublicIds(): IO[List[(ExternalEvent.Id, ExternalProposal.Id)]] = selectAllPublicIds().run(xa)

  override def listPublic(event: ExternalEvent.Id, params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[ExternalProposal]] = selectPage(event, Proposal.Status.Accepted, params).run(xa).map(_.fromSql)

  override def listCommon(talk: Talk.Id, params: Page.Params)(implicit ctx: UserCtx): IO[Page[CommonProposal]] = selectPageCommon(talk, params).run(xa).map(_.fromSql)

  override def listCommon(params: Page.Params)(implicit ctx: UserCtx): IO[Page[CommonProposal]] = selectPageCommon(params).run(xa).map(_.fromSql)

  override def listCurrentCommon(params: Page.Params)(implicit ctx: UserCtx): IO[Page[CommonProposal]] = selectPageCommonCurrent(params).run(xa).map(_.fromSql)

  override def listAllCommon(talk: Talk.Id): IO[List[CommonProposal]] = selectAllCommon(talk).run(xa)

  override def listAllCommon(user: User.Id, status: Proposal.Status): IO[List[CommonProposal]] = selectAllCommon(user, status).run(xa)

  override def listAllCommon(talk: Talk.Id, status: Proposal.Status): IO[List[CommonProposal]] = selectAllCommon(talk, status).run(xa)

  override def find(id: ExternalProposal.Id): IO[Option[ExternalProposal]] = selectOne(id).run(xa)

  override def findFull(id: ExternalProposal.Id): IO[Option[ExternalProposal.Full]] = selectOneFull(id).run(xa)

  override def listTags(): IO[List[Tag]] = selectTags().run(xa).map(_.flatten.distinct)
}

object ExternalProposalRepoSql {
  private val EXTERNAL_PROPOSALS_FULL = EXTERNAL_PROPOSALS.joinOn(_.TALK_ID).joinOn(EXTERNAL_PROPOSALS.EVENT_ID).dropFields(_.name.startsWith("location_"))
  private val COMMON_PROPOSALS = {
    val (g, c, e, t, p, ee, ep) = (GROUPS, CFPS, EVENTS, TALKS, PROPOSALS, EXTERNAL_EVENTS, EXTERNAL_PROPOSALS)
    val internalProposals = p.joinOn(_.TALK_ID).joinOn(p.EVENT_ID, _.LeftOuter).joinOn(p.CFP_ID).joinOn(c.GROUP_ID).select.fields(
      p.TITLE, p.STATUS, p.DURATION, p.SPEAKERS, p.SLIDES, p.VIDEO, p.TAGS, t.ID.as("talk_id"), t.SLUG.as("talk_slug"), t.DURATION.as("talk_duration"),
      ep.ID.asNull("ext_id"), ee.ID.asNull("event_ext_id"), ee.NAME.asNull("event_ext_name"), ee.KIND.asNull("event_ext_kind"), ee.LOGO.asNull("event_ext_logo"), ee.START.asNull("event_ext_start"), ee.URL.asNull("event_ext_url"), ep.URL.asNull("event_ext_proposal_url"),
      p.ID.as("int_id"), g.ID.as("group_id"), g.SLUG.as("group_slug"), g.NAME.as("group_name"), g.LOGO.as("group_logo"), g.OWNERS.as("group_owners"),
      c.ID.as("cfp_id"), c.SLUG.as("cfp_slug"), c.NAME.as("cfp_name"), e.ID.as("event_id"), e.SLUG.as("event_slug"), e.NAME.as("event_name"), e.KIND.as("event_kind"), e.START.as("event_start"),
      p.CREATED_AT, p.CREATED_BY, p.UPDATED_AT, p.UPDATED_BY
    ).where(e.PUBLISHED.notNull).orderBy()
    val externalProposals = ep.joinOn(_.TALK_ID).joinOn(ep.EVENT_ID).select.fields(
      ep.TITLE, ep.STATUS, ep.DURATION, ep.SPEAKERS, ep.SLIDES, ep.VIDEO, ep.TAGS, t.ID.as("talk_id"), t.SLUG.as("talk_slug"), t.DURATION.as("talk_duration"),
      ep.ID.as("ext_id"), ee.ID.as("event_ext_id"), ee.NAME.as("event_ext_name"), ee.KIND.as("event_ext_kind"), ee.LOGO.as("event_ext_logo"), ee.START.as("event_ext_start"), ee.URL.as("event_ext_url"), ep.URL.as("event_ext_proposal_url"),
      p.ID.asNull("int_id"), g.ID.asNull("group_id"), g.SLUG.asNull("group_slug"), g.NAME.asNull("group_name"), g.LOGO.asNull("group_logo"), g.OWNERS.asNull("group_owners"),
      c.ID.asNull("cfp_id"), c.SLUG.asNull("cfp_slug"), c.NAME.asNull("cfp_name"), e.ID.asNull("event_id"), e.SLUG.asNull("event_slug"), e.NAME.asNull("event_name"), e.KIND.asNull("event_kind"), e.START.asNull("event_start"),
      ep.CREATED_AT, ep.CREATED_BY, ep.UPDATED_AT, ep.UPDATED_BY
    ).orderBy()
    internalProposals.union(externalProposals, alias = Some("p"), sorts = List(("created", "created", List("-created_at"))), search = List("title"))
  }

  private[sql] def insert(e: ExternalProposal): Query.Insert[EXTERNAL_PROPOSALS] =
  // EXTERNAL_PROPOSALS.insert.values(e.id, e.talk, e.event, e.status, e.title, e.duration, e.description, e.message, e.speakers, e.slides, e.video, e.url, e.tags, e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    EXTERNAL_PROPOSALS.insert.values(fr0"${e.id}, ${e.talk}, ${e.event}, ${e.status}, ${e.title}, ${e.duration}, ${e.description}, ${e.message}, ${e.speakers}, ${e.slides}, ${e.video}, ${e.url}, ${e.tags}, " ++ insertInfo(e.info))

  private[sql] def update(id: ExternalProposal.Id)(e: ExternalProposal.Data, by: User.Id, now: Instant): Query.Update[EXTERNAL_PROPOSALS] =
    EXTERNAL_PROPOSALS.update.set(_.STATUS, e.status).set(_.TITLE, e.title).set(_.DURATION, e.duration).set(_.DESCRIPTION, e.description).set(_.MESSAGE, e.message).set(_.SLIDES, e.slides).set(_.VIDEO, e.video).set(_.URL, e.url).set(_.TAGS, e.tags).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(ep => ep.ID.is(id) and ep.SPEAKERS.like("%" + by.value + "%"))

  private[sql] def updateStatus(id: ExternalProposal.Id)(status: Proposal.Status, by: User.Id): Query.Update[EXTERNAL_PROPOSALS] =
    EXTERNAL_PROPOSALS.update.set(_.STATUS, status).where(where(id, by))

  private[sql] def updateSlides(id: ExternalProposal.Id)(slides: Url.Slides, by: User.Id, now: Instant): Query.Update[EXTERNAL_PROPOSALS] =
    EXTERNAL_PROPOSALS.update.set(_.SLIDES, slides).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(id, by))

  private[sql] def updateVideo(id: ExternalProposal.Id)(video: Url.Video, by: User.Id, now: Instant): Query.Update[EXTERNAL_PROPOSALS] =
    EXTERNAL_PROPOSALS.update.set(_.VIDEO, video).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(id, by))

  private[sql] def updateSpeakers(id: ExternalProposal.Id)(speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): Query.Update[EXTERNAL_PROPOSALS] =
    EXTERNAL_PROPOSALS.update.set(_.SPEAKERS, speakers).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(id, by))

  private[sql] def delete(id: ExternalProposal.Id, by: User.Id): Query.Delete[EXTERNAL_PROPOSALS] =
    EXTERNAL_PROPOSALS.delete.where(where(id, by))

  private[sql] def selectOne(id: ExternalProposal.Id): Query.Select.Optional[ExternalProposal] =
    EXTERNAL_PROPOSALS.select.where(_.ID is id).option[ExternalProposal](limit = true)

  private[sql] def selectOneFull(id: ExternalProposal.Id): Query.Select.Optional[ExternalProposal.Full] =
    EXTERNAL_PROPOSALS_FULL.select.where(EXTERNAL_PROPOSALS.ID is id).option[ExternalProposal.Full](limit = true)

  private[sql] def selectAllPublicIds(): Query.Select.All[(ExternalEvent.Id, ExternalProposal.Id)] =
    EXTERNAL_PROPOSALS.select.withFields(_.EVENT_ID, _.ID).where(_.STATUS is Proposal.Status.Accepted).all[(ExternalEvent.Id, ExternalProposal.Id)]

  private[sql] def selectPage(event: ExternalEvent.Id, status: Proposal.Status, params: Page.Params)(implicit ctx: UserAwareCtx): Query.Select.Paginated[ExternalProposal] =
    EXTERNAL_PROPOSALS.select.where(ep => ep.EVENT_ID.is(event) and ep.STATUS.is(status)).page[ExternalProposal](params.toSql, ctx.toSql)

  private[sql] def selectPageCommon(talk: Talk.Id, params: Page.Params)(implicit ctx: UserCtx): Query.Select.Paginated[CommonProposal] =
    COMMON_PROPOSALS.select.where(_.talk_id is talk).page[CommonProposal](params.toSql, ctx.toSql)

  private[sql] def selectPageCommon(params: Page.Params)(implicit ctx: UserCtx): Query.Select.Paginated[CommonProposal] =
    COMMON_PROPOSALS.select.where(_.speakers.like("%" + ctx.user.id.value + "%")).page[CommonProposal](params.toSql, ctx.toSql)

  private[sql] def selectPageCommonCurrent(params: Page.Params)(implicit ctx: UserCtx): Query.Select.Paginated[CommonProposal] = {
    val now = TimeUtils.toLocalDateTime(ctx.now)
    val status = COMMON_PROPOSALS.status[Proposal.Status]
    val pend = status is Proposal.Status.Pending
    val acc = status.is(Proposal.Status.Accepted) and (COMMON_PROPOSALS.event_start[LocalDateTime].gt(now) or COMMON_PROPOSALS.event_ext_start[LocalDateTime].gt(now)).par
    val dec = status.is(Proposal.Status.Declined) and COMMON_PROPOSALS.updated_at[Instant].gt(ctx.now.minus(30, ChronoUnit.DAYS))
    val cur = pend.par or acc.par or dec.par
    COMMON_PROPOSALS.select.where(p => p.speakers.like("%" + ctx.user.id.value + "%") and cur.par).page[CommonProposal](params.toSql, ctx.toSql)
  }

  private[sql] def selectAllCommon(talk: Talk.Id): Query.Select.All[CommonProposal] =
    COMMON_PROPOSALS.select.where(_.talk_id is talk).all[CommonProposal]

  private[sql] def selectAllCommon(user: User.Id, status: Proposal.Status): Query.Select.All[CommonProposal] =
    COMMON_PROPOSALS.select.where(p => p.speakers.like("%" + user.value + "%") and p.status.is(status)).all[CommonProposal]

  private[sql] def selectAllCommon(talk: Talk.Id, status: Proposal.Status): Query.Select.All[CommonProposal] =
    COMMON_PROPOSALS.select.where(p => p.talk_id.is(talk) and p.status.is(status)).all[CommonProposal]

  private[sql] def selectTags(): Query.Select.All[List[Tag]] =
    EXTERNAL_PROPOSALS.select.withFields(_.TAGS).all[List[Tag]]

  private def where(id: ExternalProposal.Id, user: User.Id): Cond = EXTERNAL_PROPOSALS.ID.is(id) and EXTERNAL_PROPOSALS.SPEAKERS.like("%" + user.value + "%")
}
