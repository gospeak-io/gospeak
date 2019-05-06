package fr.gospeak.migration.domain

import java.time.Instant

import fr.gospeak.core.domain.User
import fr.gospeak.core.domain.User.{Profile, Shirt}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.StringUtils
import fr.gospeak.libs.scalautils.domain.{EmailAddress, Url}
import fr.gospeak.migration.domain.utils.Meta
import fr.gospeak.migration.utils.AvatarUtils

import scala.util.Try

case class Person(id: String, // Person.Id,
                  data: PersonData,
                  auth: Option[PersonAuth],
                  meta: Meta) {
  lazy val toUser: User = {
    val names = data.name.split(' ').filter(_.nonEmpty)
    val emailStr = auth.map(_.loginInfo).filter(_.providerID == "credentials").map(_.providerKey)
      .orElse(data.email)
      .getOrElse(StringUtils.slugify(data.name) + "@missing-email.com")
    val email = EmailAddress.from(emailStr).get
    val validated = auth.filter(_.activated).map(_ => meta.created)
    val avatar = AvatarUtils.buildAvatarQuick(data.avatar, email)
    val profile: Profile = Profile(
      description = data.description,
      shirt = data.shirt.map(Shirt.from(_).getOrElse(User.Shirt.NotAValidSize)),
      company = data.company,
      location = data.location,
      twitter = data.twitter.map(Url.from(_).get),
      linkedin = data.linkedin.map(Url.from(_).get),
      phone = data.phone,
      webSite = data.webSite.map(Url.from(_).get))

    Try(User(
      id = User.Id.from(id).get,
      slug = User.Slug.from(StringUtils.slugify(data.name)).get,
      firstName = names.headOption.getOrElse("N/A"),
      lastName = Option(names.drop(1).mkString(" ")).filter(_.nonEmpty).getOrElse("N/A"),
      email = email,
      emailValidated = validated.map(Instant.ofEpochMilli),
      avatar = avatar,
      published = None,
      profile = profile,
      created = Instant.ofEpochMilli(meta.created),
      updated = Instant.ofEpochMilli(meta.updated))).mapFailure(e => new Exception(s"toUser error for $this", e)).get
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
                      shirt: Option[String], // Option[Person.Shirt.Value], (XS_M, S_F, S_M, M_F, M_M, L_F, L_M, XL_M)
                      description: Option[String])

case class PersonAuth(loginInfo: LoginInfo,
                      role: String, // Person.Role.Value, (User, Organizer, Admin)
                      activated: Boolean)

case class LoginInfo(providerID: String, providerKey: String)
