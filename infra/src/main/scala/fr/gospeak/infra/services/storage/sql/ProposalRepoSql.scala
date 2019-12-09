package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.{OrgaCtx, UserCtx}
import fr.gospeak.core.services.storage.ProposalRepo
import fr.gospeak.infra.services.storage.sql.ProposalRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.{Field, Insert, Select, SelectPage, Update}
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain._

class ProposalRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ProposalRepo {
  override def create(talk: Talk.Id, cfp: Cfp.Id, data: Proposal.Data, speakers: NonEmptyList[User.Id])(implicit ctx: UserCtx): IO[Proposal] =
    insert(Proposal(talk, cfp, None, data, Proposal.Status.Pending, speakers, ctx.info)).run(xa)

  override def edit(cfp: Cfp.Slug, proposal: Proposal.Id, data: Proposal.DataOrga)(implicit ctx: OrgaCtx): IO[Done] =
    update(ctx.user.id, ctx.group.slug, cfp, proposal)(data, ctx.now).run(xa)

  override def edit(talk: Talk.Slug, cfp: Cfp.Slug, data: Proposal.Data)(implicit ctx: UserCtx): IO[Done] =
    update(ctx.user.id, talk, cfp)(data, ctx.now).run(xa)

  override def editSlides(cfp: Cfp.Slug, id: Proposal.Id, slides: Slides)(implicit ctx: OrgaCtx): IO[Done] =
    updateSlides(cfp, id)(slides, ctx.user.id, ctx.now).run(xa)

  override def editSlides(talk: Talk.Slug, cfp: Cfp.Slug, slides: Slides)(implicit ctx: UserCtx): IO[Done] =
    updateSlides(ctx.user.id, talk, cfp)(slides, ctx.user.id, ctx.now).run(xa)

  override def editVideo(cfp: Cfp.Slug, id: Proposal.Id, video: Video)(implicit ctx: OrgaCtx): IO[Done] =
    updateVideo(cfp, id)(video, ctx.user.id, ctx.now).run(xa)

  override def editVideo(talk: Talk.Slug, cfp: Cfp.Slug, video: Video)(implicit ctx: UserCtx): IO[Done] =
    updateVideo(ctx.user.id, talk, cfp)(video, ctx.user.id, ctx.now).run(xa)

  override def addSpeaker(proposal: Proposal.Id)(speaker: User.Id, by: User.Id, now: Instant): IO[Done] =
    find(proposal).flatMap {
      case Some(proposalElt) =>
        if (proposalElt.speakers.toList.contains(speaker)) {
          IO.raiseError(new IllegalArgumentException("speaker already added"))
        } else {
          updateSpeakers(proposalElt.id)(proposalElt.speakers.append(speaker), by, now).run(xa)
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable talk"))
    }

  override def removeSpeaker(talk: Talk.Slug, cfp: Cfp.Slug, speaker: User.Id)(implicit ctx: UserCtx): IO[Done] =
    removeSpeaker(find(talk, cfp))(speaker, ctx.user.id, ctx.now)

  override def removeSpeaker(cfp: Cfp.Slug, id: Proposal.Id, speaker: User.Id)(implicit ctx: OrgaCtx): IO[Done] =
    removeSpeaker(find(cfp, id))(speaker, ctx.user.id, ctx.now)

  private def removeSpeaker(proposal: IO[Option[Proposal]])(speaker: User.Id, by: User.Id, now: Instant): IO[Done] =
    proposal.flatMap {
      case Some(proposalElt) =>
        if (proposalElt.info.createdBy == speaker) {
          IO.raiseError(new IllegalArgumentException("proposal creator can't be removed"))
        } else if (proposalElt.speakers.toList.contains(speaker)) {
          NonEmptyList.fromList(proposalElt.speakers.filter(_ != speaker)).map { speakers =>
            updateSpeakers(proposalElt.id)(speakers, by, now).run(xa)
          }.getOrElse {
            IO.raiseError(new IllegalArgumentException("last speaker can't be removed"))
          }
        } else {
          IO.raiseError(new IllegalArgumentException("user is not a speaker"))
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable proposal"))
    }

  override def accept(cfp: Cfp.Slug, id: Proposal.Id, event: Event.Id)(implicit ctx: OrgaCtx): IO[Done] =
    updateStatus(cfp, id)(Proposal.Status.Accepted, Some(event)).run(xa) // FIXME track user & date

  override def cancel(cfp: Cfp.Slug, id: Proposal.Id, event: Event.Id)(implicit ctx: OrgaCtx): IO[Done] =
    updateStatus(cfp, id)(Proposal.Status.Pending, None).run(xa) // FIXME track user & date + check event id was set

  override def reject(cfp: Cfp.Slug, id: Proposal.Id, by: User.Id, now: Instant): IO[Done] =
    updateStatus(cfp, id)(Proposal.Status.Declined, None).run(xa) // FIXME track user & date

  override def reject(cfp: Cfp.Slug, id: Proposal.Id)(implicit ctx: OrgaCtx): IO[Done] =
    updateStatus(cfp, id)(Proposal.Status.Declined, None).run(xa) // FIXME track user & date

  override def cancelReject(cfp: Cfp.Slug, id: Proposal.Id)(implicit ctx: OrgaCtx): IO[Done] =
    updateStatus(cfp, id)(Proposal.Status.Pending, None).run(xa) // FIXME track user & date

  override def rate(cfp: Cfp.Slug, id: Proposal.Id, grade: Proposal.Rating.Grade)(implicit ctx: OrgaCtx): IO[Done] =
    selectOneRating(id, ctx.user.id).runOption(xa).flatMap {
      case Some(_) => update(Proposal.Rating(id, grade, ctx.now, ctx.user.id)).run(xa)
      case None => insert(Proposal.Rating(id, grade, ctx.now, ctx.user.id)).run(xa).map(_ => Done)
    }

  override def find(proposal: Proposal.Id): IO[Option[Proposal]] = selectOne(proposal).runOption(xa)

  override def find(cfp: Cfp.Slug, id: Proposal.Id): IO[Option[Proposal]] = selectOne(cfp, id).runOption(xa)

  override def find(talk: Talk.Slug, cfp: Cfp.Slug)(implicit ctx: UserCtx): IO[Option[Proposal]] = selectOne(ctx.user.id, talk, cfp).runOption(xa)

  override def findFull(cfp: Cfp.Slug, id: Proposal.Id): IO[Option[Proposal.Full]] = selectOneFull(cfp, id).runOption(xa)

  override def findFull(proposal: Proposal.Id): IO[Option[Proposal.Full]] = selectOneFull(proposal).runOption(xa)

  override def findFull(talk: Talk.Slug, cfp: Cfp.Slug)(implicit ctx: UserCtx): IO[Option[Proposal.Full]] = selectOneFull(talk, cfp, ctx.user.id).runOption(xa)

  override def findPublicFull(group: Group.Id, proposal: Proposal.Id): IO[Option[Proposal.Full]] = selectOnePublicFull(group, proposal).runOption(xa)

  override def list(cfp: Cfp.Id, status: Proposal.Status, params: Page.Params): IO[Page[Proposal]] = selectPage(cfp, status, params).run(xa)

  override def listFull(cfp: Cfp.Id, params: Page.Params): IO[Page[Proposal.Full]] = selectPageFull(cfp, params).run(xa)

  override def listFull(cfp: Cfp.Id, status: Proposal.Status, params: Page.Params): IO[Page[Proposal.Full]] = selectPageFull(cfp, status, params).run(xa)

  override def listFull(group: Group.Id, params: Page.Params): IO[Page[Proposal.Full]] = selectPageFull(group, params).run(xa)

  override def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Proposal.Full]] = selectPageFull(ctx.group.id, params).run(xa)

  override def listFull(talk: Talk.Id, params: Page.Params): IO[Page[Proposal.Full]] = selectPageFull(talk, params).run(xa)

  override def listFull(speaker: User.Id, params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Proposal.Full]] = selectPageFull(ctx.group.id, speaker, params).run(xa)

  override def listFull(params: Page.Params)(implicit ctx: UserCtx): IO[Page[Proposal.Full]] = selectPageFull(ctx.user.id, params).run(xa)

  override def listPublicFull(speaker: User.Id, params: Page.Params): IO[Page[Proposal.Full]] = selectPagePublicFull(speaker, params).run(xa)

  override def listPublicFull(group: Group.Id, params: Page.Params): IO[Page[Proposal.Full]] = selectPagePublicFull(group, params).run(xa)

  override def list(ids: Seq[Proposal.Id]): IO[Seq[Proposal]] = runNel(selectAll, ids)

  override def listPublicFull(ids: Seq[Proposal.Id]): IO[Seq[Proposal.Full]] = runNel(selectAllPublicFull, ids)

  override def listTags(): IO[Seq[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)

  override def listRatings(id: Proposal.Id): IO[Seq[Proposal.Rating.Full]] = selectAllRatings(id).runList(xa)

  override def listRatings(cfp: Cfp.Slug)(implicit ctx: OrgaCtx): IO[Seq[Proposal.Rating]] = selectAllRatings(cfp, ctx.user.id).runList(xa)

  override def listRatings(proposals: Seq[Proposal.Id])(implicit ctx: OrgaCtx): IO[Seq[Proposal.Rating]] =
    NonEmptyList.fromList(proposals.toList).map(selectAllRatings(ctx.user.id, _).runList(xa)).getOrElse(IO.pure(Seq()))
}

object ProposalRepoSql {
  private val _ = proposalIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.proposals
  private val ratingTable = Tables.proposalRatings
  private val tableFull = table
    .join(Tables.cfps, _.cfp_id -> _.id).get
    .join(Tables.groups.dropFields(_.name.startsWith("location_")), _.group_id("c") -> _.id).get
    .join(Tables.talks, _.talk_id("p") -> _.id).get
    .joinOpt(Tables.events, _.event_id("p") -> _.id).get
    .joinOpt(ratingTable, _.id("p") -> _.proposal_id).get.dropFields(_.prefix == ratingTable.prefix)
    .aggregate("COALESCE(SUM(pr.grade), 0)", "score")
    .aggregate("COALESCE((COUNT(pr.grade) + SUM(pr.grade)) / 2, 0)", "likes")
    .aggregate("COALESCE((COUNT(pr.grade) - SUM(pr.grade)) / 2, 0)", "dislikes")
    .setSorts(
      "score" -> Seq(Field("-COALESCE(SUM(pr.grade), 0)", ""), Field("-COALESCE(COUNT(pr.grade), 0)", ""), Field("-created_at", "p")),
      "created" -> Seq(Field("created_at", "p")),
      "title" -> Seq(Field("LOWER(p.title)", "")))
  private val ratingTableFull = ratingTable
    .join(Tables.users, _.created_by -> _.id).get
  private val ratingTableWithProposalCfp = ratingTable
    .join(table, _.proposal_id -> _.id).get
    .join(Tables.cfps, _.cfp_id -> _.id).get
    .dropFields(_.prefix == table.prefix)
    .dropFields(_.prefix == Tables.cfps.prefix)

  private[sql] def insert(e: Proposal): Insert[Proposal] = {
    val values = fr0"${e.id}, ${e.talk}, ${e.cfp}, ${e.event}, ${e.status}, ${e.title}, ${e.duration}, ${e.description}, ${e.speakers}, ${e.slides}, ${e.video}, ${e.tags}, ${e.orgaTags}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    table.insert[Proposal](e, _ => values)
  }

  private[sql] def insert(e: Proposal.Rating): Insert[Proposal.Rating] =
    ratingTable.insert[Proposal.Rating](e, _ => fr0"${e.proposal}, ${e.grade.value}, ${e.createdAt}, ${e.createdBy}")

  private[sql] def update(orga: User.Id, group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id)(data: Proposal.DataOrga, now: Instant): Update = {
    val fields = fr0"title=${data.title}, duration=${data.duration}, description=${data.description}, slides=${data.slides}, video=${data.video}, tags=${data.tags}, orga_tags=${data.orgaTags}, updated_at=$now, updated_by=$orga"
    table.update(fields, where(orga, group, cfp, proposal))
  }

  private[sql] def update(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug)(data: Proposal.Data, now: Instant): Update = {
    val fields = fr0"title=${data.title}, duration=${data.duration}, description=${data.description}, slides=${data.slides}, video=${data.video}, tags=${data.tags}, updated_at=$now, updated_by=$speaker"
    table.update(fields, where(speaker, talk, cfp))
  }

  private[sql] def update(e: Proposal.Rating): Update =
    ratingTable.update(fr"grade=${e.grade.value}, created_at=${e.createdAt}", fr0"WHERE pr.proposal_id=${e.proposal} AND pr.created_by=${e.createdBy}")

  private[sql] def updateStatus(cfp: Cfp.Slug, id: Proposal.Id)(status: Proposal.Status, event: Option[Event.Id]): Update =
    table.update(fr0"status=$status, event_id=$event", where(cfp, id))

  private[sql] def updateSlides(cfp: Cfp.Slug, id: Proposal.Id)(slides: Slides, by: User.Id, now: Instant): Update =
    table.update(fr0"slides=$slides, updated_at=$now, updated_by=$by", where(cfp, id))

  private[sql] def updateSlides(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug)(slides: Slides, by: User.Id, now: Instant): Update =
    table.update(fr0"slides=$slides, updated_at=$now, updated_by=$by", where(speaker, talk, cfp))

  private[sql] def updateVideo(cfp: Cfp.Slug, id: Proposal.Id)(video: Video, by: User.Id, now: Instant): Update =
    table.update(fr0"video=$video, updated_at=$now, updated_by=$by", where(cfp, id))

  private[sql] def updateVideo(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug)(video: Video, by: User.Id, now: Instant): Update =
    table.update(fr0"video=$video, updated_at=$now, updated_by=$by", where(speaker, talk, cfp))

  private[sql] def updateSpeakers(id: Proposal.Id)(speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): Update =
    table.update(fr0"speakers=$speakers, updated_at=$now, updated_by=$by", fr0"WHERE p.id=$id")

  private[sql] def selectOne(id: Proposal.Id): Select[Proposal] =
    table.select[Proposal](where(id))

  private[sql] def selectOne(cfp: Cfp.Slug, id: Proposal.Id): Select[Proposal] =
    table.select[Proposal](where(cfp, id))

  private[sql] def selectOne(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug): Select[Proposal] =
    table.select[Proposal](where(speaker, talk, cfp))

  private[sql] def selectOneFull(cfp: Cfp.Slug, id: Proposal.Id): Select[Proposal.Full] =
    tableFull.select[Proposal.Full](where(cfp, id))

  private[sql] def selectOneFull(id: Proposal.Id): Select[Proposal.Full] =
    tableFull.select[Proposal.Full](fr0"WHERE p.id=$id")

  private[sql] def selectOneFull(talk: Talk.Slug, cfp: Cfp.Slug, by: User.Id): Select[Proposal.Full] =
    tableFull.select[Proposal.Full](fr0"WHERE t.slug=$talk AND c.slug=$cfp AND p.speakers LIKE ${"%" + by.value + "%"}")

  private[sql] def selectOnePublicFull(group: Group.Id, id: Proposal.Id): Select[Proposal.Full] =
    tableFull.select[Proposal.Full](fr0"WHERE c.group_id=$group AND p.id=$id AND e.published IS NOT NULL")

  private[sql] def selectPage(cfp: Cfp.Id, status: Proposal.Status, params: Page.Params): SelectPage[Proposal] =
    table.selectPage[Proposal](params, fr0"WHERE p.cfp_id=$cfp AND p.status=$status")

  private[sql] def selectPageFull(cfp: Cfp.Id, params: Page.Params): SelectPage[Proposal.Full] =
    tableFull.selectPage[Proposal.Full](params, fr0"WHERE p.cfp_id=$cfp")

  private[sql] def selectPageFull(cfp: Cfp.Id, status: Proposal.Status, params: Page.Params): SelectPage[Proposal.Full] =
    tableFull.selectPage[Proposal.Full](params, fr0"WHERE p.cfp_id=$cfp AND p.status=$status")

  private[sql] def selectPageFull(group: Group.Id, params: Page.Params): SelectPage[Proposal.Full] =
    tableFull.selectPage[Proposal.Full](params, fr0"WHERE c.group_id=$group")

  private[sql] def selectPageFull(group: Group.Id, speaker: User.Id, params: Page.Params): SelectPage[Proposal.Full] =
    tableFull.selectPage[Proposal.Full](params, fr0"WHERE c.group_id=$group AND p.speakers LIKE ${"%" + speaker.value + "%"}")

  private[sql] def selectPageFull(talk: Talk.Id, params: Page.Params): SelectPage[Proposal.Full] =
    tableFull.selectPage[Proposal.Full](params, fr0"WHERE p.talk_id=$talk")

  private[sql] def selectPageFull(speaker: User.Id, params: Page.Params): SelectPage[Proposal.Full] =
    tableFull.selectPage[Proposal.Full](params, fr0"WHERE p.speakers LIKE ${"%" + speaker.value + "%"}")

  private[sql] def selectPagePublicFull(speaker: User.Id, params: Page.Params): SelectPage[Proposal.Full] =
    tableFull.selectPage[Proposal.Full](params, fr0"WHERE p.speakers LIKE ${"%" + speaker.value + "%"} AND e.published IS NOT NULL")

  private[sql] def selectPagePublicFull(group: Group.Id, params: Page.Params): SelectPage[Proposal.Full] =
    tableFull.selectPage[Proposal.Full](params, fr0"WHERE e.group_id=$group AND e.published IS NOT NULL")

  private[sql] def selectAll(ids: NonEmptyList[Proposal.Id]): Select[Proposal] =
    table.select[Proposal](fr0"WHERE " ++ Fragments.in(fr"id", ids))

  private[sql] def selectAllPublicFull(ids: NonEmptyList[Proposal.Id]): Select[Proposal.Full] =
    tableFull.select[Proposal.Full](fr0"WHERE " ++ Fragments.in(fr"p.id", ids) ++ fr0"AND e.published IS NOT NULL")

  private[sql] def selectTags(): Select[Seq[Tag]] =
    table.select[Seq[Tag]](Seq(Field("tags", "p")), Seq())

  private[sql] def selectOneRating(id: Proposal.Id, user: User.Id): Select[Proposal.Rating] =
    ratingTable.select[Proposal.Rating](fr0"WHERE pr.proposal_id=$id AND pr.created_by=$user")

  private[sql] def selectAllRatings(id: Proposal.Id): Select[Proposal.Rating.Full] =
    ratingTableFull.select[Proposal.Rating.Full](fr0"WHERE pr.proposal_id=$id")

  private[sql] def selectAllRatings(cfp: Cfp.Slug, user: User.Id): Select[Proposal.Rating] =
    ratingTableWithProposalCfp.select[Proposal.Rating](fr0"WHERE c.slug=$cfp AND pr.created_by=$user")

  private[sql] def selectAllRatings(user: User.Id, proposals: NonEmptyList[Proposal.Id]): Select[Proposal.Rating] =
    ratingTable.select[Proposal.Rating](fr0"WHERE " ++ Fragments.in(fr"pr.proposal_id", proposals) ++ fr0"AND pr.created_by=$user")

  private def where(id: Proposal.Id): Fragment =
    fr0"WHERE p.id=$id"

  private def where(cfp: Cfp.Slug, id: Proposal.Id): Fragment =
    fr0"WHERE p.id=(SELECT p.id FROM " ++ table.value ++
      fr0" INNER JOIN " ++ Tables.cfps.value ++ fr0" ON p.cfp_id=c.id" ++
      fr0" WHERE p.id=$id AND c.slug=$cfp" ++ fr0")"

  private def where(orga: User.Id, group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Fragment =
    fr0"WHERE p.id=(SELECT p.id FROM " ++ table.value ++
      fr0" INNER JOIN " ++ Tables.cfps.value ++ fr0" ON p.cfp_id=c.id" ++
      fr0" INNER JOIN " ++ Tables.groups.value ++ fr0" ON c.group_id=g.id" ++
      fr0" WHERE p.id=$proposal AND c.slug=$cfp AND g.slug=$group AND g.owners LIKE ${"%" + orga.value + "%"}" ++ fr0")"

  private def where(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug): Fragment =
    fr0"WHERE p.id=(SELECT p.id FROM " ++ table.value ++
      fr0" INNER JOIN " ++ Tables.cfps.value ++ fr0" ON p.cfp_id=c.id" ++
      fr0" INNER JOIN " ++ Tables.talks.value ++ fr0" ON p.talk_id=t.id" ++
      fr0" WHERE c.slug=$cfp AND t.slug=$talk AND p.speakers LIKE ${"%" + speaker.value + "%"}" ++ fr0")"
}
