package gospeak.web.utils

import gospeak.core.domain.Comment
import gospeak.web.utils.Mappings._
import gospeak.libs.scala.domain.{EmailAddress, Url}
import play.api.data.Form
import play.api.data.Forms._

object GenericForm {
  val embed: Form[Url] = Form(single(
    "url" -> url))

  val invite: Form[EmailAddress] = Form(single(
    "email" -> emailAddress))

  val comment: Form[Comment.Data] = Form(mapping(
    "answers" -> optional(commentId),
    "text" -> nonEmptyText
  )(Comment.Data.apply)(Comment.Data.unapply))
}
