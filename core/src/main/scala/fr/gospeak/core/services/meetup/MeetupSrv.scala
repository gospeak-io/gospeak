package fr.gospeak.core.services.meetup

import cats.effect.IO
import fr.gospeak.core.services.meetup.domain.{MeetupGroup, MeetupToken, MeetupUser}
import fr.gospeak.libs.scalautils.domain.Url

import scala.util.Try

trait MeetupSrv {
  def buildAuthorizationUrl(redirectUri: String): Try[Url]

  def requestAccessToken(redirectUri: String, code: String): IO[MeetupToken]

  def getLoggedUser()(implicit token: MeetupToken): IO[MeetupUser]

  def getGroup(group: MeetupGroup.Slug)(implicit token: MeetupToken): IO[MeetupGroup]
}
