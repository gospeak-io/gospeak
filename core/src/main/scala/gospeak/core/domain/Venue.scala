package gospeak.core.domain

import gospeak.core.domain.utils.Info
import gospeak.core.services.meetup.domain.MeetupVenue
import gospeak.libs.scala.domain._

final case class Venue(id: Venue.Id,
                       partner: Partner.Id,
                       contact: Option[Contact.Id],
                       address: GMapPlace,
                       notes: Markdown, // private infos for the group
                       roomSize: Option[Int],
                       refs: Venue.ExtRefs,
                       info: Info) {
  def data: Venue.Data = Venue.Data(this)

  def users: List[User.Id] = info.users
}

object Venue {
  def apply(group: Group.Id, partner: Partner.Id, data: Data, info: Info): Venue =
    new Venue(Id.generate(), partner, data.contact, data.address, data.notes, data.roomSize, ExtRefs(), info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Venue.Id", new Id(_))

  final case class ExtRefs(meetup: Option[MeetupVenue.Ref] = None)

  // to add linked entities when selecting venues
  final case class Full(venue: Venue, partner: Partner, contact: Option[Contact]) {
    def users: List[User.Id] = (venue.users ++ partner.users ++ contact.map(_.users).getOrElse(List())).distinct

    def id: Id = venue.id

    def address: GMapPlace = venue.address

    def notes: Markdown = venue.notes

    def roomSize: Option[Int] = venue.roomSize

    def refs: ExtRefs = venue.refs

    def data: Data = venue.data

    def info: Info = venue.info

    def hasContact(id: Contact.Id): Boolean = contact.exists(_.id == id)
  }

  // to list public venues (venues linked in published events)
  final case class Public(slug: Partner.Slug,
                          name: Partner.Name,
                          logo: Logo,
                          address: GMapPlace,
                          // aggregation fields, should be at the end
                          id: Id,
                          events: Long)

  object Public {
    def apply(v: Venue.Full, events: Long): Public = new Public(
      slug = v.partner.slug,
      name = v.partner.name,
      logo = v.partner.logo,
      address = v.address,
      id = v.id,
      events = events)
  }

  // to list both public and group venues
  final case class Common(id: Id,
                          slug: Partner.Slug,
                          name: Partner.Name,
                          logo: Logo,
                          address: GMapPlace,
                          events: Long,
                          public: Boolean)

  object Common {
    def apply(v: Venue.Full, events: Long, public: Boolean): Common = new Common(
      id = v.id,
      slug = v.partner.slug,
      name = v.partner.name,
      logo = v.partner.logo,
      address = v.address,
      events = events,
      public = public)
  }

  // to hold form data
  final case class Data(contact: Option[Contact.Id],
                        address: GMapPlace,
                        notes: Markdown,
                        roomSize: Option[Int],
                        refs: Venue.ExtRefs)

  object Data {
    def apply(venue: Venue): Data = new Data(venue.contact, venue.address, venue.notes, venue.roomSize, venue.refs)
  }

}
