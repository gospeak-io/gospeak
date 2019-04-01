package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.User
import fr.gospeak.core.services.UserRepo
import fr.gospeak.infra.services.storage.sql.tables.UserTable
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.libs.scalautils.domain.{Avatar, Done, EmailAddress}

class UserRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with UserRepo {
  override def create(slug: User.Slug, firstName: String, lastName: String, email: EmailAddress, avatar: Avatar, now: Instant): IO[User] =
    run(UserTable.insert, User(User.Id.generate(), slug, firstName, lastName, email, None, avatar, now, now))

  override def update(user: User, now: Instant): IO[User] =
    run(UserTable.update(user.copy(updated = now))).map(_ => user)

  override def createLoginRef(login: User.Login, user: User.Id): IO[Done] =
    run(UserTable.insertLoginRef, User.LoginRef(login, user)).map(_ => Done)

  override def createCredentials(credentials: User.Credentials): IO[User.Credentials] =
    run(UserTable.insertCredentials, credentials)

  override def updateCredentials(login: User.Login)(pass: User.Password): IO[Done] =
    run(UserTable.updateCredentials(login)(pass))

  override def deleteCredentials(login: User.Login): IO[Done] =
    run(UserTable.deleteCredentials(login))

  override def findCredentials(login: User.Login): IO[Option[User.Credentials]] =
    run(UserTable.selectCredentials(login).option)

  override def find(login: User.Login): IO[Option[User]] = run(UserTable.selectOne(login).option)

  override def find(credentials: User.Credentials): IO[Option[User]] = run(UserTable.selectOne(credentials.login).option)

  override def find(email: EmailAddress): IO[Option[User]] = run(UserTable.selectOne(email).option)

  override def find(slug: User.Slug): IO[Option[User]] = run(UserTable.selectOne(slug).option)

  override def list(ids: Seq[User.Id]): IO[Seq[User]] = runIn(UserTable.selectAll)(ids)
}
