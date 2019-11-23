package fr.gospeak.migration.domain

import java.time.Instant

import fr.gospeak.core.domain.utils.SocialAccounts
import fr.gospeak.core.{domain => gs}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.StringUtils
import fr.gospeak.libs.scalautils.domain.{EmailAddress, Markdown, Url}
import fr.gospeak.migration.domain.utils.Meta
import fr.gospeak.migration.utils.AvatarUtils

import scala.util.Try

case class Person(id: String, // Person.Id,
                  data: PersonData,
                  auth: Option[PersonAuth],
                  meta: Meta) {
  def toUser: gs.User = {
    val (firstName, lastName) = getNames
    val email = getEmail
    val avatar = AvatarUtils.buildAvatarQuick(data.avatar, email)
    Try(gs.User(
      id = gs.User.Id.from(id).get,
      slug = gs.User.Slug.from(StringUtils.slugify(data.name)).get,
      status = gs.User.Status.Undefined,
      firstName = firstName,
      lastName = lastName,
      email = email,
      emailValidated = None,
      avatar = avatar,
      bio = data.description.map(Markdown),
      company = data.company,
      location = data.location,
      phone = data.phone,
      website = data.webSite.map(Url.from(_).get),
      social = SocialAccounts.fromUrls(
        twitter = data.twitter.map(t => Url.from("https://twitter.com/" + t).get),
        linkedIn = data.linkedin.map(Url.from(_).get)),
      createdAt = Instant.ofEpochMilli(meta.created),
      updatedAt = Instant.ofEpochMilli(meta.updated))).mapFailure(e => new Exception(s"toUser error for $this ", e)).get
  }

  def toContact(partner: Partner): (String, gs.Contact) = {
    val (firstName, lastName) = getNames
    (id, gs.Contact(
      id = gs.Contact.Id.generate(),
      partner = gs.Partner.Id.from(partner.id).get,
      firstName = gs.Contact.FirstName(firstName),
      lastName = gs.Contact.LastName(lastName),
      email = getEmail,
      description = Markdown(data.description.getOrElse("")),
      info = meta.toInfo))
  }

  private def getNames: (String, String) = {
    val names = data.name.split(' ').filter(_.nonEmpty)
    val firstName = names.headOption.getOrElse("")
    val lastName = Option(names.drop(1).mkString(" ")).filter(_.nonEmpty).getOrElse("")
    (firstName, lastName)
  }

  private def getEmail: EmailAddress = {
    val emailStr = auth.map(_.loginInfo).filter(_.providerID == "credentials").map(_.providerKey)
      .orElse(data.email)
      .getOrElse(StringUtils.slugify(data.name) + "@missing-email.com")
    EmailAddress.from(emailStr).get
  }
}

object Person {

  object Role {
    val user = "User"
    val organizer = "Organizer"
    val admin = "Admin"
  }

}

case class PersonData(name: String,
                      company: Option[String],
                      location: Option[String],
                      twitter: Option[String],
                      linkedin: Option[String],
                      email: Option[String],
                      phone: Option[String],
                      webSite: Option[String],
                      avatar: Option[String],
                      description: Option[String])

case class PersonAuth(loginInfo: LoginInfo,
                      role: String, // Person.Role.Value, (User, Organizer, Admin)
                      activated: Boolean) {
  def isOrga: Boolean = role == Person.Role.organizer || role == Person.Role.admin
}

case class LoginInfo(providerID: String, providerKey: String)
