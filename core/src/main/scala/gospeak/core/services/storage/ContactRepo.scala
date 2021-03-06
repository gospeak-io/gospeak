package gospeak.core.services.storage

import cats.effect.IO
import gospeak.core.domain._
import gospeak.core.domain.utils.OrgaCtx
import gospeak.libs.scala.domain.EmailAddress

trait ContactRepo extends SuggestContactRepo {
  def list(partner: Partner.Id): IO[List[Contact]]

  def create(data: Contact.Data)(implicit ctx: OrgaCtx): IO[Contact]

  def edit(contact: Contact.Id, data: Contact.Data)(implicit ctx: OrgaCtx): IO[Unit]

  def remove(partner: Partner.Id, contact: Contact.Id)(implicit ctx: OrgaCtx): IO[Unit]

  def find(id: Contact.Id): IO[Option[Contact]]

  def exists(partner: Partner.Id, email: EmailAddress): IO[Boolean]
}

trait SuggestContactRepo {
  def list(partner: Partner.Id): IO[List[Contact]]
}
