package fr.gospeak.web.user.talks.cfps

import fr.gospeak.core.domain.Talk
import fr.gospeak.libs.scalautils.domain.Markdown
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object CfpForms {

  final case class Create(title: Talk.Title, description: Markdown)

  val create: Form[Create] = Form(mapping(
    "title" -> talkTitle,
    "description" -> markdown
  )(Create.apply)(Create.unapply))
}
