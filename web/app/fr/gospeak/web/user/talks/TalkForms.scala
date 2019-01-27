package fr.gospeak.web.user.talks

import fr.gospeak.core.domain.Talk
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

import scala.concurrent.duration.FiniteDuration

object TalkForms {

  final case class Create(slug: Talk.Slug, title: Talk.Title, duration: FiniteDuration, description: String)

  val create: Form[Create] = Form(mapping(
    "slug" -> talkSlug,
    "title" -> talkTitle,
    "duration" -> duration,
    "description" -> nonEmptyText
  )(Create.apply)(Create.unapply))
}
