package fr.gospeak.web.utils

import cats.data.NonEmptyList
import fr.gospeak.web.services.openapi.error.OpenApiError.ErrorMessage
import play.api.data.FormError
import play.api.libs.json._
import play.twirl.api.Html

import scala.language.higherKinds
import scala.util.Try

object Extensions {

  implicit class FormMapExtensions[V](val in: Map[String, V]) extends AnyVal {
    def eitherGet(key: String): Either[FormError, V] =
      in.get(key).map(Right(_)).getOrElse(Left(FormError(key, Mappings.requiredError)))

    def eitherGetAndParse[A](key: String, parse: V => Try[A], err: => String): Either[FormError, A] =
      eitherGet(key).flatMap(v => parse(v).toEither.left.map(e => FormError(key, err, e.getMessage)))
  }

  implicit class EitherExtensionsWeb[A, E](val in: Either[E, A]) extends AnyVal {
    def toJsResult(f: E => String): JsResult[A] = in match {
      case Right(a) => JsSuccess(a)
      case Left(e) => JsError(f(e))
    }
  }

  implicit class SeqHtmlExtension(val in: Seq[Html]) extends AnyVal {
    def mkHtml(sep: Html): Html = Formats.mkHtml(in, sep)

    def mkHtml(sep: String): Html = Formats.mkHtml(in, Html(sep))

    def mkHtml: Html = Formats.mkHtml(in, Html(""))
  }

  implicit class ErrorMessageExtension(val in: ErrorMessage) extends AnyVal {
    def toJson: JsonValidationError = JsonValidationError(Seq(in.value), in.args: _*)
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
    def iflatMap[B](f: A => Either[NonEmptyList[JsonValidationError], B])(g: B => A): Format[B] =
      Format(js => in.reads(js).flatMap(a => asJson(f(a))), in.contramap(g))

    /**
     * Similar to `iflatMap` but with a different failure type
     */
    def validate[B](f: A => Either[NonEmptyList[ErrorMessage], B])(g: B => A): Format[B] =
      iflatMap(a => f(a).left.map(_.map(_.toJson)))(g)

    /**
     * Add a validation for a `Format[A]`, does not change the value
     * Similar to a filter but takes optional errors instead of boolean
     */
    def verify(f: A => Option[NonEmptyList[ErrorMessage]]): Format[A] =
      Format(js => in.reads(js).flatMap(a => asJson(f(a).map(_.map(_.toJson)).toLeft(a))), in)

    // def hint(attribute: String): Format[A] =
    //   Format(js => in.reads(js).flatMap(a => (js \ attribute).validate[String].map(_ => a)), in)

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
      js => (js \ attr).validate[String].flatMap(hint => if (hint == value) in.reads(js) else JsError(ErrorMessage.badHintValue(hint, value, attr).toJson)),
      a => in.writes(a).as[JsObject].deepMerge(Json.obj(attr -> value))
    )

    private def asJson[T](res: Either[NonEmptyList[JsonValidationError], T]): JsResult[T] = res.fold(errs => JsError(Seq(JsPath -> errs.toList)), JsSuccess(_))
  }

}
