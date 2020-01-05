package fr.gospeak.web.services.openapi.models

import cats.data.NonEmptyList
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
                         components: Option[Components],
                         paths: Map[Path, PathItem],
                         extensions: Option[TODO]) {
  def hasErrors: Option[NonEmptyList[ErrorMessage]] = {
    val duplicateTags = tags.getOrElse(List())
      .groupBy(_.name)
      .filter(_._2.length > 1)
      .keys.toList
      .map(ErrorMessage.duplicateValue(_, "tags"))

    val references = components.flatMap(_.schemas.map(_.values.toList.flatMap(_.flatten.collect { case Schema.ReferenceVal(r) => r }))).getOrElse(List()).distinct
    val missingReferences = references.flatMap(r => r.localRef match {
      case Some(("schemas", name)) => if (components.exists(_.schemas.exists(_.contains(name)))) None else Some(ErrorMessage.missingReference(r.value))
      case Some((component, _)) => Some(ErrorMessage.unknownReference(r.value, component))
      case None => None
    })

    val duplicatePaths = paths.keys.groupBy(_.value).filter(_._2.size > 1).keys.toList.map(ErrorMessage.duplicateValue(_, "paths"))
    val duplicateOperationId = paths.values.flatMap(_.operations.values).flatMap(_.operationId).groupBy(identity).filter(_._2.size > 1).keys.toList.map(ErrorMessage.duplicateValue(_, "paths operationId"))

    NonEmptyList.fromList(duplicateTags ++ missingReferences ++ duplicatePaths ++ duplicateOperationId)
  }
}
