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

final case class Liquid[A](value: String) {
  def render(data: A)(implicit e: Encoder[A]): Either[Liquid.Error, String] = Liquid.render(value, e.apply(data))
}

final case class LiquidHtml[A](value: Liquid[A]) extends AnyVal {
  def render(data: A)(implicit e: Encoder[A]): Either[Liquid.Error, Html] = value.render(data).map(Html)
}

object LiquidHtml {
  def apply[A](value: String): LiquidHtml[A] = new LiquidHtml(Liquid[A](value))
}

final case class LiquidMarkdown[A](value: Liquid[A]) extends AnyVal {
  def render(data: A)(implicit e: Encoder[A]): Either[Liquid.Error, Markdown] = value.render(data).map(Markdown(_))
}

object LiquidMarkdown {
  def apply[A](value: String): LiquidMarkdown[A] = new LiquidMarkdown(Liquid[A](value))
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
        case c: FailedPredicateException => Unknown(e.getMessage) // TODO
        case c: NoViableAltException => Unknown(e.getMessage) // TODO
        case c: LexerNoViableAltException => Unknown(e.getMessage) // TODO
        case _ => Unknown(e.getMessage) // TODO
      }
      case e: ExceededMaxIterationsException => Unknown(e.getMessage) // TODO
      case _ => e.getMessage match {
        case "problem with evaluating include" => Unknown("Invalid include") // TODO
        case msg: String if msg.nonEmpty => Unknown(msg)
        case _ => Unknown("Error without message")
      }
    }

    final case class InvalidTemplate(line: Int, char: Int, invalidToken: String, expectedTokens: List[String]) extends Error {
      def message = s"Invalid template at line $line:$char, found token '$invalidToken' but expect one among ${expectedTokens.map(t => s"'$t'").mkString(", ")}"
    }

    final case class MissingVariable(name: String) extends Error {
      def message = s"Missing '$name' variable"
    }

    final case class Unknown(message: String) extends Error

  }

  def render(tmpl: String, data: Json): Either[Error, String] = for {
    parsed <- Try(Template.parse(tmpl)).toEither.left.map(Error(_))
    renderSettings = new RenderSettings.Builder().withStrictVariables(true).withShowExceptionsFromInclude(true).build()
    protectionSettings = new ProtectionSettings.Builder() /*.withMaxRenderTimeMillis(1000)*/ .build()
    res <- Try(parsed.withRenderSettings(renderSettings).withProtectionSettings(protectionSettings).render(data.noSpaces)).toEither.left.map(Error(_))
  } yield res

  private def tokenName(token: Int): String = if (token < 0) "<EOF>" else LiquidParser.VOCABULARY.getSymbolicName(token)

}
