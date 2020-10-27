package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import gospeak.core.domain.utils.{AdminCtx, BasicCtx, OrgaCtx, UserAwareCtx, UserCtx}
import gospeak.core.domain.{Group, User}
import gospeak.core.services.storage.GroupRepo
import gospeak.infra.services.storage.sql.GroupRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{CustomException, Done, Page, Tag}
import gospeak.libs.sql.doobie.{DbCtx, Field, Query}
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
          groupElt.owners.filter(_ != owner).toNel.map { owners =>
            updateOwners(ctx.group.id)(owners, ctx.user.id, ctx.now).run(xa)
          }.getOrElse {
            IO.raiseError(new IllegalArgumentException("last owner can't be removed"))
          }
        } else {
          IO.raiseError(new IllegalArgumentException("user is not a owner"))
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable group"))
    }

  override def listAllSlugs()(implicit ctx: UserAwareCtx): IO[Seq[(Group.Id, Group.Slug)]] = selectAllSlugs().runList(xa)

  override def listFull(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Group.Full]] = selectPageFull(params).run(xa)

  override def listJoinable(params: Page.Params)(implicit ctx: UserCtx): IO[Page[Group]] = selectPageJoinable(params).run(xa)

  override def list(params: Page.Params)(implicit ctx: AdminCtx): IO[Page[Group]] = selectPage(params).run(xa)

  override def list(user: User.Id): IO[Seq[Group]] = selectAll(user).runList(xa)

  override def listFull(user: User.Id): IO[Seq[Group.Full]] = selectAllFull(user).runList(xa)

  override def list(implicit ctx: UserCtx): IO[Seq[Group]] = selectAll(ctx.user.id).runList(xa)

  override def list(ids: Seq[Group.Id]): IO[Seq[Group]] = runNel(selectAll, ids)

  override def listJoined(params: Page.Params)(implicit ctx: UserCtx): IO[Page[(Group, Group.Member)]] = selectPageJoined(params).run(xa)

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

  override def listMembers(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Group.Member]] = selectPageActiveMembers(group, params).run(xa)

  override def findActiveMember(group: Group.Id, user: User.Id): IO[Option[Group.Member]] = selectOneActiveMember(group, user).runOption(xa)

  override def getStats(implicit ctx: OrgaCtx): IO[Group.Stats] = selectStats(ctx.group.slug).runUnique(xa)
}

object GroupRepoSql {
  private val _ = groupIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.groups
  private val tableSelect = table.dropFields(_.name.startsWith("location_"))
  private val memberTable = Tables.groupMembers
  private val tableFull = tableSelect
    .joinOpt(memberTable, fr0"gm.leaved_at IS NULL", _.id("g") -> _.group_id).get
    .joinOpt(Tables.events, fr0"e.published IS NOT NULL", _.id("g") -> _.group_id).get
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
    .joinOpt(memberTable, fr0"gm.leaved_at IS NULL", _.id("g") -> _.group_id).get
    .joinOpt(Tables.cfps, _.id("g") -> _.group_id).get
    .joinOpt(Tables.proposals, _.id("c") -> _.cfp_id).get
    .joinOpt(Tables.events, _.id("g") -> _.group_id).get
    .aggregate("COALESCE(COUNT(DISTINCT gm.user_id), 0)", "memberCount")
    .aggregate("COALESCE(COUNT(DISTINCT p.id), 0)", "proposalCount")
    .aggregate("COALESCE(COUNT(DISTINCT e.id), 0)", "eventCount")
    .copy(fields = List("id", "slug", "name").map(Field(_, "g")))

  private[sql] def insert(e: Group): Query.Insert[Group] = {
    val values = fr0"${e.id}, ${e.slug}, ${e.name}, ${e.logo}, ${e.banner}, ${e.contact}, ${e.website}, ${e.description}, ${e.location}, ${e.location.map(_.id)}, ${e.location.map(_.geo.lat)}, ${e.location.map(_.geo.lng)}, ${e.location.flatMap(_.locality)}, ${e.location.map(_.country)}, ${e.owners}, " ++
      fr0"${e.social.facebook}, ${e.social.instagram}, ${e.social.twitter}, ${e.social.linkedIn}, ${e.social.youtube}, ${e.social.meetup}, ${e.social.eventbrite}, ${e.social.slack}, ${e.social.discord}, ${e.social.github}, " ++
      fr0"${e.tags}, ${e.status}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    table.insert[Group](e, _ => values)
  }

  private[sql] def update(group: Group.Slug)(d: Group.Data, by: User.Id, now: Instant): Query.Update = {
    val fields = fr0"slug=${d.slug}, name=${d.name}, logo=${d.logo}, banner=${d.banner}, contact=${d.contact}, website=${d.website}, description=${d.description}, location=${d.location}, location_id=${d.location.map(_.id)}, location_lat=${d.location.map(_.geo.lat)}, location_lng=${d.location.map(_.geo.lng)}, location_locality=${d.location.flatMap(_.locality)}, location_country=${d.location.map(_.country)}, " ++
      fr0"social_facebook=${d.social.facebook}, social_instagram=${d.social.instagram}, social_twitter=${d.social.twitter}, social_linkedIn=${d.social.linkedIn}, social_youtube=${d.social.youtube}, social_meetup=${d.social.meetup}, social_eventbrite=${d.social.eventbrite}, social_slack=${d.social.slack}, social_discord=${d.social.discord}, social_github=${d.social.github}, " ++
      fr0"tags=${d.tags}, updated_at=$now, updated_by=$by"
    table.update(fields).where(fr0"g.slug=$group")
  }

  private[sql] def updateOwners(group: Group.Id)(owners: NonEmptyList[User.Id], by: User.Id, now: Instant): Query.Update =
    table.update(fr0"owners=$owners, updated_at=$now, updated_by=$by").where(fr0"g.id=$group")

  private[sql] def selectAllSlugs()(implicit ctx: UserAwareCtx): Query.Select[(Group.Id, Group.Slug)] =
    table.select[(Group.Id, Group.Slug)].fields(Field("id", "g"), Field("slug", "g"))

  private[sql] def selectPage(params: Page.Params)(implicit ctx: AdminCtx): Query.SelectPage[Group] =
    tableSelect.selectPage[Group](params, adapt(ctx))

  private[sql] def selectPageFull(params: Page.Params)(implicit ctx: UserAwareCtx): Query.SelectPage[Group.Full] =
    tableFull.selectPage[Group.Full](params, adapt(ctx))

  private[sql] def selectPageJoinable(params: Page.Params)(implicit ctx: UserCtx): Query.SelectPage[Group] =
    tableSelect.selectPage[Group](params, adapt(ctx)).where(fr0"g.owners NOT LIKE ${"%" + ctx.user.id.value + "%"}")

  private[sql] def selectPageJoined(params: Page.Params)(implicit ctx: UserCtx): Query.SelectPage[(Group, Group.Member)] =
    tableWithMember.selectPage[(Group, Group.Member)](params, adapt(ctx)).where(fr0"gm.user_id=${ctx.user.id}")

  private[sql] def selectAll(user: User.Id): Query.Select[Group] =
    tableSelect.select[Group].where(fr0"g.owners LIKE ${"%" + user.value + "%"}")

  private[sql] def selectAllFull(user: User.Id): Query.Select[Group.Full] =
    tableFull.select[Group.Full].where(fr0"g.owners LIKE ${"%" + user.value + "%"}")

  private[sql] def selectAll(ids: NonEmptyList[Group.Id]): Query.Select[Group] =
    tableSelect.select[Group].where(Fragments.in(fr"g.id", ids))

  private[sql] def selectOne(group: Group.Id): Query.Select[Group] =
    tableSelect.select[Group].where(fr0"g.id=$group")

  private[sql] def selectOne(group: Group.Slug): Query.Select[Group] =
    tableSelect.select[Group].where(fr0"g.slug=$group")

  private[sql] def selectOneFull(group: Group.Slug): Query.Select[Group.Full] =
    tableFull.select[Group.Full].where(fr0"g.slug=$group")

  private[sql] def selectStats(group: Group.Slug): Query.Select[Group.Stats] =
    statTable.select[Group.Stats].where(fr0"g.slug=$group")

  private[sql] def selectTags(): Query.Select[Seq[Tag]] =
    tableSelect.select[Seq[Tag]].fields(Field("tags", "g"))

  private[sql] def insertMember(m: Group.Member): Query.Insert[Group.Member] =
    memberTable.insert[Group.Member](m, e => fr0"${e.group}, ${e.user.id}, ${e.role}, ${e.presentation}, ${e.joinedAt}, ${e.leavedAt}")

  private[sql] def disableMember(m: Group.Member, now: Instant): Query.Update =
    memberTable.update(fr0"leaved_at=$now").where(fr0"gm.group_id=${m.group} AND gm.user_id=${m.user.id}")

  private[sql] def enableMember(m: Group.Member, now: Instant): Query.Update =
    memberTable.update(fr0"joined_at=$now, leaved_at=${Option.empty[Instant]}").where(fr0"gm.group_id=${m.group} AND gm.user_id=${m.user.id}")

  private[sql] def selectPageActiveMembers(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): Query.SelectPage[Group.Member] =
    memberTableWithUser.selectPage[Group.Member](params, adapt(ctx)).where(fr0"gm.group_id=$group AND gm.leaved_at IS NULL")

  private[sql] def selectAllActiveMembers(group: Group.Id): Query.Select[Group.Member] =
    memberTableWithUser.select[Group.Member].where(fr0"gm.group_id=$group AND gm.leaved_at IS NULL")

  private[sql] def selectOneMember(group: Group.Id, user: User.Id): Query.Select[Group.Member] =
    memberTableWithUser.select[Group.Member].where(fr0"gm.group_id=$group AND gm.user_id=$user")

  private[sql] def selectOneActiveMember(group: Group.Id, user: User.Id): Query.Select[Group.Member] =
    memberTableWithUser.select[Group.Member].where(fr0"gm.group_id=$group AND gm.user_id=$user AND gm.leaved_at IS NULL")

  private def adapt(ctx: BasicCtx): DbCtx = DbCtx(ctx.now)
}
