package fr.gospeak.infra.services

import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain
import fr.gospeak.libs.scalautils.domain.Template
import io.circe.{Encoder, Json, JsonNumber, JsonObject}

class TemplateSrv {
  def render[A](tmpl: Template, data: A)(implicit e: Encoder[A]): Either[String, String] = tmpl match {
    case t: Template.Mustache => MustacheTemplateSrv.render(t, data)
  }
}

// cf https://github.com/eikek/yamusca
object MustacheTemplateSrv {

  import yamusca.context.Value
  import yamusca.imports._

  def render[A](tmpl: domain.Template.Mustache, data: A)(implicit e: Encoder[A]): Either[String, String] = {
    mustache.parse(tmpl.value)
      .map(mustache.render(_)(jsonToContext(e.apply(data))))
      .leftMap(_._2)
  }

  private def jsonToContext(json: Json): Context = {
    json.fold(
      jsonNull = Context.empty,
      jsonBoolean = (_: Boolean) => Context.empty,
      jsonNumber = (_: JsonNumber) => Context.empty,
      jsonString = (_: String) => Context.empty,
      jsonArray = (_: Vector[Json]) => Context.empty,
      jsonObject = (o: JsonObject) => Context.fromMap(o.toMap.mapValues(jsonToValue))
    )
  }

  private def jsonToValue(json: Json): Value = {
    json.fold(
      jsonNull = Value.fromString(""),
      jsonBoolean = (b: Boolean) => Value.fromBoolean(b),
      jsonNumber = (n: JsonNumber) => Value.fromString(n.toLong.map(_.toString).getOrElse(n.toDouble.toString)),
      jsonString = (s: String) => Value.fromString(s),
      jsonArray = (l: Vector[Json]) => Value.fromSeq(l.map(jsonToValue)),
      jsonObject = (o: JsonObject) => Value.fromMap(o.toMap.mapValues(jsonToValue))
    )
  }
}
