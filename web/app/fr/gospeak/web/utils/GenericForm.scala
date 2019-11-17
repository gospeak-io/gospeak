package fr.gospeak.web.utils

import fr.gospeak.core.domain.Comment
import fr.gospeak.core.domain.utils.SocialAccounts
import fr.gospeak.core.domain.utils.SocialAccounts.SocialAccount._
import fr.gospeak.libs.scalautils.domain.{EmailAddress, Url}
import fr.gospeak.web.utils.Mappings._
import play.api.data.Forms._
import play.api.data.{Form, Mapping}

object GenericForm {
  val embed: Form[Url] = Form(single("url" -> url))

  val invite: Form[EmailAddress] = Form(single(
    "email" -> emailAddress
  ))

  val comment: Form[Comment.Data] = Form(mapping(
    "answers" -> optional(commentId),
    "text" -> nonEmptyText
  )(Comment.Data.apply)(Comment.Data.unapply))

  val socialAccounts: Mapping[SocialAccounts] = mapping(
    "facebook" -> optional(url.transform[FacebookAccount](FacebookAccount, _.url)),
    "instagram" -> optional(url.transform[InstagramAccount](InstagramAccount, _.url)),
    "twitter" -> optional(url.transform[TwitterAccount](TwitterAccount, _.url)),
    "linkedIn" -> optional(url.transform[LinkedInAccount](LinkedInAccount, _.url)),
    "youtube" -> optional(url.transform[YoutubeAccount](YoutubeAccount, _.url)),
    "meetup" -> optional(url.transform[MeetupAccount](MeetupAccount, _.url)),
    "eventbrite" -> optional(url.transform[EventbriteAccount](EventbriteAccount, _.url)),
    "slack" -> optional(url.transform[SlackAccount](SlackAccount, _.url)),
    "discord" -> optional(url.transform[DiscordAccount](DiscordAccount, _.url))
  )(SocialAccounts.apply)(SocialAccounts.unapply)
}
