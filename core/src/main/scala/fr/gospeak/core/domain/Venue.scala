package fr.gospeak.core.domain

import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.services.meetup.domain.MeetupVenue
import fr.gospeak.libs.scalautils.domain._

final case class Venue(id: Venue.Id,
                       partner: Partner.Id,
                       contact: Option[Contact.Id],
                       address: GMapPlace,
                       notes: Markdown, // private infos for the group
                       roomSize: Option[Int],
                       refs: Venue.ExtRefs,
                       info: Info) {
  def data: Venue.Data = Venue.Data(this)

  def users: Seq[User.Id] = info.users
}

object Venue {
  def apply(group: Group.Id, data: Data, info: Info): Venue =
    new Venue(Id.generate(), data.partner, data.contact, data.address, data.notes, data.roomSize, ExtRefs(), info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Venue.Id", new Id(_))

  final case class ExtRefs(meetup: Option[MeetupVenue.Ref] = None)

  // to add linked entities when selecting venues
  final case class Full(venue: Venue, partner: Partner, contact: Option[Contact]) {
    def users: Seq[User.Id] = (venue.users ++ partner.users ++ contact.map(_.users).getOrElse(Seq())).distinct

    def id: Id = venue.id

    def address: GMapPlace = venue.address

    def notes: Markdown = venue.notes

    def roomSize: Option[Int] = venue.roomSize

    def refs: ExtRefs = venue.refs

    def data: Data = venue.data

    def info: Info = venue.info
  }

  // to list public venues (venues linked in published events)
  final case class Public(name: Partner.Name,
                          logo: Url,
                          address: GMapPlace,
                          // aggregation fields, should be at the end
                          id: Id,
                          events: Long)

  // to list both public and group venues
  final case class Common(id: Id,
                          name: Partner.Name,
                          logo: Url,
                          address: GMapPlace,
                          events: Long,
                          public: Boolean)

  // to hold form data
  final case class Data(partner: Partner.Id,
                        contact: Option[Contact.Id],
                        address: GMapPlace,
                        notes: Markdown,
                        roomSize: Option[Int],
                        refs: Venue.ExtRefs)

  object Data {
    def apply(venue: Venue): Data = new Data(venue.partner, venue.contact, venue.address, venue.notes, venue.roomSize, venue.refs)
  }

}
