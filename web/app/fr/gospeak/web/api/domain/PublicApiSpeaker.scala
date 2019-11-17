package fr.gospeak.web.api.domain

import fr.gospeak.core.domain.User
import fr.gospeak.core.domain.utils.Constants
import play.api.libs.json.{Json, Writes}

case class PublicApiSpeaker(slug: String,
                            firstName: String,
                            lastName: String,
                            avatar: String,
                            bio: Option[String],
                            twitter: Option[String],
                            linkedin: Option[String],
                            website: Option[String])

object PublicApiSpeaker {
  def apply(user: User): PublicApiSpeaker =
    new PublicApiSpeaker(
      slug = user.slug.value,
      firstName = user.firstName,
      lastName = user.lastName,
      avatar = user.avatar.url.value,
      bio = user.profile.bio.map(_.value),
      twitter = user.profile.twitter.map(_.value),
      linkedin = user.profile.linkedin.map(_.value),
      website = user.profile.website.map(_.value))

  case class Embedded(slug: String,
                      firstName: String,
                      lastName: String,
                      avatar: String,
                      bio: Option[String],
                      twitter: Option[String],
                      linkedin: Option[String],
                      website: Option[String])

  object Embedded {
    def apply(user: User): Embedded =
      new Embedded(
        slug = user.slug.value,
        firstName = user.firstName,
        lastName = user.lastName,
        avatar = user.avatar.url.value,
        bio = user.profile.bio.map(_.value),
        twitter = user.profile.twitter.map(_.value),
        linkedin = user.profile.linkedin.map(_.value),
        website = user.profile.website.map(_.value))

    val unknown = Embedded("missing", "Missing", "Speaker", s"https://secure.gravatar.com/avatar/fa24c69431e3df73ef30d06860dd6258?size=100&default=${Constants.gravatarStyle}", None, None, None, None)

    implicit val embeddedWrites: Writes[Embedded] = Json.writes[Embedded]
  }

  implicit val writes: Writes[PublicApiSpeaker] = Json.writes[PublicApiSpeaker]
}
