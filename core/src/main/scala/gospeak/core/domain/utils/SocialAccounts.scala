package gospeak.core.domain.utils

import gospeak.core.domain.utils.SocialAccounts.SocialAccount
import gospeak.core.domain.utils.SocialAccounts.SocialAccount._
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{CustomException, Url}

final case class SocialAccounts(facebook: Option[FacebookAccount],
                                instagram: Option[InstagramAccount],
                                twitter: Option[TwitterAccount],
                                linkedIn: Option[LinkedInAccount],
                                youtube: Option[YoutubeAccount],
                                meetup: Option[MeetupAccount],
                                eventbrite: Option[EventbriteAccount],
                                slack: Option[SlackAccount],
                                discord: Option[DiscordAccount],
                                github: Option[GithubAccount]) {
  def all: Seq[SocialAccount] = Seq(facebook, twitter, instagram, linkedIn, youtube, meetup, eventbrite, github, slack, discord).flatten
}

object SocialAccounts {
  def fromUrls(facebook: Option[Url] = None,
               instagram: Option[Url] = None,
               twitter: Option[Url.Twitter] = None,
               linkedIn: Option[Url.LinkedIn] = None,
               youtube: Option[Url.YouTube] = None,
               meetup: Option[Url.Meetup] = None,
               eventbrite: Option[Url] = None,
               slack: Option[Url] = None,
               discord: Option[Url] = None,
               github: Option[Url.Github] = None): SocialAccounts =
    new SocialAccounts(
      facebook = facebook.map(FacebookAccount),
      instagram = instagram.map(InstagramAccount),
      twitter = twitter.map(TwitterAccount),
      linkedIn = linkedIn.map(LinkedInAccount),
      youtube = youtube.map(YoutubeAccount),
      meetup = meetup.map(MeetupAccount),
      eventbrite = eventbrite.map(EventbriteAccount),
      slack = slack.map(SlackAccount),
      discord = discord.map(DiscordAccount),
      github = github.map(GithubAccount))

  def fromStrings(facebook: Option[String] = None,
                  instagram: Option[String] = None,
                  twitter: Option[String] = None,
                  linkedIn: Option[String] = None,
                  youtube: Option[String] = None,
                  meetup: Option[String] = None,
                  eventbrite: Option[String] = None,
                  slack: Option[String] = None,
                  discord: Option[String] = None,
                  github: Option[String] = None): Either[CustomException, SocialAccounts] = for {
    facebookUrl <- facebook.map(Url.from).sequence
    instagramUrl <- instagram.map(Url.from).sequence
    twitterUrl <- twitter.map(Url.Twitter.from).sequence
    linkedInUrl <- linkedIn.map(Url.LinkedIn.from).sequence
    youtubeUrl <- youtube.map(Url.YouTube.from).sequence
    meetupUrl <- meetup.map(Url.Meetup.from).sequence
    eventbriteUrl <- eventbrite.map(Url.from).sequence
    slackUrl <- slack.map(Url.from).sequence
    discordUrl <- discord.map(Url.from).sequence
    githubUrl <- github.map(Url.Github.from).sequence
  } yield fromUrls(facebookUrl, instagramUrl, twitterUrl, linkedInUrl, youtubeUrl, meetupUrl, eventbriteUrl, slackUrl, discordUrl, githubUrl)

  sealed abstract class SocialAccount(url: Url, val name: String) {
    def link: String = url.value

    def handle: String = url.handle
  }

  object SocialAccount {

    final case class FacebookAccount(url: Url) extends SocialAccount(url, "facebook") {
      override def handle: String = url.path.lastOption.getOrElse(url.handle)
    }

    final case class InstagramAccount(url: Url) extends SocialAccount(url, "instagram") {
      override def handle: String = url.path.lastOption.getOrElse(url.handle)
    }

    final case class TwitterAccount(url: Url.Twitter) extends SocialAccount(url, "twitter")

    final case class LinkedInAccount(url: Url.LinkedIn) extends SocialAccount(url, "linkedin")

    final case class YoutubeAccount(url: Url.YouTube) extends SocialAccount(url, "youtube")

    final case class MeetupAccount(url: Url.Meetup) extends SocialAccount(url, "meetup")

    final case class EventbriteAccount(url: Url) extends SocialAccount(url, "eventbrite") {
      override def handle: String = url.path.lastOption.getOrElse(url.handle)
    }

    final case class SlackAccount(url: Url) extends SocialAccount(url, "slack") {
      override def handle: String = url.host.split('.').head
    }

    final case class DiscordAccount(url: Url) extends SocialAccount(url, "discord") {
      override def handle: String = url.path.lastOption.getOrElse(url.handle)
    }

    final case class GithubAccount(url: Url.Github) extends SocialAccount(url, "github")

  }

}
