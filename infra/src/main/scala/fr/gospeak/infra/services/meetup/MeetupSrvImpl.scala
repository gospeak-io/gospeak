package fr.gospeak.infra.services.meetup

import cats.effect.IO
import fr.gospeak.core.services.meetup.MeetupSrv
import fr.gospeak.core.services.meetup.domain.{MeetupException, MeetupInfo, MeetupToken, MeetupUser}
import fr.gospeak.infra.libs.meetup.MeetupClient
import fr.gospeak.infra.libs.meetup.domain.{MeetupToken => CMeetupToken}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Avatar, Url}

import scala.util.Try

class MeetupSrvImpl(client: MeetupClient) extends MeetupSrv {
  def getInfo(redirectUri: String): Try[MeetupInfo] =
    client.buildAuthorizationUrl(redirectUri).map(authUrl => MeetupInfo(authUrl, client.buildRevokeUrl()))

  def requestAccessToken(redirectUri: String, code: String): IO[MeetupToken] =
    client.requestAccessToken(redirectUri, code).flatMap {
      case Right(token) => IO.pure(MeetupToken(
        accessToken = token.access_token,
        refreshToken = token.refresh_token))
      case Left(err) => IO.raiseError(MeetupException("requestAccessToken", err.error))
    }

  def getLoggedUser(token: MeetupToken): IO[MeetupUser] =
    client.getLoggedUser()(CMeetupToken.Access(token.accessToken)).flatMap {
      case Right(user) => Url.from(user.photo.photo_link).toIO.map { avatarUrl =>
        MeetupUser(
          id = MeetupUser.Id(user.id),
          name = user.name,
          avatar = Avatar(avatarUrl, Avatar.Source.Meetup))
      }
      case Left(err) => IO.raiseError(MeetupException("getLoggedUser", err.error))
    }
}
