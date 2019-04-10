package fr.gospeak.core.services

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.libs.scalautils.domain.{Done, EmailAddress, Page}

trait UserRepo extends OrgaUserRepo with SpeakerUserRepo with UserUserRepo with AuthUserRepo

trait OrgaUserRepo {
  def find(slug: User.Slug): IO[Option[User]]

  def speakers(group: Group.Id, params: Page.Params): IO[Page[User]]

  def list(ids: Seq[User.Id]): IO[Seq[User]]
}

trait SpeakerUserRepo {
  def list(ids: Seq[User.Id]): IO[Seq[User]]
}

trait UserUserRepo

trait AuthUserRepo {
  def create(data: User.Data, now: Instant): IO[User]

  def edit(user: User, now: Instant): IO[User]

  def createLoginRef(login: User.Login, user: User.Id): IO[Done]

  def createCredentials(credentials: User.Credentials): IO[User.Credentials]

  def editCredentials(login: User.Login)(pass: User.Password): IO[Done]

  def removeCredentials(login: User.Login): IO[Done]

  def findCredentials(login: User.Login): IO[Option[User.Credentials]]

  def find(login: User.Login): IO[Option[User]]

  def find(credentials: User.Credentials): IO[Option[User]]

  def find(email: EmailAddress): IO[Option[User]]

  def find(slug: User.Slug): IO[Option[User]]
}
