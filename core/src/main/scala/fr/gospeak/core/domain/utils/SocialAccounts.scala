package fr.gospeak.core.domain.utils

import fr.gospeak.core.domain.utils.SocialAccounts.SocialAccount
import fr.gospeak.core.domain.utils.SocialAccounts.SocialAccount._
import fr.gospeak.libs.scalautils.domain.Url

final case class SocialAccounts(facebook: Option[FacebookAccount],
                                instagram: Option[InstagramAccount],
                                twitter: Option[TwitterAccount],
                                linkedIn: Option[LinkedInAccount],
                                youtube: Option[YoutubeAccount],
                                meetup: Option[MeetupAccount],
                                eventbrite: Option[EventbriteAccount],
                                slack: Option[SlackAccount],
                                discord: Option[DiscordAccount]) {
  def all: Seq[SocialAccount] = Seq(facebook, instagram, twitter, linkedIn, youtube, meetup, eventbrite, slack, discord).flatten
}

object SocialAccounts {
  def apply(facebook: Option[Url] = None,
            instagram: Option[Url] = None,
            twitter: Option[Url] = None,
            linkedIn: Option[Url] = None,
            youtube: Option[Url] = None,
            meetup: Option[Url] = None,
            eventbrite: Option[Url] = None,
            slack: Option[Url] = None,
            discord: Option[Url] = None,
            a: Boolean = true): SocialAccounts =
    new SocialAccounts(
      facebook = facebook.map(FacebookAccount),
      instagram = instagram.map(InstagramAccount),
      twitter = twitter.map(TwitterAccount),
      linkedIn = linkedIn.map(LinkedInAccount),
      youtube = youtube.map(YoutubeAccount),
      meetup = meetup.map(MeetupAccount),
      eventbrite = eventbrite.map(EventbriteAccount),
      slack = slack.map(SlackAccount),
      discord = discord.map(DiscordAccount))

  sealed abstract class SocialAccount(url: Url) {
    def link: String = url.value
  }

  object SocialAccount {

    final case class FacebookAccount(url: Url) extends SocialAccount(url)

    final case class InstagramAccount(url: Url) extends SocialAccount(url)

    final case class TwitterAccount(url: Url) extends SocialAccount(url) {
      def handle: String = "@" + url.value.split("/").last
    }

    final case class LinkedInAccount(url: Url) extends SocialAccount(url)

    final case class YoutubeAccount(url: Url) extends SocialAccount(url)

    final case class MeetupAccount(url: Url) extends SocialAccount(url)

    final case class EventbriteAccount(url: Url) extends SocialAccount(url)

    final case class SlackAccount(url: Url) extends SocialAccount(url)

    final case class DiscordAccount(url: Url) extends SocialAccount(url)

  }

}
