package fr.gospeak.web.services.openapi.models.utils

import fr.gospeak.web.services.openapi.error.OpenApiError
import fr.gospeak.web.services.openapi.models.Schemas

trait HasValidation {
  def getErrors(s: Schemas): List[OpenApiError]
}
