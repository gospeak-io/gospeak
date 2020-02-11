package gospeak.core.domain

import gospeak.core.domain.Contact.{FirstName, LastName}
import gospeak.core.domain.utils.Info
import gospeak.libs.scala.domain._

final case class Contact(id: Contact.Id,
                         partner: Partner.Id,
                         firstName: FirstName,
                         lastName: LastName,
                         email: EmailAddress,
                         notes: Markdown, // private infos for the group
                         info: Info) {
  def data: Contact.Data = Contact.Data(this)

  def users: List[User.Id] = info.users

  def name = Contact.Name(s"${firstName.value} ${lastName.value}")
}

object Contact {
  def apply(data: Data, info: Info): Contact =
    Contact(Id.generate(), data.partner, data.firstName, data.lastName, data.email, data.notes, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Contact.Id", new Id(_))

  final case class FirstName(value: String) extends AnyVal

  final case class LastName(value: String) extends AnyVal

  final case class Name(value: String) extends AnyVal

  final case class Data(partner: Partner.Id,
                        firstName: FirstName,
                        lastName: LastName,
                        email: EmailAddress,
                        notes: Markdown)

  object Data {
    def apply(c: Contact) = new Data(c.partner, c.firstName, c.lastName, c.email, c.notes)
  }

}
