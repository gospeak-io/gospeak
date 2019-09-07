package fr.gospeak.infra.services.meetup

import java.time.ZoneOffset

import cats.effect.IO
import fr.gospeak.core.domain.{Event, Partner, Venue}
import fr.gospeak.core.services.meetup.MeetupSrv
import fr.gospeak.core.services.meetup.domain._
import fr.gospeak.infra.libs.meetup.domain.MeetupLocation
import fr.gospeak.infra.libs.meetup.{MeetupClient, domain => lib}
import fr.gospeak.libs.scalautils.Crypto.AesSecretKey
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Avatar, CustomException, Url}

import scala.util.Try

class MeetupSrvImpl(client: MeetupClient) extends MeetupSrv {
  override def hasSecureCallback: Boolean = client.hasSecureCallback

  override def buildAuthorizationUrl(redirectUri: String): Try[Url] = client.buildAuthorizationUrl(redirectUri)

  override def requestAccessToken(redirectUri: String, code: String, key: AesSecretKey): IO[MeetupToken] =
    client.requestAccessToken(redirectUri, code).flatMap {
      case Right(token) => fromLib(token, key).toIO
      case Left(err) => IO.raiseError(MeetupException.RequestToken(err.format))
    }

  override def getLoggedUser(key: AesSecretKey)(implicit token: MeetupToken): IO[MeetupUser] =
    toLib(token, key).toIO.flatMap(client.getLoggedUser()(_)).flatMap {
      case Right(user) => fromLib(user).toIO
      case Left(err) => IO.raiseError(MeetupException.LoggedUser(err.format))
    }

  override def getGroup(slug: MeetupGroup.Slug, key: AesSecretKey)(implicit token: MeetupToken): IO[MeetupGroup] =
    toLib(token, key).toIO.flatMap(client.getGroup(slug.value)(_)).flatMap {
      case Right(group) => fromLib(group).toIO
      case Left(err) => IO.raiseError(MeetupException.GroupNotFound(slug, err.format))
    }

  override def publish(event: Event,
                       venue: Option[(Partner, Venue)],
                       description: String,
                       draft: Boolean,
                       key: AesSecretKey,
                       creds: MeetupCredentials): IO[(MeetupEvent.Ref, Option[MeetupVenue.Ref])] = {
    toLib(creds, key).toIO.flatMap { implicit token =>
      for {
        orgas <- client.getOrgas(creds.group.value).flatMap(_.toIO(e => MeetupException.CantFetchOrgas(creds.group, e.format)))
        venueId <- venue.map { case (p, v) =>
          v.refs.meetup.map(r => IO.pure(r.venue.value)).getOrElse {
            for {
              location <- client.getLocations(v.address.lat, v.address.lng)
                .flatMap(_.toIO(e => MeetupException.CantFetchLocation(v.address.lat, v.address.lng, e.format)))
                .flatMap(_.headOption.toIO(MeetupException.CantFetchLocation(v.address.lat, v.address.lng, "No location found")))
              created <- client.createVenue(creds.group.value, toLib(p, v, location))
              id <- created.map(_.id).toIO(e => MeetupException.CantCreateVenue(creds.group, event, p, v, e.format))
            } yield id
          }
        }.sequence
        venueRef = venueId.map(id => MeetupVenue.Ref(creds.group, MeetupVenue.Id(id)))
        toCreateEvent = toLib(event, venue.map(_._2), venueId, orgas.map(_.id), description, draft)
        meetupEvent <- event.refs.meetup
          .map(r => client.updateEvent(creds.group.value, r.event.value.toString, toCreateEvent).flatMap(_.toIO(e => MeetupException.CantUpdateEvent(creds.group, event, e.format))))
          .getOrElse(client.createEvent(creds.group.value, toCreateEvent).flatMap(_.toIO(e => MeetupException.CantCreateEvent(creds.group, event, e.format))))
        eventRef <- Try(meetupEvent.id.toLong).map(id => MeetupEvent.Ref(creds.group, MeetupEvent.Id(id))).toIO
      } yield (eventRef, venueRef)
    }
  }

  private def toLib(token: MeetupToken, key: AesSecretKey): Try[lib.MeetupToken.Access] =
    token.accessToken.decode(key).map(lib.MeetupToken.Access)

  private def toLib(creds: MeetupCredentials, key: AesSecretKey): Try[lib.MeetupToken.Access] =
    creds.accessToken.decode(key).map(lib.MeetupToken.Access)

  private def toLib(partner: Partner, venue: Venue, location: MeetupLocation): lib.MeetupVenue.Create =
    lib.MeetupVenue.Create(
      name = partner.name.value,
      address_1 = venue.address.formatted,
      city = location.city,
      state = None,
      country = location.country,
      localized_country_name = location.localized_country_name,
      lat = venue.address.lat,
      lon = venue.address.lng,
      repinned = false,
      visibility = "public")

  private def toLib(event: Event, venue: Option[Venue], venueId: Option[Long], orgaIds: Seq[Long], description: String, isDraft: Boolean): lib.MeetupEvent.Create =
    lib.MeetupEvent.Create(
      name = event.name.value,
      description = description,
      time = event.start.toEpochSecond(venue.map(_.address.zoneOffset).getOrElse(ZoneOffset.UTC)) * 1000,
      publish_status = if (isDraft) "draft" else "published",
      announce = !isDraft,
      // duration = 10800000,
      // venue_visibility = "public",
      venue_id = venueId,
      lat = venue.map(_.address.lat),
      lon = venue.map(_.address.lng),
      how_to_find_us = None,
      rsvp_limit = venue.flatMap(_.roomSize),
      rsvp_close_time = None,
      rsvp_open_time = None,
      event_hosts = orgaIds.headOption.map(_ => orgaIds.take(lib.MeetupEvent.maxHosts).mkString(",")),
      question = None,
      guest_limit = Some(0),
      featured_photo_id = None,
      self_rsvp = true)

  private def fromLib(token: lib.MeetupToken, key: AesSecretKey): Try[MeetupToken] =
    MeetupToken.from(
      accessToken = token.access_token,
      refreshToken = token.refresh_token,
      key = key)

  private def fromLib(user: lib.MeetupUser.Alt): Either[CustomException, MeetupUser] =
    Url.from(user.photo.photo_link).map { avatarUrl =>
      MeetupUser(
        id = MeetupUser.Id(user.id),
        name = user.name,
        avatar = Avatar(avatarUrl, Avatar.Source.Meetup))
    }

  private def fromLib(group: lib.MeetupGroup): Either[CustomException, MeetupGroup] =
    for {
      slug <- MeetupGroup.Slug.from(group.urlname)
      photo <- Url.from(group.group_photo.photo_link)
      link <- Url.from(group.link)
    } yield MeetupGroup(
      id = MeetupGroup.Id(group.id),
      slug = slug,
      name = group.name,
      description = group.description,
      photo = photo,
      link = link,
      city = group.city,
      country = group.country)
}
