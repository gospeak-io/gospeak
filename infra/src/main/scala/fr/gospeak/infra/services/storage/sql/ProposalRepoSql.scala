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
import fr.gospeak.infra.services.storage.sql.CfpRepoSql.{fields => cfpFields, table => cfpTable}
import fr.gospeak.infra.services.storage.sql.EventRepoSql.{fields => eventFields, table => eventTable}
import fr.gospeak.infra.services.storage.sql.ProposalRepoSql._
import fr.gospeak.infra.services.storage.sql.TalkRepoSql.{fields => talkFields, table => talkTable}
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.SelectPage
import fr.gospeak.libs.scalautils.domain._

class ProposalRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ProposalRepo {
  override def create(talk: Talk.Id, cfp: Cfp.Id, data: Proposal.Data, speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): IO[Proposal] =
    run(insert, Proposal(talk, cfp, None, data, Proposal.Status.Pending, speakers, Info(by, now)))

  override def edit(orga: User.Id, group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id)(data: Proposal.Data, now: Instant): IO[Done] =
    run(update(orga, group, cfp, proposal)(data, now))

  override def edit(talk: Talk.Slug, cfp: Cfp.Slug)(data: Proposal.Data, by: User.Id, now: Instant): IO[Done] =
    run(update(by, talk, cfp)(data, now))

  override def editSlides(cfp: Cfp.Slug, id: Proposal.Id)(slides: Slides, by: User.Id, now: Instant): IO[Done] =
    run(updateSlides(cfp, id)(slides, by, now))

  override def editSlides(talk: Talk.Slug, cfp: Cfp.Slug)(slides: Slides, by: User.Id, now: Instant): IO[Done] =
    run(updateSlides(by, talk, cfp)(slides, by, now))

  override def editVideo(cfp: Cfp.Slug, id: Proposal.Id)(video: Video, by: User.Id, now: Instant): IO[Done] =
    run(updateVideo(cfp, id)(video, by, now))

  override def editVideo(talk: Talk.Slug, cfp: Cfp.Slug)(video: Video, by: User.Id, now: Instant): IO[Done] =
    run(updateVideo(by, talk, cfp)(video, by, now))

  override def addSpeaker(proposal: Proposal.Id)(speaker: User.Id, by: User.Id, now: Instant): IO[Done] =
    find(proposal).flatMap {
      case Some(proposalElt) =>
        if (proposalElt.speakers.toList.contains(speaker)) {
          IO.raiseError(new IllegalArgumentException("speaker already added"))
        } else {
          run(updateSpeakers(proposalElt.id)(proposalElt.speakers.append(speaker), by, now))
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
            run(updateSpeakers(proposalElt.id)(speakers, by, now))
          }.getOrElse {
            IO.raiseError(new IllegalArgumentException("last speaker can't be removed"))
          }
        } else {
          IO.raiseError(new IllegalArgumentException("user is not a speaker"))
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable proposal"))
    }

  override def accept(cfp: Cfp.Slug, id: Proposal.Id, event: Event.Id, by: User.Id, now: Instant): IO[Done] =
    run(updateStatus(cfp, id)(Proposal.Status.Accepted, Some(event))) // FIXME track user & date

  override def cancel(cfp: Cfp.Slug, id: Proposal.Id, event: Event.Id, by: User.Id, now: Instant): IO[Done] =
    run(updateStatus(cfp, id)(Proposal.Status.Pending, None)) // FIXME track user & date + check event id was set

  override def reject(cfp: Cfp.Slug, id: Proposal.Id, by: User.Id, now: Instant): IO[Done] =
    run(updateStatus(cfp, id)(Proposal.Status.Declined, None)) // FIXME track user & date

  override def cancelReject(cfp: Cfp.Slug, id: Proposal.Id, by: User.Id, now: Instant): IO[Done] =
    run(updateStatus(cfp, id)(Proposal.Status.Pending, None)) // FIXME track user & date

  override def find(proposal: Proposal.Id): IO[Option[Proposal]] = run(selectOne(proposal).option)

  override def find(cfp: Cfp.Slug, id: Proposal.Id): IO[Option[Proposal]] = run(selectOne(cfp, id).option)

  override def find(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug): IO[Option[Proposal]] = run(selectOne(speaker, talk, cfp).option)

  override def findFull(proposal: Proposal.Id): IO[Option[Proposal.Full]] = run(selectOneFull(proposal).option)

  override def findFull(talk: Talk.Slug, cfp: Cfp.Slug)(by: User.Id): IO[Option[Proposal.Full]] = run(selectOneFull(talk, cfp, by).option)

  override def findPublicFull(group: Group.Id, proposal: Proposal.Id): IO[Option[Proposal.Full]] = run(selectOnePublicFull(group, proposal).option)

  override def list(cfp: Cfp.Id, status: Proposal.Status, params: Page.Params): IO[Page[Proposal]] = run(selectPage(cfp, status, params).page)

  override def listFull(group: Group.Id, params: Page.Params): IO[Page[Proposal.Full]] = run(selectPageFull(group, params).page)

  override def listFull(cfp: Cfp.Id, params: Page.Params): IO[Page[Proposal.Full]] = run(selectPageFull(cfp, params).page)

  override def listFull(talk: Talk.Id, params: Page.Params): IO[Page[Proposal.Full]] = run(selectPageFull(talk, params).page)

  override def listFull(group: Group.Id, speaker: User.Id, params: Page.Params): IO[Page[Proposal.Full]] = run(selectPageFull(group, speaker, params).page)

  override def listFull(speaker: User.Id, params: Page.Params): IO[Page[Proposal.Full]] = run(selectPageFull(speaker, params).page)

  override def listPublicFull(speaker: User.Id, params: Page.Params): IO[Page[Proposal.Full]] = run(selectPagePublicFull(speaker, params).page)

  override def listPublicFull(group: Group.Id, params: Page.Params): IO[Page[Proposal.Full]] = run(selectPagePublicFull(group, params).page)

  override def list(ids: Seq[Proposal.Id]): IO[Seq[Proposal]] = runIn(selectAll)(ids)

  override def listPublicFull(ids: Seq[Proposal.Id]): IO[Seq[Proposal.Full]] = runIn(selectAllPublicFull)(ids)

  override def listTags(): IO[Seq[Tag]] = run(selectTags().to[List]).map(_.flatten.distinct)
}

object ProposalRepoSql {
  private val _ = proposalIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = "proposals"
  private val fields = Seq("id", "talk_id", "cfp_id", "event_id", "status", "title", "duration", "description", "speakers", "slides", "video", "tags", "created", "created_by", "updated", "updated_by")
  private[sql] val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields = Seq("id", "title", "status", "description", "tags")
  private val defaultSort = Page.OrderBy("-created")

  private val tableFullFr = Fragment.const0(
    s"$table p " +
      s"INNER JOIN $cfpTable c ON p.cfp_id=c.id " +
      s"INNER JOIN ${Tables.groups.name} ON c.group_id=g.id " +
      s"INNER JOIN $talkTable t ON p.talk_id=t.id " +
      s"LEFT OUTER JOIN $eventTable e ON p.event_id=e.id")
  private val fieldsFullFr = Fragment.const0((
    fields.map("p." + _) ++
      cfpFields.map("c." + _) ++
      Tables.groups.fields.map(_.value) ++
      talkFields.map("t." + _) ++
      eventFields.map("e." + _)).mkString(", "))

  private[sql] def insert(e: Proposal): doobie.Update0 = {
    val values = fr0"${e.id}, ${e.talk}, ${e.cfp}, ${e.event}, ${e.status}, ${e.title}, ${e.duration}, ${e.description}, ${e.speakers}, ${e.slides}, ${e.video}, ${e.tags}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"
    buildInsert(tableFr, fieldsFr, values).update
  }

  private[sql] def update(orga: User.Id, group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id)(data: Proposal.Data, now: Instant): doobie.Update0 = {
    val fields = fr0"title=${data.title}, duration=${data.duration}, description=${data.description}, slides=${data.slides}, video=${data.video}, tags=${data.tags}, updated=$now, updated_by=$orga"
    buildUpdate(tableFr, fields, where(orga, group, cfp, proposal)).update
  }

  private[sql] def update(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug)(data: Proposal.Data, now: Instant): doobie.Update0 = {
    val fields = fr0"title=${data.title}, duration=${data.duration}, description=${data.description}, slides=${data.slides}, video=${data.video}, tags=${data.tags}, updated=$now, updated_by=$speaker"
    buildUpdate(tableFr, fields, where(speaker, talk, cfp)).update
  }

  private[sql] def updateStatus(cfp: Cfp.Slug, id: Proposal.Id)(status: Proposal.Status, event: Option[Event.Id]): doobie.Update0 = {
    val fields = fr0"status=$status, event_id=$event"
    buildUpdate(tableFr, fields, where(cfp, id)).update
  }

  private[sql] def updateSlides(cfp: Cfp.Slug, id: Proposal.Id)(slides: Slides, by: User.Id, now: Instant): doobie.Update0 =
    buildUpdate(tableFr, fr0"slides=$slides, updated=$now, updated_by=$by", where(cfp, id)).update

  private[sql] def updateSlides(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug)(slides: Slides, by: User.Id, now: Instant): doobie.Update0 =
    buildUpdate(tableFr, fr0"slides=$slides, updated=$now, updated_by=$by", where(speaker, talk, cfp)).update

  private[sql] def updateVideo(cfp: Cfp.Slug, id: Proposal.Id)(video: Video, by: User.Id, now: Instant): doobie.Update0 =
    buildUpdate(tableFr, fr0"video=$video, updated=$now, updated_by=$by", where(cfp, id)).update

  private[sql] def updateVideo(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug)(video: Video, by: User.Id, now: Instant): doobie.Update0 =
    buildUpdate(tableFr, fr0"video=$video, updated=$now, updated_by=$by", where(speaker, talk, cfp)).update

  private[sql] def updateSpeakers(id: Proposal.Id)(speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): doobie.Update0 =
    buildUpdate(tableFr, fr0"speakers=$speakers, updated=$now, updated_by=$by", fr0"WHERE id=$id").update

  private[sql] def selectOne(id: Proposal.Id): doobie.Query0[Proposal] =
    buildSelect(tableFr, fieldsFr, where(id)).query[Proposal]

  private[sql] def selectOne(cfp: Cfp.Slug, id: Proposal.Id): doobie.Query0[Proposal] =
    buildSelect(tableFr, fieldsFr, where(cfp, id)).query[Proposal]

  private[sql] def selectOne(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug): doobie.Query0[Proposal] =
    buildSelect(tableFr, fieldsFr, where(speaker, talk, cfp)).query[Proposal]

  private[sql] def selectOneFull(id: Proposal.Id): doobie.Query0[Proposal.Full] =
    buildSelect(tableFullFr, fieldsFullFr, fr0"WHERE p.id=$id").query[Proposal.Full]

  private[sql] def selectOneFull(talk: Talk.Slug, cfp: Cfp.Slug, by: User.Id): doobie.Query0[Proposal.Full] =
    buildSelect(tableFullFr, fieldsFullFr, fr0"WHERE t.slug=$talk AND c.slug=$cfp AND p.speakers LIKE ${"%" + by.value + "%"}").query[Proposal.Full]

  private[sql] def selectOnePublicFull(group: Group.Id, id: Proposal.Id): doobie.Query0[Proposal.Full] =
    buildSelect(tableFullFr, fieldsFullFr, fr0"WHERE c.group_id=$group AND p.id=$id AND e.published IS NOT NULL").query[Proposal.Full]

  private[sql] def selectPage(cfp: Cfp.Id, status: Proposal.Status, params: Page.Params): SelectPage[Proposal] =
    SelectPage[Proposal](table, fieldsFr, fr0"WHERE cfp_id=$cfp AND status=$status", params, defaultSort, searchFields)

  private[sql] def selectPageFull(cfp: Cfp.Id, params: Page.Params): SelectPage[Proposal.Full] =
    SelectPage[Proposal.Full](tableFullFr, fieldsFullFr, fr0"WHERE p.cfp_id=$cfp", params, defaultSort, searchFields, "p")

  private[sql] def selectPageFull(group: Group.Id, params: Page.Params): SelectPage[Proposal.Full] =
    SelectPage[Proposal.Full](tableFullFr, fieldsFullFr, fr0"WHERE c.group_id=$group", params, defaultSort, searchFields, "p")

  private[sql] def selectPageFull(group: Group.Id, speaker: User.Id, params: Page.Params): SelectPage[Proposal.Full] =
    SelectPage[Proposal.Full](tableFullFr, fieldsFullFr, fr0"WHERE c.group_id=$group AND p.speakers LIKE ${"%" + speaker.value + "%"}", params, defaultSort, searchFields, "p")

  private[sql] def selectPageFull(talk: Talk.Id, params: Page.Params): SelectPage[Proposal.Full] =
    SelectPage[Proposal.Full](tableFullFr, fieldsFullFr, fr0"WHERE p.talk_id=$talk", params, defaultSort, searchFields, "p")

  private[sql] def selectPageFull(speaker: User.Id, params: Page.Params): SelectPage[Proposal.Full] =
    SelectPage[Proposal.Full](tableFullFr, fieldsFullFr, fr0"WHERE p.speakers LIKE ${"%" + speaker.value + "%"}", params, defaultSort, searchFields, "p")

  private[sql] def selectPagePublicFull(speaker: User.Id, params: Page.Params): SelectPage[Proposal.Full] =
    SelectPage[Proposal.Full](tableFullFr, fieldsFullFr, fr0"WHERE p.speakers LIKE ${"%" + speaker.value + "%"} AND e.published IS NOT NULL", params, defaultSort, searchFields, "p")

  private[sql] def selectPagePublicFull(group: Group.Id, params: Page.Params): SelectPage[Proposal.Full] =
    SelectPage[Proposal.Full](tableFullFr, fieldsFullFr, fr0"WHERE e.group_id=$group AND e.published IS NOT NULL", params, defaultSort, searchFields, "p")

  private[sql] def selectAll(ids: NonEmptyList[Proposal.Id]): doobie.Query0[Proposal] =
    buildSelect(tableFr, fieldsFr, fr"WHERE" ++ Fragments.in(fr"id", ids)).query[Proposal]

  private[sql] def selectAllPublicFull(ids: NonEmptyList[Proposal.Id]): doobie.Query0[Proposal.Full] =
    buildSelect(tableFullFr, fieldsFullFr, fr"WHERE" ++ Fragments.in(fr"p.id", ids) ++ fr0"AND e.published IS NOT NULL").query[Proposal.Full]

  private[sql] def selectTags(): doobie.Query0[Seq[Tag]] =
    Fragment.const0(s"SELECT tags FROM $table").query[Seq[Tag]]

  private def where(id: Proposal.Id): Fragment =
    fr0"WHERE id=$id"

  private def where(cfp: Cfp.Slug, id: Proposal.Id): Fragment =
    fr0"WHERE id=(SELECT p.id FROM " ++
      Fragment.const0(s"$table p INNER JOIN $cfpTable c ON p.cfp_id=c.id ") ++
      fr0"WHERE p.id=$id AND c.slug=$cfp" ++ fr0")"

  private def where(orga: User.Id, group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Fragment =
    fr0"WHERE id=(SELECT p.id FROM " ++
      Fragment.const0(s"$table p INNER JOIN $cfpTable c ON p.cfp_id=c.id INNER JOIN ${Tables.groups.name} ON c.group_id=g.id ") ++
      fr0"WHERE p.id=$proposal AND c.slug=$cfp AND g.slug=$group AND g.owners LIKE ${"%" + orga.value + "%"}" ++ fr0")"

  private def where(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug): Fragment =
    fr0"WHERE id=(SELECT p.id FROM " ++
      Fragment.const0(s"$table p INNER JOIN $cfpTable c ON p.cfp_id=c.id INNER JOIN $talkTable t ON p.talk_id=t.id ") ++
      fr0"WHERE c.slug=$cfp AND t.slug=$talk AND p.speakers LIKE ${"%" + speaker.value + "%"}" ++ fr0")"
}
