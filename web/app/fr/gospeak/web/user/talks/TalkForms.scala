package fr.gospeak.web.user.talks

import fr.gospeak.core.domain.Talk
import fr.gospeak.web.utils.Mappings._
import play.api.data.Forms._
import play.api.data._

object TalkForms {

  case class Create(slug: Talk.Slug, title: Talk.Title, description: String)

  val create: Form[Create] = Form(mapping(
    "slug" -> talkSlug,
    "title" -> talkTitle,
    "description" -> nonEmptyText
  )(Create.apply)(Create.unapply))
}
