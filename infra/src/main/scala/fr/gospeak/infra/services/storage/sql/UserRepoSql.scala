package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services.UserRepo
import fr.gospeak.infra.services.storage.sql.UserRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.libs.scalautils.domain.{Done, EmailAddress, Page}

class UserRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with UserRepo {
  override def create(data: User.Data, now: Instant): IO[User] =
    run(insert, User(data, now))

  override def edit(user: User, now: Instant): IO[User] =
    run(update(user.copy(updated = now))).map(_ => user.copy(updated = now))

  override def createLoginRef(login: User.Login, user: User.Id): IO[Done] =
    run(insertLoginRef, User.LoginRef(login, user)).map(_ => Done)

  override def createCredentials(credentials: User.Credentials): IO[User.Credentials] =
    run(insertCredentials, credentials)

  override def editCredentials(login: User.Login)(pass: User.Password): IO[Done] =
    run(updateCredentials(login)(pass))

  override def removeCredentials(login: User.Login): IO[Done] =
    run(deleteCredentials(login))

  override def findCredentials(login: User.Login): IO[Option[User.Credentials]] =
    run(selectCredentials(login).option)

  override def find(login: User.Login): IO[Option[User]] = run(selectOne(login).option)

  override def find(credentials: User.Credentials): IO[Option[User]] = run(selectOne(credentials.login).option)

  override def find(email: EmailAddress): IO[Option[User]] = run(selectOne(email).option)

  override def find(slug: User.Slug): IO[Option[User]] = run(selectOne(slug).option)

  // FIXME should be done in only one query: joining on speakers array or splitting speakers string
  override def speakers(group: Group.Id, params: Page.Params): IO[Page[User]] = {
    val speakerIdsQuery = fr0"SELECT p.speakers FROM proposals p INNER JOIN cfps c ON c.id=p.cfp_id WHERE c.group_id=$group".query[NonEmptyList[User.Id]]
    for {
        speakerIds <- run(speakerIdsQuery.to[List]).map(_.flatMap(_.toList).distinct)
        res <- NonEmptyList.fromList(speakerIds).map(ids => run(Queries.selectPage(selectPage(ids, _), params))).getOrElse(IO.pure(Page.empty[User]))
    } yield res
  }

  override def list(ids: Seq[User.Id]): IO[Seq[User]] = runIn(selectAll)(ids)
}

object UserRepoSql {
  private val _ = userIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val credentialsTable = "credentials"
  private val credentialsFields = Seq("provider_id", "provider_key", "hasher", "password", "salt")
  private val loginTable = "logins"
  private val loginFields = Seq("provider_id", "provider_key", "user_id")
  private val table = "users"
  private val fields = Seq("id", "slug", "first_name", "last_name", "email", "email_validated", "avatar", "avatar_source", "created", "updated")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields = Seq("id", "slug", "first_name", "last_name", "email")
  private val defaultSort = Page.OrderBy("first_name")

  private def values(e: User): Fragment =
    fr0"${e.id}, ${e.slug}, ${e.firstName}, ${e.lastName}, ${e.email}, ${e.emailValidated}, ${e.avatar.url}, ${e.avatar.source}, ${e.created}, ${e.updated}"

  private[sql] def insert(elt: User): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  private[sql] def update(elt: User): doobie.Update0 = {
    val fields = fr0"slug=${elt.slug}, first_name=${elt.firstName}, last_name=${elt.lastName}, email=${elt.email}, updated=${elt.updated}"
    val where = fr0"WHERE id=${elt.id}"
    buildUpdate(tableFr, fields, where).update
  }

  private[sql] def validateAccount(email: EmailAddress, now: Instant): doobie.Update0 =
    buildUpdate(tableFr, fr0"email_validated=$now", fr0"WHERE email=$email").update

  private[sql] def insertLoginRef(i: User.LoginRef): doobie.Update0 =
    buildInsert(Fragment.const0(loginTable), Fragment.const0(loginFields.mkString(", ")), fr0"${i.login.providerId}, ${i.login.providerKey}, ${i.user}").update

  private[sql] def insertCredentials(i: User.Credentials): doobie.Update0 =
    buildInsert(Fragment.const0(credentialsTable), Fragment.const0(credentialsFields.mkString(", ")), fr0"${i.login.providerId}, ${i.login.providerKey}, ${i.pass.hasher}, ${i.pass.password}, ${i.pass.salt}").update

  private[sql] def updateCredentials(login: User.Login)(pass: User.Password): doobie.Update0 = {
    val fields = fr0"hasher=${pass.hasher}, password=${pass.password}, salt=${pass.salt}"
    val where = fr0"WHERE provider_id=${login.providerId} AND provider_key=${login.providerKey}"
    buildUpdate(Fragment.const0(credentialsTable), fields, where).update
  }

  private[sql] def deleteCredentials(login: User.Login): doobie.Update0 = {
    val where = fr0"WHERE provider_id=${login.providerId} AND provider_key=${login.providerKey}"
    buildDelete(Fragment.const0(credentialsTable), where).update
  }

  private[sql] def selectCredentials(login: User.Login): doobie.Query0[User.Credentials] =
    buildSelect(Fragment.const0(credentialsTable), Fragment.const0(credentialsFields.mkString(", ")), fr0"WHERE provider_id=${login.providerId} AND provider_key=${login.providerKey}").query[User.Credentials]

  private[sql] def selectOne(login: User.Login): doobie.Query0[User] = {
    val selectTables = Fragment.const0(s"$table u INNER JOIN $loginTable l ON u.id=l.user_id")
    val selectedFields = Fragment.const0(fields.map("u." + _).mkString(", "))
    buildSelect(selectTables, selectedFields, fr0"WHERE l.provider_id=${login.providerId} AND l.provider_key=${login.providerKey}").query[User]
  }

  private[sql] def selectOne(email: EmailAddress): doobie.Query0[User] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE email=$email").query[User]

  private[sql] def selectOne(slug: User.Slug): doobie.Query0[User] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE slug=$slug").query[User]

  // should replace def selectPage(ids: NonEmptyList[User.Id], params: Page.Params) when split or array works...
  /* private[sql] def selectPage(group: Group.Id, params: Page.Params): (doobie.Query0[User], doobie.Query0[Long]) = {
    val speakerIds = fr0"SELECT p.speakers FROM proposals p INNER JOIN cfps c ON c.id=p.cfp_id WHERE c.group_id=$group"
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE id IN (" ++ speakerIds ++ fr0")"))
    (buildSelect(tableFr, fieldsFr, page.all).query[User], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  } */

  private[sql] def selectPage(ids: NonEmptyList[User.Id], params: Page.Params): (doobie.Query0[User], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr"WHERE" ++ Fragments.in(fr"id", ids)))
    (buildSelect(tableFr, fieldsFr, page.all).query[User], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectAll(ids: NonEmptyList[User.Id]): doobie.Query0[User] =
    buildSelect(tableFr, fieldsFr, fr"WHERE" ++ Fragments.in(fr"id", ids)).query[User]
}
