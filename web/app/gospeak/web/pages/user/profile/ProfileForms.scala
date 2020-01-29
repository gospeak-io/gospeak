package gospeak.web.pages.user.profile

import gospeak.core.domain.User
import gospeak.web.utils.Mappings._
import gospeak.libs.scala.domain.Values
import play.api.data.Form
import play.api.data.Forms._

object ProfileForms {
  val create: Form[User.Data] = Form(mapping(
    "slug" -> userSlug,
    "status" -> userStatus,
    "first-name" -> text(1, Values.maxLength.title),
    "last-name" -> text(1, Values.maxLength.title),
    "email" -> emailAddress,
    "avatar" -> avatar,
    "bio" -> optional(markdown),
    "company" -> optional(text),
    "location" -> optional(text),
    "phone" -> optional(text),
    "website" -> optional(url),
    "social" -> socialAccounts
  )(User.Data.apply)(User.Data.unapply))
}
