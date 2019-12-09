package fr.gospeak.web.pages.orga.venues

import fr.gospeak.core.domain.utils.SocialAccounts
import fr.gospeak.core.domain.{Partner, Venue}
import fr.gospeak.libs.scalautils.domain.{GMapPlace, Markdown, Url}
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms.{mapping, number, optional}

object VenueForms {
  val create: Form[Venue.Data] = Form(mapping(
    "partner" -> partnerId,
    "contact" -> optional(contactId),
    "address" -> gMapPlace,
    "notes" -> markdown,
    "roomSize" -> optional(number),
    "refs" -> venueRefs
  )(Venue.Data.apply)(Venue.Data.unapply))

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

    def toVenue(partner: Partner.Id): Venue.Data = Venue.Data(
      partner = partner,
      contact = None,
      address = address,
      notes = Markdown(""),
      roomSize = None,
      refs = Venue.ExtRefs())
  }

  val createWithPartner: Form[VenuePartnerData] = Form(mapping(
    "slug" -> partnerSlug,
    "name" -> partnerName,
    "logo" -> url,
    "address" -> gMapPlace
  )(VenuePartnerData.apply)(VenuePartnerData.unapply))
}
