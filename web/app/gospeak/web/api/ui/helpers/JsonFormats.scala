package gospeak.web.api.ui.helpers

import gospeak.core.domain.messages.Message
import gospeak.libs.scala.domain.{Html, LiquidMarkdown, PageData, Url}
import gospeak.web.api.ui._
import play.api.libs.json._

object JsonFormats {
  implicit val suggestedItemWrites: Writes[SuggestedItem] = Json.writes[SuggestedItem]
  implicit val searchResultItemWrites: Writes[SearchResultItem] = Json.writes[SearchResultItem]
  implicit val validationResultWrites: Writes[ValidationResult] = Json.writes[ValidationResult]
  implicit val templateDataResponseWrites: Writes[TemplateDataResponse] = Json.writes[TemplateDataResponse]
  implicit def liquidMarkdownReads[A]: Reads[LiquidMarkdown[A]] = (json: JsValue) => json.validate[String].map(LiquidMarkdown[A])
  implicit val messageRefReads: Reads[Message.Ref] = (json: JsValue) => json.validate[String].flatMap(Message.Ref.from(_).fold(e => JsError(e.getMessage), JsSuccess(_)))
  implicit val templateRequestReads: Reads[TemplateRequest] = Json.reads[TemplateRequest]
  implicit val htmlWrites: Writes[Html] = (o: Html) => JsString(o.value)
  implicit val templateResponseWrites: Writes[TemplateResponse] = Json.writes[TemplateResponse]
  implicit val urlWrites: Writes[Url] = (u: Url) => JsString(u.value)
  implicit val pageDataSizeWrites: Writes[PageData.Size] = Json.writes[PageData.Size]
  implicit val pageDataSizedItemWrites: Writes[PageData.SizedItem] = Json.writes[PageData.SizedItem]
  implicit val pageDataRowItemWrites: Writes[PageData.RowItem] = Json.writes[PageData.RowItem]
  implicit val pageDataWrites: Writes[PageData] = Json.writes[PageData]
}
