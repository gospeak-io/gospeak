package fr.gospeak.web.services.openapi.models

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
                         extensions: Option[TODO])
