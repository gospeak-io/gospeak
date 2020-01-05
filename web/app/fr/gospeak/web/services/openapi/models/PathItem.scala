package fr.gospeak.web.services.openapi.models

import fr.gospeak.web.services.openapi.models.PathItem.Operation
import fr.gospeak.web.services.openapi.models.utils.{Markdown, TODO}

// TODO PathItem or Ref
/**
 * @see "https://spec.openapis.org/oas/v3.0.2#paths-object"
 * @see "https://spec.openapis.org/oas/v3.0.2#path-item-object"
 */
final case class PathItem(summary: Option[String],
                          description: Option[Markdown],
                          parameters: Option[List[Parameter]], // TODO no duplicates (name & location)
                          servers: Option[List[Server]],
                          get: Option[Operation],
                          put: Option[Operation],
                          post: Option[Operation],
                          delete: Option[Operation],
                          options: Option[Operation],
                          head: Option[Operation],
                          patch: Option[Operation],
                          trace: Option[Operation],
                          extensions: Option[TODO]) {
  def operations: Map[String, Operation] =
    Map("get" -> get, "put" -> put, "post" -> post, "delete" -> delete, "options" -> options, "head" -> head, "patch" -> patch, "trace" -> trace)
      .collect { case (k, Some(v)) => (k, v) }
}

object PathItem {

  /**
   * @see "https://spec.openapis.org/oas/v3.0.2#operation-object"
   */
  final case class Operation(tags: Option[Seq[String]],
                             operationId: Option[String],
                             deprecated: Option[Boolean],
                             summary: Option[String],
                             description: Option[Markdown],
                             externalDocs: Option[ExternalDoc],
                             parameters: Option[List[Parameter]], // TODO no duplicates (name & location)
                             requestBody: Option[RequestBody],
                             responses: Map[String, Response], // TODO must have at least one, String => ResponseCode (Http code or default)
                             callbacks: Option[Map[String, TODO]], // TODO or Ref
                             security: Option[TODO],
                             servers: Option[List[Server]],
                             extensions: Option[TODO])

}
