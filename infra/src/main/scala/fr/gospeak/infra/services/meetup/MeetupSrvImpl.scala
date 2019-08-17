package fr.gospeak.infra.services.meetup

import cats.effect.IO
import fr.gospeak.core.services.meetup.MeetupSrv
import fr.gospeak.core.services.meetup.domain.{MeetupException, MeetupGroup, MeetupToken, MeetupUser}
import fr.gospeak.infra.libs.meetup.{MeetupClient, domain => lib}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Avatar, Url}

import scala.util.Try

class MeetupSrvImpl(client: MeetupClient) extends MeetupSrv {
  override def buildAuthorizationUrl(redirectUri: String): Try[Url] = client.buildAuthorizationUrl(redirectUri)

  override def requestAccessToken(redirectUri: String, code: String): IO[MeetupToken] =
    client.requestAccessToken(redirectUri, code).flatMap {
      case Right(token) => IO.pure(MeetupToken(
        accessToken = token.access_token,
        refreshToken = token.refresh_token))
      case Left(err) => IO.raiseError(MeetupException.RequestToken(err.error))
    }

  override def getLoggedUser()(implicit token: MeetupToken): IO[MeetupUser] =
    client.getLoggedUser()(toLib(token)).flatMap {
      case Right(user) => Url.from(user.photo.photo_link).toIO.map { avatarUrl =>
        MeetupUser(
          id = MeetupUser.Id(user.id),
          name = user.name,
          avatar = Avatar(avatarUrl, Avatar.Source.Meetup))
      }
      case Left(err) => IO.raiseError(MeetupException.LoggedUser(err.error))
    }

  override def getGroup(slug: MeetupGroup.Slug)(implicit token: MeetupToken): IO[MeetupGroup] =
    client.getGroup(slug.value)(toLib(token)).flatMap {
      case Right(group) => (for {
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
        country = group.country)).toIO
      case Left(err) => IO.raiseError(MeetupException.GroupNotFound(slug, err.error))
    }

  private def toLib(token: MeetupToken): lib.MeetupToken.Access = lib.MeetupToken.Access(token.accessToken)
}
