package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services.storage.UserRepo
import fr.gospeak.infra.services.storage.sql.UserRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.{Delete, Field, Insert, Select, SelectPage, Update}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Done, EmailAddress, Page}

class UserRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with UserRepo {
  override def create(data: User.Data, now: Instant): IO[User] =
    insert(User(data, User.emptyProfile, now)).run(xa)

  override def edit(user: User, now: Instant): IO[User] =
    update(user.copy(updatedAt = now)).run(xa).map(_ => user.copy(updatedAt = now))

  override def edit(user: User, editable: User.EditableFields, now: Instant): IO[User] =
    update(user.copy(firstName = editable.firstName, lastName = editable.lastName, email = editable.email, profile = editable.profile, updatedAt = now)).run(xa).map(_ => user.copy(updatedAt = now))

  override def editStatus(user: User.Id)(status: User.Profile.Status): IO[Done] =
    selectOne(user).runOption(xa).flatMap {
      case Some(userElt) => update(userElt.copy(profile = userElt.profile.copy(status = status))).run(xa)
      case None => IO.raiseError(new IllegalArgumentException(s"User $user does not exists"))
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

  override def findPublic(slug: User.Slug): IO[Option[User]] = selectOnePublic(slug).runOption(xa)

  // FIXME should be done in only one query: joining on speakers array or splitting speakers string
  override def speakers(group: Group.Id, params: Page.Params): IO[Page[User]] = {
    val speakerIdsQuery = proposalsWithCfps.select[NonEmptyList[User.Id]](Seq(Field("speakers", "p")), fr0"WHERE c.group_id=$group")
    for {
      speakerIds <- speakerIdsQuery.runList(xa).map(_.flatMap(_.toList).distinct)
      res <- NonEmptyList.fromList(speakerIds).map(ids => selectPage(ids, params).run(xa)).getOrElse(IO.pure(Page.empty[User]))
    } yield res
  }

  override def listPublic(params: Page.Params): IO[Page[User]] = selectPagePublic(params).run(xa)

  override def list(ids: Seq[User.Id]): IO[Seq[User]] = runNel(selectAll, ids)
}

object UserRepoSql {
  private val _ = userIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.users
  private val credentialsTable = Tables.credentials
  private val loginsTable = Tables.logins
  private val tableWithLogin = table.join(loginsTable, _.id -> _.user_id).get
  private val proposalsWithCfps = Tables.proposals.join(Tables.cfps, _.cfp_id -> _.id).get

  private[sql] def insert(e: User): Insert[User] = {
    val values = fr0"${e.id}, ${e.slug}, ${e.firstName}, ${e.lastName}, ${e.email}, ${e.emailValidated}, ${e.avatar.url}, ${e.avatar.source}, ${e.profile.status}, ${e.profile.bio}, ${e.profile.company}, ${e.profile.location}, ${e.profile.twitter}, ${e.profile.linkedin}, ${e.profile.phone}, ${e.profile.website}, ${e.createdAt}, ${e.updatedAt}"
    table.insert(e, _ => values)
  }

  private[sql] def update(elt: User): Update = {
    val fields = fr0"slug=${elt.slug}, first_name=${elt.firstName}, last_name=${elt.lastName}, email=${elt.email}, status=${elt.profile.status}, bio=${elt.profile.bio}, company=${elt.profile.company}, location=${elt.profile.location}, twitter=${elt.profile.twitter}, linkedin=${elt.profile.linkedin}, phone=${elt.profile.phone}, website=${elt.profile.website}, updated_at=${elt.updatedAt}"
    table.update(fields, fr0"WHERE u.id=${elt.id}")
  }

  private[sql] def validateAccount(email: EmailAddress, now: Instant): Update =
    table.update(fr0"email_validated=$now", fr0"WHERE u.email=$email")

  private[sql] def insertLoginRef(i: User.LoginRef): Insert[User.LoginRef] =
    loginsTable.insert[User.LoginRef](i, _ => fr0"${i.login.providerId}, ${i.login.providerKey}, ${i.user}")

  private[sql] def insertCredentials(i: User.Credentials): Insert[User.Credentials] =
    credentialsTable.insert[User.Credentials](i, _ => fr0"${i.login.providerId}, ${i.login.providerKey}, ${i.pass.hasher}, ${i.pass.password}, ${i.pass.salt}")

  private[sql] def updateCredentials(login: User.Login)(pass: User.Password): Update = {
    val fields = fr0"hasher=${pass.hasher}, password=${pass.password}, salt=${pass.salt}"
    val where = fr0"WHERE cd.provider_id=${login.providerId} AND cd.provider_key=${login.providerKey}"
    credentialsTable.update(fields, where)
  }

  private[sql] def deleteCredentials(login: User.Login): Delete =
    credentialsTable.delete(fr0"WHERE cd.provider_id=${login.providerId} AND cd.provider_key=${login.providerKey}")

  private[sql] def selectCredentials(login: User.Login): Select[User.Credentials] =
    credentialsTable.select[User.Credentials](fr0"WHERE cd.provider_id=${login.providerId} AND cd.provider_key=${login.providerKey}")

  private[sql] def selectOne(login: User.Login): Select[User] =
    tableWithLogin.select[User](table.fields, fr0"WHERE lg.provider_id=${login.providerId} AND lg.provider_key=${login.providerKey}")

  private[sql] def selectOne(email: EmailAddress): Select[User] =
    table.select[User](fr0"WHERE u.email=$email")

  private[sql] def selectOne(slug: User.Slug): Select[User] =
    table.select[User](fr0"WHERE u.slug=$slug")

  private[sql] def selectOne(id: User.Id): Select[User] =
    table.select[User](fr0"WHERE u.id=$id")

  private[sql] def selectOnePublic(slug: User.Slug): Select[User] = {
    val public: User.Profile.Status = User.Profile.Status.Public
    table.select[User](fr0"WHERE u.status=$public AND u.slug=$slug")
  }

  // should replace def selectPage(ids: NonEmptyList[User.Id], params: Page.Params) when split or array works...
  /* private[sql] def selectPage(group: Group.Id, params: Page.Params): SelectPage[User] = {
    val speakerIds = fr0"SELECT p.speakers FROM ${Tables.proposals.name} INNER JOIN ${Tables.cfps.name} ON c.id=p.cfp_id WHERE c.group_id=$group"
    table.selectPage[User](params, fr0"WHERE u.id IN (" ++ speakerIds ++ fr0")")
  } */

  private[sql] def selectPagePublic(params: Page.Params): SelectPage[User] = {
    val public: User.Profile.Status = User.Profile.Status.Public
    table.selectPage[User](params, fr0"WHERE u.status=$public")
  }

  private[sql] def selectPage(ids: NonEmptyList[User.Id], params: Page.Params): SelectPage[User] =
    table.selectPage[User](params, fr0"WHERE " ++ Fragments.in(fr"u.id", ids))

  private[sql] def selectAll(ids: NonEmptyList[User.Id]): Select[User] =
    table.select[User](fr0"WHERE " ++ Fragments.in(fr"u.id", ids))
}
