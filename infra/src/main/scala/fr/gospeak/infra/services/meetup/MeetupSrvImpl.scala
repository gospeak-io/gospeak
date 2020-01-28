package fr.gospeak.infra.services.meetup

import java.time.{Instant, ZoneId}

import cats.effect.IO
import fr.gospeak.core.domain.utils.Constants
import fr.gospeak.core.domain.{Event, Venue}
import fr.gospeak.core.services.meetup.{MeetupSrv, domain => gs}
import fr.gospeak.infra.libs.meetup.MeetupClient
import fr.gospeak.infra.libs.meetup.domain._
import fr.gospeak.infra.services.meetup.MeetupSrvImpl._
import gospeak.libs.scala.Crypto.AesSecretKey
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain._

import scala.util.Try

class MeetupSrvImpl(client: MeetupClient) extends MeetupSrv {
  override def hasSecureCallback: Boolean = client.hasSecureCallback

  override def buildAuthorizationUrl(redirectUri: String): Try[Url] = client.buildAuthorizationUrl(redirectUri)

  override def requestAccessToken(redirectUri: String, code: String, key: AesSecretKey): IO[gs.MeetupToken] =
    client.requestAccessToken(redirectUri, code).flatMap {
      case Right(token) => fromLib(token, key).toIO
      case Left(err) => IO.raiseError(gs.MeetupException.RequestToken(err.format))
    }

  override def getLoggedUser(key: AesSecretKey)(implicit token: gs.MeetupToken): IO[gs.MeetupUser] =
    toLib(token, key).toIO.flatMap(client.getLoggedUser()(_)).flatMap {
      case Right(user) => fromLib(user).toIO
      case Left(err) => IO.raiseError(gs.MeetupException.LoggedUser(err.format))
    }

  override def getGroup(group: gs.MeetupGroup.Slug, key: AesSecretKey)(implicit token: gs.MeetupToken): IO[gs.MeetupGroup] =
    toLib(token, key).toIO.flatMap(client.getGroup(group.value)(_)).flatMap {
      case Right(group) => fromLib(group).toIO
      case Left(err) => IO.raiseError(gs.MeetupException.GroupNotFound(group, err.format))
    }

  override def getEvents(group: gs.MeetupGroup.Slug, key: AesSecretKey, creds: gs.MeetupCredentials): IO[Seq[gs.MeetupEvent]] =
    toLib(creds, key).toIO.flatMap(client.getEvents(group.value)(_)).flatMap {
      case Right(events) => events.map(fromLib).sequence.toIO
      case Left(err) => IO.raiseError(gs.MeetupException.GroupNotFound(group, err.format))
    }

  override def getAttendees(group: gs.MeetupGroup.Slug, event: gs.MeetupEvent.Id, key: AesSecretKey, creds: gs.MeetupCredentials): IO[Seq[gs.MeetupAttendee]] =
    toLib(creds, key).toIO.flatMap(client.getEventAttendees(group.value, event.value)(_)).flatMap {
      case Right(attendees) => attendees.map(fromLib).sequence.toIO
      case Left(err) => IO.raiseError(gs.MeetupException.EventNotFound(group, event, err.format))
    }

  override def publish(event: Event,
                       venue: Option[Venue.Full],
                       description: Markdown,
                       draft: Boolean,
                       key: AesSecretKey,
                       creds: gs.MeetupCredentials): IO[(gs.MeetupEvent.Ref, Option[gs.MeetupVenue.Ref])] = {
    toLib(creds, key).toIO.flatMap { implicit token =>
      for {
        group <- client.getGroup(creds.group.value).flatMap(_.toIO(e => gs.MeetupException.GroupNotFound(creds.group, e.format)))
        timezone = Try(ZoneId.of(group.timezone)).getOrElse(Constants.defaultZoneId)
        orgas <- client.getOrgas(creds.group.value).flatMap(_.toIO(e => gs.MeetupException.CantFetchOrgas(creds.group, e.format)))
        venueId <- venue.map { v =>
          v.refs.meetup.map(r => IO.pure(r.venue.value)).getOrElse {
            for {
              location <- client.getLocations(v.address.geo)
                .flatMap(_.toIO(e => gs.MeetupException.CantFetchLocation(v.address.geo, e.format)))
                .flatMap(_.headOption.toIO(gs.MeetupException.CantFetchLocation(v.address.geo, "No location found")))
              created <- client.createVenue(creds.group.value, toLib(v, location))
              id <- created.map(_.id).toIO(e => gs.MeetupException.CantCreateVenue(creds.group, event, v, e.format))
            } yield id
          }
        }.sequence
        venueRef = venueId.map(id => gs.MeetupVenue.Ref(creds.group, gs.MeetupVenue.Id(id)))
        toCreateEvent = toLib(event, timezone, venue, venueId, orgas.map(_.id), description, draft)
        meetupEvent <- event.refs.meetup
          .map(r => client.updateEvent(creds.group.value, r.event.value, toCreateEvent).flatMap(_.toIO(e => gs.MeetupException.CantUpdateEvent(creds.group, event, e.format))))
          .getOrElse(client.createEvent(creds.group.value, toCreateEvent).flatMap(_.toIO(e => gs.MeetupException.CantCreateEvent(creds.group, event, e.format))))
        eventRef <- Try(meetupEvent.id).map(id => gs.MeetupEvent.Ref(creds.group, gs.MeetupEvent.Id(id))).toIO
      } yield (eventRef, venueRef)
    }
  }

  private def toLib(token: gs.MeetupToken, key: AesSecretKey): Try[MeetupToken.Access] =
    token.accessToken.decode(key).map(MeetupToken.Access)

  private def toLib(creds: gs.MeetupCredentials, key: AesSecretKey): Try[MeetupToken.Access] =
    creds.accessToken.decode(key).map(MeetupToken.Access)

  private def toLib(venue: Venue.Full, location: MeetupLocation): MeetupVenue.Create =
    MeetupVenue.Create(
      name = venue.partner.name.value,
      address_1 = venue.address.formatted,
      city = location.city,
      state = None,
      country = location.country,
      localized_country_name = location.localized_country_name,
      lat = venue.address.geo.lat,
      lon = venue.address.geo.lng,
      repinned = false,
      visibility = "public")

  private def toLib(event: Event, tz: ZoneId, venue: Option[Venue.Full], venueId: Option[Long], orgaIds: Seq[Long], description: Markdown, isDraft: Boolean): MeetupEvent.Create =
    MeetupEvent.Create(
      name = event.name.value,
      description = toSimpleHtml(description),
      time = event.start.toInstant(tz).toEpochMilli,
      publish_status = if (isDraft) "draft" else "published",
      announce = !isDraft,
      // duration = 10800000,
      // venue_visibility = "public",
      venue_id = venueId,
      lat = venue.map(_.address.geo.lat),
      lon = venue.map(_.address.geo.lng),
      how_to_find_us = None,
      rsvp_limit = venue.flatMap(_.roomSize),
      rsvp_close_time = None,
      rsvp_open_time = None,
      event_hosts = orgaIds.headOption.map(_ => orgaIds.take(MeetupEvent.maxHosts).mkString(",")),
      question = None,
      guest_limit = Some(0),
      featured_photo_id = None,
      self_rsvp = true)

  private def fromLib(token: MeetupToken, key: AesSecretKey): Try[gs.MeetupToken] =
    gs.MeetupToken.from(
      accessToken = token.access_token,
      refreshToken = token.refresh_token,
      key = key)

  private def fromLib(user: MeetupUser.Alt): Either[CustomException, gs.MeetupUser] =
    Url.from(user.photo.photo_link).map { avatarUrl =>
      gs.MeetupUser(
        id = gs.MeetupUser.Id(user.id),
        name = user.name,
        avatar = Avatar(avatarUrl))
    }

  private def fromLib(group: MeetupGroup): Either[CustomException, gs.MeetupGroup] =
    for {
      slug <- gs.MeetupGroup.Slug.from(group.urlname)
      photo <- Url.from(group.group_photo.photo_link)
      link <- Url.from(group.link)
    } yield gs.MeetupGroup(
      id = gs.MeetupGroup.Id(group.id),
      slug = slug,
      name = group.name,
      description = group.description,
      photo = photo,
      link = link,
      city = group.city,
      country = group.country)

  private def fromLib(event: MeetupEvent): Either[CustomException, gs.MeetupEvent] =
    for {
      venue <- event.venue.map(fromLib).sequence
    } yield gs.MeetupEvent(
      id = event.id,
      name = event.name,
      status = event.status,
      visibility = event.visibility,
      description = event.description,
      venue = venue,
      rsvp_limit = event.rsvp_limit,
      created = Instant.ofEpochMilli(event.created))

  private def fromLib(venue: MeetupVenue.Basic): Either[CustomException, gs.MeetupVenue] =
    Right(gs.MeetupVenue(
      id = venue.id,
      name = venue.name,
      address = venue.address_1,
      city = venue.city,
      country = venue.country,
      geo = Geo(venue.lat, venue.lon)))

  private def fromLib(attendee: MeetupAttendee): Either[CustomException, gs.MeetupAttendee] =
    for {
      avatarUrl <- attendee.member.photo.map(_.photo_link).map(Url.from).sequence
    } yield gs.MeetupAttendee(
      id = gs.MeetupUser.Id(attendee.member.id),
      name = attendee.member.name,
      bio = attendee.member.bio,
      avatar = avatarUrl.map(Avatar),
      host = attendee.member.event_context.exists(_.host),
      response = attendee.rsvp.response,
      guests = attendee.rsvp.guests,
      updated = Instant.ofEpochMilli(attendee.rsvp.updated))
}

object MeetupSrvImpl {
  // cf https://www.meetup.com/fr-FR/meetup_api/docs/:urlname/events#create
  private[meetup] def toSimpleHtml(md: Markdown): String = {
    md.value
      .replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>") // bold
      .replaceAll("\\*([^*]+)\\*", "<i>$1</i>") // italic
      .replaceAll("!\\[([^]]*)]\\(([^)]+)\\)", "$2") // images
      .replaceAll("\\[([^]]*)]\\(([^)]+)\\)", "$2") // links
  }
}
