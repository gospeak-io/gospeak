package fr.gospeak.infra.libs.meetup

import cats.effect.IO
import fr.gospeak.infra.libs.meetup.MeetupClient.Conf
import fr.gospeak.infra.libs.meetup.MeetupJson._
import fr.gospeak.infra.libs.meetup.domain._
import fr.gospeak.infra.utils.HttpClient
import fr.gospeak.infra.utils.HttpClient.Response
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Secret, Url}
import io.circe.parser.decode
import io.circe.{Decoder, Encoder}

import scala.util.{Failure, Try}

class MeetupClient(conf: Conf) {
  private val baseUrl = "https://api.meetup.com"

  def buildAuthorizationUrl(redirectUri: String): Try[Url] =
    if (redirectUri.startsWith(conf.baseRedirectUri)) {
      Url.from(HttpClient.buildUrl("https://secure.meetup.com/oauth2/authorize", Map(
        "scope" -> "event_management",
        "client_id" -> conf.key,
        "response_type" -> "code",
        "redirect_uri" -> redirectUri))).toTry
    } else {
      Failure(new IllegalArgumentException(s"Bad redirectUri ($redirectUri), it should start with ${conf.baseRedirectUri}"))
    }

  // https://www.meetup.com/meetup_api/auth/#oauth2server
  def requestAccessToken(redirectUri: String, code: String): IO[Either[MeetupError, MeetupToken]] =
    HttpClient.postForm("https://secure.meetup.com/oauth2/access", Map(
      "client_id" -> conf.key,
      "client_secret" -> conf.secret.decode,
      "grant_type" -> "authorization_code",
      "redirect_uri" -> redirectUri,
      "code" -> code)).flatMap(parse[MeetupToken])

  // cf https://www.meetup.com/meetup_api/auth/#oauth2-refresh
  def refreshAccessToken(refreshToken: String): IO[Either[MeetupError, MeetupToken]] =
    HttpClient.postForm("https://secure.meetup.com/oauth2/access", Map(
      "client_id" -> conf.key,
      "client_secret" -> conf.secret.decode,
      "grant_type" -> "refresh_token",
      "refresh_token" -> refreshToken)).flatMap(parse[MeetupToken])

  // cf https://www.meetup.com/meetup_api/auth/#oauth2-resources
  def getLoggedUser()(implicit accessToken: MeetupToken.Access): IO[Either[MeetupError, MeetupUser]] =
    get[MeetupUser](s"$baseUrl/2/member/self")

  // cf https://www.meetup.com/fr-FR/meetup_api/docs/:urlname#get
  def getGroup(urlname: String)(implicit accessToken: MeetupToken.Access): IO[Either[MeetupError, MeetupGroup]] =
    get[MeetupGroup](s"$baseUrl/$urlname")

  // cf https://www.meetup.com/fr-FR/meetup_api/docs/:urlname/events/#list
  def getEvents(urlname: String)(implicit accessToken: MeetupToken.Access): IO[Either[MeetupError, Seq[MeetupEvent]]] =
    get[Seq[MeetupEvent]](s"$baseUrl/$urlname/events", Map("status" -> "upcoming,past"))

  // cf https://www.meetup.com/fr-FR/meetup_api/docs/:urlname/events#create
  def createEvent(urlname: String, event: MeetupEvent.Create): IO[Response] =
    HttpClient.postJson(s"$baseUrl/$urlname", toJson(event))

  // cf https://www.meetup.com/fr-FR/meetup_api/docs/:urlname/events/:id#get
  def getEvent(urlname: String, eventId: String)(implicit accessToken: MeetupToken.Access): IO[Either[MeetupError, MeetupEvent]] =
    get[MeetupEvent](s"$baseUrl/$urlname/events/$eventId")

  // cf https://www.meetup.com/fr-FR/meetup_api/docs/:urlname/events/:id#edit
  // cf https://www.meetup.com/fr-FR/meetup_api/docs/:urlname/events/:id#delete

  def getVenues(urlname: String)(implicit accessToken: MeetupToken.Access): IO[Either[MeetupError, Seq[MeetupVenue]]] =
    get[Seq[MeetupVenue]](s"$baseUrl/$urlname/venues")

  // to test
  def getRequest(urlname: String, venueId: Long)(implicit accessToken: MeetupToken.Access): IO[String] =
    HttpClient.get(s"$baseUrl/$urlname/venues/$venueId", headers = Map("Authorization" -> s"Bearer ${accessToken.value}")).map(_.body)

  private def get[A](url: String, query: Map[String, String] = Map())(implicit accessToken: MeetupToken.Access, d: Decoder[A]): IO[Either[MeetupError, A]] =
    HttpClient.get(url, query = query, headers = Map("Authorization" -> s"Bearer ${accessToken.value}")).flatMap(parse[A])

  private def parse[A](res: Response)(implicit d: Decoder[A]): IO[Either[MeetupError, A]] = {
    decode[A](res.body) match {
      case Right(info) => IO.pure(Right(info))
      case Left(err) => decode[MeetupError.NotAuthorized](res.body).map(_.toErr)
        .orElse(decode[MeetupError.Multi](res.body).map(_.toErr))
        .orElse(decode[MeetupError](res.body)) match {
        case Right(fail) => IO.pure(Left(fail))
        case Left(_) => IO.raiseError(new IllegalArgumentException(s"Unable to parse ${res.body}", err))
      }
    }
  }

  private def toJson[A](value: A)(implicit e: Encoder[A]): String = {
    import io.circe.syntax._
    value.asJson.noSpaces
  }
}

object MeetupClient {

  final case class Conf(key: String,
                        secret: Secret,
                        baseRedirectUri: String)

}
