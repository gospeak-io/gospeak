package gospeak.libs.openapi.models.utils

import gospeak.libs.openapi.error.OpenApiError
import gospeak.libs.openapi.models.Schemas

trait HasValidation {
  def getErrors(s: Schemas): List[OpenApiError]
}
