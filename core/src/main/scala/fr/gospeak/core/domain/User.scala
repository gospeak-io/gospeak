package fr.gospeak.core.domain

import java.time.Instant

import fr.gospeak.libs.scalautils.domain._

final case class User(id: User.Id,
                      slug: User.Slug,
                      firstName: String,
                      lastName: String,
                      email: EmailAddress,
                      emailValidated: Option[Instant],
                      avatar: Avatar,
                      profile: User.Profile,
                      created: Instant,
                      updated: Instant) {
  def data: User.Data = User.Data(this)

  def name: User.Name = User.Name(firstName, lastName)

  def nameAndCompany: String = name.value + profile.company.map(c => s" ($c)").getOrElse("")

  def isPublic: Boolean = profile.status.isPublic

  def editable: User.EditableFields = User.EditableFields(firstName, lastName, email, profile)
}

object User {
  def apply(data: Data, profile: Profile, now: Instant): User =
    new User(Id.generate(), data.slug, data.firstName, data.lastName, data.email, None, data.avatar, profile, now, now)

  val emptyProfile = Profile(Profile.Status.Undefined, None, None, None, None, None, None, None)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("User.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value) with ISlug

  object Slug extends SlugBuilder[Slug]("User.Slug", new Slug(_))

  final case class Name(value: String) extends AnyVal

  object Name {
    def apply(firstName: String, lastName: String): Name = new Name(s"$firstName $lastName")
  }

  final case class Data(slug: User.Slug,
                        firstName: String,
                        lastName: String,
                        email: EmailAddress,
                        avatar: Avatar)

  object Data {
    def apply(user: User): Data = new Data(user.slug, user.firstName, user.lastName, user.email, user.avatar)
  }

  final case class ProviderId(value: String) extends AnyVal

  final case class ProviderKey(value: String) extends AnyVal

  final case class Hasher(value: String) extends AnyVal

  final case class PasswordValue(value: String) extends AnyVal

  final case class Salt(value: String) extends AnyVal

  final case class Login(providerId: ProviderId, providerKey: ProviderKey)

  final case class Password(hasher: Hasher, password: PasswordValue, salt: Option[Salt])

  final case class LoginRef(login: Login, user: User.Id)

  object LoginRef {
    def apply(providerId: String, providerKey: String, user: Id): LoginRef =
      new LoginRef(Login(ProviderId(providerId), ProviderKey(providerKey)), user)
  }

  final case class Credentials(login: Login, pass: Password)

  object Credentials {
    def apply(providerId: String, providerKey: String, hasher: String, password: String, salt: Option[String]): Credentials =
      new Credentials(Login(ProviderId(providerId), ProviderKey(providerKey)), Password(Hasher(hasher), PasswordValue(password), salt.map(Salt)))
  }

  case class Profile(status: Profile.Status,
                     description: Option[String],
                     company: Option[String],
                     location: Option[String],
                     twitter: Option[Url],
                     linkedin: Option[Url],
                     phone: Option[String],
                     website: Option[Url])

  object Profile {

    sealed trait Status extends StringEnum with Product with Serializable {
      def value: String = toString

      def isUndefined: Boolean = this == Status.Undefined

      def isPrivate: Boolean = this == Status.Private

      def isPublic: Boolean = this == Status.Public
    }

    object Status extends EnumBuilder[Status]("User.Profile.Status") {

      case object Undefined extends Status {
        def description = s"Profile privacy still undefined, choose if you want it $Public or $Private"
      }

      case object Private extends Status {
        def description = "Stay under cover, your speaker page is not accessible and you are not in the public list of speakers"
      }

      case object Public extends Status {
        def description = "Promote your name with your speaker page featuring your public talks and interventions you have done in groups"
      }

      val all: Seq[Status] = Seq(Undefined, Private, Public)
      val selectable: Seq[Status] = Seq(Private, Public)
    }

  }

  case class EditableFields(firstName: String,
                            lastName: String,
                            email: EmailAddress,
                            profile: Profile)

  object EditableFields {
    def apply(firstName: String,
              lastName: String,
              email: EmailAddress,
              status: Profile.Status,
              description: Option[String],
              company: Option[String],
              location: Option[String],
              twitter: Option[Url],
              linkedin: Option[Url],
              phone: Option[String],
              website: Option[Url]): EditableFields =
      EditableFields(firstName, lastName, email, Profile(status, description, company, location, twitter, linkedin, phone, website))

    def unapply(arg: EditableFields): Option[(String, String, EmailAddress, Profile.Status, Option[String], Option[String], Option[String], Option[Url], Option[Url], Option[String], Option[Url])] =
      Some((arg.firstName, arg.lastName, arg.email, arg.profile.status, arg.profile.description, arg.profile.company,
        arg.profile.location, arg.profile.twitter, arg.profile.linkedin, arg.profile.phone, arg.profile.website))
  }

}
