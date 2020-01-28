package fr.gospeak.web.services.openapi

import fr.gospeak.web.services.openapi.error.OpenApiError
import fr.gospeak.web.services.openapi.models.utils.{HasValidation, Js}
import fr.gospeak.web.services.openapi.models.{Schema, Schemas}

object OpenApiUtils {
  def validate(key: String, value: Map[String, HasValidation], schemas: Schemas): List[OpenApiError] =
    value.flatMap { case (k, v) => v.getErrors(schemas).map(_.atPath(s".$key", s".$k")) }.toList

  def validate(key: String, value: List[HasValidation], schemas: Schemas): List[OpenApiError] =
    value.zipWithIndex.flatMap { case (v, i) => v.getErrors(schemas).map(_.atPath(s".$key", s"[$i]")) }

  def noDuplicates[A](key: String, value: List[A], group: A => String): List[OpenApiError] =
    value.zipWithIndex
      .groupBy(v => group(v._1))
      .filter(_._2.length > 1)
      .values.flatMap(_.lastOption).toList
      .map { case (v, i) => OpenApiError.duplicateValue(group(v)).atPath(s".$key", s"[$i]") }

  def validate(schema: Option[Schema], example: Option[Js], schemas: Schemas): List[OpenApiError] =
    schema.map(schemas.resolve).map {
      case Left(errs) => errs.map(_.atPath(".schema")).toList
      case Right(Some(res)) =>
        example
          .filterNot(_.matchSchema(schemas, res))
          .map(js => OpenApiError.badExampleFormat(js.value, res.hint).atPath(".example")).toList
      case Right(None) => List()
    }.getOrElse(List())
}
