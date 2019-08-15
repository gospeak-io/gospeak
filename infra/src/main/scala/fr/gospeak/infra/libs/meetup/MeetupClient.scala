package fr.gospeak.infra.libs.meetup

import cats.effect.IO
import fr.gospeak.infra.libs.meetup.MeetupClient.Conf
import fr.gospeak.infra.libs.meetup.domain.MeetupKey
import fr.gospeak.infra.utils.HttpClient

import scala.util.{Failure, Success, Try}

// auth: https://www.meetup.com/meetup_api/auth/#oauth2
/*
  Connection procedure:
    - create a OAuth consumer: https://secure.meetup.com/fr-FR/meetup_api/oauth_consumers/
 */
class MeetupClient(conf: Conf) {
  private val baseUrl = "https://api.meetup.com"

  def dashboard(key: MeetupKey): IO[String] =
    HttpClient.getAsString(s"$baseUrl/dashboard", query = Map("key" -> key.value, "sign" -> "sign"))

  def group(urlName: String): IO[String] =
    HttpClient.getAsString(s"$baseUrl/$urlName")

  def events(urlName: String): IO[String] =
    HttpClient.getAsString(s"$baseUrl/$urlName/events", query = Map("has_ended" -> "true"))

  private def buildRedirectUrl(redirectUri: String): Try[String] =
    if (redirectUri.startsWith(conf.baseRedirectUri))
      Success(s"https://secure.meetup.com/oauth2/authorize?client_id=${conf.key}&response_type=code&redirect_uri=$redirectUri")
    else
      Failure(new IllegalArgumentException(s"Bad redirectUri ($redirectUri), it should start with ${conf.baseRedirectUri}"))
}

object MeetupClient {

  final case class Conf(key: String,
                        secret: String,
                        baseRedirectUri: String)

}
