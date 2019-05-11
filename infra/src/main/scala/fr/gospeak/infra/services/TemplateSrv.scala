package fr.gospeak.infra.services

import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain
import fr.gospeak.libs.scalautils.domain.Template
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveEncoder
import io.circe.{Encoder, Json, JsonNumber, JsonObject}

class TemplateSrv {
  def asData(data: TemplateData): Json = TemplateSrv.templateDataEncoder.apply(data)

  def render(tmpl: Template, data: TemplateData): Either[String, String] =
    renderTemplate(tmpl, data)(TemplateSrv.templateDataEncoder)

  def render(tmpl: Template): Either[String, String] =
    renderTemplate(tmpl, Json.obj())

  private def renderTemplate[A](tmpl: Template, data: A)(implicit e: Encoder[A]): Either[String, String] = tmpl match {
    case t: Template.Mustache => MustacheTemplateSrv.render(t, data)
  }
}

object TemplateSrv {
  private implicit val circeConfiguration: Configuration = Configuration.default.withDiscriminator("type")
  private implicit val strDateTimeEncoder: Encoder[TemplateData.StrDateTime] = deriveEncoder[TemplateData.StrDateTime]
  private implicit val userEncoder: Encoder[TemplateData.User] = deriveEncoder[TemplateData.User]
  private implicit val groupEncoder: Encoder[TemplateData.Group] = deriveEncoder[TemplateData.Group]
  private implicit val cfpEncoder: Encoder[TemplateData.Cfp] = deriveEncoder[TemplateData.Cfp]
  private implicit val eventEncoder: Encoder[TemplateData.Event] = deriveEncoder[TemplateData.Event]
  private implicit val proposalEncoder: Encoder[TemplateData.Proposal] = deriveEncoder[TemplateData.Proposal]
  private implicit val eventCreatedEncoder: Encoder[TemplateData.EventCreated] = deriveEncoder[TemplateData.EventCreated]
  private implicit val talkAddedEncoder: Encoder[TemplateData.TalkAdded] = deriveEncoder[TemplateData.TalkAdded]
  private implicit val talkRemovedEncoder: Encoder[TemplateData.TalkRemoved] = deriveEncoder[TemplateData.TalkRemoved]
  private implicit val eventPublishedEncoder: Encoder[TemplateData.EventPublished] = deriveEncoder[TemplateData.EventPublished]
  private implicit val proposalCreatedEncoder: Encoder[TemplateData.ProposalCreated] = deriveEncoder[TemplateData.ProposalCreated]
  private implicit val templateDataEncoder: Encoder[TemplateData] = deriveEncoder[TemplateData]
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
