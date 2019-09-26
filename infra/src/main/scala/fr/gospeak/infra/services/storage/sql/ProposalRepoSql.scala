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
import fr.gospeak.infra.utils.DoobieUtils.Queries
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
    find(by, talk, cfp).flatMap {
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

  override def list(group: Group.Id, params: Page.Params): IO[Page[Proposal]] = run(Queries.selectPage(selectPage(group, _), params))

  override def list(cfp: Cfp.Id, params: Page.Params): IO[Page[Proposal]] = run(Queries.selectPage(selectPage(cfp, _), params))

  override def list(cfp: Cfp.Id, status: Proposal.Status, params: Page.Params): IO[Page[Proposal]] = run(Queries.selectPage(selectPage(cfp, status, _), params))

  override def listWithCfp(talk: Talk.Id, params: Page.Params): IO[Page[(Proposal, Cfp)]] = run(Queries.selectPage(selectPageWithCfp(talk, _), params))

  override def listWithCfp(group: Group.Id, speaker: User.Id, params: Page.Params): IO[Page[(Proposal, Cfp)]] = run(Queries.selectPage(selectPageWithCfp(group, speaker, _), params))

  override def listWithCfp(group: Group.Id, params: Page.Params): IO[Page[(Proposal, Cfp)]] = run(Queries.selectPage(selectPageWithCfp(group, _), params))

  override def listWithEvent(speaker: User.Id, status: Proposal.Status, params: Page.Params): IO[Page[(Option[Event], Proposal)]] = run(Queries.selectPage(selectPageWithEvent(speaker, status, _), params))

  override def listFull(speaker: User.Id, params: Page.Params): IO[Page[Proposal.Full]] = run(Queries.selectPage(selectPageFull(speaker, _), params))

  override def listPublicFull(group: Group.Id, params: Page.Params): IO[Page[Proposal.Full]] = run(Queries.selectPage(selectPublicPageFull(group, _), params))

  override def list(ids: Seq[Proposal.Id]): IO[Seq[Proposal]] = runIn(selectAll)(ids)

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

  private val tableWithCfpFr = Fragment.const0(s"$table p INNER JOIN $cfpTable c ON p.cfp_id=c.id")
  private val fieldsWithCfpFr = Fragment.const0((fields.map("p." + _) ++ cfpFields.map("c." + _)).mkString(", "))

  private val tableWithEventFr = Fragment.const0(s"$table p LEFT OUTER JOIN $eventTable e ON p.event_id=e.id")
  private val fieldsWithEventFr = Fragment.const0((eventFields.map("e." + _) ++ fields.map("p." + _)).mkString(", "))

  private val tableFullFr = Fragment.const0(s"$table p INNER JOIN $cfpTable c ON p.cfp_id=c.id INNER JOIN $talkTable t ON p.talk_id=t.id LEFT OUTER JOIN $eventTable e ON p.event_id=e.id")
  private val fieldsFullFr = Fragment.const0((fields.map("p." + _) ++ cfpFields.map("c." + _) ++ talkFields.map("t." + _) ++ eventFields.map("e." + _)).mkString(", "))

  private def values(e: Proposal): Fragment =
    fr0"${e.id}, ${e.talk}, ${e.cfp}, ${e.event}, ${e.status}, ${e.title}, ${e.duration}, ${e.description}, ${e.speakers}, ${e.slides}, ${e.video}, ${e.tags}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  private[sql] def insert(elt: Proposal): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

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

  private[sql] def selectPage(cfp: Cfp.Id, params: Page.Params): (doobie.Query0[Proposal], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE cfp_id=$cfp"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Proposal], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectPage(cfp: Cfp.Id, status: Proposal.Status, params: Page.Params): (doobie.Query0[Proposal], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE cfp_id=$cfp AND status=$status"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Proposal], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectPage(group: Group.Id, params: Page.Params): (doobie.Query0[Proposal], doobie.Query0[Long]) = {
    val selectedFields = Fragment.const0(fields.map("p." + _).mkString(", "))
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE c.group_id=$group"), Some("p"))
    (buildSelect(tableWithCfpFr, selectedFields, page.all).query[Proposal], buildSelect(tableWithCfpFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectPageWithCfp(group: Group.Id, params: Page.Params): (doobie.Query0[(Proposal, Cfp)], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE c.group_id=$group"), Some("p"))
    (buildSelect(tableWithCfpFr, fieldsWithCfpFr, page.all).query[(Proposal, Cfp)], buildSelect(tableWithCfpFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectPageWithCfp(group: Group.Id, speaker: User.Id, params: Page.Params): (doobie.Query0[(Proposal, Cfp)], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE c.group_id=$group AND p.speakers LIKE ${"%" + speaker.value + "%"}"), Some("p"))
    (buildSelect(tableWithCfpFr, fieldsWithCfpFr, page.all).query[(Proposal, Cfp)], buildSelect(tableWithCfpFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectPageWithCfp(talk: Talk.Id, params: Page.Params): (doobie.Query0[(Proposal, Cfp)], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE p.talk_id=$talk"), Some("p"))
    (buildSelect(tableWithCfpFr, fieldsWithCfpFr, page.all).query[(Proposal, Cfp)], buildSelect(tableWithCfpFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectPageWithEvent(speaker: User.Id, status: Proposal.Status, params: Page.Params): (doobie.Query0[(Option[Event], Proposal)], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE p.status=$status AND p.speakers LIKE ${"%" + speaker.value + "%"}"), Some("p"))
    (buildSelect(tableWithEventFr, fieldsWithEventFr, page.all).query[(Option[Event], Proposal)], buildSelect(tableWithEventFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectPageFull(speaker: User.Id, params: Page.Params): (doobie.Query0[Proposal.Full], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE p.speakers LIKE ${"%" + speaker.value + "%"}"), Some("p"))
    (buildSelect(tableFullFr, fieldsFullFr, page.all).query[Proposal.Full], buildSelect(tableFullFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectPublicPageFull(group: Group.Id, params: Page.Params): (doobie.Query0[Proposal.Full], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE e.group_id=$group AND e.published IS NOT NULL"), Some("p"))
    (buildSelect(tableFullFr, fieldsFullFr, page.all).query[Proposal.Full], buildSelect(tableFullFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectAll(ids: NonEmptyList[Proposal.Id]): doobie.Query0[Proposal] =
    buildSelect(tableFr, fieldsFr, fr"WHERE" ++ Fragments.in(fr"id", ids)).query[Proposal]

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
      Fragment.const0(s"$table p INNER JOIN $cfpTable c ON p.cfp_id=c.id INNER JOIN ${GroupRepoSql.table} g ON c.group_id=g.id ") ++
      fr0"WHERE p.id=$proposal AND c.slug=$cfp AND g.slug=$group AND g.owners LIKE ${"%" + orga.value + "%"}" ++ fr0")"

  private def where(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug): Fragment =
    fr0"WHERE id=(SELECT p.id FROM " ++
      Fragment.const0(s"$table p INNER JOIN $cfpTable c ON p.cfp_id=c.id INNER JOIN $talkTable t ON p.talk_id=t.id ") ++
      fr0"WHERE c.slug=$cfp AND t.slug=$talk AND p.speakers LIKE ${"%" + speaker.value + "%"}" ++ fr0")"
}
