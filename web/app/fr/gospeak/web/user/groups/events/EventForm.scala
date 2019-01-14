package fr.gospeak.web.user.groups.events

import fr.gospeak.core.domain.Event
import fr.gospeak.web.utils.Mappings._
import play.api.data.Forms._
import play.api.data._

object EventForm {

  case class Create(name: Event.Name, slug: Event.Slug)

  val create: Form[Create] = Form(mapping(
    "name" -> eventName,
    "slug" -> eventSlug
  )(Create.apply)(Create.unapply))
}
