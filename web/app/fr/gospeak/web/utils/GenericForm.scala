package fr.gospeak.web.utils

import fr.gospeak.core.domain.Comment
import fr.gospeak.libs.scalautils.domain.{EmailAddress, Url}
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object GenericForm {
  val embed: Form[Url] = Form(single("url" -> url))

  val invite: Form[EmailAddress] = Form(single(
    "email" -> emailAddress
  ))

  val comment: Form[Comment.Data] = Form(mapping(
    "answers" -> optional(commentId),
    "text" -> nonEmptyText
  )(Comment.Data.apply)(Comment.Data.unapply))
}
