package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.syntax.string._
import gospeak.core.domain.utils._
import gospeak.core.domain.{Group, User}
import gospeak.core.services.storage.GroupRepo
import gospeak.infra.services.storage.sql.GroupRepoSql._
import gospeak.infra.services.storage.sql.database.Tables._
import gospeak.infra.services.storage.sql.database.tables.{GROUPS, GROUP_MEMBERS}
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{CustomException, Page, Tag}
import gospeak.libs.sql.dsl.{AggField, Query}

class GroupRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with GroupRepo {
  override def create(data: Group.Data)(implicit ctx: UserCtx): IO[Group] = {
    val group = Group(data, NonEmptyList.of(ctx.user.id), ctx.info)
    insert(group).run(xa).map(_ => group)
  }

  override def edit(data: Group.Data)(implicit ctx: OrgaCtx): IO[Unit] = {
    if (data.slug != ctx.group.slug) {
      find(data.slug).flatMap {
        case None => update(ctx.group.slug)(data, ctx.user.id, ctx.now).run(xa)
        case _ => IO.raiseError(CustomException(s"You already have a partner with slug ${data.slug}"))
      }
    } else {
      update(ctx.group.slug)(data, ctx.user.id, ctx.now).run(xa)
    }
  }

  override def addOwner(group: Group.Id, owner: User.Id, by: User.Id)(implicit ctx: UserCtx): IO[Unit] =
    find(group).flatMap {
      case Some(groupElt) =>
        if (groupElt.owners.toList.contains(owner)) {
          IO.raiseError(new IllegalArgumentException("owner already added"))
        } else {
          updateOwners(groupElt.id)(groupElt.owners.append(owner), by, ctx.now).run(xa)
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable group"))
    }

  override def removeOwner(owner: User.Id)(implicit ctx: OrgaCtx): IO[Unit] =
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

  override def listAllSlugs()(implicit ctx: UserAwareCtx): IO[List[(Group.Id, Group.Slug)]] = selectAllSlugs().run(xa)

  override def listFull(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Group.Full]] = selectPageFull(params).run(xa)

  override def listJoinable(params: Page.Params)(implicit ctx: UserCtx): IO[Page[Group]] = selectPageJoinable(params).run(xa)

  override def list(params: Page.Params)(implicit ctx: AdminCtx): IO[Page[Group]] = selectPage(params).run(xa)

  override def list(user: User.Id): IO[List[Group]] = selectAll(user).run(xa)

  override def listFull(user: User.Id): IO[List[Group.Full]] = selectAllFull(user).run(xa)

  override def list(implicit ctx: UserCtx): IO[List[Group]] = selectAll(ctx.user.id).run(xa)

  override def list(ids: List[Group.Id]): IO[List[Group]] = runNel(selectAll, ids)

  override def listJoined(params: Page.Params)(implicit ctx: UserCtx): IO[Page[(Group, Group.Member)]] = selectPageJoined(params).run(xa)

  override def find(group: Group.Id): IO[Option[Group]] = selectOne(group).run(xa)

  override def find(group: Group.Slug): IO[Option[Group]] = selectOne(group).run(xa)

  override def findFull(group: Group.Slug): IO[Option[Group.Full]] = selectOneFull(group).run(xa)

  override def exists(group: Group.Slug): IO[Boolean] = selectOne(group).run(xa).map(_.isDefined)

  override def listTags(): IO[List[Tag]] = selectTags().run(xa).map(_.flatten.distinct)

  override def join(group: Group.Id)(user: User, now: Instant): IO[Group.Member] =
    selectOneMember(group, user.id).run(xa).flatMap {
      case Some(m) =>
        if (m.isActive) IO.pure(m)
        else enableMember(m, now).run(xa).map(_ => m.copy(joinedAt = now, leavedAt = None))
      case None =>
        val member = Group.Member(group, Group.Member.Role.Member, None, now, None, user)
        insertMember(member).run(xa).map(_ => member)
    }

  override def leave(member: Group.Member)(user: User.Id, now: Instant): IO[Unit] =
    if (member.user.id == user) disableMember(member, now).run(xa)
    else IO.raiseError(CustomException("Internal error: authenticated user is not the member one"))

  override def listMembers(implicit ctx: OrgaCtx): IO[List[Group.Member]] = selectAllActiveMembers(ctx.group.id).run(xa)

  override def listMembers(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Group.Member]] = selectPageActiveMembers(group, params).run(xa)

  override def findActiveMember(group: Group.Id, user: User.Id): IO[Option[Group.Member]] = selectOneActiveMember(group, user).run(xa)

  override def getStats(implicit ctx: OrgaCtx): IO[Group.Stats] = selectStats(ctx.group.slug).run(xa)
}

object GroupRepoSql {
  private val GROUPS_SELECT = GROUPS.dropFields(_.name.startsWith("location_"))
  private val GROUPS_FULL = GROUPS_SELECT
    .join(GROUP_MEMBERS, _.LeftOuter).on(gm => GROUPS.ID.is(gm.GROUP_ID) and gm.LEAVED_AT.isNull)
    .join(EVENTS, _.LeftOuter).on(e => GROUPS.ID.is(e.GROUP_ID) and e.PUBLISHED.notNull)
    .join(PROPOSALS, _.LeftOuter).on(EVENTS.ID is _.EVENT_ID)
    .dropFields(GROUP_MEMBERS.getFields ++ EVENTS.getFields ++ PROPOSALS.getFields)
    .addFields(
      AggField("COALESCE(COUNT(DISTINCT gm.user_id), 0)", "memberCount"),
      AggField("COALESCE(COUNT(DISTINCT e.id), 0)", "eventCount"),
      AggField("COALESCE(COUNT(DISTINCT p.id), 0)", "talkCount"))
  private val MEMBERS_WITH_USERS = GROUP_MEMBERS.joinOn(_.USER_ID).dropFields(GROUP_MEMBERS.USER_ID)
  private val GROUP_WITH_MEMBERS = GROUPS_SELECT.join(MEMBERS_WITH_USERS).on(GROUPS.ID is GROUP_MEMBERS.GROUP_ID)
  private val GROUP_STATS = GROUPS
    .join(GROUP_MEMBERS, _.LeftOuter).on((g, gm) => g.ID.is(gm.GROUP_ID) and gm.LEAVED_AT.isNull)
    .join(EVENTS, _.LeftOuter).on(GROUPS.ID is _.GROUP_ID)
    .join(CFPS, _.LeftOuter).on(GROUPS.ID is _.GROUP_ID)
    .join(PROPOSALS, _.LeftOuter).on(CFPS.ID is _.CFP_ID)
    .fields(GROUPS.ID, GROUPS.SLUG, GROUPS.NAME)
    .addFields(
      AggField("COALESCE(COUNT(DISTINCT gm.user_id), 0)", "memberCount"),
      AggField("COALESCE(COUNT(DISTINCT e.id), 0)", "eventCount"),
      AggField("COALESCE(COUNT(DISTINCT p.id), 0)", "proposalCount"))

  private[sql] def insert(e: Group): Query.Insert[GROUPS] =
  // GROUPS.insert.values(e.id, e.slug, e.name, e.logo, e.banner, e.contact, e.website, e.description, e.location, e.location.map(_.id), e.location.map(_.geo.lat), e.location.map(_.geo.lng), e.location.map(_.locality), e.location.map(_.country), e.owners,
  //   e.social.facebook, e.social.instagram, e.social.twitter, e.social.linkedIn, e.social.youtube, e.social.meetup, e.social.eventbrite, e.social.slack, e.social.discord, e.social.github,
  //   e.tags, e.status, e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    GROUPS.insert.values(fr0"${e.id}, ${e.slug}, ${e.name}, ${e.logo}, ${e.banner}, ${e.contact}, ${e.website}, ${e.description}, ${e.location}, ${e.location.map(_.id)}, ${e.location.map(_.geo.lat)}, ${e.location.map(_.geo.lng)}, ${e.location.flatMap(_.locality)}, ${e.location.map(_.country)}, ${e.owners}, " ++
      fr0"${e.social.facebook}, ${e.social.instagram}, ${e.social.twitter}, ${e.social.linkedIn}, ${e.social.youtube}, ${e.social.meetup}, ${e.social.eventbrite}, ${e.social.slack}, ${e.social.discord}, ${e.social.github}, " ++
      fr0"${e.tags}, ${e.status}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}")

  private[sql] def update(group: Group.Slug)(d: Group.Data, by: User.Id, now: Instant): Query.Update[GROUPS] =
    GROUPS.update.set(_.SLUG, d.slug).set(_.NAME, d.name).set(_.LOGO, d.logo).set(_.BANNER, d.banner).set(_.CONTACT, d.contact).set(_.WEBSITE, d.website).set(_.DESCRIPTION, d.description).set(_.LOCATION, d.location).set(_.LOCATION_ID, d.location.map(_.id)).set(_.LOCATION_LAT, d.location.map(_.geo.lat)).set(_.LOCATION_LNG, d.location.map(_.geo.lng)).set(_.LOCATION_LOCALITY, d.location.flatMap(_.locality)).set(_.LOCATION_COUNTRY, d.location.map(_.country))
      .set(_.SOCIAL_FACEBOOK, d.social.facebook).set(_.SOCIAL_INSTAGRAM, d.social.instagram).set(_.SOCIAL_TWITTER, d.social.twitter).set(_.SOCIAL_LINKEDIN, d.social.linkedIn).set(_.SOCIAL_YOUTUBE, d.social.youtube).set(_.SOCIAL_MEETUP, d.social.meetup).set(_.SOCIAL_EVENTBRITE, d.social.eventbrite).set(_.SOCIAL_SLACK, d.social.slack).set(_.SOCIAL_DISCORD, d.social.discord).set(_.SOCIAL_GITHUB, d.social.github)
      .set(_.TAGS, d.tags).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by)
      .where(_.SLUG is group)

  private[sql] def updateOwners(group: Group.Id)(owners: NonEmptyList[User.Id], by: User.Id, now: Instant): Query.Update[GROUPS] =
    GROUPS.update.set(_.OWNERS, owners).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(_.ID is group)

  private[sql] def selectAllSlugs()(implicit ctx: UserAwareCtx): Query.Select.All[(Group.Id, Group.Slug)] =
    GROUPS.select.withFields(_.ID, _.SLUG).all[(Group.Id, Group.Slug)]

  private[sql] def selectPage(params: Page.Params)(implicit ctx: AdminCtx): Query.Select.Paginated[Group] =
    GROUPS_SELECT.select.page[Group](params, ctx.toDb)

  private[sql] def selectPageFull(params: Page.Params)(implicit ctx: UserAwareCtx): Query.Select.Paginated[Group.Full] =
    GROUPS_FULL.select.page[Group.Full](params, ctx.toDb)

  private[sql] def selectPageJoinable(params: Page.Params)(implicit ctx: UserCtx): Query.Select.Paginated[Group] =
    GROUPS_SELECT.select.where(_.owners.notLike("%" + ctx.user.id.value + "%")).page[Group](params, ctx.toDb)

  private[sql] def selectPageJoined(params: Page.Params)(implicit ctx: UserCtx): Query.Select.Paginated[(Group, Group.Member)] =
    GROUP_WITH_MEMBERS.select.where(GROUP_MEMBERS.USER_ID is ctx.user.id).page[(Group, Group.Member)](params, ctx.toDb)

  private[sql] def selectAll(user: User.Id): Query.Select.All[Group] =
    GROUPS_SELECT.select.where(_.owners.like("%" + user.value + "%")).all[Group]

  private[sql] def selectAllFull(user: User.Id): Query.Select.All[Group.Full] =
    GROUPS_FULL.select.where(_.owners.like("%" + user.value + "%")).all[Group.Full]

  private[sql] def selectAll(ids: NonEmptyList[Group.Id]): Query.Select.All[Group] =
    GROUPS_SELECT.select.where(_.id in ids).all[Group]

  private[sql] def selectOne(group: Group.Id): Query.Select.Optional[Group] =
    GROUPS_SELECT.select.where(_.id is group).option[Group]

  private[sql] def selectOne(group: Group.Slug): Query.Select.Optional[Group] =
    GROUPS_SELECT.select.where(_.slug is group).option[Group]

  private[sql] def selectOneFull(group: Group.Slug): Query.Select.Optional[Group.Full] =
    GROUPS_FULL.select.where(GROUPS.SLUG is group).option[Group.Full]

  private[sql] def selectStats(group: Group.Slug): Query.Select.One[Group.Stats] =
    GROUP_STATS.select.where(GROUPS.SLUG is group).one[Group.Stats]

  private[sql] def selectTags(): Query.Select.All[List[Tag]] =
    GROUPS_SELECT.select.withFields(_.tags).all[List[Tag]]

  private[sql] def insertMember(m: Group.Member): Query.Insert[GROUP_MEMBERS] =
  // GROUP_MEMBERS.insert.values(m.group, m.user.id, m.role, m.presentation, m.joinedAt, m.leavedAt)
    GROUP_MEMBERS.insert.values(fr0"${m.group}, ${m.user.id}, ${m.role}, ${m.presentation}, ${m.joinedAt}, ${m.leavedAt}")

  private[sql] def disableMember(m: Group.Member, now: Instant): Query.Update[GROUP_MEMBERS] =
    GROUP_MEMBERS.update.set(_.LEAVED_AT, now).where(gm => gm.GROUP_ID.is(m.group) and gm.USER_ID.is(m.user.id))

  private[sql] def enableMember(m: Group.Member, now: Instant): Query.Update[GROUP_MEMBERS] =
    GROUP_MEMBERS.update.set(_.JOINED_AT, now).set(_.LEAVED_AT, None).where(gm => gm.GROUP_ID.is(m.group) and gm.USER_ID.is(m.user.id))

  private[sql] def selectPageActiveMembers(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): Query.Select.Paginated[Group.Member] =
    MEMBERS_WITH_USERS.select.where(gm => gm.group_id.is(group) and gm.leaved_at.isNull).page[Group.Member](params, ctx.toDb)

  private[sql] def selectAllActiveMembers(group: Group.Id): Query.Select.All[Group.Member] =
    MEMBERS_WITH_USERS.select.where(gm => gm.group_id.is(group) and gm.leaved_at.isNull).all[Group.Member]

  private[sql] def selectOneMember(group: Group.Id, user: User.Id): Query.Select.Optional[Group.Member] =
    MEMBERS_WITH_USERS.select.where(gm => gm.group_id.is(group) and gm.user_id.is(user)).option[Group.Member]

  private[sql] def selectOneActiveMember(group: Group.Id, user: User.Id): Query.Select.Optional[Group.Member] =
    MEMBERS_WITH_USERS.select.where(gm => gm.group_id.is(group) and gm.user_id.is(user) and gm.leaved_at.isNull).option[Group.Member]
}
