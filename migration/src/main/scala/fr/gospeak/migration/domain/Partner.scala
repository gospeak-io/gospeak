package fr.gospeak.migration.domain

import java.time.LocalDate

import fr.gospeak.core.services.meetup.domain.{MeetupGroup, MeetupVenue}
import fr.gospeak.core.{domain => gs}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.StringUtils
import fr.gospeak.libs.scalautils.domain.{Markdown, Url}
import fr.gospeak.migration.domain.utils.{GMapPlace, MeetupRef, Meta}

case class Partner(id: String, // Partner.Id
                   meetupRef: Option[MeetupRef],
                   data: PartnerData,
                   meta: Meta) {
  def toPartner(group: gs.Group.Id): gs.Partner = gs.Partner(
    id = gs.Partner.Id.from(id).get,
    group = group,
    slug = gs.Partner.Slug.from(StringUtils.slugify(data.name)).get,
    name = gs.Partner.Name(data.name),
    notes = Markdown(data.comment.getOrElse("")),
    description = None,
    logo = Url.from(data.logo.getOrElse("https://img.icons8.com/bubbles/2x/company.png")).get,
    twitter = data.twitter.map(t => Url.from("https://twitter.com/" + t).get),
    info = meta.toInfo)

  def toVenue: Option[gs.Venue] = data.venue.map(venue => gs.Venue(
    id = gs.Venue.Id.generate(),
    partner = gs.Partner.Id.from(id).get,
    address = venue.location.toGMapPlace,
    description = Markdown(venue.comment.getOrElse("")),
    roomSize = venue.capacity,
    refs = gs.Venue.ExtRefs(
      meetup = meetupRef.map(r => MeetupVenue.Ref(MeetupGroup.Slug.from(r.group).get, MeetupVenue.Id(r.id)))),
    info = meta.toInfo))

  def toSponsors(group: gs.Group.Id, packs: Seq[gs.SponsorPack]): Seq[gs.Sponsor] = data.sponsoring.map(sponsor => gs.Sponsor(
    id = gs.Sponsor.Id.generate(),
    group = group,
    partner = gs.Partner.Id.from(id).get,
    pack = packs.find(_.name.value == sponsor.level).get.id,
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
                 capacity: Option[Int],
                 closeTime: Option[String], // Option[LocalTime],
                 attendeeList: Option[Boolean],
                 entranceCheck: Option[Boolean],
                 offeredAperitif: Option[Boolean],
                 contact: Option[String], // Option[Person.Id], // FIXME add to gospeak ???
                 comment: Option[String])

case class Sponsor(start: String, // LocalDate,
                   end: String, // LocalDate,
                   level: String, // SponsorLevel.Value, (Standard, Premium)
                   contact: Option[String]) // Option[Person.Id]) // FIXME add to gospeak ???
