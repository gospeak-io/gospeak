package fr.gospeak.web.api.domain

import fr.gospeak.core.domain.User
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
      bio = user.profile.description,
      twitter = user.profile.twitter.map(_.value),
      linkedin = user.profile.linkedin.map(_.value),
      website = user.profile.website.map(_.value))

  val unknown = PublicApiSpeaker("missing", "Missing", "Speaker", "https://secure.gravatar.com/avatar/fa24c69431e3df73ef30d06860dd6258?size=100&default=wavatar", None, None, None, None)

  implicit val writes: Writes[PublicApiSpeaker] = Json.writes[PublicApiSpeaker]
}
