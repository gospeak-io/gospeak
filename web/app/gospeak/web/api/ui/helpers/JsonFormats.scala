package gospeak.web.api.ui.helpers

import gospeak.core.domain.utils.TemplateData
import gospeak.web.api.ui._
import gospeak.web.utils.Extensions._
import gospeak.libs.scala.domain.Html
import gospeak.libs.scala.domain.MustacheTmpl.MustacheMarkdownTmpl
import play.api.libs.json._

object JsonFormats {
  implicit val suggestedItemWrites: Writes[SuggestedItem] = Json.writes[SuggestedItem]
  implicit val searchResultItemWrites: Writes[SearchResultItem] = Json.writes[SearchResultItem]
  implicit val validationResultWrites: Writes[ValidationResult] = Json.writes[ValidationResult]
  implicit val templateDataResponseWrites: Writes[TemplateDataResponse] = Json.writes[TemplateDataResponse]
  implicit def templateReads[A]: Reads[MustacheMarkdownTmpl[A]] = (json: JsValue) => json.validate[String].map(MustacheMarkdownTmpl[A])
  implicit val templateDataRefReads: Reads[TemplateData.Ref] = (json: JsValue) => json.validate[String].flatMap(TemplateData.Ref.from(_).toJsResult(_.getMessage))
  implicit val templateRequestReads: Reads[TemplateRequest] = Json.reads[TemplateRequest]
  implicit val htmlWrites: Writes[Html] = (o: Html) => JsString(o.value)
  implicit val templateResponseWrites: Writes[TemplateResponse] = Json.writes[TemplateResponse]
}
