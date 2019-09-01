package fr.gospeak.infra.utils

import java.net.URLEncoder

import cats.effect.IO
import fr.gospeak.libs.scalautils.domain.CustomException
import hammock.apache.ApacheInterpreter
import hammock.{Encoder, Entity, Hammock, HttpResponse, Method, Uri}

object HttpClient {

  final case class Request(url: String,
                           query: Map[String, String] = Map(),
                           headers: Map[String, String] = Map())

  final case class Response(status: Int,
                            body: String,
                            headers: Map[String, String])

  object Response {
    def from(res: HttpResponse): Response =
      Response(
        status = res.status.code,
        headers = res.headers,
        body = res.entity.content.toString)
  }

  private implicit val interpreter: ApacheInterpreter[IO] = ApacheInterpreter[IO]
  private implicit val stringEncoder: Encoder[String] = (value: String) => Entity.StringEntity(value)

  def get(url: String, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    buildUri(url, query)(uri => Hammock.request(Method.GET, uri, headers).exec[IO]).map(Response.from)

  def postJson(url: String, body: String, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    sendJson(method = Method.POST, url = url, body = body, query = query, headers = headers)

  def putJson(url: String, body: String, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    sendJson(method = Method.PUT, url = url, body = body, query = query, headers = headers)

  def patchJson(url: String, body: String, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    sendJson(method = Method.PATCH, url = url, body = body, query = query, headers = headers)

  def deleteJson(url: String, body: String, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    sendJson(method = Method.DELETE, url = url, body = body, query = query, headers = headers)

  def postForm(url: String, body: Map[String, String], query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    sendForm(method = Method.POST, url = url, body = body, query = query, headers = headers)

  def putForm(url: String, body: Map[String, String], query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    sendForm(method = Method.PUT, url = url, body = body, query = query, headers = headers)

  def patchForm(url: String, body: Map[String, String], query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    sendForm(method = Method.PATCH, url = url, body = body, query = query, headers = headers)

  def deleteForm(url: String, body: Map[String, String], query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    sendForm(method = Method.DELETE, url = url, body = body, query = query, headers = headers)

  private def sendJson(method: Method, url: String, body: String, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    buildUri(url, query)(uri => Hammock.request(method, uri, headers + ("Content-Type" -> "application/json"), Some(body)).exec[IO]).map(Response.from)

  private def sendForm(method: Method, url: String, body: Map[String, String], query: Map[String, String], headers: Map[String, String]): IO[Response] =
    buildUri(url, query)(uri => Hammock.request(method, uri, headers + ("Content-Type" -> "application/x-www-form-urlencoded"), Some(buildParams(body))).exec[IO]).map(Response.from)

  private def buildUri(url: String, query: Map[String, String])(callback: Uri => IO[HttpResponse]): IO[HttpResponse] =
    Uri.fromString(buildUrl(url, query)).map(callback).getOrElse(IO.raiseError(CustomException(s"Invalid URI '${buildUrl(url, query)}'")))

  def buildUrl(url: String, query: Map[String, String]): String = {
    if (query.isEmpty) {
      url
    } else if (url.contains("?")) {
      url + "&" + buildParams(query)
    } else {
      url + "?" + buildParams(query)
    }
  }

  private def buildParams(params: Map[String, String]): String =
    params.map { case (key, value) => s"$key=${URLEncoder.encode(value, "UTF8")}" }.mkString("&")
}
