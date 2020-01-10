package fr.gospeak.web.utils

import fr.gospeak.libs.scalautils.domain.CustomException
import play.api.libs.json._

object JsonUtils {

  implicit class JsonFormatExtension[A](val in: Format[A]) extends AnyVal {
    /**
     * Create a `Format[B]` mapping values from a `Format[A]`
     */
    def imap[B](f: A => B)(g: B => A): Format[B] =
      Format(in.map(f), in.contramap(g))

    /**
     * Create a `Format[B]` mapping values from a `Format[A]` but allowing the read part to fail
     */
    def validate[B](f: A => Either[(JsPath, Seq[JsonValidationError]), B])(g: B => A): Format[B] =
      Format(js => in.reads(js).flatMap(a => f(a).fold(e => JsError(Seq(e)), b => JsSuccess(b))), in.contramap(g))

    def validate2[B](f: A => Either[CustomException, B])(g: B => A): Format[B] =
      validate(a => f(a).left.map(toJson))(g)

    private def toJson(ex: CustomException): (JsPath, Seq[JsonValidationError]) =
      (JsPath, JsonValidationError(ex.message) +: ex.errors.map(e => JsonValidationError(e.value)))
  }

}
