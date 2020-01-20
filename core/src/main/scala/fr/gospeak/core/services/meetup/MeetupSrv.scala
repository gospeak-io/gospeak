package fr.gospeak.core.services.meetup

import cats.effect.IO
import fr.gospeak.core.domain.{Event, Venue}
import fr.gospeak.core.services.meetup.domain._
import fr.gospeak.libs.scalautils.Crypto.AesSecretKey
import fr.gospeak.libs.scalautils.domain.{Markdown, Url}

import scala.util.Try

trait MeetupSrv {
  def hasSecureCallback: Boolean

  def buildAuthorizationUrl(redirectUri: String): Try[Url]

  def requestAccessToken(redirectUri: String, code: String, key: AesSecretKey): IO[MeetupToken]

  def getLoggedUser(key: AesSecretKey)(implicit token: MeetupToken): IO[MeetupUser]

  def getGroup(group: MeetupGroup.Slug, key: AesSecretKey)(implicit token: MeetupToken): IO[MeetupGroup]

  def getAttendees(group: MeetupGroup.Slug, event: MeetupEvent.Id, key: AesSecretKey, creds: MeetupCredentials): IO[Seq[MeetupAttendee]]

  def publish(event: Event,
              venue: Option[Venue.Full],
              description: Markdown,
              draft: Boolean,
              key: AesSecretKey, creds: MeetupCredentials): IO[(MeetupEvent.Ref, Option[MeetupVenue.Ref])]
}
