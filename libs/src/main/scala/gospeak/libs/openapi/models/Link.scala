package gospeak.libs.openapi.models

import gospeak.libs.openapi.models.utils.{Markdown, TODO}

// TODO Link or Ref
/**
 * @see "https://spec.openapis.org/oas/v3.0.2#link-object"
 */
final case class Link(operationId: Option[String], // should exists in Operation, exclusive with operationRef
                      operationRef: Option[String],
                      description: Option[Markdown],
                      parameters: Option[Map[String, TODO]],
                      requestBody: Option[TODO],
                      server: Option[Server])
