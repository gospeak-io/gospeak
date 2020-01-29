package gospeak.libs.openapi.error

// FIXME: getMessage will return null :(
final case class OpenApiErrors(head: OpenApiError, tail: List[OpenApiError]) extends Throwable {
  def toList: List[OpenApiError] = head :: tail

  override def toString: String = s"OpenApiErrors(${toList.mkString(", ")})"
}

object OpenApiErrors {
  def apply(head: OpenApiError, tail: OpenApiError*): OpenApiErrors =
    new OpenApiErrors(head, tail.toList)

  def apply(errors: Seq[OpenApiError]): OpenApiErrors =
    errors.toList match {
      case head :: tail => new OpenApiErrors(head, tail)
      case Nil => new OpenApiErrors(OpenApiError.noMessage(), Nil)
    }
}
