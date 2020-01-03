package fr.gospeak.web.services.openapi.error

sealed trait OpenApiError extends Product with Serializable {
  def format: String
}

object OpenApiError {

  final case class ValidationError(path: List[String], errors: List[ErrorMessage]) extends OpenApiError {
    override def format: String = s"at ${path.mkString(".")}: ${errors.map(_.format).mkString(", ")}"
  }

  final case class MissingErrors() extends OpenApiError {
    override def format: String = "Failed without error"
  }

  final case class ErrorMessage(value: String, args: List[Any]) {
    def format: String = value
  }

  object ErrorMessage {
    def missingPath(): ErrorMessage = ErrorMessage("error.path.missing", List())

    def missingVariable(name: String): ErrorMessage = ErrorMessage("error.variable.missing", List(name))

    def expectString(): ErrorMessage = ErrorMessage("error.expected.jsstring", List())

    def expectObject(): ErrorMessage = ErrorMessage("error.expected.jsobject", List())

    def validationFailed(actual: String, expected: String, clazz: String): ErrorMessage = ErrorMessage("error.validation", List(actual, expected, clazz))
  }

}
