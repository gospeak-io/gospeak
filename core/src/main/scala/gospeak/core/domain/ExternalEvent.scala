package gospeak.core.domain

import java.time.LocalDateTime

import gospeak.core.domain.utils.Info
import gospeak.core.domain.utils.SocialAccounts.SocialAccount.TwitterAccount
import gospeak.libs.scala.domain._

final case class ExternalEvent(id: ExternalEvent.Id,
                               name: ExternalEvent.Name,
                               logo: Option[Logo],
                               description: Markdown,
                               start: LocalDateTime,
                               finish: Option[LocalDateTime],
                               location: Option[GMapPlace],
                               url: Url,
                               tickets: Option[Url],
                               videos: Option[Url],
                               twitterAccount: Option[TwitterAccount],
                               twitterHashtag: Option[TwitterHashtag],
                               tags: Seq[Tag],
                               info: Info)

object ExternalEvent {

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("ExternalEvent.Id", new Id(_))

  final case class Name(value: String) extends AnyVal

}
