package fr.gospeak.migration.domain

import fr.gospeak.migration.domain.utils.{GMapPlace, MeetupRef, Meta}

case class Partner(id: String, // Partner.Id
                   meetupRef: Option[MeetupRef],
                   data: PartnerData,
                   meta: Meta)

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
