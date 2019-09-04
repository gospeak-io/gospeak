package fr.gospeak.core.services.meetup

import cats.effect.IO
import fr.gospeak.core.domain.{Event, Partner, User, Venue}
import fr.gospeak.core.services.meetup.domain._
import fr.gospeak.libs.scalautils.Crypto.AesSecretKey
import fr.gospeak.libs.scalautils.domain.Url

import scala.util.Try

trait MeetupSrv {
  def hasSecureCallback: Boolean

  def buildAuthorizationUrl(redirectUri: String): Try[Url]

  def requestAccessToken(redirectUri: String, code: String, key: AesSecretKey): IO[MeetupToken]

  def getLoggedUser(key: AesSecretKey)(implicit token: MeetupToken): IO[MeetupUser]

  def getGroup(group: MeetupGroup.Slug, key: AesSecretKey)(implicit token: MeetupToken): IO[MeetupGroup]

  def publish(event: Event,
              venue: Option[(Partner, Venue)],
              description: String,
              draft: Boolean,
              key: AesSecretKey, creds: MeetupCredentials): IO[(MeetupEvent.Ref, Option[MeetupVenue.Ref])]
}
