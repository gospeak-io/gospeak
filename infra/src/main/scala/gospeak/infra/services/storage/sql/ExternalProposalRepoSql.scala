package gospeak.infra.services.storage.sql

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime}

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.implicits._
import doobie.util.fragment.Fragment
import gospeak.core.domain._
import gospeak.core.domain.utils.{BasicCtx, Info, UserAwareCtx, UserCtx}
import gospeak.core.services.storage.ExternalProposalRepo
import gospeak.infra.services.storage.sql.ExternalProposalRepoSql._
import gospeak.infra.services.storage.sql.database.Tables._
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.{GenericQuery, GenericRepo}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.TimeUtils
import gospeak.libs.scala.domain._
import gospeak.libs.sql.doobie.{DbCtx, Field, Query, Table}
import gospeak.libs.sql.dsl.Cond

class ExternalProposalRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ExternalProposalRepo {
  override def create(talk: Talk.Id, event: ExternalEvent.Id, data: ExternalProposal.Data, speakers: NonEmptyList[User.Id])(implicit ctx: UserCtx): IO[ExternalProposal] =
    insert(ExternalProposal(data, talk, event, speakers, Info(ctx.user.id, ctx.now))).run(xa)

  override def edit(id: ExternalProposal.Id)(data: ExternalProposal.Data)(implicit ctx: UserCtx): IO[Done] =
    update(id)(data, ctx.user.id, ctx.now).run(xa)

  override def editStatus(id: ExternalProposal.Id, status: Proposal.Status)(implicit ctx: UserCtx): IO[Done] = updateStatus(id)(status, ctx.user.id).run(xa)

  override def editSlides(id: ExternalProposal.Id, slides: Url.Slides)(implicit ctx: UserCtx): IO[Done] = updateSlides(id)(slides, ctx.user.id, ctx.now).run(xa)

  override def editVideo(id: ExternalProposal.Id, video: Url.Video)(implicit ctx: UserCtx): IO[Done] = updateVideo(id)(video, ctx.user.id, ctx.now).run(xa)

  override def addSpeaker(id: ExternalProposal.Id, speaker: User.Id)(implicit ctx: UserCtx): IO[Done] = addSpeaker(id, speaker, ctx.user.id, ctx.now)

  override def addSpeaker(id: ExternalProposal.Id, speaker: User.Id, by: User.Id, now: Instant): IO[Done] =
    find(id).flatMap {
      case Some(externalProposal) =>
        if (externalProposal.speakers.toList.contains(speaker)) {
          IO.raiseError(new IllegalArgumentException(s"Speaker ${speaker.value} already added"))
        } else {
          updateSpeakers(externalProposal.id)(externalProposal.speakers.append(speaker), by, now).run(xa)
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable talk"))
    }

  override def removeSpeaker(id: ExternalProposal.Id, speaker: User.Id)(implicit ctx: UserCtx): IO[Done] =
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

  override def remove(id: ExternalProposal.Id)(implicit ctx: UserCtx): IO[Done] = delete(id, ctx.user.id).run(xa)

  override def listAllPublicIds(): IO[List[(ExternalEvent.Id, ExternalProposal.Id)]] = selectAllPublicIds().runList(xa)

  override def listPublic(event: ExternalEvent.Id, params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[ExternalProposal]] = selectPage(event, Proposal.Status.Accepted, params).run(xa)

  override def listCommon(talk: Talk.Id, params: Page.Params)(implicit ctx: UserCtx): IO[Page[CommonProposal]] = selectPageCommon(talk, params).run(xa)

  override def listCommon(params: Page.Params)(implicit ctx: UserCtx): IO[Page[CommonProposal]] = selectPageCommon(params).run(xa)

  override def listCurrentCommon(params: Page.Params)(implicit ctx: UserCtx): IO[Page[CommonProposal]] = selectPageCommonCurrent(params).run(xa)

  override def listAllCommon(talk: Talk.Id): IO[List[CommonProposal]] = selectAllCommon(talk).runList(xa)

  override def listAllCommon(user: User.Id, status: Proposal.Status): IO[List[CommonProposal]] = selectAllCommon(user, status).runList(xa)

  override def listAllCommon(talk: Talk.Id, status: Proposal.Status): IO[List[CommonProposal]] = selectAllCommon(talk, status).runList(xa)

  override def find(id: ExternalProposal.Id): IO[Option[ExternalProposal]] = selectOne(id).runOption(xa)

  override def findFull(id: ExternalProposal.Id): IO[Option[ExternalProposal.Full]] = selectOneFull(id).runOption(xa)

  override def listTags(): IO[List[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)
}

object ExternalProposalRepoSql {

  import GenericQuery._

  private val _ = externalProposalIdMeta // for intellij not remove DoobieMappings import
  private val table = Tables.externalProposals
  private val tableFull = table
    .join(Tables.talks, _.talk_id -> _.id).get
    .join(Tables.externalEvents, _.event_id -> _.id).get.dropFields(_.name.startsWith("location_"))
  private val commonTable = Table(
    name = "((SELECT p.title, p.status, p.duration, p.speakers, p.slides, p.video, p.tags, t.id as talk_id, t.slug as talk_slug, t.duration as talk_duration, null as ext_id, null as event_ext_id, null as event_ext_name, null as event_ext_kind, null as event_ext_logo, null as event_ext_start, null as event_ext_url, null as event_ext_proposal_url, p.id as int_id, g.id as group_id, g.slug as group_slug, g.name as group_name, g.logo as group_logo, g.owners as group_owners, c.id as cfp_id, c.slug as cfp_slug, c.name as cfp_name, e.id as event_id, e.slug as event_slug, e.name as event_name, e.kind as event_kind, e.start as event_start, p.created_at, p.created_by, p.updated_at, p.updated_by FROM proposals p INNER JOIN talks t ON p.talk_id=t.id LEFT OUTER JOIN events e ON p.event_id=e.id INNER JOIN cfps c ON p.cfp_id=c.id INNER JOIN groups g ON c.group_id=g.id WHERE e.published IS NOT NULL) " +
      "UNION (SELECT ep.title, ep.status, ep.duration, ep.speakers, ep.slides, ep.video, ep.tags, t.id as talk_id, t.slug as talk_slug, t.duration as talk_duration, ep.id as ext_id, ee.id as event_ext_id, ee.name as event_ext_name, ee.kind as event_ext_kind, ee.logo as event_ext_logo, ee.start as event_ext_start, ee.url as event_ext_url, ep.url as event_ext_proposal_url, null as int_id, null as group_id, null as group_slug, null as group_name, null as group_logo, null as group_owners, null as cfp_id, null as cfp_slug, null as cfp_name, null as event_id, null as event_slug, null as event_name, null as event_kind, null as event_start, ep.created_at, ep.created_by, ep.updated_at, ep.updated_by FROM external_proposals ep INNER JOIN talks t ON ep.talk_id=t.id INNER JOIN external_events ee ON ep.event_id=ee.id))",
    prefix = "p",
    joins = List(),
    fields = List(
      "title", "status", "duration", "speakers", "slides", "video", "tags",
      "talk_id", "talk_slug", "talk_duration",
      "ext_id", "event_ext_id", "event_ext_name", "event_ext_kind", "event_ext_logo", "event_ext_start", "event_ext_url", "event_ext_proposal_url",
      "int_id", "group_id", "group_slug", "group_name", "group_logo", "group_owners",
      "cfp_id", "cfp_slug", "cfp_name",
      "event_id", "event_slug", "event_name", "event_kind", "event_start",
      "created_at", "created_by", "updated_at", "updated_by").map(Field(_, "p")),
    aggFields = List(),
    customFields = List(),
    sorts = Table.Sorts("created", Field("-created_at", "p")),
    search = List("title").map(Field(_, "p")),
    filters = List())
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

  private[sql] def insert(e: ExternalProposal): Query.Insert[ExternalProposal] = {
    val values = fr0"${e.id}, ${e.talk}, ${e.event}, ${e.status}, ${e.title}, ${e.duration}, ${e.description}, ${e.message}, ${e.speakers}, ${e.slides}, ${e.video}, ${e.url}, ${e.tags}, " ++ insertInfo(e.info)
    val q1 = table.insert[ExternalProposal](e, _ => values)
    val q2 = EXTERNAL_PROPOSALS.insert.values(e.id, e.talk, e.event, e.status, e.title, e.duration, e.description, e.message, e.speakers, e.slides, e.video, e.url, e.tags, e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def update(id: ExternalProposal.Id)(e: ExternalProposal.Data, by: User.Id, now: Instant): Query.Update = {
    val fields = fr0"status=${e.status}, title=${e.title}, duration=${e.duration}, description=${e.description}, message=${e.message}, slides=${e.slides}, video=${e.video}, url=${e.url}, tags=${e.tags}, updated_at=$now, updated_by=$by"
    val q1 = table.update(fields).where(fr0"ep.id=$id AND ep.speakers LIKE ${"%" + by.value + "%"}")
    val q2 = EXTERNAL_PROPOSALS.update.set(_.STATUS, e.status).set(_.TITLE, e.title).set(_.DURATION, e.duration).set(_.DESCRIPTION, e.description).set(_.MESSAGE, e.message).set(_.SLIDES, e.slides).set(_.VIDEO, e.video).set(_.URL, e.url).set(_.TAGS, e.tags).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(ep => ep.ID.is(id) and ep.SPEAKERS.like("%" + by.value + "%"))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def updateStatus(id: ExternalProposal.Id)(status: Proposal.Status, by: User.Id): Query.Update = {
    val q1 = table.update(fr0"status=$status").where(where(id, by))
    val q2 = EXTERNAL_PROPOSALS.update.set(_.STATUS, status).where(where2(id, by))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def updateSlides(id: ExternalProposal.Id)(slides: Url.Slides, by: User.Id, now: Instant): Query.Update = {
    val q1 = table.update(fr0"slides=$slides, updated_at=$now, updated_by=$by").where(where(id, by))
    val q2 = EXTERNAL_PROPOSALS.update.setOpt(_.SLIDES, slides).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where2(id, by))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def updateVideo(id: ExternalProposal.Id)(video: Url.Video, by: User.Id, now: Instant): Query.Update = {
    val q1 = table.update(fr0"video=$video, updated_at=$now, updated_by=$by").where(where(id, by))
    val q2 = EXTERNAL_PROPOSALS.update.setOpt(_.VIDEO, video).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where2(id, by))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def updateSpeakers(id: ExternalProposal.Id)(speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): Query.Update = {
    val q1 = table.update(fr0"speakers=$speakers, updated_at=$now, updated_by=$by").where(where(id, by))
    val q2 = EXTERNAL_PROPOSALS.update.set(_.SPEAKERS, speakers).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where2(id, by))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def delete(id: ExternalProposal.Id, by: User.Id): Query.Delete = {
    val q1 = table.delete.where(fr0"ep.id=$id AND ep.speakers LIKE ${"%" + by.value + "%"}")
    val q2 = EXTERNAL_PROPOSALS.delete.where(where2(id, by))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOne(id: ExternalProposal.Id): Query.Select[ExternalProposal] = {
    val q1 = table.select[ExternalProposal].where(fr0"ep.id=$id").one
    val q2 = EXTERNAL_PROPOSALS.select.where(_.ID is id).option[ExternalProposal](limit = true)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOneFull(id: ExternalProposal.Id): Query.Select[ExternalProposal.Full] = {
    val q1 = tableFull.select[ExternalProposal.Full].where(fr0"ep.id=$id").one
    val q2 = EXTERNAL_PROPOSALS_FULL.select.where(EXTERNAL_PROPOSALS.ID is id).option[ExternalProposal.Full](limit = true)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAllPublicIds(): Query.Select[(ExternalEvent.Id, ExternalProposal.Id)] = {
    val q1 = table.select[(ExternalEvent.Id, ExternalProposal.Id)].fields(Field("event_id", "ep"), Field("id", "ep")).where(fr0"ep.status=${Proposal.Status.Accepted: Proposal.Status}")
    val q2 = EXTERNAL_PROPOSALS.select.withFields(_.EVENT_ID, _.ID).where(_.STATUS is Proposal.Status.Accepted).all[(ExternalEvent.Id, ExternalProposal.Id)]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPage(event: ExternalEvent.Id, status: Proposal.Status, params: Page.Params)(implicit ctx: UserAwareCtx): Query.SelectPage[ExternalProposal] = {
    val q1 = table.selectPage[ExternalProposal](params, adapt(ctx)).where(fr0"ep.event_id=$event AND ep.status=$status")
    val q2 = EXTERNAL_PROPOSALS.select.where(ep => ep.EVENT_ID.is(event) and ep.STATUS.is(status)).page[ExternalProposal](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPageCommon(talk: Talk.Id, params: Page.Params)(implicit ctx: UserCtx): Query.SelectPage[CommonProposal] = {
    val q1 = commonTable.selectPage[CommonProposal](params, adapt(ctx)).where(fr0"p.talk_id=$talk")
    val q2 = COMMON_PROPOSALS.select.where(_.talk_id is talk).page[CommonProposal](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPageCommon(params: Page.Params)(implicit ctx: UserCtx): Query.SelectPage[CommonProposal] = {
    val q1 = commonTable.selectPage[CommonProposal](params, adapt(ctx)).where(fr0"p.speakers LIKE ${"%" + ctx.user.id.value + "%"}")
    val q2 = COMMON_PROPOSALS.select.where(_.speakers.like("%" + ctx.user.id.value + "%")).page[CommonProposal](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPageCommonCurrent(params: Page.Params)(implicit ctx: UserCtx): Query.SelectPage[CommonProposal] = {
    val pending = fr0"p.status=${Proposal.Status.Pending: Proposal.Status}"
    val accepted = fr0"p.status=${Proposal.Status.Accepted: Proposal.Status} AND (p.event_start > ${ctx.now} OR p.event_ext_start > ${ctx.now})"
    val declined = fr0"p.status=${Proposal.Status.Declined: Proposal.Status} AND p.updated_at > ${ctx.now.minus(30, ChronoUnit.DAYS)}"
    val current = fr0"((" ++ pending ++ fr0") OR (" ++ accepted ++ fr0") OR (" ++ declined ++ fr0"))"
    val q1 = commonTable.selectPage[CommonProposal](params, adapt(ctx)).where(fr0"p.speakers LIKE ${"%" + ctx.user.id.value + "%"} AND " ++ current)

    val now = TimeUtils.toLocalDateTime(ctx.now)
    val status = COMMON_PROPOSALS.status[Proposal.Status]
    val pend = status is Proposal.Status.Pending
    val acc = status.is(Proposal.Status.Accepted) and (COMMON_PROPOSALS.event_start[LocalDateTime].gt(now) or COMMON_PROPOSALS.event_ext_start[LocalDateTime].gt(now)).par
    val dec = status.is(Proposal.Status.Declined) and COMMON_PROPOSALS.updated_at[Instant].gt(ctx.now.minus(30, ChronoUnit.DAYS))
    val cur = pend.par or acc.par or dec.par
    val q2 = COMMON_PROPOSALS.select.where(p => p.speakers.like("%" + ctx.user.id.value + "%") and cur.par).page[CommonProposal](params, ctx.toDb)

    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAllCommon(talk: Talk.Id): Query.Select[CommonProposal] = {
    val q1 = commonTable.select[CommonProposal].where(fr0"p.talk_id=$talk")
    val q2 = COMMON_PROPOSALS.select.where(_.talk_id is talk).all[CommonProposal]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAllCommon(user: User.Id, status: Proposal.Status): Query.Select[CommonProposal] = {
    val q1 = commonTable.select[CommonProposal].where(fr0"p.speakers LIKE ${"%" + user.value + "%"} AND p.status=$status")
    val q2 = COMMON_PROPOSALS.select.where(p => p.speakers.like("%" + user.value + "%") and p.status.is(status)).all[CommonProposal]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAllCommon(talk: Talk.Id, status: Proposal.Status): Query.Select[CommonProposal] = {
    val q1 = commonTable.select[CommonProposal].where(fr0"p.talk_id=$talk AND p.status=$status")
    val q2 = COMMON_PROPOSALS.select.where(p => p.talk_id.is(talk) and p.status.is(status)).all[CommonProposal]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectTags(): Query.Select[List[Tag]] = {
    val q1 = table.select[List[Tag]].fields(Field("tags", "ep"))
    val q2 = EXTERNAL_PROPOSALS.select.withFields(_.TAGS).all[List[Tag]]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private def where(id: ExternalProposal.Id, user: User.Id): Fragment = fr0"ep.id=$id AND ep.speakers LIKE ${"%" + user.value + "%"}"

  private def where2(id: ExternalProposal.Id, user: User.Id): Cond = EXTERNAL_PROPOSALS.ID.is(id) and EXTERNAL_PROPOSALS.SPEAKERS.like("%" + user.value + "%")

  private def adapt(ctx: BasicCtx): DbCtx = DbCtx(ctx.now)
}
