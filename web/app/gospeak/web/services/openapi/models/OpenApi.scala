package gospeak.web.services.openapi.models

import cats.data.NonEmptyList
import gospeak.web.services.openapi.OpenApiUtils
import gospeak.web.services.openapi.error.OpenApiError
import gospeak.web.services.openapi.models.utils.{TODO, Version}

/**
 * A parsed OpenAPI Specification
 *
 * Similar to "io.swagger.core.v3" % "swagger-models" % "2.1.1" but in Scala and with some validation & some helpers
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
                         components: Option[Components],
                         paths: Map[Path, PathItem],
                         extensions: Option[TODO]) {
  def getSchemas: Schemas = components.flatMap(_.schemas).getOrElse(Schemas())

  def getErrors: List[OpenApiError] = {
    val s = getSchemas
    OpenApi.groupErrors(
      OpenApiUtils.validate("servers", servers.getOrElse(List()), s) ++
        components.map(_.getErrors(s)).getOrElse(List()).map(_.atPath(".components")) ++
        OpenApiUtils.validate("paths", paths.map { case (k, v) => (k.value, v) }, s) ++
        OpenApiUtils.noDuplicates[Tag]("tags", tags.getOrElse(List()), _.name) ++
        OpenApi.checkDuplicatedPaths(paths).map(_.atPath(".paths")) ++
        OpenApi.checkDuplicatedOperationIds(paths).map(_.atPath(".paths"))
    )
  }
}

object OpenApi {
  def checkDuplicatedPaths(paths: Map[Path, PathItem]): List[OpenApiError] =
    paths.keys
      .groupBy(_.mapVariables(_ => "?").value)
      .filter(_._2.size > 1)
      .values.flatMap(_.lastOption).toList
      .map(path => OpenApiError.duplicateValue(path.value).atPath(s".${path.value}"))

  def checkDuplicatedOperationIds(paths: Map[Path, PathItem]): List[OpenApiError] =
    paths.flatMap { case (path, item) =>
      item.operations.flatMap { case (key, op) =>
        op.operationId.map(id => (List(s".${path.value}", s".$key"), id))
      }
    }.toList.groupBy(_._2)
      .filter(_._2.size > 1)
      .values.flatMap(_.lastOption).toList
      .map { case (path, opId) => OpenApiError.duplicateValue(opId).atPath(path) }

  def groupErrors(errors: List[OpenApiError]): List[OpenApiError] =
    errors.groupBy(_.path).flatMap { case (path, errors) =>
      NonEmptyList.fromList(errors.flatMap(_.errors.toList)).map(errs => OpenApiError(path, errs))
    }.toList.sortBy(_.path.mkString)
}
