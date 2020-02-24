package gospeak.core.domain

import java.time.LocalDateTime

import gospeak.core.domain.utils.{Info, TemplateData}
import gospeak.core.domain.utils.SocialAccounts.SocialAccount.TwitterAccount
import gospeak.libs.scala.domain.MustacheTmpl.MustacheMarkdownTmpl
import gospeak.libs.scala.domain.{DataClass, GMapPlace, IId, Logo, Markdown, Tag, TwitterHashtag, Url, UuidIdBuilder}

final case class CommonEvent(id: CommonEvent.Id,
                             name: Event.Name,
                             kind: Event.Kind,
                             start: Option[LocalDateTime],
                             location: Option[GMapPlace],
                             twitterAccount: Option[TwitterAccount],
                             twitterHashtag: Option[TwitterHashtag],
                             tags: Seq[Tag],
                             internal: Option[CommonEvent.Internal],
                             external: Option[CommonEvent.External],
                             info: Info) {
  def logo: Option[Logo] = internal.flatMap(i => i.groupLogo.orElse(i.venueLogo)).orElse(external.flatMap(_.logo))

  def users: List[User.Id] = info.users
}

object CommonEvent {

  final class Id private(value: String) extends DataClass(value) with IId {
    def internal: Event.Id = Event.Id.from(this)

    def external: ExternalEvent.Id = ExternalEvent.Id.from(this)
  }

  object Id extends UuidIdBuilder[Id]("CommonEvent.Id", new Id(_)) {
    def apply(id: Event.Id): Id = new Id(id.value)

    def apply(id: ExternalEvent.Id): Id = new Id(id.value)
  }

  final case class Internal(groupId: Group.Id,
                            groupSlug: Group.Slug,
                            groupName: Group.Name,
                            groupLogo: Option[Logo],
                            cfpId: Option[Cfp.Id],
                            cfpSlug: Option[Cfp.Slug],
                            cfpName: Option[Cfp.Name],
                            venueId: Option[Venue.Id],
                            venueName: Option[Partner.Name],
                            venueLogo: Option[Logo],
                            eventSlug: Event.Slug,
                            description: MustacheMarkdownTmpl[TemplateData.EventInfo])

  final case class External(logo: Option[Logo],
                            description: Markdown,
                            url: Option[Url],
                            tickets: Option[Url],
                            videos: Option[Url])

}
