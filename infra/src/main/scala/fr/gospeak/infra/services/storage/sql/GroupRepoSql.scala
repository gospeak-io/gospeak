package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services.GroupRepo
import fr.gospeak.infra.services.storage.sql.GroupRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.libs.scalautils.domain.Page

class GroupRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with GroupRepo {
  override def create(data: Group.Data, by: User.Id, now: Instant): IO[Group] =
    run(insert, Group(Group.Id.generate(), data.slug, data.name, data.description, NonEmptyList.of(by), Info(by, now)))

  override def find(user: User.Id, slug: Group.Slug): IO[Option[Group]] = run(selectOne(user, slug).option)

  override def list(user: User.Id, params: Page.Params): IO[Page[Group]] = run(Queries.selectPage(selectPage(user, _), params))
}

object GroupRepoSql {
  private val _ = groupIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table: String = "groups"
  private val fields: Seq[String] = Seq("id", "slug", "name", "description", "owners", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields: Seq[String] = Seq("id", "slug", "name", "description")
  private val defaultSort: Page.OrderBy = Page.OrderBy("name")

  private def values(e: Group): Fragment =
    fr0"${e.id}, ${e.slug}, ${e.name}, ${e.description}, ${e.owners}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  private[sql] def insert(elt: Group): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  private[sql] def selectOne(user: User.Id, slug: Group.Slug): doobie.Query0[Group] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE owners LIKE ${"%" + user.value + "%"} AND slug=$slug").query[Group]

  private[sql] def selectPage(user: User.Id, params: Page.Params): (doobie.Query0[Group], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE owners LIKE ${"%" + user.value + "%"}"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Group], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }
}