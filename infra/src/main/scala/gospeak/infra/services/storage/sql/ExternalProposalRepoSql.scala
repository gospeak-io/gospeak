package gospeak.infra.services.storage.sql

import java.time.Instant
import java.time.temporal.ChronoUnit

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.implicits._
import doobie.util.fragment.Fragment
import gospeak.core.domain._
import gospeak.core.domain.utils.{Info, UserAwareCtx, UserCtx}
import gospeak.core.services.storage.ExternalProposalRepo
import gospeak.infra.services.storage.sql.ExternalProposalRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import gospeak.infra.services.storage.sql.utils.DoobieUtils._
import gospeak.infra.services.storage.sql.utils.{GenericQuery, GenericRepo}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain._

class ExternalProposalRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ExternalProposalRepo {
  override def create(talk: Talk.Id, event: ExternalEvent.Id, data: ExternalProposal.Data, speakers: NonEmptyList[User.Id])(implicit ctx: UserCtx): IO[ExternalProposal] =
    insert(ExternalProposal(data, talk, event, speakers, Info(ctx.user.id, ctx.now))).run(xa)

  override def edit(id: ExternalProposal.Id)(data: ExternalProposal.Data)(implicit ctx: UserCtx): IO[Done] =
    update(id)(data, ctx.user.id, ctx.now).run(xa)

  override def editStatus(id: ExternalProposal.Id, status: Proposal.Status)(implicit ctx: UserCtx): IO[Done] = updateStatus(id)(status, ctx.user.id).run(xa)

  override def editSlides(id: ExternalProposal.Id, slides: Url.Slides)(implicit ctx: UserCtx): IO[Done] = updateSlides(id)(slides, ctx.user.id, ctx.now).run(xa)

  override def editVideo(id: ExternalProposal.Id, video: Url.Video)(implicit ctx: UserCtx): IO[Done] = updateVideo(id)(video, ctx.user.id, ctx.now).run(xa)

  override def addSpeaker(id: ExternalProposal.Id, by: User.Id)(implicit ctx: UserCtx): IO[Done] =
    find(id).flatMap {
      case Some(externalProposal) =>
        if (externalProposal.speakers.toList.contains(ctx.user.id)) {
          IO.raiseError(new IllegalArgumentException("speaker already added"))
        } else {
          updateSpeakers(externalProposal.id)(externalProposal.speakers.append(ctx.user.id), by, ctx.now).run(xa)
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

  override def listAllPublicIds(): IO[Seq[(ExternalEvent.Id, ExternalProposal.Id)]] = selectAllPublicIds().runList(xa)

  override def listPublic(event: ExternalEvent.Id, params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[ExternalProposal]] = selectPage(event, Proposal.Status.Accepted, params).run(xa)

  override def listCommon(talk: Talk.Id, params: Page.Params)(implicit ctx: UserCtx): IO[Page[CommonProposal]] = selectPageCommon(talk, params).run(xa)

  override def listCommon(params: Page.Params)(implicit ctx: UserCtx): IO[Page[CommonProposal]] = selectPageCommon(params).run(xa)

  override def listCurrentCommon(params: Page.Params)(implicit ctx: UserCtx): IO[Page[CommonProposal]] = selectPageCommonCurrent(params).run(xa)

  override def listAllCommon(talk: Talk.Id): IO[Seq[CommonProposal]] = selectAllCommon(talk).runList(xa)

  override def listAllCommon(user: User.Id, status: Proposal.Status): IO[Seq[CommonProposal]] = selectAllCommon(user, status).runList(xa)

  override def listAllCommon(talk: Talk.Id, status: Proposal.Status): IO[List[CommonProposal]] = selectAllCommon(talk, status).runList(xa)

  override def find(id: ExternalProposal.Id): IO[Option[ExternalProposal]] = selectOne(id).runOption(xa)

  override def findFull(id: ExternalProposal.Id): IO[Option[ExternalProposal.Full]] = selectOneFull(id).runOption(xa)

  override def listTags(): IO[Seq[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)
}

object ExternalProposalRepoSql {

  import GenericQuery._

  private val _ = externalProposalIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.externalProposals
  private val tableFull = table
    .join(Tables.talks, _.talk_id -> _.id).get
    .join(Tables.externalEvents, _.event_id -> _.id).get.dropFields(_.name.startsWith("location_"))
  private val commonTable = Table(
    name = "((SELECT p.title, p.status, p.duration, p.speakers, p.slides, p.video, p.tags, t.id as talk_id, t.slug as talk_slug, t.duration as talk_duration, null as ext_id, null as event_ext_id, null   as event_ext_name, null   as event_ext_kind, null   as event_ext_logo, null    as event_ext_start, null  as event_ext_url, null  as event_ext_proposal_url, p.id as int_id, g.id as group_id, g.slug as group_slug, g.name as group_name, g.logo as group_logo, g.owners as group_owners, c.id as cfp_id, c.slug as cfp_slug, c.name as cfp_name, e.id as event_id, e.slug as event_slug, e.name as event_name, e.kind as event_kind, e.start as event_start, p.created_at, p.created_by, p.updated_at, p.updated_by FROM proposals p          INNER JOIN talks t ON p.talk_id=t.id LEFT OUTER JOIN events e     ON p.event_id=e.id INNER JOIN cfps c ON p.cfp_id=c.id INNER JOIN groups g ON c.group_id=g.id WHERE e.published IS NOT NULL) " +
      "UNION (SELECT p.title, p.status, p.duration, p.speakers, p.slides, p.video, p.tags, t.id as talk_id, t.slug as talk_slug, t.duration as talk_duration, p.id as ext_id, e.id as event_ext_id, e.name as event_ext_name, e.kind as event_ext_kind, e.logo as event_ext_logo, e.start as event_ext_start, e.url as event_ext_url, p.url as event_ext_proposal_url, null as int_id, null as group_id, null   as group_slug, null   as group_name, null   as group_logo, null     as group_owners, null as cfp_id, null   as cfp_slug, null   as cfp_name, null as event_id, null   as event_slug, null   as event_name, null   as event_kind, null    as event_start, p.created_at, p.created_by, p.updated_at, p.updated_by FROM external_proposals p INNER JOIN talks t ON p.talk_id=t.id INNER JOIN external_events e ON p.event_id=e.id))",
    prefix = "p",
    joins = Seq(),
    fields = Seq(
      "title", "status", "duration", "speakers", "slides", "video", "tags",
      "talk_id", "talk_slug", "talk_duration",
      "ext_id", "event_ext_id", "event_ext_name", "event_ext_kind", "event_ext_logo", "event_ext_start", "event_ext_url", "event_ext_proposal_url",
      "int_id", "group_id", "group_slug", "group_name", "group_logo", "group_owners",
      "cfp_id", "cfp_slug", "cfp_name",
      "event_id", "event_slug", "event_name", "event_kind", "event_start",
      "created_at", "created_by", "updated_at", "updated_by").map(Field(_, "p")),
    aggFields = Seq(),
    customFields = Seq(),
    sorts = Sorts("created", Field("-created_at", "p")),
    search = Seq("title").map(Field(_, "p")),
    filters = Seq())

  private[sql] def insert(e: ExternalProposal): Insert[ExternalProposal] = {
    val values = fr0"${e.id}, ${e.talk}, ${e.event}, ${e.status}, ${e.title}, ${e.duration}, ${e.description}, ${e.message}, ${e.speakers}, ${e.slides}, ${e.video}, ${e.url}, ${e.tags}, " ++ insertInfo(e.info)
    table.insert[ExternalProposal](e, _ => values)
  }

  private[sql] def update(id: ExternalProposal.Id)(e: ExternalProposal.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"status=${e.status}, title=${e.title}, duration=${e.duration}, description=${e.description}, message=${e.message}, slides=${e.slides}, video=${e.video}, url=${e.url}, tags=${e.tags}, updated_at=$now, updated_by=$by"
    table.update(fields, fr0"WHERE id=$id AND speakers LIKE ${"%" + by.value + "%"}")
  }

  private[sql] def updateStatus(id: ExternalProposal.Id)(status: Proposal.Status, by: User.Id): Update =
    table.update(fr0"status=$status", where(id, by))

  private[sql] def updateSlides(id: ExternalProposal.Id)(slides: Url.Slides, by: User.Id, now: Instant): Update =
    table.update(fr0"slides=$slides, updated_at=$now, updated_by=$by", where(id, by))

  private[sql] def updateVideo(id: ExternalProposal.Id)(video: Url.Video, by: User.Id, now: Instant): Update =
    table.update(fr0"video=$video, updated_at=$now, updated_by=$by", where(id, by))

  private[sql] def updateSpeakers(id: ExternalProposal.Id)(speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): Update =
    table.update(fr0"speakers=$speakers, updated_at=$now, updated_by=$by", where(id, by))

  private[sql] def delete(id: ExternalProposal.Id, by: User.Id): Delete =
    table.delete(fr0"WHERE ep.id=$id AND ep.speakers LIKE ${"%" + by.value + "%"}")

  private[sql] def selectOne(id: ExternalProposal.Id): Select[ExternalProposal] =
    table.selectOne[ExternalProposal](fr0"WHERE ep.id=$id")

  private[sql] def selectOneFull(id: ExternalProposal.Id): Select[ExternalProposal.Full] =
    tableFull.selectOne[ExternalProposal.Full](fr0"WHERE ep.id=$id")

  private[sql] def selectAllPublicIds(): Select[(ExternalEvent.Id, ExternalProposal.Id)] =
    table.select[(ExternalEvent.Id, ExternalProposal.Id)](Seq(Field("event_id", "ep"), Field("id", "ep")), fr0"WHERE ep.status=${Proposal.Status.Accepted: Proposal.Status}")

  private[sql] def selectPage(event: ExternalEvent.Id, status: Proposal.Status, params: Page.Params)(implicit ctx: UserAwareCtx): SelectPage[ExternalProposal, UserAwareCtx] =
    table.selectPage[ExternalProposal, UserAwareCtx](params, fr0"WHERE ep.event_id=$event AND ep.status=$status")

  private[sql] def selectPageCommon(talk: Talk.Id, params: Page.Params)(implicit ctx: UserCtx): SelectPage[CommonProposal, UserCtx] =
    commonTable.selectPage[CommonProposal, UserCtx](params, fr0"WHERE p.talk_id=$talk")

  private[sql] def selectPageCommon(params: Page.Params)(implicit ctx: UserCtx): SelectPage[CommonProposal, UserCtx] =
    commonTable.selectPage[CommonProposal, UserCtx](params, fr0"WHERE p.speakers LIKE ${"%" + ctx.user.id.value + "%"}")

  private[sql] def selectPageCommonCurrent(params: Page.Params)(implicit ctx: UserCtx): SelectPage[CommonProposal, UserCtx] = {
    val pending = fr0"p.status=${Proposal.Status.Pending: Proposal.Status}"
    val accepted = fr0"p.status=${Proposal.Status.Accepted: Proposal.Status} AND (p.event_start > ${ctx.now} OR p.event_ext_start > ${ctx.now})"
    val declined = fr0"p.status=${Proposal.Status.Declined: Proposal.Status} AND p.updated_at > ${ctx.now.minus(30, ChronoUnit.DAYS)}"
    val current = fr0"((" ++ pending ++ fr0") OR (" ++ accepted ++ fr0") OR (" ++ declined ++ fr0"))"
    commonTable.selectPage[CommonProposal, UserCtx](params, fr0"WHERE p.speakers LIKE ${"%" + ctx.user.id.value + "%"} AND " ++ current)
  }

  private[sql] def selectAllCommon(talk: Talk.Id): Select[CommonProposal] =
    commonTable.select[CommonProposal](fr0"WHERE p.talk_id=$talk")

  private[sql] def selectAllCommon(user: User.Id, status: Proposal.Status): Select[CommonProposal] =
    commonTable.select[CommonProposal](fr0"WHERE p.speakers LIKE ${"%" + user.value + "%"} AND p.status=$status")

  private[sql] def selectAllCommon(talk: Talk.Id, status: Proposal.Status): Select[CommonProposal] =
    commonTable.select[CommonProposal](fr0"WHERE p.talk_id=$talk AND p.status=$status")

  private[sql] def selectTags(): Select[Seq[Tag]] =
    table.select[Seq[Tag]](Seq(Field("tags", "ep")))

  private def where(id: ExternalProposal.Id, user: User.Id): Fragment =
    fr0"WHERE ep.id=$id AND ep.speakers LIKE ${"%" + user.value + "%"}"
}
