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
import fr.gospeak.infra.services.storage.sql.UserRepoSql.{fields => userFields, searchFields => userSearchFields, table => userTable}
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.libs.scalautils.domain.{CustomException, Done, Page, Tag}

class GroupRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with GroupRepo {
  override def create(data: Group.Data, by: User.Id, now: Instant): IO[Group] =
    run(insert, Group(data, NonEmptyList.of(by), Info(by, now)))

  override def edit(slug: Group.Slug)(data: Group.Data, by: User.Id, now: Instant): IO[Done] = {
    if (data.slug != slug) {
      find(data.slug).flatMap {
        case None => run(update(slug)(data, by, now))
        case _ => IO.raiseError(CustomException(s"You already have a partner with slug ${data.slug}"))
      }
    } else {
      run(update(slug)(data, by, now))
    }
  }

  override def addOwner(group: Group.Id)(owner: User.Id, by: User.Id, now: Instant): IO[Done] =
    find(group).flatMap {
      case Some(groupElt) =>
        if (groupElt.owners.toList.contains(owner)) {
          IO.raiseError(new IllegalArgumentException("owner already added"))
        } else {
          run(updateOwners(groupElt.id)(groupElt.owners.append(owner), by, now))
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable group"))
    }

  override def removeOwner(group: Group.Id)(owner: User.Id, by: User.Id, now: Instant): IO[Done] =
    find(group).flatMap {
      case Some(groupElt) =>
        if (groupElt.owners.toList.contains(owner)) {
          NonEmptyList.fromList(groupElt.owners.filter(_ != owner)).map { owners =>
            run(updateOwners(group)(owners, by, now))
          }.getOrElse {
            IO.raiseError(new IllegalArgumentException("last owner can't be removed"))
          }
        } else {
          IO.raiseError(new IllegalArgumentException("user is not a owner"))
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable group"))
    }

  override def list(params: Page.Params): IO[Page[Group]] = run(selectPage(params).page)

  override def listJoinable(user: User.Id, params: Page.Params): IO[Page[Group]] = run(selectPageJoinable(user, params).page)

  override def list(user: User.Id): IO[Seq[Group]] = run(selectAll(user).to[List])

  override def find(user: User.Id, slug: Group.Slug): IO[Option[Group]] = run(selectOne(user, slug).option)

  override def find(group: Group.Id): IO[Option[Group]] = run(selectOne(group).option)

  override def find(group: Group.Slug): IO[Option[Group]] = run(selectOne(group).option)

  override def exists(group: Group.Slug): IO[Boolean] = run(selectOne(group).option.map(_.isDefined))

  override def listTags(): IO[Seq[Tag]] = run(selectTags().to[List]).map(_.flatten.distinct)
}

object GroupRepoSql {
  private val _ = groupIdMeta // for intellij not remove DoobieUtils.Mappings import
  private[sql] val table: String = "groups"
  private[sql] val fields: Seq[String] = Seq("id", "slug", "name", "contact", "description", "owners", "tags", "created", "created_by", "updated", "updated_by")
  private[sql] val memberTable: String = "group_members"
  private[sql] val memberFields: Seq[String] = Seq("group_id", "user_id", "presentation", "joined_at")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields: Seq[String] = Seq("id", "slug", "name", "contact", "description", "tags")
  private val defaultSort: Page.OrderBy = Page.OrderBy("name")
  private val memberTableFr: Fragment = Fragment.const0(memberTable)
  private val memberFieldsFr: Fragment = Fragment.const0(memberFields.mkString(", "))

  private val memberTableWithUserFr = Fragment.const0(s"$memberTable m INNER JOIN $userTable u ON m.user_id=u.id")
  private val memberFieldsWithUserFr = Fragment.const0((userFields.map("u." + _) ++ memberFields.filter(f => f != "group_id" && f != "user_id").map("m." + _)).mkString(", "))
  private val memberSearchFieldsWithUser: Seq[String] = userSearchFields.map("u." + _) ++ Seq("presentation").map("m." + _)
  private val memberDefaultSortWithUser: Page.OrderBy = Page.OrderBy("m.joined_at")

  private[sql] def insert(e: Group): doobie.Update0 = {
    val values = fr0"${e.id}, ${e.slug}, ${e.name}, ${e.contact}, ${e.description}, ${e.owners}, ${e.tags}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"
    buildInsert(tableFr, fieldsFr, values).update
  }

  private[sql] def update(group: Group.Slug)(data: Group.Data, by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"slug=${data.slug}, name=${data.name}, contact=${data.contact}, description=${data.description}, tags=${data.tags}, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(group)).update
  }

  private[sql] def updateOwners(group: Group.Id)(owners: NonEmptyList[User.Id], by: User.Id, now: Instant): doobie.Update0 =
    buildUpdate(tableFr, fr0"owners=$owners, updated=$now, updated_by=$by", fr0"WHERE id=$group").update

  private[sql] def selectPage(params: Page.Params): Paginated[Group] =
    Paginated[Group](tableFr, fieldsFr, params, defaultSort, searchFields)

  private[sql] def selectPageJoinable(user: User.Id, params: Page.Params): Paginated[Group] =
    Paginated[Group](tableFr, fieldsFr, fr0"WHERE owners NOT LIKE ${"%" + user.value + "%"}", params, defaultSort, searchFields)

  private[sql] def selectAll(user: User.Id): doobie.Query0[Group] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE owners LIKE ${"%" + user.value + "%"}").query[Group]

  private[sql] def selectOne(user: User.Id, slug: Group.Slug): doobie.Query0[Group] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE owners LIKE ${"%" + user.value + "%"} AND slug=$slug").query[Group]

  private[sql] def selectOne(group: Group.Id): doobie.Query0[Group] =
    buildSelect(tableFr, fieldsFr, where(group)).query[Group]

  private[sql] def selectOne(group: Group.Slug): doobie.Query0[Group] =
    buildSelect(tableFr, fieldsFr, where(group)).query[Group]

  private[sql] def selectTags(): doobie.Query0[Seq[Tag]] =
    Fragment.const0(s"SELECT tags FROM $table").query[Seq[Tag]]

  private def where(group: Group.Id): Fragment = fr0"WHERE id=$group"

  private def where(group: Group.Slug): Fragment = fr0"WHERE slug=$group"

  private[sql] def insertMember(g: Group, u: User, presentation: Option[String], now: Instant): doobie.Update0 =
    buildInsert(memberTableFr, memberFieldsFr, fr0"${g.id}, ${u.id}, $presentation, $now").update

  private[sql] def selectPageMembers(group: Group.Id, params: Page.Params): Paginated[Group.Member] =
    Paginated[Group.Member](memberTableWithUserFr, memberFieldsWithUserFr, fr0"WHERE m.group_id=$group", params, memberDefaultSortWithUser, memberSearchFieldsWithUser)

  private[sql] def selectOneMember(group: Group.Id, user: User.Id): doobie.Query0[Group.Member] =
    buildSelect(memberTableWithUserFr, memberFieldsWithUserFr, fr0"WHERE m.group_id=$group AND m.user_id=$user").query[Group.Member]
}
