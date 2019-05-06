package fr.gospeak.web.pages.speaker

import fr.gospeak.core.domain.User
import fr.gospeak.core.domain.User.EditableFields
import play.api.data.Form
import play.api.data.Forms._
import fr.gospeak.web.utils.Mappings._

object ProfileForms {
  val create: Form[User.EditableFields] = Form(mapping(
    "first-name" -> text(1, 30),
    "last-name" -> text(1, 30),
    "email" -> emailAddress,
//    "avatar" ->  optional(url),
    "description" -> optional(text),
    "shirt" -> optional(shirtSize),
    "company" -> optional(text),
    "location" -> optional(text),
    "twitter" -> optional(url),
    "linkedin" -> optional(url),
    "phone" -> optional(text),
    "web-site" -> optional(url)
  )(EditableFields.apply)(EditableFields.unapply))
}
