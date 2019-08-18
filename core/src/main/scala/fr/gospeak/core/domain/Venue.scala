package fr.gospeak.core.domain

import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.services.meetup.domain.MeetupVenue
import fr.gospeak.libs.scalautils.domain._

final case class Venue(id: Venue.Id,
                       partner: Partner.Id,
                       address: GMapPlace,
                       description: Markdown,
                       roomSize: Option[Int],
                       refs: Venue.ExtRefs,
                       info: Info) {
  def data: Venue.Data = Venue.Data(this)

  def users: Seq[User.Id] = info.users
}

object Venue {
  def apply(group: Group.Id, data: Data, info: Info): Venue =
    new Venue(Id.generate(), data.partner, data.address, data.description, data.roomSize, ExtRefs(), info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Venue.Id", new Id(_))

  final case class ExtRefs(meetup: Option[MeetupVenue.Ref] = None)

  final case class Data(partner: Partner.Id,
                        address: GMapPlace,
                        description: Markdown,
                        roomSize: Option[Int])

  object Data {
    def apply(venue: Venue): Data = new Data(venue.partner, venue.address, venue.description, venue.roomSize)
  }

}
