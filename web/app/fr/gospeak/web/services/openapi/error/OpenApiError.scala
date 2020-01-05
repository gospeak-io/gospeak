package fr.gospeak.web.services.openapi.error

import cats.data.NonEmptyList

sealed trait OpenApiError extends Product with Serializable {
  def format: String
}

object OpenApiError {

  final case class ValidationError(path: List[String], errors: NonEmptyList[ErrorMessage]) extends OpenApiError {
    override def format: String = s"at ${path.mkString(".")}: ${errors.map(_.format).toList.mkString(", ")}"
  }

  final case class MissingErrors() extends OpenApiError {
    override def format: String = "Failed without error"
  }

  final case class ErrorMessage(value: String, args: List[String]) {
    def format: String = value
  }

  object ErrorMessage {
    /**
     * When a variable is used but not declared (for url in servers)
     *
     * @param name name of the missing variable
     */
    def missingVariable(name: String): ErrorMessage = ErrorMessage("error.variable.missing", List(name))

    /**
     * When a property does not exists (for object type in schemas)
     *
     * @param name     the property name
     * @param location where the missing property was referenced (can be a location hint instead of a precise location)
     */
    def missingProperty(name: String, location: String): ErrorMessage = ErrorMessage("error.property.missing", List(name, location))

    /**
     * When a reference does not exists
     *
     * @param ref the missing reference
     */
    def missingReference(ref: String): ErrorMessage = ErrorMessage("error.reference.missing", List(ref))

    /**
     * When a reference has an unknown component
     *
     * @param ref       the reference containing the unknown component
     * @param component the unknown component
     */
    def unknownReference(ref: String, component: String): ErrorMessage = ErrorMessage("error.reference.unknown", List(ref, component))

    /**
     * When a value is duplicated
     *
     * @param value    the duplicated value
     * @param location where the duplication occurred (can be a location hint instead of a precise location)
     */
    def duplicateValue(value: String, location: String): ErrorMessage = ErrorMessage("error.value.duplicate", List(value, location))

    /**
     * When a value do not match the expected format
     *
     * @param value   read value with the incorrect format
     * @param format  expected format
     * @param example an example or hint about the expected format
     */
    def badFormat(value: String, format: String, example: String): ErrorMessage = ErrorMessage("error.value.malformed", List(value, format, example))

    /**
     * When an example has a bad format
     *
     * @param value    comma separated list of examples with the incorrect format
     * @param format   the expected format
     * @param location where this error occurred (can be a location hint instead of a precise location)
     */
    def badExampleFormat(value: String, format: String, location: String): ErrorMessage = ErrorMessage("error.example.malformed", List(value, format, location))

    /**
     * When the hint attribute has an other value than the expected one
     *
     * @param value     read value of the hint attribute
     * @param expected  expected value of the hint attribute
     * @param attribute name of the hint attribute
     */
    def badHintValue(value: String, expected: String, attribute: String): ErrorMessage = ErrorMessage("error.hint.value", List(value, expected, attribute))

    /**
     * When the hint attribute has an unknown (unexpected) value
     *
     * @param value     read value of the hint attribute
     * @param attribute name of the hint attribute
     */
    def unknownHint(value: String, attribute: String): ErrorMessage = ErrorMessage("error.hint.unknown", List(attribute, value))

    /**
     * When an attribute is expected but not present (from Play-JSON)
     */
    def missingPath(): ErrorMessage = ErrorMessage("error.path.missing", List())

    /**
     * When expects a JsString but got something else instead (from Play-JSON)
     */
    def expectString(): ErrorMessage = ErrorMessage("error.expected.jsstring", List())

    /**
     * When expects a JsObject but got something else instead (from Play-JSON)
     */
    def expectObject(): ErrorMessage = ErrorMessage("error.expected.jsobject", List())

    /**
     * When there is an error but no message
     */
    def noMessage(): ErrorMessage = ErrorMessage("error.message.missing", List())
  }

}
