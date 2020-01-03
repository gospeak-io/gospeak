package fr.gospeak.web.services.openapi.error

sealed trait OpenApiError extends Product with Serializable {
  def format: String
}

object OpenApiError {

  final case class ValidationError(path: List[String], errors: List[Message]) extends OpenApiError {
    override def format: String = s"at ${path.mkString(".")}: ${errors.map(_.format).mkString(", ")}"
  }

  final case class MissingErrors() extends OpenApiError {
    override def format: String = "Failed without error"
  }

  final case class Message(value: String, args: List[Any]) {
    def format: String = value
  }

  object Message {
    def apply(value: String, args: Any*): Message = new Message(value, args.toList)

    def validationFailed(actual: String, expected: String, clazz: String): Message = Message("error.validation", List(actual, expected, clazz))
  }

}
