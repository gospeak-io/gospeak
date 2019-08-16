package fr.gospeak.core.services.meetup

import cats.effect.IO
import fr.gospeak.core.services.meetup.domain.{MeetupInfo, MeetupToken, MeetupUser}

import scala.util.Try

trait MeetupSrv {
  def getInfo(redirectUri: String): Try[MeetupInfo]

  def requestAccessToken(redirectUri: String, code: String): IO[MeetupToken]

  def getLoggedUser(token: MeetupToken): IO[MeetupUser]
}
