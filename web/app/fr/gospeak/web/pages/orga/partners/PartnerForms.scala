package fr.gospeak.web.pages.orga.partners

import fr.gospeak.core.domain.utils.SocialAccounts
import fr.gospeak.core.domain.{Contact, Partner, Venue}
import fr.gospeak.libs.scalautils.domain.{GMapPlace, Markdown, Url}
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object PartnerForms {
  val create: Form[Partner.Data] = Form(mapping(
    "slug" -> partnerSlug,
    "name" -> partnerName,
    "notes" -> markdown,
    "description" -> optional(markdown),
    "logo" -> url,
    "social" -> socialAccounts
  )(Partner.Data.apply)(Partner.Data.unapply))

  val createVenue: Form[Venue.Data] = Form(mapping(
    "contact" -> optional(contactId),
    "address" -> gMapPlace,
    "notes" -> markdown,
    "roomSize" -> optional(number),
    "refs" -> venueRefs
  )(Venue.Data.apply)(Venue.Data.unapply))

  val createContact: Form[Contact.Data] = Form(mapping(
    "partner" -> partnerId,
    "first_name" -> contactFirstName,
    "last_name" -> contactLastName,
    "email" -> emailAddress,
    "notes" -> markdown
  )(Contact.Data.apply)(Contact.Data.unapply))

  final case class VenuePartnerData(slug: Partner.Slug,
                                    name: Partner.Name,
                                    logo: Url,
                                    address: GMapPlace) {
    def toPartner: Partner.Data = Partner.Data(
      slug = slug,
      name = name,
      notes = Markdown(""),
      description = None,
      logo = logo,
      social = SocialAccounts.fromUrls())

    def toVenue: Venue.Data = Venue.Data(
      contact = None,
      address = address,
      notes = Markdown(""),
      roomSize = None,
      refs = Venue.ExtRefs())
  }

  val createVenueWithPartner: Form[VenuePartnerData] = Form(mapping(
    "slug" -> partnerSlug,
    "name" -> partnerName,
    "logo" -> url,
    "address" -> gMapPlace
  )(VenuePartnerData.apply)(VenuePartnerData.unapply))
}
