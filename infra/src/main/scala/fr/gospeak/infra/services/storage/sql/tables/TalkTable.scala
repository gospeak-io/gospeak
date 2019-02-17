package fr.gospeak.infra.services.storage.sql.tables

import java.time.Instant

import cats.data.NonEmptyList
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.{Talk, User}
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.libs.scalautils.domain.Page

object TalkTable {
  private val _ = talkIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = "talks"
  private val fields = Seq("id", "slug", "title", "duration", "status", "description", "speakers", "slides", "video", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields = Seq("id", "slug", "title", "description")
  private val defaultSort = Page.OrderBy("title")

  private def values(e: Talk): Fragment =
    fr0"${e.id}, ${e.slug}, ${e.title}, ${e.duration}, ${e.status}, ${e.description}, ${e.speakers}, ${e.slides}, ${e.video}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  def insert(elt: Talk): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  def selectOne(user: User.Id, slug: Talk.Slug): doobie.Query0[Talk] =
    buildSelect(tableFr, fieldsFr, where(user, slug)).query[Talk]

  def selectPage(user: User.Id, params: Page.Params): (doobie.Query0[Talk], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE speakers LIKE ${"%" + user.value + "%"}"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Talk], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  def selectAll(ids: NonEmptyList[Talk.Id]): doobie.Query0[Talk] =
    buildSelect(tableFr, fieldsFr, fr"WHERE" ++ Fragments.in(fr"id", ids)).query[Talk]

  def update(user: User.Id, slug: Talk.Slug)(data: Talk.Data, now: Instant): doobie.Update0 = {
    val fields = fr0"slug=${data.slug}, title=${data.title}, duration=${data.duration}, description=${data.description}, slides=${data.slides}, video=${data.video}, updated=$now, updated_by=$user"
    buildUpdate(tableFr, fields, where(user, slug)).update
  }

  def updateStatus(user: User.Id, slug: Talk.Slug)(status: Talk.Status): doobie.Update0 =
    buildUpdate(tableFr, fr0"status=$status", where(user, slug)).update

  private def where(user: User.Id, slug: Talk.Slug): Fragment =
    fr0"WHERE speakers LIKE ${"%" + user.value + "%"} AND slug=$slug"
}
