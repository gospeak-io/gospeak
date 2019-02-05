package fr.gospeak.infra.services.storage.sql.tables

import java.time.Instant

import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.{Event, Group, User}
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.libs.scalautils.domain.Page

object EventTable {
  private val _ = eventIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = "events"
  private val fields = Seq("group_id", "id", "slug", "name", "start", "description", "venue", "talks", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields = Seq("id", "slug", "name", "description")
  private val defaultSort = Page.OrderBy("-start")

  private def values(e: Event): Fragment =
    fr0"${e.group}, ${e.id}, ${e.slug}, ${e.name}, ${e.start}, ${e.description}, ${e.venue}, ${e.talks}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  def insert(elt: Event): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  def selectOne(group: Group.Id, event: Event.Slug): doobie.Query0[Event] =
    buildSelect(tableFr, fieldsFr, where(group, event)).query[Event]

  def selectPage(group: Group.Id, params: Page.Params): (doobie.Query0[Event], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE group_id=$group"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Event], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  def selectAllAfter(group: Group.Id, now: Instant, params: Page.Params): (doobie.Query0[Event], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE group_id=$group AND start > $now"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Event], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  def update(group: Group.Id, event: Event.Slug)(data: Event.Data, by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"slug=${data.slug}, name=${data.name}, start=${data.start}, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(group, event)).update
  }

  private def where(group: Group.Id, event: Event.Slug): Fragment =
    fr0"WHERE group_id=$group AND slug=$event"
}
