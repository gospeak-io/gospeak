package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import gospeak.core.domain.utils.{BasicCtx, OrgaCtx, UserAwareCtx, UserCtx}
import gospeak.core.domain.{Group, Proposal, User}
import gospeak.core.services.storage.UserRepo
import gospeak.infra.services.storage.sql.UserRepoSql._
import gospeak.infra.services.storage.sql.database.Tables._
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Done, EmailAddress, Page}
import gospeak.libs.sql.doobie.{DbCtx, Field, Query, Table}
import gospeak.libs.sql.dsl

import scala.language.postfixOps

class UserRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with UserRepo {
  override def create(data: User.Data, now: Instant, emailValidated: Option[Instant]): IO[User] =
    insert(User(data, now, emailValidated)).run(xa)

  override def edit(user: User.Id)(data: User.Data, now: Instant): IO[User] =
    update(user)(data, now).run(xa).flatMap(_ => find(user).flatMap(_.toIO(new IllegalArgumentException(s"User $user does not exists"))))

  override def edit(data: User.Data)(implicit ctx: UserCtx): IO[User] = edit(ctx.user.id)(data, ctx.now)

  override def editStatus(status: User.Status)(implicit ctx: UserCtx): IO[Done] =
    selectOne(ctx.user.id).runOption(xa).flatMap {
      case Some(userElt) => update(ctx.user.id)(userElt.data.copy(status = status), ctx.now).run(xa)
      case None => IO.raiseError(new IllegalArgumentException(s"User ${ctx.user.id} does not exists"))
    }

  override def createLoginRef(login: User.Login, user: User.Id): IO[Done] = insertLoginRef(User.LoginRef(login, user)).run(xa).map(_ => Done)

  override def createCredentials(credentials: User.Credentials): IO[User.Credentials] = insertCredentials(credentials).run(xa)

  override def editCredentials(login: User.Login)(pass: User.Password): IO[Done] = updateCredentials(login)(pass).run(xa)

  override def removeCredentials(login: User.Login): IO[Done] = deleteCredentials(login).run(xa)

  override def findCredentials(login: User.Login): IO[Option[User.Credentials]] = selectCredentials(login).runOption(xa)

  override def find(login: User.Login): IO[Option[User]] = selectOne(login).runOption(xa)

  override def find(credentials: User.Credentials): IO[Option[User]] = selectOne(credentials.login).runOption(xa)

  override def find(email: EmailAddress): IO[Option[User]] = selectOne(email).runOption(xa)

  override def find(slug: User.Slug): IO[Option[User]] = selectOne(slug).runOption(xa)

  override def find(id: User.Id): IO[Option[User]] = selectOne(id).runOption(xa)

  override def findPublic(slug: User.Slug)(implicit ctx: UserAwareCtx): IO[Option[User.Full]] = selectOnePublic(slug).runOption(xa)

  // FIXME should be done in only one query: joining on speakers array or splitting speakers string
  override def speakers(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[User.Full]] = {
    val speakerIdsQuery = proposalsWithCfps.select[NonEmptyList[User.Id]].fields(Field("speakers", "p")).where(fr0"c.group_id=${ctx.group.id}")
    for {
      speakerIds <- speakerIdsQuery.runList(xa).map(_.flatMap(_.toList).distinct)
      res <- speakerIds.toNel.map(ids => selectPage(ids, params).run(xa)).getOrElse(IO.pure(Page.empty[User.Full]))
    } yield res
  }

  override def speakersPublic(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[User.Full]] = {
    val speakerIdsQuery = proposalsWithCfpEvents.select[NonEmptyList[User.Id]].fields(Field("speakers", "p")).where(fr0"c.group_id=$group AND e.published IS NOT NULL")
    for {
      speakerIds <- speakerIdsQuery.runList(xa).map(_.flatMap(_.toList).distinct)
      res <- speakerIds.toNel.map(ids => selectPage(ids, params).run(xa)).getOrElse(IO.pure(Page.empty[User.Full]))
    } yield res
  }

  override def speakerCountPublic(group: Group.Id): IO[Long] = {
    proposalsWithCfpEvents.select[NonEmptyList[User.Id]].fields(Field("speakers", "p")).where(fr0"c.group_id=$group AND e.published IS NOT NULL")
      .runList(xa).map(_.flatMap(_.toList).distinct.length.toLong)
  }

  override def listAllPublicSlugs()(implicit ctx: UserAwareCtx): IO[List[(User.Id, User.Slug)]] = selectAllPublicSlugs().runList(xa)

  override def listPublic(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[User.Full]] = selectPagePublic(params).run(xa)

  override def list(ids: List[User.Id]): IO[List[User]] = runNel(selectAll, ids)
}

object UserRepoSql {
  private val _ = userIdMeta // for intellij not remove DoobieMappings import
  private val table = Tables.users
  private val credentialsTable = Tables.credentials
  private val loginsTable = Tables.logins
  private val tableWithLogin = table.join(loginsTable, _.id -> _.user_id).get
  private val proposalsWithCfps = Tables.proposals.join(Tables.cfps, _.cfp_id -> _.id).get
  private val proposalsWithCfpEvents = proposalsWithCfps.join(Tables.events, _.event_id -> _.id).get
  val tableFull: Table = table
    .joinOpt(Tables.groups, fr0"g.owners LIKE CONCAT('%', u.id, '%')").get.dropFields(_.prefix == Tables.groups.prefix)
    .joinOpt(Tables.talks, fr0"t.speakers LIKE CONCAT('%', u.id, '%')").get.dropFields(_.prefix == Tables.talks.prefix)
    .joinOpt(Tables.proposals, fr0"p.speakers LIKE CONCAT('%', u.id, '%') AND p.status=${Proposal.Status.Accepted: Proposal.Status}").get.dropFields(_.prefix == Tables.proposals.prefix)
    .joinOpt(Tables.externalProposals, fr0"ep.speakers LIKE CONCAT('%', u.id, '%') AND ep.status=${Proposal.Status.Accepted: Proposal.Status}").get.dropFields(_.prefix == Tables.externalProposals.prefix)
    .aggregate("COALESCE(COUNT(DISTINCT g.id), 0)", "groupCount")
    .aggregate("COALESCE(COUNT(DISTINCT t.id), 0)", "talkCount")
    .aggregate("COALESCE(COUNT(DISTINCT p.id), 0) + COALESCE(COUNT(DISTINCT ep.id), 0)", "proposalCount")
    .filters(Table.Filter.Bool.fromNullable("mentor", "Is mentor", "u.mentoring"))
    .setSorts(
      Table.Sort("contribution",
        Field("mentoring IS NULL", "u"),
        Field("-(COALESCE(COUNT(DISTINCT p.id), 0) + COALESCE(COUNT(DISTINCT ep.id), 0))", ""),
        Field("-COALESCE(COUNT(DISTINCT t.id), 0)", ""),
        Field("-MAX(p.created_at)", ""),
        Field("created_at", "u")),
      Table.Sort("name", Field("LOWER(u.last_name)", ""), Field("LOWER(u.first_name)", "")),
      Table.Sort("created", Field("-created_at", "u")),
      Table.Sort("updated", Field("-updated_at", "u")))
  private val USERS_WITH_LOGINS = USERS.join(LOGINS).on(_.ID is _.USER_ID).dropFields(LOGINS.getFields)
  private val USERS_FULL = USERS
    .join(GROUPS, _.LeftOuter).on(_.OWNERS cond fr0" LIKE CONCAT('%', u.id, '%')").dropFields(GROUPS.getFields)
    .join(TALKS, _.LeftOuter).on(_.SPEAKERS cond fr0" LIKE CONCAT('%', u.id, '%')").dropFields(TALKS.getFields)
    .join(PROPOSALS, _.LeftOuter).on(p => p.SPEAKERS.cond(fr0" LIKE CONCAT('%', u.id, '%')") and p.STATUS.is(Proposal.Status.Accepted)).dropFields(PROPOSALS.getFields)
    .join(EXTERNAL_PROPOSALS, _.LeftOuter).on(ep => ep.SPEAKERS.cond(fr0" LIKE CONCAT('%', u.id, '%')") and ep.STATUS.is(Proposal.Status.Accepted)).dropFields(EXTERNAL_PROPOSALS.getFields)
    .addFields(
      dsl.AggField("COALESCE(COUNT(DISTINCT g.id), 0)", "groupCount"),
      dsl.AggField("COALESCE(COUNT(DISTINCT t.id), 0)", "talkCount"),
      dsl.AggField("COALESCE(COUNT(DISTINCT p.id), 0) + COALESCE(COUNT(DISTINCT ep.id), 0)", "proposalCount"))
    .filters(dsl.Table.Filter.Bool.fromNullable("mentor", "Is mentor", USERS.MENTORING))
    .sorts(
      dsl.Table.Sort("contribution",
        dsl.Field.Order("u.mentoring IS NULL"),
        dsl.Field.Order("-(COALESCE(COUNT(DISTINCT p.id), 0) + COALESCE(COUNT(DISTINCT ep.id), 0))"),
        dsl.Field.Order("-COALESCE(COUNT(DISTINCT t.id), 0)"),
        dsl.Field.Order("-MAX(p.created_at)"),
        dsl.Field.Order("u.created_at")),
      dsl.Table.Sort("name", dsl.Field.Order("LOWER(u.last_name)"), dsl.Field.Order("LOWER(u.first_name)")),
      dsl.Table.Sort("created", USERS.CREATED_AT.desc),
      dsl.Table.Sort("updated", USERS.UPDATED_AT.desc))

  private[sql] def insertLoginRef(i: User.LoginRef): Query.Insert[User.LoginRef] = {
    val q1 = loginsTable.insert[User.LoginRef](i, _ => fr0"${i.login.providerId}, ${i.login.providerKey}, ${i.user}")
    val q2 = LOGINS.insert.values(i.login.providerId, i.login.providerKey, i.user)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def insertCredentials(i: User.Credentials): Query.Insert[User.Credentials] = {
    val q1 = credentialsTable.insert[User.Credentials](i, _ => fr0"${i.login.providerId}, ${i.login.providerKey}, ${i.pass.hasher}, ${i.pass.password}, ${i.pass.salt}")
    val q2 = CREDENTIALS.insert.values(i.login.providerId, i.login.providerKey, i.pass.hasher, i.pass.password, i.pass.salt)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def updateCredentials(login: User.Login)(pass: User.Password): Query.Update = {
    val q1 = credentialsTable.update(fr0"hasher=${pass.hasher}, password=${pass.password}, salt=${pass.salt}")
      .where(fr0"cd.provider_id=${login.providerId} AND cd.provider_key=${login.providerKey}")
    val q2 = CREDENTIALS.update.set(_.HASHER, pass.hasher).set(_.PASSWORD, pass.password).set(_.SALT, pass.salt)
      .where(cd => cd.PROVIDER_ID.is(login.providerId) and cd.PROVIDER_KEY.is(login.providerKey))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def deleteCredentials(login: User.Login): Query.Delete = {
    val q1 = credentialsTable.delete.where(fr0"cd.provider_id=${login.providerId} AND cd.provider_key=${login.providerKey}")
    val q2 = CREDENTIALS.delete.where(cd => cd.PROVIDER_ID.is(login.providerId) and cd.PROVIDER_KEY.is(login.providerKey))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectCredentials(login: User.Login): Query.Select[User.Credentials] = {
    val q1 = credentialsTable.select[User.Credentials].where(fr0"cd.provider_id=${login.providerId} AND cd.provider_key=${login.providerKey}")
    val q2 = CREDENTIALS.select.where(cd => cd.PROVIDER_ID.is(login.providerId) and cd.PROVIDER_KEY.is(login.providerKey)).option[User.Credentials]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def insert(e: User): Query.Insert[User] = {
    val values = fr0"${e.id}, ${e.slug}, ${e.status}, ${e.firstName}, ${e.lastName}, ${e.email}, ${e.emailValidated}, ${e.emailValidationBeforeLogin}, ${e.avatar}, ${e.title}, ${e.bio}, ${e.mentoring}, ${e.company}, ${e.location}, ${e.phone}, ${e.website}, " ++
      fr0"${e.social.facebook}, ${e.social.instagram}, ${e.social.twitter}, ${e.social.linkedIn}, ${e.social.youtube}, ${e.social.meetup}, ${e.social.eventbrite}, ${e.social.slack}, ${e.social.discord}, ${e.social.github}, " ++
      fr0"${e.createdAt}, ${e.updatedAt}"
    val q1 = table.insert[User](e, _ => values)
    val q2 = USERS.insert.values(e.id, e.slug, e.status, e.firstName, e.lastName, e.email, e.emailValidated, e.emailValidationBeforeLogin, e.avatar, e.title, e.bio, e.mentoring, e.company, e.location, e.phone, e.website,
      e.social.facebook, e.social.instagram, e.social.twitter, e.social.linkedIn, e.social.youtube, e.social.meetup, e.social.eventbrite, e.social.slack, e.social.discord, e.social.github,
      e.createdAt, e.updatedAt)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def update(user: User.Id)(d: User.Data, now: Instant): Query.Update = {
    val fields = fr0"slug=${d.slug}, status=${d.status}, first_name=${d.firstName}, last_name=${d.lastName}, email=${d.email}, avatar=${d.avatar}, title=${d.title}, bio=${d.bio}, mentoring=${d.mentoring}, company=${d.company}, location=${d.location}, phone=${d.phone}, website=${d.website}, " ++
      fr0"social_facebook=${d.social.facebook}, social_instagram=${d.social.instagram}, social_twitter=${d.social.twitter}, social_linkedIn=${d.social.linkedIn}, social_youtube=${d.social.youtube}, social_meetup=${d.social.meetup}, social_eventbrite=${d.social.eventbrite}, social_slack=${d.social.slack}, social_discord=${d.social.discord}, social_github=${d.social.github}, " ++
      fr0"updated_at=$now"
    val q1 = table.update(fields).where(fr0"u.id=$user")
    val q2 = USERS.update.set(_.SLUG, d.slug).set(_.STATUS, d.status).set(_.FIRST_NAME, d.firstName).set(_.LAST_NAME, d.lastName).set(_.EMAIL, d.email).set(_.AVATAR, d.avatar).set(_.TITLE, d.title).set(_.BIO, d.bio).set(_.MENTORING, d.mentoring).set(_.COMPANY, d.company).set(_.LOCATION, d.location).set(_.PHONE, d.phone).set(_.WEBSITE, d.website)
      .set(_.SOCIAL_FACEBOOK, d.social.facebook).set(_.SOCIAL_INSTAGRAM, d.social.instagram).set(_.SOCIAL_TWITTER, d.social.twitter).set(_.SOCIAL_LINKEDIN, d.social.linkedIn).set(_.SOCIAL_YOUTUBE, d.social.youtube).set(_.SOCIAL_MEETUP, d.social.meetup).set(_.SOCIAL_EVENTBRITE, d.social.eventbrite).set(_.SOCIAL_SLACK, d.social.slack).set(_.SOCIAL_DISCORD, d.social.discord).set(_.SOCIAL_GITHUB, d.social.github)
      .set(_.UPDATED_AT, now).where(_.ID is user)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def validateAccount(email: EmailAddress, now: Instant): Query.Update = {
    val q1 = table.update(fr0"email_validated=$now").where(fr0"u.email=$email")
    val q2 = USERS.update.setOpt(_.EMAIL_VALIDATED, now).where(_.EMAIL is email)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOne(login: User.Login): Query.Select[User] = {
    val q1 = tableWithLogin.select[User].fields(table.fields).where(fr0"lg.provider_id=${login.providerId} AND lg.provider_key=${login.providerKey}")
    val q2 = USERS_WITH_LOGINS.select.where(LOGINS.PROVIDER_ID.is(login.providerId) and LOGINS.PROVIDER_KEY.is(login.providerKey)).option[User]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOne(email: EmailAddress): Query.Select[User] = {
    val q1 = table.select[User].where(fr0"u.email=$email")
    val q2 = USERS.select.where(_.EMAIL is email).option[User]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOne(slug: User.Slug): Query.Select[User] = {
    val q1 = table.select[User].where(fr0"u.slug=$slug")
    val q2 = USERS.select.where(_.SLUG is slug).option[User]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOne(id: User.Id): Query.Select[User] = {
    val q1 = table.select[User].where(fr0"u.id=$id")
    val q2 = USERS.select.where(_.ID is id).option[User]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOnePublic(slug: User.Slug)(implicit ctx: UserAwareCtx): Query.Select[User.Full] = {
    val q1 = tableFull.select[User.Full].where(fr0"u.slug=$slug AND (u.status=${User.Status.Public: User.Status} OR u.id=${ctx.user.map(_.id).getOrElse(User.Id.empty).value})").one
    val q2 = USERS_FULL.select.where(USERS.SLUG.is(slug) and (USERS.STATUS.is(User.Status.Public) or USERS.ID.is(ctx.user.map(_.id).getOrElse(User.Id.empty))).par).option[User.Full](limit = true)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  // should replace def selectPage(ids: NonEmptyList[User.Id], params: Page.Params) when split or array works...
  /* private[sql] def selectPage(group: Group.Id, params: Page.Params)(implicit ctx: BasicCtx): Query.SelectPage[User] = {
    val speakerIds = fr0"SELECT p.speakers FROM ${Tables.proposals.name} INNER JOIN ${Tables.cfps.name} ON c.id=p.cfp_id WHERE c.group_id=$group"
    table.selectPage[User](params, adapt(ctx)).where(fr0"u.id IN (" ++ speakerIds ++ fr0")")
  } */

  private[sql] def selectAllPublicSlugs()(implicit ctx: UserAwareCtx): Query.Select[(User.Id, User.Slug)] = {
    val q1 = table.select[(User.Id, User.Slug)].fields(Field("id", "u"), Field("slug", "u")).where(fr0"u.status=${User.Status.Public: User.Status}")
    val q2 = USERS.select.withFields(_.ID, _.SLUG).where(_.STATUS is User.Status.Public).all[(User.Id, User.Slug)]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPagePublic(params: Page.Params)(implicit ctx: UserAwareCtx): Query.SelectPage[User.Full] = {
    val q1 = tableFull.selectPage[User.Full](params, adapt(ctx)).where(fr0"u.status=${User.Status.Public: User.Status}")
    val q2 = USERS_FULL.select.where(USERS.STATUS is User.Status.Public).page[User.Full](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPage(ids: NonEmptyList[User.Id], params: Page.Params)(implicit ctx: BasicCtx): Query.SelectPage[User.Full] = {
    val q1 = tableFull.selectPage[User.Full](params, adapt(ctx)).where(Fragments.in(fr"u.id", ids))
    val q2 = USERS_FULL.select.where(USERS.ID in ids).page[User.Full](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAll(ids: NonEmptyList[User.Id]): Query.Select[User] = {
    val q1 = table.select[User].where(Fragments.in(fr"u.id", ids))
    val q2 = USERS.select.where(_.ID in ids).all[User]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private def adapt(ctx: BasicCtx): DbCtx = DbCtx(ctx.now)
}
