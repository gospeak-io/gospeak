package gospeak.core.domain

import java.time.LocalDateTime

import gospeak.core.domain.utils.Info
import gospeak.core.domain.utils.SocialAccounts.SocialAccount.TwitterAccount
import gospeak.libs.scala.domain._

final case class ExternalEvent(id: ExternalEvent.Id,
                               name: Event.Name,
                               kind: Event.Kind,
                               logo: Option[Logo],
                               description: Markdown,
                               start: Option[LocalDateTime],
                               finish: Option[LocalDateTime],
                               location: Option[GMapPlace],
                               url: Option[Url],
                               tickets: Option[Url],
                               videos: Option[Url],
                               twitterAccount: Option[TwitterAccount],
                               twitterHashtag: Option[TwitterHashtag],
                               tags: Seq[Tag],
                               info: Info) {
  def data: ExternalEvent.Data = ExternalEvent.Data(this)
}

object ExternalEvent {
  def apply(d: Data, info: Info): ExternalEvent =
    new ExternalEvent(Id.generate(), d.name, d.kind, d.logo, d.description, d.start, d.finish, d.location, d.url, d.tickets, d.videos, d.twitterAccount, d.twitterHashtag, d.tags, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("ExternalEvent.Id", new Id(_))

  final case class Data(name: Event.Name,
                        kind: Event.Kind,
                        logo: Option[Logo],
                        description: Markdown,
                        start: Option[LocalDateTime],
                        finish: Option[LocalDateTime],
                        location: Option[GMapPlace],
                        url: Option[Url],
                        tickets: Option[Url],
                        videos: Option[Url],
                        twitterAccount: Option[TwitterAccount],
                        twitterHashtag: Option[TwitterHashtag],
                        tags: Seq[Tag])

  object Data {
    def apply(e: ExternalEvent): Data = new Data(e.name, e.kind, e.logo, e.description, e.start, e.finish, e.location, e.url, e.tickets, e.videos, e.twitterAccount, e.twitterHashtag, e.tags)
  }

}
