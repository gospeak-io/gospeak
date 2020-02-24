package gospeak.web.utils

import play.api.data.{Form, FormError}
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.mvc.Flash
import play.twirl.api.Html

import scala.language.higherKinds
import scala.util.Try

object Extensions {

  implicit class FormExtensions[A](val in: Form[A]) extends AnyVal {
    def flash(implicit messages: Messages): Flash = Flash(in.data ++ in.errors.headOption.map(_ => "error" -> errors))

    private def errors(implicit messages: Messages): String = in.errors.map(e => s"${e.key}: ${e.format}<br>").mkString("\n")
  }

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

}
