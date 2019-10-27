package fr.gospeak.core.domain

import fr.gospeak.core.domain.Contact.{FirstName, LastName}
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.libs.scalautils.domain._

final case class Contact(id: Contact.Id,
                         partner: Partner.Id,
                         firstName: FirstName,
                         lastName: LastName,
                         email: EmailAddress,
                         description: Markdown,
                         info: Info) {
  def data: Contact.Data = Contact.Data(this)

  def users: Seq[User.Id] = info.users

  def name = Contact.Name(s"${firstName.value} ${lastName.value}")
}

object Contact {
  def apply(data: Data,
            info: Info): Contact = Contact(Id.generate(), data.partner, data.firstName, data.lastName, data.email, data.description, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Contact.Id", new Id(_))

  final case class FirstName(value: String) extends AnyVal

  final case class LastName(value: String) extends AnyVal

  final case class Name(value: String) extends AnyVal

  final case class Data(partner: Partner.Id, firstName: FirstName, lastName: LastName, email: EmailAddress, description: Markdown)

  object Data {
    def apply(c: Contact) = new Data(c.partner, c.firstName, c.lastName, c.email, c.description)
  }

}
