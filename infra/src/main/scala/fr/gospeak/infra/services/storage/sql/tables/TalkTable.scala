package fr.gospeak.infra.services.storage.sql.tables

import cats.data.NonEmptyList
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.update.Update
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

  private[tables] def slugToIdQuery(user: User.Id, slug: Talk.Slug): doobie.Query0[Talk.Id] =
    buildSelect(tableFr, fr0"id", fr0"WHERE slug=$slug AND speakers LIKE ${"%" + user.value + "%"}").query[Talk.Id]

  private[tables] def selectOneQuery(id: Talk.Id, user: User.Id): doobie.Query0[Talk] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE id=$id AND speakers LIKE ${"%" + user.value + "%"}").query[Talk]

  private[tables] def selectPageQuery(user: User.Id, params: Page.Params): doobie.Query0[Talk] =
    buildSelect(tableFr, fieldsFr, paginate(params, searchFields, defaultSort, Some(fr0"WHERE speakers LIKE ${"%" + user.value + "%"}"))).query[Talk]

  private[tables] def countPageQuery(user: User.Id, params: Page.Params): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)", fr0"WHERE speakers LIKE ${"%" + user.value + "%"}").query[Long]

  def insert(elt: Talk): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  def insertMany(elts: NonEmptyList[Talk]): doobie.ConnectionIO[Int] = Update[Talk](insert(elts.head).sql).updateMany(elts)

  // slug should be unique for the user
  def slugToId(user: User.Id, slug: Talk.Slug): doobie.ConnectionIO[Option[Talk.Id]] = slugToIdQuery(user, slug).option

  def selectOne(id: Talk.Id, user: User.Id): doobie.ConnectionIO[Option[Talk]] = selectOneQuery(id, user).option

  def selectPage(user: User.Id, params: Page.Params): doobie.ConnectionIO[Page[Talk]] = for {
    elts <- selectPageQuery(user, params).to[List]
    total <- countPageQuery(user, params).unique
  } yield Page(elts, params, Page.Total(total.toInt))
}
