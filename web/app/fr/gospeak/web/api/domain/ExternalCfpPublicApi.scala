package fr.gospeak.web.api.domain

import java.time.LocalDateTime

import fr.gospeak.core.domain.ExternalCfp
import play.api.libs.json.{Json, Writes}

case class ExternalCfpPublicApi(id: String,
                                url: String,
                                name: String,
                                description: String,
                                logo: Option[String],
                                begin: Option[LocalDateTime],
                                close: Option[LocalDateTime],
                                eventUrl: Option[String],
                                eventStart: Option[LocalDateTime],
                                eventFinish: Option[LocalDateTime],
                                eventLocation: Option[String],
                                eventTwitterAccount: Option[String],
                                eventTwitterHashtag: Option[String],
                                tags: Seq[String])

object ExternalCfpPublicApi {
  def apply(cfp: ExternalCfp): ExternalCfpPublicApi =
    new ExternalCfpPublicApi(
      id = cfp.id.value,
      url = cfp.url.value,
      name = cfp.name.value,
      description = cfp.description.value,
      logo = cfp.logo.map(_.url.value),
      begin = cfp.begin,
      close = cfp.close,
      eventUrl = cfp.event.url.map(_.value),
      eventStart = cfp.event.start,
      eventFinish = cfp.event.finish,
      eventLocation = cfp.event.location.map(_.value),
      eventTwitterAccount = cfp.event.twitterAccount.map(_.url.value),
      eventTwitterHashtag = cfp.event.twitterHashtag.map(_.url),
      tags = cfp.tags.map(_.value))

  implicit val writes: Writes[ExternalCfpPublicApi] = Json.writes[ExternalCfpPublicApi]
}
