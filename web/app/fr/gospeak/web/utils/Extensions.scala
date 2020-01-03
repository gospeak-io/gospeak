package fr.gospeak.web.utils

import fr.gospeak.web.services.openapi.error.OpenApiError.Message
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
      Format(in.reads(_).flatMap(f(_).fold(errs => JsError(Seq(JsPath -> errs)), JsSuccess(_))), in.contramap(g))

    def validate[B](f: A => Either[Seq[Message], B])(g: B => A): Format[B] =
      iflatMap(f(_).left.map(_.map(m => JsonValidationError(Seq(m.value), m.args: _*))))(g)
  }

}
