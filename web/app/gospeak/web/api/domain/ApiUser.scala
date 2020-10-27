package gospeak.web.api.domain

import gospeak.core.domain.User
import gospeak.core.domain.utils.{BasicCtx, Constants}
import gospeak.libs.scala.domain.{EmailAddress, Secret}
import gospeak.web.api.domain.utils.ApiSocial
import gospeak.web.api.domain.utils.JsonFormats._
import gospeak.web.utils.GsForms
import play.api.libs.json.{Json, Reads, Writes}

object ApiUser {

  // data to display publicly
  final case class Published(slug: String,
                             firstName: String,
                             lastName: String,
                             avatar: String,
                             bio: Option[String],
                             website: Option[String],
                             social: ApiSocial)

  object Published {
    implicit val writes: Writes[Published] = Json.writes[Published]
  }

  def published(user: User.Full)(implicit ctx: BasicCtx): Published =
    new Published(
      slug = user.slug.value,
      firstName = user.firstName,
      lastName = user.lastName,
      avatar = user.avatar.url.value,
      bio = user.bio.map(_.value),
      website = user.website.map(_.value),
      social = ApiSocial.from(user.social))

  // embedded data in other models, should be public
  final case class Embed(slug: String,
                         firstName: String,
                         lastName: String,
                         avatar: String,
                         bio: Option[String],
                         website: Option[String],
                         social: ApiSocial)

  object Embed {
    implicit val writes: Writes[Embed] = Json.writes[Embed]
  }

  def embed(id: User.Id, users: List[User])(implicit ctx: BasicCtx): Embed =
    users.find(_.id == id).map(embed).getOrElse(unknown(id))

  def embed(user: User)(implicit ctx: BasicCtx): Embed =
    new Embed(
      slug = user.slug.value,
      firstName = user.firstName,
      lastName = user.lastName,
      avatar = user.avatar.url.value,
      bio = user.bio.map(_.value),
      website = user.website.map(_.value),
      social = ApiSocial.from(user.social))

  def unknown(id: User.Id)(implicit ctx: BasicCtx): Embed =
    new Embed(
      slug = "unknown",
      firstName = "Unknown",
      lastName = "User",
      avatar = Constants.Placeholders.unknownUser,
      bio = None,
      website = None,
      social = ApiSocial(None, None, None, None, None, None, None, None, None, None))

  final case class SignupPayload(firstName: String,
                                 lastName: String,
                                 username: User.Slug,
                                 email: EmailAddress,
                                 password: Secret,
                                 rememberMe: Option[Boolean]) {
    def asData: GsForms.SignupData = GsForms.SignupData(username, firstName, lastName, email, password, rememberMe.getOrElse(false))
  }

  object SignupPayload {
    implicit val reads: Reads[SignupPayload] = Json.reads[SignupPayload]
  }

  final case class LoginPayload(email: EmailAddress,
                                password: Secret,
                                rememberMe: Option[Boolean]) {
    def asData: GsForms.LoginData = GsForms.LoginData(email, password, rememberMe.getOrElse(false))
  }

  object LoginPayload {
    implicit val reads: Reads[LoginPayload] = Json.reads[LoginPayload]
  }

}
