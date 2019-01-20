package fr.gospeak.infra.services.storage.sql.tables

import cats.data.NonEmptyList
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.update.Update
import fr.gospeak.core.domain.utils.Page
import fr.gospeak.core.domain.{Event, Group}
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._

object EventTable {
  private val _ = eventIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = "events"
  private val fields = Seq("group_id", "id", "slug", "name", "description", "venue", "talks", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields = Seq("id", "slug", "name", "description")
  private val defaultSort = Page.OrderBy("name")

  private def values(e: Event): Fragment =
    fr0"${e.group}, ${e.id}, ${e.slug}, ${e.name}, ${e.description}, ${e.venue}, ${e.talks}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  private[tables] def slugToIdQuery(group: Group.Id, slug: Event.Slug): doobie.Query0[Event.Id] =
    buildSelect(tableFr, fr0"id", fr0"WHERE slug=$slug AND group_id=$group").query[Event.Id]

  private[tables] def selectOneQuery(id: Event.Id): doobie.Query0[Event] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE id=$id").query[Event]

  private[tables] def selectPageQuery(group: Group.Id, params: Page.Params): doobie.Query0[Event] =
    buildSelect(tableFr, fieldsFr, paginate(params, searchFields, defaultSort, Some(fr0"WHERE group_id=$group"))).query[Event]

  private[tables] def countPageQuery(group: Group.Id, params: Page.Params): doobie.Query0[Long] =
    buildSelect(tableFr, fr0"count(*)", fr0"WHERE group_id=$group").query[Long]

  def insert(elt: Event): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  def insertMany(elts: NonEmptyList[Event]): doobie.ConnectionIO[Int] = Update[Event](insert(elts.head).sql).updateMany(elts)

  // slug should be unique for the group
  def slugToId(group: Group.Id, slug: Event.Slug): doobie.ConnectionIO[Option[Event.Id]] = slugToIdQuery(group, slug).option

  def selectOne(id: Event.Id): doobie.ConnectionIO[Option[Event]] = selectOneQuery(id).option

  def selectPage(group: Group.Id, params: Page.Params): doobie.ConnectionIO[Page[Event]] = for {
    elts <- selectPageQuery(group, params).to[List]
    total <- countPageQuery(group, params).unique
  } yield Page(elts, params, Page.Total(total.toInt))
}
