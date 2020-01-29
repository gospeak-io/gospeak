package gospeak.core.domain

import java.time.Instant

import gospeak.core.domain.utils.SocialAccounts
import gospeak.libs.scala.domain._

final case class User(id: User.Id,
                      slug: User.Slug,
                      status: User.Status,
                      firstName: String,
                      lastName: String,
                      email: EmailAddress,
                      emailValidated: Option[Instant],
                      emailValidationBeforeLogin: Boolean,
                      avatar: Avatar,
                      bio: Option[Markdown],
                      company: Option[String],
                      location: Option[String],
                      phone: Option[String],
                      website: Option[Url],
                      social: SocialAccounts,
                      createdAt: Instant,
                      updatedAt: Instant) {
  def data: User.Data = User.Data(this)

  def name: User.Name = User.Name(firstName, lastName)

  def nameAndCompany: String = name.value + company.map(c => s" ($c)").getOrElse("")

  def isPublic: Boolean = status.isPublic
}

object User {
  def apply(d: Data, now: Instant, emailValidated: Option[Instant] = None): User =
    new User(Id.generate(), d.slug, d.status, d.firstName, d.lastName, d.email, emailValidated, emailValidationBeforeLogin = false, avatar = d.avatar, bio = d.bio, company = d.company, location = d.location, phone = d.phone, website = d.website, social = d.social, createdAt = now, updatedAt = now)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("User.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value) with ISlug

  object Slug extends SlugBuilder[Slug]("User.Slug", new Slug(_))

  final case class Name(value: String) extends AnyVal

  object Name {
    def apply(firstName: String, lastName: String): Name = new Name(s"$firstName $lastName")
  }

  sealed trait Status extends StringEnum {
    def value: String = toString

    def isUndefined: Boolean = this == Status.Undefined

    def isPrivate: Boolean = this == Status.Private

    def isPublic: Boolean = this == Status.Public
  }

  object Status extends EnumBuilder[Status]("User.Status") {

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

  final case class Data(slug: User.Slug,
                        status: User.Status,
                        firstName: String,
                        lastName: String,
                        email: EmailAddress,
                        avatar: Avatar,
                        bio: Option[Markdown],
                        company: Option[String],
                        location: Option[String],
                        phone: Option[String],
                        website: Option[Url],
                        social: SocialAccounts)

  object Data {
    def apply(u: User): Data = new Data(u.slug, u.status, u.firstName, u.lastName, u.email, u.avatar, u.bio, u.company, u.location, u.phone, u.website, u.social)
  }

}
