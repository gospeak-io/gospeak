package fr.gospeak.web.utils

import fr.gospeak.web.api.ui._
import play.api.libs.json.{Json, Reads, Writes}

object JsonFormats {
  implicit val suggestedItemWrites: Writes[SuggestedItem] = Json.writes[SuggestedItem]
  implicit val validationResultWrites: Writes[ValidationResult] = Json.writes[ValidationResult]
  implicit val templateDataResponseWrites: Writes[TemplateDataResponse] = Json.writes[TemplateDataResponse]
  implicit val templateRequestReads: Reads[TemplateRequest] = Json.reads[TemplateRequest]
  implicit val templateResponseWrites: Writes[TemplateResponse] = Json.writes[TemplateResponse]
}
