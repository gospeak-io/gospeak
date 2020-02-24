package gospeak.libs.matomo

import java.util.UUID

import cats.effect.IO
import gospeak.libs.http.HttpClient
import gospeak.libs.scala.domain.Secret

/**
 * doc:
 *  - https://developer.matomo.org/guides/querying-the-reporting-api
 *  - https://developer.matomo.org/api-reference/tracking-api
 *
 *  - https://developer.matomo.org/api-reference/reporting-api#Actions
 *  - https://developer.matomo.org/api-reference/reporting-api#Events
 */
class MatomoClient(conf: MatomoClient.Conf) {
  // see https://developer.matomo.org/api-reference/tracking-api
  def trackEvent(category: String, action: String, name: Option[String], value: Option[Double], user: Option[String]): IO[Option[MatomoError]] =
    send(Map(
      "action_name" -> Some(s"$category/$action${name.map("/" + _).getOrElse("")}"),
      "uid" -> user,
      "e_c" -> Some(category),
      "e_a" -> Some(action),
      "e_n" -> name,
      "e_v" -> value.map(_.toString)
    ).collect { case (key, Some(value)) => (key, value) })

  private def send(query: Map[String, String]): IO[Option[MatomoError]] =
    HttpClient.get(conf.baseUrl + "/matomo.php", query = query ++ Map(
      "idsite" -> conf.site.toString,
      "rec" -> "1",
      "rand" -> UUID.randomUUID().toString,
      "apiv" -> "1",
      "send_image" -> "0"
    ), headers = Map()).map(res => if (res.status == 204) None else Some(MatomoError(res.body)))
}

object MatomoClient {

  final case class Conf(baseUrl: String,
                        site: Int,
                        token: Secret)

}
