package gospeak.libs.openapi.models

import gospeak.libs.openapi.OpenApiUtils
import gospeak.libs.openapi.error.OpenApiError
import gospeak.libs.openapi.models.PathItem.Operation
import gospeak.libs.openapi.models.utils.{HasValidation, Markdown, TODO}

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
                          extensions: Option[TODO]) extends HasValidation {
  def operations: Map[String, Operation] =
    Map("get" -> get, "put" -> put, "post" -> post, "delete" -> delete, "options" -> options, "head" -> head, "patch" -> patch, "trace" -> trace)
      .collect { case (k, Some(v)) => (k, v) }

  def getErrors(s: Schemas): List[OpenApiError] = {
    val paramErrors = OpenApiUtils.validate("parameters", parameters.getOrElse(List()), s)
    val serverErrors = OpenApiUtils.validate("servers", servers.getOrElse(List()), s)
    val opsErrors = OpenApiUtils.validate("operations", operations, s).map(e => e.copy(path = e.path.filter(_ != ".operations")))
    paramErrors ++ serverErrors ++ opsErrors
  }
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
                             extensions: Option[TODO]) extends HasValidation {
    def getErrors(s: Schemas): List[OpenApiError] = {
      val paramErrors = OpenApiUtils.validate("parameters", parameters.getOrElse(List()), s)
      val responseErrors = OpenApiUtils.validate("responses", responses, s)
      val serverErrors = OpenApiUtils.validate("servers", servers.getOrElse(List()), s)
      paramErrors ++ responseErrors ++ serverErrors
    }
  }

}
