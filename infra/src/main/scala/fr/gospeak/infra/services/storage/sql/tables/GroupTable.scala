package fr.gospeak.infra.services.storage.sql.tables

import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.utils.Page
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._

object GroupTable {
  private val _ = groupIdMeta // for intellij not remove DoobieUtils.Mappings import
  private[tables] val table = "groups"
  private[tables] val fields = Seq("id", "slug", "name", "description", "owners", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields = Seq("id", "slug", "name", "description")
  private val defaultSort = Page.OrderBy("name")

  private def values(e: Group): Fragment =
    fr0"${e.id}, ${e.slug}, ${e.name}, ${e.description}, ${e.owners}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  def insert(elt: Group): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  // slug should be unique on the platform
  def slugToId(slug: Group.Slug): doobie.Query0[Group.Id] =
    buildSelect(tableFr, fr0"id", fr0"WHERE slug=$slug").query[Group.Id]

  def selectOwned(id: Group.Id, user: User.Id): doobie.Query0[Group] =
    selectOne(Some(fr0"WHERE owners LIKE ${"%" + user.value + "%"}"), id)

  def selectOwned(user: User.Id, params: Page.Params): (doobie.Query0[Group], doobie.Query0[Long]) =
    selectPage(Some(fr0"WHERE owners LIKE ${"%" + user.value + "%"}"), params)

  def selectWithCfp(id: Group.Id): doobie.Query0[Group] =
    selectOne(None, id)

  def selectWithCfp(params: Page.Params): (doobie.Query0[Group], doobie.Query0[Long]) =
    selectPage(None, params) // TODO filter by has opened cfp

  private def selectOne(where: Option[Fragment], id: Group.Id): doobie.Query0[Group] =
    buildSelect(tableFr, fieldsFr, where.map(_ ++ fr0" AND id=$id").getOrElse(fr0"WHERE id=$id")).query[Group]

  private def selectPage(where: Option[Fragment], params: Page.Params): (doobie.Query0[Group], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, where)
    (buildSelect(tableFr, fieldsFr, page.all).query[Group], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }
}
