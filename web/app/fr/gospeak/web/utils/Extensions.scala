package fr.gospeak.web.utils

import play.api.data.FormError
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

  implicit class SeqHtmlExtension(val in: Seq[Html]) extends AnyVal {
    def mkHtml(sep: Html): Html = Formats.mkHtml(in, sep)

    def mkHtml(sep: String): Html = Formats.mkHtml(in, Html(sep))

    def mkHtml: Html = Formats.mkHtml(in, Html(""))
  }

}
