package gospeak.infra.services.storage.sql

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.syntax.connectionio._
import doobie.syntax.string._
import doobie.util.fragment.Fragment
import fr.loicknuchel.safeql.models.FailedQuery
import fr.loicknuchel.safeql.{AggField, Field, Query, Table}
import gospeak.core.domain.utils._
import gospeak.core.domain.{Group, Proposal, User}
import gospeak.core.services.storage.UserRepo
import gospeak.infra.services.storage.sql.UserRepoSql._
import gospeak.infra.services.storage.sql.database.Tables._
import gospeak.infra.services.storage.sql.database.tables.{CREDENTIALS, LOGINS, USERS}
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{EmailAddress, Page}

import java.time.Instant
import scala.util.control.NonFatal

class UserRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with UserRepo {
  override def create(data: User.Data, now: Instant, emailValidated: Option[Instant]): IO[User] = {
    val user = User(data, now, emailValidated)
    insert(user).run(xa).map(_ => user)
  }

  override def edit(user: User.Id)(data: User.Data, now: Instant): IO[User] =
    update(user)(data, now).run(xa).flatMap(_ => find(user).flatMap(_.toIO(new IllegalArgumentException(s"User $user does not exists"))))

  override def edit(data: User.Data)(implicit ctx: UserCtx): IO[User] = edit(ctx.user.id)(data, ctx.now)

  override def editStatus(status: User.Status)(implicit ctx: UserCtx): IO[Unit] =
    selectOne(ctx.user.id).run(xa).flatMap {
      case Some(userElt) => update(ctx.user.id)(userElt.data.copy(status = status), ctx.now).run(xa)
      case None => IO.raiseError(new IllegalArgumentException(s"User ${ctx.user.id} does not exists"))
    }

  override def createLoginRef(login: User.Login, user: User.Id): IO[Unit] = insertLoginRef(User.LoginRef(login, user)).run(xa)

  override def createCredentials(credentials: User.Credentials): IO[User.Credentials] = insertCredentials(credentials).run(xa).map(_ => credentials)

  override def editCredentials(login: User.Login)(pass: User.Password): IO[Unit] = updateCredentials(login)(pass).run(xa)

  override def removeCredentials(login: User.Login): IO[Unit] = deleteCredentials(login).run(xa)

  override def findCredentials(login: User.Login): IO[Option[User.Credentials]] = selectCredentials(login).run(xa)

  override def find(login: User.Login): IO[Option[User]] = selectOne(login).run(xa)

  override def find(credentials: User.Credentials): IO[Option[User]] = selectOne(credentials.login).run(xa)

  override def find(email: EmailAddress): IO[Option[User]] = selectOne(email).run(xa)

  override def find(slug: User.Slug): IO[Option[User]] = selectOne(slug).run(xa)

  override def find(id: User.Id): IO[Option[User]] = selectOne(id).run(xa)

  override def findPublic(slug: User.Slug)(implicit ctx: UserAwareCtx): IO[Option[User.Full]] = selectOnePublic(slug).run(xa)

  // FIXME should be done in only one query: joining on speakers array or splitting speakers string
  override def speakers(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[User.Full]] = {
    val speakerIdsQuery = PROPOSALS_WITH_CFPS.select.fields(PROPOSALS.SPEAKERS).where(CFPS.GROUP_ID.is(ctx.group.id)).all[NonEmptyList[User.Id]]
    for {
      speakerIds <- speakerIdsQuery.run(xa).map(_.flatMap(_.toList).distinct)
      res <- speakerIds.toNel.map(ids => selectPage(ids, params).run(xa).map(_.fromSql)).getOrElse(IO.pure(Page.empty[User.Full]))
    } yield res
  }

  override def speakersPublic(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[User.Full]] = {
    val speakerIdsQuery = PROPOSALS_WITH_CFPS_EVENTS.select.fields(PROPOSALS.SPEAKERS).where(CFPS.GROUP_ID.is(group) and EVENTS.PUBLISHED.notNull).all[NonEmptyList[User.Id]]
    for {
      speakerIds <- speakerIdsQuery.run(xa).map(_.flatMap(_.toList).distinct)
      res <- speakerIds.toNel.map(ids => selectPage(ids, params).run(xa).map(_.fromSql)).getOrElse(IO.pure(Page.empty[User.Full]))
    } yield res
  }

  override def speakerCountPublic(group: Group.Id): IO[Long] = {
    PROPOSALS_WITH_CFPS_EVENTS.select.fields(PROPOSALS.SPEAKERS).where(CFPS.GROUP_ID.is(group) and EVENTS.PUBLISHED.notNull).all[NonEmptyList[User.Id]]
      .run(xa).map(_.flatMap(_.toList).distinct.length.toLong)
  }

  override def listAllPublicSlugs()(implicit ctx: UserAwareCtx): IO[List[(User.Id, User.Slug)]] = selectAllPublicSlugs().run(xa)

  override def listPublic(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[User.Full]] = selectPagePublic(params).run(xa).map(_.fromSql)

  override def list(ids: List[User.Id]): IO[List[User]] = runNel(selectAll, ids)

  override def list(params: Page.Params)(implicit ctx: AdminCtx): IO[Page[User.Admin]] = selectPageAdmin(params).run(xa).map(_.fromSql)

  override def delete(user: User.Id)(implicit ctx: AdminCtx): IO[Unit] = for {
    logins <- selectLogins(user).run(xa)
    _ <- logins.map(deleteCredentials(_).run(xa)).sequence
    _ <- execUpdate(deleteLogins(user).fr)
    _ <- deleteUser(user).run(xa)
  } yield ()

  // to allow a delete all that is not supported in safeql Delete
  private def execUpdate(fr: Fragment): IO[Int] = fr.update.run.transact(xa).recoverWith { case NonFatal(e) => IO.raiseError(FailedQuery(fr, e)) }
}

object UserRepoSql {
  private val PROPOSALS_WITH_CFPS = PROPOSALS.joinOn(_.CFP_ID)
  private val PROPOSALS_WITH_CFPS_EVENTS = PROPOSALS_WITH_CFPS.joinOn(PROPOSALS.EVENT_ID, _.Inner)
  private val USERS_WITH_LOGINS = USERS.join(LOGINS).on(_.ID is _.USER_ID).dropFields(LOGINS.getFields)
  val FILTERS = List(Table.Filter.Bool.fromNullable("mentor", "Is mentor", USERS.MENTORING))
  val SORTS = List(
    Table.Sort("contribution",
      Field.Order("u.mentoring IS NULL"),
      Field.Order("-(COALESCE(COUNT(DISTINCT p.id), 0) + COALESCE(COUNT(DISTINCT ep.id), 0))"),
      Field.Order("-COALESCE(COUNT(DISTINCT t.id), 0)"),
      Field.Order("-MAX(p.created_at)"),
      Field.Order("u.created_at")),
    Table.Sort("name", Field.Order("LOWER(u.last_name)"), Field.Order("LOWER(u.first_name)")),
    Table.Sort("created", USERS.CREATED_AT.desc),
    Table.Sort("updated", USERS.UPDATED_AT.desc))
  private val USERS_FULL = USERS
    .join(GROUPS, _.LeftOuter).on(_.OWNERS cond fr0" LIKE CONCAT('%', u.id, '%')").dropFields(GROUPS.getFields)
    .join(TALKS, _.LeftOuter).on(_.SPEAKERS cond fr0" LIKE CONCAT('%', u.id, '%')").dropFields(TALKS.getFields)
    .join(PROPOSALS, _.LeftOuter).on(p => p.SPEAKERS.cond(fr0" LIKE CONCAT('%', u.id, '%')") and p.STATUS.is(Proposal.Status.Accepted)).dropFields(PROPOSALS.getFields)
    .join(EXTERNAL_PROPOSALS, _.LeftOuter).on(ep => ep.SPEAKERS.cond(fr0" LIKE CONCAT('%', u.id, '%')") and ep.STATUS.is(Proposal.Status.Accepted)).dropFields(EXTERNAL_PROPOSALS.getFields)
    .addFields(
      AggField("COALESCE(COUNT(DISTINCT g.id), 0)", "groupCount"),
      AggField("COALESCE(COUNT(DISTINCT t.id), 0)", "talkCount"),
      AggField("COALESCE(COUNT(DISTINCT p.id), 0) + COALESCE(COUNT(DISTINCT ep.id), 0)", "proposalCount"))
    .filters(FILTERS)
    .sorts(SORTS)
  val FILTERS_ADMIN = List(
    Table.Filter.Bool.fromNullable("email", "Has validated email", USERS.EMAIL_VALIDATED),
    Table.Filter.Bool.fromCount("orga", "Is orga", GROUPS.ID),
    Table.Filter.Bool.fromCount("member", "Is member", GROUP_MEMBERS.GROUP_ID),
    Table.Filter.Bool.fromCount("attendee", "Is attendee", EVENT_RSVPS.EVENT_ID),
    Table.Filter.Bool.fromCount("talks", "Has talk", TALKS.ID),
    Table.Filter.Bool.fromCount("proposals", "Has proposal", PROPOSALS.ID),
    Table.Filter.Bool.fromCount("ext-proposals", "Has ext proposal", EXTERNAL_PROPOSALS.ID),
    Table.Filter.Bool.fromCount("ext-events", "Has ext event", EXTERNAL_EVENTS.ID),
    Table.Filter.Bool.fromCount("ext-cfps", "Has ext cfp", EXTERNAL_CFPS.ID),
    Table.Filter.Bool.fromCount("requests", "Has requests", USER_REQUESTS.ID),
    new Table.Filter.Value("created", "Created", false, v => USERS.CREATED_AT.gt(Instant.parse(v))))
  val SORTS_ADMIN = List(
    Table.Sort("name", Field.Order("LOWER(u.last_name)"), Field.Order("LOWER(u.first_name)")),
    Table.Sort("created", USERS.CREATED_AT.desc))
  private val USERS_ADMIN = USERS
    .join(GROUPS, _.LeftOuter).on(_.OWNERS cond fr0" LIKE CONCAT('%', u.id, '%')").dropFields(GROUPS.getFields)
    .join(GROUP_MEMBERS, _.LeftOuter).on(_.USER_ID is USERS.ID).dropFields(GROUP_MEMBERS.getFields)
    .join(EVENT_RSVPS, _.LeftOuter).on(_.USER_ID is USERS.ID).dropFields(EVENT_RSVPS.getFields)
    .join(TALKS, _.LeftOuter).on(_.SPEAKERS cond fr0" LIKE CONCAT('%', u.id, '%')").dropFields(TALKS.getFields)
    .join(PROPOSALS, _.LeftOuter).on(_.SPEAKERS.cond(fr0" LIKE CONCAT('%', u.id, '%')")).dropFields(PROPOSALS.getFields)
    .join(EXTERNAL_PROPOSALS, _.LeftOuter).on(_.SPEAKERS.cond(fr0" LIKE CONCAT('%', u.id, '%')")).dropFields(EXTERNAL_PROPOSALS.getFields)
    .join(EXTERNAL_EVENTS, _.LeftOuter).on(_.CREATED_BY is USERS.ID).dropFields(EXTERNAL_EVENTS.getFields)
    .join(EXTERNAL_CFPS, _.LeftOuter).on(_.CREATED_BY is USERS.ID).dropFields(EXTERNAL_CFPS.getFields)
    .join(USER_REQUESTS, _.LeftOuter).on(_.CREATED_BY is USERS.ID).dropFields(USER_REQUESTS.getFields)
    .addFields(
      AggField("COALESCE(COUNT(DISTINCT g.id), 0)", "groupOrgaCount"),
      AggField("COALESCE(COUNT(DISTINCT gm.group_id), 0)", "groupMemberCount"),
      AggField("COALESCE(COUNT(DISTINCT er.event_id), 0)", "attendeeCount"),
      AggField("COALESCE(COUNT(DISTINCT t.id), 0)", "talkCount"),
      AggField("COALESCE(COUNT(DISTINCT p.id), 0)", "proposalCount"),
      AggField("COALESCE(COUNT(DISTINCT ep.id), 0)", "extProposalCount"),
      AggField("COALESCE(COUNT(DISTINCT ep.id), 0)", "extEventCount"),
      AggField("COALESCE(COUNT(DISTINCT ep.id), 0)", "extCfpCount"),
      AggField("COALESCE(COUNT(DISTINCT ur.id), 0)", "requestCount"))
    .filters(FILTERS_ADMIN)
    .sorts(SORTS_ADMIN)

  private[sql] def selectLogins(user: User.Id): Query.Select.All[User.Login] =
    LOGINS.select.where(_.USER_ID.is(user)).dropFields(LOGINS.USER_ID).all[User.Login]

  private[sql] def insertLoginRef(i: User.LoginRef): Query.Insert[LOGINS] =
    LOGINS.insert.values(i.login.providerId, i.login.providerKey, i.user)

  private[sql] def insertCredentials(i: User.Credentials): Query.Insert[CREDENTIALS] =
  // CREDENTIALS.insert.values(i.login.providerId, i.login.providerKey, i.pass.hasher, i.pass.password, i.pass.salt)
    CREDENTIALS.insert.values(fr0"${i.login.providerId}, ${i.login.providerKey}, ${i.pass.hasher}, ${i.pass.password}, ${i.pass.salt}")

  private[sql] def updateCredentials(login: User.Login)(pass: User.Password): Query.Update[CREDENTIALS] =
    CREDENTIALS.update.set(_.HASHER, pass.hasher).set(_.PASSWORD, pass.password).set(_.SALT, pass.salt)
      .where(cd => cd.PROVIDER_ID.is(login.providerId) and cd.PROVIDER_KEY.is(login.providerKey))

  private[sql] def deleteCredentials(login: User.Login): Query.Delete[CREDENTIALS] =
    CREDENTIALS.delete.where(cd => cd.PROVIDER_ID.is(login.providerId) and cd.PROVIDER_KEY.is(login.providerKey))

  private[sql] def deleteLogins(user: User.Id): Query.Delete[LOGINS] =
    LOGINS.delete.where(_.USER_ID is user)

  private[sql] def deleteUser(user: User.Id): Query.Delete[USERS] =
    USERS.delete.where(_.ID is user)

  private[sql] def selectCredentials(login: User.Login): Query.Select.Optional[User.Credentials] =
    CREDENTIALS.select.where(cd => cd.PROVIDER_ID.is(login.providerId) and cd.PROVIDER_KEY.is(login.providerKey)).option[User.Credentials]

  private[sql] def insert(e: User): Query.Insert[USERS] =
  // USERS.insert.values(e.id, e.slug, e.status, e.firstName, e.lastName, e.email, e.emailValidated, e.emailValidationBeforeLogin, e.avatar, e.title, e.bio, e.mentoring, e.company, e.location, e.phone, e.website,
  //   e.social.facebook, e.social.instagram, e.social.twitter, e.social.linkedIn, e.social.youtube, e.social.meetup, e.social.eventbrite, e.social.slack, e.social.discord, e.social.github,
  //   e.createdAt, e.updatedAt)
    USERS.insert.values(fr0"${e.id}, ${e.slug}, ${e.status}, ${e.firstName}, ${e.lastName}, ${e.email}, ${e.emailValidated}, ${e.emailValidationBeforeLogin}, ${e.avatar}, ${e.title}, ${e.bio}, ${e.mentoring}, ${e.company}, ${e.location}, ${e.phone}, ${e.website}, " ++
      fr0"${e.social.facebook}, ${e.social.instagram}, ${e.social.twitter}, ${e.social.linkedIn}, ${e.social.youtube}, ${e.social.meetup}, ${e.social.eventbrite}, ${e.social.slack}, ${e.social.discord}, ${e.social.github}, " ++
      fr0"${e.createdAt}, ${e.updatedAt}")

  private[sql] def update(user: User.Id)(d: User.Data, now: Instant): Query.Update[USERS] =
    USERS.update.set(_.SLUG, d.slug).set(_.STATUS, d.status).set(_.FIRST_NAME, d.firstName).set(_.LAST_NAME, d.lastName).set(_.EMAIL, d.email).set(_.AVATAR, d.avatar).set(_.TITLE, d.title).set(_.BIO, d.bio).set(_.MENTORING, d.mentoring).set(_.COMPANY, d.company).set(_.LOCATION, d.location).set(_.PHONE, d.phone).set(_.WEBSITE, d.website)
      .set(_.SOCIAL_FACEBOOK, d.social.facebook).set(_.SOCIAL_INSTAGRAM, d.social.instagram).set(_.SOCIAL_TWITTER, d.social.twitter).set(_.SOCIAL_LINKEDIN, d.social.linkedIn).set(_.SOCIAL_YOUTUBE, d.social.youtube).set(_.SOCIAL_MEETUP, d.social.meetup).set(_.SOCIAL_EVENTBRITE, d.social.eventbrite).set(_.SOCIAL_SLACK, d.social.slack).set(_.SOCIAL_DISCORD, d.social.discord).set(_.SOCIAL_GITHUB, d.social.github)
      .set(_.UPDATED_AT, now).where(_.ID is user)

  private[sql] def validateAccount(email: EmailAddress, now: Instant): Query.Update[USERS] =
    USERS.update.set(_.EMAIL_VALIDATED, now).where(_.EMAIL is email)

  private[sql] def selectOne(login: User.Login): Query.Select.Optional[User] =
    USERS_WITH_LOGINS.select.where(LOGINS.PROVIDER_ID.is(login.providerId) and LOGINS.PROVIDER_KEY.is(login.providerKey)).option[User]

  private[sql] def selectOne(email: EmailAddress): Query.Select.Optional[User] =
    USERS.select.where(_.EMAIL is email).option[User]

  private[sql] def selectOne(slug: User.Slug): Query.Select.Optional[User] =
    USERS.select.where(_.SLUG is slug).option[User]

  private[sql] def selectOne(id: User.Id): Query.Select.Optional[User] =
    USERS.select.where(_.ID is id).option[User]

  private[sql] def selectOnePublic(slug: User.Slug)(implicit ctx: UserAwareCtx): Query.Select.Optional[User.Full] =
    USERS_FULL.select.where(USERS.SLUG.is(slug) and (USERS.STATUS.is(User.Status.Public) or USERS.ID.is(ctx.user.map(_.id).getOrElse(User.Id.empty))).par).option[User.Full](limit = true)

  // should replace def selectPage(ids: NonEmptyList[User.Id], params: Page.Params) when split or array works...
  /* private[sql] def selectPage(group: Group.Id, params: Page.Params)(implicit ctx: BasicCtx): Query.SelectPage[User] = {
    val speakerIds = fr0"SELECT p.speakers FROM ${Tables.proposals.name} INNER JOIN ${Tables.cfps.name} ON c.id=p.cfp_id WHERE c.group_id=$group"
    table.selectPage[User](params, adapt(ctx)).where(fr0"u.id IN (" ++ speakerIds ++ fr0")")
  } */

  private[sql] def selectAllPublicSlugs()(implicit ctx: UserAwareCtx): Query.Select.All[(User.Id, User.Slug)] =
    USERS.select.withFields(_.ID, _.SLUG).where(_.STATUS is User.Status.Public).all[(User.Id, User.Slug)]

  private[sql] def selectPagePublic(params: Page.Params)(implicit ctx: UserAwareCtx): Query.Select.Paginated[User.Full] =
    USERS_FULL.select.where(USERS.STATUS is User.Status.Public).page[User.Full](params.toSql, ctx.toSql)

  private[sql] def selectPage(ids: NonEmptyList[User.Id], params: Page.Params)(implicit ctx: BasicCtx): Query.Select.Paginated[User.Full] =
    USERS_FULL.select.where(USERS.ID in ids).page[User.Full](params.toSql, ctx.toSql)

  private[sql] def selectPageAdmin(params: Page.Params)(implicit ctx: AdminCtx): Query.Select.Paginated[User.Admin] =
    USERS_ADMIN.select.page[User.Admin](params.toSql, ctx.toSql)

  private[sql] def selectAll(ids: NonEmptyList[User.Id]): Query.Select.All[User] =
    USERS.select.where(_.ID in ids).all[User]
}
