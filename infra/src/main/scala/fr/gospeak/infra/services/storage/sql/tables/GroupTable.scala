package fr.gospeak.infra.services.storage.sql.tables

import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.libs.scalautils.domain.Page

object GroupTable {
  private val _ = groupIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table: String = "groups"
  private val fields: Seq[String] = Seq("id", "slug", "name", "description", "owners", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields: Seq[String] = Seq("id", "slug", "name", "description")
  private val defaultSort: Page.OrderBy = Page.OrderBy("name")

  private def values(e: Group): Fragment =
    fr0"${e.id}, ${e.slug}, ${e.name}, ${e.description}, ${e.owners}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  def insert(elt: Group): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  def selectOne(user: User.Id, slug: Group.Slug): doobie.Query0[Group] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE owners LIKE ${"%" + user.value + "%"} AND slug=$slug").query[Group]

  def selectPage(user: User.Id, params: Page.Params): (doobie.Query0[Group], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE owners LIKE ${"%" + user.value + "%"}"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Group], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }
}
