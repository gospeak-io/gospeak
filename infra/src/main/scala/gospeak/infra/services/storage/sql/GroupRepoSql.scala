package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import gospeak.core.domain.utils._
import gospeak.core.domain.{Group, User}
import gospeak.core.services.storage.GroupRepo
import gospeak.infra.services.storage.sql.GroupRepoSql._
import gospeak.infra.services.storage.sql.database.Tables._
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{CustomException, Done, Page, Tag}
import gospeak.libs.sql.doobie.{DbCtx, Field, Query}
import gospeak.libs.sql.dsl.AggField

class GroupRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with GroupRepo {
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

  override def listAllSlugs()(implicit ctx: UserAwareCtx): IO[List[(Group.Id, Group.Slug)]] = selectAllSlugs().runList(xa)

  override def listFull(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Group.Full]] = selectPageFull(params).run(xa)

  override def listJoinable(params: Page.Params)(implicit ctx: UserCtx): IO[Page[Group]] = selectPageJoinable(params).run(xa)

  override def list(params: Page.Params)(implicit ctx: AdminCtx): IO[Page[Group]] = selectPage(params).run(xa)

  override def list(user: User.Id): IO[List[Group]] = selectAll(user).runList(xa)

  override def listFull(user: User.Id): IO[List[Group.Full]] = selectAllFull(user).runList(xa)

  override def list(implicit ctx: UserCtx): IO[List[Group]] = selectAll(ctx.user.id).runList(xa)

  override def list(ids: List[Group.Id]): IO[List[Group]] = runNel(selectAll, ids)

  override def listJoined(params: Page.Params)(implicit ctx: UserCtx): IO[Page[(Group, Group.Member)]] = selectPageJoined(params).run(xa)

  override def find(group: Group.Id): IO[Option[Group]] = selectOne(group).runOption(xa)

  override def find(group: Group.Slug): IO[Option[Group]] = selectOne(group).runOption(xa)

  override def findFull(group: Group.Slug): IO[Option[Group.Full]] = selectOneFull(group).runOption(xa)

  override def exists(group: Group.Slug): IO[Boolean] = selectOne(group).runExists(xa)

  override def listTags(): IO[List[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)

  override def join(group: Group.Id)(user: User, now: Instant): IO[Group.Member] =
    selectOneMember(group, user.id).runOption(xa).flatMap {
      case Some(m) => if (m.isActive) IO.pure(m) else enableMember(m, now).run(xa).map(_ => m.copy(joinedAt = now, leavedAt = None))
      case None => insertMember(Group.Member(group, Group.Member.Role.Member, None, now, None, user)).run(xa)
    }

  override def leave(member: Group.Member)(user: User.Id, now: Instant): IO[Done] =
    if (member.user.id == user) disableMember(member, now).run(xa)
    else IO.raiseError(CustomException("Internal error: authenticated user is not the member one"))

  override def listMembers(implicit ctx: OrgaCtx): IO[List[Group.Member]] = selectAllActiveMembers(ctx.group.id).runList(xa)

  override def listMembers(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Group.Member]] = selectPageActiveMembers(group, params).run(xa)

  override def findActiveMember(group: Group.Id, user: User.Id): IO[Option[Group.Member]] = selectOneActiveMember(group, user).runOption(xa)

  override def getStats(implicit ctx: OrgaCtx): IO[Group.Stats] = selectStats(ctx.group.slug).runUnique(xa)
}

object GroupRepoSql {
  private val _ = groupIdMeta // for intellij not remove DoobieMappings import
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
    .dropFields(f => Set(memberTable.prefix, Tables.events.prefix, Tables.proposals.prefix).contains(f.prefix))
  private val memberTableWithUser = memberTable
    .join(Tables.users, _.user_id -> _.id).flatMap(_.dropField(_.user_id)).get
  private val tableWithMember = tableSelect
    .join(memberTableWithUser, _.id -> _.group_id).get
  private val statTable = table
    .joinOpt(memberTable, fr0"gm.leaved_at IS NULL", _.id("g") -> _.group_id).get
    .joinOpt(Tables.events, _.id("g") -> _.group_id).get
    .joinOpt(Tables.cfps, _.id("g") -> _.group_id).get
    .joinOpt(Tables.proposals, _.id("c") -> _.cfp_id).get
    .aggregate("COALESCE(COUNT(DISTINCT gm.user_id), 0)", "memberCount")
    .aggregate("COALESCE(COUNT(DISTINCT e.id), 0)", "eventCount")
    .aggregate("COALESCE(COUNT(DISTINCT p.id), 0)", "proposalCount")
    .copy(fields = List("id", "slug", "name").map(Field(_, "g")))
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

  private[sql] def insert(e: Group): Query.Insert[Group] = {
    val values = fr0"${e.id}, ${e.slug}, ${e.name}, ${e.logo}, ${e.banner}, ${e.contact}, ${e.website}, ${e.description}, ${e.location}, ${e.location.map(_.id)}, ${e.location.map(_.geo.lat)}, ${e.location.map(_.geo.lng)}, ${e.location.flatMap(_.locality)}, ${e.location.map(_.country)}, ${e.owners}, " ++
      fr0"${e.social.facebook}, ${e.social.instagram}, ${e.social.twitter}, ${e.social.linkedIn}, ${e.social.youtube}, ${e.social.meetup}, ${e.social.eventbrite}, ${e.social.slack}, ${e.social.discord}, ${e.social.github}, " ++
      fr0"${e.tags}, ${e.status}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    val q1 = table.insert[Group](e, _ => values)
    val q2 = GROUPS.insert.values(e.id, e.slug, e.name, e.logo, e.banner, e.contact, e.website, e.description, e.location, e.location.map(_.id), e.location.map(_.geo.lat), e.location.map(_.geo.lng), e.location.map(_.locality), e.location.map(_.country), e.owners,
      e.social.facebook, e.social.instagram, e.social.twitter, e.social.linkedIn, e.social.youtube, e.social.meetup, e.social.eventbrite, e.social.slack, e.social.discord, e.social.github,
      e.tags, e.status, e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def update(group: Group.Slug)(d: Group.Data, by: User.Id, now: Instant): Query.Update = {
    val fields = fr0"slug=${d.slug}, name=${d.name}, logo=${d.logo}, banner=${d.banner}, contact=${d.contact}, website=${d.website}, description=${d.description}, location=${d.location}, location_id=${d.location.map(_.id)}, location_lat=${d.location.map(_.geo.lat)}, location_lng=${d.location.map(_.geo.lng)}, location_locality=${d.location.flatMap(_.locality)}, location_country=${d.location.map(_.country)}, " ++
      fr0"social_facebook=${d.social.facebook}, social_instagram=${d.social.instagram}, social_twitter=${d.social.twitter}, social_linkedIn=${d.social.linkedIn}, social_youtube=${d.social.youtube}, social_meetup=${d.social.meetup}, social_eventbrite=${d.social.eventbrite}, social_slack=${d.social.slack}, social_discord=${d.social.discord}, social_github=${d.social.github}, " ++
      fr0"tags=${d.tags}, updated_at=$now, updated_by=$by"
    val q1 = table.update(fields).where(fr0"g.slug=$group")
    val q2 = GROUPS.update.set(_.SLUG, d.slug).set(_.NAME, d.name).set(_.LOGO, d.logo).set(_.BANNER, d.banner).set(_.CONTACT, d.contact).set(_.WEBSITE, d.website).set(_.DESCRIPTION, d.description).set(_.LOCATION, d.location).set(_.LOCATION_ID, d.location.map(_.id)).set(_.LOCATION_LAT, d.location.map(_.geo.lat)).set(_.LOCATION_LNG, d.location.map(_.geo.lng)).set(_.LOCATION_LOCALITY, d.location.flatMap(_.locality)).set(_.LOCATION_COUNTRY, d.location.map(_.country))
      .set(_.SOCIAL_FACEBOOK, d.social.facebook).set(_.SOCIAL_INSTAGRAM, d.social.instagram).set(_.SOCIAL_TWITTER, d.social.twitter).set(_.SOCIAL_LINKEDIN, d.social.linkedIn).set(_.SOCIAL_YOUTUBE, d.social.youtube).set(_.SOCIAL_MEETUP, d.social.meetup).set(_.SOCIAL_EVENTBRITE, d.social.eventbrite).set(_.SOCIAL_SLACK, d.social.slack).set(_.SOCIAL_DISCORD, d.social.discord).set(_.SOCIAL_GITHUB, d.social.github)
      .set(_.TAGS, d.tags).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by)
      .where(_.SLUG is group)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def updateOwners(group: Group.Id)(owners: NonEmptyList[User.Id], by: User.Id, now: Instant): Query.Update = {
    val q1 = table.update(fr0"owners=$owners, updated_at=$now, updated_by=$by").where(fr0"g.id=$group")
    val q2 = GROUPS.update.set(_.OWNERS, owners).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(_.ID is group)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAllSlugs()(implicit ctx: UserAwareCtx): Query.Select[(Group.Id, Group.Slug)] = {
    val q1 = table.select[(Group.Id, Group.Slug)].fields(Field("id", "g"), Field("slug", "g"))
    val q2 = GROUPS.select.withFields(_.ID, _.SLUG).all[(Group.Id, Group.Slug)]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPage(params: Page.Params)(implicit ctx: AdminCtx): Query.SelectPage[Group] = {
    val q1 = tableSelect.selectPage[Group](params, adapt(ctx))
    val q2 = GROUPS_SELECT.select.page[Group](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPageFull(params: Page.Params)(implicit ctx: UserAwareCtx): Query.SelectPage[Group.Full] = {
    val q1 = tableFull.selectPage[Group.Full](params, adapt(ctx))
    val q2 = GROUPS_FULL.select.page[Group.Full](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPageJoinable(params: Page.Params)(implicit ctx: UserCtx): Query.SelectPage[Group] = {
    val q1 = tableSelect.selectPage[Group](params, adapt(ctx)).where(fr0"g.owners NOT LIKE ${"%" + ctx.user.id.value + "%"}")
    val q2 = GROUPS_SELECT.select.where(_.owners.notLike("%" + ctx.user.id.value + "%")).page[Group](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPageJoined(params: Page.Params)(implicit ctx: UserCtx): Query.SelectPage[(Group, Group.Member)] = {
    val q1 = tableWithMember.selectPage[(Group, Group.Member)](params, adapt(ctx)).where(fr0"gm.user_id=${ctx.user.id}")
    val q2 = GROUP_WITH_MEMBERS.select.where(GROUP_MEMBERS.USER_ID is ctx.user.id).page[(Group, Group.Member)](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAll(user: User.Id): Query.Select[Group] = {
    val q1 = tableSelect.select[Group].where(fr0"g.owners LIKE ${"%" + user.value + "%"}")
    val q2 = GROUPS_SELECT.select.where(_.owners.like("%" + user.value + "%")).all[Group]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAllFull(user: User.Id): Query.Select[Group.Full] = {
    val q1 = tableFull.select[Group.Full].where(fr0"g.owners LIKE ${"%" + user.value + "%"}")
    val q2 = GROUPS_FULL.select.where(_.owners.like("%" + user.value + "%")).all[Group.Full]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAll(ids: NonEmptyList[Group.Id]): Query.Select[Group] = {
    val q1 = tableSelect.select[Group].where(Fragments.in(fr"g.id", ids))
    val q2 = GROUPS_SELECT.select.where(_.id in ids).all[Group]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOne(group: Group.Id): Query.Select[Group] = {
    val q1 = tableSelect.select[Group].where(fr0"g.id=$group")
    val q2 = GROUPS_SELECT.select.where(_.id is group).all[Group]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOne(group: Group.Slug): Query.Select[Group] = {
    val q1 = tableSelect.select[Group].where(fr0"g.slug=$group")
    val q2 = GROUPS_SELECT.select.where(_.slug is group).all[Group]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOneFull(group: Group.Slug): Query.Select[Group.Full] = {
    val q1 = tableFull.select[Group.Full].where(fr0"g.slug=$group")
    val q2 = GROUPS_FULL.select.where(GROUPS.SLUG is group).all[Group.Full]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectStats(group: Group.Slug): Query.Select[Group.Stats] = {
    val q1 = statTable.select[Group.Stats].where(fr0"g.slug=$group")
    val q2 = GROUP_STATS.select.where(GROUPS.SLUG is group).one[Group.Stats]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectTags(): Query.Select[List[Tag]] = {
    val q1 = tableSelect.select[List[Tag]].fields(Field("tags", "g"))
    val q2 = GROUPS_SELECT.select.withFields(_.tags).all[List[Tag]]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def insertMember(m: Group.Member): Query.Insert[Group.Member] = {
    val q1 = memberTable.insert[Group.Member](m, e => fr0"${e.group}, ${e.user.id}, ${e.role}, ${e.presentation}, ${e.joinedAt}, ${e.leavedAt}")
    val q2 = GROUP_MEMBERS.insert.values(m.group, m.user.id, m.role, m.presentation, m.joinedAt, m.leavedAt)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def disableMember(m: Group.Member, now: Instant): Query.Update = {
    val q1 = memberTable.update(fr0"leaved_at=$now").where(fr0"gm.group_id=${m.group} AND gm.user_id=${m.user.id}")
    val q2 = GROUP_MEMBERS.update.setOpt(_.LEAVED_AT, now).where(gm => gm.GROUP_ID.is(m.group) and gm.USER_ID.is(m.user.id))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def enableMember(m: Group.Member, now: Instant): Query.Update = {
    val q1 = memberTable.update(fr0"joined_at=$now, leaved_at=${Option.empty[Instant]}").where(fr0"gm.group_id=${m.group} AND gm.user_id=${m.user.id}")
    val q2 = GROUP_MEMBERS.update.set(_.JOINED_AT, now).set(_.LEAVED_AT, None).where(gm => gm.GROUP_ID.is(m.group) and gm.USER_ID.is(m.user.id))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPageActiveMembers(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): Query.SelectPage[Group.Member] = {
    val q1 = memberTableWithUser.selectPage[Group.Member](params, adapt(ctx)).where(fr0"gm.group_id=$group AND gm.leaved_at IS NULL")
    val q2 = MEMBERS_WITH_USERS.select.where(gm => gm.group_id.is(group) and gm.leaved_at.isNull).page[Group.Member](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAllActiveMembers(group: Group.Id): Query.Select[Group.Member] = {
    val q1 = memberTableWithUser.select[Group.Member].where(fr0"gm.group_id=$group AND gm.leaved_at IS NULL")
    val q2 = MEMBERS_WITH_USERS.select.where(gm => gm.group_id.is(group) and gm.leaved_at.isNull).all[Group.Member]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOneMember(group: Group.Id, user: User.Id): Query.Select[Group.Member] = {
    val q1 = memberTableWithUser.select[Group.Member].where(fr0"gm.group_id=$group AND gm.user_id=$user")
    val q2 = MEMBERS_WITH_USERS.select.where(gm => gm.group_id.is(group) and gm.user_id.is(user)).option[Group.Member]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOneActiveMember(group: Group.Id, user: User.Id): Query.Select[Group.Member] = {
    val q1 = memberTableWithUser.select[Group.Member].where(fr0"gm.group_id=$group AND gm.user_id=$user AND gm.leaved_at IS NULL")
    val q2 = MEMBERS_WITH_USERS.select.where(gm => gm.group_id.is(group) and gm.user_id.is(user) and gm.leaved_at.isNull).option[Group.Member]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private def adapt(ctx: BasicCtx): DbCtx = DbCtx(ctx.now)
}
