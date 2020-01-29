package gospeak.web.pages.orga.partners

import gospeak.core.domain.utils.SocialAccounts
import gospeak.core.domain.{Contact, Partner, Venue}
import gospeak.web.utils.Mappings._
import gospeak.libs.scala.domain.{GMapPlace, Logo, Markdown}
import play.api.data.Form
import play.api.data.Forms._

object PartnerForms {
  val create: Form[Partner.Data] = Form(mapping(
    "slug" -> partnerSlug,
    "name" -> partnerName,
    "notes" -> markdown,
    "description" -> optional(markdown),
    "logo" -> logo,
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
                                    logo: Logo,
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
    "logo" -> logo,
    "address" -> gMapPlace
  )(VenuePartnerData.apply)(VenuePartnerData.unapply))
}
