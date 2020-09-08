package gospeak.core.domain

import java.time.LocalDateTime

import gospeak.core.domain.messages.Message
import gospeak.core.domain.utils.Info
import gospeak.core.domain.utils.SocialAccounts.SocialAccount.TwitterAccount
import gospeak.libs.scala.domain._

final case class CommonEvent(name: Event.Name,
                             kind: Event.Kind,
                             start: Option[LocalDateTime],
                             location: Option[GMapPlace],
                             twitterAccount: Option[TwitterAccount],
                             twitterHashtag: Option[TwitterHashtag],
                             tags: List[Tag],
                             extra: Either[CommonEvent.External, CommonEvent.Internal],
                             info: Info) {
  def logo: Option[Logo] = extra.fold(_.logo, i => i.group.logo.orElse(i.venue.map(_.logo)))

  def users: List[User.Id] = info.users

  def fold[A](f: CommonEvent.External => A)(g: CommonEvent.Internal => A): A = extra.fold(f, g)

  def internal: Option[CommonEvent.Internal] = extra.right.toOption

  def external: Option[CommonEvent.External] = extra.left.toOption
}

object CommonEvent {
  def apply(event: ExternalEvent): CommonEvent = new CommonEvent(
    name = event.name,
    kind = event.kind,
    start = event.start,
    location = event.location,
    twitterAccount = event.twitterAccount,
    twitterHashtag = event.twitterHashtag,
    tags = event.tags,
    extra = Left(External(
      id = event.id,
      logo = event.logo,
      description = event.description,
      url = event.url,
      tickets = event.tickets,
      videos = event.videos)),
    info = event.info)

  final case class InternalGroup(id: Group.Id,
                                 slug: Group.Slug,
                                 name: Group.Name,
                                 logo: Option[Logo])

  final case class InternalCfp(id: Cfp.Id,
                               slug: Cfp.Slug,
                               name: Cfp.Name)

  final case class InternalVenue(id: Venue.Id,
                                 name: Partner.Name,
                                 logo: Logo)

  final case class Internal(id: Event.Id,
                            slug: Event.Slug,
                            description: LiquidMarkdown[Message.EventInfo],
                            group: InternalGroup,
                            cfp: Option[InternalCfp],
                            venue: Option[InternalVenue])

  final case class External(id: ExternalEvent.Id,
                            logo: Option[Logo],
                            description: Markdown,
                            url: Option[Url],
                            tickets: Option[Url],
                            videos: Option[Url])

}
