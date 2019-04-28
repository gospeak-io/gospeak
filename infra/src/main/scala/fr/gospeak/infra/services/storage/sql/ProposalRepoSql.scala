package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.services.ProposalRepo
import fr.gospeak.infra.services.storage.sql.ProposalRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.libs.scalautils.domain.{Done, Page, Slides, Tag, Video}

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

  override def accept(cfp: Cfp.Slug, id: Proposal.Id, event: Event.Id, by: User.Id, now: Instant): IO[Done] =
    run(updateStatus(cfp, id)(Proposal.Status.Accepted, Some(event))) // FIXME track user & date

  override def cancel(cfp: Cfp.Slug, id: Proposal.Id, event: Event.Id, by: User.Id, now: Instant): IO[Done] =
    run(updateStatus(cfp, id)(Proposal.Status.Pending, None)) // FIXME track user & date + check event id was set

  override def reject(cfp: Cfp.Slug, id: Proposal.Id, by: User.Id, now: Instant): IO[Done] =
    run(updateStatus(cfp, id)(Proposal.Status.Rejected, None)) // FIXME track user & date

  override def cancelReject(cfp: Cfp.Slug, id: Proposal.Id, by: User.Id, now: Instant): IO[Done] =
    run(updateStatus(cfp, id)(Proposal.Status.Pending, None)) // FIXME track user & date

  override def find(cfp: Cfp.Slug, id: Proposal.Id): IO[Option[Proposal]] = run(selectOne(cfp, id).option)

  override def find(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug): IO[Option[Proposal]] = run(selectOne(speaker, talk, cfp).option)

  override def list(talk: Talk.Id, params: Page.Params): IO[Page[(Cfp, Proposal)]] = run(Queries.selectPage(selectPage(talk, _), params))

  override def list(group: Group.Id, params: Page.Params): IO[Page[Proposal]] = run(Queries.selectPage(selectPage(group, _), params))

  override def list(group: Group.Id, speaker: User.Id, params: Page.Params): IO[Page[(Cfp, Proposal)]] = run(Queries.selectPage(selectPage(group, speaker, _), params))

  override def list(cfp: Cfp.Id, params: Page.Params): IO[Page[Proposal]] = run(Queries.selectPage(selectPage(cfp, _), params))

  override def list(cfp: Cfp.Id, status: Proposal.Status, params: Page.Params): IO[Page[Proposal]] = run(Queries.selectPage(selectPage(cfp, status, _), params))

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

  private[sql] def selectOne(id: Proposal.Id): doobie.Query0[Proposal] =
    buildSelect(tableFr, fieldsFr, where(id)).query[Proposal]

  private[sql] def selectOne(cfp: Cfp.Slug, id: Proposal.Id): doobie.Query0[Proposal] =
    buildSelect(tableFr, fieldsFr, where(cfp, id)).query[Proposal]

  private[sql] def selectOne(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug): doobie.Query0[Proposal] =
    buildSelect(tableFr, fieldsFr, where(speaker, talk, cfp)).query[Proposal]

  private[sql] def selectPage(talk: Talk.Id, params: Page.Params): (doobie.Query0[(Cfp, Proposal)], doobie.Query0[Long]) = {
    val selectedTables = Fragment.const0(s"$table p INNER JOIN ${CfpRepoSql.table} c ON p.cfp_id=c.id")
    val selectedFields = Fragment.const0((CfpRepoSql.fields.map("c." + _) ++ fields.map("p." + _)).mkString(", "))
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE p.talk_id=$talk"), Some("p"))
    (buildSelect(selectedTables, selectedFields, page.all).query[(Cfp, Proposal)], buildSelect(selectedTables, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectPage(group: Group.Id, params: Page.Params): (doobie.Query0[Proposal], doobie.Query0[Long]) = {
    val selectedTables = Fragment.const0(s"$table p INNER JOIN ${CfpRepoSql.table} c ON p.cfp_id=c.id")
    val selectedFields = Fragment.const0(fields.map("p." + _).mkString(", "))
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE c.group_id=$group"), Some("p"))
    (buildSelect(selectedTables, selectedFields, page.all).query[Proposal], buildSelect(selectedTables, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectPage(group: Group.Id, speaker: User.Id, params: Page.Params): (doobie.Query0[(Cfp, Proposal)], doobie.Query0[Long]) = {
    val selectedTables = Fragment.const0(s"$table p INNER JOIN ${CfpRepoSql.table} c ON p.cfp_id=c.id")
    val selectedFields = Fragment.const0((CfpRepoSql.fields.map("c." + _) ++ fields.map("p." + _)).mkString(", "))
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE c.group_id=$group AND p.speakers LIKE ${"%" + speaker.value + "%"}"), Some("p"))
    (buildSelect(selectedTables, selectedFields, page.all).query[(Cfp, Proposal)], buildSelect(selectedTables, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectPage(cfp: Cfp.Id, params: Page.Params): (doobie.Query0[Proposal], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE cfp_id=$cfp"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Proposal], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectPage(cfp: Cfp.Id, status: Proposal.Status, params: Page.Params): (doobie.Query0[Proposal], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE cfp_id=$cfp AND status=$status"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Proposal], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectAll(ids: NonEmptyList[Proposal.Id]): doobie.Query0[Proposal] =
    buildSelect(tableFr, fieldsFr, fr"WHERE" ++ Fragments.in(fr"id", ids)).query[Proposal]

  private[sql] def selectTags(): doobie.Query0[Seq[Tag]] =
    Fragment.const0(s"SELECT tags FROM $table").query[Seq[Tag]]

  private def where(id: Proposal.Id): Fragment =
    fr0"WHERE id=$id"

  private def where(cfp: Cfp.Slug, id: Proposal.Id): Fragment =
    fr0"WHERE id=(SELECT p.id FROM " ++
      Fragment.const0(s"$table p INNER JOIN ${CfpRepoSql.table} c ON p.cfp_id=c.id ") ++
      fr0"WHERE p.id=$id AND c.slug=$cfp" ++ fr0")"

  private def where(orga: User.Id, group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Fragment =
    fr0"WHERE id=(SELECT p.id FROM " ++
      Fragment.const0(s"$table p INNER JOIN ${CfpRepoSql.table} c ON p.cfp_id=c.id INNER JOIN ${GroupRepoSql.table} g ON c.group_id=g.id ") ++
      fr0"WHERE p.id=$proposal AND c.slug=$cfp AND g.slug=$group AND g.owners LIKE ${"%" + orga.value + "%"}" ++ fr0")"

  private def where(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug): Fragment =
    fr0"WHERE id=(SELECT p.id FROM " ++
      Fragment.const0(s"$table p INNER JOIN ${CfpRepoSql.table} c ON p.cfp_id=c.id INNER JOIN ${TalkRepoSql.table} t ON p.talk_id=t.id ") ++
      fr0"WHERE c.slug=$cfp AND t.slug=$talk AND t.speakers LIKE ${"%" + speaker.value + "%"}" ++ fr0")"
}
