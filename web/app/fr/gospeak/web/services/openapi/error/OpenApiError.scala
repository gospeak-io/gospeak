package fr.gospeak.web.services.openapi.error

import cats.data.NonEmptyList
import fr.gospeak.web.services.openapi.error.OpenApiError.ErrorMessage

final case class OpenApiError(path: List[String], errors: NonEmptyList[ErrorMessage]) {
  def format: String = s"at ${path.mkString(".")}: ${errors.map(_.format).toList.mkString(", ")}"

  def atPath(parentPath: String*): OpenApiError = OpenApiError(parentPath.toList ++ path, errors)

  def atPath(parentPath: List[String]): OpenApiError = OpenApiError(parentPath ++ path, errors)
}

object OpenApiError {
  def apply(error: ErrorMessage, other: ErrorMessage*): OpenApiError =
    new OpenApiError(List(), NonEmptyList.of(error, other: _*))

  /**
   * When a variable is used but not declared (for url in servers)
   *
   * @param name name of the missing variable
   */
  def missingVariable(name: String): OpenApiError = OpenApiError(ErrorMessage.missingVariable(name))

  /**
   * When a property does not exists (for object type in schemas)
   *
   * @param name the property name
   */
  def missingProperty(name: String): OpenApiError = OpenApiError(ErrorMessage.missingProperty(name))

  /**
   * When a reference does not exists
   *
   * @param ref the missing reference
   */
  def missingReference(ref: String): OpenApiError = OpenApiError(ErrorMessage.missingReference(ref))

  /**
   * When a reference has an invalid component
   *
   * @param ref       the reference containing the invalid component
   * @param component the invalid component
   * @param expected  the expected component
   * @return
   */
  def badReference(ref: String, component: String, expected: String): OpenApiError = OpenApiError(ErrorMessage.badReference(ref, component, expected))

  /**
   * When a value is duplicated
   *
   * @param value the duplicated value
   */
  def duplicateValue(value: String): OpenApiError = OpenApiError(ErrorMessage.duplicateValue(value))

  /**
   * When a value do not match the expected format
   *
   * @param value   read value with the incorrect format
   * @param format  expected format
   * @param example an example or hint about the expected format
   */
  def badFormat(value: String, format: String, example: String): OpenApiError = OpenApiError(ErrorMessage.badFormat(value, format, example))

  /**
   * When an example has a bad format
   *
   * @param value    comma separated list of examples with the incorrect format
   * @param format   the expected format
   */
  def badExampleFormat(value: String, format: String): OpenApiError = OpenApiError(ErrorMessage.badExampleFormat(value, format))

  /**
   * When the hint attribute has an other value than the expected one
   *
   * @param value     read value of the hint attribute
   * @param expected  expected value of the hint attribute
   * @param attribute name of the hint attribute
   */
  def badHintValue(value: String, expected: String, attribute: String): OpenApiError = OpenApiError(ErrorMessage.badHintValue(value, expected, attribute))

  /**
   * When the hint attribute has an unknown (unexpected) value
   *
   * @param value     read value of the hint attribute
   * @param attribute name of the hint attribute
   */
  def unknownHint(value: String, attribute: String): OpenApiError = OpenApiError(ErrorMessage.unknownHint(value, attribute))

  /**
   * When an attribute is expected but not present (from Play-JSON)
   */
  def missingPath(): OpenApiError = OpenApiError(ErrorMessage.missingPath())

  /**
   * When expects a JsString but got something else instead (from Play-JSON)
   */
  def expectString(): OpenApiError = OpenApiError(ErrorMessage.expectString())

  /**
   * When expects a JsObject but got something else instead (from Play-JSON)
   */
  def expectObject(): OpenApiError = OpenApiError(ErrorMessage.expectObject())

  /**
   * When there is an error but no message
   */
  def noMessage(): OpenApiError = OpenApiError(ErrorMessage.noMessage())

  final case class ErrorMessage(value: String, args: List[String]) {
    def format: String = value
  }

  object ErrorMessage {
    def missingVariable(name: String): ErrorMessage = ErrorMessage("error.openapi.variable.missing", List(name))

    def missingProperty(name: String): ErrorMessage = ErrorMessage("error.openapi.property.missing", List(name))

    def missingReference(ref: String): ErrorMessage = ErrorMessage("error.openapi.reference.missing", List(ref))

    def badReference(ref: String, component: String, expected: String): ErrorMessage = ErrorMessage("error.openapi.reference.invalid", List(ref, component, expected))

    def duplicateValue(value: String): ErrorMessage = ErrorMessage("error.openapi.value.duplicate", List(value))

    def badFormat(value: String, format: String, example: String): ErrorMessage = ErrorMessage("error.openapi.value.malformed", List(value, format, example))

    def badExampleFormat(value: String, format: String): ErrorMessage = ErrorMessage("error.openapi.example.malformed", List(value, format))

    def badHintValue(value: String, expected: String, attribute: String): ErrorMessage = ErrorMessage("error.openapi.hint.value", List(value, expected, attribute))

    def unknownHint(value: String, attribute: String): ErrorMessage = ErrorMessage("error.openapi.hint.unknown", List(value, attribute))

    def missingPath(): ErrorMessage = ErrorMessage("error.path.missing", List())

    def expectString(): ErrorMessage = ErrorMessage("error.expected.jsstring", List())

    def expectObject(): ErrorMessage = ErrorMessage("error.expected.jsobject", List())

    def noMessage(): ErrorMessage = ErrorMessage("error.message.missing", List())
  }

}
