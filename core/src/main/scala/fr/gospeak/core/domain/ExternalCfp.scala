package fr.gospeak.core.domain

import java.time.LocalDateTime

import fr.gospeak.core.domain.utils.Info
import fr.gospeak.libs.scalautils.domain._

case class ExternalCfp(id: ExternalCfp.Id,
                       name: ExternalCfp.Name,
                       logo: Option[Logo],
                       description: Markdown,
                       begin: Option[LocalDateTime],
                       close: Option[LocalDateTime],
                       url: Url,
                       event: ExternalCfp.Event,
                       tags: Seq[Tag],
                       info: Info) {
  def data: ExternalCfp.Data = ExternalCfp.Data(this)
}

object ExternalCfp {
  def apply(data: Data, info: Info): ExternalCfp =
    new ExternalCfp(Id.generate(), data.name, data.logo, data.description, data.begin, data.close, data.url, data.event, data.tags, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("ExternalCfp.Id", new Id(_))

  final case class Name(value: String) extends AnyVal

  final case class Event(start: Option[LocalDateTime],
                         finish: Option[LocalDateTime],
                         url: Option[Url],
                         location: Option[GMapPlace],
                         tickets: Option[Url],
                         videos: Option[Url],
                         twitterAccount: Option[TwitterAccount],
                         twitterHashtag: Option[TwitterHashtag])

  final case class Data(name: ExternalCfp.Name,
                        logo: Option[Logo],
                        description: Markdown,
                        begin: Option[LocalDateTime],
                        close: Option[LocalDateTime],
                        url: Url,
                        event: Event,
                        tags: Seq[Tag])

  object Data {
    def apply(cfp: ExternalCfp): Data = new Data(cfp.name, cfp.logo, cfp.description, cfp.begin, cfp.close, cfp.url, cfp.event, cfp.tags)
  }

}
