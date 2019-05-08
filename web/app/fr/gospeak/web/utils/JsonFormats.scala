package fr.gospeak.web.utils

import fr.gospeak.web.api.ui.{SuggestedItem, ValidationResult}
import play.api.libs.json.{Json, Writes}

object JsonFormats {
  implicit val suggestedItemWrites: Writes[SuggestedItem] = Json.writes[SuggestedItem]
  implicit val validationResultWrites: Writes[ValidationResult] = Json.writes[ValidationResult]
}
