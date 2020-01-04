package fr.gospeak.web.services.openapi.models

import fr.gospeak.web.services.openapi.error.OpenApiError.ErrorMessage
import fr.gospeak.web.services.openapi.models.utils.{TODO, Version}

/**
 * A parsed OpenAPI Specification
 *
 * Similar to "io.swagger.core.v3" % "swagger-models" % "2.1.1" but in Scala and some helpers
 *
 * @see "https://spec.openapis.org/oas/v3.0.2"
 * @see "https://github.com/OAI/OpenAPI-Specification/blob/3.0.2/versions/3.0.2.md"
 */
final case class OpenApi(openapi: Version,
                         info: Info,
                         externalDocs: Option[ExternalDoc],
                         servers: Option[List[Server]],
                         tags: Option[List[Tag]],
                         security: Option[TODO],
                         paths: Option[TODO],
                         components: Option[TODO],
                         extensions: Option[TODO]) {
  def hasErrors: Option[List[ErrorMessage]] = {
    val duplicateTags = tags
      .map(t => t.groupBy(_.name).filter(_._2.length > 1).keys.toList)
      .getOrElse(List())
      .map(ErrorMessage.duplicateValue(_, "tags"))

    val errors = duplicateTags
    if (errors.isEmpty) None else Some(errors)
  }
}
