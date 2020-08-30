package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import gospeak.core.domain.utils.{BasicCtx, OrgaCtx, UserAwareCtx, UserCtx}
import gospeak.core.domain.{Group, User}
import gospeak.core.services.storage.UserRepo
import gospeak.infra.services.storage.sql.UserRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Done, EmailAddress, Page}
import gospeak.libs.sql.doobie.{DbCtx, Field, Query, Table}

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
    .joinOpt(Tables.proposals, fr0"p.speakers LIKE CONCAT('%', u.id, '%') AND p.status='Accepted'").get.dropFields(_.prefix == Tables.proposals.prefix)
    .joinOpt(Tables.externalProposals, fr0"ep.speakers LIKE CONCAT('%', u.id, '%') AND ep.status='Accepted'").get.dropFields(_.prefix == Tables.externalProposals.prefix)
    .aggregate("COALESCE(COUNT(DISTINCT g.id), 0)", "groupCount")
    .aggregate("COALESCE(COUNT(DISTINCT t.id), 0)", "talkCount")
    .aggregate("COALESCE(COUNT(DISTINCT p.id), 0) + COALESCE(COUNT(DISTINCT ep.id), 0)", "proposalCount")
    .copy(filters = List(
      Table.Filter.Bool.fromNullable("mentor", "Is mentor", "u.mentoring")))
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

  private[sql] def insert(e: User): Query.Insert[User] = {
    val values = fr0"${e.id}, ${e.slug}, ${e.status}, ${e.firstName}, ${e.lastName}, ${e.email}, ${e.emailValidated}, ${e.emailValidationBeforeLogin}, ${e.avatar.url}, ${e.title}, ${e.bio}, ${e.mentoring}, ${e.company}, ${e.location}, ${e.phone}, ${e.website}, " ++
      fr0"${e.social.facebook}, ${e.social.instagram}, ${e.social.twitter}, ${e.social.linkedIn}, ${e.social.youtube}, ${e.social.meetup}, ${e.social.eventbrite}, ${e.social.slack}, ${e.social.discord}, ${e.social.github}, " ++
      fr0"${e.createdAt}, ${e.updatedAt}"
    table.insert(e, _ => values)
  }

  private[sql] def update(user: User.Id)(d: User.Data, now: Instant): Query.Update = {
    val fields = fr0"slug=${d.slug}, status=${d.status}, first_name=${d.firstName}, last_name=${d.lastName}, email=${d.email}, avatar=${d.avatar.url}, title=${d.title}, bio=${d.bio}, mentoring=${d.mentoring}, company=${d.company}, location=${d.location}, phone=${d.phone}, website=${d.website}, " ++
      fr0"social_facebook=${d.social.facebook}, social_instagram=${d.social.instagram}, social_twitter=${d.social.twitter}, social_linkedIn=${d.social.linkedIn}, social_youtube=${d.social.youtube}, social_meetup=${d.social.meetup}, social_eventbrite=${d.social.eventbrite}, social_slack=${d.social.slack}, social_discord=${d.social.discord}, social_github=${d.social.github}, " ++
      fr0"updated_at=$now"
    table.update(fields).where(fr0"u.id=$user")
  }

  private[sql] def validateAccount(email: EmailAddress, now: Instant): Query.Update =
    table.update(fr0"email_validated=$now").where(fr0"u.email=$email")

  private[sql] def insertLoginRef(i: User.LoginRef): Query.Insert[User.LoginRef] =
    loginsTable.insert[User.LoginRef](i, _ => fr0"${i.login.providerId}, ${i.login.providerKey}, ${i.user}")

  private[sql] def insertCredentials(i: User.Credentials): Query.Insert[User.Credentials] =
    credentialsTable.insert[User.Credentials](i, _ => fr0"${i.login.providerId}, ${i.login.providerKey}, ${i.pass.hasher}, ${i.pass.password}, ${i.pass.salt}")

  private[sql] def updateCredentials(login: User.Login)(pass: User.Password): Query.Update = {
    val fields = fr0"hasher=${pass.hasher}, password=${pass.password}, salt=${pass.salt}"
    val where = fr0"cd.provider_id=${login.providerId} AND cd.provider_key=${login.providerKey}"
    credentialsTable.update(fields).where(where)
  }

  private[sql] def deleteCredentials(login: User.Login): Query.Delete =
    credentialsTable.delete.where(fr0"cd.provider_id=${login.providerId} AND cd.provider_key=${login.providerKey}")

  private[sql] def selectCredentials(login: User.Login): Query.Select[User.Credentials] =
    credentialsTable.select[User.Credentials].where(fr0"cd.provider_id=${login.providerId} AND cd.provider_key=${login.providerKey}")

  private[sql] def selectOne(login: User.Login): Query.Select[User] =
    tableWithLogin.select[User].fields(table.fields).where(fr0"lg.provider_id=${login.providerId} AND lg.provider_key=${login.providerKey}")

  private[sql] def selectOne(email: EmailAddress): Query.Select[User] =
    table.select[User].where(fr0"u.email=$email")

  private[sql] def selectOne(slug: User.Slug): Query.Select[User] =
    table.select[User].where(fr0"u.slug=$slug")

  private[sql] def selectOne(id: User.Id): Query.Select[User] =
    table.select[User].where(fr0"u.id=$id")

  private[sql] def selectOnePublic(slug: User.Slug)(implicit ctx: UserAwareCtx): Query.Select[User.Full] =
    tableFull.select[User.Full].where(fr0"u.slug=$slug AND (u.status=${User.Status.Public: User.Status} OR u.id=${ctx.user.map(_.id.value).getOrElse("")})").one

  // should replace def selectPage(ids: NonEmptyList[User.Id], params: Page.Params) when split or array works...
  /* private[sql] def selectPage(group: Group.Id, params: Page.Params)(implicit ctx: BasicCtx): Query.SelectPage[User] = {
    val speakerIds = fr0"SELECT p.speakers FROM ${Tables.proposals.name} INNER JOIN ${Tables.cfps.name} ON c.id=p.cfp_id WHERE c.group_id=$group"
    table.selectPage[User](params, adapt(ctx)).where(fr0"u.id IN (" ++ speakerIds ++ fr0")")
  } */

  private[sql] def selectAllPublicSlugs()(implicit ctx: UserAwareCtx): Query.Select[(User.Id, User.Slug)] =
    table.select[(User.Id, User.Slug)].fields(Field("id", "u"), Field("slug", "u")).where(fr0"u.status=${User.Status.Public: User.Status}")

  private[sql] def selectPagePublic(params: Page.Params)(implicit ctx: UserAwareCtx): Query.SelectPage[User.Full] = {
    val public: User.Status = User.Status.Public
    tableFull.selectPage[User.Full](params, adapt(ctx)).where(fr0"u.status=$public")
  }

  private[sql] def selectPage(ids: NonEmptyList[User.Id], params: Page.Params)(implicit ctx: BasicCtx): Query.SelectPage[User.Full] =
    tableFull.selectPage[User.Full](params, adapt(ctx)).where(Fragments.in(fr"u.id", ids))

  private[sql] def selectAll(ids: NonEmptyList[User.Id]): Query.Select[User] =
    table.select[User].where(Fragments.in(fr"u.id", ids))

  private def adapt(ctx: BasicCtx): DbCtx = DbCtx(ctx.now)
}
