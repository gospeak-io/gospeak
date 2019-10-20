package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.implicits._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services.storage.GroupRepo
import fr.gospeak.infra.services.storage.sql.GroupRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils._
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{CustomException, Done, Page, Tag}

class GroupRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with GroupRepo {
  override def create(data: Group.Data, by: User.Id, now: Instant): IO[Group] =
    insert(Group(data, NonEmptyList.of(by), Info(by, now))).run(xa)

  override def edit(slug: Group.Slug)(data: Group.Data, by: User.Id, now: Instant): IO[Done] = {
    if (data.slug != slug) {
      find(data.slug).flatMap {
        case None => update(slug)(data, by, now).run(xa)
        case _ => IO.raiseError(CustomException(s"You already have a partner with slug ${data.slug}"))
      }
    } else {
      update(slug)(data, by, now).run(xa)
    }
  }

  override def addOwner(group: Group.Id)(owner: User.Id, by: User.Id, now: Instant): IO[Done] =
    find(group).flatMap {
      case Some(groupElt) =>
        if (groupElt.owners.toList.contains(owner)) {
          IO.raiseError(new IllegalArgumentException("owner already added"))
        } else {
          updateOwners(groupElt.id)(groupElt.owners.append(owner), by, now).run(xa)
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable group"))
    }

  override def removeOwner(group: Group.Id)(owner: User.Id, by: User.Id, now: Instant): IO[Done] =
    find(group).flatMap {
      case Some(groupElt) =>
        if (groupElt.owners.toList.contains(owner)) {
          NonEmptyList.fromList(groupElt.owners.filter(_ != owner)).map { owners =>
            updateOwners(group)(owners, by, now).run(xa)
          }.getOrElse {
            IO.raiseError(new IllegalArgumentException("last owner can't be removed"))
          }
        } else {
          IO.raiseError(new IllegalArgumentException("user is not a owner"))
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable group"))
    }

  override def list(params: Page.Params): IO[Page[Group]] = selectPage(params).run(xa)

  override def listJoinable(user: User.Id, params: Page.Params): IO[Page[Group]] = selectPageJoinable(user, params).run(xa)

  override def list(user: User.Id): IO[Seq[Group]] = selectAll(user).runList(xa)

  override def find(user: User.Id, slug: Group.Slug): IO[Option[Group]] = selectOne(user, slug).runOption(xa)

  override def find(group: Group.Id): IO[Option[Group]] = selectOne(group).runOption(xa)

  override def find(group: Group.Slug): IO[Option[Group]] = selectOne(group).runOption(xa)

  override def exists(group: Group.Slug): IO[Boolean] = selectOne(group).runExists(xa)

  override def listTags(): IO[Seq[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)
}

object GroupRepoSql {
  private val _ = groupIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.groups
  private val memberTable = Tables.groupMembers
  private val memberTableWithUser = Tables.groupMembers.join(Tables.users, _.field("user_id"), _.field("id")).flatMap(_.dropField(_.field("user_id"))).get

  private[sql] def insert(e: Group): Insert[Group] = {
    val values = fr0"${e.id}, ${e.slug}, ${e.name}, ${e.contact}, ${e.description}, ${e.owners}, ${e.tags}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"
    table.insert[Group](e, _ => values)
  }

  private[sql] def update(group: Group.Slug)(data: Group.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"g.slug=${data.slug}, g.name=${data.name}, g.contact=${data.contact}, g.description=${data.description}, g.tags=${data.tags}, g.updated=$now, g.updated_by=$by"
    table.update(fields, fr0"WHERE g.slug=$group")
  }

  private[sql] def updateOwners(group: Group.Id)(owners: NonEmptyList[User.Id], by: User.Id, now: Instant): Update =
    table.update(fr0"g.owners=$owners, g.updated=$now, g.updated_by=$by", fr0"WHERE g.id=$group")

  private[sql] def selectPage(params: Page.Params): SelectPage[Group] =
    table.selectPage[Group](params)

  private[sql] def selectPageJoinable(user: User.Id, params: Page.Params): SelectPage[Group] =
    table.selectPage[Group](params, fr0"WHERE g.owners NOT LIKE ${"%" + user.value + "%"}")

  private[sql] def selectAll(user: User.Id): Select[Group] =
    table.select[Group](fr0"WHERE g.owners LIKE ${"%" + user.value + "%"}")

  private[sql] def selectOne(user: User.Id, slug: Group.Slug): Select[Group] =
    table.select[Group](fr0"WHERE g.owners LIKE ${"%" + user.value + "%"} AND g.slug=$slug")

  private[sql] def selectOne(group: Group.Id): Select[Group] =
    table.select[Group](fr0"WHERE g.id=$group")

  private[sql] def selectOne(group: Group.Slug): Select[Group] =
    table.select[Group](fr0"WHERE g.slug=$group")

  private[sql] def selectTags(): Select[Seq[Tag]] =
    table.select[Seq[Tag]](Seq(Field("tags", "g")))

  private[sql] def insertMember(m: Group.Member): Insert[Group.Member] =
    memberTable.insert[Group.Member](m, e => fr0"${e.group}, ${e.user.id}, ${e.role}, ${e.presentation}, ${e.joinedAt}")

  private[sql] def selectPageMembers(group: Group.Id, params: Page.Params): SelectPage[Group.Member] =
    memberTableWithUser.selectPage(params, fr0"WHERE gm.group_id=$group")

  private[sql] def selectOneMember(group: Group.Id, user: User.Id): Select[Group.Member] =
    memberTableWithUser.select(fr0"WHERE gm.group_id=$group AND gm.user_id=$user")
}
