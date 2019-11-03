package fr.gospeak.web.pages.orga

import fr.gospeak.core.domain.Group
import fr.gospeak.infra.libs.timeshape.TimeShape
import fr.gospeak.libs.scalautils.domain.{EmailAddress, Markdown}
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object GroupForms {
  def create(timeShape: TimeShape): Form[Group.Data] = Form(mapping(
    "slug" -> groupSlug,
    "name" -> groupName,
    "contact" -> optional(emailAddress),
    "description" -> markdown,
    "location" -> optional(gMapPlace(timeShape)),
    "tags" -> tags
  )(Group.Data.apply)(Group.Data.unapply))

  final case class ContactMembers(from: EmailAddress,
                                  subject: String,
                                  content: Markdown)

  val contactMembers: Form[ContactMembers] = Form(mapping(
    "from" -> emailAddress,
    "subject" -> nonEmptyText,
    "content" -> markdown
  )(ContactMembers.apply)(ContactMembers.unapply))
}
