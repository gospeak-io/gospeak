package fr.gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain._
import fr.gospeak.libs.scalautils.domain.{Done, EmailAddress, Page}

trait ContactRepo extends SuggestContactRepo {
  def list(partner: Partner.Id): IO[Seq[Contact]]

  def create(data: Contact.Data, by: User.Id, now: Instant): IO[Contact]

  def list(partner: Partner.Id, params: Page.Params): IO[Page[Contact]]

  def find(id: Contact.Id): IO[Option[Contact]]

  def edit(contact: Contact.Id, data: Contact.Data)(by: User.Id, now: Instant): IO[Done]

  def exists(partner: Partner.Id, email: EmailAddress): IO[Boolean]
}

trait SuggestContactRepo {
  def list(partner: Partner.Id): IO[Seq[Contact]]
}
