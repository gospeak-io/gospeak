package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.services.storage.ProposalRepo
import fr.gospeak.infra.services.storage.sql.ProposalRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.{Field, Insert, Select, SelectPage, Update}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain._

class ProposalRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ProposalRepo {
  override def create(talk: Talk.Id, cfp: Cfp.Id, data: Proposal.Data, speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): IO[Proposal] =
    insert(Proposal(talk, cfp, None, data, Proposal.Status.Pending, speakers, Info(by, now))).run(xa)

  override def edit(orga: User.Id, group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id)(data: Proposal.Data, now: Instant): IO[Done] =
    update(orga, group, cfp, proposal)(data, now).run(xa)

  override def edit(talk: Talk.Slug, cfp: Cfp.Slug)(data: Proposal.Data, by: User.Id, now: Instant): IO[Done] =
    update(by, talk, cfp)(data, now).run(xa)

  override def editSlides(cfp: Cfp.Slug, id: Proposal.Id)(slides: Slides, by: User.Id, now: Instant): IO[Done] =
    updateSlides(cfp, id)(slides, by, now).run(xa)

  override def editSlides(talk: Talk.Slug, cfp: Cfp.Slug)(slides: Slides, by: User.Id, now: Instant): IO[Done] =
    updateSlides(by, talk, cfp)(slides, by, now).run(xa)

  override def editVideo(cfp: Cfp.Slug, id: Proposal.Id)(video: Video, by: User.Id, now: Instant): IO[Done] =
    updateVideo(cfp, id)(video, by, now).run(xa)

  override def editVideo(talk: Talk.Slug, cfp: Cfp.Slug)(video: Video, by: User.Id, now: Instant): IO[Done] =
    updateVideo(by, talk, cfp)(video, by, now).run(xa)

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

  override def removeSpeaker(talk: Talk.Slug, cfp: Cfp.Slug)(speaker: User.Id, by: User.Id, now: Instant): IO[Done] =
    removeSpeaker(find(by, talk, cfp))(speaker, by, now)

  override def removeSpeaker(cfp: Cfp.Slug, id: Proposal.Id)(speaker: User.Id, by: User.Id, now: Instant): IO[Done] =
    removeSpeaker(find(cfp, id))(speaker, by, now)

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

  override def accept(cfp: Cfp.Slug, id: Proposal.Id, event: Event.Id, by: User.Id, now: Instant): IO[Done] =
    updateStatus(cfp, id)(Proposal.Status.Accepted, Some(event)).run(xa) // FIXME track user & date

  override def cancel(cfp: Cfp.Slug, id: Proposal.Id, event: Event.Id, by: User.Id, now: Instant): IO[Done] =
    updateStatus(cfp, id)(Proposal.Status.Pending, None).run(xa) // FIXME track user & date + check event id was set

  override def reject(cfp: Cfp.Slug, id: Proposal.Id, by: User.Id, now: Instant): IO[Done] =
    updateStatus(cfp, id)(Proposal.Status.Declined, None).run(xa) // FIXME track user & date

  override def cancelReject(cfp: Cfp.Slug, id: Proposal.Id, by: User.Id, now: Instant): IO[Done] =
    updateStatus(cfp, id)(Proposal.Status.Pending, None).run(xa) // FIXME track user & date

  override def find(proposal: Proposal.Id): IO[Option[Proposal]] = selectOne(proposal).runOption(xa)

  override def find(cfp: Cfp.Slug, id: Proposal.Id): IO[Option[Proposal]] = selectOne(cfp, id).runOption(xa)

  override def find(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug): IO[Option[Proposal]] = selectOne(speaker, talk, cfp).runOption(xa)

  override def findFull(proposal: Proposal.Id): IO[Option[Proposal.Full]] = selectOneFull(proposal).runOption(xa)

  override def findFull(talk: Talk.Slug, cfp: Cfp.Slug)(by: User.Id): IO[Option[Proposal.Full]] = selectOneFull(talk, cfp, by).runOption(xa)

  override def findPublicFull(group: Group.Id, proposal: Proposal.Id): IO[Option[Proposal.Full]] = selectOnePublicFull(group, proposal).runOption(xa)

  override def list(cfp: Cfp.Id, status: Proposal.Status, params: Page.Params): IO[Page[Proposal]] = selectPage(cfp, status, params).run(xa)

  override def listFull(cfp: Cfp.Id, params: Page.Params): IO[Page[Proposal.Full]] = selectPageFull(cfp, params).run(xa)

  override def listFull(group: Group.Id, params: Page.Params): IO[Page[Proposal.Full]] = selectPageFull(group, params).run(xa)

  override def listFull(talk: Talk.Id, params: Page.Params): IO[Page[Proposal.Full]] = selectPageFull(talk, params).run(xa)

  override def listFull(group: Group.Id, speaker: User.Id, params: Page.Params): IO[Page[Proposal.Full]] = selectPageFull(group, speaker, params).run(xa)

  override def listFull(speaker: User.Id, params: Page.Params): IO[Page[Proposal.Full]] = selectPageFull(speaker, params).run(xa)

  override def listPublicFull(speaker: User.Id, params: Page.Params): IO[Page[Proposal.Full]] = selectPagePublicFull(speaker, params).run(xa)

  override def listPublicFull(group: Group.Id, params: Page.Params): IO[Page[Proposal.Full]] = selectPagePublicFull(group, params).run(xa)

  override def list(ids: Seq[Proposal.Id]): IO[Seq[Proposal]] = runNel(selectAll, ids)

  override def listPublicFull(ids: Seq[Proposal.Id]): IO[Seq[Proposal.Full]] = runNel(selectAllPublicFull, ids)

  override def listTags(): IO[Seq[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)
}

object ProposalRepoSql {
  private val _ = proposalIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.proposals
  private val tableFull = table
    .join(Tables.cfps, _.field("cfp_id"), _.field("id")).get
    .join(Tables.groups, _.field("group_id", "c"), _.field("id")).get
    .join(Tables.talks, _.field("talk_id", "p"), _.field("id")).get
    .joinOpt(Tables.events, _.field("event_id", "p"), _.field("id")).get

  private[sql] def insert(e: Proposal): Insert[Proposal] = {
    val values = fr0"${e.id}, ${e.talk}, ${e.cfp}, ${e.event}, ${e.status}, ${e.title}, ${e.duration}, ${e.description}, ${e.speakers}, ${e.slides}, ${e.video}, ${e.tags}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"
    table.insert[Proposal](e, _ => values)
  }

  private[sql] def update(orga: User.Id, group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id)(data: Proposal.Data, now: Instant): Update = {
    val fields = fr0"p.title=${data.title}, p.duration=${data.duration}, p.description=${data.description}, p.slides=${data.slides}, p.video=${data.video}, p.tags=${data.tags}, p.updated=$now, p.updated_by=$orga"
    table.update(fields, where(orga, group, cfp, proposal))
  }

  private[sql] def update(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug)(data: Proposal.Data, now: Instant): Update = {
    val fields = fr0"p.title=${data.title}, p.duration=${data.duration}, p.description=${data.description}, p.slides=${data.slides}, p.video=${data.video}, p.tags=${data.tags}, p.updated=$now, p.updated_by=$speaker"
    table.update(fields, where(speaker, talk, cfp))
  }

  private[sql] def updateStatus(cfp: Cfp.Slug, id: Proposal.Id)(status: Proposal.Status, event: Option[Event.Id]): Update =
    table.update(fr0"p.status=$status, p.event_id=$event", where(cfp, id))

  private[sql] def updateSlides(cfp: Cfp.Slug, id: Proposal.Id)(slides: Slides, by: User.Id, now: Instant): Update =
    table.update(fr0"p.slides=$slides, p.updated=$now, p.updated_by=$by", where(cfp, id))

  private[sql] def updateSlides(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug)(slides: Slides, by: User.Id, now: Instant): Update =
    table.update(fr0"p.slides=$slides, p.updated=$now, p.updated_by=$by", where(speaker, talk, cfp))

  private[sql] def updateVideo(cfp: Cfp.Slug, id: Proposal.Id)(video: Video, by: User.Id, now: Instant): Update =
    table.update(fr0"p.video=$video, p.updated=$now, p.updated_by=$by", where(cfp, id))

  private[sql] def updateVideo(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug)(video: Video, by: User.Id, now: Instant): Update =
    table.update(fr0"p.video=$video, p.updated=$now, p.updated_by=$by", where(speaker, talk, cfp))

  private[sql] def updateSpeakers(id: Proposal.Id)(speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): Update =
    table.update(fr0"p.speakers=$speakers, p.updated=$now, p.updated_by=$by", fr0"WHERE p.id=$id")

  private[sql] def selectOne(id: Proposal.Id): Select[Proposal] =
    table.select[Proposal](where(id))

  private[sql] def selectOne(cfp: Cfp.Slug, id: Proposal.Id): Select[Proposal] =
    table.select[Proposal](where(cfp, id))

  private[sql] def selectOne(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug): Select[Proposal] =
    table.select[Proposal](where(speaker, talk, cfp))

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

  private def where(id: Proposal.Id): Fragment =
    fr0"WHERE p.id=$id"

  private def where(cfp: Cfp.Slug, id: Proposal.Id): Fragment =
    fr0"WHERE p.id=(SELECT p.id FROM " ++
      Fragment.const0(s"${table.value} INNER JOIN ${Tables.cfps.value} ON p.cfp_id=c.id") ++
      fr0" WHERE p.id=$id AND c.slug=$cfp" ++ fr0")"

  private def where(orga: User.Id, group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Fragment =
    fr0"WHERE p.id=(SELECT p.id FROM " ++
      Fragment.const0(s"${table.value} INNER JOIN ${Tables.cfps.value} ON p.cfp_id=c.id INNER JOIN ${Tables.groups.value} ON c.group_id=g.id") ++
      fr0" WHERE p.id=$proposal AND c.slug=$cfp AND g.slug=$group AND g.owners LIKE ${"%" + orga.value + "%"}" ++ fr0")"

  private def where(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug): Fragment =
    fr0"WHERE p.id=(SELECT p.id FROM " ++
      Fragment.const0(s"${table.value} INNER JOIN ${Tables.cfps.value} ON p.cfp_id=c.id INNER JOIN ${Tables.talks.value} ON p.talk_id=t.id") ++
      fr0" WHERE c.slug=$cfp AND t.slug=$talk AND p.speakers LIKE ${"%" + speaker.value + "%"}" ++ fr0")"
}
