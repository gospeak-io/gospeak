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
import fr.gospeak.libs.scalautils.domain.{Done, Page, Slides, Video}

class ProposalRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ProposalRepo {
  override def create(talk: Talk.Id, cfp: Cfp.Id, data: Proposal.Data, speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): IO[Proposal] =
    run(insert, Proposal(talk, cfp, None, data, Proposal.Status.Pending, speakers, Info(by, now)))

  override def edit(user: User.Id, id: Proposal.Id)(data: Proposal.Data, now: Instant): IO[Done] =
    run(update(user, id)(data, now))

  override def editStatus(id: Proposal.Id)(status: Proposal.Status, event: Option[Event.Id]): IO[Done] =
    run(updateStatus(id)(status, event))

  override def editSlides(id: Proposal.Id)(slides: Slides, now: Instant, user: User.Id): IO[Done] = run(updateSlides(id)(slides, now, user))

  override def editVideo(id: Proposal.Id)(video: Video, now: Instant, user: User.Id): IO[Done] = run(updateVideo(id)(video, now, user))

  override def find(id: Proposal.Id): IO[Option[Proposal]] = run(selectOne(id).option)

  override def find(talk: Talk.Id, cfp: Cfp.Id): IO[Option[Proposal]] = run(selectOne(talk, cfp).option)

  override def list(talk: Talk.Id, params: Page.Params): IO[Page[(Cfp, Proposal)]] = run(Queries.selectPage(selectPage(talk, _), params))

  override def list(group: Group.Id, params: Page.Params): IO[Page[Proposal]] = run(Queries.selectPage(selectPage(group, _), params))

  override def list(group: Group.Id, speaker: User.Id, params: Page.Params): IO[Page[(Cfp, Proposal)]] = run(Queries.selectPage(selectPage(group, speaker, _), params))

  override def list(cfp: Cfp.Id, params: Page.Params): IO[Page[Proposal]] = run(Queries.selectPage(selectPage(cfp, _), params))

  override def list(cfp: Cfp.Id, status: Proposal.Status, params: Page.Params): IO[Page[Proposal]] = run(Queries.selectPage(selectPage(cfp, status, _), params))

  override def list(ids: Seq[Proposal.Id]): IO[Seq[Proposal]] = runIn(selectAll)(ids)
}

object ProposalRepoSql {
  private val _ = proposalIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = "proposals"
  private val fields = Seq("id", "talk_id", "cfp_id", "event_id", "title", "duration", "status", "description", "speakers", "slides", "video", "created", "created_by", "updated", "updated_by")
  private[sql] val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields = Seq("id", "title", "status", "description")
  private val defaultSort = Page.OrderBy("-created")

  private def values(e: Proposal): Fragment =
    fr0"${e.id}, ${e.talk}, ${e.cfp}, ${e.event}, ${e.title}, ${e.duration}, ${e.status}, ${e.description}, ${e.speakers}, ${e.slides}, ${e.video}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  private[sql] def insert(elt: Proposal): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  private[sql] def update(user: User.Id, id: Proposal.Id)(data: Proposal.Data, now: Instant): doobie.Update0 = {
    val fields = fr0"title=${data.title}, duration=${data.duration}, description=${data.description}, slides=${data.slides}, video=${data.video}, updated=$now, updated_by=$user"
    buildUpdate(tableFr, fields, where(id)).update
  }

  private[sql] def updateStatus(id: Proposal.Id)(status: Proposal.Status, event: Option[Event.Id]): doobie.Update0 = {
    val fields = fr0"status=$status, event_id=$event"
    buildUpdate(tableFr, fields, where(id)).update
  }

  private[sql] def updateSlides(id: Proposal.Id)(slides: Slides, now: Instant, user: User.Id): doobie.Update0 =
    buildUpdate(tableFr, fr0"slides=$slides, updated=$now, updated_by=$user", where(id)).update

  private[sql] def updateVideo(id: Proposal.Id)(video: Video, now: Instant, user: User.Id): doobie.Update0 =
    buildUpdate(tableFr, fr0"video=$video, updated=$now, updated_by=$user", where(id)).update

  private[sql] def selectOne(id: Proposal.Id): doobie.Query0[Proposal] =
    buildSelect(tableFr, fieldsFr, where(id)).query[Proposal]

  private[sql] def selectOne(talk: Talk.Id, cfp: Cfp.Id): doobie.Query0[Proposal] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE talk_id=$talk AND cfp_id=$cfp").query[Proposal]

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

  private def where(id: Proposal.Id): Fragment =
    fr0"WHERE id=$id"
}
