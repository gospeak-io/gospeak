package fr.gospeak.web.pages.orga

import fr.gospeak.core.domain.Group
import fr.gospeak.web.utils.Mappings._
import gospeak.libs.scala.domain.{EmailAddress, Markdown}
import play.api.data.Form
import play.api.data.Forms._

object GroupForms {
  val create: Form[Group.Data] = Form(mapping(
    "slug" -> groupSlug,
    "name" -> groupName,
    "logo" -> optional(logo),
    "banner" -> optional(banner),
    "contact" -> optional(emailAddress),
    "website" -> optional(url),
    "description" -> markdown,
    "location" -> optional(gMapPlace),
    "social" -> socialAccounts,
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
