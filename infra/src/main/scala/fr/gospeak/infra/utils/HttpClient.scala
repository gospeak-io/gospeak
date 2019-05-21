package fr.gospeak.infra.utils

import java.net.URLEncoder

import cats.effect.IO
import fr.gospeak.libs.scalautils.domain.CustomException
import hammock.apache.ApacheInterpreter
import hammock.{Hammock, HttpResponse, Method, Uri}

object HttpClient {
  private implicit val interpreter: ApacheInterpreter[IO] = ApacheInterpreter[IO]

  def get(uri: String, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[HttpResponse] =
    Uri.fromString(buildUrl(uri, query)).map { uri =>
      Hammock.request(Method.GET, uri, headers).exec[IO]
    }.getOrElse(IO.raiseError(CustomException(s"Invalid URI '$uri'")))

  def getAsString(uri: String, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[String] =
    get(uri, query, headers).map(_.entity.content.toString)

  private def buildUrl(uri: String, query: Map[String, String]): String = {
    def buildQuery(query: Map[String, String]): String =
      query.map { case (key, value) => s"$key=${URLEncoder.encode(value, "UTF8")}" }.mkString("&")

    if (query.isEmpty) {
      uri
    } else if (uri.contains("?")) {
      uri + "&" + buildQuery(query)
    } else {
      uri + "?" + buildQuery(query)
    }
  }
}
