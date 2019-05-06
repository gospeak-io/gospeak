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
                      published: Option[Instant],
                      profile: User.Profile,
                      created: Instant,
                      updated: Instant) {
  def data: User.Data = User.Data(this)

  def name: User.Name = User.Name(firstName, lastName)

  def isPublic: Boolean = published.isDefined

  def editable: User.EditableFields = User.EditableFields(firstName, lastName, email, profile)
}

object User {
  def apply(data: Data, profile: Profile, now: Instant): User =
    new User(Id.generate(), data.slug, data.firstName, data.lastName, data.email, None, data.avatar, None, profile, now, now)

  val emptyProfile = Profile(None, None, None, None, None, None, None, None)

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

  object Shirt {

    sealed trait Size

    case object XS extends Size

    case object S extends Size

    case object M extends Size

    case object L extends Size

    case object XL extends Size

    case object XXL extends Size

    case object NotAValidSize extends Size

    val all: Seq[Size] = Seq(XS, S, M, L, XL, XXL)

    def from(in: String): Either[CustomException, Size] =
      all.find(_.toString == in).map(Right(_)).getOrElse(Left(CustomException(s"'$in' is not a valid Shirt.Size")))


  }


  case class Profile(description: Option[String],
                     shirt: Option[Shirt.Size],
                     company: Option[String],
                     location: Option[String],
                     twitter: Option[Url],
                     linkedin: Option[Url],
                     phone: Option[String],
                     webSite: Option[Url]
                    )

  case class EditableFields(firstName: String,
                            lastName: String,
                            email: EmailAddress,
                            // avatar: Avatar, // need to have avatar utils ...
                            profile: Profile)

  object EditableFields {
    def apply(firstName: String,
              lastName: String,
              email: EmailAddress,
              // avatar: Option[Url],
              description: Option[String],
              shirt: Option[Shirt.Size], //(XS_M, S_F, S_M, M_F, M_M, L_F, L_M, XL_M)
              company: Option[String],
              location: Option[String],
              twitter: Option[Url],
              linkedin: Option[Url],
              phone: Option[String],
              webSite: Option[Url]): EditableFields = EditableFields(firstName, lastName, email,
      Profile(description, shirt, company, location, twitter, linkedin, phone, webSite))

    def unapply(arg: EditableFields): Option[(String, String, EmailAddress, Option[String], Option[Shirt.Size], Option[String], Option[String], Option[Url], Option[Url], Option[String], Option[Url])] =
      Some((arg.firstName, arg.lastName, arg.email, arg.profile.description, arg.profile.shirt, arg.profile.company,
        arg.profile.location, arg.profile.twitter, arg.profile.linkedin, arg.profile.phone, arg.profile.webSite))

  }

}
