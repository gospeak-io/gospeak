package fr.gospeak.libs.scalautils.domain

import fr.gospeak.libs.scalautils.CustomException

import scala.util.{Failure, Success, Try}

final class Email private(value: String) extends DataClass(value)

object Email {
  def from(in: String): Try[Email] = {
    val errs = errors(in)
    if (errs.isEmpty) Success(new Email(in))
    else Failure(CustomException(s"'$in' is an invalid Email: " + errs.mkString(", ")))
  }

  // FIXME: improve
  def errors(in: String): Seq[String] =
    Seq(
      if (in.contains("@")) None else Some("Missing @")
    ).flatten
}
