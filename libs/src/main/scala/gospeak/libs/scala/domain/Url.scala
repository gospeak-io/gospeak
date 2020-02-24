package gospeak.libs.scala.domain

import java.net.URL

import scala.util.Try

final class Url private(value: String) extends DataClass(value)

object Url {
  def from(in: String): Either[CustomException, Url] = {
    val errs = errors(in)
    if (errs.isEmpty) Right(new Url(in))
    else Left(CustomException(s"'$in' is an invalid Url", errs))
  }

  // FIXME: improve
  private def errors(in: String): Seq[CustomError] =
    Seq(
      if (in.startsWith("http")) None else Some("Do not starts with 'http'"),
      Try(new URL(in)).failed.map(_.getMessage).toOption
    ).flatten.map(CustomError)
}
