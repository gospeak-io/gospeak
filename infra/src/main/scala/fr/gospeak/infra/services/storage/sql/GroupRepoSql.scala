package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import fr.gospeak.core.domain.utils.{OrgaCtx, UserCtx}
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services.storage.GroupRepo
import fr.gospeak.infra.services.storage.sql.GroupRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{CustomException, Done, Page, Tag}
import org.slf4j.LoggerFactory

class GroupRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with GroupRepo {
  private val logger = LoggerFactory.getLogger(this.getClass)

  override def create(data: Group.Data)(implicit ctx: UserCtx): IO[Group] =
    insert(Group(data, NonEmptyList.of(ctx.user.id), ctx.info)).run(xa)

  override def edit(data: Group.Data)(implicit ctx: OrgaCtx): IO[Done] = {
    if (data.slug != ctx.group.slug) {
      find(data.slug).flatMap {
        case None => update(ctx.group.slug)(data, ctx.user.id, ctx.now).run(xa)
        case _ => IO.raiseError(CustomException(s"You already have a partner with slug ${data.slug}"))
      }
    } else {
      update(ctx.group.slug)(data, ctx.user.id, ctx.now).run(xa)
    }
  }

  override def addOwner(group: Group.Id, owner: User.Id, by: User.Id)(implicit ctx: UserCtx): IO[Done] =
    find(group).flatMap {
      case Some(groupElt) =>
        if (groupElt.owners.toList.contains(owner)) {
          IO.raiseError(new IllegalArgumentException("owner already added"))
        } else {
          updateOwners(groupElt.id)(groupElt.owners.append(owner), by, ctx.now).run(xa)
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable group"))
    }

  override def removeOwner(owner: User.Id)(implicit ctx: OrgaCtx): IO[Done] =
    find(ctx.group.id).flatMap {
      case Some(groupElt) =>
        if (groupElt.owners.toList.contains(owner)) {
          NonEmptyList.fromList(groupElt.owners.filter(_ != owner)).map { owners =>
            updateOwners(ctx.group.id)(owners, ctx.user.id, ctx.now).run(xa)
          }.getOrElse {
            IO.raiseError(new IllegalArgumentException("last owner can't be removed"))
          }
        } else {
          IO.raiseError(new IllegalArgumentException("user is not a owner"))
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable group"))
    }

  override def list(params: Page.Params): IO[Page[Group.Full]] = selectPage(params).run(xa)

  override def listJoinable(params: Page.Params)(implicit ctx: UserCtx): IO[Page[Group]] = selectPageJoinable(ctx.user.id, params).run(xa)

  override def list(user: User.Id): IO[Seq[Group]] = selectAll(user).runList(xa)

  override def listFull(user: User.Id): IO[Seq[Group.Full]] = selectAllFull(user).runList(xa)

  override def list(implicit ctx: UserCtx): IO[Seq[Group]] = selectAll(ctx.user.id).runList(xa)

  override def list(ids: Seq[Group.Id]): IO[Seq[Group]] = runNel(selectAll, ids)

  override def listJoined(params: Page.Params)(implicit ctx: UserCtx): IO[Page[(Group, Group.Member)]] = selectPageJoined(ctx.user.id, params).run(xa)

  override def find(user: User.Id, slug: Group.Slug): IO[Option[Group]] = selectOne(user, slug).runOption(xa)

  override def find(group: Group.Id): IO[Option[Group]] = selectOne(group).runOption(xa)

  override def find(group: Group.Slug): IO[Option[Group]] = selectOne(group).runOption(xa)

  override def findFull(group: Group.Slug): IO[Option[Group.Full]] = selectOneFull(group).runOption(xa)

  override def exists(group: Group.Slug): IO[Boolean] = selectOne(group).runExists(xa)

  override def listTags(): IO[Seq[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)

  override def join(group: Group.Id)(user: User, now: Instant): IO[Done] =
    selectOneMember(group, user.id).runOption(xa).flatMap {
      case Some(m) => if (m.isActive) IO.pure(Done) else enableMember(m, now).run(xa)
      case None => insertMember(Group.Member(group, Group.Member.Role.Member, None, now, None, user)).run(xa).map(_ => Done)
    }

  override def leave(member: Group.Member)(user: User.Id, now: Instant): IO[Done] =
    if (member.user.id == user) disableMember(member, now).run(xa)
    else IO.raiseError(CustomException("Internal error: authenticated user is not the member one"))

  override def listMembers(implicit ctx: OrgaCtx): IO[Seq[Group.Member]] = selectAllActiveMembers(ctx.group.id).runList(xa)

  override def listMembers(group: Group.Id, params: Page.Params): IO[Page[Group.Member]] = selectPageActiveMembers(group, params).run(xa)

  override def findActiveMember(group: Group.Id, user: User.Id): IO[Option[Group.Member]] = selectOneActiveMember(group, user).runOption(xa)

  override def getStats(implicit ctx: OrgaCtx): IO[Group.Stats] = selectStats(ctx.group.slug).runUnique(xa)
}

object GroupRepoSql {
  private val _ = groupIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.groups
  private val tableSelect = table.dropFields(_.name.startsWith("location_"))
  private val memberTable = Tables.groupMembers
  private val tableFull = tableSelect
    .joinOpt(memberTable, _.id("g") -> _.group_id, fr0"gm.leaved_at IS NULL").get
    .joinOpt(Tables.events, _.id("g") -> _.group_id, fr0"e.published IS NOT NULL").get
    .joinOpt(Tables.proposals, _.id("e") -> _.event_id).get
    .aggregate("COALESCE(COUNT(DISTINCT gm.user_id), 0)", "memberCount")
    .aggregate("COALESCE(COUNT(DISTINCT e.id), 0)", "eventCount")
    .aggregate("COALESCE(COUNT(DISTINCT p.id), 0)", "talkCount")
    .dropFields(f => Seq(memberTable.prefix, Tables.events.prefix, Tables.proposals.prefix).contains(f.prefix))
  private val memberTableWithUser = Tables.groupMembers
    .join(Tables.users, _.user_id -> _.id).flatMap(_.dropField(_.user_id)).get
  private val tableWithMember = tableSelect
    .join(memberTableWithUser, _.id -> _.group_id).get
  private val statTable = table
    .joinOpt(memberTable, _.id("g") -> _.group_id, fr0"gm.leaved_at IS NULL").get
    .joinOpt(Tables.cfps, _.id("g") -> _.group_id).get
    .joinOpt(Tables.proposals, _.id("c") -> _.cfp_id).get
    .joinOpt(Tables.events, _.id("g") -> _.group_id).get
    .aggregate("COALESCE(COUNT(DISTINCT gm.user_id), 0)", "memberCount")
    .aggregate("COALESCE(COUNT(DISTINCT p.id), 0)", "proposalCount")
    .aggregate("COALESCE(COUNT(DISTINCT e.id), 0)", "eventCount")
    .copy(fields = Seq("id", "slug", "name").map(Field(_, "g")))

  private[sql] def insert(e: Group): Insert[Group] = {
    val values = fr0"${e.id}, ${e.slug}, ${e.name}, ${e.logo}, ${e.banner}, ${e.contact}, ${e.website}, ${e.description}, ${e.location}, ${e.location.map(_.id)}, ${e.location.map(_.geo.lat)}, ${e.location.map(_.geo.lng)}, ${e.location.flatMap(_.locality)}, ${e.location.map(_.country)}, ${e.owners}, " ++
      fr0"${e.social.facebook}, ${e.social.instagram}, ${e.social.twitter}, ${e.social.linkedIn}, ${e.social.youtube}, ${e.social.meetup}, ${e.social.eventbrite}, ${e.social.slack}, ${e.social.discord}, ${e.social.github}, " ++
      fr0"${e.tags}, ${e.status}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    table.insert[Group](e, _ => values)
  }

  private[sql] def update(group: Group.Slug)(d: Group.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"slug=${d.slug}, name=${d.name}, logo=${d.logo}, banner=${d.banner}, contact=${d.contact}, website=${d.website}, description=${d.description}, location=${d.location}, location_id=${d.location.map(_.id)}, location_lat=${d.location.map(_.geo.lat)}, location_lng=${d.location.map(_.geo.lng)}, location_locality=${d.location.flatMap(_.locality)}, location_country=${d.location.map(_.country)}, " ++
      fr0"social_facebook=${d.social.facebook}, social_instagram=${d.social.instagram}, social_twitter=${d.social.twitter}, social_linkedIn=${d.social.linkedIn}, social_youtube=${d.social.youtube}, social_meetup=${d.social.meetup}, social_eventbrite=${d.social.eventbrite}, social_slack=${d.social.slack}, social_discord=${d.social.discord}, social_github=${d.social.github}, " ++
      fr0"tags=${d.tags}, updated_at=$now, updated_by=$by"
    table.update(fields, fr0"WHERE g.slug=$group")
  }

  private[sql] def updateOwners(group: Group.Id)(owners: NonEmptyList[User.Id], by: User.Id, now: Instant): Update =
    table.update(fr0"owners=$owners, updated_at=$now, updated_by=$by", fr0"WHERE g.id=$group")

  private[sql] def selectPage(params: Page.Params): SelectPage[Group.Full] =
    tableFull.selectPage[Group.Full](params)

  private[sql] def selectPageJoinable(user: User.Id, params: Page.Params): SelectPage[Group] =
    tableSelect.selectPage[Group](params, fr0"WHERE g.owners NOT LIKE ${"%" + user.value + "%"}")

  private[sql] def selectPageJoined(user: User.Id, params: Page.Params): SelectPage[(Group, Group.Member)] =
    tableWithMember.selectPage[(Group, Group.Member)](params, fr0"WHERE gm.user_id=$user")

  private[sql] def selectAll(user: User.Id): Select[Group] =
    tableSelect.select[Group](fr0"WHERE g.owners LIKE ${"%" + user.value + "%"}")

  private[sql] def selectAllFull(user: User.Id): Select[Group.Full] =
    tableFull.select[Group.Full](fr0"WHERE g.owners LIKE ${"%" + user.value + "%"}")

  private[sql] def selectAll(ids: NonEmptyList[Group.Id]): Select[Group] =
    tableSelect.select[Group](fr0"WHERE " ++ Fragments.in(fr"g.id", ids))

  private[sql] def selectOne(user: User.Id, slug: Group.Slug): Select[Group] =
    tableSelect.select[Group](fr0"WHERE g.owners LIKE ${"%" + user.value + "%"} AND g.slug=$slug")

  private[sql] def selectOne(group: Group.Id): Select[Group] =
    tableSelect.select[Group](fr0"WHERE g.id=$group")

  private[sql] def selectOne(group: Group.Slug): Select[Group] =
    tableSelect.select[Group](fr0"WHERE g.slug=$group")

  private[sql] def selectOneFull(group: Group.Slug): Select[Group.Full] =
    tableFull.select[Group.Full](fr0"WHERE g.slug=$group")

  private[sql] def selectStats(group: Group.Slug): Select[Group.Stats] =
    statTable.select[Group.Stats](fr0"WHERE g.slug=$group")

  private[sql] def selectTags(): Select[Seq[Tag]] =
    tableSelect.select[Seq[Tag]](Seq(Field("tags", "g")), Seq())

  private[sql] def insertMember(m: Group.Member): Insert[Group.Member] =
    memberTable.insert[Group.Member](m, e => fr0"${e.group}, ${e.user.id}, ${e.role}, ${e.presentation}, ${e.joinedAt}, ${e.leavedAt}")

  private[sql] def disableMember(m: Group.Member, now: Instant): Update =
    memberTable.update(fr0"gm.leaved_at=$now", fr0"WHERE gm.group_id=${m.group} AND gm.user_id=${m.user.id}")

  private[sql] def enableMember(m: Group.Member, now: Instant): Update =
    memberTable.update(fr0"gm.joined_at=$now, gm.leaved_at=${Option.empty[Instant]}", fr0"WHERE gm.group_id=${m.group} AND gm.user_id=${m.user.id}")

  private[sql] def selectPageActiveMembers(group: Group.Id, params: Page.Params): SelectPage[Group.Member] =
    memberTableWithUser.selectPage(params, fr0"WHERE gm.group_id=$group AND gm.leaved_at IS NULL")

  private[sql] def selectAllActiveMembers(group: Group.Id): Select[Group.Member] =
    memberTableWithUser.select(fr0"WHERE gm.group_id=$group AND gm.leaved_at IS NULL")

  private[sql] def selectOneMember(group: Group.Id, user: User.Id): Select[Group.Member] =
    memberTableWithUser.select(fr0"WHERE gm.group_id=$group AND gm.user_id=$user")

  private[sql] def selectOneActiveMember(group: Group.Id, user: User.Id): Select[Group.Member] =
    memberTableWithUser.select(fr0"WHERE gm.group_id=$group AND gm.user_id=$user AND gm.leaved_at IS NULL")
}
