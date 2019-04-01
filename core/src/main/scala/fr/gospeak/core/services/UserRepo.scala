package fr.gospeak.core.services

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.User
import fr.gospeak.libs.scalautils.domain.{Avatar, Done, EmailAddress}

trait UserRepo {
  def create(slug: User.Slug, firstName: String, lastName: String, email: EmailAddress, avatar: Avatar, now: Instant): IO[User]

  def update(user: User, now: Instant): IO[User]

  def createLoginRef(login: User.Login, user: User.Id): IO[Done]

  def createCredentials(credentials: User.Credentials): IO[User.Credentials]

  def updateCredentials(login: User.Login)(pass: User.Password): IO[Done]

  def deleteCredentials(login: User.Login): IO[Done]

  def findCredentials(login: User.Login): IO[Option[User.Credentials]]

  def find(login: User.Login): IO[Option[User]]

  def find(credentials: User.Credentials): IO[Option[User]]

  def find(email: EmailAddress): IO[Option[User]]

  def find(slug: User.Slug): IO[Option[User]]

  def list(ids: Seq[User.Id]): IO[Seq[User]]
}
