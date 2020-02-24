package gospeak.web.api.domain.utils

import gospeak.core.domain.utils.{BasicCtx, SocialAccounts}
import play.api.libs.json.{Json, Writes}

final case class ApiSocial(facebook: Option[ApiSocial.Account],
                           instagram: Option[ApiSocial.Account],
                           twitter: Option[ApiSocial.Account],
                           linkedIn: Option[ApiSocial.Account],
                           youtube: Option[ApiSocial.Account],
                           meetup: Option[ApiSocial.Account],
                           eventbrite: Option[ApiSocial.Account],
                           slack: Option[ApiSocial.Account],
                           discord: Option[ApiSocial.Account],
                           github: Option[ApiSocial.Account])

object ApiSocial {

  final case class Account(url: String, handle: String)

  object Account {
    implicit val writes: Writes[Account] = Json.writes[Account]
  }

  def from(s: SocialAccounts)(implicit ctx: BasicCtx): ApiSocial =
    ApiSocial(
      facebook = s.facebook.map(a => Account(a.url.value, a.handle)),
      instagram = s.instagram.map(a => Account(a.url.value, a.handle)),
      twitter = s.twitter.map(a => Account(a.url.value, a.handle)),
      linkedIn = s.linkedIn.map(a => Account(a.url.value, a.handle)),
      youtube = s.youtube.map(a => Account(a.url.value, a.handle)),
      meetup = s.meetup.map(a => Account(a.url.value, a.handle)),
      eventbrite = s.eventbrite.map(a => Account(a.url.value, a.handle)),
      slack = s.slack.map(a => Account(a.url.value, a.handle)),
      discord = s.discord.map(a => Account(a.url.value, a.handle)),
      github = s.github.map(a => Account(a.url.value, a.handle)))

  implicit val writes: Writes[ApiSocial] = Json.writes[ApiSocial]
}
