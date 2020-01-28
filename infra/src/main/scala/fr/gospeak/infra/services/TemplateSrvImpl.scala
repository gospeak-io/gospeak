package fr.gospeak.infra.services

import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.core.services.TemplateSrv
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.Markdown
import gospeak.libs.scala.domain.MustacheTmpl.{MustacheMarkdownTmpl, MustacheTextTmpl}
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.{Encoder, Json, JsonNumber, JsonObject}

class TemplateSrvImpl extends TemplateSrv {
  def render[A <: TemplateData](tmpl: MustacheTextTmpl[A], data: A): Either[String, String] =
    MustacheTemplateSrv.render(tmpl.value, data: TemplateData)(TemplateSrvImpl.templateDataEncoder)

  def render[A <: TemplateData](tmpl: MustacheMarkdownTmpl[A], data: A): Either[String, Markdown] =
    MustacheTemplateSrv.render(tmpl.value, data: TemplateData)(TemplateSrvImpl.templateDataEncoder).map(Markdown)

  def render[A <: TemplateData](tmpl: MustacheTextTmpl[A]): Either[String, String] =
    MustacheTemplateSrv.render(tmpl.value, Json.obj())

  def render[A <: TemplateData](tmpl: MustacheMarkdownTmpl[A]): Either[String, Markdown] =
    MustacheTemplateSrv.render(tmpl.value, Json.obj()).map(Markdown)
}

object TemplateSrvImpl {
  private implicit val circeConfiguration: Configuration = Configuration.default.withDiscriminator("type")
  private implicit val strDateTimeEncoder: Encoder[TemplateData.StrDateTime] = deriveConfiguredEncoder[TemplateData.StrDateTime]
  private implicit val descriptionEncoder: Encoder[TemplateData.Description] = deriveConfiguredEncoder[TemplateData.Description]
  private implicit val userEncoder: Encoder[TemplateData.User] = deriveConfiguredEncoder[TemplateData.User]
  private implicit val groupEncoder: Encoder[TemplateData.Group] = deriveConfiguredEncoder[TemplateData.Group]
  private implicit val cfpEncoder: Encoder[TemplateData.Cfp] = deriveConfiguredEncoder[TemplateData.Cfp]
  private implicit val eventEncoder: Encoder[TemplateData.Event] = deriveConfiguredEncoder[TemplateData.Event]
  private implicit val proposalEncoder: Encoder[TemplateData.Proposal] = deriveConfiguredEncoder[TemplateData.Proposal]
  private implicit val eventVenueEncoder: Encoder[TemplateData.EventVenue] = deriveConfiguredEncoder[TemplateData.EventVenue]
  private implicit val talkSpeakerEncoder: Encoder[TemplateData.TalkSpeaker] = deriveConfiguredEncoder[TemplateData.TalkSpeaker]
  private implicit val eventTalkEncoder: Encoder[TemplateData.EventTalk] = deriveConfiguredEncoder[TemplateData.EventTalk]

  private implicit val eventCreatedEncoder: Encoder[TemplateData.EventCreated] = deriveConfiguredEncoder[TemplateData.EventCreated]
  private implicit val talkAddedEncoder: Encoder[TemplateData.TalkAdded] = deriveConfiguredEncoder[TemplateData.TalkAdded]
  private implicit val talkRemovedEncoder: Encoder[TemplateData.TalkRemoved] = deriveConfiguredEncoder[TemplateData.TalkRemoved]
  private implicit val eventPublishedEncoder: Encoder[TemplateData.EventPublished] = deriveConfiguredEncoder[TemplateData.EventPublished]
  private implicit val proposalCreatedEncoder: Encoder[TemplateData.ProposalCreated] = deriveConfiguredEncoder[TemplateData.ProposalCreated]
  private implicit val eventInfoEncoder: Encoder[TemplateData.EventInfo] = deriveConfiguredEncoder[TemplateData.EventInfo]

  private implicit val templateDataEncoder: Encoder[TemplateData] = deriveConfiguredEncoder[TemplateData]

  def asData(data: TemplateData): Json = templateDataEncoder.apply(data)
}

// cf https://github.com/eikek/yamusca
object MustacheTemplateSrv {

  import yamusca.context.Value
  import yamusca.imports._

  def render[A](tmpl: String, data: A)(implicit e: Encoder[A]): Either[String, String] = {
    mustache.parse(tmpl)
      .map(mustache.render(_)(jsonToContext(e.apply(data))))
      .map(v => if (v.startsWith("Vector(MapValue(yamusca.context$")) "Invalid mustache template" else v)
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
