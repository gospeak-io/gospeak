package gospeak.libs.openapi

import gospeak.libs.openapi.error.OpenApiError
import gospeak.libs.openapi.error.OpenApiError.ErrorMessage
import play.api.libs.json._

object JsonUtils {

  implicit class ErrorMessageExtension(val in: ErrorMessage) extends AnyVal {
    def toJson: JsonValidationError = JsonValidationError(List(in.value), in.args: _*)
  }

  implicit class OpenApiErrorExtension(val in: OpenApiError) extends AnyVal {
    def toJson: (JsPath, List[JsonValidationError]) = (JsPath(in.path.map(asPathNode)), in.errors.toList.map(_.toJson))

    private def asPathNode(str: String): PathNode = str.head match {
      case '*' => RecursiveSearch(str.stripPrefix("*"))
      case '.' => KeyPathNode(str.stripPrefix("."))
      case '[' => IdxPathNode(str.stripPrefix("[").stripSuffix("]").toInt)
      case _ => KeyPathNode(str) // incorrect PathNode, best effort fallback
    }
  }

  implicit class JsonFormatExtension[A](val in: Format[A]) extends AnyVal {
    /**
     * Create a `Format[B]` mapping values from a `Format[A]`
     */
    def imap[B](f: A => B)(g: B => A): Format[B] =
      Format(in.map(f), in.contramap(g))

    /**
     * Create a `Format[B]` mapping values from a `Format[A]` but allowing the read part to fail
     */
    def validate[B](f: A => Either[OpenApiError, B])(g: B => A): Format[B] =
      Format(js => in.reads(js).flatMap(a => asJson(f(a))), in.contramap(g))

    /**
     * Add a validation for a `Format[A]`, does not change the value
     * Similar to a filter but takes list of errors instead of boolean
     */
    def verify(f: A => List[OpenApiError]): Format[A] =
      Format(js => in.reads(js).flatMap(a => asJson(f(a), a)), in)

    /**
     * Add a hint attribute to the `Format[A]`.
     *
     * On read, the `attr` field should be present with the value specified in params
     * On write, adds the `attr` field with the value specified in params
     *
     * @param attr  hint attribute name
     * @param value hint attribute value
     */
    def hint(attr: String, value: String): Format[A] = Format(
      js => (js \ attr).validate[String].flatMap(hint => if (hint == value) in.reads(js) else JsError(List(OpenApiError.badHintValue(hint, value, attr).toJson))),
      a => in.writes(a).as[JsObject].deepMerge(Json.obj(attr -> value))
    )

    private def asJson[T](res: Either[OpenApiError, T]): JsResult[T] =
      res.fold(err => JsError(List(err.toJson)), JsSuccess(_))

    private def asJson[T](errors: List[OpenApiError], value: T): JsResult[T] =
      if (errors.isEmpty) JsSuccess(value) else JsError(errors.map(_.toJson))
  }

}
