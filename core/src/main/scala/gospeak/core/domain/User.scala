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
                      title: Option[String],
                      bio: Option[Markdown],
                      mentoring: Option[Markdown],
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
    new User(Id.generate(), d.slug, d.status, d.firstName, d.lastName, d.email, emailValidated, emailValidationBeforeLogin = false, avatar = d.avatar, title = d.title, bio = d.bio, mentoring = d.mentoring, company = d.company, location = d.location, phone = d.phone, website = d.website, social = d.social, createdAt = now, updatedAt = now)

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

    def isPublic: Boolean = this == Status.Public

    def isPrivate: Boolean = this == Status.Private
  }

  object Status extends EnumBuilder[Status]("User.Status") {

    case object Public extends Status {
      def description = "Promote your name with your speaker page featuring your public talks and interventions you have done in groups"
    }

    case object Private extends Status {
      def description = "Stay under cover, your speaker page is not accessible and you are not in the public list of speakers"
    }

    val all: Seq[Status] = Seq(Public, Private)
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

  final case class Full(user: User, groupCount: Long, talkCount: Long, proposalCount: Long) {
    def id: Id = user.id

    def slug: Slug = user.slug

    def firstName: String = user.firstName

    def lastName: String = user.lastName

    def name: Name = user.name

    def email: EmailAddress = user.email

    def avatar: Avatar = user.avatar

    def title: Option[String] = user.title

    def bio: Option[Markdown] = user.bio

    def mentoring: Option[Markdown] = user.mentoring

    def company: Option[String] = user.company

    def location: Option[String] = user.location

    def website: Option[Url] = user.website

    def social: SocialAccounts = user.social

    def isPublic: Boolean = user.isPublic
  }

  final case class Data(slug: User.Slug,
                        status: User.Status,
                        firstName: String,
                        lastName: String,
                        email: EmailAddress,
                        avatar: Avatar,
                        title: Option[String],
                        bio: Option[Markdown],
                        mentoring: Option[Markdown],
                        company: Option[String],
                        location: Option[String],
                        phone: Option[String],
                        website: Option[Url],
                        social: SocialAccounts)

  object Data {
    def apply(u: User): Data = new Data(u.slug, u.status, u.firstName, u.lastName, u.email, u.avatar, u.title, u.bio, u.mentoring, u.company, u.location, u.phone, u.website, u.social)
  }

}
