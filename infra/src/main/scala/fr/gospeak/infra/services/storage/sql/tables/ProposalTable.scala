package fr.gospeak.infra.services.storage.sql.tables

import cats.data.NonEmptyList
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.update.Update
import fr.gospeak.core.domain.utils.Page
import fr.gospeak.core.domain.{Group, Proposal, Talk}
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._

object ProposalTable {
  private val _ = proposalIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = "proposals"
  private val fields = Seq("id", "talk_id", "group_id", "title", "description", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields = Seq("id", "title", "description")
  private val defaultSort = Page.OrderBy("title")

  private def values(e: Proposal): Fragment =
    fr0"${e.id}, ${e.talk}, ${e.group}, ${e.title}, ${e.description}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  private[tables] def selectOneQuery(id: Proposal.Id): doobie.Query0[Proposal] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE id=$id").query[Proposal]

  private[tables] def selectPageQuery(group: Group.Id, params: Page.Params): doobie.Query0[Proposal] =
    buildSelect(tableFr, fieldsFr, paginate(params, searchFields, defaultSort, Some(fr0"WHERE group_id=$group"))).query[Proposal]

  private[tables] def countPageQuery(group: Group.Id, params: Page.Params): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)", fr0"WHERE group_id=$group").query[Long]

  private[tables] def selectPageQuery(talk: Talk.Id, params: Page.Params): doobie.Query0[(Group, Proposal)] = {
    val selectedFields = Fragment.const0((GroupTable.fields.map("g." + _) ++ fields.map("p." + _)).mkString(", "))
    val selectedTables = Fragment.const0(s"${GroupTable.table} g INNER JOIN $table p ON p.group_id=g.id")
    buildSelect(selectedTables, selectedFields, paginate(params, searchFields, defaultSort, Some(fr0"WHERE p.talk_id=$talk"), Some("p"))).query[(Group, Proposal)]
  }

  private[tables] def countPageQuery(talk: Talk.Id, params: Page.Params): doobie.Query0[Long] = {
    val selectedTables = Fragment.const0(s"${GroupTable.table} g INNER JOIN $table p ON p.group_id=g.id")
    buildSelect(selectedTables, fr0"count(*)", fr0"WHERE p.talk_id=$talk").query[Long]
  }

  def insert(elt: Proposal): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  def insertMany(elts: NonEmptyList[Proposal]): doobie.ConnectionIO[Int] = Update[Proposal](insert(elts.head).sql).updateMany(elts)

  def selectOne(id: Proposal.Id): doobie.ConnectionIO[Option[Proposal]] = selectOneQuery(id).option

  def selectPage(group: Group.Id, params: Page.Params): doobie.ConnectionIO[Page[Proposal]] = for {
    elts <- selectPageQuery(group, params).to[List]
    total <- countPageQuery(group, params).unique
  } yield Page(elts, params, Page.Total(total.toInt))

  def selectPage(talk: Talk.Id, params: Page.Params): doobie.ConnectionIO[Page[(Group, Proposal)]] = for {
    elts <- selectPageQuery(talk, params).to[List]
    total <- countPageQuery(talk, params).unique
  } yield Page(elts, params, Page.Total(total.toInt))
}
