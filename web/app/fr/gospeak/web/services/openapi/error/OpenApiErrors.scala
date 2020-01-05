package fr.gospeak.web.services.openapi.error

import cats.data.NonEmptyList
import fr.gospeak.web.services.openapi.error.OpenApiError.MissingErrors

// FIXME: getMessage will return null :(
final case class OpenApiErrors(head: OpenApiError, tail: List[OpenApiError]) extends Throwable {
  def toList: NonEmptyList[OpenApiError] = NonEmptyList.of(head, tail: _*)

  override def toString: String = s"OpenApiErrors(${toList.toList.mkString(", ")})"
}

object OpenApiErrors {
  def apply(head: OpenApiError, tail: OpenApiError*): OpenApiErrors = new OpenApiErrors(head, tail.toList)

  def apply(errors: Seq[OpenApiError]): OpenApiErrors =
    errors.toList match {
      case head :: tail => new OpenApiErrors(head, tail)
      case Nil => new OpenApiErrors(MissingErrors(), Nil)
    }
}
