package fr.gospeak.infra.utils

import cats.effect.IO
import fr.gospeak.libs.scalautils.domain.CustomException
import hammock.apache.ApacheInterpreter
import hammock.{Hammock, HttpResponse, Method, Uri}

object HttpClient {
  private implicit val interpreter: ApacheInterpreter[IO] = ApacheInterpreter[IO]

  def get(uri: String): IO[HttpResponse] =
    Uri.fromString(uri).map { uri =>
      Hammock.request(Method.GET, uri, Map()).exec[IO]
    }.getOrElse(IO.raiseError(CustomException(s"Invalid URI '$uri'")))

  def getAsString(uri: String): IO[String] = get(uri).map(_.entity.content.toString)
}
