package fr.gospeak.infra.libs.meetup

import cats.effect.IO
import fr.gospeak.infra.libs.meetup.domain.MeetupKey
import fr.gospeak.infra.utils.HttpClient

class MeetupClient {
  private val baseUrl = "https://api.meetup.com"

  def dashboard(key: MeetupKey): IO[String] =
    HttpClient.getAsString(s"$baseUrl/dashboard", query = Map("key" -> key.value, "sign" -> "sign"))

  def group(urlName: String): IO[String] =
    HttpClient.getAsString(s"$baseUrl/$urlName")

  def events(urlName: String): IO[String] =
    HttpClient.getAsString(s"$baseUrl/$urlName/events", query = Map("has_ended" -> "true"))

}
