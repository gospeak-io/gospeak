package gospeak.web.api.domain

import java.time.LocalDateTime

import gospeak.core.domain.ExternalCfp
import gospeak.core.domain.utils.BasicCtx
import play.api.libs.json.{Json, Writes}

object ApiExternalCfp {

  // data to display publicly
  final case class Published(id: String,
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
                             tags: List[String])

  object Published {
    implicit val writes: Writes[Published] = Json.writes[Published]
  }

  def published(cfp: ExternalCfp.Full)(implicit ctx: BasicCtx): Published =
    new Published(
      id = cfp.id.value,
      url = cfp.url.value,
      name = cfp.event.name.value,
      description = cfp.description.value,
      logo = cfp.event.logo.map(_.url.value),
      begin = cfp.begin,
      close = cfp.close,
      eventUrl = cfp.event.url.map(_.value),
      eventStart = cfp.event.start,
      eventFinish = cfp.event.finish,
      eventLocation = cfp.event.location.map(_.value),
      eventTwitterAccount = cfp.event.twitterAccount.map(_.url.value),
      eventTwitterHashtag = cfp.event.twitterHashtag.map(_.url),
      tags = cfp.event.tags.map(_.value))

}
