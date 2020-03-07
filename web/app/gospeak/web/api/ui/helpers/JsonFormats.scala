package gospeak.web.api.ui.helpers

import gospeak.core.domain.messages.Message
import gospeak.libs.scala.domain.{Html, Mustache}
import gospeak.web.api.ui._
import gospeak.web.utils.Extensions._
import play.api.libs.json._

object JsonFormats {
  implicit val suggestedItemWrites: Writes[SuggestedItem] = Json.writes[SuggestedItem]
  implicit val searchResultItemWrites: Writes[SearchResultItem] = Json.writes[SearchResultItem]
  implicit val validationResultWrites: Writes[ValidationResult] = Json.writes[ValidationResult]
  implicit val templateDataResponseWrites: Writes[TemplateDataResponse] = Json.writes[TemplateDataResponse]
  implicit def templateReads[A]: Reads[Mustache.Markdown[A]] = (json: JsValue) => json.validate[String].map(Mustache.Markdown[A])
  implicit val messageRefReads: Reads[Message.Ref] = (json: JsValue) => json.validate[String].flatMap(Message.Ref.from(_).toJsResult(_.getMessage))
  implicit val templateRequestReads: Reads[TemplateRequest] = Json.reads[TemplateRequest]
  implicit val htmlWrites: Writes[Html] = (o: Html) => JsString(o.value)
  implicit val templateResponseWrites: Writes[TemplateResponse] = Json.writes[TemplateResponse]
}
