package fr.gospeak.web.pages.orga.partners.contacts

import fr.gospeak.core.domain.Contact
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms.mapping

object ContactForms {
  val create: Form[Contact.Data] = Form(mapping(
    "partner" -> partnerId,
    "first_name" -> contactFirstName,
    "last_name" -> contactLastName,
    "email" -> emailAddress,
    "notes" -> markdown
  )(Contact.Data.apply)(Contact.Data.unapply))
}
