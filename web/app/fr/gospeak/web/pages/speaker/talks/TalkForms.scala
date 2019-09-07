package fr.gospeak.web.pages.speaker.talks

import fr.gospeak.core.domain.Talk
import fr.gospeak.libs.scalautils.domain.EmailAddress
import fr.gospeak.web.utils.Mappings._
import play.api.data.{Form, Mapping}
import play.api.data.Forms._

object TalkForms {
  val talkMappings: Mapping[Talk.Data] = mapping(
    "slug" -> talkSlug,
    "title" -> talkTitle,
    "duration" -> duration,
    "description" -> markdown,
    "slides" -> optional(slides),
    "video" -> optional(video),
    "tags" -> tags
  )(Talk.Data.apply)(Talk.Data.unapply)
  val create: Form[Talk.Data] = Form(talkMappings)

  val addSpeaker: Form[EmailAddress] = Form(single(
    "email" -> emailAddress
  ))
}
