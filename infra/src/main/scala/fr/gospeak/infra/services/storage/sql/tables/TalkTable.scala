package fr.gospeak.infra.services.storage.sql.tables

import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.utils.Page
import fr.gospeak.core.domain.{Talk, User}
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._

object TalkTable {
  private val _ = talkIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = "talks"
  private val fields = Seq("id", "slug", "title", "description", "speakers", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields = Seq("id", "slug", "title", "description")
  private val defaultSort = Page.OrderBy("title")

  private def values(e: Talk): Fragment =
    fr0"${e.id}, ${e.slug}, ${e.title}, ${e.description}, ${e.speakers}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  def insert(elt: Talk): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  def selectOne(user: User.Id, slug: Talk.Slug): doobie.Query0[Talk] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE speakers LIKE ${"%" + user.value + "%"} AND slug=$slug").query[Talk]

  def selectPage(user: User.Id, params: Page.Params): (doobie.Query0[Talk], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE speakers LIKE ${"%" + user.value + "%"}"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Talk], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }
}
