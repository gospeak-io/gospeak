package gospeak.web.services.openapi.models.utils

import gospeak.web.services.openapi.error.OpenApiError
import gospeak.web.services.openapi.models.Schemas

trait HasValidation {
  def getErrors(s: Schemas): List[OpenApiError]
}
