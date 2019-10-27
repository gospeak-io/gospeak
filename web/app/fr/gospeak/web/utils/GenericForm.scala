package fr.gospeak.web.utils

import fr.gospeak.libs.scalautils.domain.{EmailAddress, Url}
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms.single

object GenericForm {
  val embed: Form[Url] = Form(single("url" -> url))

  val invite: Form[EmailAddress] = Form(single(
    "email" -> emailAddress
  ))
}
