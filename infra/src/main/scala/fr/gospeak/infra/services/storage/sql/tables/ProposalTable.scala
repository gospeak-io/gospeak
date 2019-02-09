package fr.gospeak.infra.services.storage.sql.tables

import cats.data.NonEmptyList
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.{Cfp, Proposal, Talk}
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.libs.scalautils.domain.Page

object ProposalTable {
  private val _ = proposalIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = "proposals"
  private val fields = Seq("id", "talk_id", "cfp_id", "event_id", "title", "status", "description", "speakers", "created", "created_by", "updated", "updated_by")
  private[tables] val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields = Seq("id", "title", "status", "description")
  private val defaultSort = Page.OrderBy("-created")

  private def values(e: Proposal): Fragment =
    fr0"${e.id}, ${e.talk}, ${e.cfp}, ${e.event}, ${e.title}, ${e.status}, ${e.description}, ${e.speakers}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  def insert(elt: Proposal): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  def selectOne(id: Proposal.Id): doobie.Query0[Proposal] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE id=$id").query[Proposal]

  def selectOne(talk: Talk.Id, cfp: Cfp.Id): doobie.Query0[Proposal] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE talk_id=$talk AND cfp_id=$cfp").query[Proposal]

  def selectPage(talk: Talk.Id, params: Page.Params): (doobie.Query0[(Cfp, Proposal)], doobie.Query0[Long]) = {
    val selectedTables = Fragment.const0(s"${CfpTable.table} c INNER JOIN $table p ON p.cfp_id=c.id")
    val selectedFields = Fragment.const0((CfpTable.fields.map("c." + _) ++ fields.map("p." + _)).mkString(", "))
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE p.talk_id=$talk"), Some("p"))
    (buildSelect(selectedTables, selectedFields, page.all).query[(Cfp, Proposal)], buildSelect(selectedTables, fr0"count(*)", page.where).query[Long])
  }

  def selectPage(cfp: Cfp.Id, params: Page.Params): (doobie.Query0[Proposal], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE cfp_id=$cfp"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Proposal], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  def selectPage(cfp: Cfp.Id, status: Proposal.Status, params: Page.Params): (doobie.Query0[Proposal], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE cfp_id=$cfp AND status=$status"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Proposal], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  def selectAll(ids: NonEmptyList[Proposal.Id]): doobie.Query0[Proposal] =
    buildSelect(tableFr, fieldsFr, fr"WHERE" ++ Fragments.in(fr"id", ids)).query[Proposal]
}
