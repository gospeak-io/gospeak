package gospeak.core.services.meetup.domain

import gospeak.core.domain.{Event, Venue}
import gospeak.libs.scala.domain.Geo

sealed abstract class MeetupException(msg: String) extends Exception(msg)

object MeetupException {

  final case class RequestToken(error: String) extends MeetupException(s"Meetup API error while requesting access_token: $error")

  final case class LoggedUser(error: String) extends MeetupException(s"Meetup API error while fetching logged user: $error")

  final case class GroupNotFound(slug: MeetupGroup.Slug,
                                 error: String) extends MeetupException(s"Meetup group '${slug.value}' not found: $error")

  final case class EventNotFound(slug: MeetupGroup.Slug,
                                 id: MeetupEvent.Id,
                                 error: String) extends MeetupException(s"Meetup event ${id.value} in group '${slug.value}' not found: $error")

  final case class CantFetchLocation(geo: Geo,
                                     error: String) extends MeetupException(s"Unable to fetch meetup locations for $geo: $error")

  final case class CantFetchOrgas(slug: MeetupGroup.Slug,
                                  error: String) extends MeetupException(s"Unable to fetch meetup orgas for group '${slug.value}': $error")

  final case class CantCreateVenue(slug: MeetupGroup.Slug,
                                   event: Event,
                                   venue: Venue.Full,
                                   error: String) extends MeetupException(
    s"Unable to create meetup venue '${venue.partner.name.value}' (${venue.address.formatted}) for event '${event.name.value}': $error")

  final case class CantCreateEvent(slug: MeetupGroup.Slug,
                                   event: Event,
                                   error: String) extends MeetupException(s"Unable to create meetup event '${event.name.value}': $error")

  final case class CantUpdateEvent(slug: MeetupGroup.Slug,
                                   event: Event,
                                   error: String) extends MeetupException(s"Unable to update meetup event '${event.name.value}': $error")

}
