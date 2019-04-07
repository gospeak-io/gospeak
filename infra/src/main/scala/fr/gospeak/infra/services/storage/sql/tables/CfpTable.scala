package fr.gospeak.infra.services.storage.sql.tables

import java.time.Instant

import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.{Cfp, Group, Talk, User}
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.libs.scalautils.domain.Page

object CfpTable {
  private val _ = groupIdMeta // for intellij not remove DoobieUtils.Mappings import
  private[tables] val table = "cfps"
  private[tables] val fields = Seq("id", "group_id", "slug", "name", "start", "end", "description", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields = Seq("id", "slug", "name", "description")
  private val defaultSort = Page.OrderBy("name")

  private def values(e: Cfp): Fragment =
    fr0"${e.id}, ${e.group}, ${e.slug}, ${e.name}, ${e.start}, ${e.end}, ${e.description}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  def insert(elt: Cfp): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  def update(group: Group.Id, slug: Cfp.Slug)(data: Cfp.Data, by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"slug=${data.slug}, name=${data.name}, start=${data.start}, end=${data.end}, description=${data.description}, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(group, slug)).update
  }

  def selectOne(group: Group.Id, slug: Cfp.Slug): doobie.Query0[Cfp] =
    buildSelect(tableFr, fieldsFr, where(group, slug)).query[Cfp]

  def selectOne(slug: Cfp.Slug): doobie.Query0[Cfp] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE slug=$slug").query[Cfp]

  def selectOne(id: Cfp.Id): doobie.Query0[Cfp] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE id=$id").query[Cfp]

  def selectOne(id: Group.Id): doobie.Query0[Cfp] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE group_id=$id").query[Cfp]

  def selectPage(group: Group.Id, params: Page.Params): (doobie.Query0[Cfp], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE group_id=$group"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Cfp], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  def selectPage(talk: Talk.Id, params: Page.Params): (doobie.Query0[Cfp], doobie.Query0[Long]) = {
    val talkCfps = buildSelect(ProposalTable.tableFr, fr0"cfp_id", fr0"WHERE talk_id = $talk")
    val where = fr0"WHERE id NOT IN (" ++ talkCfps ++ fr0")"
    val page = paginate(params, searchFields, defaultSort, Some(where))
    (buildSelect(tableFr, fieldsFr, page.all).query[Cfp], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private def where(group: Group.Id, slug: Cfp.Slug): Fragment =
    fr0"WHERE group_id=$group AND slug=$slug"
}
