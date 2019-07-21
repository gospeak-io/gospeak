package fr.gospeak.migration.domain

import java.time.LocalDate

import fr.gospeak.core.domain.{Group, SponsorPack, Partner => NewPartner, Sponsor => NewSponsor, Venue => NewVenue}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.StringUtils
import fr.gospeak.libs.scalautils.domain.{Markdown, Url}
import fr.gospeak.migration.domain.utils.{GMapPlace, MeetupRef, Meta}

case class Partner(id: String, // Partner.Id
                   meetupRef: Option[MeetupRef],
                   data: PartnerData,
                   meta: Meta) {
  def toPartner(group: Group.Id): NewPartner = NewPartner(
    id = NewPartner.Id.from(id).get,
    group = group,
    slug = NewPartner.Slug.from(StringUtils.slugify(data.name)).get,
    name = NewPartner.Name(data.name),
    // data.twitter // FIXME
    description = Markdown(data.comment.getOrElse("")),
    logo = Url.from(data.logo.getOrElse("https://img.icons8.com/bubbles/2x/company.png")).get,
    // data.contacts // FIXME
    info = meta.toInfo)

  def toVenue: Option[NewVenue] = data.venue.map(venue => NewVenue(
    id = NewVenue.Id.generate(),
    partner = NewPartner.Id.from(id).get,
    address = venue.location.toGMapPlace,
    // closeTime // FIXME
    // attendeeList // FIXME
    // entranceCheck // FIXME
    // offeredAperitif // FIXME
    description = Markdown(venue.comment.getOrElse("")),
    roomSize = None, // venue.capacity.map(_.toInt),
    // contact // FIXME
    info = meta.toInfo))

  def toSponsors(group: Group.Id, packs: Seq[SponsorPack]): Seq[NewSponsor] = data.sponsoring.map(sponsor => NewSponsor(
    id = NewSponsor.Id.generate(),
    group = group,
    partner = NewPartner.Id.from(id).get,
    pack = packs.find(_.name.value == sponsor.level).get.id,
    // contact: Option[Contact.Id], // FIXME
    start = LocalDate.parse(sponsor.start),
    finish = LocalDate.parse(sponsor.end),
    paid = Some(LocalDate.parse(sponsor.start)),
    price = packs.find(_.name.value == sponsor.level).get.price,
    info = meta.toInfo))
}

case class PartnerData(name: String,
                       twitter: Option[String],
                       logo: Option[String],
                       contacts: List[String], // List[Person.Id],
                       venue: Option[Venue],
                       sponsoring: List[Sponsor],
                       sponsorAperitif: Boolean,
                       comment: Option[String])

case class Venue(location: GMapPlace,
                 // capacity: Option[Int],
                 closeTime: Option[String], // Option[LocalTime],
                 attendeeList: Option[Boolean],
                 entranceCheck: Option[Boolean],
                 offeredAperitif: Option[Boolean],
                 contact: Option[String], // Option[Person.Id],
                 comment: Option[String])

case class Sponsor(start: String, // LocalDate,
                   end: String, // LocalDate,
                   level: String, // SponsorLevel.Value, (Standard, Premium)
                   contact: Option[String]) // Option[Person.Id])
