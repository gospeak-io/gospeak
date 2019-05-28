package fr.gospeak.web.utils

import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.libs.scalautils.domain.{Html, MarkdownTemplate}
import fr.gospeak.web.api.ui._
import fr.gospeak.web.utils.Extensions._
import play.api.libs.json._

object JsonFormats {
  implicit val suggestedItemWrites: Writes[SuggestedItem] = Json.writes[SuggestedItem]
  implicit val validationResultWrites: Writes[ValidationResult] = Json.writes[ValidationResult]
  implicit val templateDataResponseWrites: Writes[TemplateDataResponse] = Json.writes[TemplateDataResponse]
  implicit def templateReads[A]: Reads[MarkdownTemplate[A]] = (json: JsValue) => json.validate[String].map(MarkdownTemplate.Mustache[A])
  implicit val templateDataRefReads: Reads[TemplateData.Ref] = (json: JsValue) => json.validate[String].flatMap(TemplateData.Ref.from(_).toJsResult(_.getMessage))
  implicit val templateRequestReads: Reads[TemplateRequest] = Json.reads[TemplateRequest]
  implicit val htmlWrites: Writes[Html] = (o: Html) => JsString(o.value)
  implicit val templateResponseWrites: Writes[TemplateResponse] = Json.writes[TemplateResponse]
}
