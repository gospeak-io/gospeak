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

/**
 * Allows to interact with the meetup API
 *
 * `groupSlug` is `urlname` in API, this name change in parameters is for better clariry
 */
class MeetupClient(conf: Conf) {
  private val baseUrl = "https://api.meetup.com"

  def hasSecureCallback: Boolean = conf.baseRedirectUri.startsWith("https")

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
  def getGroup(groupSlug: String)(implicit accessToken: MeetupToken.Access): IO[Either[MeetupError, MeetupGroup]] =
    get[MeetupGroup](s"$baseUrl/$groupSlug")

  // cf https://www.meetup.com/fr-FR/meetup_api/docs/:urlname/events/#list
  def getEvents(groupSlug: String)(implicit accessToken: MeetupToken.Access): IO[Either[MeetupError, Seq[MeetupEvent]]] =
    get[Seq[MeetupEvent]](s"$baseUrl/$groupSlug/events", Map("status" -> "upcoming,past"))

  // cf https://www.meetup.com/fr-FR/meetup_api/docs/:urlname/events#create
  def createEvent(groupSlug: String, event: MeetupEvent.Create)(implicit accessToken: MeetupToken.Access): IO[Either[MeetupError, MeetupEvent]] =
    post[MeetupEvent](s"$baseUrl/$groupSlug/events", event.toMap)

  // cf https://www.meetup.com/fr-FR/meetup_api/docs/:urlname/events/:id#get
  def getEvent(groupSlug: String, eventId: String)(implicit accessToken: MeetupToken.Access): IO[Either[MeetupError, MeetupEvent]] =
    get[MeetupEvent](s"$baseUrl/$groupSlug/events/$eventId")

  // cf https://www.meetup.com/fr-FR/meetup_api/docs/:urlname/events/:id#edit
  def updateEvent(groupSlug: String, eventId: String, event: MeetupEvent.Create)(implicit accessToken: MeetupToken.Access): IO[Either[MeetupError, MeetupEvent]] =
    patch[MeetupEvent](s"$baseUrl/$groupSlug/events/$eventId", event.toMap)

  // cf https://www.meetup.com/fr-FR/meetup_api/docs/:urlname/events/:id#delete
  def deleteEvent(groupSlug: String, eventId: String)(implicit accessToken: MeetupToken.Access): IO[Either[MeetupError, Unit]] =
    delete[Unit](s"$baseUrl/$groupSlug/events/$eventId", Map("remove_from_calendar" -> "true"))

  def getVenues(groupSlug: String)(implicit accessToken: MeetupToken.Access): IO[Either[MeetupError, Seq[MeetupVenue]]] =
    get[Seq[MeetupVenue]](s"$baseUrl/$groupSlug/venues", Map("page" -> "50"))

  // to test
  def getRequest(groupSlug: String, venueId: Long)(implicit accessToken: MeetupToken.Access): IO[String] =
    HttpClient.get(s"$baseUrl/$groupSlug/venues/$venueId", headers = Map("Authorization" -> s"Bearer ${accessToken.value}")).map(_.body)

  private def get[A](url: String, query: Map[String, String] = Map())(implicit accessToken: MeetupToken.Access, d: Decoder[A]): IO[Either[MeetupError, A]] =
    HttpClient.get(url, query = query, headers = Map("Authorization" -> s"Bearer ${accessToken.value}")).flatMap(parse[A])

  private def post[A](url: String, body: Map[String, String], query: Map[String, String] = Map())(implicit accessToken: MeetupToken.Access, d: Decoder[A]): IO[Either[MeetupError, A]] =
    HttpClient.postForm(url, body, query = query, headers = Map("Authorization" -> s"Bearer ${accessToken.value}")).flatMap(parse[A])

  private def patch[A](url: String, body: Map[String, String], query: Map[String, String] = Map())(implicit accessToken: MeetupToken.Access, d: Decoder[A]): IO[Either[MeetupError, A]] =
    HttpClient.patchForm(url, body, query = query, headers = Map("Authorization" -> s"Bearer ${accessToken.value}")).flatMap(parse[A])

  private def delete[A](url: String, body: Map[String, String], query: Map[String, String] = Map())(implicit accessToken: MeetupToken.Access, d: Decoder[A]): IO[Either[MeetupError, A]] =
    HttpClient.deleteForm(url, body, query = query, headers = Map("Authorization" -> s"Bearer ${accessToken.value}")).flatMap(parse[A])

  private def parse[A](res: Response)(implicit d: Decoder[A]): IO[Either[MeetupError, A]] = {
    val body = transformBody(res.body)
    decode[A](body) match {
      case Right(info) => IO.pure(Right(info))
      case Left(err) => decode[MeetupError.NotAuthorized](body).map(_.toErr)
        .orElse(decode[MeetupError.Multi](body).map(_.toErr))
        .orElse(decode[MeetupError](body)) match {
        case Right(fail) => IO.pure(Left(fail))
        case Left(_) => IO.raiseError(new IllegalArgumentException(s"Unable to parse response body: '${res.body}'", err))
      }
    }
  }

  // when body is not a JSON, transform it to JSON
  private def transformBody(body: String): String =
    if (body == "()") "{}" else body

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
