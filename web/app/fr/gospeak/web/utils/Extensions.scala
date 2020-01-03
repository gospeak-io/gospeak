package fr.gospeak.web.utils

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

  implicit class JsonFormatExtension[A](val in: Format[A]) extends AnyVal {
    def imap[B](f: A => B)(g: B => A): Format[B] =
      Format(in.map(f), in.contramap(g))

    def iflatMap[B](f: A => Either[Seq[JsonValidationError], B])(g: B => A): Format[B] =
      Format(js => in.reads(js).flatMap(a => asJson(f(a))), in.contramap(g))

    def validate[B](f: A => Either[Seq[ErrorMessage], B])(g: B => A): Format[B] =
      iflatMap(a => f(a).left.map(asJson))(g)

    def check(f: A => Option[Seq[ErrorMessage]]): Format[A] =
      Format(js => in.reads(js).flatMap(a => asJson(f(a).map(asJson).toLeft(a))), in)

    private def asJson(errs: Seq[ErrorMessage]): Seq[JsonValidationError] =
      errs.map(err => JsonValidationError(Seq(err.value), err.args: _*))

    private def asJson[T](res: Either[Seq[JsonValidationError], T]): JsResult[T] =
      res.fold(errs => JsError(Seq(JsPath -> errs)), JsSuccess(_))
  }

}
