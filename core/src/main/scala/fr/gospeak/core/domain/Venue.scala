package fr.gospeak.core.domain

import java.time.ZoneId

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

  final case class Full(venue: Venue, partner: Partner, contact: Option[Contact]) {
    def users: Seq[User.Id] = (venue.users ++ partner.users ++ contact.map(_.users).getOrElse(Seq())).distinct

    def id: Id = venue.id

    def address: GMapPlace = venue.address

    def notes: Markdown = venue.notes

    def roomSize: Option[Int] = venue.roomSize

    def timezone: ZoneId = venue.address.timezone

    def refs: ExtRefs = venue.refs

    def data: Data = venue.data

    def info: Info = venue.info
  }

  final case class Public(name: Partner.Name,
                          logo: Url,
                          address: GMapPlace,
                          id: Id,
                          events: Long)

  final case class Public1(venue: Venue, partner: Partner, contact: Option[Contact], group: Group, event: Event)

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
