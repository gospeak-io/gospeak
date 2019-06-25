package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services.storage.GroupRepo
import fr.gospeak.infra.services.storage.sql.GroupRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.libs.scalautils.domain.{Done, Page, Tag}

class GroupRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with GroupRepo {
  override def create(data: Group.Data, by: User.Id, now: Instant): IO[Group] =
    run(insert, Group(data, NonEmptyList.of(by), Info(by, now)))

  override def addOwner(group: Group.Id)(owner: User.Id, by: User.Id, now: Instant): IO[Done] =
    run(GroupRepoSql.addOwner(group)(owner, by, now))

  override def list(user: User.Id, params: Page.Params): IO[Page[Group]] = run(Queries.selectPage(selectPage(user, _), params))

  override def list(user: User.Id): IO[Seq[Group]] = run(selectAll(user).to[List])

  override def listPublic(params: Page.Params): IO[Page[Group]] = run(Queries.selectPage(selectPagePublic, params))

  override def listJoinable(user: User.Id, params: Page.Params): IO[Page[Group]] = run(Queries.selectPage(selectPageJoinable(user, _), params))

  override def find(user: User.Id, slug: Group.Slug): IO[Option[Group]] = run(selectOne(user, slug).option)

  override def find(group: Group.Id): IO[Option[Group]] = run(selectOne(group).option)

  override def findPublic(user: User.Id, params: Page.Params): IO[Page[Group]] = run(Queries.selectPage(selectPagePublic(user, _), params))

  override def findPublic(slug: Group.Slug): IO[Option[Group]] = run(selectOnePublic(slug).option)

  override def listTags(): IO[Seq[Tag]] = run(selectTags().to[List]).map(_.flatten.distinct)
}

object GroupRepoSql {
  private val _ = groupIdMeta // for intellij not remove DoobieUtils.Mappings import
  private[sql] val table: String = "groups"
  private val fields: Seq[String] = Seq("id", "slug", "name", "description", "owners", "tags", "published", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields: Seq[String] = Seq("id", "slug", "name", "description", "tags")
  private val defaultSort: Page.OrderBy = Page.OrderBy("name")

  private def values(e: Group): Fragment =
    fr0"${e.id}, ${e.slug}, ${e.name}, ${e.description}, ${e.owners}, ${e.tags}, ${e.published}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  private[sql] def insert(elt: Group): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  private[sql] def addOwner(group: Group.Id)(owner: User.Id, by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"owners=CONCAT(owners, '," ++ Fragment.const0(owner.value) ++ fr0"'), updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, fr0"WHERE id=$group").update
  }

  private[sql] def selectPage(user: User.Id, params: Page.Params): (doobie.Query0[Group], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE owners LIKE ${"%" + user.value + "%"}"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Group], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectPagePublic(params: Page.Params): (doobie.Query0[Group], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE published IS NOT NULL"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Group], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectPagePublic(user: User.Id, params: Page.Params): (doobie.Query0[Group], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE published IS NOT NULL AND owners LIKE ${"%" + user.value + "%"}"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Group], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectPageJoinable(user: User.Id, params: Page.Params): (doobie.Query0[Group], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE owners NOT LIKE ${"%" + user.value + "%"}"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Group], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectAll(user: User.Id): doobie.Query0[Group] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE owners LIKE ${"%" + user.value + "%"}").query[Group]

  private[sql] def selectOne(user: User.Id, slug: Group.Slug): doobie.Query0[Group] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE owners LIKE ${"%" + user.value + "%"} AND slug=$slug").query[Group]

  private[sql] def selectOne(group: Group.Id): doobie.Query0[Group] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE id=$group").query[Group]

  private[sql] def selectOnePublic(slug: Group.Slug): doobie.Query0[Group] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE published IS NOT NULL AND slug=$slug").query[Group]

  private[sql] def selectTags(): doobie.Query0[Seq[Tag]] =
    Fragment.const0(s"SELECT tags FROM $table").query[Seq[Tag]]
}
