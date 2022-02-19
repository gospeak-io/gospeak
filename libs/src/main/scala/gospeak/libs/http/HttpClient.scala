package gospeak.libs.http

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import cats.effect.IO
import gospeak.libs.http.HttpClient._
import gospeak.libs.scala.domain.CustomException
import hammock.apache.ApacheInterpreter
import hammock.{Encoder, Entity, Hammock, HttpResponse, InterpTrans, Method, Uri}

trait HttpClient {

  def get(url: String, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response]

  def post(url: String, body: Option[String] = None, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response]

  def postJson(url: String, body: String, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response]

  def putJson(url: String, body: String, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response]

  def patchJson(url: String, body: String, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response]

  def deleteJson(url: String, body: String, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response]

  def postForm(url: String, body: Map[String, String], query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response]

  def putForm(url: String, body: Map[String, String], query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response]

  def patchForm(url: String, body: Map[String, String], query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response]

  def deleteForm(url: String, body: Map[String, String], query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response]

  def buildUrl(url: String, query: Map[String, String]): String
}

object HttpClient {

  final case class Request(url: String,
                           query: Map[String, String] = Map(),
                           headers: Map[String, String] = Map())

  final case class Response(status: Int,
                            body: String,
                            headers: Map[String, String]) {
    def isOk: Boolean = 200 <= status && status < 400
  }

  object Response {
    def from(res: HttpResponse): Response =
      Response(
        status = res.status.code,
        headers = res.headers,
        body = res.entity match {
          case Entity.EmptyEntity => ""
          case e: Entity.StringEntity => e.body
          case e: Entity.ByteArrayEntity => new String(e.body, StandardCharsets.UTF_8)
        })
  }

  def buildUrl(url: String, query: Map[String, String]): String = {
    if (query.isEmpty) {
      url
    } else if (url.contains("?")) {
      url + "&" + buildParams(query)
    } else {
      url + "?" + buildParams(query)
    }
  }

  def buildParams(params: Map[String, String]): String =
    params.map { case (key, value) => s"$key=${URLEncoder.encode(value, "UTF8")}" }.mkString("&")

}

class HttpClientImpl extends HttpClient {
  private implicit val interpreter: InterpTrans[IO] = ApacheInterpreter.instance[IO]
  private implicit val stringEncoder: Encoder[String] = (value: String) => Entity.StringEntity(value)

  def get(url: String, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    buildUri(url, query)(request(Method.GET, _, headers))

  def post(url: String, body: Option[String] = None, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    send(Method.POST, url, body, query = query, headers = headers)

  def postJson(url: String, body: String, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    sendJson(Method.POST, url = url, body = body, query = query, headers = headers)

  def putJson(url: String, body: String, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    sendJson(Method.PUT, url = url, body = body, query = query, headers = headers)

  def patchJson(url: String, body: String, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    sendJson(Method.PATCH, url = url, body = body, query = query, headers = headers)

  def deleteJson(url: String, body: String, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    sendJson(Method.DELETE, url = url, body = body, query = query, headers = headers)

  def postForm(url: String, body: Map[String, String], query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    sendForm(Method.POST, url, body = body, query = query, headers = headers)

  def putForm(url: String, body: Map[String, String], query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    sendForm(Method.PUT, url, body = body, query = query, headers = headers)

  def patchForm(url: String, body: Map[String, String], query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    sendForm(Method.PATCH, url, body = body, query = query, headers = headers)

  def deleteForm(url: String, body: Map[String, String], query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    sendForm(Method.DELETE, url, body = body, query = query, headers = headers)

  private def sendJson(method: Method, url: String, body: String, query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    send(method, url, Some(body), query, headers + ("Content-Type" -> "application/json"))

  private def sendForm(method: Method, url: String, body: Map[String, String], query: Map[String, String], headers: Map[String, String]): IO[Response] =
    send(method, url, Some(buildParams(body)), query, headers + ("Content-Type" -> "application/x-www-form-urlencoded"))

  private def send(method: Method, url: String, body: Option[String], query: Map[String, String] = Map(), headers: Map[String, String] = Map()): IO[Response] =
    buildUri(url, query)(request(method, _, headers, body))

  private def buildUri(url: String, query: Map[String, String])(callback: Uri => IO[Response]): IO[Response] =
    Uri.fromString(buildUrl(url, query)).map(callback).getOrElse(IO.raiseError(CustomException(s"Invalid URI '${buildUrl(url, query)}'")))

  private def request(method: Method,
                      uri: Uri,
                      headers: Map[String, String],
                      body: Option[String] = None): IO[Response] = {
    // def requestInfo: String = s"HttpClient.$method ${uri.path}\n  headers: $headers" + body.map(b => s"\n  body: $b").getOrElse("")
    Hammock.request(method, uri, headers, body).exec[IO].map(Response.from)
    // .map { r => println(s"\n$requestInfo\n  response: ${r.status} ${r.body}"); r }
    // .recoverWith { case e => println(s"\n$requestInfo\n  response: ${e.getClass.getSimpleName}: ${e.getMessage}"); IO.raiseError(e) }
  }

  def buildUrl(url: String, query: Map[String, String]): String = HttpClient.buildUrl(url, query)
}

class FakeHttpClient(fetch: String => IO[Response]) extends HttpClient {
  override def get(url: String, query: Map[String, String], headers: Map[String, String]): IO[Response] = fetch(url)

  override def post(url: String, body: Option[String] = None, query: Map[String, String], headers: Map[String, String]): IO[Response] = fetch(url)

  override def postJson(url: String, body: String, query: Map[String, String], headers: Map[String, String]): IO[Response] = fetch(url)

  override def putJson(url: String, body: String, query: Map[String, String], headers: Map[String, String]): IO[Response] = fetch(url)

  override def patchJson(url: String, body: String, query: Map[String, String], headers: Map[String, String]): IO[Response] = fetch(url)

  override def deleteJson(url: String, body: String, query: Map[String, String], headers: Map[String, String]): IO[Response] = fetch(url)

  override def postForm(url: String, body: Map[String, String], query: Map[String, String], headers: Map[String, String]): IO[Response] = fetch(url)

  override def putForm(url: String, body: Map[String, String], query: Map[String, String], headers: Map[String, String]): IO[Response] = fetch(url)

  override def patchForm(url: String, body: Map[String, String], query: Map[String, String], headers: Map[String, String]): IO[Response] = fetch(url)

  override def deleteForm(url: String, body: Map[String, String], query: Map[String, String], headers: Map[String, String]): IO[Response] = fetch(url)

  override def buildUrl(url: String, query: Map[String, String]): String = HttpClient.buildUrl(url, query)
}
