package gospeak.libs.scala.domain

import io.circe.{Encoder, Json}
import liqp.exceptions.{ExceededMaxIterationsException, LiquidException, VariableNotExistException}
import liqp.{ProtectionSettings, RenderSettings, Template}
import liquid.parser.v4.LiquidParser
import org.antlr.v4.runtime.{FailedPredicateException, InputMismatchException, LexerNoViableAltException, NoViableAltException}

import scala.collection.JavaConverters._
import scala.util.Try

/**
 * Libs:
 *  - https://github.com/bkiers/Liqp
 *  - https://github.com/edadma/liquescent
 */

final case class Liquid[A](value: String) extends AnyVal {
  def render(data: A)(implicit e: Encoder[A]): Either[Liquid.Error, String] = render(e.apply(data))

  def render(data: Json): Either[Liquid.Error, String] = Liquid.render(value, data)

  def as[B]: Liquid[B] = Liquid[B](value)

  def asMarkdown: LiquidMarkdown[A] = LiquidMarkdown[A](value)
}

final case class LiquidHtml[A](value: String) extends AnyVal {
  def render(data: A)(implicit e: Encoder[A]): Either[Liquid.Error, Html] = Liquid.render(value, e.apply(data)).map(Html)
}

final case class LiquidMarkdown[A](value: String) extends AnyVal {
  def render(data: A)(implicit e: Encoder[A]): Either[Liquid.Error, Markdown] = render(e.apply(data))

  def render(data: Json): Either[Liquid.Error, Markdown] = Liquid.render(value, data).map(Markdown(_))

  def as[B]: LiquidMarkdown[B] = LiquidMarkdown[B](value)

  def asText: Liquid[A] = Liquid[A](value)
}

object Liquid {

  sealed trait Error {
    def message: String
  }

  object Error {
    def apply(e: Throwable): Error = e match {
      case e: VariableNotExistException => MissingVariable(e.getVariableName)
      case e: LiquidException => e.getCause match {
        case c: InputMismatchException => InvalidTemplate(e.line, e.charPositionInLine, c.getOffendingToken.getText, c.getExpectedTokens.toList.asScala.toList.map(tokenName(_)))
        case c: FailedPredicateException => LiquidError(e.line, e.charPositionInLine, e.getMessage)
        case c: NoViableAltException => LiquidError(e.line, e.charPositionInLine, e.getMessage)
        case c: LexerNoViableAltException => LiquidError(e.line, e.charPositionInLine, e.getMessage)
        case _ => LiquidError(e.line, e.charPositionInLine, e.getMessage)
      }
      case e: ExceededMaxIterationsException => TooManyIterations(Try(e.getMessage.split(": ")(1).toInt).getOrElse(-1))
      case _ => e.getMessage match {
        case "problem with evaluating include" => BadInclude()
        case msg: String if msg.nonEmpty => Unknown("Liquid error: " + msg)
        case _ => Unknown("Unknown liquid error without message")
      }
    }

    final case class MissingVariable(name: String) extends Error {
      override def message = s"Missing '$name' variable"
    }

    final case class InvalidTemplate(line: Int, char: Int, invalidToken: String, expectedTokens: List[String]) extends Error {
      override def message = s"Invalid template at line $line:$char, found token '$invalidToken' but expect one among ${expectedTokens.map(t => s"'$t'").mkString(", ")}"
    }

    final case class LiquidError(line: Int, char: Int, err: String) extends Error {
      override def message: String = s"Invalid template at line $line:$char, $err"
    }

    final case class BadInclude() extends Error {
      override def message: String = "Invalid include"
    }

    final case class TooManyIterations(maxIterations: Int) extends Error {
      override def message: String = s"Exceeded $maxIterations iterations"
    }

    final case class Unknown(message: String) extends Error

  }

  def render(tmpl: String, data: Json): Either[Error, String] = for {
    parsed <- Try(Template.parse(tmpl)).toEither.left.map(Error(_))
    renderSettings = new RenderSettings.Builder()
      // .withStrictVariables(true) // do not work with optional values :(
      .withShowExceptionsFromInclude(true).build()
    protectionSettings = new ProtectionSettings.Builder()
      // .withMaxRenderTimeMillis(1000)
      .build()
    res <- Try(parsed.withRenderSettings(renderSettings).withProtectionSettings(protectionSettings).render(data.noSpaces)).toEither.left.map(Error(_))
  } yield res

  private def tokenName(token: Int): String = if (token < 0) "<EOF>" else LiquidParser.VOCABULARY.getSymbolicName(token)

}
