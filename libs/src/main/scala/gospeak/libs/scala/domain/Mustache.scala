package gospeak.libs.scala.domain

import io.circe.{Encoder, Json, JsonNumber, JsonObject}
import yamusca.context._
import yamusca.imports.{Context, mustache}
import yamusca.parser.ParseInput

import scala.collection.mutable

final case class Mustache[A](value: String) extends AnyVal {
  def render(data: A)(implicit e: Encoder[A]): Either[Mustache.Error, String] = render(e.apply(data))

  def render(data: Json): Either[Mustache.Error, String] = Mustache.render(value, data)

  def as[B]: Mustache[B] = Mustache[B](value)

  def asMarkdown: MustacheMarkdown[A] = MustacheMarkdown[A](value)
}

final case class MustacheHtml[A](value: String) extends AnyVal {
  def render(data: A)(implicit e: Encoder[A]): Either[Mustache.Error, Html] = Mustache.render(value, e.apply(data)).map(Html)
}

final case class MustacheMarkdown[A](value: String) extends AnyVal {
  def render(data: A)(implicit e: Encoder[A]): Either[Mustache.Error, Markdown] = render(e.apply(data))

  def render(data: Json): Either[Mustache.Error, Markdown] = Mustache.render(value, data).map(Markdown(_))

  def asText: Mustache[A] = Mustache[A](value)
}

object Mustache {

  sealed trait Error {
    def message: String
  }

  object Error {
    def apply(err: (ParseInput, String)): Error = Unknown(err._2)

    final case class MissingVariables(names: List[String]) extends Error {
      def message = s"Missing variable: ${names.map(n => s"'$n'").mkString(", ")}"
    }

    final case class Unknown(message: String) extends Error

  }

  def render(tmpl: String, data: Json): Either[Error, String] =
    mustache.parse(tmpl).left.map(Error(_)).flatMap { t =>
      val ctx = ContextWrapper(data)
      val res = mustache.render(t)(ctx)
      if (ctx.missingKeys.isEmpty) {
        Right(res)
      } else {
        Left(Error.MissingVariables(ctx.missingKeys.toList))
      }
    }

  private final case class ContextWrapper(parentKeys: List[String], ctx: Context, usedKeys: mutable.HashSet[String], missingKeys: mutable.ListBuffer[String]) extends Context {
    private val specialKeys = Set("-first", "-last", "-index")

    override def find(key: String): (Context, Option[Value]) = {
      def wrapValue(value: Value, parentKeys: List[String]): Value = value match {
        case SimpleValue(_) => value
        case BoolValue(_) => value
        case ListValue(v) => ListValue(v.zipWithIndex.map { case (e, i) => wrapValue(e, parentKeys :+ s"[$i]") })
        case MapValue(c, e) => MapValue(ContextWrapper(parentKeys, c, usedKeys, missingKeys), e)
        case LambdaValue(_) => value
      }

      val res = ctx.find(key)
      val path = parentKeys :+ s".$key"
      if (res._2.isEmpty && !specialKeys.contains(key)) {
        missingKeys += path.mkString
      } else {
        usedKeys += path.mkString
      }
      (ContextWrapper(parentKeys, res._1, usedKeys, missingKeys), res._2.map(wrapValue(_, path)))
    }
  }

  private object ContextWrapper {
    def apply(data: Json): ContextWrapper = new ContextWrapper(List(), jsonToContext(data), mutable.HashSet[String](), mutable.ListBuffer[String]())

    private def jsonToContext(json: Json): Context = {
      def jsonToValue(json: Json): Value =
        json.fold(
          jsonNull = Value.fromString(""),
          jsonBoolean = (b: Boolean) => Value.fromBoolean(b),
          jsonNumber = (n: JsonNumber) => Value.fromString(n.toLong.map(_.toString).getOrElse(n.toDouble.toString)),
          jsonString = (s: String) => Value.fromString(s),
          jsonArray = (l: Vector[Json]) => Value.fromSeq(l.map(jsonToValue)),
          jsonObject = (o: JsonObject) => Value.fromMap(o.toMap.mapValues(jsonToValue)))

      json.fold(
        jsonNull = Context.empty,
        jsonBoolean = (_: Boolean) => Context.empty,
        jsonNumber = (_: JsonNumber) => Context.empty,
        jsonString = (_: String) => Context.empty,
        jsonArray = (_: Vector[Json]) => Context.empty,
        jsonObject = (o: JsonObject) => Context.fromMap(o.toMap.mapValues(jsonToValue)))
    }
  }

}
