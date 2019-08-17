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

  def name = Contact.Name(firstName, lastName)

}

object Contact {

  def apply(data: Data,
            info: Info): Contact = Contact(Id.generate(), data.partner, data.firstName, data.lastName, data.email, data.description, info)


  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Contact.Id", new Id(_))

  final case class Data(partner: Partner.Id, firstName: FirstName, lastName: LastName, email: EmailAddress, description: Markdown)

  final case class FirstName(value: String) extends AnyVal

  final case class LastName(value: String) extends AnyVal

  final case class Description(value: String) extends AnyVal

  object Data {
    def apply(c: Contact) = new Data(c.partner, c.firstName, c.lastName, c.email, c.description)
  }

  final case class Name(value: String) extends AnyVal

  object Name {
    def apply(firstName: FirstName, lastName: LastName): Name = new Name(s"${firstName.value} ${lastName.value}")
  }

}
