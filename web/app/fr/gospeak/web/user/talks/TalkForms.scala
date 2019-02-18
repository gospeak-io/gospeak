package fr.gospeak.web.user.talks

import fr.gospeak.core.domain.Talk
import fr.gospeak.libs.scalautils.domain.Url
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object TalkForms {
  val create: Form[Talk.Data] = Form(mapping(
    "slug" -> talkSlug,
    "title" -> talkTitle,
    "duration" -> duration,
    "description" -> markdown,
    "slides" -> optional(slides),
    "video" -> optional(video)
  )(Talk.Data.apply)(Talk.Data.unapply))

  val embed: Form[Url] = Form(single("url" -> url))
}
